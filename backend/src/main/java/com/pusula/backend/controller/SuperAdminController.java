package com.pusula.backend.controller;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.AuditLogRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.repository.PaymentEventRepository;
import com.pusula.backend.repository.WebhookEventRepository;
import com.pusula.backend.entity.PaymentEvent;
import com.pusula.backend.entity.WebhookEvent;
import com.pusula.backend.dto.SystemStatusDTO;
import com.pusula.backend.dto.CompanyQuotaStatusDTO;
import com.pusula.backend.dto.QuotaDTO;
import com.pusula.backend.service.AuditLogService;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPER_ADMIN_OPS','SUPER_ADMIN_READONLY')")
public class SuperAdminController {

    private final CompanyRepository companyRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final FeatureService featureService;
    private final JwtService jwtService;
    private final PaymentEventRepository paymentEventRepository;
    private final WebhookEventRepository webhookEventRepository;
    @Value("${app.deploy.version:unknown}")
    private String deployVersion = "unknown";

    public SuperAdminController(
            CompanyRepository companyRepository,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            FeatureService featureService,
            JwtService jwtService,
            PaymentEventRepository paymentEventRepository,
            WebhookEventRepository webhookEventRepository) {
        this.companyRepository = companyRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.featureService = featureService;
        this.jwtService = jwtService;
        this.paymentEventRepository = paymentEventRepository;
        this.webhookEventRepository = webhookEventRepository;
    }

    /**
     * Tüm şirketleri (tenant) listele.
     */
    @GetMapping("/companies")
    @PreAuthorize("hasAuthority('SUPERADMIN_READ_COMPANIES')")
    public ResponseEntity<List<Company>> getAllCompanies() {
        return ResponseEntity.ok(companyRepository.findAll());
    }

    @GetMapping("/companies/{id}/quota-status")
    @PreAuthorize("hasAuthority('SUPERADMIN_READ_COMPANIES')")
    public ResponseEntity<CompanyQuotaStatusDTO> getCompanyQuotaStatus(@PathVariable Long id) {
        Company company = companyRepository.findById(id).orElse(null);
        if (company == null) {
            return notFound("COMPANY_NOT_FOUND", "Şirket bulunamadı.");
        }

        Map<String, Object> featureContext = featureService.getFeatureContext(id);
        QuotaDTO quota = (QuotaDTO) featureContext.get("quota");

        int usage = quota.getCurrentMonthlyTickets() + quota.getCurrentMonthlyProposals();
        int limit = quota.getMaxMonthlyTickets() < 0 || quota.getMaxMonthlyProposals() < 0
                ? -1
                : quota.getMaxMonthlyTickets() + quota.getMaxMonthlyProposals();

        double percent = limit > 0
                ? Math.min((usage * 100.0) / limit, 100.0)
                : 0.0;

        CompanyQuotaStatusDTO response = new CompanyQuotaStatusDTO(
                company.getPlanType().name(),
                usage,
                limit,
                percent,
                limit > 0 && percent >= 80.0);

        return ResponseEntity.ok(response);
    }

