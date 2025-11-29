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
        private final UserRepository userRepository;

        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        private static BaseFont turkishBaseFont;
        private static Font HEADER_FONT;
        private static Font SECTION_FONT;
        private static Font NORMAL_FONT;
        private static Font SMALL_FONT;
        private static Font SMALL_BOLD_FONT;

        static {
                try {
                        turkishBaseFont = BaseFont.createFont("/fonts/FreeSans.ttf", BaseFont.IDENTITY_H,
                                        BaseFont.EMBEDDED);
                        // Brand color: #020a55 (RGB: 2, 10, 85)
                        Color brandColor = new Color(2, 10, 85);
                        HEADER_FONT = new Font(turkishBaseFont, 18, Font.BOLD, brandColor);
                        SECTION_FONT = new Font(turkishBaseFont, 12, Font.BOLD, brandColor);
                        NORMAL_FONT = new Font(turkishBaseFont, 10, Font.NORMAL, Color.BLACK);
                        SMALL_FONT = new Font(turkishBaseFont, 8, Font.NORMAL, Color.GRAY);
                        SMALL_BOLD_FONT = new Font(turkishBaseFont, 8, Font.BOLD, Color.BLACK);
                } catch (Exception e) {
                        throw new RuntimeException("Failed to load Turkish font", e);
                }
        }

        private final ServiceUsedPartRepository serviceUsedPartRepository;

        public ReportService(ServiceTicketRepository ticketRepository,
                        CustomerRepository customerRepository,
                        CompanyRepository companyRepository,
                        DailyClosingRepository dailyClosingRepository,
                        ExpenseRepository expenseRepository,
                        UserRepository userRepository,
                        ServiceUsedPartRepository serviceUsedPartRepository) {
                this.ticketRepository = ticketRepository;
                this.customerRepository = customerRepository;
                this.companyRepository = companyRepository;
                this.dailyClosingRepository = dailyClosingRepository;
                this.expenseRepository = expenseRepository;
                this.userRepository = userRepository;
                this.serviceUsedPartRepository = serviceUsedPartRepository;
        }

        /**
         * Generate Service Report PDF for a completed ticket (Professional Layout)
         */
        public byte[] generateServiceReport(Long ticketId) {
                ServiceTicket ticket = ticketRepository.findById(ticketId)
                                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

                Customer customer = customerRepository.findById(ticket.getCustomerId())
                                .orElseThrow(() -> new RuntimeException("Customer not found"));

                Company company = companyRepository.findById(ticket.getCompanyId())
                                .orElseThrow(() -> new RuntimeException("Company not found"));

                User technician = null;
                if (ticket.getAssignedTechnicianId() != null) {
                        technician = userRepository.findById(ticket.getAssignedTechnicianId()).orElse(null);
                }

                try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        Document document = new Document(PageSize.A4, 30, 30, 30, 30); // Reduced margins
                        PdfWriter.getInstance(document, baos);
                        document.open();

                        // 1. HEADER SECTION (Logo Left, Info Right)
                        addHeaderSection(document, company);

                        // 2. TITLE
                        Paragraph title = new Paragraph("SERVİS FORMU", HEADER_FONT);
                        title.setAlignment(Element.ALIGN_CENTER);
                        title.setSpacingBefore(10);
                        title.setSpacingAfter(10);
                        document.add(title);

                        // 3. CUSTOMER & DATES (Left: Customer, Right: Dates)
                        addCustomerAndDates(document, customer, ticket);

                        // 4. TECHNICIAN NOTE (Full Width)
                        addTechnicianNote(document, ticket);

                        // 5. FINANCIALS (Parts & Service Fee Table + Totals)
                        addFinancialSection(document, ticket);

                        // 6. SIGNATURES (Left: Tech, Right: Customer)
                        addSignatureSection(document, technician, customer);

                        // 7. FOOTER (Warranty Info)
                        addFooterSection(document);

                        document.close();
                        return baos.toByteArray();

                } catch (Exception e) {
                        throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
                }
        }

        // ========== LAYOUT HELPERS ==========

        private void addHeaderSection(Document document, Company company) throws DocumentException {
                PdfPTable headerTable = new PdfPTable(2);
                headerTable.setWidthPercentage(100);
                headerTable.setWidths(new float[] { 1f, 1f }); // Equal width, but content aligned

                // Left: Logo
                PdfPCell logoCell = new PdfPCell();
                logoCell.setBorder(Rectangle.NO_BORDER);
                logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);

                if (company.getLogoPath() != null && !company.getLogoPath().isEmpty()) {
                        try {
                                java.nio.file.Path path = java.nio.file.Paths.get("uploads", company.getLogoPath());
                                if (java.nio.file.Files.exists(path)) {
                                        Image logo = Image.getInstance(path.toAbsolutePath().toString());
                                        logo.scaleToFit(120, 60);
                                        logoCell.addElement(logo);
                                }
                        } catch (Exception e) {
                                // Ignore if logo fails
                        }
                } else {
                        // Fallback text if no logo
                        Paragraph p = new Paragraph(company.getName(), SECTION_FONT);
                        logoCell.addElement(p);
                }
                headerTable.addCell(logoCell);

                // Right: Company Info
                PdfPCell infoCell = new PdfPCell();
                infoCell.setBorder(Rectangle.NO_BORDER);
                infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

                Paragraph nameP = new Paragraph(company.getName(), SECTION_FONT);
                nameP.setAlignment(Element.ALIGN_RIGHT);
                infoCell.addElement(nameP);

                Paragraph addressP = new Paragraph(company.getAddress() != null ? company.getAddress() : "",
                                SMALL_FONT);
                addressP.setAlignment(Element.ALIGN_RIGHT);
                infoCell.addElement(addressP);

                Paragraph phoneP = new Paragraph(company.getPhone() != null ? company.getPhone() : "", SMALL_FONT);
                phoneP.setAlignment(Element.ALIGN_RIGHT);
                infoCell.addElement(phoneP);

                Paragraph emailP = new Paragraph(company.getEmail() != null ? company.getEmail() : "", SMALL_FONT);
                emailP.setAlignment(Element.ALIGN_RIGHT);
                infoCell.addElement(emailP);

                headerTable.addCell(infoCell);
                document.add(headerTable);
        }

        private void addCustomerAndDates(Document document, Customer customer, ServiceTicket ticket)
                        throws DocumentException {
                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(100);
                table.setSpacingBefore(15);
                table.setWidths(new float[] { 1f, 1f });

                // Left: Customer Info
                PdfPCell customerCell = new PdfPCell();
                customerCell.setBorder(Rectangle.NO_BORDER);
                customerCell.setVerticalAlignment(Element.ALIGN_TOP);

                Paragraph custTitle = new Paragraph("MÜŞTERİ BİLGİLERİ", SECTION_FONT);
                custTitle.setSpacingAfter(5);
                customerCell.addElement(custTitle);

                customerCell.addElement(new Paragraph("Ad Soyad: " + customer.getName(), NORMAL_FONT));
                if (customer.getAddress() != null && !customer.getAddress().isEmpty()) {
                        customerCell.addElement(new Paragraph("Adres: " + customer.getAddress(), SMALL_FONT));
                }
                customerCell.addElement(new Paragraph("Telefon: " + customer.getPhone(), SMALL_FONT));
                table.addCell(customerCell);

                // Right: Dates
                PdfPCell dateCell = new PdfPCell();
                dateCell.setBorder(Rectangle.NO_BORDER);
                dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                dateCell.setVerticalAlignment(Element.ALIGN_TOP);

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                String startDate = ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(fmt) : "-";
                String endDate = "-";
                if (ticket.getStatus() == ServiceTicket.TicketStatus.COMPLETED && ticket.getUpdatedAt() != null) {
                        endDate = ticket.getUpdatedAt().format(fmt);
                }

                Paragraph pStart = new Paragraph("Müracaat Tarihi: " + startDate, NORMAL_FONT);
                pStart.setAlignment(Element.ALIGN_RIGHT);
                dateCell.addElement(pStart);

                Paragraph pEnd = new Paragraph("Bitiş Tarihi: " + endDate, NORMAL_FONT);
                pEnd.setAlignment(Element.ALIGN_RIGHT);
                pEnd.setSpacingBefore(3);
                dateCell.addElement(pEnd);

                table.addCell(dateCell);
                document.add(table);
        }

        private void addTechnicianNote(Document document, ServiceTicket ticket) throws DocumentException {
                Paragraph title = new Paragraph("TEKNİSYEN NOTU", SECTION_FONT);
                title.setSpacingBefore(15);
                title.setSpacingAfter(5);
                document.add(title);

                PdfPTable table = new PdfPTable(1);
                table.setWidthPercentage(100);
                PdfPCell cell = new PdfPCell(
                                new Paragraph(ticket.getNotes() != null && !ticket.getNotes().isEmpty()
                                                ? ticket.getNotes()
                                                : "-", NORMAL_FONT));
                cell.setPadding(8);
                cell.setBorderColor(Color.LIGHT_GRAY);
                cell.setMinimumHeight(50f);
                cell.setBackgroundColor(new Color(250, 250, 250));
                table.addCell(cell);
                document.add(table);
        }

        private void addFinancialSection(Document document, ServiceTicket ticket) throws DocumentException {
                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(100);
                table.setSpacingBefore(10);
                table.setWidths(new float[] { 4f, 1f }); // Description, Amount

                // Header
                addCellToTable(table, "YAPILAN İŞLEM / KULLANILAN PARÇA", true, Element.ALIGN_LEFT);
                addCellToTable(table, "TUTAR", true, Element.ALIGN_RIGHT);

                BigDecimal subTotal = BigDecimal.ZERO;

                // 1. List Used Parts
                List<ServiceUsedPart> usedParts = serviceUsedPartRepository.findByServiceTicketId(ticket.getId());
                for (ServiceUsedPart part : usedParts) {
                        String partName = part.getInventory() != null ? part.getInventory().getPartName()
                                        : "Yedek Parça";
                        BigDecimal price = part.getSellingPriceSnapshot()
                                        .multiply(new BigDecimal(part.getQuantityUsed()));
                        subTotal = subTotal.add(price);

                        String desc = String.format("%s (x%d)", partName, part.getQuantityUsed());
                        addCellToTable(table, desc, false, Element.ALIGN_LEFT);
                        addCellToTable(table, String.format("%.2f ₺", price), false, Element.ALIGN_RIGHT);
                }

                // 2. Service Fee (Labor)
                BigDecimal serviceFee = ticket.getCollectedAmount() != null ? ticket.getCollectedAmount()
                                : BigDecimal.ZERO;
                subTotal = subTotal.add(serviceFee);

                addCellToTable(table, "İşçilik / Servis Hizmet Bedeli", false, Element.ALIGN_LEFT);
                addCellToTable(table, String.format("%.2f ₺", serviceFee), false, Element.ALIGN_RIGHT);

                // Fill empty rows if list is short
                int rowsFilled = usedParts.size() + 1;
                for (int i = rowsFilled; i < 5; i++) {
                        addCellToTable(table, " ", false, Element.ALIGN_LEFT);
                        addCellToTable(table, " ", false, Element.ALIGN_RIGHT);
                }

                document.add(table);

                // Totals
                PdfPTable totalsTable = new PdfPTable(2);
                totalsTable.setWidthPercentage(100);
                totalsTable.setWidths(new float[] { 4f, 1f });

                // Calculate VAT
                BigDecimal vatRate = new BigDecimal("0.20");
                BigDecimal vatAmount = subTotal.multiply(vatRate);
                BigDecimal grandTotal = subTotal.add(vatAmount);

                addTotalRow(totalsTable, "Ara Toplam:", String.format("%.2f ₺", subTotal));
                addTotalRow(totalsTable, "KDV (%20):", String.format("%.2f ₺", vatAmount));
                addTotalRow(totalsTable, "GENEL TOPLAM:", String.format("%.2f ₺", grandTotal));

                document.add(totalsTable);
        }

        private void addCellToTable(PdfPTable table, String text, boolean isHeader, int alignment) {
                PdfPCell cell = new PdfPCell(new Phrase(text, isHeader ? SMALL_BOLD_FONT : SMALL_FONT));
                cell.setHorizontalAlignment(alignment);
                cell.setPadding(5);
                cell.setBorderColor(Color.LIGHT_GRAY);
                if (isHeader) {
                        cell.setBackgroundColor(new Color(240, 240, 240));
                }
                table.addCell(cell);
        }

        private void addTotalRow(PdfPTable table, String label, String value) {
                PdfPCell c1 = new PdfPCell(new Phrase(label, SMALL_BOLD_FONT));
                c1.setBorder(Rectangle.NO_BORDER);
                c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
                c1.setPadding(2);

                PdfPCell c2 = new PdfPCell(new Phrase(value, SMALL_FONT));
                c2.setBorder(Rectangle.BOX);
                c2.setBorderColor(Color.LIGHT_GRAY);
                c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
                c2.setPadding(2);

                table.addCell(c1);
                table.addCell(c2);
        }

        private void addSignatureSection(Document document, User technician, Customer customer)
                        throws DocumentException {
                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(100);
                table.setSpacingBefore(40);
                table.setWidths(new float[] { 1f, 1f });

                // Technician Signature
                PdfPCell techCell = new PdfPCell();
                techCell.setBorder(Rectangle.NO_BORDER);
                techCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                techCell.setMinimumHeight(70f);

                // Try to load signature image
                boolean signatureLoaded = false;
                if (technician != null && technician.getSignaturePath() != null
                                && !technician.getSignaturePath().isEmpty()) {
                        try {
                                java.nio.file.Path path = java.nio.file.Paths.get("uploads",
                                                technician.getSignaturePath());
                                if (java.nio.file.Files.exists(path)) {
                                        Image signatureImg = Image.getInstance(path.toAbsolutePath().toString());
                                        signatureImg.scaleToFit(150, 50); // Max signature size
                                        techCell.addElement(signatureImg);
                                        signatureLoaded = true;
                                }
                        } catch (Exception e) {
                                // If signature image fails, fall back to text
                        }
                }

                // Fallback: name + line if no signature image
                if (!signatureLoaded) {
                        String techName = technician != null && technician.getFullName() != null
                                        ? technician.getFullName()
                                        : "";
                        Paragraph techNameP = new Paragraph(techName, NORMAL_FONT);
                        techCell.addElement(techNameP);

                        // Add signature line
                        Paragraph techLine = new Paragraph("_______________________", NORMAL_FONT);
                        techLine.setSpacingBefore(3);
                        techCell.addElement(techLine);
                }

                Paragraph techSigP = new Paragraph("Teknisyen İmzası", SMALL_FONT);
                techSigP.setSpacingBefore(2);
                techCell.addElement(techSigP);
                table.addCell(techCell);

                // Customer Signature (always text, no image)
                PdfPCell custCell = new PdfPCell();
                custCell.setBorder(Rectangle.NO_BORDER);
                custCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                custCell.setMinimumHeight(70f);
                String custName = customer != null && customer.getName() != null ? customer.getName()
                                : "";
                Paragraph pCust = new Paragraph(custName, NORMAL_FONT);
                pCust.setAlignment(Element.ALIGN_RIGHT);
                custCell.addElement(pCust);

                // Add signature line
                Paragraph custLine = new Paragraph("_______________________", NORMAL_FONT);
                custLine.setAlignment(Element.ALIGN_RIGHT);
                custLine.setSpacingBefore(3);
                custCell.addElement(custLine);

                Paragraph pSign = new Paragraph("Müşteri İmzası", SMALL_FONT);
                pSign.setAlignment(Element.ALIGN_RIGHT);
                pSign.setSpacingBefore(2);
                custCell.addElement(pSign);
                table.addCell(custCell);

                document.add(table);
        }

        private void addFooterSection(Document document) throws DocumentException {
                Paragraph footer = new Paragraph();
                footer.setSpacingBefore(25);
                footer.setAlignment(Element.ALIGN_LEFT);

                footer.add(new Chunk("GARANTİ VE BİLGİLENDİRME\n\n", SMALL_BOLD_FONT));

                Font bulletFont = new Font(turkishBaseFont, 7, Font.NORMAL, Color.DARK_GRAY);

                footer.add(new Chunk(
                                "• Değişen yedek parça 6 (altı) ay, işçilik 1 (bir) yıl garantilidir. Değiştirilen arızalı parça servis tarafından iade alınır.\n",
                                bulletFont));
                footer.add(new Chunk("• Garantinin geçerli olabilmesi için bu belgenin ibrazı şarttır.\n", bulletFont));
                footer.add(new Chunk("• 45 gün içinde alınmayan ürünlerden servisimiz sorumlu değildir.\n",
                                bulletFont));
                footer.add(new Chunk(
                                "• Mamülün yanlış kullanıldığı veya servisimiz dışında kimseler tarafından müdahale edilmesi halinde garanti geçerli değildir. Yapılan işlem müşterinin isteği ve onayı ile yapılmıştır.\n",
                                bulletFont));
                footer.add(new Chunk("• Bu belge fatura, fiş vs. yerine geçmez.", bulletFont));

                document.add(footer);
        }

        /**
         * Generate Proposal Form PDF (Stub)
         */
        public byte[] generateProposalForm(Long proposalId) {
                try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        Document document = new Document();
                        PdfWriter.getInstance(document, baos);
                        document.open();
                        document.add(new Paragraph("Proposal Form - Not Implemented Yet"));
                        document.close();
                        return baos.toByteArray();
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
        }

        // ========== MONTHLY FINANCIAL REPORTS ==========

        public List<com.pusula.backend.dto.MonthlySummaryDTO> getMonthlyArchives(Long companyId) {
                List<DailyClosing> closings = dailyClosingRepository.findAll().stream()
                                .filter(dc -> dc.getCompanyId().equals(companyId))
                                .collect(java.util.stream.Collectors.toList());

                Map<java.time.YearMonth, List<DailyClosing>> monthlyGroups = closings.stream()
                                .collect(java.util.stream.Collectors
                                                .groupingBy(dc -> java.time.YearMonth.from(dc.getDate())));

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
                                .filter(st -> st.getStatus() != null
                                                && st.getStatus().equals(ServiceTicket.TicketStatus.COMPLETED))
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

                DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE",
                                new Locale("tr", "TR"));
                java.text.DecimalFormat currencyFormat = new java.text.DecimalFormat("#,##0.00");

                for (java.time.LocalDate date : activeDates) {
                        Paragraph dateHeader = new Paragraph(date.format(dayFormatter).toUpperCase(), SECTION_FONT);
                        dateHeader.setSpacingBefore(15);
                        dateHeader.setSpacingAfter(5);
                        document.add(dateHeader);

                        BigDecimal dailyIncome = BigDecimal.ZERO;
                        BigDecimal dailyExpense = BigDecimal.ZERO;

                        List<ServiceTicket> dayTickets = ticketsByDate.getOrDefault(date,
                                        java.util.Collections.emptyList());
                        for (ServiceTicket ticket : dayTickets) {
                                String customerName = ticket.getCustomerId() != null
                                                ? customerRepository.findById(ticket.getCustomerId())
                                                                .map(Customer::getName)
                                                                .orElse("Bilinmiyor")
                                                : "Bilinmiyor";
                                String serviceDesc = ticket.getDescription() != null
                                                && !ticket.getDescription().isEmpty()
                                                                ? ticket.getDescription()
                                                                : "Servis Hizmeti";
                                BigDecimal amount = ticket.getCollectedAmount() != null ? ticket.getCollectedAmount()
                                                : BigDecimal.ZERO;
                                dailyIncome = dailyIncome.add(amount);

                                String line = String.format("   Gelir: %s - %s → %s ₺", customerName, serviceDesc,
                                                currencyFormat.format(amount));
                                Font greenFont = new Font(turkishBaseFont, 10, Font.NORMAL, new Color(0, 128, 0));
                                Paragraph incomeLine = new Paragraph(line, greenFont);
                                incomeLine.setIndentationLeft(10);
                                document.add(incomeLine);
                        }

                        List<Expense> dayExpenses = expensesByDate.getOrDefault(date,
                                        java.util.Collections.emptyList());
                        for (Expense expense : dayExpenses) {
                                String categoryName = getCategoryNameTurkish(expense.getCategory().name());
                                dailyExpense = dailyExpense.add(expense.getAmount());

                                String line = String.format("   Gider: %s - %s → %s ₺", categoryName,
                                                expense.getDescription(),
                                                currencyFormat.format(expense.getAmount()));
                                Font redFont = new Font(turkishBaseFont, 10, Font.NORMAL, new Color(192, 0, 0));
                                Paragraph expenseLine = new Paragraph(line, redFont);
                                expenseLine.setIndentationLeft(10);
                                document.add(expenseLine);
                        }

                        BigDecimal dailyNet = dailyIncome.subtract(dailyExpense);
                        String netLabel = dailyNet.compareTo(BigDecimal.ZERO) >= 0 ? "Günlük Net Kâr"
                                        : "Günlük Net Zarar";

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
                                        new Paragraph(netSign + currencyFormat.format(dailyNet) + " ₺",
                                                        SMALL_BOLD_FONT));
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

                Color totalColor = netProfit.compareTo(BigDecimal.ZERO) >= 0 ? new Color(0, 128, 0)
                                : new Color(192, 0, 0);

                Font totalFont = new Font(turkishBaseFont, 16, Font.BOLD, totalColor);
                Paragraph totalPara = new Paragraph(totalText, totalFont);
                totalPara.setAlignment(Element.ALIGN_RIGHT);
                totalPara.setSpacingBefore(10);
                totalPara.setSpacingAfter(20);
                document.add(totalPara);

                // Footer
                Paragraph footer = new Paragraph("Rapor Oluşturma Tarihi: "
                                + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                                SMALL_FONT);
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

        private String getStatusTurkish(ServiceTicket.TicketStatus status) {
                Map<String, String> statusNames = Map.of(
                                "PENDING", "Beklemede",
                                "ASSIGNED", "Atandı",
                                "IN_PROGRESS", "Devam Ediyor",
                                "COMPLETED", "Tamamlandı",
                                "CANCELLED", "İptal Edildi");
                return statusNames.getOrDefault(status.name(), status.toString());
        }
}
