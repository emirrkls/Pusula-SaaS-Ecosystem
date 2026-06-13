package com.pusula.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleVerifyRequest {

    @NotBlank(message = "purchaseToken zorunludur")
    private String purchaseToken;

    @NotBlank(message = "productId zorunludur")
    private String productId;

    @NotBlank(message = "plan zorunludur")
    private String plan;

    public String getPurchaseToken() {
        return purchaseToken;
    }

    public void setPurchaseToken(String purchaseToken) {
        this.purchaseToken = purchaseToken;
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
}
