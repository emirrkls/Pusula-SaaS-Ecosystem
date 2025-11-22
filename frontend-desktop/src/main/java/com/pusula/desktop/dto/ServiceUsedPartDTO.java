package com.pusula.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceUsedPartDTO {
    private UUID id;
    private UUID ticketId;
    private UUID inventoryId;
    private String partName;
    private Integer quantityUsed;
    private BigDecimal sellingPriceSnapshot;
}
