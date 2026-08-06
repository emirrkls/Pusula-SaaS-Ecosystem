package com.pusula.backend.service;

import com.pusula.backend.dto.MonthlySummaryDTO;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceUsedPart;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceStructuredPricingTest {
    @Mock ServiceTicketRepository ticketRepository;
    @Mock CustomerRepository customerRepository;
    @Mock CompanyRepository companyRepository;
    @Mock DailyClosingRepository dailyClosingRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock UserRepository userRepository;
    @Mock ServiceUsedPartRepository usedPartRepository;
    @Mock ProposalRepository proposalRepository;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(ticketRepository, customerRepository, companyRepository,
                dailyClosingRepository, expenseRepository, userRepository, usedPartRepository,
                proposalRepository);
    }

    @Test
    void monthlyProfitUsesInvoiceRevenueAndSnapshotPartCost() {
        LocalDate completionDate = LocalDate.now().withDayOfMonth(2);
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(100L);
        ticket.setCompanyId(10L);
        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setCompletedAt(completionDate.atTime(12, 0));
        ticket.setPartsTotal(new BigDecimal("500.00"));
        ticket.setLaborFee(BigDecimal.ZERO);
        ticket.setInvoiceTotal(new BigDecimal("500.00"));
        ticket.setCollectedAmount(new BigDecimal("500.00"));

        Inventory inventory = Inventory.builder()
                .id(20L)
                .companyId(10L)
                .partName("Parça")
                .quantity(1)
                .buyPrice(new BigDecimal("999.00"))
                .sellPrice(new BigDecimal("500.00"))
                .build();
        ServiceUsedPart usedPart = ServiceUsedPart.builder()
                .companyId(10L)
                .inventory(inventory)
                .quantityUsed(1)
                .buyingPriceSnapshot(new BigDecimal("400.00"))
                .sellingPriceSnapshot(new BigDecimal("500.00"))
                .build();

        when(ticketRepository.findAll()).thenReturn(List.of(ticket));
        when(expenseRepository.findByCompanyId(10L)).thenReturn(List.of());
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of(usedPart));

        List<MonthlySummaryDTO> summaries = service.getMonthlyArchives(10L);

        assertEquals(1, summaries.size());
        assertEquals(new BigDecimal("500.00"), summaries.get(0).getTotalIncome());
        assertEquals(new BigDecimal("400.00"), summaries.get(0).getTotalExpense());
        assertEquals(new BigDecimal("100.00"), summaries.get(0).getNetProfit());
    }
}
