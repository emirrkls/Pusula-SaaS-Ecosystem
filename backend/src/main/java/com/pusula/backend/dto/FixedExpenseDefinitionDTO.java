package com.pusula.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isPaidThisMonth")
    private boolean paidThisMonth; // Serialized as "isPaidThisMonth" in JSON
    private BigDecimal paidAmountThisMonth; // Track partial payments
    private String frequency; // MONTHLY or WEEKLY

    // Parent-child expense linking
    private Long linkedExpenseId; // ID of linked expense (weekly linked to monthly)
    private String linkedExpenseName; // Name of linked expense for display
    private BigDecimal linkedPaymentsThisMonth; // Total payments made by linked expense this month
}
