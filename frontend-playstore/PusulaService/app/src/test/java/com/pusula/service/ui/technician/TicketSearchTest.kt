package com.pusula.service.ui.technician

import com.pusula.service.data.model.FieldTicketDTO
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketSearchTest {
    @Test
    fun searchesVisibleTicketFieldsWithinSelectedCategory() {
        val ticket = FieldTicketDTO(
            id = 75,
            customerName = "Şaban Şen",
            customerPhone = "0555 111 22 33",
            description = "Klima bakım sök tak",
            notes = "Dış ünite kontrol edilecek",
            assignedTechnicianName = "Uğur Mert Yıldırım",
            scheduledDate = "2025-05-26T14:00:00"
        )

        assertTrue(ticketMatchesSearch(ticket, "75"))
        assertTrue(ticketMatchesSearch(ticket, "şaban"))
        assertTrue(ticketMatchesSearch(ticket, "BAKIM"))
        assertTrue(ticketMatchesSearch(ticket, "0555"))
        assertTrue(ticketMatchesSearch(ticket, "uğur"))
        assertTrue(ticketMatchesSearch(ticket, "2025-05-26"))
        assertTrue(ticketMatchesSearch(ticket, "  "))
        assertFalse(ticketMatchesSearch(ticket, "gaz şarjı"))
    }

    @Test
    fun sortsPastCategoriesNewestFirstAndUpcomingCallsSoonestFirst() {
        val older = FieldTicketDTO(id = 1, scheduledDate = "2025-05-26T10:00:00")
        val newer = FieldTicketDTO(id = 2, scheduledDate = "2025-06-03T14:00:00")

        assertTrue(sortTicketsForCategory(listOf(older, newer), "Kapanan").map { it.id } == listOf(2L, 1L))
        assertTrue(sortTicketsForCategory(listOf(newer, older), "İleri Tarihli").map { it.id } == listOf(1L, 2L))
    }
}
