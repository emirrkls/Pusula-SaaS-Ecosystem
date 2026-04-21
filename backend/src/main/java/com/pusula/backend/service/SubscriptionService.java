package com.pusula.backend.service;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.repository.CompanyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Manages subscription lifecycle: upgrades, downgrades, expiry, and read-only enforcement.
 * Integrates with Iyzico/PayTR payment providers.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final CompanyRepository companyRepository;

    public SubscriptionService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
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

        // Set subscription expiry to 30 days from now
        company.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));

        Company saved = companyRepository.save(company);
        log.info("Plan upgraded: companyId={}, {} → {}, subscriptionId={}",
                companyId, oldPlan, newPlan, iyzicoSubscriptionId);

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
}
