package com.pusula.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AppleVerifyRequest {

    @Size(max = 255)
    private String transactionId;

    @Size(max = 255)
    private String productId;

    @Size(max = 32)
    private String plan;

    @NotBlank(message = "signedTransactionInfo zorunludur")
    @Size(max = 20000, message = "signedTransactionInfo cok uzun")
    private String signedTransactionInfo;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getSignedTransactionInfo() {
        return signedTransactionInfo;
    }

    public void setSignedTransactionInfo(String signedTransactionInfo) {
        this.signedTransactionInfo = signedTransactionInfo;
    }
}
