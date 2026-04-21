package com.pusula.backend.controller;

import com.pusula.backend.entity.Plan;
import com.pusula.backend.repository.PlanRepository;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.entity.User;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final PlanRepository planRepository;
    private final FeatureService featureService;

    public SubscriptionController(PlanRepository planRepository, FeatureService featureService) {
        this.planRepository = planRepository;
        this.featureService = featureService;
    }

    /**
     * Public endpoint — returns all available plans for plan comparison pages.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<Plan>> getPlans() {
        return ResponseEntity.ok(planRepository.findAll());
    }

    /**
     * Get current tenant's feature context (authenticated).
     */
    @GetMapping("/my-context")
    public ResponseEntity<Map<String, Object>> getMyContext() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(featureService.getFeatureContext(user.getCompanyId()));
    }
}
