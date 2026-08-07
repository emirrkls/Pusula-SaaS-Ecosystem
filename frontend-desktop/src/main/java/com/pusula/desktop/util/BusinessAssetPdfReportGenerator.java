package com.pusula.desktop.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pusula.desktop.controller.BusinessAssetDialogController;
import com.pusula.desktop.dto.BusinessAssetDTO;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class BusinessAssetPdfReportGenerator {
    private static final Color BRAND = new Color(2, 10, 85);
    private static final Color HEADER = new Color(51, 65, 85);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private static final Font TITLE_FONT;
    private static final Font SUBTITLE_FONT;
    private static final Font HEADER_FONT;
    private static final Font NORMAL_FONT;
    private static final Font SMALL_FONT;
    private static final Font BOLD_FONT;

    static {
        BaseFont baseFont;
        try (InputStream stream = BusinessAssetPdfReportGenerator.class.getResourceAsStream("/fonts/Inter.ttf")) {
            if (stream == null) throw new IOException("Inter font resource not found");
            baseFont = BaseFont.createFont("Inter.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, stream.readAllBytes(), null);
        } catch (Exception e) {
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception fallbackError) {
                throw new ExceptionInInitializerError(fallbackError);
            }
        }
        TITLE_FONT = new Font(baseFont, 20, Font.BOLD, BRAND);
        SUBTITLE_FONT = new Font(baseFont, 11, Font.NORMAL, MUTED);
        HEADER_FONT = new Font(baseFont, 8, Font.BOLD, Color.WHITE);
        NORMAL_FONT = new Font(baseFont, 8, Font.NORMAL, Color.BLACK);
        SMALL_FONT = new Font(baseFont, 7, Font.NORMAL, MUTED);
        BOLD_FONT = new Font(baseFont, 10, Font.BOLD, Color.BLACK);
    }

    private BusinessAssetPdfReportGenerator() {}

    public static void generate(Stage stage, List<BusinessAssetDTO> assets) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Takım / Demirbaş Raporunu Kaydet");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Dosyaları", "*.pdf"));
        chooser.setInitialFileName("Takim_Demirbas_Raporu_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf");
        File targetFile = chooser.showSaveDialog(stage);
        if (targetFile == null) return;

        Path temporary = null;
        try {
            Path target = targetFile.toPath().toAbsolutePath();
            temporary = Files.createTempFile(target.getParent(), ".demirbas-raporu-", ".pdf");
            writeReport(temporary.toFile(), assets);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            NotificationHelper.showSuccess("Takım / demirbaş raporu kaydedildi.");
        } catch (Exception e) {
            e.printStackTrace();
            NotificationHelper.showError("Rapor oluşturulamadı: " + e.getMessage());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    static void writeReport(File file, List<BusinessAssetDTO> assets) throws IOException, DocumentException {
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 35, 35);
        try (FileOutputStream output = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, output);
            document.open();
            try {
                Paragraph title = new Paragraph("Takımlar / Demirbaşlar Raporu", TITLE_FONT);
                title.setAlignment(Element.ALIGN_CENTER);
                document.add(title);

                Paragraph subtitle = new Paragraph("Pusula Servis Ekosistemi", SUBTITLE_FONT);
                subtitle.setAlignment(Element.ALIGN_CENTER);
                subtitle.setSpacingAfter(12);
                document.add(subtitle);

                Paragraph created = new Paragraph("Oluşturulma Tarihi: "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", TR)), SMALL_FONT);
                created.setSpacingAfter(10);
                document.add(created);

                addSummary(document, assets);
                addTable(document, assets);

                Paragraph footer = new Paragraph("© " + java.time.Year.now().getValue()
                        + " Pusula Servis Ekosistemi - Bu rapor otomatik oluşturulmuştur.", SMALL_FONT);
                footer.setAlignment(Element.ALIGN_CENTER);
                footer.setSpacingBefore(15);
                document.add(footer);
            } finally {
                if (document.isOpen()) document.close();
            }
        }
    }

    private static void addSummary(Document document, List<BusinessAssetDTO> assets) throws DocumentException {
        int units = assets.stream().mapToInt(a -> safeInt(a.getQuantity(), 1)).sum();
        BigDecimal totalValue = assets.stream()
                .map(a -> safeMoney(a.getPurchasePrice()).multiply(BigDecimal.valueOf(safeInt(a.getQuantity(), 1))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PdfPTable summary = new PdfPTable(3);
        summary.setWidthPercentage(100);
        addSummaryCell(summary, "Kayıt", String.valueOf(assets.size()));
        addSummaryCell(summary, "Toplam Adet", String.valueOf(units));
        addSummaryCell(summary, "Envanter Değeri", formatMoney(totalValue) + " ₺");
        summary.setSpacingAfter(12);
        document.add(summary);
    }

    private static void addSummaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Phrase phrase = new Phrase();
        phrase.add(new com.lowagie.text.Chunk(value + "\n", BOLD_FONT));
        phrase.add(new com.lowagie.text.Chunk(label, SMALL_FONT));
        cell.setPhrase(phrase);
        table.addCell(cell);
    }

    private static void addTable(Document document, List<BusinessAssetDTO> assets) throws DocumentException {
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 2.2f, 1.2f, .55f, 1.05f, 1.25f, 2f, 1f, 1.1f });
        String[] headers = { "Takım / Demirbaş", "Kategori", "Adet", "Durum", "Seri No",
                "Konum / Zimmet", "Alış Tarihi", "Birim Değer" };
        for (String header : headers) addHeader(table, header);
        table.setHeaderRows(1);

        boolean alternate = false;
        for (BusinessAssetDTO asset : assets) {
            Color background = alternate ? new Color(248, 250, 252) : Color.WHITE;
            String nameAndNotes = value(asset.getAssetName());
            if (asset.getNotes() != null && !asset.getNotes().isBlank()) {
                nameAndNotes += "\nNot: " + asset.getNotes().trim();
            }
            addCell(table, nameAndNotes, background, Element.ALIGN_LEFT);
            addCell(table, value(asset.getCategory()), background, Element.ALIGN_LEFT);
            addCell(table, String.valueOf(safeInt(asset.getQuantity(), 1)), background, Element.ALIGN_CENTER);
            addCell(table, BusinessAssetDialogController.conditionLabel(asset.getCondition()), background, Element.ALIGN_LEFT);
            addCell(table, value(asset.getSerialNumber()), background, Element.ALIGN_LEFT);
            addCell(table, assignment(asset), background, Element.ALIGN_LEFT);
            addCell(table, formatDate(asset.getPurchaseDate()), background, Element.ALIGN_CENTER);
            addCell(table, formatMoney(asset.getPurchasePrice()) + " ₺", background, Element.ALIGN_RIGHT);
            alternate = !alternate;
        }
        document.add(table);
    }

    private static void addHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER);
        cell.setBorderColor(HEADER);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static void addCell(PdfPTable table, String text, Color background, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, NORMAL_FONT));
        cell.setBackgroundColor(background);
        cell.setBorderColor(BORDER);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private static String assignment(BusinessAssetDTO asset) {
        String location = value(asset.getLocation());
        String assigned = asset.getAssignedTo();
        return assigned == null || assigned.isBlank() ? location : location + " / " + assigned;
    }

    private static String formatDate(LocalDate date) { return date == null ? "—" : DATE.format(date); }
    private static String value(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static int safeInt(Integer value, int fallback) { return value == null ? fallback : value; }
    private static BigDecimal safeMoney(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private static String formatMoney(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(TR);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(safeMoney(value));
    }
}
