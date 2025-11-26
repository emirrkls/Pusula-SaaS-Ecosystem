package com.pusula.desktop.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pusula.desktop.dto.InventoryDTO;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PdfReportGenerator {

    public static void generateInventoryReport(Stage stage, List<InventoryDTO> inventoryList) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Inventory Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName("Inventory_Report_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf");

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Document document = new Document();
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();

                // Title
                Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
                Paragraph title = new Paragraph("Envanter Raporu", titleFont);
                title.setAlignment(Element.ALIGN_CENTER);
                document.add(title);

                document.add(new Paragraph("Oluşturulma Tarihi: "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))));
                document.add(new Paragraph(" ")); // Spacer

                // Table
                PdfPTable table = new PdfPTable(5); // 5 columns
                table.setWidthPercentage(100);
                table.setWidths(new float[] { 3, 1, 1, 1, 1 });

                // Headers
                addTableHeader(table, "Parça Adı");
                addTableHeader(table, "Miktar");
                addTableHeader(table, "Alış Fiyatı");
                addTableHeader(table, "Satış Fiyatı");
                addTableHeader(table, "Kritik Seviye");

                // Data
                for (InventoryDTO item : inventoryList) {
                    table.addCell(item.getPartName());
                    table.addCell(String.valueOf(item.getQuantity()));
                    table.addCell(item.getBuyPrice() != null ? item.getBuyPrice().toString() : "0.00");
                    table.addCell(item.getSellPrice() != null ? item.getSellPrice().toString() : "0.00");
                    table.addCell(String.valueOf(item.getCriticalLevel()));
                }

                document.add(table);
                document.close();

                AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, stage, "Başarılı",
                        "Rapor başarıyla kaydedildi!");

            } catch (DocumentException | IOException e) {
                e.printStackTrace();
                AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.ERROR, stage, "Error",
                        "Failed to generate report: " + e.getMessage());
            }
        }
    }

    private static void addTableHeader(PdfPTable table, String headerTitle) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        header.setBorderWidth(2);
        header.setPhrase(new Phrase(headerTitle));
        table.addCell(header);
    }
}
