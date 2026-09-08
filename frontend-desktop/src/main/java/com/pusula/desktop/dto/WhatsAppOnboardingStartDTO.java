package com.pusula.desktop.dto;

import java.time.LocalDateTime;

public class WhatsAppOnboardingStartDTO {
    private String url;
    private String appId;
    private String configurationId;
    private LocalDateTime expiresAt;

    public String getUrl() { return url; }
    public String getAppId() { return appId; }
    public String getConfigurationId() { return configurationId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
