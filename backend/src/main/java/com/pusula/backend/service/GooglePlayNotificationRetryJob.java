package com.pusula.backend.service;

import com.pusula.backend.repository.GooglePlayNotificationRepository;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.List;

@Component
public class GooglePlayNotificationRetryJob {
    private static final Logger log = LoggerFactory.getLogger(GooglePlayNotificationRetryJob.class);
    private final GooglePlayNotificationSettings settings;
    private final GooglePlayNotificationRepository inbox;
    private final GooglePlayNotificationService service;
    public GooglePlayNotificationRetryJob(GooglePlayNotificationSettings settings,
            GooglePlayNotificationRepository inbox, GooglePlayNotificationService service) {
        this.settings = settings; this.inbox = inbox; this.service = service;
    }
    @Scheduled(fixedDelayString = "${google.play.notifications.retry-delay-ms:60000}")
    public void retryDue() {
        if (!settings.configured()) return;
        var due = inbox.findTop20ByProcessingStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                List.of("PENDING", "RETRY", "WAITING_OWNER"), LocalDateTime.now(ZoneOffset.UTC));
        for (var row : due) {
            try { service.process(row.getId()); }
            catch (Exception ex) {
                log.warn("Google notification processing deferred, type={}", ex.getClass().getSimpleName());
                try { service.defer(row.getId()); }
                catch (Exception ignored) { log.warn("Google notification retry scheduling unavailable"); }
            }
        }
    }
    @Scheduled(fixedDelay = 86400000, initialDelay = 3600000)
    public void cleanup() { if (settings.configured()) service.cleanup(); }
}
