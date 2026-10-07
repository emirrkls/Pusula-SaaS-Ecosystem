package com.pusula.backend.service;

import com.pusula.backend.repository.AppStoreNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppStoreNotificationRetryJob {
    private static final Logger log = LoggerFactory.getLogger(AppStoreNotificationRetryJob.class);
    private final AppStoreNotificationRepository events;
    private final AppStoreNotificationService service;
    public AppStoreNotificationRetryJob(AppStoreNotificationRepository events, AppStoreNotificationService service) {
        this.events = events; this.service = service;
    }
    @Scheduled(fixedDelayString = "${apple.app-store.notification-retry-ms:60000}")
    public void retryUnboundNotifications() {
        for (var event : events.findTop100ByProcessingStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                "WAITING_OWNER", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            try { service.retry(event.getId()); }
            catch (RuntimeException ex) {
                // Neither signed payloads nor transaction identifiers belong in logs.
                log.warn("App Store notification retry failed; exceptionType={}", ex.getClass().getSimpleName());
            }
        }
    }
}
