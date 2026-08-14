package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.pusula.backend.entity.PaymentMethod;

public class CompleteServiceRequest {
    private BigDecimal collectedAmount;
    @JsonAlias("laborCost")
    private BigDecimal laborFee;
    private PaymentMethod paymentMethod;
    private LocalDate completionDate;
    private String technicianNote;

    public CompleteServiceRequest() {
    }

    public CompleteServiceRequest(BigDecimal collectedAmount, BigDecimal laborCost) {
        this.collectedAmount = collectedAmount;
        this.laborFee = laborCost;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public BigDecimal getLaborCost() {
        return laborFee;
    }

    public void setLaborCost(BigDecimal laborCost) {
        this.laborFee = laborCost;
    }

    public BigDecimal getLaborFee() {
        return laborFee;
    }

    public void setLaborFee(BigDecimal laborFee) {
        this.laborFee = laborFee;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }

    public String getTechnicianNote() {
        return technicianNote;
    }

    public void setTechnicianNote(String technicianNote) {
        this.technicianNote = technicianNote;
    }
}
