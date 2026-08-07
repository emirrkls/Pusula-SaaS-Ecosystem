package com.pusula.backend.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.pusula.backend.dto.MonthlySummaryDTO;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ExpenseTreatment;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void monthlyReportSeparatesProfitabilityFromCashMovements() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        ServiceTicket sale = new ServiceTicket();
        sale.setId(900L);
        sale.setCompanyId(10L);
        sale.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        sale.setCompletedAt(date.atTime(12, 0));
        sale.setCollectionDate(date);
        sale.setInvoiceTotal(new BigDecimal("10000.00"));
        sale.setCollectedAmount(new BigDecimal("7000.00"));
        sale.setOutstandingAmount(new BigDecimal("3000.00"));
        sale.setPaymentMethod(PaymentMethod.CASH);

        Expense serviceExpense = Expense.builder()
                .companyId(10L)
                .category(ExpenseCategory.MATERIAL)
                .amount(new BigDecimal("1000.00"))
                .date(date)
                .financialTreatment(ExpenseTreatment.SERVICE_DIRECT_EXPENSE)
                .build();
        Expense operatingExpense = Expense.builder()
                .companyId(10L)
                .category(ExpenseCategory.RENT)
                .amount(new BigDecimal("2000.00"))
                .date(date)
                .financialTreatment(ExpenseTreatment.OPERATING_EXPENSE)
                .build();
        Expense debtPrincipalPayment = Expense.builder()
                .companyId(10L)
                .category(ExpenseCategory.MATERIAL)
                .amount(new BigDecimal("4000.00"))
                .date(date)
                .financialTreatment(ExpenseTreatment.CASH_ONLY)
                .build();

        when(ticketRepository.findAll()).thenReturn(List.of(sale));
        when(expenseRepository.findByCompanyId(10L))
                .thenReturn(List.of(serviceExpense, operatingExpense, debtPrincipalPayment));
        when(usedPartRepository.findByServiceTicketId(900L)).thenReturn(List.of());

        MonthlySummaryDTO summary = service.getMonthlyArchives(10L).get(0);

        assertEquals(new BigDecimal("3000.00"), summary.getCurrentAccountTransferred());
        assertEquals(new BigDecimal("7000.00"), summary.getCashCardCollections());
        assertEquals(BigDecimal.ZERO, summary.getCurrentAccountCollections());
        assertEquals(new BigDecimal("1000.00"), summary.getServiceDirectCost());
        assertEquals(new BigDecimal("2000.00"), summary.getOtherOperatingExpenses());
        assertEquals(new BigDecimal("3000.00"), summary.getTotalProfitExpenses());
        assertEquals(new BigDecimal("7000.00"), summary.getNetProfit());
        assertEquals(new BigDecimal("1000.00"), summary.getServiceCashExpenses());
        assertEquals(new BigDecimal("6000.00"), summary.getOtherCashExpenses());
        assertEquals(new BigDecimal("7000.00"), summary.getTotalCashExpenses());
        assertEquals(BigDecimal.ZERO.setScale(2), summary.getNetCash());
    }

    @Test
    void monthlyPdfListsSalesAndProfitExpensesWithoutCashOnlyMovements() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 1);
        ServiceTicket sale = new ServiceTicket();
        sale.setId(787L);
        sale.setCompanyId(10L);
        sale.setCustomerId(50L);
        sale.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        sale.setCompletedAt(date.atTime(12, 0));
        sale.setCollectionDate(date);
        sale.setDescription("2 Klima Bakım");
        sale.setInvoiceTotal(new BigDecimal("1800.00"));
        sale.setCollectedAmount(new BigDecimal("1800.00"));
        sale.setPaymentMethod(PaymentMethod.CASH);

        Expense rent = Expense.builder()
                .companyId(10L)
                .category(ExpenseCategory.RENT)
                .description("Ağustos Kirası")
                .amount(new BigDecimal("28000.00"))
                .date(date)
                .financialTreatment(ExpenseTreatment.OPERATING_EXPENSE)
                .build();
        Expense debtPayment = Expense.builder()
                .companyId(10L)
                .category(ExpenseCategory.MATERIAL)
                .description("Borç Ödemesi: Tedarikçi")
                .amount(new BigDecimal("36000.00"))
                .date(date)
                .financialTreatment(ExpenseTreatment.CASH_ONLY)
                .build();

        when(ticketRepository.findAll()).thenReturn(List.of(sale));
        when(expenseRepository.findByCompanyId(10L)).thenReturn(List.of(rent, debtPayment));
        when(expenseRepository.findByCompanyIdAndDateBetween(10L, date, date.withDayOfMonth(31)))
                .thenReturn(List.of(rent, debtPayment));
        when(customerRepository.findById(50L)).thenReturn(Optional.of(Customer.builder()
                .id(50L)
                .companyId(10L)
                .name("Eren Taştan")
                .build()));
        when(usedPartRepository.findByServiceTicketId(787L)).thenReturn(List.of());

        byte[] pdf = service.generateMonthlyPDF(YearMonth.of(2026, 8), 10L);
        PdfReader reader = new PdfReader(pdf);
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page));
        }
        reader.close();

        String reportText = text.toString();
        assertTrue(reportText.contains("Eren Taştan"));
        assertTrue(reportText.contains("2 Klima Bakım"));
        assertTrue(reportText.contains("Ağustos Kirası"));
        assertFalse(reportText.contains("Borç Ödemesi"));
        assertFalse(reportText.contains("Net Nakit"));
    }
}
