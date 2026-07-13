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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Manages subscription lifecycle: upgrades, downgrades, expiry, and read-only enforcement.
 * Integrates with Iyzico/PayTR payment providers.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final String APP_STORE_PROVIDER = "APP_STORE";

    private final CompanyRepository companyRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final GooglePlayVerificationService googlePlayVerificationService;
    private final AppleAppStoreVerificationService appleAppStoreVerificationService;
    private final AuditLogService auditLogService;

    public SubscriptionService(
            CompanyRepository companyRepository,
            PaymentEventRepository paymentEventRepository,
            GooglePlayVerificationService googlePlayVerificationService,
            AppleAppStoreVerificationService appleAppStoreVerificationService,
            AuditLogService auditLogService) {
        this.companyRepository = companyRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.googlePlayVerificationService = googlePlayVerificationService;
        this.appleAppStoreVerificationService = appleAppStoreVerificationService;
        this.auditLogService = auditLogService;
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
            PlanType planType,
            String purchaseToken,
            String productId) {
        String tokenHash = sha256(purchaseToken);
        Optional<PaymentEvent> existingOpt = paymentEventRepository
                .findByProviderAndTokenHash("GOOGLE_PLAY", tokenHash);

        if (existingOpt.isPresent()) {
            PaymentEvent existing = existingOpt.get();
            boolean alreadyProcessed = existing.getStatus() == PaymentEventStatus.PROCESSED;
            String description = alreadyProcessed
                    ? "Google purchase token tekrar geldi; idempotent replay olarak işlendi"
                    : "Google purchase token daha once kaydedildi; tekrar işlenmedi";
            auditLogService.log(
                    "SUBSCRIPTION_GOOGLE_VERIFY_IDEMPOTENT",
                    "PAYMENT_EVENT",
                    existing.getId(),
                    description);
            return new GoogleVerifyResult(
                    alreadyProcessed,
                    true,
                    planType.name(),
                    existing.getExternalSubscriptionId(),
                    alreadyProcessed ? "processed" : "failed");
        }

        PaymentEvent paymentEvent = new PaymentEvent();
        paymentEvent.setCompanyId(companyId);
        paymentEvent.setProvider("GOOGLE_PLAY");
        paymentEvent.setEventType("SUBSCRIPTION_VERIFY");
        paymentEvent.setTokenHash(tokenHash);
        paymentEvent.setPurchaseTokenMasked(maskToken(purchaseToken));
        paymentEvent.setStatus(PaymentEventStatus.RECEIVED);
        paymentEventRepository.save(paymentEvent);

        GooglePlayVerificationService.GoogleVerificationResult verification = googlePlayVerificationService
                .verifySubscription(purchaseToken, productId);

        if (!verification.valid()) {
            paymentEvent.setStatus(PaymentEventStatus.FAILED);
            paymentEvent.setFailureReason(verification.reason());
            paymentEventRepository.save(paymentEvent);
            auditLogService.log(
                    "SUBSCRIPTION_GOOGLE_VERIFY_FAILED",
                    "PAYMENT_EVENT",
                    paymentEvent.getId(),
                    "Google subscription doğrulama başarısız: " + verification.reason());
            return new GoogleVerifyResult(false, false, planType.name(), null, "failed");
        }

        Company updated = upgradePlan(companyId, planType, "google:" + verification.subscriptionId());
        paymentEvent.setExternalSubscriptionId(verification.subscriptionId());
        paymentEvent.setStatus(PaymentEventStatus.PROCESSED);
        paymentEventRepository.save(paymentEvent);

        auditLogService.log(
                "SUBSCRIPTION_GOOGLE_VERIFY_SUCCESS",
                "PAYMENT_EVENT",
                paymentEvent.getId(),
                "Google subscription doğrulandı ve plan upgrade edildi: " + updated.getPlanType());

        return new GoogleVerifyResult(true, false, updated.getPlanType().name(), verification.subscriptionId(), "processed");
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
            Company updated = upgradePlanFromAppStore(
                    companyId,
                    verification.planType(),
                    originalTransactionHash,
                    verification.expiresDate());

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
    public Company upgradePlanFromAppStore(
            Long companyId,
            PlanType newPlan,
            String originalTransactionHash,
            LocalDateTime expiresAt) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found: " + companyId));

        PlanType oldPlan = company.getPlanType();
        company.setPlanType(newPlan);
        company.setIsReadOnly(false);
        company.setSubscriptionStatus("ACTIVE");
        company.setSubscriptionProvider("APP_STORE");
        company.setExternalSubscriptionId("appstore:" + originalTransactionHash);
        company.setSubscriptionExpiresAt(expiresAt);

        Company saved = companyRepository.saveAndFlush(company);
        log.info("App Store plan upgraded: companyId={}, {} -> {}, expiresAt={}",
                companyId, oldPlan, newPlan, expiresAt);
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

        // Grace period: 7 days after expiry before read-only
        if (company.getSubscriptionExpiresAt() == null) {
            company.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(7));
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

        company.setPlanType(PlanType.CIRAK);
        company.setSubscriptionStatus("CANCELLED");
        company.setIyzicoSubscriptionId(null);
        companyRepository.save(company);

        log.info("Subscription cancelled: companyId={}", companyId);
    }

    /**
     * Check if a company is currently in read-only mode.
     */
    public boolean isReadOnly(Long companyId) {
        return companyRepository.findById(companyId)
                .map(Company::getIsReadOnly)
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

            // Check if subscription has expired
            if (company.getSubscriptionExpiresAt() != null
                    && company.getSubscriptionExpiresAt().isBefore(LocalDateTime.now())) {

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
        if (token.length() <= 8) {
            return "****" + token;
        }
        return "****" + token.substring(token.length() - 8);
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
            String status
    ) {
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
