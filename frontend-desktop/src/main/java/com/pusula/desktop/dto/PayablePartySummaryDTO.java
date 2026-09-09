package com.pusula.desktop.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayablePartySummaryDTO {
    private Long partyId;
    private String name;
    private String phone;
    private BigDecimal totalPurchases;
    private BigDecimal totalPaid;
    private BigDecimal balance;
    private LocalDate firstDebtDate;
    private LocalDate lastMovementDate;
    private long openItemCount;
}
