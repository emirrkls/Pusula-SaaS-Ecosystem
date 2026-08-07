package com.pusula.backend.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenBalanceReportServiceTest {

    @Mock CompanyDebtRepository debtRepository;
    @Mock CompanyDebtPaymentRepository paymentRepository;
    @Mock CompanyDebtAdditionRepository additionRepository;
    @Mock CurrentAccountRepository currentAccountRepository;
    @Mock CompanyRepository companyRepository;

    private OpenBalanceReportService service;

    @BeforeEach
    void setUp() {
        service = new OpenBalanceReportService(debtRepository, paymentRepository, additionRepository,
                currentAccountRepository, companyRepository, "Europe/Istanbul");
        when(companyRepository.findById(7L)).thenReturn(Optional.of(Company.builder()
                .id(7L).name("Pusula İklimlendirme").subscriptionStatus("ACTIVE").build()));
    }

    @Test
    void debtPdfContainsOnlyOpenDebtsAndTheirDatedMovements() throws Exception {
        CompanyDebt open = debt(20L, "ZT Soğutma", "70000.00", CompanyDebt.DebtStatus.PARTIAL);
        CompanyDebt paid = debt(21L, "Kapalı Tedarikçi", "0.00", CompanyDebt.DebtStatus.PAID);
        when(debtRepository.findByCompanyIdAndDeletedFalse(7L)).thenReturn(List.of(open, paid));
        when(additionRepository.findByCompanyIdAndDebtIdInOrderByAdditionDateAscIdAsc(7L, List.of(20L)))
                .thenReturn(List.of(CompanyDebtAddition.builder()
                        .id(1L).companyId(7L).debtId(20L).amount(new BigDecimal("5000.00"))
                        .additionDate(LocalDate.of(2026, 6, 3)).notes("Yeni sipariş").build()));
        when(paymentRepository.findByCompanyIdAndDebtIdInOrderByPaymentDateAscIdAsc(7L, List.of(20L)))
                .thenReturn(List.of(CompanyDebtPayment.builder()
                        .id(2L).companyId(7L).debtId(20L).expenseId(80L)
                        .amount(new BigDecimal("30000.00")).paymentDate(LocalDate.of(2026, 6, 10))
                        .expenseCategory(ExpenseCategory.MATERIAL).notes("Ara ödeme").build()));

        byte[] pdf = service.generateOpenCompanyDebtsPdf(7L);
        writeSample("open-debts-sample.pdf", pdf);
        String text = pdfText(pdf);

        assertTrue(text.contains("ZT Soğutma"));
        assertTrue(text.contains("Yeni sipariş"));
        assertTrue(text.contains("Ara ödeme"));
        assertTrue(text.contains("03.06.2026"));
        assertTrue(text.contains("10.06.2026"));
        assertFalse(text.contains("Kapalı Tedarikçi"));
    }

    @Test
    void currentAccountPdfContainsOnlyPositiveBalances() throws Exception {
        Customer customer = Customer.builder().id(30L).companyId(7L)
                .name("Murat Budak").phone("05000000000").build();
        Customer closedCustomer = Customer.builder().id(31L).companyId(7L)
                .name("Kapalı Cari").phone("1111111111").build();
        when(currentAccountRepository.findByCompanyIdOrderByBalanceDesc(7L)).thenReturn(List.of(
                CurrentAccount.builder().id(1L).companyId(7L).customer(customer)
                        .balance(new BigDecimal("3500.00")).build(),
                CurrentAccount.builder().id(2L).companyId(7L).customer(closedCustomer)
                        .balance(BigDecimal.ZERO).build()));

        byte[] pdf = service.generateOpenCurrentAccountsPdf(7L);
        writeSample("open-current-accounts-sample.pdf", pdf);
        String text = pdfText(pdf);

        assertTrue(text.contains("Murat Budak"));
        assertTrue(text.contains("05000000000"));
        assertFalse(text.contains("Kapalı Cari"));
    }

    private CompanyDebt debt(Long id, String creditor, String remaining, CompanyDebt.DebtStatus status) {
        return CompanyDebt.builder()
                .id(id).companyId(7L).creditorName(creditor).description("Malzeme alımı")
                .originalAmount(new BigDecimal("100000.00")).remainingAmount(new BigDecimal(remaining))
                .expenseCategory(ExpenseCategory.MATERIAL).debtDate(LocalDate.of(2026, 5, 1))
                .dueDate(LocalDate.of(2026, 7, 1)).status(status).deleted(false).build();
    }

    private String pdfText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page));
        }
        reader.close();
        return text.toString();
    }

    private void writeSample(String name, byte[] pdf) throws Exception {
        if (Boolean.getBoolean("writeReportSamples")) {
            Path directory = Path.of("target", "report-samples");
            Files.createDirectories(directory);
            Files.write(directory.resolve(name), pdf);
        }
    }
}
