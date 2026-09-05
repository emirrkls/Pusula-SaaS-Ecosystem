package com.pusula.backend.service;

import com.pusula.backend.annotation.CheckQuota;
import com.pusula.backend.dto.PublicServiceRequestDTO;
import com.pusula.backend.dto.ServicePhotoDTO;
import com.pusula.backend.dto.ServicePhotoPageDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.dto.ServiceTicketRescheduleRequest;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.CurrentAccountTransaction;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.Notification;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceTicketReschedule;
import com.pusula.backend.entity.User;
import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.ServiceTicketRescheduleRepository;
import com.pusula.backend.repository.ServicePhotoRepository;
import com.pusula.backend.repository.ServiceUsedPartRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.repository.VehicleStockRepository;
import com.pusula.backend.entity.VehicleStock;
import com.pusula.backend.entity.ServicePhoto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ServiceTicketService {
    private static final long MAX_SERVICE_PHOTO_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB


    private static final Logger log = LoggerFactory.getLogger(ServiceTicketService.class);
    private final ZoneId businessZone;
    private final ZoneId serverZone = ZoneId.systemDefault();

    private final ServiceTicketRepository repository;
    private final ServiceTicketRescheduleRepository rescheduleRepository;
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
    private final ApplicationEventPublisher eventPublisher;
    private final FinanceService financeService;
    private final UploadUrlSigner uploadUrlSigner;
    private final CurrentAccountLedgerService currentAccountLedgerService;
    private final AdminNotificationService adminNotificationService;

    public ServiceTicketService(ServiceTicketRepository repository,
            ServiceTicketRescheduleRepository rescheduleRepository,
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
            ApplicationEventPublisher eventPublisher,
            FinanceService financeService, UploadUrlSigner uploadUrlSigner,
            CurrentAccountLedgerService currentAccountLedgerService,
            AdminNotificationService adminNotificationService,
            @Value("${app.business.timezone:Europe/Istanbul}") String businessTimezone) {
        this.repository = repository;
        this.rescheduleRepository = rescheduleRepository;
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
        this.eventPublisher = eventPublisher;
        this.financeService = financeService;
        this.uploadUrlSigner = uploadUrlSigner;
        this.currentAccountLedgerService = currentAccountLedgerService;
        this.adminNotificationService = adminNotificationService;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private boolean isAdmin(User user) {
        return "COMPANY_ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole());
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
    @Transactional
    public ServiceTicketDTO createTicket(ServiceTicketDTO dto) {
        User user = getCurrentUser();
        validateScheduleWindow(dto.getScheduledDate(), dto.getScheduledEndDate());
        if (dto.getCustomerId() == null) {
            throw new IllegalArgumentException("Müşteri seçimi zorunludur.");
        }
        customerRepository.findByIdAndCompanyId(dto.getCustomerId(), user.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı veya erişim reddedildi."));
        if (dto.getStatus() == ServiceTicket.TicketStatus.COMPLETED
                || dto.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
            throw new IllegalArgumentException("Yeni servis kaydı kapalı durumla oluşturulamaz.");
        }
        User assignedTechnician = dto.getAssignedTechnicianId() == null
                ? null
                : requireTechnician(dto.getAssignedTechnicianId(), user.getCompanyId());
        ServiceTicket ticket = ServiceTicket.builder()
                .companyId(user.getCompanyId())
                .customerId(dto.getCustomerId())
                .assignedTechnicianId(dto.getAssignedTechnicianId())
                .status(assignedTechnician == null
                        ? ServiceTicket.TicketStatus.PENDING
                        : ServiceTicket.TicketStatus.ASSIGNED)
                .scheduledDate(dto.getScheduledDate())
                .description(dto.getDescription())
                .notes(dto.getNotes())
                .build();
        ticket.setScheduledEndDate(dto.getScheduledEndDate());
        if (isAdmin(user)) {
            ticket.setTechnicianPrivateNote(normalizePrivateNote(dto.getTechnicianPrivateNote()));
        }

        if (assignedTechnician == null && dto.getStatus() != null) {
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

        if (assignedTechnician != null) {
            publishAssignment(saved, assignedTechnician.getId());
        } else {
            notifyAdmins(saved, "Yeni atanmamış servis", ticketSummary(saved),
                    Notification.NotificationType.INFO, Notification.NotificationCategory.NEW_SERVICE, null);
        }

        // Customer notification is non-blocking from the business flow: a Meta/API
        // failure must never prevent the service ticket from being created.
        try {
            whatsAppNotificationService.notifyServiceCreated(saved.getId());
        } catch (Exception e) {
            log.warn("WhatsApp creation notification failed (non-blocking): {}", e.getMessage());
        }

        return mapToDTO(saved);
    }

    @Transactional
    public ServiceTicketDTO updateTicket(Long id, ServiceTicketDTO dto) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(id)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        if (ticket.getStatus() == ServiceTicket.TicketStatus.COMPLETED
                || ticket.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Kapanmış servis genel güncelleme ile değiştirilemez. Önce güvenli yeniden açma işlemini kullanın.");
        }

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
            if (dto.getScheduledDate() != null
                    && (!Objects.equals(dto.getScheduledDate(), ticket.getScheduledDate())
                        || !Objects.equals(dto.getScheduledEndDate(), ticket.getScheduledEndDate()))) {
                throw new AccessDeniedException("Randevu değişikliği için gerekçeli yeniden planlama işlemini kullanın.");
            }
            if (dto.getStatus() != null && dto.getStatus() != ticket.getStatus()
                    && !(ticket.getStatus() == ServiceTicket.TicketStatus.ASSIGNED
                        && dto.getStatus() == ServiceTicket.TicketStatus.IN_PROGRESS)) {
                throw new AccessDeniedException("Teknisyen yalnızca atanmış işi İşlemde durumuna alabilir.");
            }
        }

        // Track old values for audit logging
        String oldStatus = ticket.getStatus() != null ? getStatusInTurkish(ticket.getStatus()) : null;
        Long oldTechnicianId = ticket.getAssignedTechnicianId();
        LocalDateTime oldScheduledDate = ticket.getScheduledDate();
        LocalDateTime oldScheduledEndDate = ticket.getScheduledEndDate();

        // Apply updates
        if (dto.getStatus() != null && !dto.getStatus().equals(ticket.getStatus())) {
            if (dto.getStatus() == ServiceTicket.TicketStatus.COMPLETED) {
                throw new IllegalArgumentException("Servis kapatma işlemi tahsilat bilgileriyle yapılmalıdır.");
            }
            if (dto.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
                throw new IllegalArgumentException("Servis iptali parça iade akışıyla yapılmalıdır.");
            }
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

        Long newTechnicianId = null;
        if (dto.getAssignedTechnicianId() != null && !dto.getAssignedTechnicianId().equals(oldTechnicianId)) {
            User technician = requireTechnician(dto.getAssignedTechnicianId(), user.getCompanyId());
            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    ticket.getId(),
                    "Teknisyen atandı: ID " + dto.getAssignedTechnicianId());
            ticket.setAssignedTechnicianId(technician.getId());
            ticket.setStatus(ServiceTicket.TicketStatus.ASSIGNED);
            newTechnicianId = technician.getId();
        }

        if (dto.getScheduledDate() != null) {
            validateScheduleWindow(dto.getScheduledDate(), dto.getScheduledEndDate());
            ticket.setScheduledDate(dto.getScheduledDate());
            ticket.setScheduledEndDate(dto.getScheduledEndDate());
        }
        if (dto.getNotes() != null)
            ticket.setNotes(dto.getNotes());
        if (dto.getTechnicianPrivateNote() != null) {
            String requestedPrivateNote = normalizePrivateNote(dto.getTechnicianPrivateNote());
            if (!isAdmin(user) && !Objects.equals(requestedPrivateNote, ticket.getTechnicianPrivateNote())) {
                throw new AccessDeniedException("Teknisyene özel notu yalnızca işletme yöneticisi değiştirebilir.");
            }
            if (isAdmin(user)) {
                ticket.setTechnicianPrivateNote(requestedPrivateNote);
            }
        }

        boolean scheduleChanged = !Objects.equals(oldScheduledDate, ticket.getScheduledDate())
                || !Objects.equals(oldScheduledEndDate, ticket.getScheduledEndDate());
        if (newTechnicianId != null || scheduleChanged) {
            ticket.setAssignmentNotificationSentAt(null);
        }

        ServiceTicket saved = repository.save(ticket);
        if (newTechnicianId != null || (scheduleChanged && saved.getAssignedTechnicianId() != null)) {
            publishAssignment(saved, saved.getAssignedTechnicianId());
        }
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
                    featureService.checkQuota(dto.getCompanyId(), "CUSTOMERS");
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

        notifyAdmins(saved, "Yeni web servis talebi", ticketSummary(saved),
                Notification.NotificationType.INFO, Notification.NotificationCategory.NEW_SERVICE, null);

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

    @Transactional
    public ServiceTicketDTO assignTechnician(Long ticketId, Long technicianId) {
        return assignTechnician(ticketId, technicianId, null, null);
    }

    @Transactional
    public ServiceTicketDTO assignTechnician(Long ticketId, Long technicianId,
            LocalDateTime scheduledDate, LocalDateTime scheduledEndDate) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        User technician = requireTechnician(technicianId, currentUser.getCompanyId());

        boolean changed = !Objects.equals(ticket.getAssignedTechnicianId(), technician.getId());
        if (scheduledDate != null) {
            validateScheduleWindow(scheduledDate, scheduledEndDate);
            boolean scheduleChanged = !Objects.equals(ticket.getScheduledDate(), scheduledDate)
                    || !Objects.equals(ticket.getScheduledEndDate(), scheduledEndDate);
            ticket.setScheduledDate(scheduledDate);
            ticket.setScheduledEndDate(scheduledEndDate);
            changed = changed || scheduleChanged;
        }

        ticket.setAssignedTechnicianId(technician.getId());
        ticket.setStatus(ServiceTicket.TicketStatus.ASSIGNED);
        if (changed) {
            ticket.setAssignmentNotificationSentAt(null);
        }

        ServiceTicket saved = repository.save(ticket);

        if (changed) {
            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    saved.getId(),
                    "Teknisyen atandı: " + technician.getFullName());
            publishAssignment(saved, technician.getId());
        }

        return mapToDTO(saved);
    }

    @Transactional
    public ServiceTicketDTO rescheduleTicket(Long ticketId, ServiceTicketRescheduleRequest request) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findByIdAndCompanyIdForUpdate(ticketId, user.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Servis fişi bulunamadı veya erişim reddedildi."));
        requireOpenAssignedTicketAccess(ticket, user);
        if (request == null || request.getScheduledDate() == null) {
            throw new IllegalArgumentException("Yeni randevu tarihi ve saati zorunludur.");
        }
        if (!request.getScheduledDate().isAfter(LocalDateTime.now(businessZone))) {
            throw new IllegalArgumentException("Yeni randevu gelecekte bir tarih olmalıdır.");
        }
        validateScheduleWindow(request.getScheduledDate(), request.getScheduledEndDate());
        if (request.getReason() == null) {
            throw new IllegalArgumentException("Yeniden planlama nedeni seçilmelidir.");
        }
        String note = request.getNote() == null ? "" : request.getNote().trim();
        if (note.length() < 5) {
            throw new IllegalArgumentException("Yeniden planlama açıklaması en az 5 karakter olmalıdır.");
        }
        if (note.length() > 1000) {
            throw new IllegalArgumentException("Yeniden planlama açıklaması 1000 karakterden uzun olamaz.");
        }
        if (Objects.equals(ticket.getScheduledDate(), request.getScheduledDate())
                && Objects.equals(ticket.getScheduledEndDate(), request.getScheduledEndDate())) {
            throw new IllegalArgumentException("Yeni randevu mevcut randevudan farklı olmalıdır.");
        }

        LocalDateTime oldStart = ticket.getScheduledDate();
        LocalDateTime oldEnd = ticket.getScheduledEndDate();
        LocalDateTime changedAt = LocalDateTime.now(businessZone);

        ServiceTicketReschedule event = new ServiceTicketReschedule();
        event.setCompanyId(ticket.getCompanyId());
        event.setServiceTicketId(ticket.getId());
        event.setOldScheduledDate(oldStart);
        event.setOldScheduledEndDate(oldEnd);
        event.setNewScheduledDate(request.getScheduledDate());
        event.setNewScheduledEndDate(request.getScheduledEndDate());
        event.setReason(request.getReason());
        event.setNote(note);
        event.setChangedByUserId(user.getId());
        event.setChangedByName(user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername() : user.getFullName().trim());
        rescheduleRepository.save(event);

        ticket.setScheduledDate(request.getScheduledDate());
        ticket.setScheduledEndDate(request.getScheduledEndDate());
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        ticket.setWorkProgressReason(request.getReason());
        ticket.setWorkProgressNote(note);
        ticket.setLastRescheduledAt(changedAt);
        ticket.setAssignmentNotificationSentAt(null);
        ServiceTicket saved = repository.save(ticket);

        String oldValue = formatSchedule(oldStart, oldEnd);
        String newValue = formatSchedule(saved.getScheduledDate(), saved.getScheduledEndDate());
        auditLogService.log("RESCHEDULE", "TICKET", saved.getId(),
                "İş yeniden planlandı · " + getProgressReasonInTurkish(request.getReason()) + " · " + note,
                oldValue, newValue);
        publishAssignment(saved, saved.getAssignedTechnicianId());
        if ("TECHNICIAN".equals(user.getRole())) {
            notifyAdmins(saved, "İş yeniden planlandı",
                    "#" + saved.getId() + " · " + displayName(user) + " · "
                            + getProgressReasonInTurkish(request.getReason()) + " · " + formatSchedule(saved.getScheduledDate(), saved.getScheduledEndDate()),
                    Notification.NotificationType.WARNING, Notification.NotificationCategory.SERVICE_RESCHEDULED,
                    user.getId());
        }
        return mapToDTO(saved);
    }

    @Transactional
    public ServiceTicketDTO resumeTicket(Long ticketId) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findByIdAndCompanyIdForUpdate(ticketId, user.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Servis fişi bulunamadı veya erişim reddedildi."));
        requireOpenAssignedTicketAccess(ticket, user);
        if (ticket.getWorkProgressReason() == null) {
            throw new IllegalStateException("Bu iş için aktif bir bekleme/yeniden planlama kaydı bulunmuyor.");
        }
        String previousReason = getProgressReasonInTurkish(ticket.getWorkProgressReason());
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        ticket.setWorkProgressReason(null);
        ticket.setWorkProgressNote(null);
        ServiceTicket saved = repository.save(ticket);
        auditLogService.log("RESUME", "TICKET", saved.getId(), "İşe devam edildi", previousReason, "İşlemde");
        return mapToDTO(saved);
    }

    private void requireOpenAssignedTicketAccess(ServiceTicket ticket, User user) {
        if (ticket.getStatus() == ServiceTicket.TicketStatus.COMPLETED
                || ticket.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
            throw new IllegalStateException("Kapanmış servis yeniden planlanamaz.");
        }
        if (ticket.getAssignedTechnicianId() == null) {
            throw new IllegalStateException("Yeniden planlama için servise teknisyen atanmış olmalıdır.");
        }
        if (!isAdmin(user) && !("TECHNICIAN".equals(user.getRole())
                && Objects.equals(ticket.getAssignedTechnicianId(), user.getId()))) {
            throw new AccessDeniedException("Yalnızca atanmış teknisyen veya işletme yöneticisi yeniden planlayabilir.");
        }
    }

    private String formatSchedule(LocalDateTime start, LocalDateTime end) {
        if (start == null) return "Planlanmamış";
        var formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return end == null ? start.format(formatter) : start.format(formatter) + " - " + end.format(formatter);
    }

    @Transactional
    public ServiceTicketDTO reopenCompletedService(Long ticketId) {
        User currentUser = getCurrentUser();
        if (!isAdmin(currentUser)) {
            throw new AccessDeniedException("Servisi yalnızca şirket yöneticisi yeniden açabilir.");
        }

        ServiceTicket ticket = repository.findByIdAndCompanyIdForUpdate(ticketId, currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Servis fişi bulunamadı veya erişim reddedildi."));
        if (ticket.getStatus() != ServiceTicket.TicketStatus.COMPLETED || ticket.isCurrentAccountPayment()) {
            throw new IllegalStateException("Yalnızca tamamlanmış normal servis fişleri yeniden açılabilir.");
        }

        BigDecimal previousOutstanding = ticket.getOutstandingAmount() != null
                ? ticket.getOutstandingAmount().max(BigDecimal.ZERO)
                : (ticket.getPaymentMethod() == PaymentMethod.CURRENT_ACCOUNT
                        ? ticket.getEffectiveInvoiceTotal().max(BigDecimal.ZERO)
                        : BigDecimal.ZERO);
        if (previousOutstanding.signum() > 0) {
            CurrentAccount account = currentAccountRepository
                    .findByCustomerIdAndCompanyIdForUpdate(ticket.getCustomerId(), ticket.getCompanyId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Fişin oluşturduğu cari borç bulunamadığı için güvenli biçimde yeniden açılamıyor."));
            BigDecimal balance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            if (balance.compareTo(previousOutstanding) < 0) {
                throw new IllegalStateException(
                        "Bu fişin cari borcuna ödeme yapılmış. Cari bakiye düzeltilmeden fiş yeniden açılamaz.");
            }
            account.setBalance(balance.subtract(previousOutstanding));
            currentAccountRepository.save(account);
            currentAccountLedgerService.record(account, CurrentAccountTransaction.TransactionType.REVERSAL,
                    previousOutstanding.negate(), LocalDate.now(businessZone),
                    "Yeniden açılan servis fişi #" + ticket.getId(), null,
                    "SERVICE_TICKET_REOPEN", ticket.getId());
        }

        String previousStatus = getStatusInTurkish(ticket.getStatus());
        ticket.setStatus(ServiceTicket.TicketStatus.IN_PROGRESS);
        ticket.setReopenedAt(LocalDateTime.now(businessZone));
        ServiceTicket saved = repository.save(ticket);
        if (saved.getCompletedAt() != null) {
            financeService.reconcileClosedDay(saved.getCompanyId(), saved.getCompletedAt().toLocalDate());
        }
        auditLogService.log("REOPEN", "TICKET", saved.getId(),
                "Servis fişi yönetici tarafından yeniden açıldı", previousStatus, "Yeniden Açıldı");
        return mapToDTO(saved);
    }

    @Transactional
    public List<ServiceTicketDTO> assignTechnicianBulk(List<Long> ticketIds, Long technicianId) {
        if (technicianId == null) {
            throw new IllegalArgumentException("Teknisyen seçilmelidir.");
        }
        if (ticketIds == null || ticketIds.isEmpty()) {
            throw new IllegalArgumentException("En az bir servis fişi seçilmelidir.");
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(ticketIds);
        if (uniqueIds.contains(null)) {
            throw new IllegalArgumentException("Geçersiz servis fişi seçimi.");
        }
        if (uniqueIds.size() > 200) {
            throw new IllegalArgumentException("Tek işlemde en fazla 200 servis fişi atanabilir.");
        }

        User currentUser = getCurrentUser();
        User technician = requireTechnician(technicianId, currentUser.getCompanyId());
        List<ServiceTicket> tickets = uniqueIds.stream()
                .sorted()
                .map(id -> repository.findByIdAndCompanyIdForUpdate(id, currentUser.getCompanyId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Servis fişi bulunamadı veya erişim reddedildi: " + id)))
                .toList();

        for (ServiceTicket ticket : tickets) {
            if (ticket.getAssignedTechnicianId() != null
                    || ticket.getStatus() != ServiceTicket.TicketStatus.PENDING) {
                throw new IllegalStateException(
                        "Yalnızca atama bekleyen açık servis fişleri toplu atanabilir: " + ticket.getId());
            }
        }

        return tickets.stream().map(ticket -> {
            ticket.setAssignedTechnicianId(technician.getId());
            ticket.setStatus(ServiceTicket.TicketStatus.ASSIGNED);
            ticket.setAssignmentNotificationSentAt(null);
            ServiceTicket saved = repository.save(ticket);
            auditLogService.log("UPDATE", "TICKET", saved.getId(),
                    "Toplu teknisyen ataması: " + technician.getFullName());
            publishAssignment(saved, technician.getId());
            return mapToDTO(saved);
        }).toList();
    }

    private User requireTechnician(Long technicianId, Long companyId) {
        return userRepository.findByIdAndCompanyId(technicianId, companyId)
                .filter(user -> "TECHNICIAN".equals(user.getRole()))
                .orElseThrow(() -> new IllegalArgumentException("Technician not found, wrong tenant, or invalid role"));
    }

    private void publishAssignment(ServiceTicket ticket, Long technicianId) {
        eventPublisher.publishEvent(new TicketAssignedEvent(ticket.getCompanyId(), technicianId, ticket.getId()));
    }

    private void validateScheduleWindow(LocalDateTime scheduledDate, LocalDateTime scheduledEndDate) {
        if (scheduledEndDate != null && scheduledDate == null) {
            throw new IllegalArgumentException("Bitiş saati için planlanan başlangıç zamanı seçilmelidir.");
        }
        if (scheduledEndDate != null && !scheduledEndDate.isAfter(scheduledDate)) {
            throw new IllegalArgumentException("Planlanan bitiş saati başlangıç saatinden sonra olmalıdır.");
        }
    }

    @Transactional
    public ServiceUsedPartDTO addUsedPart(Long ticketId, ServiceUsedPartDTO dto) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));
        validatePartMutationAccess(ticket, currentUser);

        if (dto.getQuantityUsed() == null || dto.getQuantityUsed().signum() <= 0) {
            throw new IllegalArgumentException("Parça adedi sıfırdan büyük olmalıdır.");
        }

        String clientRequestId = normalizeClientRequestId(dto.getClientRequestId());
        if (clientRequestId != null) {
            Optional<com.pusula.backend.entity.ServiceUsedPart> existing = serviceUsedPartRepository
                    .findByCompanyIdAndClientRequestId(currentUser.getCompanyId(), clientRequestId);
            if (existing.isPresent()) {
                com.pusula.backend.entity.ServiceUsedPart part = existing.get();
                if (!ticketId.equals(part.getServiceTicket().getId())) {
                    throw new IllegalArgumentException("İstek kimliği başka bir servis fişinde kullanılmış.");
                }
                return mapUsedPart(part, ticketId);
            }
        }

        com.pusula.backend.entity.Inventory inventory = inventoryRepository
                .findByIdAndCompanyIdForUpdate(dto.getInventoryId(), currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));
        BigDecimal inventoryQuantityBefore = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;

        Long sourceVehicleId = null;
        BigDecimal quantityNeeded = normalizeUsedQuantity(dto.getQuantityUsed(), inventory.getUnitOfMeasure());
        if (inventory.getQuantity() == null || inventory.getQuantity().compareTo(quantityNeeded) < 0) {
            throw new RuntimeException("Yetersiz stok: " + inventory.getPartName());
        }

        // Check if we should use vehicle stock
        if (dto.getSourceVehicleId() != null) {
            VehicleStock vehicleStock = vehicleStockRepository
                    .findForUpdate(
                            dto.getSourceVehicleId(), dto.getInventoryId(), currentUser.getCompanyId())
                    .orElse(null);

            if (vehicleStock != null && vehicleStock.getQuantity().compareTo(quantityNeeded) >= 0) {
                // Deduct from vehicle stock
                vehicleStock.setQuantity(vehicleStock.getQuantity().subtract(quantityNeeded));
                vehicleStockRepository.save(vehicleStock);
                sourceVehicleId = dto.getSourceVehicleId();

                // Also deduct from main inventory (vehicle parts are part of total)
                inventory.setQuantity(inventory.getQuantity().subtract(quantityNeeded));
                inventoryRepository.save(inventory);
            } else {
                // Vehicle doesn't have enough, fall back to main inventory
                inventory.setQuantity(inventory.getQuantity().subtract(quantityNeeded));
                inventoryRepository.save(inventory);
            }
        } else {
            // No vehicle specified, use main inventory directly
            inventory.setQuantity(inventory.getQuantity().subtract(quantityNeeded));
            inventoryRepository.save(inventory);
        }

        BigDecimal inventorySellingPrice = inventory.getSellPrice() != null
                ? inventory.getSellPrice()
                : BigDecimal.ZERO;
        BigDecimal effectiveSellingPrice = normalizeSellingPrice(
                dto.getSellingPriceSnapshot() != null ? dto.getSellingPriceSnapshot() : inventorySellingPrice);

        // Create Used Part record with source tracking and the agreed unit sale price.
        com.pusula.backend.entity.ServiceUsedPart usedPart = com.pusula.backend.entity.ServiceUsedPart.builder()
                .companyId(currentUser.getCompanyId())
                .serviceTicket(ticket)
                .inventory(inventory)
                .quantityUsed(quantityNeeded)
                .unitOfMeasure(inventory.getUnitOfMeasure())
                .sellingPriceSnapshot(effectiveSellingPrice)
                .buyingPriceSnapshot(inventory.getBuyPrice())
                .sourceVehicleId(sourceVehicleId)
                .clientRequestId(clientRequestId)
                .build();

        com.pusula.backend.entity.ServiceUsedPart saved = serviceUsedPartRepository.save(usedPart);

        auditLogService.log(
                "UPDATE",
                "TICKET",
                ticket.getId(),
                "Parça eklendi: " + inventory.getPartName() + " x" + dto.getQuantityUsed());

        notifyCriticalStockCrossing(inventory, inventoryQuantityBefore, currentUser, ticket.getId());

        return new ServiceUsedPartDTO(
                saved.getId(),
                saved.getServiceTicket().getId(),
                saved.getInventory().getId(),
                saved.getInventory().getPartName(),
                saved.getQuantityUsed(),
                saved.getSellingPriceSnapshot(),
                saved.getSourceVehicleId(),
                saved.getClientRequestId(),
                saved.getUnitOfMeasure().name());
    }

    private String normalizeClientRequestId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Geçersiz parça ekleme istek kimliği.");
        }
        return normalized;
    }

    private BigDecimal normalizeSellingPrice(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Parça satış fiyatı sıfır veya daha büyük olmalıdır.");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Parça satış fiyatı izin verilen üst sınırı aşıyor.");
        }
        return normalized;
    }

    private BigDecimal normalizeUsedQuantity(BigDecimal value, com.pusula.backend.entity.InventoryUnit unit) {
        BigDecimal normalized = value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Kullanılan miktar en az 0,001 olmalıdır.");
        }
        if (!unit.allowsFractionalQuantity() && normalized.scale() > 0) {
            throw new IllegalArgumentException("Adet birimli ürünlerde miktar tam sayı olmalıdır.");
        }
        return normalized;
    }

    @Transactional
    public ServiceUsedPartDTO updateUsedPart(Long ticketId, Long partId, ServiceUsedPartDTO dto) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Servis fişi bulunamadı veya erişim reddedildi."));
        validatePartMutationAccess(ticket, currentUser);
        if (dto.getQuantityUsed() == null || dto.getQuantityUsed().signum() <= 0) {
            throw new IllegalArgumentException("Parça adedi sıfırdan büyük olmalıdır.");
        }

        com.pusula.backend.entity.ServiceUsedPart part = serviceUsedPartRepository
                .findByIdAndCompanyId(partId, currentUser.getCompanyId())
                .filter(p -> p.getServiceTicket() != null && ticketId.equals(p.getServiceTicket().getId()))
                .orElseThrow(() -> new RuntimeException("Kullanılan parça bulunamadı."));

        BigDecimal oldQuantity = part.getQuantityUsed() != null ? part.getQuantityUsed() : BigDecimal.ZERO;
        BigDecimal requestedQuantity = normalizeUsedQuantity(dto.getQuantityUsed(), part.getUnitOfMeasure());
        BigDecimal delta = requestedQuantity.subtract(oldQuantity);
        StockAdjustment stockAdjustment = adjustUsedPartStock(part, delta, currentUser.getCompanyId());
        part.setQuantityUsed(requestedQuantity);
        com.pusula.backend.entity.ServiceUsedPart saved = serviceUsedPartRepository.save(part);

        auditLogService.log("UPDATE", "TICKET", ticketId,
                "Parça adedi güncellendi: " + partDisplayName(saved) + " x" + oldQuantity + " → x"
                        + saved.getQuantityUsed());
        if (delta.signum() > 0 && stockAdjustment != null) {
            notifyCriticalStockCrossing(stockAdjustment.inventory(), stockAdjustment.previousQuantity(),
                    currentUser, ticketId);
        }
        return mapUsedPart(saved, ticketId);
    }

    @Transactional
    public void deleteUsedPart(Long ticketId, Long partId) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Servis fişi bulunamadı veya erişim reddedildi."));
        validatePartMutationAccess(ticket, currentUser);

        com.pusula.backend.entity.ServiceUsedPart part = serviceUsedPartRepository
                .findByIdAndCompanyId(partId, currentUser.getCompanyId())
                .filter(p -> p.getServiceTicket() != null && ticketId.equals(p.getServiceTicket().getId()))
                .orElseThrow(() -> new RuntimeException("Kullanılan parça bulunamadı."));
        BigDecimal quantity = part.getQuantityUsed() != null ? part.getQuantityUsed() : BigDecimal.ZERO;
        String partName = partDisplayName(part);
        adjustUsedPartStock(part, quantity.negate(), currentUser.getCompanyId());
        serviceUsedPartRepository.delete(part);
        auditLogService.log("DELETE", "TICKET", ticketId,
                "Kullanılan parça silindi ve stoğa iade edildi: " + partName + " x" + quantity);
    }

    private void validatePartMutationAccess(ServiceTicket ticket, User currentUser) {
        if (!isAdmin(currentUser)
                && (ticket.getAssignedTechnicianId() == null
                        || !ticket.getAssignedTechnicianId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Yalnızca size atanmış servisin parçalarını değiştirebilirsiniz.");
        }
        if (ticket.getStatus() == ServiceTicket.TicketStatus.COMPLETED
                || ticket.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
            throw new IllegalStateException("Kapanmış veya iptal edilmiş servisin parçaları değiştirilemez.");
        }
    }

    private StockAdjustment adjustUsedPartStock(com.pusula.backend.entity.ServiceUsedPart part, BigDecimal delta, Long companyId) {
        if (delta.signum() == 0) {
            return null;
        }
        Inventory linkedInventory = part.getInventory();
        Inventory inventory;
        if (linkedInventory != null && companyId.equals(linkedInventory.getCompanyId())) {
            inventory = inventoryRepository
                    .findByIdAndCompanyIdForUpdate(linkedInventory.getId(), companyId)
                    .orElseThrow(() -> new IllegalStateException("Parçanın envanter kaydı bulunamadı."));
        } else {
            Long historicalInventoryId = part.getInventoryId();
            if (historicalInventoryId == null) {
                throw new IllegalStateException("Parçanın bağlı olduğu envanter kaydı bulunamadı.");
            }
            inventory = inventoryRepository
                    .findIncludingDeletedByIdAndCompanyIdForUpdate(historicalInventoryId, companyId)
                    .orElseThrow(() -> new IllegalStateException("Parçanın envanter kaydı bulunamadı."));
        }

        if (delta.signum() > 0 && inventory.isDeleted()) {
            throw new IllegalStateException("Silinmiş bir envanter kaleminden yeni parça kullanılamaz.");
        }
        Inventory stockOrigin = inventory;
        boolean mergedIntoReplacement = false;
        // A returned unit means this stock item exists again and must be visible.
        // If the deleted row's barcode was recreated in the meantime, merge the
        // return into that active row. Reviving the historical row would violate
        // the active-barcode uniqueness constraint and abort ticket cancellation.
        if (delta.signum() < 0 && inventory.isDeleted()) {
            String barcode = inventory.getBarcode();
            Optional<Inventory> activeReplacement = barcode == null || barcode.isBlank()
                    ? Optional.empty()
                    : inventoryRepository.findActiveBarcodeReplacementForUpdate(
                            barcode, companyId, inventory.getId());
            if (activeReplacement.isPresent()) {
                Inventory replacement = activeReplacement.get();
                BigDecimal historicalQuantity = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
                BigDecimal replacementQuantity = replacement.getQuantity() != null ? replacement.getQuantity() : BigDecimal.ZERO;
                replacement.setQuantity(replacementQuantity.add(historicalQuantity));
                inventory = replacement;
                part.setInventory(replacement);
                mergedIntoReplacement = true;
            } else {
                inventory.setDeleted(false);
            }
        }
        BigDecimal currentInventory = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
        if (delta.signum() > 0 && currentInventory.compareTo(delta) < 0) {
            throw new IllegalStateException("Yetersiz stok: " + inventory.getPartName());
        }

        VehicleStock vehicleStock = null;
        if (part.getSourceVehicleId() != null) {
            vehicleStock = vehicleStockRepository
                    .findForUpdate(
                            part.getSourceVehicleId(), stockOrigin.getId(), companyId)
                    .orElseThrow(() -> new IllegalStateException("Aracın parça stok kaydı bulunamadı."));
            BigDecimal currentVehicleStock = vehicleStock.getQuantity() != null ? vehicleStock.getQuantity() : BigDecimal.ZERO;
            if (delta.signum() > 0 && currentVehicleStock.compareTo(delta) < 0) {
                throw new IllegalStateException("Araçta yeterli parça stoğu yok.");
            }
            if (mergedIntoReplacement) {
                Optional<VehicleStock> replacementVehicleStock = vehicleStockRepository.findForUpdate(
                        part.getSourceVehicleId(), inventory.getId(), companyId);
                if (replacementVehicleStock.isPresent()) {
                    VehicleStock targetVehicleStock = replacementVehicleStock.get();
                    BigDecimal targetQuantity = targetVehicleStock.getQuantity() != null
                            ? targetVehicleStock.getQuantity() : BigDecimal.ZERO;
                    targetVehicleStock.setQuantity(targetQuantity.add(currentVehicleStock).subtract(delta));
                    vehicleStockRepository.save(targetVehicleStock);
                    vehicleStockRepository.delete(vehicleStock);
                } else {
                    vehicleStock.setInventory(inventory);
                    vehicleStock.setQuantity(currentVehicleStock.subtract(delta));
                    vehicleStockRepository.save(vehicleStock);
                }
            } else {
                vehicleStock.setQuantity(currentVehicleStock.subtract(delta));
                vehicleStockRepository.save(vehicleStock);
            }
        }

        inventory.setQuantity(currentInventory.subtract(delta));
        inventoryRepository.save(inventory);
        return new StockAdjustment(inventory, currentInventory);
    }

    private record StockAdjustment(Inventory inventory, BigDecimal previousQuantity) { }

    private String partDisplayName(com.pusula.backend.entity.ServiceUsedPart part) {
        return part.getInventory() != null && part.getInventory().getPartName() != null
                ? part.getInventory().getPartName()
                : "Yedek Parça";
    }

    private ServiceUsedPartDTO mapUsedPart(com.pusula.backend.entity.ServiceUsedPart part, Long ticketId) {
        Inventory inventory = part.getInventory();
        return new ServiceUsedPartDTO(
                part.getId(),
                ticketId,
                inventory != null ? inventory.getId() : part.getInventoryId(),
                partDisplayName(part),
                part.getQuantityUsed(),
                part.getSellingPriceSnapshot() != null ? part.getSellingPriceSnapshot() : BigDecimal.ZERO,
                part.getSourceVehicleId(),
                part.getClientRequestId(),
                part.getUnitOfMeasure().name());
    }

    public List<ServiceUsedPartDTO> getUsedParts(Long ticketId) {
        User currentUser = getCurrentUser();
        // Verify access to ticket
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        if ("TECHNICIAN".equals(currentUser.getRole())
                && (ticket.getAssignedTechnicianId() == null
                        || !ticket.getAssignedTechnicianId().equals(currentUser.getId()))) {
            throw new RuntimeException("Access Denied: You can only view parts for tickets assigned to you.");
        }

        return serviceUsedPartRepository.findByServiceTicketId(ticketId).stream()
                .map(part -> {
                    Inventory inventory = part.getInventory();
                    Long inventoryId = inventory != null ? inventory.getId() : part.getInventoryId();
                    String partName = inventory != null && inventory.getPartName() != null
                            ? inventory.getPartName()
                            : "Yedek Parça";
                    return new ServiceUsedPartDTO(
                            part.getId(),
                            ticketId,
                            inventoryId,
                            partName,
                            part.getQuantityUsed(),
                            part.getSellingPriceSnapshot() != null ? part.getSellingPriceSnapshot() : BigDecimal.ZERO,
                            part.getSourceVehicleId(),
                            part.getClientRequestId());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ServiceTicketDTO completeService(Long ticketId, BigDecimal collectedAmount, PaymentMethod paymentMethod,
            LocalDate requestedCompletionDate) {
        return completeService(ticketId, collectedAmount, null, paymentMethod, requestedCompletionDate);
    }

    @Transactional
    public ServiceTicketDTO completeService(Long ticketId, BigDecimal collectedAmount, BigDecimal laborFee,
            PaymentMethod paymentMethod, LocalDate requestedCompletionDate) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        if (!isAdmin(currentUser)
                && (ticket.getAssignedTechnicianId() == null
                        || !ticket.getAssignedTechnicianId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Yalnızca size atanmış servisi kapatabilirsiniz.");
        }
        if (ticket.getStatus() == ServiceTicket.TicketStatus.COMPLETED
                || ticket.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
            throw new IllegalStateException("Kapalı bir servis tekrar tamamlanamaz.");
        }
        if (collectedAmount == null || collectedAmount.signum() < 0) {
            throw new IllegalArgumentException("Tahsilat tutarı sıfır veya daha büyük olmalıdır.");
        }

        List<com.pusula.backend.entity.ServiceUsedPart> usedParts = serviceUsedPartRepository
                .findByServiceTicketId(ticketId);
        if (usedParts == null) {
            usedParts = List.of();
        }
        BigDecimal partsTotal = usedParts.stream()
                .map(part -> {
                    BigDecimal unitPrice = part.getSellingPriceSnapshot() != null
                            ? part.getSellingPriceSnapshot()
                            : BigDecimal.ZERO;
                    BigDecimal quantity = part.getQuantityUsed() != null ? part.getQuantityUsed() : BigDecimal.ZERO;
                    return unitPrice.multiply(quantity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PaymentMethod effectivePaymentMethod = paymentMethod != null ? paymentMethod : PaymentMethod.CASH;
        BigDecimal effectiveLaborFee;
        BigDecimal invoiceTotal;
        BigDecimal effectiveCollectedAmount;
        BigDecimal storedCollectedAmount;
        BigDecimal outstandingAmount;
        boolean warrantyCompletion = effectivePaymentMethod == PaymentMethod.WARRANTY;
        boolean structuredPricingRequest = laborFee != null || warrantyCompletion;

        if (warrantyCompletion) {
            if (collectedAmount.signum() != 0) {
                throw new IllegalArgumentException("Garanti kapsamındaki serviste tahsilat 0 olmalıdır.");
            }
            if (laborFee != null && laborFee.signum() != 0) {
                throw new IllegalArgumentException("Garanti kapsamındaki serviste işçilik bedeli 0 olmalıdır.");
            }
            effectiveLaborFee = BigDecimal.ZERO;
            invoiceTotal = BigDecimal.ZERO;
            effectiveCollectedAmount = BigDecimal.ZERO;
            storedCollectedAmount = BigDecimal.ZERO;
            outstandingAmount = BigDecimal.ZERO;
        } else if (!structuredPricingRequest) {
            // Older clients sent a single amount. Preserve it as the invoice total.
            invoiceTotal = collectedAmount;
            effectiveLaborFee = invoiceTotal.subtract(partsTotal).max(BigDecimal.ZERO);
            effectiveCollectedAmount = effectivePaymentMethod == PaymentMethod.CURRENT_ACCOUNT
                    ? BigDecimal.ZERO
                    : collectedAmount;
            // Keep the legacy database contract: on CURRENT_ACCOUNT this field held
            // the debt/invoice amount even though no cash was collected.
            storedCollectedAmount = collectedAmount;
            outstandingAmount = effectivePaymentMethod == PaymentMethod.CURRENT_ACCOUNT
                    ? invoiceTotal
                    : BigDecimal.ZERO;
        } else {
            if (laborFee.signum() < 0) {
                throw new IllegalArgumentException("İşçilik/servis bedeli negatif olamaz.");
            }
            effectiveLaborFee = laborFee;
            invoiceTotal = partsTotal.add(effectiveLaborFee);

            if (effectivePaymentMethod == PaymentMethod.CURRENT_ACCOUNT) {
                if (collectedAmount.signum() != 0) {
                    throw new IllegalArgumentException("Cari hesapta tahsil edilen tutar 0 olmalıdır.");
                }
                effectiveCollectedAmount = BigDecimal.ZERO;
            } else {
                if (collectedAmount.compareTo(invoiceTotal) > 0) {
                    throw new IllegalArgumentException("Tahsil edilen tutar fiş toplamını aşamaz.");
                }
                effectiveCollectedAmount = collectedAmount;
            }
            storedCollectedAmount = effectiveCollectedAmount;
            outstandingAmount = invoiceTotal.subtract(effectiveCollectedAmount);
        }

        LocalDate businessToday = LocalDate.now(businessZone);
        if (requestedCompletionDate != null && !isAdmin(currentUser)) {
            throw new AccessDeniedException("Geçmiş kapanış tarihi yalnızca yöneticiler tarafından seçilebilir.");
        }
        LocalDate completionDate = requestedCompletionDate != null ? requestedCompletionDate : businessToday;
        if (completionDate.isAfter(businessToday)) {
            throw new IllegalArgumentException("Servis kapanış tarihi gelecekte olamaz.");
        }
        LocalTime completionTime = completionDate.equals(businessToday)
                ? LocalTime.now(businessZone)
                : LocalTime.NOON;

        String previousStatus = getStatusInTurkish(ticket.getStatus());
        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setWorkProgressReason(null);
        ticket.setWorkProgressNote(null);
        ticket.setPartsTotal(structuredPricingRequest ? (warrantyCompletion ? BigDecimal.ZERO : partsTotal) : null);
        ticket.setLaborFee(structuredPricingRequest ? effectiveLaborFee : null);
        ticket.setInvoiceTotal(structuredPricingRequest ? invoiceTotal : null);
        ticket.setCollectedAmount(storedCollectedAmount);
        ticket.setOutstandingAmount(structuredPricingRequest ? outstandingAmount : null);
        ticket.setPaymentMethod(effectivePaymentMethod);
        if (warrantyCompletion) {
            ticket.setWarrantyCall(true);
        }
        ticket.setCompletedAt(completionDate.atTime(completionTime));
        ticket.setCollectionDate(effectiveCollectedAmount.signum() > 0 ? completionDate : null);

        // Any unpaid portion becomes customer debt, including partial cash/card payments.
        CurrentAccount debtAccount = null;
        if (outstandingAmount.signum() > 0 && ticket.getCustomerId() != null) {
            // Fetch customer entity
            Customer customer = customerRepository.findById(ticket.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));

            CurrentAccount account = currentAccountRepository
                    .findByCustomerIdAndCompanyId(ticket.getCustomerId(), ticket.getCompanyId())
                    .orElseGet(() -> {
                        CurrentAccount newAccount = CurrentAccount.builder()
                                .companyId(ticket.getCompanyId())
                                .customer(customer)
                                .balance(BigDecimal.ZERO)
                                .build();
                        return currentAccountRepository.save(newAccount);
                    });

            // ADD to debt (positive balance = customer owes us)
            BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            account.setBalance(currentBalance.add(outstandingAmount));
            debtAccount = currentAccountRepository.save(account);
        }

        ServiceTicket saved = repository.save(ticket);
        if (debtAccount != null) {
            currentAccountLedgerService.record(debtAccount, CurrentAccountTransaction.TransactionType.CHARGE,
                    outstandingAmount, completionDate,
                    "Servis fişi #" + saved.getId() + " - " + saved.getDescription(),
                    effectivePaymentMethod, "SERVICE_TICKET", saved.getId());
        }
        financeService.reconcileClosedDay(saved.getCompanyId(), completionDate);
        if (!completionDate.equals(businessToday)) {
            auditLogService.log("BACKDATED_COMPLETE", "TICKET", saved.getId(),
                    "Servis geçmiş iş tarihiyle kapatıldı: " + completionDate
                            + " | Gerçek işlem zamanı: " + LocalDateTime.now(businessZone));
        }

        auditLogService.log(
                "UPDATE",
                "TICKET",
                saved.getId(),
                "Servis tamamlandı",
                previousStatus,
                getStatusInTurkish(ServiceTicket.TicketStatus.COMPLETED));
        auditLogService.log(
                "UPDATE",
                "TICKET",
                saved.getId(),
                String.format("Tahsilat kaydedildi: %.2f ₺ (%s)",
                        effectiveCollectedAmount,
                        effectivePaymentMethod.name()));

        // WhatsApp notification — async fire-and-forget
        try {
            whatsAppNotificationService.notifyServiceCompleted(ticketId, effectiveCollectedAmount,
                    outstandingAmount);
        } catch (Exception e) {
            // Don't fail the service completion if notification fails
            log.warn("WhatsApp notification failed (non-blocking): {}", e.getMessage());
        }

        if ("TECHNICIAN".equals(currentUser.getRole())) {
            notifyAdmins(saved, "Servis tamamlandı",
                    "#" + saved.getId() + " · " + displayName(currentUser) + " · " + saved.getDescription(),
                    Notification.NotificationType.INFO, Notification.NotificationCategory.SERVICE_COMPLETED,
                    currentUser.getId());
        }

        return mapToDTO(saved);
    }

    private void notifyCriticalStockCrossing(Inventory inventory, BigDecimal previousQuantity, User actor,
            Long ticketId) {
        BigDecimal critical = inventory.getCriticalLevel() != null ? inventory.getCriticalLevel() : BigDecimal.ZERO;
        BigDecimal current = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
        if (previousQuantity.compareTo(critical) > 0 && current.compareTo(critical) <= 0) {
            adminNotificationService.notifyCompanyAdmins(actor.getCompanyId(), "Kritik stok seviyesi",
                    inventory.getPartName() + " · Kalan " + current.stripTrailingZeros().toPlainString(),
                    Notification.NotificationType.WARNING, Notification.NotificationCategory.CRITICAL_STOCK,
                    ticketId != null ? "TICKET" : "INVENTORY", ticketId != null ? ticketId : inventory.getId(), null);
        }
    }

    private void notifyAdmins(ServiceTicket ticket, String title, String message,
            Notification.NotificationType severity, Notification.NotificationCategory category, Long excludedUserId) {
        adminNotificationService.notifyCompanyAdmins(ticket.getCompanyId(), title, truncate(message, 500), severity,
                category, "TICKET", ticket.getId(), excludedUserId);
    }

    private String ticketSummary(ServiceTicket ticket) {
        return "#" + ticket.getId() + " · " + (ticket.getDescription() == null ? "Servis talebi" : ticket.getDescription());
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank() ? user.getUsername() : user.getFullName().trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }

    @Transactional
    public ServiceTicketDTO cancelService(Long ticketId) {
        User currentUser = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(currentUser.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        if (!isAdmin(currentUser)
                && (ticket.getAssignedTechnicianId() == null
                        || !ticket.getAssignedTechnicianId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Yalnızca size atanmış servisi iptal edebilirsiniz.");
        }
        if (ticket.getStatus() == ServiceTicket.TicketStatus.COMPLETED
                || ticket.getStatus() == ServiceTicket.TicketStatus.CANCELLED) {
            throw new IllegalStateException("Kapalı bir servis tekrar iptal edilemez.");
        }
        if (ticket.getReopenedAt() != null) {
            throw new IllegalStateException(
                    "Yeniden açılmış servis fişi iptal edilemez. Düzeltmeleri tamamlayıp fişi yeniden kapatın.");
        }

        // Return all used parts back to inventory. This also safely revives an
        // inventory row that was soft-deleted after its stock reached zero.
        List<com.pusula.backend.entity.ServiceUsedPart> usedParts = serviceUsedPartRepository
                .findByServiceTicketId(ticketId);

        for (com.pusula.backend.entity.ServiceUsedPart usedPart : usedParts) {
            BigDecimal quantity = usedPart.getQuantityUsed() != null ? usedPart.getQuantityUsed() : BigDecimal.ZERO;
            String partName = partDisplayName(usedPart);
            Long inventoryId = usedPart.getInventory() != null
                    ? usedPart.getInventory().getId() : usedPart.getInventoryId();
            adjustUsedPartStock(usedPart, quantity.negate(), ticket.getCompanyId());

            // Log the return
            auditLogService.log(
                    "RETURN",
                    "INVENTORY",
                    inventoryId,
                    "Parça iade edildi (iptal): " + partName + " x" + quantity);
        }

        // Delete the used parts records (soft delete)
        for (com.pusula.backend.entity.ServiceUsedPart usedPart : usedParts) {
            serviceUsedPartRepository.delete(usedPart);
        }

        ticket.setStatus(ServiceTicket.TicketStatus.CANCELLED);
        ticket.setWorkProgressReason(null);
        ticket.setWorkProgressNote(null);

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
        boolean isWarranty = originalTicket.getEffectiveCompletedAt() != null &&
                originalTicket.getEffectiveCompletedAt().isAfter(thirtyDaysAgo);
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
                .scheduledEndDate(ticket.getScheduledEndDate())
                .workProgressReason(canViewTechnicianPrivateNote(ticket) ? ticket.getWorkProgressReason() : null)
                .workProgressNote(canViewTechnicianPrivateNote(ticket) ? ticket.getWorkProgressNote() : null)
                .lastRescheduledAt(canViewTechnicianPrivateNote(ticket) ? ticket.getLastRescheduledAt() : null)
                .description(ticket.getDescription())
                .notes(ticket.getNotes())
                .technicianPrivateNote(canViewTechnicianPrivateNote(ticket)
                        ? ticket.getTechnicianPrivateNote()
                        : null)
                .collectedAmount(ticket.getCollectedAmount())
                .laborFee(ticket.getLaborFee())
                .partsTotal(ticket.getPartsTotal())
                .invoiceTotal(ticket.getInvoiceTotal())
                .outstandingAmount(ticket.getOutstandingAmount())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .completedAt(ticket.getEffectiveCompletedAt())
                .reopenedAt(ticket.getReopenedAt())
                .collectionDate(ticket.getEffectiveCollectionDate())
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

    private boolean canViewTechnicianPrivateNote(ServiceTicket ticket) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return false;
        }
        return isAdmin(user) || ("TECHNICIAN".equals(user.getRole())
                && Objects.equals(ticket.getAssignedTechnicianId(), user.getId()));
    }

    private String normalizePrivateNote(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Save a PencilKit signature image to local storage.
     * Strategy: local filesystem (same as desktop app), avoiding S3 costs.
     * Path: /uploads/signatures/{companyId}/{ticketId}.png
     */
    public String saveSignature(Long ticketId, String signatureBase64) {
        User user = getCurrentUser();
        Long companyId = user.getCompanyId();

        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> companyId.equals(t.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Servis fişi bulunamadı veya erişim reddedildi."));
        if (!isAdmin(user)
                && (ticket.getAssignedTechnicianId() == null
                        || !ticket.getAssignedTechnicianId().equals(user.getId()))) {
            throw new AccessDeniedException("Yalnızca size atanmış servis için imza kaydedebilirsiniz.");
        }

        try {
            Path dir = Paths.get("uploads", "signatures", companyId.toString());
            Files.createDirectories(dir);

            if (signatureBase64 == null || signatureBase64.isBlank()) {
                throw new IllegalArgumentException("İmza görseli boş olamaz.");
            }
            byte[] imageBytes;
            try {
                imageBytes = Base64.getDecoder().decode(signatureBase64);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("İmza verisi geçerli Base64 formatında değil.", ex);
            }
            if (imageBytes.length == 0 || imageBytes.length > 2L * 1024L * 1024L) {
                throw new IllegalArgumentException("İmza görseli 2 MB'dan büyük olamaz.");
            }
            if (imageBytes.length < 8
                    || (imageBytes[0] & 0xFF) != 0x89 || imageBytes[1] != 0x50
                    || imageBytes[2] != 0x4E || imageBytes[3] != 0x47) {
                throw new IllegalArgumentException("İmza gerçek bir PNG görseli olmalıdır.");
            }
            String fileName = ticketId + "_" + java.util.UUID.randomUUID() + ".png";
            Path filePath = dir.resolve(fileName);

            try (OutputStream os = Files.newOutputStream(filePath)) {
                os.write(imageBytes);
            }
            ticket.setCustomerSignaturePath("signatures/" + companyId + "/" + fileName);
            repository.save(ticket);

            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    ticketId,
                    "Müşteri imzası kaydedildi");

            return uploadUrlSigner.sign("/uploads/" + ticket.getCustomerSignaturePath());
        } catch (IOException e) {
            throw new RuntimeException("İmza kaydedilemedi: " + e.getMessage(), e);
        }
    }

    public ServicePhotoDTO uploadServicePhoto(Long ticketId, ServicePhoto.PhotoType type, String note, MultipartFile file) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        validateServicePhotoUpload(file);
        String safeNote = normalizePhotoNote(note);

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
                    .note(safeNote)
                    .uploadedByName(user.getFullName() == null || user.getFullName().isBlank()
                            ? user.getUsername() : user.getFullName().trim())
                    .build();
            ServicePhoto saved = servicePhotoRepository.save(photo);
            auditLogService.log(
                    "UPDATE",
                    "TICKET",
                    ticket.getId(),
                    "Servis görseli yüklendi (" + type.name() + ")");
            Customer customer = customerRepository.findById(ticket.getCustomerId()).orElse(null);
            return mapPhotoToDTO(saved, ticket, customer);
        } catch (IOException e) {
            log.error("Service photo upload failed for ticketId={}", ticketId, e);
            throw new RuntimeException("Servis görseli yüklenemedi: " + e.getMessage(), e);
        }
    }

    public List<ServicePhotoDTO> getServicePhotos(Long ticketId) {
        User user = getCurrentUser();
        ServiceTicket ticket = repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        Customer customer = customerRepository.findById(ticket.getCustomerId()).orElse(null);
        return servicePhotoRepository.findByTicketIdOrderByUploadedAtDesc(ticketId).stream()
                .map(photo -> mapPhotoToDTO(photo, ticket, customer))
                .collect(Collectors.toList());
    }

    public List<ServicePhotoDTO> getCompanyServicePhotos(
            ServicePhoto.PhotoType type,
            Long ticketId,
            LocalDate startDate,
            LocalDate endDate,
            String query,
            Integer limit) {
        User user = getCurrentUser();
        List<ServiceTicket> companyTickets = repository.findByCompanyId(user.getCompanyId());
        List<Long> companyTicketIds = companyTickets.stream()
                .map(ServiceTicket::getId)
                .collect(Collectors.toList());

        if (companyTicketIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ServiceTicket> ticketsById = companyTickets.stream()
                .collect(Collectors.toMap(ServiceTicket::getId, ticket -> ticket));
        Map<Long, Customer> customersById = new HashMap<>();
        customerRepository.findAllById(companyTickets.stream()
                        .map(ServiceTicket::getCustomerId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .forEach(customer -> customersById.put(customer.getId(), customer));
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.forLanguageTag("tr-TR"));
        List<ServicePhoto> photos = servicePhotoRepository.findByTicketIdInOrderByUploadedAtDesc(companyTicketIds);
        return photos.stream()
                .filter(photo -> type == null || photo.getType() == type)
                .filter(photo -> ticketId == null || photo.getTicketId().equals(ticketId))
                .filter(photo -> {
                    ServiceTicket ticket = ticketsById.get(photo.getTicketId());
                    LocalDate serviceDate = toBusinessDate(resolvePhotoServiceDate(ticket));
                    if (serviceDate == null) return false;
                    boolean afterStart = startDate == null || !serviceDate.isBefore(startDate);
                    boolean beforeEnd = endDate == null || !serviceDate.isAfter(endDate);
                    return afterStart && beforeEnd;
                })
                .filter(photo -> matchesPhotoQuery(photo, ticketsById.get(photo.getTicketId()),
                        customersById, normalizedQuery))
                .sorted(Comparator
                        .comparing((ServicePhoto photo) -> resolvePhotoServiceDate(ticketsById.get(photo.getTicketId())),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ServicePhoto::getUploadedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit != null && limit > 0 ? limit : Long.MAX_VALUE)
                .map(photo -> {
                    ServiceTicket ticket = ticketsById.get(photo.getTicketId());
                    Customer customer = ticket == null ? null : customersById.get(ticket.getCustomerId());
                    return mapPhotoToDTO(photo, ticket, customer);
                })
                .collect(Collectors.toList());
    }

    public ServicePhotoPageDTO getCompanyServicePhotoPage(
            ServicePhoto.PhotoType type,
            Long ticketId,
            LocalDate startDate,
            LocalDate endDate,
            String query,
            Integer page,
            Integer size) {
        User user = getCurrentUser();
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? 24 : Math.max(1, Math.min(size, 48));
        String normalizedQuery = query == null ? "" : query.trim()
                .toLowerCase(Locale.forLanguageTag("tr-TR"));
        String queryPattern = normalizedQuery.isBlank() ? "%" : "%" + normalizedQuery + "%";
        LocalDateTime startDateTime = toServerDateTime(startDate);
        LocalDateTime endDateTime = endDate == null ? null : toServerDateTime(endDate.plusDays(1));

        Page<Long> ticketPage = servicePhotoRepository.findServiceFileTicketIds(
                user.getCompanyId(),
                type != null, type == null ? ServicePhoto.PhotoType.BEFORE : type,
                ticketId != null, ticketId == null ? 0L : ticketId,
                startDateTime != null, startDateTime == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : startDateTime,
                endDateTime != null, endDateTime == null ? LocalDateTime.of(2100, 1, 1, 0, 0) : endDateTime,
                !normalizedQuery.isBlank(), queryPattern,
                PageRequest.of(safePage, safeSize));
        if (ticketPage.isEmpty()) {
            return new ServicePhotoPageDTO(List.of(), safePage, safeSize,
                    ticketPage.getTotalElements(), ticketPage.getTotalPages(), false);
        }

        List<ServiceTicket> tickets = repository.findAllById(ticketPage.getContent());
        Map<Long, ServiceTicket> ticketsById = tickets.stream()
                .filter(ticket -> user.getCompanyId().equals(ticket.getCompanyId()))
                .collect(Collectors.toMap(ServiceTicket::getId, ticket -> ticket));
        Map<Long, Customer> customersById = new HashMap<>();
        customerRepository.findAllById(tickets.stream()
                        .map(ServiceTicket::getCustomerId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .forEach(customer -> customersById.put(customer.getId(), customer));

        List<ServicePhotoDTO> items = servicePhotoRepository
                .findByTicketIdInOrderByUploadedAtDesc(ticketPage.getContent()).stream()
                .filter(photo -> type == null || photo.getType() == type)
                .filter(photo -> matchesPhotoQuery(photo, ticketsById.get(photo.getTicketId()),
                        customersById, normalizedQuery))
                .sorted(Comparator
                        .comparing((ServicePhoto photo) -> resolvePhotoServiceDate(ticketsById.get(photo.getTicketId())),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ServicePhoto::getUploadedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(photo -> {
                    ServiceTicket ticket = ticketsById.get(photo.getTicketId());
                    Customer customer = ticket == null ? null : customersById.get(ticket.getCustomerId());
                    return mapPhotoToDTO(photo, ticket, customer, true);
                })
                .toList();

        return new ServicePhotoPageDTO(items, safePage, safeSize,
                ticketPage.getTotalElements(), ticketPage.getTotalPages(), ticketPage.hasNext());
    }

    public void deleteServicePhoto(Long ticketId, Long photoId) {
        User user = getCurrentUser();
        repository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new RuntimeException("Ticket not found or access denied"));

        ServicePhoto photo = servicePhotoRepository.findById(photoId)
                .filter(p -> p.getTicketId().equals(ticketId))
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        fileUploadService.deleteServicePhotoAndThumbnail(photo.getUrl());
        servicePhotoRepository.delete(photo);
    }

    private ServicePhotoDTO mapPhotoToDTO(ServicePhoto photo, ServiceTicket ticket, Customer customer) {
        return mapPhotoToDTO(photo, ticket, customer, false);
    }

    private ServicePhotoDTO mapPhotoToDTO(ServicePhoto photo, ServiceTicket ticket,
                                           Customer customer, boolean includeThumbnail) {
        String signedUrl = uploadUrlSigner.sign(photo.getUrl());
        String thumbnailPath = includeThumbnail
                ? fileUploadService.getOrCreateServicePhotoThumbnail(photo.getUrl())
                : null;
        String thumbnailUrl = thumbnailPath == null
                ? signedUrl
                : uploadUrlSigner.sign("/uploads/" + thumbnailPath);
        return new ServicePhotoDTO(
                photo.getId(),
                photo.getTicketId(),
                signedUrl,
                thumbnailUrl,
                photo.getType(),
                photo.getNote(),
                photo.getUploadedByName(),
                photo.getUploadedAt(),
                resolvePhotoServiceDate(ticket),
                customer == null ? null : customer.getName(),
                ticket == null ? null : ticket.getDescription()
        );
    }

    private LocalDateTime resolvePhotoServiceDate(ServiceTicket ticket) {
        if (ticket == null) return null;
        if (ticket.getEffectiveCompletedAt() != null) return ticket.getEffectiveCompletedAt();
        if (ticket.getScheduledDate() != null) return ticket.getScheduledDate();
        return ticket.getCreatedAt();
    }

    private boolean matchesPhotoQuery(ServicePhoto photo, ServiceTicket ticket,
                                      Map<Long, Customer> customersById, String query) {
        if (query == null || query.isBlank()) return true;
        Customer customer = ticket == null ? null : customersById.get(ticket.getCustomerId());
        String haystack = String.join(" ",
                String.valueOf(photo.getTicketId()),
                photo.getType() == null ? "" : photo.getType().name(),
                photo.getNote() == null ? "" : photo.getNote(),
                customer == null || customer.getName() == null ? "" : customer.getName(),
                ticket == null || ticket.getDescription() == null ? "" : ticket.getDescription());
        return haystack.toLowerCase(Locale.forLanguageTag("tr-TR")).contains(query);
    }

    private String normalizePhotoNote(String note) {
        if (note == null || note.isBlank()) return null;
        String normalized = note.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.length() > 500) {
            throw new RuntimeException("Görsel notu 500 karakterden uzun olamaz.");
        }
        return normalized;
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
                || contentType.equalsIgnoreCase("image/webp")
                || contentType.equalsIgnoreCase("image/*")
                || contentType.toLowerCase(Locale.ROOT).startsWith("image/");
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
                return "İşlemde";
            case COMPLETED:
                return "Tamamlandı";
            case CANCELLED:
                return "İptal Edildi";
            default:
                return status.toString();
        }
    }

    private LocalDateTime toServerDateTime(LocalDate date) {
        if (date == null) return null;
        return date.atStartOfDay(businessZone).withZoneSameInstant(serverZone).toLocalDateTime();
    }

    private String getProgressReasonInTurkish(ServiceTicket.WorkProgressReason reason) {
        return switch (reason) {
            case PART_PENDING -> "Parça Bekleniyor";
            case CUSTOMER_AVAILABILITY -> "Müşteri Uygunluğu Bekleniyor";
            case CUSTOMER_APPROVAL -> "Müşteri Onayı Bekleniyor";
            case EXTERNAL_SUPPORT -> "Harici Destek Bekleniyor";
            case RESCHEDULED -> "Yeniden Planlandı";
            case OTHER -> "Diğer";
        };
    }
}
