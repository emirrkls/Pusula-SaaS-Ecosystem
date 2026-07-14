package com.pusula.backend.service;

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

        ReportService service = new ReportService(ticketRepository, customerRepository, companyRepository,
                dailyClosingRepository, expenseRepository, userRepository, usedPartRepository, proposalRepository);

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
}
