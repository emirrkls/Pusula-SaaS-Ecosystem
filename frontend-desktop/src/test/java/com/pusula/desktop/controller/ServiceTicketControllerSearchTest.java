package com.pusula.desktop.controller;

import com.pusula.desktop.dto.ServiceTicketDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTicketControllerSearchTest {
    @Test
    void searchesTicketFieldsWithoutRequiringAutocompleteSelection() {
        ServiceTicketDTO ticket = new ServiceTicketDTO();
        ticket.setId(75L);
        ticket.setCustomerName("Şaban Şen");
        ticket.setDescription("Klima bakım sök tak");
        ticket.setNotes("Dış ünite kontrol edilecek");
        ticket.setAssignedTechnicianName("Uğur Mert Yıldırım");
        ticket.setScheduledDate(LocalDateTime.of(2025, 5, 26, 14, 0));

        assertTrue(ServiceTicketController.matchesSearch(ticket, "75"));
        assertTrue(ServiceTicketController.matchesSearch(ticket, "şaban"));
        assertTrue(ServiceTicketController.matchesSearch(ticket, "BAKIM"));
        assertTrue(ServiceTicketController.matchesSearch(ticket, "uğur"));
        assertTrue(ServiceTicketController.matchesSearch(ticket, "2025-05-26"));
        assertTrue(ServiceTicketController.matchesSearch(ticket, "  "));
        assertFalse(ServiceTicketController.matchesSearch(ticket, "gaz şarjı"));
    }
}
