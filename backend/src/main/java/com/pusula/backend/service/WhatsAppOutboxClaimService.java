package com.pusula.backend.service;

import com.pusula.backend.entity.WhatsAppMessageOutbox;
import com.pusula.backend.entity.WhatsAppOutboxStatus;
import com.pusula.backend.repository.WhatsAppMessageOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WhatsAppOutboxClaimService {
    private final WhatsAppMessageOutboxRepository repository;

    public WhatsAppOutboxClaimService(WhatsAppMessageOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<Long> claimDue(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<WhatsAppMessageOutbox> claimed = repository.lockDueBatch(
                now, now.minusMinutes(5), batchSize);
        for (WhatsAppMessageOutbox item : claimed) {
            item.setStatus(WhatsAppOutboxStatus.PROCESSING);
            item.setProcessingStartedAt(now);
            item.setAttemptCount(item.getAttemptCount() + 1);
        }
        repository.saveAll(claimed);
        return claimed.stream().map(WhatsAppMessageOutbox::getId).toList();
    }
}
