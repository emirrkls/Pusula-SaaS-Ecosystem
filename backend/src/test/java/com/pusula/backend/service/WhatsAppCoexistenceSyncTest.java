package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppCoexistenceSyncTest {
    @Mock WhatsAppCoexistenceSyncJobRepository jobs;
    @Mock WhatsAppCoexistenceRecordRepository records;
    @Mock WhatsAppBusinessIntegrationRepository integrations;
    @Mock WhatsAppCloudApiClient client;
    @Mock WhatsAppCredentialCrypto crypto;
    WhatsAppCoexistenceSyncStore store;
    LocalDateTime now = LocalDateTime.now();
    @BeforeEach void setup() { store = new WhatsAppCoexistenceSyncStore(jobs, integrations, records); }
    WhatsAppCoexistenceSyncJob job(String type, String status) {
        var job = new WhatsAppCoexistenceSyncJob(); job.setId(1L); job.setIntegrationId(22L);
        job.setGeneration("g"); job.setSyncType(type); job.setStatus(status); job.setNextAttemptAt(now.minusSeconds(1));
        job.setRequestedAt(now.minusSeconds(1)); job.setDeadlineAt(now.plusHours(23)); return job;
    }
    WhatsAppBusinessIntegration integration() {
        var integration = new WhatsAppBusinessIntegration(); ReflectionTestUtils.setField(integration, "id", 22L);
        integration.setCoexistence(true); integration.setCoexistenceGeneration("g");
        integration.setWebhookSubscriptionStatus("SUBSCRIBED"); integration.setStatus("CONNECTED");
        integration.setPhoneNumberId("67890"); integration.setAccessTokenCiphertext("ciphertext");
        return integration;
    }
    @Test void snapshotDoesNotClaimReadyUntilHistoryCompletedOrExplicitlyDeclined() {
        var c = job("smb_app_state_sync", "ACCEPTED"); var h = job("history", "ACCEPTED");
        when(jobs.findByIntegrationIdAndGeneration(22L, "g")).thenReturn(List.of(c,h));
        assertEquals("SYNCING", store.snapshot(integration()).status());
        h.setStatus("DECLINED"); assertEquals("READY", store.snapshot(integration()).status());
        h.setStatus("COMPLETE"); assertEquals("READY", store.snapshot(integration()).status());
        h.setStatus("UNKNOWN"); assertEquals("ACTION_REQUIRED", store.snapshot(integration()).status());
        h.setStatus("COMPLETE");
        var failedSubscription = integration(); failedSubscription.setWebhookSubscriptionStatus("FAILED");
        assertEquals("ACTION_REQUIRED", store.snapshot(failedSubscription).status());
    }
    @Test void oneShotIntentIsCommittedAndOnlyPendingJobsCanBeClaimed() {
        var job = job("history", "PENDING");
        when(jobs.lockById(1L)).thenReturn(Optional.of(job));
        when(integrations.findById(22L)).thenReturn(Optional.of(integration()));
        assertTrue(store.claim(1L, now).isPresent());
        assertEquals("REQUESTING", job.getStatus());
        verify(jobs).saveAndFlush(job);
        assertTrue(store.claim(1L, now).isEmpty());
    }
    @Test void staleGenerationCannotBeDispatched() {
        var job = job("history", "PENDING"); job.setGeneration("old");
        when(jobs.lockById(1L)).thenReturn(Optional.of(job));
        when(integrations.findById(22L)).thenReturn(Optional.of(integration()));
        assertTrue(store.claim(1L, now).isEmpty()); assertEquals("FAILED", job.getStatus());
    }
    @Test void ambiguousNetworkResultNeverSchedulesAutomaticRepeat() {
        var job = job("history", "REQUESTING"); when(jobs.lockById(1L)).thenReturn(Optional.of(job));
        store.outcome(1L, null, "REQUEST_OUTCOME_UNKNOWN", false, now);
        assertEquals("UNKNOWN", job.getStatus());
    }
    @Test void onlyDefiniteRateLimitRejectionCanBeRetried() {
        var job = job("history", "REQUESTING"); job.setAttemptCount(1);
        when(jobs.lockById(1L)).thenReturn(Optional.of(job));
        store.outcome(1L, null, "META_RATE_LIMIT", true, now);
        assertEquals("PENDING", job.getStatus()); assertTrue(job.getNextAttemptAt().isAfter(now));
    }
    @Test void httpAcceptanceDoesNotOverwriteEarlierConsentOrProgressWebhook() {
        var job = job("history", "DECLINED"); when(jobs.lockById(1L)).thenReturn(Optional.of(job));
        store.outcome(1L, "req-1", null, false, now);
        assertEquals("DECLINED", job.getStatus()); assertEquals("req-1", job.getRequestId());
        job.setStatus("COMPLETE"); job.setProgress(100);
        store.outcome(1L, "req-1", null, false, now);
        assertEquals("COMPLETE", job.getStatus()); assertEquals(100, job.getProgress());
    }
    @Test void workerIsDisabledUnlessApiIsEnabled() {
        var worker = new WhatsAppCoexistenceSyncWorker(store, client, crypto, false);
        worker.runDue(); verifyNoInteractions(jobs, client, crypto);
    }
}
