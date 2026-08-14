package com.pusula.backend.service;

import com.pusula.backend.dto.QuotaDTO;
import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.Plan;
import com.pusula.backend.entity.PlanFeature;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.entity.UsageTracking;
import com.pusula.backend.exception.FeatureNotAvailableException;
import com.pusula.backend.exception.QuotaExceededException;
import com.pusula.backend.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Single backend authority for plan features, limits and current usage.
 * Plan definitions live in plans/plan_features; application code never embeds
 * package-specific numbers.
 */
@Service
public class FeatureService {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final CompanyRepository companyRepository;
    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final UsageTrackingRepository usageTrackingRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final InventoryRepository inventoryRepository;
    private final ServiceTicketRepository ticketRepository;
    private final ProposalRepository proposalRepository;
    private final VehicleRepository vehicleRepository;
    private final CommercialDeviceRepository commercialDeviceRepository;
    private final StorageUsageService storageUsageService;

    public FeatureService(CompanyRepository companyRepository,
                          PlanRepository planRepository,
                          PlanFeatureRepository planFeatureRepository,
                          UsageTrackingRepository usageTrackingRepository,
                          UserRepository userRepository,
                          CustomerRepository customerRepository,
                          InventoryRepository inventoryRepository,
                          ServiceTicketRepository ticketRepository,
                          ProposalRepository proposalRepository,
                          VehicleRepository vehicleRepository,
                          CommercialDeviceRepository commercialDeviceRepository,
                          StorageUsageService storageUsageService) {
        this.companyRepository = companyRepository;
        this.planRepository = planRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.usageTrackingRepository = usageTrackingRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.inventoryRepository = inventoryRepository;
        this.ticketRepository = ticketRepository;
        this.proposalRepository = proposalRepository;
        this.vehicleRepository = vehicleRepository;
        this.commercialDeviceRepository = commercialDeviceRepository;
        this.storageUsageService = storageUsageService;
    }

    public Map<String, Object> getFeatureContext(Long companyId) {
        Company company = getCompany(companyId);
        Map<String, Object> context = new HashMap<>();
        context.put("planType", company.getPlanType().name());
        context.put("features", getFeatureFlags(company.getPlanType()));
        context.put("quota", getQuota(companyId));
        context.put("isReadOnly", isReadOnly(company));
        context.put("trialDaysRemaining", calculateTrialDays(company));
        return context;
    }

    public Map<String, Boolean> getFeatureFlags(PlanType planType) {
        Plan plan = getPlan(planType);
        List<PlanFeature> features = planFeatureRepository.findByPlanId(plan.getId());
        if (features.isEmpty()) {
            throw new IllegalStateException("Paket özellikleri tanımlı değil: " + planType.name());
        }
        return features.stream().collect(Collectors.toMap(
                PlanFeature::getFeatureKey,
                PlanFeature::isEnabled,
                (first, second) -> second));
    }

    public void checkFeature(Long companyId, String featureKey) {
        Company company = getCompany(companyId);
        if (!getFeatureFlags(company.getPlanType()).getOrDefault(featureKey, false)) {
            throw new FeatureNotAvailableException(featureKey, company.getPlanType().name());
        }
    }

    public void checkQuota(Long companyId, String usageType) {
        Company company = getCompany(companyId);
        Plan plan = getPlan(company.getPlanType());
        int limit = getQuotaLimit(plan, usageType);
        int current = getCurrentUsage(companyId, usageType);
        if (limit >= 0 && current >= limit) {
            throw new QuotaExceededException(usageType, limit, current);
        }
    }

    public void checkStorageQuota(Long companyId, long additionalBytes) {
        Plan plan = getPlan(getCompany(companyId).getPlanType());
        Integer limitMb = plan.getStorageLimitMb();
        if (limitMb == null) {
            return;
        }
        long currentBytes = storageUsageService.getUsageBytes(companyId);
        long limitBytes = limitMb.longValue() * BYTES_PER_MB;
        if (additionalBytes > 0 && currentBytes + additionalBytes > limitBytes) {
            int currentMb = (int) Math.ceil(currentBytes / (double) BYTES_PER_MB);
            throw new QuotaExceededException("STORAGE", limitMb, currentMb);
        }
    }

