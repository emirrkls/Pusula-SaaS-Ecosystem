package com.pusula.desktop.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BusinessAssetDTO {
    private Long id;
    private String assetName;
    private String category;
    private Integer quantity;
    private String condition;
    private String serialNumber;
    private String location;
    private String assignedTo;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private String notes;
}
