package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record CurrentAccountHistoryDTO(
        Long accountId,
        Long customerId,
        String customerName,
        Long partyId,
        String partyType,
        String accountName,
        BigDecimal currentBalance,
        List<CurrentAccountTransactionDTO> transactions) {
}