    public QuotaDTO getQuota(Long companyId) {
        Company company = getCompany(companyId);
        Plan plan = getPlan(company.getPlanType());
        QuotaDTO dto = new QuotaDTO();
        dto.setMaxCompanyAdmins(limitValue(plan.getMaxCompanyAdmins()));
        dto.setMaxTechnicians(limitValue(plan.getMaxTechnicians()));
        dto.setMaxCustomers(limitValue(plan.getMaxCustomers()));
        dto.setMaxMonthlyTickets(limitValue(plan.getMaxMonthlyTickets()));
        dto.setMaxMonthlyProposals(limitValue(plan.getMaxMonthlyProposals()));
        dto.setMaxInventoryItems(limitValue(plan.getMaxInventoryItems()));
        dto.setStorageLimitMb(limitValue(plan.getStorageLimitMb()));
        dto.setMaxVehicles(limitValue(plan.getMaxVehicles()));
        dto.setMaxCommercialDevices(limitValue(plan.getMaxCommercialDevices()));
        dto.setAuditRetentionDays(plan.getAuditRetentionDays());

        dto.setCurrentCompanyAdmins(getCurrentUsage(companyId, "COMPANY_ADMINS"));
        dto.setCurrentTechnicians(getCurrentUsage(companyId, "TECHNICIANS"));
        dto.setCurrentCustomers(getCurrentUsage(companyId, "CUSTOMERS"));
        dto.setCurrentMonthlyTickets(getCurrentUsage(companyId, "TICKETS"));
        dto.setCurrentMonthlyProposals(getCurrentUsage(companyId, "PROPOSALS"));
        dto.setCurrentInventoryItems(getCurrentUsage(companyId, "INVENTORY"));
        dto.setCurrentStorageMb(getCurrentUsage(companyId, "STORAGE"));
        dto.setCurrentVehicles(getCurrentUsage(companyId, "VEHICLES"));
        dto.setCurrentCommercialDevices(getCurrentUsage(companyId, "COMMERCIAL_DEVICES"));
        return dto;
    }

    public Integer getAuditRetentionDays(Long companyId) {
        return getPlan(getCompany(companyId).getPlanType()).getAuditRetentionDays();
    }

    /** Legacy usage ledger retained for analytics; enforcement uses real data. */
    public void incrementUsage(Long companyId, String usageType) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        UsageTracking tracking = usageTrackingRepository
                .findByCompanyIdAndUsageTypeAndPeriodStart(companyId, usageType, monthStart)
                .orElseGet(() -> {
                    UsageTracking item = new UsageTracking();
                    item.setCompanyId(companyId);
                    item.setUsageType(usageType);
                    item.setPeriodStart(monthStart);
                    item.setPeriodEnd(monthEnd);
                    item.setCurrentCount(0);
                    return item;
                });
        tracking.increment();
        usageTrackingRepository.save(tracking);
    }

    private int getQuotaLimit(Plan plan, String usageType) {
        Integer limit = switch (usageType) {
            case "COMPANY_ADMINS" -> plan.getMaxCompanyAdmins();
            case "TECHNICIANS" -> plan.getMaxTechnicians();
            case "CUSTOMERS" -> plan.getMaxCustomers();
            case "TICKETS" -> plan.getMaxMonthlyTickets();
            case "PROPOSALS" -> plan.getMaxMonthlyProposals();
            case "INVENTORY" -> plan.getMaxInventoryItems();
            case "STORAGE" -> plan.getStorageLimitMb();
            case "VEHICLES" -> plan.getMaxVehicles();
            case "COMMERCIAL_DEVICES" -> plan.getMaxCommercialDevices();
            default -> throw new IllegalArgumentException("Bilinmeyen kota türü: " + usageType);
        };
        return limitValue(limit);
    }

    private int getCurrentUsage(Long companyId, String usageType) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonth = monthStart.plusMonths(1);
        long usage = switch (usageType) {
            case "COMPANY_ADMINS" -> userRepository.countByCompanyIdAndRole(companyId, "COMPANY_ADMIN");
            case "TECHNICIANS" -> userRepository.countByCompanyIdAndRole(companyId, "TECHNICIAN");
            case "CUSTOMERS" -> customerRepository.countByCompanyId(companyId);
            case "TICKETS" -> ticketRepository.countByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    companyId, monthStart, nextMonth);
            case "PROPOSALS" -> proposalRepository.countByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    companyId, monthStart, nextMonth);
            case "INVENTORY" -> inventoryRepository.countByCompanyId(companyId);
            case "STORAGE" -> (long) Math.ceil(storageUsageService.getUsageBytes(companyId) / (double) BYTES_PER_MB);
            case "VEHICLES" -> vehicleRepository.countByCompanyIdAndIsActiveTrue(companyId);
            case "COMMERCIAL_DEVICES" -> commercialDeviceRepository.countByCompanyId(companyId);
            default -> throw new IllegalArgumentException("Bilinmeyen kota türü: " + usageType);
        };
        return usage > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) usage;
    }

    private int limitValue(Integer value) {
        return value == null ? -1 : value;
    }

    private Company getCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Şirket bulunamadı: " + companyId));
    }

    private Plan getPlan(PlanType planType) {
        return planRepository.findByName(planType.name())
                .orElseThrow(() -> new IllegalStateException("Paket tanımı bulunamadı: " + planType.name()));
    }

    private boolean isReadOnly(Company company) {
        if ("SUSPENDED".equals(company.getSubscriptionStatus())) return true;
        return "TRIAL".equals(company.getSubscriptionStatus())
                && company.getTrialEndsAt() != null
                && company.getTrialEndsAt().isBefore(LocalDateTime.now());
    }

    private Integer calculateTrialDays(Company company) {
        if (company.getTrialEndsAt() == null) return null;
        long days = ChronoUnit.DAYS.between(LocalDateTime.now(), company.getTrialEndsAt());
        return days > 0 ? (int) days : 0;
    }
}
