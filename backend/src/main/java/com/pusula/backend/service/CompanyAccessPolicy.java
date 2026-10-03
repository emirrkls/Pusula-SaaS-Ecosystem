package com.pusula.backend.service;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Shared access rules for authentication, feature gates and subscriptions. */
public final class CompanyAccessPolicy {
    private CompanyAccessPolicy() {}

    public static void initializeFreePlan(Company company) {
        company.setPlanType(PlanType.CIRAK);
        company.setSubscriptionStatus("ACTIVE");
        company.setTrialEndsAt(null);
        company.setSubscriptionExpiresAt(null);
        company.setIsReadOnly(false);
    }

    public static boolean isReadOnly(Company company) {
        if (company == null) return false;
        if (company.getIsReadOnly() || "SUSPENDED".equals(company.getSubscriptionStatus())) return true;
        return company.getPlanType() != PlanType.CIRAK
                && "TRIAL".equals(company.getSubscriptionStatus())
                && company.getTrialEndsAt() != null
                && !company.getTrialEndsAt().isAfter(LocalDateTime.now());
    }

    public static Integer trialDaysRemaining(Company company) {
        if (company == null || company.getPlanType() == PlanType.CIRAK
                || !"TRIAL".equals(company.getSubscriptionStatus()) || company.getTrialEndsAt() == null) return null;
        return Math.max(0, (int) ChronoUnit.DAYS.between(LocalDateTime.now(), company.getTrialEndsAt()));
    }
}
