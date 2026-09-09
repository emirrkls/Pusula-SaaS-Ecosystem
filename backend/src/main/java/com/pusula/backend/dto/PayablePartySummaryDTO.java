package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayablePartySummaryDTO(
        Long partyId, String name, String phone, BigDecimal totalPurchases,
        BigDecimal totalPaid, BigDecimal balance, LocalDate firstDebtDate,
        LocalDate lastMovementDate, long openItemCount) {
}
