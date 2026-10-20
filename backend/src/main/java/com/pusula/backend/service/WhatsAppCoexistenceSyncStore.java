package com.pusula.backend.service;

import com.pusula.backend.dto.WhatsAppOnboardingDtos.SyncStatus;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class WhatsAppCoexistenceSyncStore {
    private final WhatsAppCoexistenceSyncJobRepository jobs;
    private final WhatsAppBusinessIntegrationRepository integrations;
    private final WhatsAppCoexistenceRecordRepository records;

    public WhatsAppCoexistenceSyncStore(WhatsAppCoexistenceSyncJobRepository jobs,
            WhatsAppBusinessIntegrationRepository integrations, WhatsAppCoexistenceRecordRepository records) {
        this.jobs = jobs; this.integrations = integrations; this.records = records;
    }

    @Transactional
    public void enqueue(WhatsAppBusinessIntegration integration, LocalDateTime now) {
        records.deleteByIntegrationId(integration.getId());
        if (!integration.isCoexistence()) return;
        for (String type : List.of("smb_app_state_sync", "history")) {
            WhatsAppCoexistenceSyncJob job = new WhatsAppCoexistenceSyncJob();
            job.setIntegrationId(integration.getId());
            job.setGeneration(integration.getCoexistenceGeneration());
            job.setSyncType(type);
            job.setNextAttemptAt(now);
            job.setDeadlineAt(now.plusHours(24));
            job.setUpdatedAt(now);
            jobs.save(job);
        }
    }

    @Transactional(readOnly = true)
    public SyncStatus snapshot(WhatsAppBusinessIntegration integration) {
        if (!integration.isCoexistence()) return null;
        List<WhatsAppCoexistenceSyncJob> current = jobs.findByIntegrationIdAndGeneration(
            integration.getId(), integration.getCoexistenceGeneration());
        WhatsAppCoexistenceSyncJob contacts = find(current, "smb_app_state_sync");
        WhatsAppCoexistenceSyncJob history = find(current, "history");
        String c = contacts == null ? "MISSING" : contacts.getStatus();
        String h = history == null ? "MISSING" : history.getStatus();
        boolean error = "FAILED".equals(integration.getWebhookSubscriptionStatus())
            || List.of(c, h).stream().anyMatch(s -> Set.of("MISSING", "FAILED", "UNKNOWN").contains(s));
        // Meta provides overall progress for history, but no final contact-snapshot marker.
        boolean ready = Set.of("ACCEPTED", "RECEIVING", "COMPLETE").contains(c)
            && Set.of("COMPLETE", "DECLINED").contains(h);
        String status = error ? "ACTION_REQUIRED" : ready ? "READY"
            : !"SUBSCRIBED".equals(integration.getWebhookSubscriptionStatus()) ? "WAITING_WEBHOOKS" : "SYNCING";
        return new SyncStatus(status, c, h, history == null ? 0 : history.getProgress());
    }

    private WhatsAppCoexistenceSyncJob find(List<WhatsAppCoexistenceSyncJob> list, String type) {
        return list.stream().filter(j -> type.equals(j.getSyncType())).findFirst().orElse(null);
    }

    @Transactional
    public List<Long> due(LocalDateTime now) {
        jobs.markStaleRequests(now.minusMinutes(2), now);
        jobs.expireWindows(now);
        return jobs.findDue(now, PageRequest.of(0, 10));
    }

    // Commit the intent BEFORE the network call. A worker restart may not repeat a one-shot API request.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Dispatch> claim(Long id, LocalDateTime now) {
        WhatsAppCoexistenceSyncJob job = jobs.lockById(id).orElse(null);
        if (job == null || !"PENDING".equals(job.getStatus()) || job.getNextAttemptAt().isAfter(now)) return Optional.empty();
        WhatsAppBusinessIntegration integration = integrations.findById(job.getIntegrationId()).orElse(null);
        if (integration == null || integration.isDeleted() || !integration.isCoexistence()
                || !"CONNECTED".equals(integration.getStatus())
                || !Objects.equals(job.getGeneration(), integration.getCoexistenceGeneration())
                || (integration.getTokenExpiresAt() != null && !integration.getTokenExpiresAt().isAfter(now))
                || !job.getDeadlineAt().isAfter(now)) {
            job.setStatus("FAILED"); job.setErrorCode("CONNECTION_UNAVAILABLE"); job.setUpdatedAt(now);
            jobs.save(job);
            return Optional.empty();
        }
        if (!"SUBSCRIBED".equals(integration.getWebhookSubscriptionStatus())) return Optional.empty();
        job.setStatus("REQUESTING");
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setRequestedAt(now); job.setUpdatedAt(now);
        jobs.saveAndFlush(job);
        return Optional.of(new Dispatch(id, integration.getPhoneNumberId(), job.getSyncType(),
            integration.getAccessTokenCiphertext()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void outcome(Long id, String requestId, String failure, boolean rejectedRetryable, LocalDateTime now) {
        WhatsAppCoexistenceSyncJob job = jobs.lockById(id).orElseThrow();
        if (requestId != null) job.setRequestId(requestId);
        // Webhooks can arrive before the HTTP response; never regress their progress/consent result.
        if ("REQUESTING".equals(job.getStatus()) || "UNKNOWN".equals(job.getStatus()) && requestId != null) {
            if (requestId != null) {
                job.setStatus("ACCEPTED"); job.setErrorCode(null);
            } else if (rejectedRetryable && job.getAttemptCount() < 6 && now.plusMinutes(2).isBefore(job.getDeadlineAt())) {
                job.setStatus("PENDING"); job.setErrorCode("META_RATE_LIMIT");
                job.setRequestedAt(null);
                job.setNextAttemptAt(now.plusSeconds(Math.min(300, 30L << Math.min(job.getAttemptCount(), 3))));
            } else {
                job.setStatus(rejectedRetryable || "META_REJECTED".equals(failure) ? "FAILED" : "UNKNOWN");
                job.setErrorCode(failure);
            }
        }
        job.setUpdatedAt(now); jobs.save(job);
    }

    public record Dispatch(Long id, String phoneNumberId, String syncType, String ciphertext) {
        @Override public String toString() { return "CoexistenceDispatch[id=" + id + ", type=" + syncType + "]"; }
    }
}
