package com.pusula.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportService {

    private final ServiceTicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final DailyClosingRepository dailyClosingRepository;
    private final ExpenseRepository expenseRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static BaseFont turkishBaseFont;
    private static Font HEADER_FONT;
    private static Font SECTION_FONT;
    private static Font NORMAL_FONT;
    private static Font SMALL_FONT;
    private static Font SMALL_BOLD_FONT;

    static {
        try {
            turkishBaseFont = BaseFont.createFont("/fonts/FreeSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            HEADER_FONT = new Font(turkishBaseFont, 18, Font.BOLD, Color.BLACK);
            SECTION_FONT = new Font(turkishBaseFont, 12, Font.BOLD, Color.BLACK);
            NORMAL_FONT = new Font(turkishBaseFont, 10, Font.NORMAL, Color.BLACK);
            SMALL_FONT = new Font(turkishBaseFont, 8, Font.NORMAL, Color.GRAY);
            SMALL_BOLD_FONT = new Font(turkishBaseFont, 8, Font.BOLD, Color.BLACK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Turkish font", e);
        }
    }

    public ReportService(ServiceTicketRepository ticketRepository,
            CustomerRepository customerRepository,
            CompanyRepository companyRepository,
            DailyClosingRepository dailyClosingRepository,
            ExpenseRepository expenseRepository) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.dailyClosingRepository = dailyClosingRepository;
        this.expenseRepository = expenseRepository;
    }

    /**
     * Generate Service Report PDF for a completed ticket
     */
    public byte[] generateServiceReport(Long ticketId) {
        ServiceTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

        Customer customer = customerRepository.findById(ticket.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Company company = companyRepository.findById(ticket.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title
            addTitle(document, "SERVİS RAPORU");
            document.add(new Paragraph(" ")); // Spacer

            // Company & Customer Info Side by Side
            addCompanyCustomerInfo(document, company, customer);
            document.add(new Paragraph(" "));

            // Ticket Info
            addTicketInfo(document, ticket);
            document.add(new Paragraph(" "));

            // Payment Table
            addPaymentTable(document, ticket);
            document.add(new Paragraph(" "));

            // Footer
            addFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Generate Proposal Form PDF (placeholder for future implementation)
     */
    public byte[] generateProposalForm(Long proposalId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();

            addTitle(document, "TEKLİF FORMU");
            document.add(new Paragraph("Proposal ID: " + proposalId, NORMAL_FONT));
            document.add(new Paragraph("Bu özellik yakında eklenecektir.", NORMAL_FONT));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate proposal PDF: " + e.getMessage(), e);
        }
    }

    // ========== HELPER METHODS ==========

    private void addTitle(Document document, String title) throws DocumentException {
        Paragraph titleParagraph = new Paragraph(title, HEADER_FONT);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(titleParagraph);
    }

    private void addCompanyCustomerInfo(Document document, Company company, Customer customer)
            throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        // Company Info Column
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.BOX);
        companyCell.setBackgroundColor(new Color(240, 240, 240));
        companyCell.setPadding(10);

        Paragraph companyTitle = new Paragraph("Şirket Bilgileri", SECTION_FONT);
        Paragraph companyName = new Paragraph(company.getName(), NORMAL_FONT);
        Paragraph companyPhone = new Paragraph("Tel: " + (company.getPhone() != null ? company.getPhone() : "N/A"),
                NORMAL_FONT);
        Paragraph companyAddress = new Paragraph(
                "Adres: " + (company.getAddress() != null ? company.getAddress() : "N/A"), SMALL_FONT);

        companyCell.addElement(companyTitle);
        companyCell.addElement(companyName);
        companyCell.addElement(companyPhone);
        companyCell.addElement(companyAddress);

        // Customer Info Column
        PdfPCell customerCell = new PdfPCell();
        customerCell.setBorder(Rectangle.BOX);
        customerCell.setBackgroundColor(new Color(240, 240, 240));
        customerCell.setPadding(10);

        Paragraph customerTitle = new Paragraph("Müşteri Bilgileri", SECTION_FONT);
        Paragraph customerName = new Paragraph(customer.getName(), NORMAL_FONT);
        Paragraph customerPhone = new Paragraph("Tel: " + customer.getPhone(), NORMAL_FONT);
        Paragraph customerAddress = new Paragraph(
                "Adres: " + (customer.getAddress() != null ? customer.getAddress() : "N/A"), SMALL_FONT);

        customerCell.addElement(customerTitle);
        customerCell.addElement(customerName);
        customerCell.addElement(customerPhone);
        customerCell.addElement(customerAddress);

        table.addCell(companyCell);
        table.addCell(customerCell);
        document.add(table);
    }

    private void addTicketInfo(Document document, ServiceTicket ticket) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        addInfoRow(table, "Açıklama", ticket.getDescription() != null ? ticket.getDescription() : "N/A");
        addInfoRow(table, "Notlar", ticket.getNotes() != null ? ticket.getNotes() : "Yok");
        addInfoRow(table, "Durum", ticket.getStatus() != null ? ticket.getStatus().toString() : "N/A");
        addInfoRow(table, "Tarih", ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DATE_FORMAT) : "N/A");
        addInfoRow(table, "Planlanan Tarih",
                ticket.getScheduledDate() != null ? ticket.getScheduledDate().format(DATE_FORMAT) : "Belirlenmedi");

        document.add(table);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label + ":", SECTION_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", NORMAL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addPaymentTable(Document document, ServiceTicket ticket) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("Ücret Bilgileri", SECTION_FONT);
        document.add(sectionTitle);
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 3f, 1f });

        // Header Row
        Color headerColor = new Color(52, 152, 219); // Blue
        addTableHeader(table, "Açıklama", headerColor);
        addTableHeader(table, "Tutar", headerColor);

        // Service fee row
        BigDecimal amount = ticket.getCollectedAmount() != null ? ticket.getCollectedAmount() : BigDecimal.ZERO;
        addSimpleRow(table, "Servis Ücreti", String.format("%.2f ₺", amount));

        // Total Row
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOPLAM", SECTION_FONT));
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalLabelCell.setBackgroundColor(new Color(240, 240, 240));
        totalLabelCell.setPadding(8);

        PdfPCell totalValueCell = new PdfPCell(new Phrase(String.format("%.2f ₺", amount), SECTION_FONT));
        totalValueCell.setBackgroundColor(new Color(240, 240, 240));
        totalValueCell.setPadding(8);

        table.addCell(totalLabelCell);
        table.addCell(totalValueCell);

        document.add(table);
    }

    private void addTableHeader(PdfPTable table, String text, Color bgColor) {
        PdfPCell cell = new PdfPCell(
                new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8);
        table.addCell(cell);
    }

    private void addSimpleRow(PdfPTable table, String label, String value) {
        table.addCell(createCell(label, Element.ALIGN_LEFT));
        table.addCell(createCell(value, Element.ALIGN_RIGHT));
    }

    private PdfPCell createCell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, NORMAL_FONT));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        return cell;
    }

    private void addFooter(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        Paragraph signatureLabel = new Paragraph("Müşteri İmzası: _____________________", NORMAL_FONT);
        document.add(signatureLabel);

        document.add(new Paragraph(" "));

        Paragraph terms = new Paragraph(
                "Koşullar: Yapılan işler için 30 gün garanti verilmektedir. " +
                        "Garanti süresi içerisinde aynı arıza için ücretsiz servis hizmeti sunulmaktadır.",
                SMALL_FONT);
        terms.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(terms);
    }
    // ========== MONTHLY FINANCIAL REPORTS ==========

    public List<com.pusula.backend.dto.MonthlySummaryDTO> getMonthlyArchives(Long companyId) {
        List<DailyClosing> closings = dailyClosingRepository.findAll().stream()
                .filter(dc -> dc.getCompanyId().equals(companyId))
                .collect(java.util.stream.Collectors.toList());

        Map<java.time.YearMonth, List<DailyClosing>> monthlyGroups = closings.stream()
                .collect(java.util.stream.Collectors.groupingBy(dc -> java.time.YearMonth.from(dc.getDate())));

        List<com.pusula.backend.dto.MonthlySummaryDTO> summaries = new ArrayList<>();
        DateTimeFormatter turkishFormat = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("tr", "TR"));

        for (Map.Entry<java.time.YearMonth, List<DailyClosing>> entry : monthlyGroups.entrySet()) {
            java.time.YearMonth month = entry.getKey();
            List<DailyClosing> monthClosings = entry.getValue();

            BigDecimal totalIncome = monthClosings.stream()
                    .map(DailyClosing::getTotalIncome)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalExpense = monthClosings.stream()
                    .map(DailyClosing::getTotalExpense)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            summaries.add(com.pusula.backend.dto.MonthlySummaryDTO.builder()
                    .period(month.toString())
                    .displayPeriod(month.atDay(1).format(turkishFormat))
                    .totalIncome(totalIncome)
                    .totalExpense(totalExpense)
                    .netProfit(totalIncome.subtract(totalExpense))
                    .build());
        }

        summaries.sort(Comparator.comparing(com.pusula.backend.dto.MonthlySummaryDTO::getPeriod).reversed());
        return summaries;
    }

    public byte[] generateMonthlyPDF(java.time.YearMonth period, Long companyId) throws DocumentException {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        DateTimeFormatter turkishFormat = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("tr", "TR"));
        String displayPeriod = period.atDay(1).format(turkishFormat);

        Paragraph title = new Paragraph("AYLIK FİNANSAL RAPOR", HEADER_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph periodPara = new Paragraph(displayPeriod.toUpperCase(), SECTION_FONT);
        periodPara.setAlignment(Element.ALIGN_CENTER);
        periodPara.setSpacingAfter(20);
        document.add(periodPara);

        java.time.LocalDate startDate = period.atDay(1);
        java.time.LocalDate endDate = period.atEndOfMonth();

        List<DailyClosing> closings = dailyClosingRepository.findAll().stream()
                .filter(dc -> dc.getCompanyId().equals(companyId))
                .filter(dc -> !dc.getDate().isBefore(startDate) && !dc.getDate().isAfter(endDate))
                .collect(java.util.stream.Collectors.toList());

        BigDecimal totalIncome = closings.stream().map(DailyClosing::getTotalIncome).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal totalExpense = closings.stream().map(DailyClosing::getTotalExpense).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal netProfit = totalIncome.subtract(totalExpense);

        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingBefore(10);
        summaryTable.setSpacingAfter(20);

        addFinancialRow(summaryTable, "Toplam Gelir:", String.format("%.2f ₺", totalIncome));
        addFinancialRow(summaryTable, "Toplam Gider:", String.format("%.2f ₺", totalExpense));
        addFinancialRow(summaryTable, "Net Kâr:", String.format("%.2f ₺", netProfit));
        document.add(summaryTable);

        // DAILY LEDGER
        Paragraph ledgerTitle = new Paragraph("Günlük Maliyet Defteri", SECTION_FONT);
        ledgerTitle.setSpacingBefore(20);
        document.add(ledgerTitle);

        List<ServiceTicket> completedTickets = ticketRepository.findAll().stream()
                .filter(st -> st.getCompanyId().equals(companyId))
                .filter(st -> st.getStatus() != null && st.getStatus().equals(ServiceTicket.TicketStatus.COMPLETED))
                .filter(st -> st.getUpdatedAt() != null &&
                        !st.getUpdatedAt().toLocalDate().isBefore(startDate) &&
                        !st.getUpdatedAt().toLocalDate().isAfter(endDate))
                .collect(java.util.stream.Collectors.toList());

        List<Expense> expenses = expenseRepository.findByCompanyIdAndDateBetween(companyId, startDate, endDate);

        Map<java.time.LocalDate, List<ServiceTicket>> ticketsByDate = completedTickets.stream()
                .collect(java.util.stream.Collectors.groupingBy(st -> st.getUpdatedAt().toLocalDate()));

        Map<java.time.LocalDate, List<Expense>> expensesByDate = expenses.stream()
                .collect(java.util.stream.Collectors.groupingBy(Expense::getDate));

        java.util.Set<java.time.LocalDate> activeDates = new java.util.TreeSet<>();
        activeDates.addAll(ticketsByDate.keySet());
        activeDates.addAll(expensesByDate.keySet());

        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE", new Locale("tr", "TR"));
        java.text.DecimalFormat currencyFormat = new java.text.DecimalFormat("#,##0.00");

        for (java.time.LocalDate date : activeDates) {
            Paragraph dateHeader = new Paragraph(date.format(dayFormatter).toUpperCase(), SECTION_FONT);
            dateHeader.setSpacingBefore(15);
            dateHeader.setSpacingAfter(5);
            document.add(dateHeader);

            BigDecimal dailyIncome = BigDecimal.ZERO;
            BigDecimal dailyExpense = BigDecimal.ZERO;

            List<ServiceTicket> dayTickets = ticketsByDate.getOrDefault(date, java.util.Collections.emptyList());
            for (ServiceTicket ticket : dayTickets) {
                String customerName = ticket.getCustomerId() != null
                        ? customerRepository.findById(ticket.getCustomerId()).map(Customer::getName)
                                .orElse("Bilinmiyor")
                        : "Bilinmiyor";
                String serviceDesc = ticket.getDescription() != null && !ticket.getDescription().isEmpty()
                        ? ticket.getDescription()
                        : "Servis Hizmeti";
                BigDecimal amount = ticket.getCollectedAmount() != null ? ticket.getCollectedAmount() : BigDecimal.ZERO;
                dailyIncome = dailyIncome.add(amount);

                String line = String.format("   Gelir: %s - %s → %s ₺", customerName, serviceDesc,
                        currencyFormat.format(amount));
                Font greenFont = new Font(turkishBaseFont, 10, Font.NORMAL, new Color(0, 128, 0));
                Paragraph incomeLine = new Paragraph(line, greenFont);
                incomeLine.setIndentationLeft(10);
                document.add(incomeLine);
            }

            List<Expense> dayExpenses = expensesByDate.getOrDefault(date, java.util.Collections.emptyList());
            for (Expense expense : dayExpenses) {
                String categoryName = getCategoryNameTurkish(expense.getCategory().name());
                dailyExpense = dailyExpense.add(expense.getAmount());

                String line = String.format("   Gider: %s - %s → %s ₺", categoryName, expense.getDescription(),
                        currencyFormat.format(expense.getAmount()));
                Font redFont = new Font(turkishBaseFont, 10, Font.NORMAL, new Color(192, 0, 0));
                Paragraph expenseLine = new Paragraph(line, redFont);
                expenseLine.setIndentationLeft(10);
                document.add(expenseLine);
            }

            BigDecimal dailyNet = dailyIncome.subtract(dailyExpense);
            String netLabel = dailyNet.compareTo(BigDecimal.ZERO) >= 0 ? "Günlük Net Kâr" : "Günlük Net Zarar";

            PdfPTable netTable = new PdfPTable(2);
            netTable.setWidthPercentage(100);
            netTable.setSpacingBefore(5);
            netTable.setSpacingAfter(5);
            netTable.setWidths(new float[] { 3f, 1f });

            PdfPCell labelCell = new PdfPCell(new Paragraph(netLabel + ":", SMALL_BOLD_FONT));
            labelCell.setBorder(Rectangle.TOP);
            labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            labelCell.setBackgroundColor(new Color(240, 248, 255));
            labelCell.setPadding(5);

            String netSign = dailyNet.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            PdfPCell amountCell = new PdfPCell(
                    new Paragraph(netSign + currencyFormat.format(dailyNet) + " ₺", SMALL_BOLD_FONT));
            amountCell.setBorder(Rectangle.TOP);
            amountCell.setBackgroundColor(new Color(240, 248, 255));
            amountCell.setPadding(5);
            amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

            netTable.addCell(labelCell);
            netTable.addCell(amountCell);
            document.add(netTable);
        }

        // MONTHLY NET TOTAL - Grand Total
        Paragraph separator = new Paragraph(" ");
        separator.setSpacingBefore(20);
        document.add(separator);

        // Horizontal line
        PdfPTable separatorLine = new PdfPTable(1);
        separatorLine.setWidthPercentage(100);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorder(Rectangle.TOP);
        lineCell.setBorderWidthTop(2f);
        lineCell.setFixedHeight(1f);
        separatorLine.addCell(lineCell);
        document.add(separatorLine);

        // Monthly net total
        String monthLabel = displayPeriod.toUpperCase();
        String netSign = netProfit.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        String totalText = String.format("GENEL TOPLAM (%s): %s%s ₺",
                monthLabel,
                netSign,
                currencyFormat.format(netProfit));

        Color totalColor = netProfit.compareTo(BigDecimal.ZERO) >= 0 ? new Color(0, 128, 0) : new Color(192, 0, 0);

        Font totalFont = new Font(turkishBaseFont, 16, Font.BOLD, totalColor);
        Paragraph totalPara = new Paragraph(totalText, totalFont);
        totalPara.setAlignment(Element.ALIGN_RIGHT);
        totalPara.setSpacingBefore(10);
        totalPara.setSpacingAfter(20);
        document.add(totalPara);

        // Footer
        Paragraph footer = new Paragraph("Rapor Oluşturma Tarihi: "
                + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), SMALL_FONT);
        footer.setAlignment(Element.ALIGN_RIGHT);
        footer.setSpacingBefore(30);
        document.add(footer);

        document.close();
        return baos.toByteArray();
    }

    private void addFinancialRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, SECTION_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, NORMAL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String getCategoryNameTurkish(String category) {
        Map<String, String> categoryNames = Map.of(
                "RENT", "Kira",
                "SALARY", "Maaş",
                "BILLS", "Faturalar",
                "FUEL", "Yakıt",
                "FOOD", "Yemek",
                "TAX", "Vergi",
                "MATERIAL", "Malzeme",
                "OTHER", "Diğer");
        return categoryNames.getOrDefault(category, category);
    }
}