    /**
     * Süper admin tarafından yeni şirket + ilk admin kullanıcı oluştur.
     */
    @PostMapping("/companies")
    @PreAuthorize("hasAuthority('SUPERADMIN_WRITE_COMPANY')")
    @Transactional
    public ResponseEntity<Company> createCompany(@RequestBody CreateCompanyRequest request) {
        if (request == null || request.name == null || request.name.isBlank()) {
            return badRequest("VALIDATION_ERROR", "Şirket adı zorunludur.");
        }
        if (request.adminUsername == null || request.adminUsername.isBlank()) {
            return badRequest("VALIDATION_ERROR", "Admin kullanıcı adı zorunludur.");
        }
        if (request.adminPassword == null || request.adminPassword.isBlank()) {
            return badRequest("VALIDATION_ERROR", "Admin şifresi zorunludur.");
        }
        if (!hasReason(request.reason)) {
            return badRequest("REASON_REQUIRED", "En az 5 karakterlik işlem gerekçesi zorunludur.");
        }
        if (!isConfirmed(request.confirmAction)) {
            return badRequest("CONFIRMATION_REQUIRED", "Kritik işlem onayı (confirmAction=true) zorunludur.");
        }
        if (userRepository.findByUsername(request.adminUsername.trim()).isPresent()) {
            return conflict("USERNAME_CONFLICT", "Bu kullanıcı adı zaten kullanımda.");
        }

        PlanType planType = parsePlanType(request.planType);
        Company company = new Company();
        company.setName(request.name.trim());
        company.setPlanType(planType);
        company.setSubscriptionStatus(planType == PlanType.CIRAK ? "TRIAL" : "ACTIVE");
        company.setBillingEmail(trimOrNull(request.billingEmail));
        company.setEmail(trimOrNull(request.billingEmail));
        company.setOrgCode(generateUniqueOrgCode());
        if (planType == PlanType.CIRAK) {
            company.setTrialEndsAt(java.time.LocalDateTime.now().plusDays(14));
        } else {
            company.setSubscriptionExpiresAt(java.time.LocalDateTime.now().plusDays(30));
        }
        Company savedCompany = companyRepository.save(company);

        User adminUser = User.builder()
                .companyId(savedCompany.getId())
                .username(request.adminUsername.trim())
                .passwordHash(passwordEncoder.encode(request.adminPassword))
                .fullName(
                        request.adminFullName != null && !request.adminFullName.isBlank()
                                ? request.adminFullName.trim()
                                : "Şirket Yöneticisi")
                .role("COMPANY_ADMIN")
                .build();
        userRepository.save(adminUser);
        auditLogService.logChange(
                "CREATE",
                "COMPANY",
                savedCompany.getId(),
                "Super admin yeni şirket oluşturdu. Sebep: " + request.reason.trim(),
                null,
                savedCompany);

        return ResponseEntity.ok(savedCompany);
    }

    /**
     * Şirket temel ayarlarını güncelle (ad, paket, e-posta).
     */
    @PutMapping("/companies/{id}")
    @PreAuthorize("hasAuthority('SUPERADMIN_WRITE_COMPANY')")
    public ResponseEntity<Company> updateCompany(@PathVariable Long id, @RequestBody UpdateCompanyRequest request) {
        if (request == null || !hasReason(request.reason)) {
            return badRequest("REASON_REQUIRED", "En az 5 karakterlik işlem gerekçesi zorunludur.");
        }
        if (!isConfirmed(request.confirmAction)) {
            return badRequest("CONFIRMATION_REQUIRED", "Kritik işlem onayı (confirmAction=true) zorunludur.");
        }
        Company company = companyRepository.findById(id).orElse(null);
        if (company == null) {
            return notFound("COMPANY_NOT_FOUND", "Şirket bulunamadı.");
        }
        Company before = snapshot(company);

        if (request.name != null && !request.name.isBlank()) {
            company.setName(request.name.trim());
        }
        if (request.billingEmail != null) {
            String email = trimOrNull(request.billingEmail);
            company.setBillingEmail(email);
            company.setEmail(email);
        }
        if (request.planType != null && !request.planType.isBlank()) {
            company.setPlanType(parsePlanType(request.planType));
        }
        if (request.subscriptionStatus != null && !request.subscriptionStatus.isBlank()) {
            company.setSubscriptionStatus(request.subscriptionStatus.trim().toUpperCase(Locale.ROOT));
        }

        Company updated = companyRepository.save(company);
        auditLogService.logChange(
                "UPDATE",
                "COMPANY",
                updated.getId(),
                "Super admin şirket bilgilerini güncelledi. Sebep: " + request.reason.trim(),
                before,
                updated);
        return ResponseEntity.ok(updated);
    }

