package com.pusula.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtPaymentRequestDTO {
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String notes;
}
