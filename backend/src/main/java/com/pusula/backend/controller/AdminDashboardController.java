package com.pusula.backend.controller;

import com.pusula.backend.dto.InventoryDTO;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.AdminDashboardService;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.service.InventoryService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin dashboard endpoints — financial summaries, technician performance,
 * quota tracking, and cost management.
 * Restricted to COMPANY_ADMIN and SUPER_ADMIN roles.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;
    private final FeatureService featureService;
    private final InventoryService inventoryService;

    public AdminDashboardController(AdminDashboardService dashboardService,
                                    FeatureService featureService,
                                    InventoryService inventoryService) {
        this.dashboardService = dashboardService;
        this.featureService = featureService;
        this.inventoryService = inventoryService;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * GET /api/admin/dashboard — primary KPI summary for admin home screen.
     * Returns: monthly revenue, outstanding debt, net profit, profit margin.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardService.DashboardKPIs> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboardKPIs(getCurrentUser().getCompanyId()));
    }

    /**
     * GET /api/admin/technician-stats — performance stats for each technician.
     * Returns: name, completed tickets today, total collected, average ticket time.
     */
    @GetMapping("/technician-stats")
    public ResponseEntity<List<AdminDashboardService.TechnicianStatDTO>> getTechnicianStats() {
        return ResponseEntity.ok(dashboardService.getTechnicianStats(getCurrentUser().getCompanyId()));
    }

    /**
     * GET /api/admin/profit-analysis — real-time profit margin analysis.
     * Calculates from InventoryAdminDTO with buyPrice vs sellPrice.
     */
    @GetMapping("/profit-analysis")
    public ResponseEntity<AdminDashboardService.ProfitAnalysis> getProfitAnalysis() {
        return ResponseEntity.ok(dashboardService.getProfitAnalysis(getCurrentUser().getCompanyId()));
    }

    /**
     * GET /api/admin/quota-status — current plan limits and usage.
     */
    @GetMapping("/quota-status")
    public ResponseEntity<AdminDashboardService.QuotaStatus> getQuotaStatus() {
        User user = getCurrentUser();
        return ResponseEntity.ok(dashboardService.getQuotaStatus(user.getCompanyId()));
    }

    /**
     * GET /api/admin/field-radar — technician locations and active ticket coordinates.
     */
    @GetMapping("/field-radar")
    public ResponseEntity<List<AdminDashboardService.FieldPin>> getFieldRadar() {
        return ResponseEntity.ok(dashboardService.getFieldRadarPins(getCurrentUser().getCompanyId()));
    }

    /**
     * GET /api/admin/catalog — full inventory with buyPrice (admin only).
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<InventoryDTO>> getFullCatalog() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    /**
     * PUT /api/admin/catalog/bulk-price — bulk update buy/sell prices.
     */
    @PutMapping("/catalog/bulk-price")
    public ResponseEntity<Map<String, Integer>> bulkUpdatePrices(
            @RequestBody List<AdminDashboardService.PriceUpdateDTO> updates) {
        int count = dashboardService.bulkUpdatePrices(getCurrentUser().getCompanyId(), updates);
        return ResponseEntity.ok(Map.of("updatedCount", count));
    }
}
