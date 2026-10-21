package com.pusula.backend.service;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PaymentEvent;
import com.pusula.backend.entity.PaymentEventStatus;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.PaymentEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages subscription lifecycle: upgrades, downgrades, expiry, and read-only enforcement.
 * Integrates with Iyzico/PayTR payment providers.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final String APP_STORE_PROVIDER = "APP_STORE";
    private static final String GOOGLE_PLAY_PROVIDER = "GOOGLE_PLAY";
    private static final long SUBSCRIPTION_GRACE_PERIOD_DAYS = 7;
    private static final Map<String, PlanType> GOOGLE_PRODUCT_PLANS = Map.of(
            "usta", PlanType.USTA,
            "patron", PlanType.PATRON
    );

    private final CompanyRepository companyRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final GooglePlayVerificationService googlePlayVerificationService;
    private final AppleAppStoreVerificationService appleAppStoreVerificationService;
    private final AuditLogService auditLogService;
    private final GooglePlayReceiptStore googleReceipts;

    public SubscriptionService(
            CompanyRepository companyRepository,
            PaymentEventRepository paymentEventRepository,
            GooglePlayVerificationService googlePlayVerificationService,
            AppleAppStoreVerificationService appleAppStoreVerificationService,
            AuditLogService auditLogService) {
        this(companyRepository, paymentEventRepository, googlePlayVerificationService,
                appleAppStoreVerificationService, auditLogService, null);
    }

    @Autowired
    public SubscriptionService(CompanyRepository companyRepository, PaymentEventRepository paymentEventRepository,
            GooglePlayVerificationService googlePlayVerificationService,
            AppleAppStoreVerificationService appleAppStoreVerificationService, AuditLogService auditLogService,
            GooglePlayReceiptStore googleReceipts) {
        this.companyRepository = companyRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.googlePlayVerificationService = googlePlayVerificationService;
        this.appleAppStoreVerificationService = appleAppStoreVerificationService;
        this.auditLogService = auditLogService;
        this.googleReceipts = googleReceipts;
    }

    /**
     * Upgrade a company's plan after successful payment.
     * Called by PaymentController when Iyzico webhook confirms payment.
     */
    @Transactional
    public Company upgradePlan(Long companyId, PlanType newPlan, String iyzicoSubscriptionId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + companyId));

        PlanType oldPlan = company.getPlanType();
        company.setPlanType(newPlan);
        company.setIsReadOnly(false);
        company.setSubscriptionStatus("ACTIVE");
        company.setIyzicoSubscriptionId(iyzicoSubscriptionId);
        company.setSubscriptionProvider("IYZICO");
        company.setExternalSubscriptionId(iyzicoSubscriptionId);

        // Set subscription expiry to 30 days from now
        company.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));

        Company saved = companyRepository.save(company);
        log.info("Plan upgraded: companyId={}, {} → {}, subscriptionId={}",
                companyId, oldPlan, newPlan, iyzicoSubscriptionId);

        return saved;
    }

    @Transactional
    public GoogleVerifyResult verifyGooglePurchaseAndUpgradePlan(
            Long companyId,
            String purchaseToken,
            String productId) {
        requireAllowedGoogleProduct(productId);
        String tokenHash = sha256(purchaseToken);
        Optional<PaymentEvent> existingOpt = paymentEventRepository
                .findByProviderAndTokenHash(GOOGLE_PLAY_PROVIDER, tokenHash);

        if (existingOpt.isPresent()) {
            PaymentEvent existing = existingOpt.get();
            if (!companyId.equals(existing.getCompanyId())) {
                auditLogService.log(
                        "SUBSCRIPTION_GOOGLE_VERIFY_CONFLICT",
                        "PAYMENT_EVENT",
                        existing.getId(),
                        "Google purchase token baska bir company tarafindan kullanilmis");
                throw new PaymentOwnershipException();
            }
        }
        if (existingOpt.isPresent() && "GOOGLE_REPLACED".equals(existingOpt.get().getFailureReason()))
            return new GoogleVerifyResult(false, true, null, null, "receipt_replaced");
        // A renewal reuses the token. Re-check Google even on replay, and allow retry after an
        // outage. Serialize updates within a tenant; the unique receipt index protects owners.
        Company company = companyRepository.lockById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        PaymentEvent paymentEvent = existingOpt.orElseGet(PaymentEvent::new);
        paymentEvent.setCompanyId(companyId);
        paymentEvent.setProvider(GOOGLE_PLAY_PROVIDER);
        paymentEvent.setEventType("SUBSCRIPTION_VERIFY");
        paymentEvent.setTokenHash(tokenHash);
        paymentEvent.setPurchaseTokenMasked(maskToken(purchaseToken));
        if (existingOpt.isEmpty()) {
            paymentEvent.setStatus(PaymentEventStatus.RECEIVED);
            paymentEventRepository.saveAndFlush(paymentEvent);
        }

        GooglePlayVerificationService.GoogleVerificationResult verification = googlePlayVerificationService
                .verifySubscription(purchaseToken, productId);

        if (!verification.valid() || verification.expiresAt() == null
                || !verification.expiresAt().isAfter(Instant.now())) {
            boolean currentGoogleReceipt = GOOGLE_PLAY_PROVIDER.equals(company.getSubscriptionProvider())
                    && ( ("google:" + tokenHash).equals(company.getExternalSubscriptionId())
                    || (paymentEvent.getExternalSubscriptionId() != null
                    && ("google:" + paymentEvent.getExternalSubscriptionId()).equals(company.getExternalSubscriptionId())) );
            if (currentGoogleReceipt && !verification.retryable() && verification.expiresAt() != null
                    && verification.state() != null && !googleManuallyRestricted(company)
                    && (verification.obfuscatedAccountId() == null
                    || GooglePlayAccountBinding.companyId(companyId).equals(verification.obfuscatedAccountId()))) {
                company.setIsReadOnly(true);
                company.setSubscriptionStatus("EXPIRED");
                company.setSubscriptionExpiresAt(LocalDateTime.ofInstant(verification.expiresAt(), ZoneOffset.UTC));
                companyRepository.save(company);
            }
            if (paymentEvent.getStatus() != PaymentEventStatus.PROCESSED)
                paymentEvent.setStatus(PaymentEventStatus.FAILED);
            if (googleReceipts != null && currentGoogleReceipt && paymentEvent.getStatus() == PaymentEventStatus.PROCESSED
                    && verification.expiresAt() != null && !verification.retryable()
                    && (verification.obfuscatedAccountId() == null
                    || GooglePlayAccountBinding.companyId(companyId).equals(verification.obfuscatedAccountId())))
                googleReceipts.remember(purchaseToken, companyId, verification);
            paymentEvent.setFailureReason(verification.reason());
            paymentEventRepository.save(paymentEvent);
            auditLogService.log(
                    "SUBSCRIPTION_GOOGLE_VERIFY_FAILED",
                    "PAYMENT_EVENT",
                    paymentEvent.getId(),
                    "Google subscription doğrulama başarısız: " + verification.reason());
            return new GoogleVerifyResult(false, existingOpt.isPresent(), null, null,
                    verification.retryable() ? "retryable" : "not_entitled");
        }

        PaymentEvent linkedReceipt = verification.linkedTokenHash() == null ? null : paymentEventRepository
                .findByProviderAndTokenHash(GOOGLE_PLAY_PROVIDER, verification.linkedTokenHash()).orElse(null);
        if (linkedReceipt != null && !companyId.equals(linkedReceipt.getCompanyId())) throw new PaymentOwnershipException();
        boolean claimedReceipt = existingOpt.isPresent() && paymentEvent.getStatus() == PaymentEventStatus.PROCESSED;
        boolean claimedLink = linkedReceipt != null && linkedReceipt.getStatus() == PaymentEventStatus.PROCESSED;
        String account = verification.obfuscatedAccountId();
        if ((account != null && !GooglePlayAccountBinding.companyId(companyId).equals(account))
                || (!claimedReceipt && !claimedLink && (account == null || account.isBlank()))) {
            throw new PaymentOwnershipException();
        }
        // Restoring an older Google receipt must not overwrite a newer subscription purchased
        // through another provider. New purchases still use the explicit purchase flow.
        if (company.getSubscriptionProvider() != null
                && !GOOGLE_PLAY_PROVIDER.equals(company.getSubscriptionProvider())
                && company.getPlanType() != PlanType.CIRAK && !"TRIAL".equals(company.getSubscriptionStatus())
                && (company.getSubscriptionExpiresAt() == null
                || company.getSubscriptionExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))) {
            return new GoogleVerifyResult(false, true, null, null, "another_provider_active");
        }
        boolean linksToCurrent = verification.linkedTokenHash() != null
                && (("google:" + verification.linkedTokenHash()).equals(company.getExternalSubscriptionId())
                || (linkedReceipt != null && currentGoogleReceipt(company, linkedReceipt, verification.linkedTokenHash())));
        if (GOOGLE_PLAY_PROVIDER.equals(company.getSubscriptionProvider())
                && !currentGoogleReceipt(company, paymentEvent, tokenHash) && !linksToCurrent
                && (claimedReceipt || (company.getSubscriptionExpiresAt() != null
                && company.getSubscriptionExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))))) {
            return new GoogleVerifyResult(false, true, null, null, "another_google_receipt_active");
        }
        PlanType verifiedPlan = requireAllowedGoogleProduct(verification.productId());
        if (!productId.equals(verification.productId()) && !productId.equals(verification.deferredProductId()))
            throw new IllegalArgumentException("Verified Google product does not match the requested product");
        Company updated = upgradePlanFromGooglePlay(companyId, verifiedPlan, verification.subscriptionId(),
                verification.expiresAt());
        paymentEvent.setExternalSubscriptionId(verification.subscriptionId());
        paymentEvent.setStatus(PaymentEventStatus.PROCESSED);
        paymentEvent.setFailureReason(null);
        paymentEventRepository.save(paymentEvent);

        if (googleReceipts != null) googleReceipts.remember(purchaseToken, companyId, verification);
        if (verification.linkedTokenHash() != null) {
            // Keep an ownership tombstone: an old token must never restore over its replacement.
            PaymentEvent retired = linkedReceipt == null ? new PaymentEvent() : linkedReceipt;
            retired.setCompanyId(companyId); retired.setProvider(GOOGLE_PLAY_PROVIDER);
            retired.setEventType("SUBSCRIPTION_REPLACED"); retired.setTokenHash(verification.linkedTokenHash());
            retired.setPurchaseTokenMasked("sha256:" + verification.linkedTokenHash().substring(0,12));
            retired.setStatus(PaymentEventStatus.PROCESSED); retired.setFailureReason("GOOGLE_REPLACED");
            paymentEventRepository.saveAndFlush(retired);
            if (googleReceipts != null) googleReceipts.retire(verification.linkedTokenHash());
        }

        auditLogService.log(
                "SUBSCRIPTION_GOOGLE_VERIFY_SUCCESS",
                "PAYMENT_EVENT",
                paymentEvent.getId(),
                "Google subscription doğrulandı ve plan upgrade edildi: " + updated.getPlanType());

        return new GoogleVerifyResult(true, existingOpt.isPresent(), updated.getPlanType().name(), verification.subscriptionId(), "processed",
                verification.deferredProductId() == null ? null : requireAllowedGoogleProduct(verification.deferredProductId()).name(),
                verification.basePlanId(), tokenHash);
    }

    @Transactional
    public Company upgradePlanFromGooglePlay(
            Long companyId,
            PlanType newPlan,
            String verifiedSubscriptionId,
            Instant verifiedExpiresAt) {
        if (verifiedExpiresAt == null || !verifiedExpiresAt.isAfter(Instant.now()))
            throw new IllegalArgumentException("Google subscription is not active");
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + companyId));

        PlanType oldPlan = company.getPlanType();
        company.setPlanType(newPlan);
        if (!googleManuallyRestricted(company)) {
            company.setIsReadOnly(false);
            company.setSubscriptionStatus("ACTIVE");
        }
        company.setSubscriptionProvider(GOOGLE_PLAY_PROVIDER);
        company.setExternalSubscriptionId("google:" + verifiedSubscriptionId);
        company.setIyzicoSubscriptionId(null);
        company.setSubscriptionExpiresAt(LocalDateTime.ofInstant(verifiedExpiresAt, ZoneOffset.UTC));

        Company saved = companyRepository.save(company);
        log.info("Google Play plan upgraded: companyId={}, {} -> {}", companyId, oldPlan, newPlan);
        return saved;
    }

    /** Authenticated RTDN is a hint, never an entitlement or an ownership claim. */
    @Transactional
    public String reconcileGoogleNotification(String purchaseToken) {
        String hash = sha256(purchaseToken);
        var receipt = paymentEventRepository.findByProviderAndTokenHash(GOOGLE_PLAY_PROVIDER, hash).orElse(null);
        if (receipt == null || receipt.getCompanyId() == null || receipt.getStatus() != PaymentEventStatus.PROCESSED) {
            // Only Google's verified link to an already claimed receipt can resolve a replacement
            // while the app is closed. Unrelated pushes cannot establish ownership.
            var discovery = googlePlayVerificationService.verifySubscription(purchaseToken, null);
            if (discovery.retryable()) return "RETRY";
            if (!discovery.valid() || discovery.linkedTokenHash() == null) return "WAITING_OWNER";
            var old = paymentEventRepository.findByProviderAndTokenHash(GOOGLE_PLAY_PROVIDER, discovery.linkedTokenHash()).orElse(null);
            if (old == null || old.getCompanyId() == null || old.getStatus() != PaymentEventStatus.PROCESSED) return "WAITING_OWNER";
            if ("GOOGLE_REPLACED".equals(old.getFailureReason())) return "IGNORED_STALE";
            var applied = verifyGooglePurchaseAndUpgradePlan(old.getCompanyId(), purchaseToken, discovery.productId());
            return applied.verified() ? "APPLIED" : "retryable".equals(applied.status()) ? "RETRY" : "REJECTED";
        }
        if ("GOOGLE_REPLACED".equals(receipt.getFailureReason())) return "IGNORED_STALE";
        var company = companyRepository.lockById(receipt.getCompanyId()).orElse(null);
        if (company == null || !currentGoogleReceipt(company, receipt, hash)) return "IGNORED_STALE";
        // Serialize the API read with tenant updates; delayed/duplicate notifications query today's state.
        var result = googlePlayVerificationService.verifySubscription(purchaseToken, null);
        if (result.retryable()) return "RETRY";
        if (result.expiresAt() == null || result.state() == null) return "REJECTED";
        if (result.obfuscatedAccountId() != null
                && !GooglePlayAccountBinding.companyId(company.getId()).equals(result.obfuscatedAccountId()))
            return "REJECTED_OWNER";
        if (googleReceipts != null) googleReceipts.remember(purchaseToken, company.getId(), result);
        if (result.valid() && result.expiresAt().isAfter(Instant.now())) {
            upgradePlanFromGooglePlay(company.getId(), requireAllowedGoogleProduct(result.productId()),
                    result.subscriptionId(), result.expiresAt());
            receipt.setExternalSubscriptionId(result.subscriptionId());
            paymentEventRepository.save(receipt);
        } else {
            company.setSubscriptionExpiresAt(LocalDateTime.ofInstant(result.expiresAt(), ZoneOffset.UTC));
            if (!googleManuallyRestricted(company)) {
                company.setIsReadOnly(true);
                company.setSubscriptionStatus("EXPIRED");
            }
            companyRepository.save(company);
        }
        return "APPLIED";
    }

    private boolean currentGoogleReceipt(Company company, PaymentEvent receipt, String hash) {
        return GOOGLE_PLAY_PROVIDER.equals(company.getSubscriptionProvider())
                && (("google:" + hash).equals(company.getExternalSubscriptionId())
                || (receipt.getExternalSubscriptionId() != null
                && ("google:" + receipt.getExternalSubscriptionId()).equals(company.getExternalSubscriptionId())));
    }

    private boolean googleManuallyRestricted(Company company) {
        String status = company.getSubscriptionStatus();
        return "SUSPENDED".equals(status) || (Boolean.TRUE.equals(company.getIsReadOnly())
                && !"EXPIRED".equals(status) && !"REVOKED".equals(status) && !"PAYMENT_FAILED".equals(status));
    }

    @Transactional
    public AppleVerifyResult verifyAppleTransactionAndUpgradePlan(
            Long companyId,
            String signedTransactionInfo) {
        AppleAppStoreVerificationService.AppleVerificationResult verification =
                appleAppStoreVerificationService.verifyTransaction(signedTransactionInfo);

        String transactionHash = sha256(verification.transactionId());
        String originalTransactionHash = sha256(verification.originalTransactionId());
        String externalSubscriptionId = "appstore:" + originalTransactionHash;
        Optional<PaymentEvent> existingOpt = paymentEventRepository
                .findByProviderAndTokenHash(APP_STORE_PROVIDER, transactionHash);

        if (existingOpt.isPresent()) {
            PaymentEvent existing = existingOpt.get();
            if (!companyId.equals(existing.getCompanyId())) {
                auditLogService.log(
                        "SUBSCRIPTION_APP_STORE_VERIFY_CONFLICT",
                        "PAYMENT_EVENT",
                        existing.getId(),
                        "Apple transaction baska bir company tarafindan islenmis");
                throw new AppStoreVerificationException(
                        AppStoreVerificationException.Reason.OWNERSHIP_CONFLICT,
                        "Apple transaction baska bir sirket tarafindan kullanilmis");
            }

            boolean alreadyProcessed = existing.getStatus() == PaymentEventStatus.PROCESSED;
            auditLogService.log(
                    "SUBSCRIPTION_APP_STORE_VERIFY_IDEMPOTENT",
                    "PAYMENT_EVENT",
                    existing.getId(),
                    "Apple transaction tekrar geldi; idempotent olarak islendi");
            return new AppleVerifyResult(
                    alreadyProcessed,
                    true,
                    verification.planType().name(),
                    existing.getExternalSubscriptionId(),
                    alreadyProcessed ? "processed" : "failed");
        }

        Optional<Company> owner = companyRepository
                .findBySubscriptionProviderAndExternalSubscriptionId(APP_STORE_PROVIDER, externalSubscriptionId);
        if (owner.isPresent() && !companyId.equals(owner.get().getId())) {
            auditLogService.log(
                    "SUBSCRIPTION_APP_STORE_VERIFY_CONFLICT",
                    "COMPANY",
                    owner.get().getId(),
                    "Apple subscription baska bir company tarafindan sahiplenilmis");
            throw ownershipConflict();
        }

        Optional<PaymentEvent> recordedOwner = paymentEventRepository
                .findFirstByProviderAndExternalSubscriptionIdAndStatusOrderByIdAsc(
                        APP_STORE_PROVIDER, externalSubscriptionId, PaymentEventStatus.PROCESSED);
        if (recordedOwner.isPresent() && !companyId.equals(recordedOwner.get().getCompanyId())) {
            throw ownershipConflict();
        }
        Company target = companyRepository.lockById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + companyId));
        if (verification.appAccountToken() != null) {
            // Only the Apple-signed token is trusted; never a client-submitted account identifier.
            if (!verification.appAccountToken().equals(target.getAppStoreAccountToken())) {
                throw ownershipConflict();
            }
        } else if (owner.isEmpty()) {
            // Old builds did not send appAccountToken. Restore a previously bound chain,
            // but never assign an unclaimed legacy purchase to whichever account is logged in.
            if (recordedOwner.isEmpty()) {
                throw new AppStoreVerificationException(
                        AppStoreVerificationException.Reason.ACCOUNT_BINDING_REQUIRED,
                        "Bu eski Apple satin alimi henuz bir isletmeye baglanmamis. Yeniden odeme yapmayin; destekle iletisime gecin.");
            }
        }

        PaymentEvent paymentEvent = new PaymentEvent();
        paymentEvent.setCompanyId(companyId);
        paymentEvent.setProvider(APP_STORE_PROVIDER);
        paymentEvent.setEventType("SUBSCRIPTION_VERIFY");
        paymentEvent.setTokenHash(transactionHash);
        paymentEvent.setPurchaseTokenMasked("sha256:" + transactionHash.substring(0, 12));
        paymentEvent.setExternalSubscriptionId(externalSubscriptionId);
        paymentEvent.setStatus(PaymentEventStatus.RECEIVED);

        try {
            paymentEventRepository.saveAndFlush(paymentEvent);
            Company updated = upgradePlanFromAppStore(companyId, verification, originalTransactionHash);

            paymentEvent.setStatus(PaymentEventStatus.PROCESSED);
            paymentEventRepository.save(paymentEvent);

            auditLogService.log(
                    "SUBSCRIPTION_APP_STORE_VERIFY_SUCCESS",
                    "PAYMENT_EVENT",
                    paymentEvent.getId(),
                    "Apple subscription dogrulandi ve plan upgrade edildi: " + updated.getPlanType());

            return new AppleVerifyResult(
                    true,
                    false,
                    updated.getPlanType().name(),
                    externalSubscriptionId,
                    "processed");
        } catch (DataIntegrityViolationException ex) {
            log.warn("App Store subscription ownership conflict: companyId={}", companyId);
            throw ownershipConflict();
        } catch (RuntimeException ex) {
            paymentEvent.setStatus(PaymentEventStatus.FAILED);
            paymentEvent.setFailureReason("Apple subscription update failed");
            paymentEventRepository.save(paymentEvent);
            auditLogService.log(
                    "SUBSCRIPTION_APP_STORE_VERIFY_FAILED",
                    "PAYMENT_EVENT",
                    paymentEvent.getId(),
                    "Apple subscription dogrulandi ancak plan guncellenemedi");
            throw ex;
        }
    }

    @Transactional
    public Company upgradePlanFromAppStore(Long companyId,
            AppleAppStoreVerificationService.AppleVerificationResult verification, String originalTransactionHash) {
        Company company = companyRepository.lockById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + companyId));

        String subscriptionId = "appstore:" + originalTransactionHash;
        if (subscriptionId.equals(company.getExternalSubscriptionId())
                && AppStoreEntitlementPolicy.isStale(company, verification.purchaseDate(), verification.expiresDate(),
                        verification.signedDate(), verification.environment())) return company;
        boolean manualRestriction = AppStoreEntitlementPolicy.manuallyRestricted(company);

        PlanType oldPlan = company.getPlanType();
        company.setPlanType(verification.planType());
        if (!manualRestriction) {
            company.setIsReadOnly(false);
            company.setSubscriptionStatus("ACTIVE");
        }
        company.setSubscriptionProvider("APP_STORE");
        company.setExternalSubscriptionId(subscriptionId);
        company.setSubscriptionExpiresAt(verification.expiresDate());
        company.setAppStoreTransactionHash(sha256(verification.transactionId()));
        company.setAppStorePurchaseDate(verification.purchaseDate());
        company.setAppStoreSignedDate(verification.signedDate());
        company.setAppStoreEnvironment(verification.environment());

        Company saved = companyRepository.saveAndFlush(company);
        log.info("App Store plan upgraded: companyId={}, {} -> {}, expiresAt={}",
                companyId, oldPlan, verification.planType(), verification.expiresDate());
        return saved;
    }

    /**
     * Handle successful recurring payment — extend subscription.
     */
    @Transactional
    public void handlePaymentSuccess(String iyzicoSubscriptionId) {
        Optional<Company> opt = companyRepository.findAll().stream()
                .filter(c -> iyzicoSubscriptionId.equals(c.getIyzicoSubscriptionId()))
                .findFirst();

        if (opt.isEmpty()) {
            log.warn("No company found for subscription: {}", iyzicoSubscriptionId);
            return;
        }

        Company company = opt.get();
        company.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
        company.setIsReadOnly(false);
        company.setSubscriptionStatus("ACTIVE");
        companyRepository.save(company);

        log.info("Subscription renewed: companyId={}, expiresAt={}",
                company.getId(), company.getSubscriptionExpiresAt());
    }

    /**
     * Handle failed payment — mark for grace period.
     */
    @Transactional
    public void handlePaymentFailure(String iyzicoSubscriptionId) {
        Optional<Company> opt = companyRepository.findAll().stream()
                .filter(c -> iyzicoSubscriptionId.equals(c.getIyzicoSubscriptionId()))
                .findFirst();

        if (opt.isEmpty()) return;

        Company company = opt.get();
        company.setSubscriptionStatus("PAYMENT_FAILED");

        // A missing expiry means the payment failed immediately. Record that
        // moment as the expiry; the scheduled enforcement adds the grace period.
        if (company.getSubscriptionExpiresAt() == null) {
            company.setSubscriptionExpiresAt(LocalDateTime.now());
        }

        companyRepository.save(company);
        log.warn("Payment failed for companyId={}, grace period until {}",
                company.getId(), company.getSubscriptionExpiresAt());
    }

    /**
     * Cancel subscription — immediate downgrade to free plan.
     */
    @Transactional
    public void cancelSubscription(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        if (!"SUSPENDED".equals(company.getSubscriptionStatus())) {
            CompanyAccessPolicy.initializeFreePlan(company);
        } else {
            company.setPlanType(PlanType.CIRAK);
            company.setTrialEndsAt(null);
            company.setSubscriptionExpiresAt(null);
        }
        company.setIyzicoSubscriptionId(null);
        companyRepository.save(company);

        log.info("Subscription cancelled: companyId={}", companyId);
    }

    /**
     * Check if a company is currently in read-only mode.
     */
    public boolean isReadOnly(Long companyId) {
        return companyRepository.findById(companyId)
                .map(CompanyAccessPolicy::isReadOnly)
                .orElse(false);
    }

    /**
     * CRON: Runs daily at 2 AM — checks expired subscriptions and enforces read-only mode.
     * Companies whose subscription expires get 7-day grace, then read-only.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void enforceExpiredSubscriptions() {
        log.info("Running subscription expiry check...");

        List<Company> allCompanies = companyRepository.findAll();
        int enforced = 0;

        for (Company company : allCompanies) {
            // Skip free plan (never expires)
            if (PlanType.CIRAK.equals(company.getPlanType())) continue;

            // Skip already read-only
            if (Boolean.TRUE.equals(company.getIsReadOnly())) continue;

            // Paid subscriptions remain writable for seven full days after
            // their billing expiry. Only then is read-only mode enforced.
            boolean storeManaged = APP_STORE_PROVIDER.equals(company.getSubscriptionProvider())
                    || GOOGLE_PLAY_PROVIDER.equals(company.getSubscriptionProvider());
            long graceDays = storeManaged
                    ? 0 : SUBSCRIPTION_GRACE_PERIOD_DAYS;
            LocalDateTime now = storeManaged
                    ? LocalDateTime.now(java.time.ZoneOffset.UTC) : LocalDateTime.now();
            if (company.getSubscriptionExpiresAt() != null
                    && company.getSubscriptionExpiresAt()
                            .plusDays(graceDays)
                            .isBefore(now)) {

                company.setIsReadOnly(true);
                company.setSubscriptionStatus("EXPIRED");
                companyRepository.save(company);
                enforced++;

                log.warn("Company {} set to READ_ONLY (subscription expired at {})",
                        company.getId(), company.getSubscriptionExpiresAt());
            }
        }

        log.info("Subscription check complete: {} companies set to read-only", enforced);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 kullanılamıyor", e);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "n/a";
        }
        return "sha256:" + sha256(token).substring(0, 12);
    }

    private PlanType requireAllowedGoogleProduct(String productId) {
        PlanType planType = GOOGLE_PRODUCT_PLANS.get(productId);
        if (planType == null) {
            throw new IllegalArgumentException("Google productId tanimli degil");
        }
        return planType;
    }

    private AppStoreVerificationException ownershipConflict() {
        return new AppStoreVerificationException(
                AppStoreVerificationException.Reason.OWNERSHIP_CONFLICT,
                "Apple aboneligi baska bir sirket tarafindan kullanilmis");
    }

    public record GoogleVerifyResult(
            boolean verified,
            boolean idempotentReplay,
            String plan,
            String subscriptionId,
            String status,
            String scheduledPlan,
            String basePlanId,
            String receiptHash
    ) {
        public GoogleVerifyResult(boolean verified, boolean replay, String plan, String subscriptionId, String status) {
            this(verified, replay, plan, subscriptionId, status, null, null, null);
        }
    }

    public record AppleVerifyResult(
            boolean verified,
            boolean idempotentReplay,
            String plan,
            String subscriptionId,
            String status
    ) {
    }
}
