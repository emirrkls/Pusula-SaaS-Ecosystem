package com.pusula.backend.repository;

import com.pusula.backend.entity.PaymentEvent;
import com.pusula.backend.entity.PaymentEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    Optional<PaymentEvent> findByProviderAndTokenHash(String provider, String tokenHash);
    long countByStatusAndCreatedAtAfter(PaymentEventStatus status, LocalDateTime createdAt);
}
