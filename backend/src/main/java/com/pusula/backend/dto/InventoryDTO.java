package com.pusula.backend.dto;

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
public class InventoryDTO {
    private UUID id;
    private String partName;
    private Integer quantity;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private Integer criticalLevel;
}
