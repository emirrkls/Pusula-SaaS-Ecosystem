package com.pusula.backend.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.entity.PushEnvironment;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class PushyApnsGateway implements ApnsGateway {
    private final ApplePushProperties properties;
    private final ApnsClient sandboxClient;
    private final ApnsClient productionClient;

    public PushyApnsGateway(ApplePushProperties properties) {
        this.properties = properties;
        if (!properties.isEnabled()) {
            this.sandboxClient = null;
            this.productionClient = null;
            return;
        }
        requireCredentials(properties);
        try {
            ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(
                    new File(properties.getKeyPath()), properties.getTeamId(), properties.getKeyId());
            this.sandboxClient = build(ApnsClientBuilder.DEVELOPMENT_APNS_HOST, signingKey);
            this.productionClient = build(ApnsClientBuilder.PRODUCTION_APNS_HOST, signingKey);
        } catch (Exception ex) {
            throw new IllegalStateException("APNs clients could not be initialized", ex);
        }
    }

    private ApnsClient build(String host, ApnsSigningKey signingKey) throws Exception {
        return new ApnsClientBuilder()
                .setApnsServer(host)
                .setSigningKey(signingKey)
                .setConnectionTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ApnsDeliveryResult send(String deviceToken, PushEnvironment environment, String payload) {
        if (!properties.isEnabled()) {
            return ApnsDeliveryResult.disabledResult();
        }
        try {
            ApnsClient client = environment == PushEnvironment.SANDBOX ? sandboxClient : productionClient;
            SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
                    deviceToken, properties.getBundleId(), payload);
            PushNotificationResponse<SimpleApnsPushNotification> response =
                    client.sendNotification(notification).get(20, TimeUnit.SECONDS);
            return response.isAccepted()
                    ? ApnsDeliveryResult.acceptedResult()
                    : ApnsDeliveryResult.rejected(response.getRejectionReason().orElse("Unknown"));
        } catch (Exception ex) {
            throw new IllegalStateException("APNs delivery failed", ex);
        }
    }

    @PreDestroy
    public void close() {
        closeClient(sandboxClient);
        closeClient(productionClient);
    }

    private void closeClient(ApnsClient client) {
        if (client != null) {
            client.close();
        }
    }

    private void requireCredentials(ApplePushProperties p) {
        if (isBlank(p.getKeyPath()) || isBlank(p.getKeyId()) || isBlank(p.getTeamId()) || isBlank(p.getBundleId())) {
            throw new IllegalStateException("APNs is enabled but provider credentials are incomplete");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
