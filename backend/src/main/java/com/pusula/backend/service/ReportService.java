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

@Service
public class ReportService {

    private final ServiceTicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

    public ReportService(ServiceTicketRepository ticketRepository,
            CustomerRepository customerRepository,
            CompanyRepository companyRepository) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
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
}
