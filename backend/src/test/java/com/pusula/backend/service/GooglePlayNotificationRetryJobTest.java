package com.pusula.backend.service;

import com.pusula.backend.entity.GooglePlayNotification;
import com.pusula.backend.repository.GooglePlayNotificationRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GooglePlayNotificationRetryJobTest {
    final GooglePlayNotificationRepository inbox = mock(GooglePlayNotificationRepository.class);
    final GooglePlayNotificationService service = mock(GooglePlayNotificationService.class);
    GooglePlayNotificationSettings settings(boolean enabled) {
        return new GooglePlayNotificationSettings(enabled, "https://example.test/callback", "push@example.test",
                "projects/test/subscriptions/play", "com.pusula.service");
    }
    GooglePlayNotification row(String id) {
        return new GooglePlayNotification(id, "hash", "token-hash", "encrypted-token", "SUBSCRIPTION_2", 1,
                LocalDateTime.now(ZoneOffset.UTC));
    }
    @Test void failedAttemptIsDeferredAndDoesNotBlockOtherDueMessages() {
        when(inbox.findTop20ByProcessingStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(any(), any()))
                .thenReturn(List.of(row("one"), row("two")));
        doThrow(new IllegalStateException("test-only")).when(service).process("one");
        new GooglePlayNotificationRetryJob(settings(true), inbox, service).retryDue();
        verify(service).defer("one"); verify(service).process("two");
    }
    @Test void disabledJobDoesNotQueryOrProcessOrCleanup() {
        var job = new GooglePlayNotificationRetryJob(settings(false), inbox, service);
        job.retryDue(); job.cleanup(); verifyNoInteractions(inbox, service);
    }
}
