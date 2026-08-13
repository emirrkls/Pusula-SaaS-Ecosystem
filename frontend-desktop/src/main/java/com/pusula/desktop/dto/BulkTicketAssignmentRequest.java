package com.pusula.desktop.dto;

import java.util.List;

public record BulkTicketAssignmentRequest(List<Long> ticketIds, Long technicianId) {
}
