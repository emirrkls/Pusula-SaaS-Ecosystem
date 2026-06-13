package com.pusula.backend.controller;

import com.pusula.backend.entity.Plan;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.repository.PlanRepository;
import com.pusula.backend.dto.GoogleVerifyRequest;
import com.pusula.backend.dto.GoogleVerifyResponse;
import com.pusula.backend.dto.PlanSummaryDTO;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.service.SubscriptionService;
import com.pusula.backend.entity.User;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final PlanRepository planRepository;
    private final FeatureService featureService;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(
            PlanRepository planRepository,
            FeatureService featureService,
            SubscriptionService subscriptionService) {
        this.planRepository = planRepository;
        this.featureService = featureService;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Public endpoint — returns all available plans for plan comparison pages.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanSummaryDTO>> getPlans() {
        List<PlanSummaryDTO> plans = planRepository.findAll().stream()
                .map(this::toPlanSummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(plans);
    }

    /**
     * Get current tenant's feature context (authenticated).
     */
    @GetMapping("/my-context")
    public ResponseEntity<Map<String, Object>> getMyContext() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(featureService.getFeatureContext(user.getCompanyId()));
    }

    @PostMapping("/google-verify")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<GoogleVerifyResponse> verifyGooglePurchase(@Valid @RequestBody GoogleVerifyRequest request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        PlanType planType;
        try {
            planType = PlanType.valueOf(request.getPlan().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                    new GoogleVerifyResponse(false, false, request.getPlan(), null, "invalid_plan"));
        }

        SubscriptionService.GoogleVerifyResult result = subscriptionService.verifyGooglePurchaseAndUpgradePlan(
                user.getCompanyId(),
                planType,
                request.getPurchaseToken(),
                request.getProductId());

        return ResponseEntity.ok(new GoogleVerifyResponse(
                result.verified(),
                result.idempotentReplay(),
                result.plan(),
                result.subscriptionId(),
                result.status()));
    }

    private PlanSummaryDTO toPlanSummary(Plan plan) {
        PlanSummaryDTO dto = new PlanSummaryDTO();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setDisplayName(plan.getDisplayName());
        dto.setPriceMonthly(plan.getPriceMonthly());
        dto.setPriceYearly(plan.getPriceYearly());
        dto.setMaxTechnicians(plan.getMaxTechnicians());
        dto.setMaxCustomers(plan.getMaxCustomers());
        dto.setMaxMonthlyTickets(plan.getMaxMonthlyTickets());
        dto.setMaxMonthlyProposals(plan.getMaxMonthlyProposals());
        dto.setMaxInventoryItems(plan.getMaxInventoryItems());
        dto.setStorageLimitMb(plan.getStorageLimitMb());
        dto.setIsActive(plan.getIsActive());
        return dto;
    }
}
