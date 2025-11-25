package com.pusula.desktop.dto;

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
public class DailyTotalDTO {
    private LocalDate date;
    private BigDecimal income;
    private BigDecimal expense;
}
