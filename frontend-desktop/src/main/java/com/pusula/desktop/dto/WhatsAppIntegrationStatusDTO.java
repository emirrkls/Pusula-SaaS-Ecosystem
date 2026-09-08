package com.pusula.desktop.dto;

import java.time.LocalDateTime;

public class WhatsAppIntegrationStatusDTO {
    private boolean connected;
    private String displayPhoneNumber;
    private String verifiedName;
    private String status;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime updatedAt;

    public boolean isConnected() { return connected; }
    public String getDisplayPhoneNumber() { return displayPhoneNumber; }
    public String getVerifiedName() { return verifiedName; }
    public String getStatus() { return status; }
    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
