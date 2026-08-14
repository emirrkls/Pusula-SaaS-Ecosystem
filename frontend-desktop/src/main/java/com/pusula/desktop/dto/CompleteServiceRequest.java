package com.pusula.desktop.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CompleteServiceRequest {
    private BigDecimal collectedAmount;
    private BigDecimal laborCost;
    private String paymentMethod;
    private LocalDate completionDate;
    private String technicianNote;

    public CompleteServiceRequest() {
    }

    public CompleteServiceRequest(BigDecimal collectedAmount, BigDecimal laborCost) {
        this.collectedAmount = collectedAmount;
        this.laborCost = laborCost;
        this.paymentMethod = "CASH"; // Default
    }

    public CompleteServiceRequest(BigDecimal collectedAmount, String paymentMethod) {
        this.collectedAmount = collectedAmount;
        this.paymentMethod = paymentMethod;
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }

    public String getTechnicianNote() { return technicianNote; }
    public void setTechnicianNote(String technicianNote) { this.technicianNote = technicianNote; }
}
