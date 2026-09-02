package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CurrentAccountTransactionDTO(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDate effectiveDate,
        String description,
        String paymentMethod,
        String sourceType,
        Long sourceId,
        LocalDateTime createdAt) {
}
