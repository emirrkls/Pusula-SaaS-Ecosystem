package com.pusula.desktop.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtAdditionRequestDTO {
    private BigDecimal amount;
    private LocalDate additionDate;
    private String notes;
}
