package com.pusula.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalDTO {
    private Long id;
    private Long companyId;
    private Long customerId;
    private String customerName;
    private Long preparedById;
    private String preparedByName;
    private String status;
    private LocalDate validUntil;
    private String note;
    private String title;
    private BigDecimal taxRate;
    private BigDecimal discount;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalPrice;
    private List<ProposalItemDTO> items;
}
