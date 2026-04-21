package com.pusula.backend.dto;

/**
 * Quota information returned to the client on login and token refresh.
 * The iOS app uses this to display quota bars and enforce local limits.
 */
public class QuotaDTO {
    private int maxTechnicians;
    private int maxCustomers;
    private int maxMonthlyTickets;
    private int maxMonthlyProposals;
    private int maxInventoryItems;
    private int storageLimitMb;
    // Current usage
    private int currentTechnicians;
    private int currentCustomers;
    private int currentMonthlyTickets;
    private int currentMonthlyProposals;
    private int currentInventoryItems;
    private int currentStorageMb;

    public QuotaDTO() {}

    // --- Getters / Setters ---
    public int getMaxTechnicians() { return maxTechnicians; }
    public void setMaxTechnicians(int v) { this.maxTechnicians = v; }

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

    public int getCurrentTechnicians() { return currentTechnicians; }
    public void setCurrentTechnicians(int v) { this.currentTechnicians = v; }

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

    public static QuotaDTO unlimited() {
        QuotaDTO q = new QuotaDTO();
        q.maxTechnicians = -1;
        q.maxCustomers = -1;
        q.maxMonthlyTickets = -1;
        q.maxMonthlyProposals = -1;
        q.maxInventoryItems = -1;
        q.storageLimitMb = -1;
        return q;
    }
}
