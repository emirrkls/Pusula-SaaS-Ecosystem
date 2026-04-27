package com.pusula.backend.service;

import com.pusula.backend.dto.ProposalDTO;
import com.pusula.backend.dto.ProposalItemDTO;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final ProposalItemRepository proposalItemRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ServiceTicketRepository serviceTicketRepository;

    public ProposalService(ProposalRepository proposalRepository,
            ProposalItemRepository proposalItemRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            ServiceTicketRepository serviceTicketRepository) {
        this.proposalRepository = proposalRepository;
        this.proposalItemRepository = proposalItemRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.serviceTicketRepository = serviceTicketRepository;
    }

    public List<ProposalDTO> getAllByCompany(Long companyId) {
        return proposalRepository.findByCompanyId(companyId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ProposalDTO getById(Long id) {
        return proposalRepository.findById(id)
                .map(this::mapToDTO)
                .orElse(null);
    }

    @Transactional
    public ProposalDTO create(ProposalDTO dto) {
        User currentUser = getCurrentUser();

        Proposal proposal = new Proposal();
        proposal.setCompanyId(currentUser.getCompanyId());
        proposal.setCustomerId(dto.getCustomerId());
        proposal.setPreparedById(dto.getPreparedById() != null ? dto.getPreparedById() : currentUser.getId());
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
                ProposalItem item = new ProposalItem();
                item.setProposal(saved);
                item.setCompanyId(currentUser.getCompanyId());
                item.setDescription(itemDto.getDescription());
                item.setQuantity(itemDto.getQuantity());
                item.setUnitCost(itemDto.getUnitCost());
                item.setUnitPrice(itemDto.getUnitPrice());
                item.setTotalPrice(itemDto.getUnitPrice().multiply(new BigDecimal(itemDto.getQuantity())));
                proposalItemRepository.save(item);
            }
        }

        // Recalculate total
        recalculateTotal(saved);

        return mapToDTO(proposalRepository.save(saved));
    }

    @Transactional
    public ProposalDTO update(Long id, ProposalDTO dto) {
        Proposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Teklif bulunamadı"));

        Proposal.ProposalStatus oldStatus = proposal.getStatus();
        Proposal.ProposalStatus newStatus = parseStatus(dto.getStatus());
        validateStatusTransition(oldStatus, newStatus);
        boolean shouldCreateTicket = (newStatus == Proposal.ProposalStatus.APPROVED)
                && (oldStatus != Proposal.ProposalStatus.APPROVED);

        proposal.setCustomerId(dto.getCustomerId());
        proposal.setPreparedById(dto.getPreparedById());
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
                ProposalItem item = new ProposalItem();
                item.setProposal(proposal);
                item.setCompanyId(proposal.getCompanyId());
                item.setDescription(itemDto.getDescription());
                item.setQuantity(itemDto.getQuantity());
                item.setUnitCost(itemDto.getUnitCost());
                item.setUnitPrice(itemDto.getUnitPrice());
                item.setTotalPrice(itemDto.getUnitPrice().multiply(new BigDecimal(itemDto.getQuantity())));
                proposalItemRepository.save(item);
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

        serviceTicketRepository.save(ticket);
    }

    @Transactional
    public void delete(Long id) {
        proposalRepository.deleteById(id);
    }

    @Transactional
    public ProposalDTO convertToJob(Long id) {
        Proposal proposal = proposalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Teklif bulunamadı"));

        if (proposal.getStatus() == Proposal.ProposalStatus.APPROVED) {
            throw new RuntimeException("Teklif zaten işe dönüştürülmüş");
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
                .items(items)
                .build();
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
