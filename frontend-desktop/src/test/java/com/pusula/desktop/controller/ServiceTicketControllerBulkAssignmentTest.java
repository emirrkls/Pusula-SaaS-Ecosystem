package com.pusula.desktop.controller;

import com.pusula.desktop.dto.ServiceTicketDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTicketControllerBulkAssignmentTest {
    @Test
    void onlyPendingUnassignedTicketsAreEligible() {
        ServiceTicketDTO pending = ticket("PENDING", null);
        assertTrue(ServiceTicketController.isPendingUnassigned(pending));
        assertFalse(ServiceTicketController.isPendingUnassigned(ticket("ASSIGNED", 7L)));
        assertFalse(ServiceTicketController.isPendingUnassigned(ticket("PENDING", 7L)));
        assertFalse(ServiceTicketController.isPendingUnassigned(ticket("COMPLETED", null)));
        assertFalse(ServiceTicketController.isPendingUnassigned(null));
    }

    private ServiceTicketDTO ticket(String status, Long technicianId) {
        ServiceTicketDTO ticket = new ServiceTicketDTO();
        ticket.setStatus(status);
        ticket.setAssignedTechnicianId(technicianId);
        return ticket;
    }
}
