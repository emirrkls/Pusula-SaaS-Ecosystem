package com.pusula.backend.controller;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.SubscriptionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Payment and subscription management endpoints.
 * Handles Iyzico/PayTR webhooks and plan upgrades.
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final SubscriptionService subscriptionService;

    public PaymentController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * POST /api/payment/init — Create a payment initialization for plan upgrade.
     * Returns a payment form URL (Iyzico checkout form / PayTR iframe token).
     * Admin only.
     */
    @PostMapping("/init")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> initPayment(@RequestBody Map<String, String> request) {
        User user = getCurrentUser();
        String targetPlan = request.getOrDefault("plan", "USTA");

        log.info("Payment init: companyId={}, targetPlan={}", user.getCompanyId(), targetPlan);

        // TODO: Replace with real Iyzico API call
        // For now, return a mock payment form URL
        String mockPaymentUrl = "https://sandbox-api.iyzipay.com/v2/checkout-form/"
                + java.util.UUID.randomUUID().toString();

        return ResponseEntity.ok(Map.of(
                "paymentUrl", mockPaymentUrl,
                "plan", targetPlan,
                "provider", "IYZICO",
                "mode", "SANDBOX"
        ));
    }

    /**
     * POST /api/payment/webhook/iyzico — Iyzico webhook callback.
     * Called by Iyzico when payment is completed or fails.
     * NOT authenticated — validated by Iyzico signature.
     */
    @PostMapping("/webhook/iyzico")
    public ResponseEntity<Map<String, String>> iyzicoWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Iyzico webhook received: {}", payload);

        String eventType = (String) payload.getOrDefault("iyziEventType", "");
        String subscriptionId = (String) payload.getOrDefault("subscriptionReferenceCode", "");
        String status = (String) payload.getOrDefault("status", "");

        // TODO: Validate Iyzico signature (HMAC)
        // String signature = request.getHeader("x-iyz-signature");
        // if (!validateSignature(signature, payload)) return ResponseEntity.status(401).build();

        switch (eventType) {
            case "subscription.order.success":
                subscriptionService.handlePaymentSuccess(subscriptionId);
                log.info("Iyzico: Payment success for subscription {}", subscriptionId);
                break;

            case "subscription.order.failure":
                subscriptionService.handlePaymentFailure(subscriptionId);
                log.warn("Iyzico: Payment failed for subscription {}", subscriptionId);
                break;

            case "subscription.cancelled":
                // Find company by subscription ID and cancel
                log.warn("Iyzico: Subscription cancelled: {}", subscriptionId);
                break;

            default:
                log.info("Iyzico: Unhandled event type: {}", eventType);
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /**
     * POST /api/payment/upgrade — Direct upgrade (admin initiates after payment confirmation).
     * Used when payment is confirmed via polling or manual verification.
     * Admin only.
     */
    @PostMapping("/upgrade")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Company> upgradePlan(@RequestBody Map<String, String> request) {
        User user = getCurrentUser();
        String planName = request.getOrDefault("plan", "USTA");
        String subscriptionId = request.getOrDefault("subscriptionId", "");

        PlanType plan;
        try {
            plan = PlanType.valueOf(planName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        Company updated = subscriptionService.upgradePlan(
                user.getCompanyId(), plan, subscriptionId);

        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/payment/cancel — Cancel current subscription.
     * Admin only.
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> cancelSubscription() {
        User user = getCurrentUser();
        subscriptionService.cancelSubscription(user.getCompanyId());
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    /**
     * GET /api/payment/status — Check subscription read-only status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus() {
        User user = getCurrentUser();
        boolean readOnly = subscriptionService.isReadOnly(user.getCompanyId());
        return ResponseEntity.ok(Map.of(
                "isReadOnly", readOnly,
                "companyId", user.getCompanyId()
        ));
    }
}
