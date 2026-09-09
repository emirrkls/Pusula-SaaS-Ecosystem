package com.pusula.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentAccountDTO {
    private Long id;
    private Long companyId;
    private Long customerId;
    private String customerName;
    private Long partyId;
    private String partyType;
    private String accountName;
    private BigDecimal balance;
    private LocalDateTime lastUpdated;
}