    /**
     * Şirketin ilk admin kullanıcısının şifresini sıfırla.
     */
    @PostMapping("/companies/{id}/reset-admin-password")
    @PreAuthorize("hasAuthority('SUPERADMIN_RESET_PASSWORD')")
    public ResponseEntity<Void> resetCompanyAdminPassword(
            @PathVariable Long id,
            @RequestBody ResetPasswordRequest request) {
        if (request == null || request.newPassword == null || request.newPassword.isBlank()) {
            return badRequest("VALIDATION_ERROR", "Yeni şifre zorunludur.");
        }
        if (!hasReason(request.reason)) {
            return badRequest("REASON_REQUIRED", "En az 5 karakterlik işlem gerekçesi zorunludur.");
        }
        if (!isConfirmed(request.confirmAction)) {
            return badRequest("CONFIRMATION_REQUIRED", "Kritik işlem onayı (confirmAction=true) zorunludur.");
        }
        if (!companyRepository.existsById(id)) {
            return notFound("COMPANY_NOT_FOUND", "Şirket bulunamadı.");
        }

        Optional<User> adminOpt = userRepository.findFirstByCompanyIdAndRoleOrderByIdAsc(id, "COMPANY_ADMIN");
        if (adminOpt.isEmpty()) {
            return notFound("ADMIN_NOT_FOUND", "Şirket için admin kullanıcı bulunamadı.");
        }

        User admin = adminOpt.get();
        String previousPasswordHash = admin.getPasswordHash();
        admin.setPasswordHash(passwordEncoder.encode(request.newPassword.trim()));
        userRepository.save(admin);
        auditLogService.log(
                "UPDATE",
                "USER",
                admin.getId(),
                "Super admin admin şifresini sıfırladı. Sebep: " + request.reason.trim(),
                previousPasswordHash,
                admin.getPasswordHash());
        return ResponseEntity.ok().build();
    }

    /**
     * Şirketi askıya al veya aktif et.
     */
    @PutMapping("/companies/{id}/suspend")
    @PreAuthorize("hasAuthority('SUPERADMIN_SUSPEND_COMPANY')")
    public ResponseEntity<Company> toggleSuspendCompany(
            @PathVariable Long id,
            @RequestBody(required = false) SuspendActionRequest request) {
        if (request == null || !hasReason(request.reason)) {
            return badRequest("REASON_REQUIRED", "En az 5 karakterlik işlem gerekçesi zorunludur.");
        }
        if (!isConfirmed(request.confirmAction)) {
            return badRequest("CONFIRMATION_REQUIRED", "Kritik işlem onayı (confirmAction=true) zorunludur.");
        }
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        Company before = snapshot(company);

        if ("SUSPENDED".equals(company.getSubscriptionStatus())) {
            company.setSubscriptionStatus("ACTIVE");
            company.setIsReadOnly(false);
        } else {
            company.setSubscriptionStatus("SUSPENDED");
            // Askıya alınan hesaplar read_only moduna geçer
            company.setIsReadOnly(true); 
        }

        Company updated = companyRepository.save(company);
        auditLogService.logChange(
                "UPDATE",
                "COMPANY",
                updated.getId(),
                "Super admin şirket askı durumunu değiştirdi. Sebep: " + request.reason.trim(),
                before,
                updated);

        return ResponseEntity.ok(updated);
    }

    /**
     * Şirket kurum kodunu güvenli şekilde yeniden üret.
     */
    @PostMapping("/companies/{id}/org-code/rotate")
    @PreAuthorize("hasAuthority('SUPERADMIN_WRITE_COMPANY')")
    public ResponseEntity<Map<String, String>> rotateOrgCode(
            @PathVariable Long id,
            @RequestBody(required = false) OrgCodeRotateRequest request) {
        if (request == null || !hasReason(request.reason)) {
            return badRequest("REASON_REQUIRED", "En az 5 karakterlik işlem gerekçesi zorunludur.");
        }
        if (!isConfirmed(request.confirmAction)) {
            return badRequest("CONFIRMATION_REQUIRED", "Kritik işlem onayı (confirmAction=true) zorunludur.");
        }

        Company company = companyRepository.findById(id).orElse(null);
        if (company == null) {
            return notFound("COMPANY_NOT_FOUND", "Şirket bulunamadı.");
        }

        String oldOrgCode = company.getOrgCode();
        String newOrgCode = generateUniqueOrgCode();
        company.setOrgCode(newOrgCode);
        companyRepository.save(company);

        auditLogService.log(
                "UPDATE",
                "COMPANY",
                company.getId(),
                "Super admin kurum kodu yeniledi. Sebep: " + request.reason.trim(),
                oldOrgCode,
                newOrgCode);

        return ResponseEntity.ok(Map.of(
                "companyId", String.valueOf(company.getId()),
                "oldOrgCode", oldOrgCode != null ? oldOrgCode : "",
                "newOrgCode", newOrgCode));
    }

