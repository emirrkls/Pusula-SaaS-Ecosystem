package com.pusula.backend.event;

public record TicketAssignedEvent(Long companyId, Long technicianId, Long ticketId) {
}
