package com.pusula.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseDayRequest {
    private Long companyId;
    private LocalDate date;
    private Long userId;
}
