package com.pusula.backend.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceUsedPart;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceMissingInventoryTest {

    @Test
    void servicePdfUsesFallbackForHistoricalPartWithoutInventory() {
        ServiceTicketRepository ticketRepository = mock(ServiceTicketRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        DailyClosingRepository dailyClosingRepository = mock(DailyClosingRepository.class);
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ServiceUsedPartRepository usedPartRepository = mock(ServiceUsedPartRepository.class);
        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        ServiceTicketNoteRepository serviceTicketNoteRepository = mock(ServiceTicketNoteRepository.class);

        ReportService service = new ReportService(ticketRepository, customerRepository, companyRepository,
                dailyClosingRepository, expenseRepository, userRepository, usedPartRepository, proposalRepository,
                serviceTicketNoteRepository);

        ServiceTicket ticket = ServiceTicket.builder()
                .id(100L)
                .companyId(10L)
                .customerId(200L)
                .status(ServiceTicket.TicketStatus.COMPLETED)
                .collectedAmount(new BigDecimal("500.00"))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        ticket.setUpdatedAt(LocalDateTime.now());

        Customer customer = Customer.builder().id(200L).companyId(10L).name("Customer").build();
        Company company = Company.builder().id(10L).name("Company").subscriptionStatus("ACTIVE").build();
        ServiceUsedPart missingInventoryPart = ServiceUsedPart.builder()
                .id(300L)
                .companyId(10L)
                .serviceTicket(ticket)
                .inventory(null)
                .quantityUsed(2)
                .sellingPriceSnapshot(new BigDecimal("125.50"))
                .build();

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(missingInventoryPart));

        byte[] pdf = service.generateServiceReport(100L);

        assertTrue(pdf.length > 100);
    }

    @Test
    void servicePdfUsesScheduledDateAsApplicationDateForHistoricalTicket() throws Exception {
        ServiceTicketRepository ticketRepository = mock(ServiceTicketRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        DailyClosingRepository dailyClosingRepository = mock(DailyClosingRepository.class);
        ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ServiceUsedPartRepository usedPartRepository = mock(ServiceUsedPartRepository.class);
        ProposalRepository proposalRepository = mock(ProposalRepository.class);
        ServiceTicketNoteRepository serviceTicketNoteRepository = mock(ServiceTicketNoteRepository.class);

        ReportService service = new ReportService(ticketRepository, customerRepository, companyRepository,
                dailyClosingRepository, expenseRepository, userRepository, usedPartRepository, proposalRepository,
                serviceTicketNoteRepository);

        LocalDateTime historicalDate = LocalDateTime.of(2025, 4, 17, 9, 0);
        ServiceTicket ticket = ServiceTicket.builder()
                .id(101L)
                .companyId(10L)
                .customerId(200L)
                .status(ServiceTicket.TicketStatus.COMPLETED)
                .scheduledDate(historicalDate)
                .createdAt(LocalDateTime.of(2026, 8, 13, 19, 0))
                .collectedAmount(BigDecimal.ZERO)
                .build();
        ticket.setCompletedAt(historicalDate.plusHours(4));

        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(customerRepository.findById(200L)).thenReturn(Optional.of(
                Customer.builder().id(200L).companyId(10L).name("Customer").build()));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(
                Company.builder().id(10L).name("Company").subscriptionStatus("ACTIVE").build()));
        when(usedPartRepository.findByServiceTicketId(101L)).thenReturn(List.of());

        byte[] pdf = service.generateServiceReport(101L);
        PdfReader reader = new PdfReader(pdf);
        String text;
        try {
            text = new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }

        assertTrue(text.contains("Müracaat Tarihi: 17/04/2025"));
        assertFalse(text.contains("Müracaat Tarihi: 13/08/2026"));
    }
}
