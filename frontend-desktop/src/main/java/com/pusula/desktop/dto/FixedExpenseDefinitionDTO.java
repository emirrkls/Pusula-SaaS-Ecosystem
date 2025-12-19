package com.pusula.desktop.dto;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixedExpenseDefinitionDTO {
    private Long id;
    private Long companyId;
    private String name;
    private BigDecimal defaultAmount;
    private String category;
    private Integer dayOfMonth;
    private String description;
    @SerializedName("isPaidThisMonth")
    private boolean paidThisMonth; // Maps to backend's isPaidThisMonth, getter: isPaidThisMonth()
    private String frequency; // "MONTHLY" or "WEEKLY" from backend Enum
}
