package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class CompanyAccessPolicyTest {
    @Test void freePlanIgnoresLegacyTrialExpiryButHonorsManualRestrictions() {
        var c = company(PlanType.CIRAK, "TRIAL"); c.setTrialEndsAt(LocalDateTime.now().minusDays(30));
        assertFalse(CompanyAccessPolicy.isReadOnly(c));
        assertNull(CompanyAccessPolicy.trialDaysRemaining(c));
        c.setSubscriptionStatus("SUSPENDED"); assertTrue(CompanyAccessPolicy.isReadOnly(c));
        c.setSubscriptionStatus("ACTIVE"); c.setIsReadOnly(true); assertTrue(CompanyAccessPolicy.isReadOnly(c));
    }
    @Test void paidTrialAndStoredExpiryRestrictionRemainEffective() {
        var c = company(PlanType.PATRON, "TRIAL"); c.setTrialEndsAt(LocalDateTime.now().minusMinutes(1));
        assertTrue(CompanyAccessPolicy.isReadOnly(c)); assertEquals(0, CompanyAccessPolicy.trialDaysRemaining(c));
        c.setTrialEndsAt(LocalDateTime.now().plusDays(3)); assertFalse(CompanyAccessPolicy.isReadOnly(c));
        c.setSubscriptionStatus("EXPIRED"); c.setIsReadOnly(true); assertTrue(CompanyAccessPolicy.isReadOnly(c));
    }
    @Test void freeRegistrationHasNoEndDate() {
        var c = company(PlanType.USTA, "TRIAL"); c.setTrialEndsAt(LocalDateTime.now()); c.setIsReadOnly(true);
        CompanyAccessPolicy.initializeFreePlan(c);
        assertEquals("ACTIVE", c.getSubscriptionStatus()); assertEquals(PlanType.CIRAK, c.getPlanType());
        assertNull(c.getTrialEndsAt()); assertNull(c.getSubscriptionExpiresAt()); assertFalse(c.getIsReadOnly());
    }
    private Company company(PlanType plan, String status) { var c = new Company(); c.setPlanType(plan); c.setSubscriptionStatus(status); return c; }
}
