package com.pusula.backend.service;

import com.pusula.backend.dto.MonthlySummaryDTO;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.PaymentMethod;
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
        assertEquals(new BigDecimal("500.00"), summaries.get(0).getTotalCollected());
        assertEquals(BigDecimal.ZERO, summaries.get(0).getCurrentAccountTransferred());
        assertEquals(new BigDecimal("400.00"), summaries.get(0).getTotalExpense());
        assertEquals(new BigDecimal("100.00"), summaries.get(0).getNetProfit());
    }

    @Test
    void monthlyProfitDoesNotRecognizeCurrentAccountCollectionAsSecondSale() {
        LocalDate completionDate = LocalDate.of(2025, 8, 5);
        LocalDate collectionDate = LocalDate.of(2025, 9, 3);

        ServiceTicket originalSale = new ServiceTicket();
        originalSale.setId(100L);
        originalSale.setCompanyId(10L);
        originalSale.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        originalSale.setCompletedAt(completionDate.atTime(12, 0));
        originalSale.setInvoiceTotal(new BigDecimal("135000.00"));
        originalSale.setCollectedAmount(BigDecimal.ZERO);
        originalSale.setPaymentMethod(PaymentMethod.CURRENT_ACCOUNT);

        ServiceTicket laterCollection = new ServiceTicket();
        laterCollection.setId(101L);
        laterCollection.setCompanyId(10L);
        laterCollection.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        laterCollection.setCompletedAt(collectionDate.atTime(13, 0));
        laterCollection.setCollectionDate(collectionDate);
        laterCollection.setCollectedAmount(new BigDecimal("135000.00"));
        laterCollection.setCurrentAccountPayment(true);

        when(ticketRepository.findAll()).thenReturn(List.of(originalSale, laterCollection));
        when(expenseRepository.findByCompanyId(10L)).thenReturn(List.of());
        when(usedPartRepository.findByServiceTicketId(100L)).thenReturn(List.of());

        List<MonthlySummaryDTO> summaries = service.getMonthlyArchives(10L);

        assertEquals(2, summaries.size());

        MonthlySummaryDTO collectionMonth = summaries.get(0);
        assertEquals("2025-09", collectionMonth.getPeriod());
        assertEquals(BigDecimal.ZERO, collectionMonth.getTotalIncome());
        assertEquals(BigDecimal.ZERO, collectionMonth.getCurrentAccountTransferred());
        assertEquals(new BigDecimal("135000.00"), collectionMonth.getTotalCollected());
        assertEquals(BigDecimal.ZERO, collectionMonth.getNetProfit());
        assertEquals(new BigDecimal("135000.00"), collectionMonth.getNetCash());

        MonthlySummaryDTO saleMonth = summaries.get(1);
        assertEquals("2025-08", saleMonth.getPeriod());
        assertEquals(new BigDecimal("135000.00"), saleMonth.getTotalIncome());
        assertEquals(new BigDecimal("135000.00"), saleMonth.getCurrentAccountTransferred());
        assertEquals(BigDecimal.ZERO, saleMonth.getTotalCollected());
        assertEquals(new BigDecimal("135000.00"), saleMonth.getNetProfit());
        assertEquals(BigDecimal.ZERO, saleMonth.getNetCash());
    }

    @Test
    void monthlyReportShowsPartialCashRemainderAsCurrentAccountTransfer() {
        LocalDate completionDate = LocalDate.of(2026, 7, 23);

        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(801L);
        ticket.setCompanyId(10L);
        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setCompletedAt(completionDate.atTime(12, 0));
        ticket.setCollectionDate(completionDate);
        ticket.setPaymentMethod(PaymentMethod.CASH);
        ticket.setPartsTotal(BigDecimal.ZERO);
        ticket.setLaborFee(new BigDecimal("185304.00"));
        ticket.setInvoiceTotal(new BigDecimal("185304.00"));
        ticket.setCollectedAmount(new BigDecimal("100000.00"));
        ticket.setOutstandingAmount(new BigDecimal("85304.00"));

        when(ticketRepository.findAll()).thenReturn(List.of(ticket));
        when(expenseRepository.findByCompanyId(10L)).thenReturn(List.of());
        when(usedPartRepository.findByServiceTicketId(801L)).thenReturn(List.of());

        List<MonthlySummaryDTO> summaries = service.getMonthlyArchives(10L);

        assertEquals(1, summaries.size());
        MonthlySummaryDTO summary = summaries.get(0);
        assertEquals(new BigDecimal("185304.00"), summary.getTotalIncome());
        assertEquals(new BigDecimal("100000.00"), summary.getTotalCollected());
        assertEquals(new BigDecimal("85304.00"), summary.getCurrentAccountTransferred());
        assertEquals(new BigDecimal("185304.00"), summary.getNetProfit());
        assertEquals(new BigDecimal("100000.00"), summary.getNetCash());
    }
}
