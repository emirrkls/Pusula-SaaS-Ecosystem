package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.util.Map;

public class PlanSummaryDTO {
    private Long id;
    private String name;
    private String displayName;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private Integer maxTechnicians;
    private Integer maxCompanyAdmins;
    private Integer maxCustomers;
    private Integer maxMonthlyTickets;
    private Integer maxMonthlyProposals;
    private Integer maxInventoryItems;
    private Integer storageLimitMb;
    private Integer maxVehicles;
    private Integer maxCommercialDevices;
    private Integer auditRetentionDays;
    private Boolean isActive;
    private Map<String, Boolean> features;

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
    public Integer getMaxCompanyAdmins() { return maxCompanyAdmins; }
    public void setMaxCompanyAdmins(Integer maxCompanyAdmins) { this.maxCompanyAdmins = maxCompanyAdmins; }
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
    public Integer getMaxVehicles() { return maxVehicles; }
    public void setMaxVehicles(Integer maxVehicles) { this.maxVehicles = maxVehicles; }
    public Integer getMaxCommercialDevices() { return maxCommercialDevices; }
    public void setMaxCommercialDevices(Integer maxCommercialDevices) { this.maxCommercialDevices = maxCommercialDevices; }
    public Integer getAuditRetentionDays() { return auditRetentionDays; }
    public void setAuditRetentionDays(Integer auditRetentionDays) { this.auditRetentionDays = auditRetentionDays; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
    public Map<String, Boolean> getFeatures() { return features; }
    public void setFeatures(Map<String, Boolean> features) { this.features = features; }
}
