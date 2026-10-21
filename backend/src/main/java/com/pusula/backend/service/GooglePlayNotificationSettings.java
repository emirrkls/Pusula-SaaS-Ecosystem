package com.pusula.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record GooglePlayNotificationSettings(
        @Value("${google.play.notifications.enabled:false}") boolean enabled,
        @Value("${google.play.notifications.audience:}") String audience,
        @Value("${google.play.notifications.service-account-email:}") String email,
        @Value("${google.play.notifications.subscription:}") String subscription,
        @Value("${google.play.package-name:}") String packageName) {
    public boolean configured() {
        return enabled && audience.startsWith("https://") && !email.isBlank()
                && subscription.matches("projects/[^/]+/subscriptions/[^/]+") && !packageName.isBlank();
    }
}
