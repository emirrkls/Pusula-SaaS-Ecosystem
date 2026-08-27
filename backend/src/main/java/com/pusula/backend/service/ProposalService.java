package com.pusula.backend.service;

import com.pusula.backend.annotation.CheckQuota;
import com.pusula.backend.dto.ProposalDTO;
import com.pusula.backend.dto.ProposalItemDTO;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final ProposalItemRepository proposalItemRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ServiceTicketRepository serviceTicketRepository;
    private final InventoryRepository inventoryRepository;
    private final ServiceUsedPartRepository serviceUsedPartRepository;
    private final FeatureService featureService;

    public ProposalService(ProposalRepository proposalRepository,
            ProposalItemRepository proposalItemRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            ServiceTicketRepository serviceTicketRepository,
            InventoryRepository inventoryRepository,
            ServiceUsedPartRepository serviceUsedPartRepository,
            FeatureService featureService) {
        this.proposalRepository = proposalRepository;
        this.proposalItemRepository = proposalItemRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.serviceTicketRepository = serviceTicketRepository;
        this.inventoryRepository = inventoryRepository;
        this.serviceUsedPartRepository = serviceUsedPartRepository;
        this.featureService = featureService;
    }

    public List<ProposalDTO> getAllByCompany(Long companyId) {
        return proposalRepository.findByCompanyId(companyId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ProposalDTO getById(Long id, Long companyId) {
        return proposalRepository.findByIdAndCompanyId(id, companyId)
                .map(this::mapToDTO)
                .orElse(null);
    }

    @Transactional
    @CheckQuota("PROPOSALS")
    public ProposalDTO create(ProposalDTO dto) {
        User currentUser = getCurrentUser();
        validateCustomerOwnership(dto.getCustomerId(), currentUser.getCompanyId());

        Proposal proposal = new Proposal();
        proposal.setCompanyId(currentUser.getCompanyId());
        proposal.setCustomerId(dto.getCustomerId());
        // The authenticated user is the authoritative proposal preparer. Clients
        // must not be able to select another tenant's signature by sending an ID.
        proposal.setPreparedById(currentUser.getId());
        proposal.setStatus(Proposal.ProposalStatus.DRAFT);
        proposal.setValidUntil(dto.getValidUntil());
        proposal.setNote(dto.getNote());
        proposal.setTitle(dto.getTitle());
        proposal.setTaxRate(dto.getTaxRate() != null ? dto.getTaxRate() : new BigDecimal("20"));
        proposal.setDiscount(dto.getDiscount() != null ? dto.getDiscount() : BigDecimal.ZERO);

        Proposal saved = proposalRepository.save(proposal);

        // Save items
        if (dto.getItems() != null) {
            for (ProposalItemDTO itemDto : dto.getItems()) {
                ProposalItem item = buildItem(saved, currentUser.getCompanyId(), itemDto);
                saved.getItems().add(proposalItemRepository.save(item));
            }
        }

        // Recalculate total
        recalculateTotal(saved);
        featureService.incrementUsage(currentUser.getCompanyId(), "PROPOSALS");

        return mapToDTO(proposalRepository.save(saved));
    }

    @Transactional
    public ProposalDTO update(Long id, Long companyId, ProposalDTO dto) {
        Proposal proposal = proposalRepository.findByIdAndCompanyIdForUpdate(id, companyId)
                .orElseThrow(() -> new RuntimeException("Teklif bulunamadı"));
        validateCustomerOwnership(dto.getCustomerId(), companyId);

        Proposal.ProposalStatus oldStatus = proposal.getStatus();
        Proposal.ProposalStatus newStatus = parseStatus(dto.getStatus());
        validateStatusTransition(oldStatus, newStatus);
        boolean shouldCreateTicket = (newStatus == Proposal.ProposalStatus.APPROVED)
                && (oldStatus != Proposal.ProposalStatus.APPROVED);

        proposal.setCustomerId(dto.getCustomerId());
        // Keep authorship immutable when clients omit preparedById on update.
        // Legacy proposals without an author are attributed to the user repairing
        // the record so future PDFs can resolve the correct stored signature.
        if (proposal.getPreparedById() == null) {
            proposal.setPreparedById(getCurrentUser().getId());
        }
        proposal.setStatus(newStatus);
        proposal.setValidUntil(dto.getValidUntil());
        proposal.setNote(dto.getNote());
        proposal.setTitle(dto.getTitle());
        proposal.setTaxRate(dto.getTaxRate());
        proposal.setDiscount(dto.getDiscount());

        // Delete old items and add new ones
        proposalItemRepository.deleteAll(proposal.getItems());
        proposal.getItems().clear();

        if (dto.getItems() != null) {
            for (ProposalItemDTO itemDto : dto.getItems()) {
                ProposalItem item = buildItem(proposal, proposal.getCompanyId(), itemDto);
                proposal.getItems().add(proposalItemRepository.save(item));
            }
        }

        recalculateTotal(proposal);
        Proposal saved = proposalRepository.save(proposal);

        // Auto-create service ticket when status changes to APPROVED (same as "işe
        // dönüştür")
        if (shouldCreateTicket) {
            createServiceTicketFromProposal(saved);
        }

        return mapToDTO(saved);
    }

    /**
     * Creates a service ticket from an approved proposal.
     * Shared logic between convertToJob() and update() when status changes to
     * APPROVED.
     */
    private void createServiceTicketFromProposal(Proposal proposal) {
        if (proposal.getGeneratedServiceTicketId() != null) {
            throw new IllegalStateException("Teklif daha önce servis fişine dönüştürülmüş.");
        }
        featureService.checkQuota(proposal.getCompanyId(), "TICKETS");

        Map<Long, Integer> requiredStock = new LinkedHashMap<>();
        for (ProposalItem item : proposal.getItems()) {
            if (item.getInventoryId() != null) {
                requiredStock.merge(item.getInventoryId(), item.getQuantity(), Integer::sum);
            }
        }

        Map<Long, Inventory> lockedInventory = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> requirement : requiredStock.entrySet()) {
            Inventory inventory = inventoryRepository
                    .findByIdAndCompanyIdForUpdate(requirement.getKey(), proposal.getCompanyId())
                    .orElseThrow(() -> new RuntimeException("Teklifteki envanter ürünü artık bulunamıyor (ID: "
                            + requirement.getKey() + ")"));
            BigDecimal available = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
            if (available.compareTo(BigDecimal.valueOf(requirement.getValue())) < 0) {
                throw new RuntimeException(inventory.getPartName() + " için stok yetersiz. Gerekli: "
                        + requirement.getValue() + ", mevcut: " + available);
            }
            lockedInventory.put(requirement.getKey(), inventory);
        }

        // Build description from items
        StringBuilder description = new StringBuilder("Teklif #" + proposal.getId() + "\n");
        for (ProposalItem item : proposal.getItems()) {
            description.append("• ").append(item.getDescription())
                    .append(" (").append(item.getQuantity()).append(" adet)\n");
        }

        ServiceTicket ticket = new ServiceTicket();
        ticket.setCompanyId(proposal.getCompanyId());
        ticket.setCustomerId(proposal.getCustomerId());
        ticket.setDescription(description.toString());
        ticket.setStatus(ServiceTicket.TicketStatus.PENDING);
        ticket.setScheduledDate(LocalDateTime.now());
        ticket.setCollectedAmount(BigDecimal.ZERO);

        ServiceTicket savedTicket = serviceTicketRepository.save(ticket);
        proposal.setGeneratedServiceTicketId(savedTicket.getId());
        proposalRepository.save(proposal);

        for (Map.Entry<Long, Integer> requirement : requiredStock.entrySet()) {
            Inventory inventory = lockedInventory.get(requirement.getKey());
            inventory.setQuantity(inventory.getQuantity().subtract(BigDecimal.valueOf(requirement.getValue())));
            inventoryRepository.save(inventory);
        }

        for (ProposalItem item : proposal.getItems()) {
            if (item.getInventoryId() == null) {
                continue;
            }
            Inventory inventory = lockedInventory.get(item.getInventoryId());
            ServiceUsedPart usedPart = ServiceUsedPart.builder()
                    .companyId(proposal.getCompanyId())
                    .serviceTicket(savedTicket)
                    .inventory(inventory)
                    .quantityUsed(BigDecimal.valueOf(item.getQuantity()))
                    .unitOfMeasure(inventory.getUnitOfMeasure())
                    .sellingPriceSnapshot(item.getUnitPrice())
                    .buyingPriceSnapshot(item.getUnitCost())
                    .sourceVehicleId(null)
                    .build();
            serviceUsedPartRepository.save(usedPart);
        }
        featureService.incrementUsage(proposal.getCompanyId(), "TICKETS");
    }

    @Transactional
    public void delete(Long id, Long companyId) {
        proposalRepository.delete(getOwnedProposal(id, companyId));
    }

    @Transactional
    public ProposalDTO convertToJob(Long id, Long companyId) {
        Proposal proposal = proposalRepository.findByIdAndCompanyIdForUpdate(id, companyId)
                .orElseThrow(() -> new RuntimeException("Teklif bulunamadı veya erişim reddedildi."));

        if (proposal.getStatus() == Proposal.ProposalStatus.APPROVED) {
            throw new RuntimeException("Teklif zaten işe dönüştürülmüş");
        }
        if (proposal.getStatus() == Proposal.ProposalStatus.REJECTED) {
            throw new RuntimeException("Reddedilen teklif işe dönüştürülemez");
        }

        proposal.setStatus(Proposal.ProposalStatus.APPROVED);
        proposalRepository.save(proposal);

        // Create service ticket using shared helper
        createServiceTicketFromProposal(proposal);

        return mapToDTO(proposal);
    }

    private void recalculateTotal(Proposal proposal) {
        BigDecimal subtotal = proposal.getItems().stream()
                .map(ProposalItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxRate = proposal.getTaxRate() != null ? proposal.getTaxRate() : new BigDecimal("20");
        BigDecimal discount = proposal.getDiscount() != null ? proposal.getDiscount() : BigDecimal.ZERO;

        BigDecimal taxAmount = subtotal.multiply(taxRate).divide(new BigDecimal("100"));
        BigDecimal total = subtotal.add(taxAmount).subtract(discount);

        proposal.setTotalPrice(total);
    }

    private ProposalDTO mapToDTO(Proposal proposal) {
        String customerName = customerRepository.findById(proposal.getCustomerId())
                .map(Customer::getName)
                .orElse("Unknown");

        String preparedByName = null;
        if (proposal.getPreparedById() != null) {
            preparedByName = userRepository.findById(proposal.getPreparedById())
                    .map(User::getFullName)
                    .orElse("Unknown");
        }

        // Calculate subtotal and tax
        BigDecimal subtotal = proposal.getItems().stream()
                .map(ProposalItem::getTotalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxRate = proposal.getTaxRate() != null ? proposal.getTaxRate() : new BigDecimal("20");
        BigDecimal taxAmount = subtotal.multiply(taxRate).divide(new BigDecimal("100"));

        List<ProposalItemDTO> items = proposal.getItems().stream()
                .map(item -> ProposalItemDTO.builder()
                        .id(item.getId())
                        .inventoryId(item.getInventoryId())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitCost(isAdmin() ? item.getUnitCost() : null) // Filter for non-admins
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        return ProposalDTO.builder()
                .id(proposal.getId())
                .companyId(proposal.getCompanyId())
                .customerId(proposal.getCustomerId())
                .customerName(customerName)
                .preparedById(proposal.getPreparedById())
                .preparedByName(preparedByName)
                .status(proposal.getStatus().name())
                .validUntil(proposal.getValidUntil())
                .note(proposal.getNote())
                .title(proposal.getTitle())
                .taxRate(proposal.getTaxRate())
                .discount(proposal.getDiscount())
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .totalPrice(proposal.getTotalPrice())
                .generatedServiceTicketId(proposal.getGeneratedServiceTicketId())
                .items(items)
                .build();
    }

    private ProposalItem buildItem(Proposal proposal, Long companyId, ProposalItemDTO dto) {
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new RuntimeException("Teklif kalemi adedi sıfırdan büyük olmalıdır");
        }

        ProposalItem item = new ProposalItem();
        item.setProposal(proposal);
        item.setCompanyId(companyId);
        item.setQuantity(dto.getQuantity());
        item.setInventoryId(dto.getInventoryId());

        BigDecimal unitPrice = dto.getUnitPrice();
        if (dto.getInventoryId() != null) {
            Inventory inventory = inventoryRepository.findByIdAndCompanyId(dto.getInventoryId(), companyId)
                    .orElseThrow(() -> new RuntimeException("Seçilen envanter ürünü bulunamadı"));
            item.setDescription(inventory.getPartName());
            item.setUnitCost(defaultMoney(inventory.getBuyPrice()));
            if (unitPrice == null) {
                unitPrice = defaultMoney(inventory.getSellPrice());
            }
        } else {
            if (dto.getDescription() == null || dto.getDescription().isBlank()) {
                throw new RuntimeException("Teklif kalemi açıklaması boş olamaz");
            }
            item.setDescription(dto.getDescription().trim());
            item.setUnitCost(defaultMoney(dto.getUnitCost()));
        }

        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Teklif kalemi fiyatı negatif olamaz");
        }
        item.setUnitPrice(unitPrice);
        item.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(dto.getQuantity())));
        return item;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean isAdmin() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);
        return user != null && ("COMPANY_ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole()));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Proposal getOwnedProposal(Long id, Long companyId) {
        return proposalRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new RuntimeException("Teklif bulunamadı"));
    }

    private void validateCustomerOwnership(Long customerId, Long companyId) {
        if (customerId == null || customerRepository.findByIdAndCompanyId(customerId, companyId).isEmpty()) {
            throw new RuntimeException("Müşteri bulunamadı");
        }
    }

    private Proposal.ProposalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new RuntimeException("Teklif durumu boş olamaz");
        }
        try {
            return Proposal.ProposalStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Geçersiz teklif durumu: " + status);
        }
    }

    private void validateStatusTransition(Proposal.ProposalStatus oldStatus, Proposal.ProposalStatus newStatus) {
        if (oldStatus == newStatus) {
            return;
        }
        switch (oldStatus) {
            case DRAFT:
                if (newStatus == Proposal.ProposalStatus.SENT || newStatus == Proposal.ProposalStatus.REJECTED) return;
                break;
            case SENT:
                if (newStatus == Proposal.ProposalStatus.APPROVED || newStatus == Proposal.ProposalStatus.REJECTED) return;
                break;
            case APPROVED:
            case REJECTED:
                break;
        }
        throw new RuntimeException("Durum geçişine izin verilmiyor: " + oldStatus + " -> " + newStatus);
    }
}