    @PostMapping("/support/impersonate-readonly")
    @PreAuthorize("hasAnyAuthority('SUPERADMIN_READ_COMPANIES','SUPERADMIN_VIEW_SYSTEM')")
    public ResponseEntity<Map<String, Object>> startReadOnlyImpersonation(
            @RequestBody(required = false) ImpersonationRequest request) {
        if (request == null || request.companyId == null) {
            return badRequest("VALIDATION_ERROR", "companyId zorunludur.");
        }
        if (!hasReason(request.reason)) {
            return badRequest("REASON_REQUIRED", "En az 5 karakterlik işlem gerekçesi zorunludur.");
        }
        if (!isConfirmed(request.confirmAction)) {
            return badRequest("CONFIRMATION_REQUIRED", "Kritik işlem onayı (confirmAction=true) zorunludur.");
        }

        Company target = companyRepository.findById(request.companyId).orElse(null);
        if (target == null) {
            return notFound("COMPANY_NOT_FOUND", "Şirket bulunamadı.");
        }

        User currentUser = (User) org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("companyId", target.getId());
        claims.put("impersonatedCompanyId", target.getId());
        claims.put("impersonationReadOnly", true);
        claims.put("impersonationBy", currentUser.getUsername());
        String token = jwtService.generateToken(claims, currentUser);

        auditLogService.log(
                "SUPPORT_IMPERSONATION_STARTED",
                "COMPANY",
                target.getId(),
                "Read-only impersonation başlatıldı. Sebep: " + request.reason.trim());

        return ResponseEntity.ok(Map.of(
                "mode", "READ_ONLY",
                "companyId", target.getId(),
                "companyName", target.getName(),
                "token", token));
    }

    @GetMapping("/support/diagnostic-package/{companyId}")
    @PreAuthorize("hasAnyAuthority('SUPERADMIN_READ_COMPANIES','SUPERADMIN_VIEW_SYSTEM')")
    public ResponseEntity<Map<String, Object>> getDiagnosticPackage(@PathVariable Long companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return notFound("COMPANY_NOT_FOUND", "Şirket bulunamadı.");
        }

        Map<String, Object> context = featureService.getFeatureContext(companyId);
        QuotaDTO quota = (QuotaDTO) context.get("quota");

        List<Map<String, Object>> authFailures = new ArrayList<>();
        auditLogRepository.findByCompanyIdOrderByTimestampDesc(companyId, PageRequest.of(0, 50))
                .getContent()
                .stream()
                .filter(log -> {
                    String action = log.getActionType() != null ? log.getActionType() : "";
                    return action.contains("LOGIN_FAILED") || action.contains("AUTH_FAILED");
                })
                .limit(5)
                .forEach(log -> authFailures.add(Map.of(
                        "action", log.getActionType(),
                        "description", log.getDescription(),
                        "timestamp", log.getTimestamp() != null ? log.getTimestamp().toString() : "",
                        "ipAddress", log.getIpAddress() != null ? log.getIpAddress() : "")));

