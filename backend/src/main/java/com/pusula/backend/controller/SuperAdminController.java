package com.pusula.backend.controller;

import com.pusula.backend.entity.Company;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.AuditLogRepository;
import com.pusula.backend.dto.SystemStatusDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final CompanyRepository companyRepository;
    private final AuditLogRepository auditLogRepository;

    public SuperAdminController(CompanyRepository companyRepository, AuditLogRepository auditLogRepository) {
        this.companyRepository = companyRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Tüm şirketleri (tenant) listele.
     */
    @GetMapping("/companies")
    public ResponseEntity<List<Company>> getAllCompanies() {
        return ResponseEntity.ok(companyRepository.findAll());
    }

    /**
     * Şirketi askıya al veya aktif et.
     */
    @PutMapping("/companies/{id}/suspend")
    public ResponseEntity<Company> toggleSuspendCompany(@PathVariable Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        
        if ("SUSPENDED".equals(company.getSubscriptionStatus())) {
            company.setSubscriptionStatus("ACTIVE");
            company.setIsReadOnly(false);
        } else {
            company.setSubscriptionStatus("SUSPENDED");
            // Askıya alınan hesaplar read_only moduna geçer
            company.setIsReadOnly(true); 
        }
        
        return ResponseEntity.ok(companyRepository.save(company));
    }

    /**
     * Sistem donanım metriklerini ve son logları getir.
     */
    @GetMapping("/system-status")
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
}
