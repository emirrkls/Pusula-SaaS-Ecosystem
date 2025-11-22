package com.pusula.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class ServiceUsedPartDTO {
    private UUID id;
    private UUID ticketId;
    private UUID inventoryId;
    private String partName;
    private Integer quantityUsed;
    private BigDecimal sellingPriceSnapshot;

    public ServiceUsedPartDTO() {
    }

    public ServiceUsedPartDTO(UUID id, UUID ticketId, UUID inventoryId, String partName, Integer quantityUsed,
            BigDecimal sellingPriceSnapshot) {
        this.id = id;
        this.ticketId = ticketId;
        this.inventoryId = inventoryId;
        this.partName = partName;
        this.quantityUsed = quantityUsed;
        this.sellingPriceSnapshot = sellingPriceSnapshot;
    }

    public static ServiceUsedPartDTOBuilder builder() {
        return new ServiceUsedPartDTOBuilder();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(UUID inventoryId) {
        this.inventoryId = inventoryId;
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public Integer getQuantityUsed() {
        return quantityUsed;
    }

    public void setQuantityUsed(Integer quantityUsed) {
        this.quantityUsed = quantityUsed;
    }

    public BigDecimal getSellingPriceSnapshot() {
        return sellingPriceSnapshot;
    }

    public void setSellingPriceSnapshot(BigDecimal sellingPriceSnapshot) {
        this.sellingPriceSnapshot = sellingPriceSnapshot;
    }

    public static class ServiceUsedPartDTOBuilder {
        private UUID id;
        private UUID ticketId;
        private UUID inventoryId;
        private String partName;
        private Integer quantityUsed;
        private BigDecimal sellingPriceSnapshot;

        ServiceUsedPartDTOBuilder() {
        }

        public ServiceUsedPartDTOBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public ServiceUsedPartDTOBuilder ticketId(UUID ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public ServiceUsedPartDTOBuilder inventoryId(UUID inventoryId) {
            this.inventoryId = inventoryId;
            return this;
        }

        public ServiceUsedPartDTOBuilder partName(String partName) {
            this.partName = partName;
            return this;
        }

        public ServiceUsedPartDTOBuilder quantityUsed(Integer quantityUsed) {
            this.quantityUsed = quantityUsed;
            return this;
        }

        public ServiceUsedPartDTOBuilder sellingPriceSnapshot(BigDecimal sellingPriceSnapshot) {
            this.sellingPriceSnapshot = sellingPriceSnapshot;
            return this;
        }

        public ServiceUsedPartDTO build() {
            return new ServiceUsedPartDTO(id, ticketId, inventoryId, partName, quantityUsed, sellingPriceSnapshot);
        }
    }
}
