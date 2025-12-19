package com.pusula.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalItemDTO {
    private Long id;
    private String description;
    private Integer quantity;
    private BigDecimal unitCost; // Admin-only, will be filtered
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
