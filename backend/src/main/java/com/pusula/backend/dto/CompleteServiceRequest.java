package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.pusula.backend.entity.PaymentMethod;

public class CompleteServiceRequest {
    private BigDecimal collectedAmount;
    private BigDecimal laborCost;
    private PaymentMethod paymentMethod;
    private LocalDate completionDate;

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
}
