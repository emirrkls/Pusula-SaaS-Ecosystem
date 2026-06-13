package com.pusula.backend.service;

import com.pusula.backend.annotation.CheckQuota;
import com.pusula.backend.dto.PublicServiceRequestDTO;
import com.pusula.backend.dto.ServicePhotoDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.ServicePhotoRepository;
import com.pusula.backend.repository.ServiceUsedPartRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.repository.VehicleStockRepository;
import com.pusula.backend.entity.VehicleStock;
import com.pusula.backend.entity.ServicePhoto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ServiceTicketService {
    private static final long MAX_SERVICE_PHOTO_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB


    private static final Logger log = LoggerFactory.getLogger(ServiceTicketService.class);
    private final ZoneId businessZone;
    private final ZoneId serverZone = ZoneId.systemDefault();

    private final ServiceTicketRepository repository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final ServiceUsedPartRepository serviceUsedPartRepository;
    private final AuditLogService auditLogService;
    private final CurrentAccountRepository currentAccountRepository;
    private final VehicleStockRepository vehicleStockRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final FeatureService featureService;
    private final ServicePhotoRepository servicePhotoRepository;
    private final FileUploadService fileUploadService;

    public ServiceTicketService(ServiceTicketRepository repository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            InventoryRepository inventoryRepository,
            ServiceUsedPartRepository serviceUsedPartRepository,
            AuditLogService auditLogService,
            CurrentAccountRepository currentAccountRepository,
            VehicleStockRepository vehicleStockRepository,
            WhatsAppNotificationService whatsAppNotificationService,
            FeatureService featureService,
            ServicePhotoRepository servicePhotoRepository,
            FileUploadService fileUploadService,
            @Value("${app.business.timezone:Europe/Istanbul}") String businessTimezone) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.serviceUsedPartRepository = serviceUsedPartRepository;
        this.auditLogService = auditLogService;
        this.currentAccountRepository = currentAccountRepository;
        this.vehicleStockRepository = vehicleStockRepository;
        this.whatsAppNotificationService = whatsAppNotificationService;
        this.featureService = featureService;
        this.servicePhotoRepository = servicePhotoRepository;
        this.fileUploadService = fileUploadService;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public List<ServiceTicketDTO> getAllTickets() {
        User user = getCurrentUser();
        return repository.findByCompanyId(user.getCompanyId()).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get tickets assigned to a specific technician.
     * Used by the iOS technician flow — enriched with customer details.
     */
    public List<ServiceTicketDTO> getAssignedTickets(Long technicianId) {
        return repository.findByAssignedTechnicianId(technicianId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ServiceTicketDTO getTicketById(Long ticketId) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        if ("TECHNICIAN".equals(user.getRole())) {
            if (ticket.getAssignedTechnicianId() == null || !ticket.getAssignedTechnicianId().equals(user.getId())) {
                throw new RuntimeException("Access Denied: You can only view tickets assigned to you.");
            }
        }
        return mapToDTO(ticket);
    }

    @CheckQuota("TICKETS")
    public ServiceTicketDTO createTicket(ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = ServiceTicket.builder()
                .companyId(user.getCompanyId())
                .customerId(dto.getCustomerId())
                .assignedTechnicianId(dto.getAssignedTechnicianId())
                .status(ServiceTicket.TicketStatus.PENDING)
                .scheduledDate(dto.getScheduledDate())
                .description(dto.getDescription())
                .notes(dto.getNotes())
                .build();

        if (dto.getStatus() != null) {
            ticket.setStatus(dto.getStatus());
        }

        ServiceTicket saved = repository.save(ticket);
        featureService.incrementUsage(user.getCompanyId(), "TICKETS");

        // Log ticket creation
        auditLogService.log(
                "CREATE",
                "TICKET",
                saved.getId(),
                "Yeni servis fişi oluşturuldu: " + saved.getDescription());

        return mapToDTO(saved);
    }

    public ServiceTicketDTO updateTicket(Long id, ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(id)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        // RBAC: If user is TECHNICIAN, they can only update if assigned to them
        if ("TECHNICIAN".equals(user.getRole())) {
            if (ticket.getAssignedTechnicianId() == null || !ticket.getAssignedTechnicianId().equals(user.getId())) {
                throw new RuntimeException("Access Denied: You can only update tickets assigned to you.");
            }
            // Prevent Technician from re-assigning the ticket
            if (dto.getAssignedTechnicianId() != null
                    && !dto.getAssignedTechnicianId().equals(ticket.getAssignedTechnicianId())) {
                throw new RuntimeException("Access Denied: Technicians cannot re-assign tickets.");
            }
        }

        // Track old values for audit logging
        String oldStatus = ticket.getStatus() != null ? getStatusInTurkish(ticket.getStatus()) : null;
        Long oldTechnicianId = ticket.getAssignedTechnicianId();

        // Apply updates
        if (dto.getStatus() != null && !dto.getStatus().equals(ticket.getStatus())) {
            String newStatus = getStatusInTurkish(dto.getStatus());
            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    ticket.getId(),
                    "Durum değişti",
                    oldStatus,
                    newStatus);
            ticket.setStatus(dto.getStatus());
        }

        if (dto.getAssignedTechnicianId() != null && !dto.getAssignedTechnicianId().equals(oldTechnicianId)) {
            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    ticket.getId(),
                    "Teknisyen atandı: ID " + dto.getAssignedTechnicianId());
            ticket.setAssignedTechnicianId(dto.getAssignedTechnicianId());
        }

        if (dto.getScheduledDate() != null)
            ticket.setScheduledDate(dto.getScheduledDate());
        if (dto.getNotes() != null)
            ticket.setNotes(dto.getNotes());

        ServiceTicket saved = repository.save(ticket);
        return mapToDTO(saved);
    }

    public ServiceTicketDTO createPublicTicket(PublicServiceRequestDTO dto) {
        featureService.checkQuota(dto.getCompanyId(), "TICKETS");
        // 1. Telefon numarasını normalize et (başındaki 0 veya +90 kaldır, tutarlılık için)
        String normalizedPhone = normalizePhoneNumber(dto.getCustomerPhone());

        // 2. Müşteri bul veya oluştur (telefon numarasına göre)
        Customer customer = customerRepository.findByCompanyId(dto.getCompanyId()).stream()
                .filter(c -> c.getPhone() != null && normalizePhoneNumber(c.getPhone()).equals(normalizedPhone))
                .findFirst()
                .orElseGet(() -> {
                    Customer newCustomer = Customer.builder()
                            .companyId(dto.getCompanyId())
                            .name(dto.getCustomerName())
                            .phone(normalizedPhone)
                            .address(dto.getCustomerAddress())
                            .build();
                    log.info("Web formundan yeni müşteri oluşturuldu: {}", dto.getCustomerName());
                    return customerRepository.save(newCustomer);
                });

        // 3. Mevcut müşterinin adresini güncelle (farklı adresten talep geldiyse)
        if (dto.getCustomerAddress() != null && !dto.getCustomerAddress().equals(customer.getAddress())) {
            customer.setAddress(dto.getCustomerAddress());
            customerRepository.save(customer);
        }

        // 4. Açıklama metnini cihaz tipi bilgisiyle zenginleştir
        String enrichedDescription = buildTicketDescription(dto);

        // 5. İş emri oluştur — PENDING (Beklemede) statüsüyle
        ServiceTicket ticket = ServiceTicket.builder()
                .companyId(dto.getCompanyId())
                .customerId(customer.getId())
                .status(ServiceTicket.TicketStatus.PENDING)
                .description(enrichedDescription)
                .notes("[WEB FORMU] " + dto.getCustomerName() + " — " + normalizedPhone)
                .build();

        ServiceTicket saved = repository.save(ticket);
        featureService.incrementUsage(dto.getCompanyId(), "TICKETS");

        // 6. Audit log kaydı
        auditLogService.log(
                "CREATE",
                "TICKET",
                saved.getId(),
                "Web formundan servis talebi: " + dto.getCustomerName() + " - " + dto.getDeviceType());

        return mapToDTO(saved);
    }

    /**
     * Telefon numarasını normalize eder.
     * +905551234567 → 5551234567
     * 05551234567 → 5551234567
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("90") && cleaned.length() == 12) {
            return cleaned.substring(2); // +90 kaldır
        }
        if (cleaned.startsWith("0") && cleaned.length() == 11) {
            return cleaned.substring(1); // Baştaki 0 kaldır
        }
        return cleaned;
    }

    /**
     * İş emri açıklamasını cihaz tipi ve müşteri notu ile zenginleştirir.
     * Operatörün iş emrini ilk bakışta anlaması için yapılandırılmış format.
     */
    private String buildTicketDescription(PublicServiceRequestDTO dto) {
        StringBuilder sb = new StringBuilder();

        // Cihaz tipi etiketi
        if (dto.getDeviceType() != null && !dto.getDeviceType().isBlank()) {
            sb.append("[").append(dto.getDeviceType().toUpperCase()).append("] ");
        }

        // Müşteri açıklaması
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            sb.append(dto.getDescription());
        } else {
            sb.append("Web formu üzerinden servis talebi");
        }

        return sb.toString();
    }

    public ServiceTicketDTO assignTechnician(Long ticketId, Long technicianId) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        User technician = userRepository.findById(technicianId)
                .filter(u -> u.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Technician not found or access denied"));

        ticket.setAssignedTechnicianId(technician.getId());
        ticket.setStatus(ServiceTicket.TicketStatus.ASSIGNED);

        ServiceTicket saved = repository.save(ticket);

        // Log technician assignment
        auditLogService.log(
                "UPDATE",
                "TICKET",
                saved.getId(),
                "Teknisyen atandı: " + technician.getFullName());

        return mapToDTO(saved);
    }

    @Transactional
    public ServiceUsedPartDTO addUsedPart(Long ticketId, ServiceUsedPartDTO dto) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        com.pusula.backend.entity.Inventory inventory = inventoryRepository.findById(dto.getInventoryId())
                .filter(i -> i.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));

        Long sourceVehicleId = null;
        int quantityNeeded = dto.getQuantityUsed();

        // Check if we should use vehicle stock
        if (dto.getSourceVehicleId() != null) {
            VehicleStock vehicleStock = vehicleStockRepository
                    .findByVehicleIdAndInventoryId(dto.getSourceVehicleId(), dto.getInventoryId())
                    .orElse(null);

            if (vehicleStock != null && vehicleStock.getQuantity() >= quantityNeeded) {
                // Deduct from vehicle stock
                vehicleStock.setQuantity(vehicleStock.getQuantity() - quantityNeeded);
                vehicleStockRepository.save(vehicleStock);
                sourceVehicleId = dto.getSourceVehicleId();

                // Also deduct from main inventory (vehicle parts are part of total)
                inventory.setQuantity(inventory.getQuantity() - quantityNeeded);
                inventoryRepository.save(inventory);
            } else {
                // Vehicle doesn't have enough, fall back to main inventory
                if (inventory.getQuantity() < quantityNeeded) {
                    throw new RuntimeException("Insufficient stock for item: " + inventory.getPartName());
                }
                inventory.setQuantity(inventory.getQuantity() - quantityNeeded);
                inventoryRepository.save(inventory);
            }
        } else {
            // No vehicle specified, use main inventory directly
            if (inventory.getQuantity() < quantityNeeded) {
                throw new RuntimeException("Insufficient stock for item: " + inventory.getPartName());
            }
            inventory.setQuantity(inventory.getQuantity() - quantityNeeded);
            inventoryRepository.save(inventory);
        }

        // Create Used Part record with source tracking
        com.pusula.backend.entity.ServiceUsedPart usedPart = com.pusula.backend.entity.ServiceUsedPart.builder()
                .companyId(currentUser.getCompanyId())
                .serviceTicket(ticket)
                .inventory(inventory)
                .quantityUsed(dto.getQuantityUsed())
                .sellingPriceSnapshot(inventory.getSellPrice())
                .sourceVehicleId(sourceVehicleId)
                .build();

        com.pusula.backend.entity.ServiceUsedPart saved = serviceUsedPartRepository.save(usedPart);

        return new ServiceUsedPartDTO(
                saved.getId(),
                saved.getServiceTicket().getId(),
                saved.getInventory().getId(),
                saved.getInventory().getPartName(),
                saved.getQuantityUsed(),
                saved.getSellingPriceSnapshot(),
                saved.getSourceVehicleId());
    }

    public List<ServiceUsedPartDTO> getUsedParts(Long ticketId) {
        User currentUser = getCurrentUser();
        // Verify access to ticket
        repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        return serviceUsedPartRepository.findByServiceTicketId(ticketId).stream()
                .map(part -> new ServiceUsedPartDTO(
                        part.getId(),
                        part.getServiceTicket().getId(),
                        part.getInventory().getId(),
                        part.getInventory().getPartName(),
                        part.getQuantityUsed(),
                        part.getSellingPriceSnapshot(),
                        part.getSourceVehicleId()))
                .collect(Collectors.toList());
    }

    public ServiceTicketDTO completeService(Long ticketId, BigDecimal collectedAmount, PaymentMethod paymentMethod) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setCollectedAmount(collectedAmount);
        ticket.setPaymentMethod(paymentMethod != null ? paymentMethod : PaymentMethod.CASH);

        // If CURRENT_ACCOUNT, create/update debt record (not liquid cash)
        if (paymentMethod == PaymentMethod.CURRENT_ACCOUNT && ticket.getCustomerId() != null) {
            // Fetch customer entity
            Customer customer = customerRepository.findById(ticket.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));

            CurrentAccount account = currentAccountRepository
                    .findByCustomerId(ticket.getCustomerId())
                    .orElseGet(() -> {
                        CurrentAccount newAccount = CurrentAccount.builder()
                                .companyId(ticket.getCompanyId())
                                .customer(customer)
                                .balance(BigDecimal.ZERO)
                                .build();
                        return currentAccountRepository.save(newAccount);
                    });

            // ADD to debt (positive balance = customer owes us)
            account.setBalance(account.getBalance().add(collectedAmount));
            currentAccountRepository.save(account);
        }

        ServiceTicket saved = repository.save(ticket);

        // WhatsApp notification — async fire-and-forget
        try {
            BigDecimal remainingDebt = BigDecimal.ZERO;
            if (paymentMethod == PaymentMethod.CURRENT_ACCOUNT) {
                remainingDebt = collectedAmount;
            }
            whatsAppNotificationService.notifyServiceCompleted(ticketId, collectedAmount, remainingDebt);
        } catch (Exception e) {
            // Don't fail the service completion if notification fails
            log.warn("WhatsApp notification failed (non-blocking): {}", e.getMessage());
        }

        return mapToDTO(saved);
    }

    @Transactional
    public ServiceTicketDTO cancelService(Long ticketId) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        // Return all used parts back to inventory
        List<com.pusula.backend.entity.ServiceUsedPart> usedParts = serviceUsedPartRepository
                .findByServiceTicketId(ticketId);

        for (com.pusula.backend.entity.ServiceUsedPart usedPart : usedParts) {
            com.pusula.backend.entity.Inventory inventory = usedPart.getInventory();
            // Add the quantity back to main inventory
            inventory.setQuantity(inventory.getQuantity() + usedPart.getQuantityUsed());
            inventoryRepository.save(inventory);

            // If part came from a vehicle, also restore vehicle stock
            if (usedPart.getSourceVehicleId() != null) {
                VehicleStock vehicleStock = vehicleStockRepository
                        .findByVehicleIdAndInventoryId(usedPart.getSourceVehicleId(), inventory.getId())
                        .orElse(null);
                if (vehicleStock != null) {
                    vehicleStock.setQuantity(vehicleStock.getQuantity() + usedPart.getQuantityUsed());
                    vehicleStockRepository.save(vehicleStock);
                }
            }

            // Log the return
            auditLogService.log(
                    "RETURN",
                    "INVENTORY",
                    inventory.getId(),
                    "Parça iade edildi (iptal): " + inventory.getPartName() + " x" + usedPart.getQuantityUsed());
        }

        // Delete the used parts records (soft delete)
        for (com.pusula.backend.entity.ServiceUsedPart usedPart : usedParts) {
            serviceUsedPartRepository.delete(usedPart);
        }

        ticket.setStatus(ServiceTicket.TicketStatus.CANCELLED);

        // Log cancellation
        auditLogService.log(
                "CANCEL",
                "TICKET",
                ticket.getId(),
                "Servis fişi iptal edildi");

        return mapToDTO(repository.save(ticket));
    }

    public ServiceTicketDTO createFollowUpTicket(Long originalTicketId) {
        User currentUser = getCurrentUser();

        // Fetch original ticket
        ServiceTicket originalTicket = repository.findById(originalTicketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Original ticket not found or access denied"));

        // Verify original ticket is COMPLETED
        if (originalTicket.getStatus() != ServiceTicket.TicketStatus.COMPLETED) {
            throw new RuntimeException("Can only create follow-up for completed tickets");
        }

        // Create new ticket
        ServiceTicket followUpTicket = new ServiceTicket();
        followUpTicket.setCompanyId(currentUser.getCompanyId());
        followUpTicket.setCustomerId(originalTicket.getCustomerId());
        followUpTicket.setDescription("RECALL: " + originalTicket.getDescription());
        followUpTicket.setStatus(ServiceTicket.TicketStatus.PENDING);
        followUpTicket.setParentTicketId(originalTicketId);

        // Check if warranty call (completed less than 30 days ago)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        boolean isWarranty = originalTicket.getUpdatedAt() != null &&
                originalTicket.getUpdatedAt().isAfter(thirtyDaysAgo);
        followUpTicket.setWarrantyCall(isWarranty);

        // Save and return
        ServiceTicket saved = repository.save(followUpTicket);
        return mapToDTO(saved);
    }

    private ServiceTicketDTO mapToDTO(ServiceTicket ticket) {
        // Fetch customer details for technician field view
        String customerName = null;
        String customerPhone = null;
        String customerAddress = null;
        String customerCoordinates = null;
        BigDecimal customerBalance = null;

        if (ticket.getCustomerId() != null) {
            var customerOpt = customerRepository.findById(ticket.getCustomerId());
            if (customerOpt.isPresent()) {
                var customer = customerOpt.get();
                customerName = customer.getName();
                customerPhone = customer.getPhone();
                customerAddress = customer.getAddress();
                customerCoordinates = customer.getCoordinates();

                // Calculate outstanding balance from current account
                try {
                    customerBalance = currentAccountRepository
                            .findByCustomerId(ticket.getCustomerId())
                            .map(ca -> ca.getBalance() != null ? ca.getBalance() : BigDecimal.ZERO)
                            .orElse(BigDecimal.ZERO);
                } catch (Exception e) {
                    customerBalance = BigDecimal.ZERO;
                }
            }
        }

        // Fetch assigned technician name
        String assignedTechnicianName = null;
        if (ticket.getAssignedTechnicianId() != null) {
            assignedTechnicianName = userRepository.findById(ticket.getAssignedTechnicianId())
                    .map(User::getFullName)
                    .orElse(null);
        }

        ServiceTicketDTO dto = ServiceTicketDTO.builder()
                .id(ticket.getId())
                .customerId(ticket.getCustomerId())
                .customerName(customerName)
                .assignedTechnicianId(ticket.getAssignedTechnicianId())
                .status(ticket.getStatus())
                .scheduledDate(ticket.getScheduledDate())
                .description(ticket.getDescription())
                .notes(ticket.getNotes())
                .collectedAmount(ticket.getCollectedAmount())
                .createdAt(ticket.getCreatedAt())
                .parentTicketId(ticket.getParentTicketId())
                .isWarrantyCall(ticket.isWarrantyCall())
                .paymentMethod(ticket.getPaymentMethod())
                .build();

        // Set enriched customer details for mobile field view
        dto.setCustomerPhone(customerPhone);
        dto.setCustomerAddress(customerAddress);
        dto.setCustomerCoordinates(customerCoordinates);
        dto.setCustomerBalance(customerBalance);
        dto.setAssignedTechnicianName(assignedTechnicianName);

        return dto;
    }

    /**
     * Save a PencilKit signature image to local storage.
     * Strategy: local filesystem (same as desktop app), avoiding S3 costs.
     * Path: /uploads/signatures/{companyId}/{ticketId}.png
     */
    public String saveSignature(Long ticketId, String signatureBase64) {
        User user = getCurrentUser();
        Long companyId = user.getCompanyId();

        try {
            Path dir = Paths.get("uploads", "signatures", companyId.toString());
            Files.createDirectories(dir);

            byte[] imageBytes = Base64.getDecoder().decode(signatureBase64);
            Path filePath = dir.resolve(ticketId + ".png");

            try (OutputStream os = Files.newOutputStream(filePath)) {
                os.write(imageBytes);
            }

            return "/uploads/signatures/" + companyId + "/" + ticketId + ".png";
        } catch (IOException e) {
            throw new RuntimeException("İmza kaydedilemedi: " + e.getMessage(), e);
        }
    }

    public ServicePhotoDTO uploadServicePhoto(Long ticketId, ServicePhoto.PhotoType type, MultipartFile file) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        validateServicePhotoUpload(file);

        try {
            String relativePath = fileUploadService.uploadServicePhoto(
                    user.getCompanyId(),
                    ticket.getId(),
                    type.name(),
                    file
            );
            String url = "/uploads/" + relativePath;
            ServicePhoto photo = ServicePhoto.builder()
                    .ticketId(ticket.getId())
                    .url(url)
                    .type(type)
                    .build();
            ServicePhoto saved = servicePhotoRepository.save(photo);
            return mapPhotoToDTO(saved);
        } catch (IOException e) {
            throw new RuntimeException("Servis görseli yüklenemedi: " + e.getMessage(), e);
        }
    }

    public List<ServicePhotoDTO> getServicePhotos(Long ticketId) {
        User user = getCurrentUser();
        repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        return servicePhotoRepository.findByTicketIdOrderByUploadedAtDesc(ticketId).stream()
                .map(this::mapPhotoToDTO)
                .collect(Collectors.toList());
    }

    public List<ServicePhotoDTO> getCompanyServicePhotos(
            ServicePhoto.PhotoType type,
            Long ticketId,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit) {
        User user = getCurrentUser();
        List<Long> companyTicketIds = repository.findByCompanyId(user.getCompanyId()).stream()
                .map(ServiceTicket::getId)
                .collect(Collectors.toList());

        if (companyTicketIds.isEmpty()) {
            return List.of();
        }

        List<ServicePhoto> photos = servicePhotoRepository.findByTicketIdInOrderByUploadedAtDesc(companyTicketIds);
        return photos.stream()
                .filter(photo -> type == null || photo.getType() == type)
                .filter(photo -> ticketId == null || photo.getTicketId().equals(ticketId))
                .filter(photo -> {
                    LocalDate photoDate = toBusinessDate(photo.getUploadedAt());
                    if (photoDate == null) return false;
                    boolean afterStart = startDate == null || !photoDate.isBefore(startDate);
                    boolean beforeEnd = endDate == null || !photoDate.isAfter(endDate);
                    return afterStart && beforeEnd;
                })
                .limit(limit != null && limit > 0 ? limit : Long.MAX_VALUE)
                .map(this::mapPhotoToDTO)
                .collect(Collectors.toList());
    }

    public void deleteServicePhoto(Long ticketId, Long photoId) {
        User user = getCurrentUser();
        repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        ServicePhoto photo = servicePhotoRepository.findById(photoId)
                .filter(p -> p.getTicketId().equals(ticketId))
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        deletePhotoFileIfExists(photo.getUrl());
        servicePhotoRepository.delete(photo);
    }

    private ServicePhotoDTO mapPhotoToDTO(ServicePhoto photo) {
        return new ServicePhotoDTO(
                photo.getId(),
                photo.getTicketId(),
                photo.getUrl(),
                photo.getType(),
                photo.getUploadedAt()
        );
    }

    private void validateServicePhotoUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Yüklenecek görsel bulunamadı.");
        }
        if (file.getSize() > MAX_SERVICE_PHOTO_SIZE_BYTES) {
            throw new RuntimeException("Görsel boyutu 5 MB'dan büyük olamaz.");
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new RuntimeException("Görsel tipi doğrulanamadı.");
        }
        boolean allowed = contentType.equalsIgnoreCase("image/jpeg")
                || contentType.equalsIgnoreCase("image/jpg")
                || contentType.equalsIgnoreCase("image/png")
                || contentType.equalsIgnoreCase("image/webp");
        if (!allowed) {
            throw new RuntimeException("Sadece JPG, PNG veya WEBP formatı desteklenir.");
        }
    }

    private void deletePhotoFileIfExists(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return;
        }
        try {
            String relative = url.substring(1); // uploads/...
            Path filePath = Paths.get(relative).normalize();
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("Service photo file could not be deleted for url={}", url, e);
        }
    }

    private LocalDate toBusinessDate(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(serverZone).withZoneSameInstant(businessZone).toLocalDate();
    }

    private String getStatusInTurkish(ServiceTicket.TicketStatus status) {
        switch (status) {
            case PENDING:
                return "Beklemede";
            case ASSIGNED:
                return "Atandı";
            case IN_PROGRESS:
                return "Devam Ediyor";
            case COMPLETED:
                return "Tamamlandı";
            case CANCELLED:
                return "İptal Edildi";
            default:
                return status.toString();
        }
    }
}
