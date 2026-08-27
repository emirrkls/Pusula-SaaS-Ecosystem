package com.pusula.backend.dto;

import java.math.BigDecimal;

/**
 * Technician-safe inventory DTO — buyPrice is NEVER included.
 * This DTO is returned when the authenticated user has TECHNICIAN role.
 * 
 * SECURITY: The absence of buyPrice is intentional and critical.
 * Technicians in the field must NEVER see cost data on their devices.
 */
public class InventoryTechDTO {
    private Long id;
    private String partName;
    private BigDecimal quantity;
    private BigDecimal sellPrice; // ONLY sell price visible
    private String unitOfMeasure;
    private String brand;
    private String category;
    private String barcode;

    public InventoryTechDTO() {}

    public InventoryTechDTO(Long id, String partName, BigDecimal quantity, BigDecimal sellPrice,
                            String unitOfMeasure, String brand, String category, String barcode) {
        this.id = id;
        this.partName = partName;
        this.quantity = quantity;
        this.sellPrice = sellPrice;
        this.unitOfMeasure = unitOfMeasure;
        this.brand = brand;
        this.category = category;
        this.barcode = barcode;
    }

    // --- Getters / Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getSellPrice() { return sellPrice; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }

    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
