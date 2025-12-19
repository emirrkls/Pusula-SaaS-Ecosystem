package com.pusula.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleRequestDTO {
    private Long deviceId;
    private Long customerId;
    private BigDecimal sellingPrice;
    private String paymentMethod; // "CASH", "CREDIT_CARD", "CURRENT_ACCOUNT"
    private LocalDate saleDate;
}
