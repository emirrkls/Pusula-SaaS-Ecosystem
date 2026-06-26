package com.pusula.backend.repository;

import com.pusula.backend.entity.WebhookEvent;
import com.pusula.backend.entity.WebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    long countByStatusAndCreatedAtAfter(WebhookEventStatus status, LocalDateTime createdAt);
    List<WebhookEvent> findTop5ByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime createdAt);
}
