package com.pusula.backend.dto;

import java.util.List;

public class BulkTicketAssignmentRequest {
    private List<Long> ticketIds;
    private Long technicianId;

    public List<Long> getTicketIds() {
        return ticketIds;
    }

    public void setTicketIds(List<Long> ticketIds) {
        this.ticketIds = ticketIds;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }
}
