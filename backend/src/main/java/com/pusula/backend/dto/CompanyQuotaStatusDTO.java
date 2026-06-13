package com.pusula.backend.dto;

public class CompanyQuotaStatusDTO {
    private String plan;
    private int monthlyUsage;
    private int monthlyLimit;
    private double limitPercent;
    private boolean nearLimitWarning;

    public CompanyQuotaStatusDTO(String plan, int monthlyUsage, int monthlyLimit, double limitPercent, boolean nearLimitWarning) {
        this.plan = plan;
        this.monthlyUsage = monthlyUsage;
        this.monthlyLimit = monthlyLimit;
        this.limitPercent = limitPercent;
        this.nearLimitWarning = nearLimitWarning;
    }

    public String getPlan() {
        return plan;
    }

    public int getMonthlyUsage() {
        return monthlyUsage;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    public double getLimitPercent() {
        return limitPercent;
    }

    public boolean isNearLimitWarning() {
        return nearLimitWarning;
    }
}
