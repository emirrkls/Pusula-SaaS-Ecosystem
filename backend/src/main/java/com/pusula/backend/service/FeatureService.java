package com.pusula.backend.service;

import com.pusula.backend.dto.QuotaDTO;
import com.pusula.backend.entity.*;
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
 * Central service for SaaS feature flag and quota management.
 * Determines what features are available for a given tenant
 * and enforces usage quotas based on their subscription plan.
 */
@Service
public class FeatureService {

    private final CompanyRepository companyRepository;
    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final UsageTrackingRepository usageTrackingRepository;
    private final UserRepository userRepository;

    public FeatureService(CompanyRepository companyRepository,
                          PlanRepository planRepository,
                          PlanFeatureRepository planFeatureRepository,
                          UsageTrackingRepository usageTrackingRepository,
                          UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.planRepository = planRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.usageTrackingRepository = usageTrackingRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get the full feature context for a tenant —
     * feature flags, quota limits, current usage, and trial info.
     */
    public Map<String, Object> getFeatureContext(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Şirket bulunamadı: " + companyId));

        Map<String, Boolean> features = getFeatureFlags(company.getPlanType());
        QuotaDTO quota = getQuota(company);

        Map<String, Object> context = new HashMap<>();
        context.put("planType", company.getPlanType().name());
        context.put("features", features);
        context.put("quota", quota);
        context.put("isReadOnly", isReadOnly(company));
        context.put("trialDaysRemaining", calculateTrialDays(company));

        return context;
    }

    /**
     * Get feature flags for a plan type.
     * First tries DB lookup (plan_features table), falls back to hardcoded defaults.
     */
    public Map<String, Boolean> getFeatureFlags(PlanType planType) {
        // Try DB lookup first
        var planOpt = planRepository.findByName(planType.name());
        if (planOpt.isPresent()) {
            List<PlanFeature> dbFeatures = planFeatureRepository.findByPlanId(planOpt.get().getId());
            if (!dbFeatures.isEmpty()) {
                return dbFeatures.stream()
                        .collect(Collectors.toMap(PlanFeature::getFeatureKey, PlanFeature::isEnabled));
            }
        }

        // Fallback to hardcoded defaults
        return getDefaultFeatures(planType);
    }

    /**
     * Check if a specific feature is enabled for a tenant.
     * Throws FeatureNotAvailableException if not.
     */
    public void checkFeature(Long companyId, String featureKey) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Şirket bulunamadı"));

        Map<String, Boolean> features = getFeatureFlags(company.getPlanType());
        if (!features.getOrDefault(featureKey, false)) {
            throw new FeatureNotAvailableException(featureKey, company.getPlanType().name());
        }
    }

    /**
     * Check if a tenant has remaining quota for a usage type.
     * Throws QuotaExceededException if limit reached.
     */
    public void checkQuota(Long companyId, String usageType) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Şirket bulunamadı"));

        int limit = getQuotaLimit(company.getPlanType(), usageType);
        if (limit == -1) return; // Unlimited

