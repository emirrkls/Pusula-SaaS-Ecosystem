package com.pusula.backend.service;

import com.pusula.backend.repository.GooglePlayPurchaseRepository;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Set;

/** No enclosing transaction: Google is acknowledged only after the entitlement transaction commits. */
@Component
public class GooglePlayLifecycleJob {
    private static final Logger log=LoggerFactory.getLogger(GooglePlayLifecycleJob.class);
    private final GooglePlayReceiptStore store;
    private final GooglePlayPurchaseRepository receipts;
    private final SubscriptionService subscriptions;
    private final GooglePlayAcknowledgementService acknowledgements;
    public GooglePlayLifecycleJob(GooglePlayReceiptStore store, GooglePlayPurchaseRepository receipts,
            SubscriptionService subscriptions, GooglePlayAcknowledgementService acknowledgements) {
        this.store=store; this.receipts=receipts; this.subscriptions=subscriptions; this.acknowledgements=acknowledgements;
    }
    @Scheduled(fixedDelayString="${google.play.lifecycle.delay-ms:60000}")
    public void reconcileDue() {
        if (!store.enabled()) return;
        for (var row:receipts.findTop20ByEncryptedTokenIsNotNullAndNextCheckAtLessThanEqualOrderByNextCheckAtAsc(LocalDateTime.now(ZoneOffset.UTC))) {
            try {
                if (row.getExpiresAt()!=null && row.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).minusDays(60))) {
                    store.retire(row.getId()); continue;
                }
                String token=store.token(row);
                String outcome=subscriptions.reconcileGoogleNotification(token);
                if ("IGNORED_STALE".equals(outcome)) { store.retire(row.getId()); continue; }
                if (!"APPLIED".equals(outcome)) { store.deferred(row.getId()); continue; }
                var latest=store.find(row.getId()).orElse(null);
                if (latest!=null && !latest.isRetired() && "ACKNOWLEDGEMENT_STATE_PENDING".equals(latest.getAcknowledgementState())
                        && Set.of("SUBSCRIPTION_STATE_ACTIVE","SUBSCRIPTION_STATE_IN_GRACE_PERIOD","SUBSCRIPTION_STATE_CANCELED").contains(latest.getState())
                        && latest.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    if (acknowledgements.acknowledge(token,latest.getProductId())) store.acknowledged(row.getId());
                    else store.deferred(row.getId());
                }
            } catch (Exception ex) {
                log.warn("Google lifecycle reconciliation deferred, type={}",ex.getClass().getSimpleName());
                try { store.deferred(row.getId()); } catch (Exception ignored) { log.warn("Google lifecycle retry scheduling unavailable"); }
            }
        }
    }
}
