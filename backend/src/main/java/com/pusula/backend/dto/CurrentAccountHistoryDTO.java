package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record CurrentAccountHistoryDTO(
        Long accountId,
        Long customerId,
        String customerName,
        BigDecimal currentBalance,
        List<CurrentAccountTransactionDTO> transactions) {
}
