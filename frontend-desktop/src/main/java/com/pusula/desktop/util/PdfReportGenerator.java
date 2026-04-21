package com.pusula.desktop.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pusula.desktop.dto.InventoryDTO;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PdfReportGenerator - Generates modern, professional PDF reports.
 * Uses Inter font for consistency with the app's premium design.
 */
public class PdfReportGenerator {

    // Modern color palette (matches app design)
    private static final Color BRAND_COLOR = new Color(2, 10, 85); // Dark blue
    private static final Color HEADER_BG = new Color(30, 41, 59); // Slate-800
    private static final Color TABLE_HEADER_BG = new Color(51, 65, 85); // Slate-700
    private static final Color TABLE_HEADER_TEXT = Color.WHITE;
    private static final Color CRITICAL_BG = new Color(254, 226, 226); // Red-100
    private static final Color CRITICAL_TEXT = new Color(185, 28, 28); // Red-700
    private static final Color BORDER_COLOR = new Color(203, 213, 225); // Slate-300
    private static final Color TEXT_MUTED = new Color(100, 116, 139); // Slate-500

    // Fonts (loaded once)
    private static BaseFont interBaseFont;
    private static Font TITLE_FONT;
    private static Font SUBTITLE_FONT;
    private static Font HEADER_FONT;
    private static Font NORMAL_FONT;
    private static Font SMALL_FONT;
    private static Font BOLD_FONT;
    private static Font CRITICAL_FONT;

