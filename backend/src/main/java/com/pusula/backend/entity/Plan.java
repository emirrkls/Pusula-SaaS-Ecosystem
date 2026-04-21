package com.pusula.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name; // CIRAK, USTA, PATRON

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "price_monthly")
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly")
    private BigDecimal priceYearly;

    @Column(name = "max_technicians")
    private Integer maxTechnicians;

    @Column(name = "max_customers")
    private Integer maxCustomers;

    @Column(name = "max_monthly_tickets")
    private Integer maxMonthlyTickets;

    @Column(name = "max_monthly_proposals")
    private Integer maxMonthlyProposals;

    @Column(name = "max_inventory_items")
    private Integer maxInventoryItems;

    @Column(name = "storage_limit_mb")
    private Integer storageLimitMb;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY)
    private List<PlanFeature> features;

    // --- Getters / Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public BigDecimal getPriceMonthly() { return priceMonthly; }
    public void setPriceMonthly(BigDecimal priceMonthly) { this.priceMonthly = priceMonthly; }

    public BigDecimal getPriceYearly() { return priceYearly; }
    public void setPriceYearly(BigDecimal priceYearly) { this.priceYearly = priceYearly; }

    public Integer getMaxTechnicians() { return maxTechnicians; }
    public void setMaxTechnicians(Integer maxTechnicians) { this.maxTechnicians = maxTechnicians; }

    public Integer getMaxCustomers() { return maxCustomers; }
    public void setMaxCustomers(Integer maxCustomers) { this.maxCustomers = maxCustomers; }

    public Integer getMaxMonthlyTickets() { return maxMonthlyTickets; }
    public void setMaxMonthlyTickets(Integer maxMonthlyTickets) { this.maxMonthlyTickets = maxMonthlyTickets; }

    public Integer getMaxMonthlyProposals() { return maxMonthlyProposals; }
    public void setMaxMonthlyProposals(Integer maxMonthlyProposals) { this.maxMonthlyProposals = maxMonthlyProposals; }

    public Integer getMaxInventoryItems() { return maxInventoryItems; }
    public void setMaxInventoryItems(Integer maxInventoryItems) { this.maxInventoryItems = maxInventoryItems; }

    public Integer getStorageLimitMb() { return storageLimitMb; }
    public void setStorageLimitMb(Integer storageLimitMb) { this.storageLimitMb = storageLimitMb; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public List<PlanFeature> getFeatures() { return features; }
    public void setFeatures(List<PlanFeature> features) { this.features = features; }
}
