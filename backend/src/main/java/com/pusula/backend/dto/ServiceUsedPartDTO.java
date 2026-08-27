package com.pusula.backend.dto;

import java.math.BigDecimal;

public class ServiceUsedPartDTO {
    private Long id;
    private Long ticketId;
    private Long inventoryId;
    private String partName;
    private BigDecimal quantityUsed;
    private BigDecimal sellingPriceSnapshot;
    private String unitOfMeasure = "ADET";
    private Long sourceVehicleId; // NULL = main inventory, set = from vehicle
    private String clientRequestId;

    public ServiceUsedPartDTO() {
    }

    public ServiceUsedPartDTO(Long id, Long ticketId, Long inventoryId, String partName, BigDecimal quantityUsed,
            BigDecimal sellingPriceSnapshot) {
        this.id = id;
        this.ticketId = ticketId;
        this.inventoryId = inventoryId;
        this.partName = partName;
        this.quantityUsed = quantityUsed;
        this.sellingPriceSnapshot = sellingPriceSnapshot;
    }

    public ServiceUsedPartDTO(Long id, Long ticketId, Long inventoryId, String partName, BigDecimal quantityUsed,
            BigDecimal sellingPriceSnapshot, Long sourceVehicleId) {
        this(id, ticketId, inventoryId, partName, quantityUsed, sellingPriceSnapshot);
        this.sourceVehicleId = sourceVehicleId;
    }

    public ServiceUsedPartDTO(Long id, Long ticketId, Long inventoryId, String partName, BigDecimal quantityUsed,
            BigDecimal sellingPriceSnapshot, Long sourceVehicleId, String clientRequestId, String unitOfMeasure) {
        this(id, ticketId, inventoryId, partName, quantityUsed, sellingPriceSnapshot, sourceVehicleId);
        this.clientRequestId = clientRequestId;
        this.unitOfMeasure = unitOfMeasure;
    }

    public ServiceUsedPartDTO(Long id, Long ticketId, Long inventoryId, String partName, BigDecimal quantityUsed,
            BigDecimal sellingPriceSnapshot, Long sourceVehicleId, String clientRequestId) {
        this(id, ticketId, inventoryId, partName, quantityUsed, sellingPriceSnapshot, sourceVehicleId,
                clientRequestId, "ADET");
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

    public BigDecimal getQuantityUsed() {
        return quantityUsed;
    }

    public void setQuantityUsed(BigDecimal quantityUsed) {
        this.quantityUsed = quantityUsed;
    }

    public BigDecimal getSellingPriceSnapshot() {
        return sellingPriceSnapshot;
    }

    public void setSellingPriceSnapshot(BigDecimal sellingPriceSnapshot) {
        this.sellingPriceSnapshot = sellingPriceSnapshot;
    }

    public Long getSourceVehicleId() {
        return sourceVehicleId;
    }

    public void setSourceVehicleId(Long sourceVehicleId) {
        this.sourceVehicleId = sourceVehicleId;
    }

    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public static class ServiceUsedPartDTOBuilder {
        private Long id;
        private Long ticketId;
        private Long inventoryId;
        private String partName;
        private BigDecimal quantityUsed;
        private BigDecimal sellingPriceSnapshot;
        private Long sourceVehicleId;
        private String clientRequestId;
        private String unitOfMeasure = "ADET";

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

        public ServiceUsedPartDTOBuilder quantityUsed(BigDecimal quantityUsed) {
            this.quantityUsed = quantityUsed;
            return this;
        }

        public ServiceUsedPartDTOBuilder quantityUsed(Integer quantityUsed) {
            return quantityUsed(quantityUsed == null ? null : BigDecimal.valueOf(quantityUsed));
        }

        public ServiceUsedPartDTOBuilder sellingPriceSnapshot(BigDecimal sellingPriceSnapshot) {
            this.sellingPriceSnapshot = sellingPriceSnapshot;
            return this;
        }

        public ServiceUsedPartDTOBuilder sourceVehicleId(Long sourceVehicleId) {
            this.sourceVehicleId = sourceVehicleId;
            return this;
        }

        public ServiceUsedPartDTOBuilder unitOfMeasure(String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
            return this;
        }

        public ServiceUsedPartDTOBuilder clientRequestId(String clientRequestId) {
            this.clientRequestId = clientRequestId;
            return this;
        }

        public ServiceUsedPartDTO build() {
            return new ServiceUsedPartDTO(id, ticketId, inventoryId, partName, quantityUsed, sellingPriceSnapshot,
                    sourceVehicleId, clientRequestId, unitOfMeasure);
        }
    }
}
