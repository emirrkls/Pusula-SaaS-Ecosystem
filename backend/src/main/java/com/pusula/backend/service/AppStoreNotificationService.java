package com.pusula.backend.service;

import com.pusula.backend.entity.AppStoreNotification;
import com.pusula.backend.repository.AppStoreNotificationRepository;
import com.pusula.backend.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Service
public class AppStoreNotificationService {
    private static final Set<String> ENTITLEMENT_EVENTS = Set.of("SUBSCRIBED", "DID_RENEW", "OFFER_REDEEMED",
            "DID_CHANGE_RENEWAL_PREF", "DID_CHANGE_RENEWAL_STATUS", "DID_FAIL_TO_RENEW", "EXPIRED",
            "GRACE_PERIOD_EXPIRED", "REFUND", "REVOKE", "REFUND_REVERSED", "RENEWAL_EXTENDED");
    private final AppleAppStoreVerificationService verifier;
    private final AppStoreNotificationRepository events;
    private final CompanyRepository companies;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AppStoreNotificationService(AppleAppStoreVerificationService verifier,
            AppStoreNotificationRepository events, CompanyRepository companies) {
        this(verifier, events, companies, Clock.systemUTC());
    }

    AppStoreNotificationService(AppleAppStoreVerificationService verifier,
            AppStoreNotificationRepository events, CompanyRepository companies, Clock clock) {
        this.verifier = verifier; this.events = events; this.companies = companies; this.clock = clock;
    }

    @Transactional
    public void receive(String signedPayload) {
        // Verify the outer JWS, nested transaction and renewal BEFORE persistence/lookup.
        var verified = verifier.verifyNotification(signedPayload);
        var existing = events.lockById(verified.notificationId());
        if (existing.isPresent()) {
            process(existing.get());
            return;
        }
        var transaction = verified.transaction();
        var event = new AppStoreNotification(verified,
                transaction == null ? null : hash(transaction.originalTransactionId()),
                transaction == null ? null : hash(transaction.transactionId()), now());
        events.saveAndFlush(event);
        // Unique UUID/version protects concurrent deliveries; a race returns 5xx for retry.
        process(event);
    }

    @Transactional
    public void retry(String id) {
        events.lockById(id).ifPresent(this::process);
    }

    private void process(AppStoreNotification event) {
        if (!"WAITING_OWNER".equals(event.getProcessingStatus())) return;
        if (!ENTITLEMENT_EVENTS.contains(event.getType()) || event.getOriginalTransactionHash() == null) {
            event.finish("IGNORED", null, now()); return;
        }
        // Never trust a client-supplied company ID or create an account from a notification.
        String subscriptionId = "appstore:" + event.getOriginalTransactionHash();
        var owner = companies.findBySubscriptionProviderAndExternalSubscriptionId("APP_STORE", subscriptionId);
        if (owner.isEmpty()) {
            // A first purchase notification can precede the authenticated binding request.
            if (event.getReceivedAt().plusDays(30).isBefore(now())) event.finish("UNBOUND", null, now());
            else event.retryAfter(now().plusMinutes(5));
            return;
        }
        var company = companies.lockById(owner.get().getId()).orElseThrow();
        // Re-check under the company lock; another payment provider/binding may have won.
        if (!"APP_STORE".equals(company.getSubscriptionProvider())
                || !subscriptionId.equals(company.getExternalSubscriptionId())
                || AppStoreEntitlementPolicy.isStale(company, event.getPurchaseDate(), event.getExpiresDate(),
                        event.getSignedDate(), event.getEnvironment())) {
            event.finish("STALE", company.getId(), now()); return;
        }
        boolean manualRestriction = AppStoreEntitlementPolicy.manuallyRestricted(company);
        LocalDateTime expiresAt = event.getExpiresDate();
        boolean revoked = event.isRevoked() || "REVOKED".equals(event.getSubscriptionStatus())
                || "REVOKE".equals(event.getType());
        if (revoked && company.getAppStoreTransactionHash() != null
                && !company.getAppStoreTransactionHash().equals(event.getTransactionHash())) {
            event.finish("STALE", company.getId(), now()); return;
        }
        boolean grace = !revoked && ("BILLING_GRACE_PERIOD".equals(event.getSubscriptionStatus())
                || "GRACE_PERIOD".equals(event.getSubtype())) && event.getGraceExpiresDate() != null;
        if (grace && event.getGraceExpiresDate().isAfter(expiresAt)) expiresAt = event.getGraceExpiresDate();
        boolean active = !revoked && expiresAt.isAfter(now());
        company.setPlanType(event.getPlanType());
        company.setSubscriptionExpiresAt(expiresAt);
        company.setAppStoreTransactionHash(event.getTransactionHash());
        company.setAppStorePurchaseDate(event.getPurchaseDate());
        company.setAppStoreSignedDate(event.getSignedDate());
        company.setAppStoreEnvironment(event.getEnvironment());
        if (!manualRestriction) {
            company.setIsReadOnly(!active);
            company.setSubscriptionStatus(revoked ? "REVOKED" : active ? "ACTIVE" : "EXPIRED");
        }
        // Turning auto-renew off does not cancel an already-paid period; scheduled
        // downgrade uses the CURRENT transaction's product, not autoRenewProductId.
        companies.saveAndFlush(company);
        event.finish("PROCESSED", company.getId(), now());
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
