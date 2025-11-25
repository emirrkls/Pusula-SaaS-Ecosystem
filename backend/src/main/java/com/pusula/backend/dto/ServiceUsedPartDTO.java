package com.pusula.backend.dto;

import java.math.BigDecimal;

public class ServiceUsedPartDTO {
    private Long id;
    private Long ticketId;
    private Long inventoryId;
    private String partName;
    private Integer quantityUsed;
    private BigDecimal sellingPriceSnapshot;

    public ServiceUsedPartDTO() {
    }

    public ServiceUsedPartDTO(Long id, Long ticketId, Long inventoryId, String partName, Integer quantityUsed,
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
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
        private Long id;
        private Long ticketId;
        private Long inventoryId;
        private String partName;
        private Integer quantityUsed;
        private BigDecimal sellingPriceSnapshot;

        ServiceUsedPartDTOBuilder() {
        }

        public ServiceUsedPartDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ServiceUsedPartDTOBuilder ticketId(Long ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public ServiceUsedPartDTOBuilder inventoryId(Long inventoryId) {
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
