package com.pusula.desktop.dto;

import java.math.BigDecimal;

public class CompleteServiceRequest {
    private BigDecimal collectedAmount;
    private BigDecimal laborCost;

    public CompleteServiceRequest() {
    }

    public CompleteServiceRequest(BigDecimal collectedAmount, BigDecimal laborCost) {
        this.collectedAmount = collectedAmount;
        this.laborCost = laborCost;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public BigDecimal getLaborCost() {
        return laborCost;
    }

    public void setLaborCost(BigDecimal laborCost) {
        this.laborCost = laborCost;
    }
}