        int current = getCurrentUsage(companyId, usageType);
        if (current >= limit) {
            throw new QuotaExceededException(usageType, limit, current);
        }
    }

    /**
     * Increment usage counter for a tenant.
     */
    public void incrementUsage(Long companyId, String usageType) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        UsageTracking tracking = usageTrackingRepository
                .findByCompanyIdAndUsageTypeAndPeriodStart(companyId, usageType, monthStart)
                .orElseGet(() -> {
                    UsageTracking t = new UsageTracking();
                    t.setCompanyId(companyId);
                    t.setUsageType(usageType);
                    t.setPeriodStart(monthStart);
                    t.setPeriodEnd(monthEnd);
                    t.setCurrentCount(0);
                    return t;
                });

        tracking.increment();
        usageTrackingRepository.save(tracking);
    }

    // ── Private Helpers ──────────────────────────────────────────────

    private QuotaDTO getQuota(Company company) {
        PlanType plan = company.getPlanType();
        QuotaDTO dto = new QuotaDTO();

        // Set limits based on plan
        switch (plan) {
            case CIRAK:
                dto.setMaxTechnicians(1);
                dto.setMaxCustomers(50);
                dto.setMaxMonthlyTickets(30);
                dto.setMaxMonthlyProposals(10);
                dto.setMaxInventoryItems(50);
                dto.setStorageLimitMb(100);
                break;
            case USTA:
                dto.setMaxTechnicians(5);
                dto.setMaxCustomers(500);
                dto.setMaxMonthlyTickets(300);
                dto.setMaxMonthlyProposals(100);
                dto.setMaxInventoryItems(500);
                dto.setStorageLimitMb(1000);
                break;
            case PATRON:
                return QuotaDTO.unlimited();
        }

        // Set current usage
        dto.setCurrentTechnicians((int) userRepository.countByCompanyIdAndRole(company.getId(), "TECHNICIAN"));
        dto.setCurrentMonthlyTickets(getCurrentUsage(company.getId(), "TICKETS"));
        dto.setCurrentMonthlyProposals(getCurrentUsage(company.getId(), "PROPOSALS"));

        return dto;
    }

    private int getQuotaLimit(PlanType plan, String usageType) {
        return switch (plan) {
            case CIRAK -> switch (usageType) {
                case "TICKETS" -> 30;
                case "PROPOSALS" -> 10;
                case "TECHNICIANS" -> 1;
                case "CUSTOMERS" -> 50;
                case "INVENTORY" -> 50;
                default -> -1;
            };
            case USTA -> switch (usageType) {
                case "TICKETS" -> 300;
                case "PROPOSALS" -> 100;
                case "TECHNICIANS" -> 5;
                case "CUSTOMERS" -> 500;
                case "INVENTORY" -> 500;
                default -> -1;
            };
            case PATRON -> -1; // Unlimited
        };
    }

    private int getCurrentUsage(Long companyId, String usageType) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        return usageTrackingRepository
                .findByCompanyIdAndUsageTypeAndPeriodStart(companyId, usageType, monthStart)
                .map(UsageTracking::getCurrentCount)
                .orElse(0);
    }

    private boolean isReadOnly(Company company) {
        if ("SUSPENDED".equals(company.getSubscriptionStatus())) return true;
        if ("TRIAL".equals(company.getSubscriptionStatus()) && company.getTrialEndsAt() != null) {
            return company.getTrialEndsAt().isBefore(LocalDateTime.now());
        }
        return false;
    }

    private Integer calculateTrialDays(Company company) {
        if (company.getTrialEndsAt() == null) return null;
        long days = ChronoUnit.DAYS.between(LocalDateTime.now(), company.getTrialEndsAt());
        return days > 0 ? (int) days : 0;
    }

    private Map<String, Boolean> getDefaultFeatures(PlanType planType) {
        Map<String, Boolean> features = new HashMap<>();
        features.put("SERVICE_TICKETS", true);
        features.put("CUSTOMER_MANAGEMENT", true);
        features.put("BASIC_INVENTORY", true);

        boolean isUsta = planType == PlanType.USTA || planType == PlanType.PATRON;
        boolean isPatron = planType == PlanType.PATRON;

        features.put("FINANCE_MODULE", isUsta);
        features.put("PDF_EXPORT", isUsta);
        features.put("PROPOSAL_MODULE", isUsta);
        features.put("VEHICLE_TRACKING", isUsta);
        features.put("AUDIT_LOGS", isUsta);
        features.put("WHATSAPP_INTEGRATION", isUsta);
        features.put("MULTI_TECHNICIAN", isUsta);
        features.put("DAILY_CLOSING", isUsta);
        features.put("COMMERCIAL_DEVICES", isPatron);
        features.put("COMPANY_DEBT_TRACKING", isPatron);
        features.put("CUSTOM_BRANDING", isPatron);

        return features;
    }
}
