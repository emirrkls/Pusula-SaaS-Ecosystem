package com.pusula.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceUsedPartDTO {
    private Long id;
    private Long ticketId;
    private Long inventoryId;
    private String partName;
    private Integer quantityUsed;
    private BigDecimal sellingPriceSnapshot;
}