    static {
        try {
            // Load Inter font with Turkish character support
            interBaseFont = BaseFont.createFont("/fonts/Inter.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            TITLE_FONT = new Font(interBaseFont, 20, Font.BOLD, BRAND_COLOR);
            SUBTITLE_FONT = new Font(interBaseFont, 12, Font.NORMAL, TEXT_MUTED);
            HEADER_FONT = new Font(interBaseFont, 10, Font.BOLD, TABLE_HEADER_TEXT);
            NORMAL_FONT = new Font(interBaseFont, 10, Font.NORMAL, Color.BLACK);
            SMALL_FONT = new Font(interBaseFont, 8, Font.NORMAL, TEXT_MUTED);
            BOLD_FONT = new Font(interBaseFont, 10, Font.BOLD, Color.BLACK);
            CRITICAL_FONT = new Font(interBaseFont, 10, Font.BOLD, CRITICAL_TEXT);
        } catch (Exception e) {
            System.err.println("Could not load Inter font, falling back to Helvetica: " + e.getMessage());
            // Fallback to Helvetica if Inter not available
            TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND_COLOR);
            SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12, TEXT_MUTED);
            HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TABLE_HEADER_TEXT);
            NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
            BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
            CRITICAL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, CRITICAL_TEXT);
        }
    }

    public static void generateInventoryReport(Stage stage, List<InventoryDTO> inventoryList) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Envanter Raporunu Kaydet");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Dosyaları", "*.pdf"));
        fileChooser.setInitialFileName("Envanter_Raporu_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf");

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Document document = new Document(PageSize.A4, 40, 40, 50, 50);
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();

                // ===== HEADER SECTION =====
                addHeader(document);

                // ===== DATE INFO =====
                Paragraph dateInfo = new Paragraph(
                        "Oluşturulma Tarihi: "
                                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm")),
                        SMALL_FONT);
                dateInfo.setSpacingAfter(15);
                document.add(dateInfo);

                // ===== SUMMARY BOX =====
                addSummaryBox(document, inventoryList);

                // ===== TABLE =====
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                table.setWidths(new float[] { 3f, 1f, 1.2f, 1.2f, 1f });
                table.setSpacingBefore(15);

                // Table headers
                addTableHeader(table, "Parça Adı");
                addTableHeader(table, "Miktar");
                addTableHeader(table, "Alış (₺)");
                addTableHeader(table, "Satış (₺)");
                addTableHeader(table, "Kritik");

                // Table data
                for (InventoryDTO item : inventoryList) {
                    boolean isCritical = item.getQuantity() <= item.getCriticalLevel();
                    addTableRow(table, item, isCritical);
                }

                document.add(table);

                // ===== FOOTER =====
                addFooter(document);

                document.close();

                NotificationHelper.showSuccess("Rapor başarıyla kaydedildi!");

            } catch (DocumentException | IOException e) {
                e.printStackTrace();
                NotificationHelper.showError("Rapor oluşturulamadı: " + e.getMessage());
            }
        }
    }

    private static void addHeader(Document document) throws DocumentException {
        Paragraph title = new Paragraph("📦 Envanter Raporu", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph subtitle = new Paragraph("Pusula Servis Ekosistemi", SUBTITLE_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);
    }

    private static void addSummaryBox(Document document, List<InventoryDTO> inventoryList) throws DocumentException {
        int totalItems = inventoryList.size();
        long criticalCount = inventoryList.stream()
                .filter(i -> i.getQuantity() <= i.getCriticalLevel())
                .count();
        int totalQuantity = inventoryList.stream()
                .mapToInt(InventoryDTO::getQuantity)
                .sum();

        PdfPTable summaryTable = new PdfPTable(3);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(10);

        addSummaryCell(summaryTable, "Toplam Ürün", String.valueOf(totalItems));
        addSummaryCell(summaryTable, "Toplam Stok", String.valueOf(totalQuantity));
        addSummaryCell(summaryTable, "Kritik Stok", String.valueOf(criticalCount), criticalCount > 0);

        document.add(summaryTable);
    }

    private static void addSummaryCell(PdfPTable table, String label, String value) {
        addSummaryCell(table, label, value, false);
    }

    private static void addSummaryCell(PdfPTable table, String label, String value, boolean isWarning) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        if (isWarning) {
            cell.setBackgroundColor(CRITICAL_BG);
        }

        Phrase content = new Phrase();
        content.add(new Chunk(value + "\n", isWarning ? CRITICAL_FONT : BOLD_FONT));
        content.add(new Chunk(label, SMALL_FONT));
        cell.setPhrase(content);

        table.addCell(cell);
    }

    private static void addTableHeader(PdfPTable table, String headerTitle) {
        PdfPCell header = new PdfPCell(new Phrase(headerTitle, HEADER_FONT));
        header.setBackgroundColor(TABLE_HEADER_BG);
        header.setBorderColor(TABLE_HEADER_BG);
        header.setPadding(8);
        header.setHorizontalAlignment(Element.ALIGN_CENTER);
        header.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(header);
    }

    private static void addTableRow(PdfPTable table, InventoryDTO item, boolean isCritical) {
        Color bgColor = isCritical ? CRITICAL_BG : Color.WHITE;
        Font font = isCritical ? CRITICAL_FONT : NORMAL_FONT;

        addCell(table, item.getPartName(), font, bgColor, Element.ALIGN_LEFT);
        addCell(table, String.valueOf(item.getQuantity()), font, bgColor, Element.ALIGN_CENTER);
        addCell(table, formatCurrency(item.getBuyPrice()), NORMAL_FONT, bgColor, Element.ALIGN_RIGHT);
        addCell(table, formatCurrency(item.getSellPrice()), NORMAL_FONT, bgColor, Element.ALIGN_RIGHT);
        addCell(table, String.valueOf(item.getCriticalLevel()), NORMAL_FONT, bgColor, Element.ALIGN_CENTER);
    }

    private static void addCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private static void addFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph(
                "\n© " + java.time.Year.now().getValue()
                        + " Pusula Servis Ekosistemi - Bu rapor otomatik olarak oluşturulmuştur.",
                SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);
    }

    private static String formatCurrency(java.math.BigDecimal amount) {
        if (amount == null)
            return "0,00";
        return String.format("%,.2f", amount).replace(",", " ").replace(".", ",").replace(" ", ".");
    }
}
