package com.pusula.backend.dto;

/**
 * Quota information returned to the client on login and token refresh.
 * The iOS app uses this to display quota bars and enforce local limits.
 */
public class QuotaDTO {
    private int maxTechnicians;
    private int maxCompanyAdmins;
    private int maxCustomers;
    private int maxMonthlyTickets;
    private int maxMonthlyProposals;
    private int maxInventoryItems;
    private int storageLimitMb;
    private int maxVehicles;
    private int maxCommercialDevices;
    private Integer auditRetentionDays;
    // Current usage
    private int currentTechnicians;
    private int currentCompanyAdmins;
    private int currentCustomers;
    private int currentMonthlyTickets;
    private int currentMonthlyProposals;
    private int currentInventoryItems;
    private int currentStorageMb;
    private int currentVehicles;
    private int currentCommercialDevices;

    public QuotaDTO() {}

    // --- Getters / Setters ---
    public int getMaxTechnicians() { return maxTechnicians; }
    public void setMaxTechnicians(int v) { this.maxTechnicians = v; }
    public int getMaxCompanyAdmins() { return maxCompanyAdmins; }
    public void setMaxCompanyAdmins(int v) { this.maxCompanyAdmins = v; }

    public int getMaxCustomers() { return maxCustomers; }
    public void setMaxCustomers(int v) { this.maxCustomers = v; }

    public int getMaxMonthlyTickets() { return maxMonthlyTickets; }
    public void setMaxMonthlyTickets(int v) { this.maxMonthlyTickets = v; }

    public int getMaxMonthlyProposals() { return maxMonthlyProposals; }
    public void setMaxMonthlyProposals(int v) { this.maxMonthlyProposals = v; }

    public int getMaxInventoryItems() { return maxInventoryItems; }
    public void setMaxInventoryItems(int v) { this.maxInventoryItems = v; }

    public int getStorageLimitMb() { return storageLimitMb; }
    public void setStorageLimitMb(int v) { this.storageLimitMb = v; }
    public int getMaxVehicles() { return maxVehicles; }
    public void setMaxVehicles(int v) { this.maxVehicles = v; }
    public int getMaxCommercialDevices() { return maxCommercialDevices; }
    public void setMaxCommercialDevices(int v) { this.maxCommercialDevices = v; }
    public Integer getAuditRetentionDays() { return auditRetentionDays; }
    public void setAuditRetentionDays(Integer v) { this.auditRetentionDays = v; }

    public int getCurrentTechnicians() { return currentTechnicians; }
    public void setCurrentTechnicians(int v) { this.currentTechnicians = v; }
    public int getCurrentCompanyAdmins() { return currentCompanyAdmins; }
    public void setCurrentCompanyAdmins(int v) { this.currentCompanyAdmins = v; }

    public int getCurrentCustomers() { return currentCustomers; }
    public void setCurrentCustomers(int v) { this.currentCustomers = v; }

    public int getCurrentMonthlyTickets() { return currentMonthlyTickets; }
    public void setCurrentMonthlyTickets(int v) { this.currentMonthlyTickets = v; }

    public int getCurrentMonthlyProposals() { return currentMonthlyProposals; }
    public void setCurrentMonthlyProposals(int v) { this.currentMonthlyProposals = v; }

    public int getCurrentInventoryItems() { return currentInventoryItems; }
    public void setCurrentInventoryItems(int v) { this.currentInventoryItems = v; }

    public int getCurrentStorageMb() { return currentStorageMb; }
    public void setCurrentStorageMb(int v) { this.currentStorageMb = v; }
    public int getCurrentVehicles() { return currentVehicles; }
    public void setCurrentVehicles(int v) { this.currentVehicles = v; }
    public int getCurrentCommercialDevices() { return currentCommercialDevices; }
    public void setCurrentCommercialDevices(int v) { this.currentCommercialDevices = v; }

    public static QuotaDTO unlimited() {
        QuotaDTO q = new QuotaDTO();
        q.maxTechnicians = -1;
        q.maxCompanyAdmins = -1;
        q.maxCustomers = -1;
        q.maxMonthlyTickets = -1;
        q.maxMonthlyProposals = -1;
        q.maxInventoryItems = -1;
        q.storageLimitMb = -1;
        q.maxVehicles = -1;
        q.maxCommercialDevices = -1;
        return q;
    }
}
