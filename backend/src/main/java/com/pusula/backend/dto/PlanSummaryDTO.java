package com.pusula.backend.dto;

import java.math.BigDecimal;

public class PlanSummaryDTO {
    private Long id;
    private String name;
    private String displayName;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private Integer maxTechnicians;
    private Integer maxCustomers;
    private Integer maxMonthlyTickets;
    private Integer maxMonthlyProposals;
    private Integer maxInventoryItems;
    private Integer storageLimitMb;
    private Boolean isActive;

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
    public void setIsActive(Boolean active) { isActive = active; }
}
