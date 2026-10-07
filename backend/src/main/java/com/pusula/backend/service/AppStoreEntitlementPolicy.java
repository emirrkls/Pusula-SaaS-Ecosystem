package com.pusula.backend.service;

import com.pusula.backend.entity.Company;
import java.time.LocalDateTime;
import java.util.Set;

/** Ordering and administrative restrictions shared by device verification and notifications. */
final class AppStoreEntitlementPolicy {
    private AppStoreEntitlementPolicy() {}

    static boolean isStale(Company company, LocalDateTime purchaseDate, LocalDateTime expiresDate,
            Long signedDate, String environment) {
        if (company.getAppStoreEnvironment() != null
                && !company.getAppStoreEnvironment().equals(environment)) return true;
        if (company.getAppStorePurchaseDate() != null && purchaseDate != null) {
            int comparison = purchaseDate.compareTo(company.getAppStorePurchaseDate());
            if (comparison < 0) return true;
            if (comparison > 0) return false;
        } else if (company.getSubscriptionExpiresAt() != null && expiresDate != null
                && expiresDate.isBefore(company.getSubscriptionExpiresAt())) {
            // Existing bindings predating V44 have no transaction snapshot yet.
            return true;
        }
        return company.getAppStoreSignedDate() != null
                && (signedDate == null || signedDate <= company.getAppStoreSignedDate());
    }

    static boolean manuallyRestricted(Company company) {
        return "SUSPENDED".equals(company.getSubscriptionStatus())
                || (company.getIsReadOnly() && !Set.of("EXPIRED", "REVOKED", "PAYMENT_FAILED")
                        .contains(company.getSubscriptionStatus()));
    }
}
