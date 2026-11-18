package com.pusula.desktop.controller;

import com.pusula.desktop.dto.ServiceTicketDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTicketControllerDateFilterTest {
    @Test
    void onlyOpenPendingOrInProgressTicketsOlderThan48HoursAreOverdue() {
        ServiceTicketDTO ticket = new ServiceTicketDTO();
        ticket.setCreatedAt(LocalDateTime.now().minusHours(49));
        for (String status : java.util.List.of("PENDING", "IN_PROGRESS")) {
            ticket.setStatus(status);
            assertTrue(ServiceTicketController.isActive(ticket));
            assertTrue(ServiceTicketController.isOverdue(ticket));
        }
        ticket.setStatus("ASSIGNED");
        assertTrue(ServiceTicketController.isActive(ticket));
        assertFalse(ServiceTicketController.isOverdue(ticket));
        for (String status : java.util.List.of("CANCELLED", "COMPLETED")) {
            ticket.setStatus(status);
            assertFalse(ServiceTicketController.isActive(ticket));
            assertFalse(ServiceTicketController.isOverdue(ticket));
        }
        ticket.setStatus("PENDING");
        ticket.setCreatedAt(LocalDateTime.now().minusHours(47));
        assertFalse(ServiceTicketController.isOverdue(ticket));
        ticket.setCreatedAt(null);
        assertFalse(ServiceTicketController.isOverdue(ticket));
    }

    @Test
    void scheduledDateIsFilteredInclusively() {
        ServiceTicketDTO ticket = new ServiceTicketDTO();
        ticket.setScheduledDate(LocalDateTime.of(2026, 8, 26, 14, 30));

        assertTrue(ServiceTicketController.matchesDateRange(
                ticket, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 26)));
        assertTrue(ServiceTicketController.matchesDateRange(
                ticket, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        assertFalse(ServiceTicketController.matchesDateRange(
                ticket, LocalDate.of(2026, 8, 27), null));
        assertFalse(ServiceTicketController.matchesDateRange(
                ticket, null, LocalDate.of(2026, 8, 25)));
    }

    @Test
    void completedAndCreatedDatesAreUsedOnlyWhenScheduleIsMissing() {
        ServiceTicketDTO completed = new ServiceTicketDTO();
        completed.setCompletedAt(LocalDateTime.of(2026, 3, 9, 18, 0));
        assertTrue(ServiceTicketController.matchesDateRange(
                completed, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9)));

        ServiceTicketDTO created = new ServiceTicketDTO();
        created.setCreatedAt(LocalDateTime.of(2025, 4, 22, 9, 0));
        assertTrue(ServiceTicketController.matchesDateRange(
                created, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30)));
    }
}
