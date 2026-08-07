package com.pusula.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OpenBalanceReportService {

    private static final Color BRAND = new Color(2, 10, 85);
    private static final Color HEADER_BG = new Color(51, 65, 85);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color RED = new Color(185, 28, 28);
    private static final Color GREEN = new Color(22, 163, 74);
    private static final Color ORANGE = new Color(234, 88, 12);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final CompanyDebtRepository debtRepository;
    private final CompanyDebtPaymentRepository paymentRepository;
    private final CompanyDebtAdditionRepository additionRepository;
    private final CurrentAccountRepository currentAccountRepository;
    private final CompanyRepository companyRepository;
    private final ZoneId businessZone;

    private final BaseFont baseFont;
    private final Font titleFont;
    private final Font sectionFont;
    private final Font normalFont;
    private final Font smallFont;
    private final Font boldFont;
    private final Font headerFont;

    public OpenBalanceReportService(
            CompanyDebtRepository debtRepository,
            CompanyDebtPaymentRepository paymentRepository,
            CompanyDebtAdditionRepository additionRepository,
            CurrentAccountRepository currentAccountRepository,
            CompanyRepository companyRepository,
            @Value("${app.business.timezone:Europe/Istanbul}") String businessTimezone) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
        this.additionRepository = additionRepository;
        this.currentAccountRepository = currentAccountRepository;
        this.companyRepository = companyRepository;
        this.businessZone = ZoneId.of(businessTimezone);
        try {
            this.baseFont = loadFont();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF fontu yüklenemedi", exception);
        }
        this.titleFont = new Font(baseFont, 18, Font.BOLD, BRAND);
        this.sectionFont = new Font(baseFont, 12, Font.BOLD, BRAND);
        this.normalFont = new Font(baseFont, 9, Font.NORMAL, Color.BLACK);
        this.smallFont = new Font(baseFont, 8, Font.NORMAL, MUTED);
        this.boldFont = new Font(baseFont, 9, Font.BOLD, Color.BLACK);
        this.headerFont = new Font(baseFont, 8, Font.BOLD, Color.WHITE);
    }

    @Transactional(readOnly = true)
    public byte[] generateOpenCompanyDebtsPdf(Long companyId) {
        Company company = getCompany(companyId);
        List<CompanyDebt> debts = debtRepository.findByCompanyIdAndDeletedFalse(companyId).stream()
                .filter(debt -> debt.getRemainingAmount() != null && debt.getRemainingAmount().signum() > 0)
                .sorted(Comparator.comparing(CompanyDebt::getDueDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CompanyDebt::getDebtDate)
                        .thenComparing(CompanyDebt::getCreditorName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<Long> debtIds = debts.stream().map(CompanyDebt::getId).toList();
        Map<Long, List<CompanyDebtPayment>> paymentsByDebt = debtIds.isEmpty()
                ? Map.of()
                : paymentRepository.findByCompanyIdAndDebtIdInOrderByPaymentDateAscIdAsc(companyId, debtIds)
                        .stream().collect(Collectors.groupingBy(CompanyDebtPayment::getDebtId));
        Map<Long, List<CompanyDebtAddition>> additionsByDebt = debtIds.isEmpty()
                ? Map.of()
                : additionRepository.findByCompanyIdAndDebtIdInOrderByAdditionDateAscIdAsc(companyId, debtIds)
                        .stream().collect(Collectors.groupingBy(CompanyDebtAddition::getDebtId));

        return createDocument(PageSize.A4.rotate(), document -> {
            addReportHeader(document, company, "AÇIK İŞLETME BORÇLARI");
            BigDecimal totalDebt = debts.stream().map(CompanyDebt::getOriginalAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalRemaining = debts.stream().map(CompanyDebt::getRemainingAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPaid = debts.stream().map(this::paidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long overdueCount = debts.stream().filter(debt -> isOverdue(debt, LocalDate.now(businessZone))).count();
            addSummary(document, List.of(
                    new SummaryItem("Açık Borç", String.valueOf(debts.size()), BRAND),
                    new SummaryItem("Toplam Borç", money(totalDebt), RED),
                    new SummaryItem("Toplam Ödenen", money(totalPaid), GREEN),
                    new SummaryItem("Kalan Borç", money(totalRemaining), RED),
                    new SummaryItem("Vadesi Geçen", String.valueOf(overdueCount), overdueCount > 0 ? ORANGE : GREEN)));

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 2.1f, 1.15f, 2.3f, 1.05f, 1.05f, 1.25f, 1.25f, 1.05f, 1.05f });
            table.setHeaderRows(1);
            for (String header : List.of("Alacaklı", "Kategori", "Açıklama", "Başlangıç", "Vade",
                    "Toplam Borç", "Ödenen", "Kalan", "Durum")) {
                addHeaderCell(table, header);
            }
            for (CompanyDebt debt : debts) {
                BigDecimal paid = paidAmount(debt);
                addCell(table, debt.getCreditorName(), Element.ALIGN_LEFT, normalFont);
                addCell(table, categoryText(debt.getExpenseCategory()), Element.ALIGN_LEFT, normalFont);
                addCell(table, safe(debt.getDescription()), Element.ALIGN_LEFT, normalFont);
                addCell(table, formatDate(debt.getDebtDate()), Element.ALIGN_CENTER, normalFont);
                addCell(table, formatDate(debt.getDueDate()), Element.ALIGN_CENTER, normalFont);
                addCell(table, money(debt.getOriginalAmount()), Element.ALIGN_RIGHT, normalFont);
                addCell(table, money(paid), Element.ALIGN_RIGHT, new Font(baseFont, 9, Font.BOLD, GREEN));
                addCell(table, money(debt.getRemainingAmount()), Element.ALIGN_RIGHT,
                        new Font(baseFont, 9, Font.BOLD, RED));
                addCell(table, debtStatus(debt, LocalDate.now(businessZone)), Element.ALIGN_CENTER, normalFont);
            }
            document.add(table);

            Paragraph movementTitle = new Paragraph("BORÇ HAREKETLERİ", sectionFont);
            movementTitle.setSpacingBefore(18);
            movementTitle.setSpacingAfter(8);
            document.add(movementTitle);
            if (debts.isEmpty()) {
                document.add(new Paragraph("Açık veya kısmi borç bulunmuyor.", normalFont));
            }
            for (CompanyDebt debt : debts) {
                addDebtHistory(document, debt,
                        additionsByDebt.getOrDefault(debt.getId(), List.of()),
                        paymentsByDebt.getOrDefault(debt.getId(), List.of()));
            }
            addFooter(document);
        });
    }

    @Transactional(readOnly = true)
    public byte[] generateOpenCurrentAccountsPdf(Long companyId) {
        Company company = getCompany(companyId);
        List<CurrentAccount> accounts = currentAccountRepository.findByCompanyIdOrderByBalanceDesc(companyId).stream()
                .filter(account -> account.getBalance() != null && account.getBalance().signum() > 0)
                .toList();

        return createDocument(PageSize.A4, document -> {
            addReportHeader(document, company, "AÇIK CARİ HESAPLAR");
            BigDecimal total = accounts.stream().map(CurrentAccount::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            addSummary(document, List.of(
                    new SummaryItem("Açık Cari", String.valueOf(accounts.size()), BRAND),
                    new SummaryItem("Toplam Müşteri Alacağı", money(total), ORANGE)));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 2.6f, 1.6f, 1.5f, 1.5f });
            table.setHeaderRows(1);
            for (String header : List.of("Müşteri", "Telefon", "Güncel Bakiye", "Son Güncelleme")) {
                addHeaderCell(table, header);
            }
            for (CurrentAccount account : accounts) {
                Customer customer = account.getCustomer();
                addCell(table, customer != null ? safe(customer.getName()) : "Bilinmeyen Müşteri",
                        Element.ALIGN_LEFT, normalFont);
                addCell(table, customer != null ? safe(customer.getPhone()) : "", Element.ALIGN_LEFT, normalFont);
                addCell(table, money(account.getBalance()), Element.ALIGN_RIGHT,
                        new Font(baseFont, 9, Font.BOLD, ORANGE));
                addCell(table, account.getLastUpdated() != null ? account.getLastUpdated().format(DATE_TIME) : "-",
                        Element.ALIGN_CENTER, normalFont);
            }
            document.add(table);
            if (accounts.isEmpty()) {
                document.add(new Paragraph("Pozitif bakiyeli cari hesap bulunmuyor.", normalFont));
            }
            addFooter(document);
        });
    }

    private void addDebtHistory(Document document, CompanyDebt debt, List<CompanyDebtAddition> additions,
            List<CompanyDebtPayment> payments) throws DocumentException {
        BigDecimal trackedAdditions = additions.stream().map(CompanyDebtAddition::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialAmount = debt.getOriginalAmount().subtract(trackedAdditions).max(BigDecimal.ZERO);

        Paragraph heading = new Paragraph(debt.getCreditorName() + " — Kalan: " + money(debt.getRemainingAmount()),
                boldFont);
        heading.setSpacingBefore(10);
        heading.setSpacingAfter(4);
        document.add(heading);

        List<DebtMovement> movements = new ArrayList<>();
        movements.add(new DebtMovement(debt.getDebtDate(), 0, "Başlangıç",
                safe(debt.getDescription()), initialAmount, RED));
        additions.forEach(addition -> movements.add(new DebtMovement(addition.getAdditionDate(), 1, "İlave",
                safe(addition.getNotes()), addition.getAmount(), ORANGE)));
        payments.forEach(payment -> movements.add(new DebtMovement(payment.getPaymentDate(), 2, "Ödeme",
                safe(payment.getNotes()), payment.getAmount(), GREEN)));
        BigDecimal trackedPayments = payments.stream().map(CompanyDebtPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal untrackedReduction = paidAmount(debt).subtract(trackedPayments).max(BigDecimal.ZERO);
        if (untrackedReduction.signum() > 0) {
            movements.add(new DebtMovement(null, 3, "Önceki ödeme/düzeltme",
                    "Eski kayıtta işlem tarihi bulunmuyor", untrackedReduction, GREEN));
        }
        movements.sort(Comparator.comparing(DebtMovement::date,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DebtMovement::order));

        PdfPTable history = new PdfPTable(4);
        history.setWidthPercentage(100);
        history.setWidths(new float[] { 1.1f, 1.1f, 4.8f, 1.4f });
        for (String header : List.of("Tarih", "İşlem", "Açıklama", "Tutar")) {
            addHeaderCell(history, header);
        }
        for (DebtMovement movement : movements) {
            addCell(history, formatDate(movement.date()), Element.ALIGN_CENTER, normalFont);
            addCell(history, movement.type(), Element.ALIGN_CENTER,
                    new Font(baseFont, 9, Font.BOLD, movement.color()));
            addCell(history, movement.description(), Element.ALIGN_LEFT, normalFont);
            addCell(history, money(movement.amount()), Element.ALIGN_RIGHT,
                    new Font(baseFont, 9, Font.BOLD, movement.color()));
        }
        document.add(history);
    }

    private void addReportHeader(Document document, Company company, String title) throws DocumentException {
        Paragraph companyName = new Paragraph(safe(company.getName()), sectionFont);
        companyName.setAlignment(Element.ALIGN_CENTER);
        document.add(companyName);
        Paragraph reportTitle = new Paragraph(title, titleFont);
        reportTitle.setAlignment(Element.ALIGN_CENTER);
        reportTitle.setSpacingAfter(5);
        document.add(reportTitle);
        Paragraph date = new Paragraph("Rapor oluşturma: " + ZonedDateTime.now(businessZone).format(DATE_TIME), smallFont);
        date.setAlignment(Element.ALIGN_CENTER);
        date.setSpacingAfter(14);
        document.add(date);
    }

    private void addSummary(Document document, List<SummaryItem> items) throws DocumentException {
        PdfPTable summary = new PdfPTable(items.size());
        summary.setWidthPercentage(100);
        summary.setSpacingAfter(14);
        for (SummaryItem item : items) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(BORDER);
            cell.setPadding(9);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            Phrase phrase = new Phrase();
            phrase.add(new Chunk(item.value() + "\n", new Font(baseFont, 11, Font.BOLD, item.color())));
            phrase.add(new Chunk(item.label(), smallFont));
            cell.setPhrase(phrase);
            summary.addCell(cell);
        }
        document.add(summary);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(HEADER_BG);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, int alignment, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setBorderColor(BORDER);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private byte[] createDocument(Rectangle pageSize, DocumentWriter writer) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(pageSize, 28, 28, 30, 30);
            PdfWriter.getInstance(document, output);
            document.open();
            writer.write(document);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF raporu oluşturulamadı", exception);
        }
    }

    private BaseFont loadFont() throws Exception {
        try (InputStream input = OpenBalanceReportService.class.getResourceAsStream("/fonts/Inter.ttf")) {
            if (input != null) {
                return BaseFont.createFont("Inter.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                        input.readAllBytes(), null);
            }
        }
        return BaseFont.createFont(BaseFont.HELVETICA, "Cp1254", BaseFont.NOT_EMBEDDED);
    }

    private Company getCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Şirket bulunamadı"));
    }

    private boolean isOverdue(CompanyDebt debt, LocalDate today) {
        return debt.getDueDate() != null && debt.getDueDate().isBefore(today)
                && debt.getRemainingAmount() != null && debt.getRemainingAmount().signum() > 0;
    }

    private String debtStatus(CompanyDebt debt, LocalDate today) {
        if (isOverdue(debt, today)) return "Vadesi Geçti";
        return debt.getStatus() == CompanyDebt.DebtStatus.PARTIAL ? "Kısmi" : "Ödenmedi";
    }

    private BigDecimal paidAmount(CompanyDebt debt) {
        BigDecimal original = debt.getOriginalAmount() != null ? debt.getOriginalAmount() : BigDecimal.ZERO;
        BigDecimal remaining = debt.getRemainingAmount() != null ? debt.getRemainingAmount() : BigDecimal.ZERO;
        return original.subtract(remaining).max(BigDecimal.ZERO);
    }

    private String categoryText(ExpenseCategory category) {
        if (category == null) return "Diğer";
        return switch (category) {
            case RENT -> "Kira";
            case SALARY -> "Maaş";
            case BILLS -> "Faturalar";
            case FUEL -> "Yakıt";
            case FOOD -> "Yemek";
            case TAX -> "Vergi";
            case MATERIAL -> "Malzeme";
            case DEVICE_SALE -> "Cihaz Satışı";
            case OTHER -> "Diğer";
        };
    }

    private String money(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr-TR"));
        return formatter.format(value != null ? value : BigDecimal.ZERO);
    }

    private String formatDate(LocalDate value) {
        return value != null ? value.format(DATE) : "-";
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private void addFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph("Bu rapor yalnızca rapor tarihindeki açık bakiyeleri içerir.", smallFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(16);
        document.add(footer);
    }

    @FunctionalInterface
    private interface DocumentWriter {
        void write(Document document) throws Exception;
    }

    private record SummaryItem(String label, String value, Color color) {}

    private record DebtMovement(LocalDate date, int order, String type, String description,
            BigDecimal amount, Color color) {}
}
