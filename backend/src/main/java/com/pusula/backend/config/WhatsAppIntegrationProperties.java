package com.pusula.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppIntegrationProperties {
    private final String appId;
    private final String appSecret;
    private final String configurationId;
    private final String connectUrl;
    private final String credentialEncryptionKey;
    private final String graphVersion;

    public WhatsAppIntegrationProperties(
            @Value("${whatsapp.onboarding.app-id:}") String appId,
            @Value("${whatsapp.onboarding.app-secret:}") String appSecret,
            @Value("${whatsapp.onboarding.configuration-id:}") String configurationId,
            @Value("${whatsapp.onboarding.connect-url:https://www.pusulaiklimlendirme.com/whatsapp-connect}") String connectUrl,
            @Value("${whatsapp.onboarding.credential-encryption-key:}") String credentialEncryptionKey,
            @Value("${whatsapp.api.graph-version:v26.0}") String graphVersion) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.configurationId = configurationId;
        this.connectUrl = connectUrl;
        this.credentialEncryptionKey = credentialEncryptionKey;
        this.graphVersion = graphVersion;
    }

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
    public String getConfigurationId() { return configurationId; }
    public String getConnectUrl() { return connectUrl; }
    public String getCredentialEncryptionKey() { return credentialEncryptionKey; }
    public String getGraphVersion() { return graphVersion; }
}
