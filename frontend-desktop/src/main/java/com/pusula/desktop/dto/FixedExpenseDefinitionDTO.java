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
    private BigDecimal paidAmountThisMonth; // Track partial payments
    private String frequency; // "MONTHLY" or "WEEKLY" from backend Enum

    // Parent-child expense linking
    private Long linkedExpenseId; // ID of the linked expense (e.g., weekly linked to monthly)
    private String linkedExpenseName; // Name of linked expense for display
    private BigDecimal linkedPaymentsThisMonth; // Total payments from linked expense this month
}