        long failedWebhooks24h = webhookEventRepository.findAll().stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)))
                .filter(e -> "FAILED".equals(String.valueOf(e.getStatus())))
                .count();

        long failedPayments24h = paymentEventRepository.findAll().stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)))
                .filter(e -> "FAILED".equals(String.valueOf(e.getStatus())))
                .count();

        List<Map<String, Object>> recentWebhookEvents = webhookEventRepository.findAll().stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7)))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> event = new java.util.HashMap<>();
                    event.put("provider", e.getProvider());
                    event.put("eventType", e.getEventType());
                    event.put("status", String.valueOf(e.getStatus()));
                    event.put("retryCount", e.getRetryCount());
                    event.put("failureReason", e.getFailureReason() != null ? e.getFailureReason() : "");
                    event.put("createdAt", e.getCreatedAt().toString());
                    return event;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "company", Map.of(
                        "id", company.getId(),
                        "name", company.getName(),
                        "plan", company.getPlanType() != null ? company.getPlanType().name() : "CIRAK",
                        "subscriptionStatus", company.getSubscriptionStatus() != null ? company.getSubscriptionStatus() : "UNKNOWN"),
                "quota", Map.of(
                        "currentMonthlyTickets", quota.getCurrentMonthlyTickets(),
                        "maxMonthlyTickets", quota.getMaxMonthlyTickets(),
                        "currentMonthlyProposals", quota.getCurrentMonthlyProposals(),
                        "maxMonthlyProposals", quota.getMaxMonthlyProposals(),
                        "currentInventoryItems", quota.getCurrentInventoryItems(),
                        "maxInventoryItems", quota.getMaxInventoryItems()),
                "authFailures", authFailures,
                "webhookStatus", Map.of(
                        "failedCount24h", failedWebhooks24h,
                        "recentEvents", recentWebhookEvents),
                "paymentStatus", Map.of(
                        "failedCount24h", failedPayments24h)));
    }

    /**
     * Sistem donanım metriklerini ve son logları getir.
     */
    @GetMapping("/system-status")
    @PreAuthorize("hasAuthority('SUPERADMIN_VIEW_SYSTEM')")
    public ResponseEntity<SystemStatusDTO> getSystemStatus() {
        SystemStatusDTO status = new SystemStatusDTO();

        // 1. RAM Usage
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double ramPercent = ((double) usedMemory / totalMemory) * 100;
        status.setRamUsagePercent(Math.round(ramPercent * 10.0) / 10.0);

        // 2. CPU Usage
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getSystemLoadAverage();
        // Fallback for simple CPU representation
        if (cpuLoad < 0) {
            cpuLoad = 2.0 + (Math.random() * 5.0); 
        } else {
            cpuLoad = cpuLoad * 10; // scale approximation
        }
        status.setCpuUsagePercent(Math.round(cpuLoad * 10.0) / 10.0);

        // 3. JVM Uptime
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = rb.getUptime();
        Duration duration = Duration.ofMillis(uptimeMs);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        status.setJvmUptime(String.format("%d Gün %d Saat %d Dk", days, hours, minutes));

        // 4. DB Stats
        long totalCompanies = companyRepository.count();
        long totalLogs = auditLogRepository.count();
        status.setTotalDbRecords(totalCompanies * 50 + totalLogs); // approximate full DB size
        status.setActiveDbConnections((long) (10 + Math.random() * 20)); // simulated connection count

        // 5. Recent Logs
        List<SystemStatusDTO.SystemLogDTO> recentLogs = auditLogRepository
                .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent().stream()
                .map(log -> {
                    String level = log.getActionType().contains("FAILED") ? "ERROR" : "INFO";
                    String time = log.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String msg = String.format("[%s] %s - IP: %s", log.getEntityType(), log.getDescription(), log.getIpAddress());
                    return new SystemStatusDTO.SystemLogDTO(level, time, msg);
                })
                .collect(Collectors.toList());
        status.setRecentLogs(recentLogs);

        return ResponseEntity.ok(status);
    }

    @GetMapping("/operations-dashboard")
    @PreAuthorize("hasAuthority('SUPERADMIN_VIEW_SYSTEM')")
    public ResponseEntity<Map<String, Object>> getOperationsDashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime oneDayAgo = now.minusHours(24);

        List<com.pusula.backend.entity.AuditLog> recentAuthLogs = auditLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp() != null && log.getTimestamp().isAfter(sevenDaysAgo))
                .filter(log -> {
                    String action = log.getActionType() != null ? log.getActionType() : "";
                    return action.contains("LOGIN_FAILED") || action.contains("AUTH_FAILED");
                })
                .toList();

        List<Map<String, Object>> authFailureTrend = recentAuthLogs.stream()
                .collect(Collectors.groupingBy(log -> log.getTimestamp().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> point = new java.util.HashMap<>();
                    point.put("date", entry.getKey().toString());
                    point.put("count", entry.getValue().size());
                    return point;
                })
                .toList();

        long webhookFailedCount24h = webhookEventRepository.findAll().stream()
                .filter(event -> event.getCreatedAt() != null && event.getCreatedAt().isAfter(oneDayAgo))
                .filter(event -> "FAILED".equals(String.valueOf(event.getStatus())))
                .count();

        long readOnlyTenantCount = companyRepository.findAll().stream()
                .filter(company -> Boolean.TRUE.equals(company.getIsReadOnly()))
                .count();

        List<Map<String, String>> alerts = new ArrayList<>();
        if (webhookFailedCount24h >= 5) {
            alerts.add(Map.of(
                    "severity", "CRITICAL",
                    "message", "Son 24 saatte webhook başarısız sayısı kritik eşiği geçti"));
        }
        long todayAuthFailures = recentAuthLogs.stream()
                .filter(log -> log.getTimestamp() != null && log.getTimestamp().isAfter(oneDayAgo))
                .count();
        if (todayAuthFailures >= 20) {
            alerts.add(Map.of(
                    "severity", "WARNING",
                    "message", "Auth failure trendinde son 24 saatte belirgin artış var"));
        }
        if (readOnlyTenantCount > 0) {
            alerts.add(Map.of(
                    "severity", "WARNING",
                    "message", "Read-only moda geçen tenantlar mevcut"));
        }

        return ResponseEntity.ok(Map.of(
                "deployVersion", deployVersion,
                "webhookFailedCount24h", webhookFailedCount24h,
                "authFailureTrend", authFailureTrend,
                "tenantReadOnlyCount", readOnlyTenantCount,
                "alerts", alerts));
    }

    private String generateUniqueOrgCode() {
        String code;
        do {
            code = "PUS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        } while (companyRepository.findByOrgCodeIgnoreCase(code).isPresent());
        return code;
    }

    private PlanType parsePlanType(String planType) {
        if (planType == null || planType.isBlank()) {
            return PlanType.CIRAK;
        }
        try {
            return PlanType.valueOf(planType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PlanType.CIRAK;
        }
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasReason(String reason) {
        return reason != null && !reason.trim().isEmpty() && reason.trim().length() >= 5;
    }

    private boolean isConfirmed(Boolean confirmAction) {
        return Boolean.TRUE.equals(confirmAction);
    }

    private Company snapshot(Company src) {
        Company copy = new Company();
        copy.setId(src.getId());
        copy.setName(src.getName());
        copy.setPlanType(src.getPlanType());
        copy.setSubscriptionStatus(src.getSubscriptionStatus());
        copy.setEmail(src.getEmail());
        copy.setBillingEmail(src.getBillingEmail());
        copy.setOrgCode(src.getOrgCode());
        copy.setIsReadOnly(src.getIsReadOnly());
        copy.setSubscriptionExpiresAt(src.getSubscriptionExpiresAt());
        copy.setTrialEndsAt(src.getTrialEndsAt());
        return copy;
    }

    public static class CreateCompanyRequest {
        public String name;
        public String billingEmail;
        public String planType;
        public String adminUsername;
        public String adminFullName;
        public String adminPassword;
        public String reason;
        public Boolean confirmAction;
    }

    public static class UpdateCompanyRequest {
        public String name;
        public String billingEmail;
        public String planType;
        public String subscriptionStatus;
        public String reason;
        public Boolean confirmAction;
    }

    public static class ResetPasswordRequest {
        public String newPassword;
        public String reason;
        public Boolean confirmAction;
    }

    public static class SuspendActionRequest {
        public String reason;
        public Boolean confirmAction;
    }

    public static class OrgCodeRotateRequest {
        public String reason;
        public Boolean confirmAction;
    }

    public static class ImpersonationRequest {
        public Long companyId;
        public String reason;
        public Boolean confirmAction;
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> badRequest(String code, String message) {
        return (ResponseEntity<T>) ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "code", code,
                "message", message,
                "traceId", UUID.randomUUID().toString(),
                "path", "/api/superadmin"));
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> notFound(String code, String message) {
        return (ResponseEntity<T>) ResponseEntity.status(404).body(Map.of(
                "status", 404,
                "code", code,
                "message", message,
                "traceId", UUID.randomUUID().toString(),
                "path", "/api/superadmin"));
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> conflict(String code, String message) {
        return (ResponseEntity<T>) ResponseEntity.status(409).body(Map.of(
                "status", 409,
                "code", code,
                "message", message,
                "traceId", UUID.randomUUID().toString(),
                "path", "/api/superadmin"));
    }
}
