package com.pusula.desktop.util;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.pusula.desktop.dto.InventoryDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfReportGeneratorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesReadablePdfWhenInventoryContainsNullNumericValues() throws Exception {
        InventoryDTO missingCriticalLevel = item("R410A Gaz", 1, null,
                new BigDecimal("100.00"), new BigDecimal("150.00"));
        InventoryDTO missingSellPrice = item("Bakır Boru", 2, 2,
                new BigDecimal("200.00"), null);
        Path output = tempDirectory.resolve("inventory.pdf");

        PdfReportGenerator.writeInventoryReport(output.toFile(),
                List.of(missingCriticalLevel, missingSellPrice));

        assertTrue(Files.size(output) > 0);
        PdfReader reader = new PdfReader(output.toString());
        try {
            assertEquals(1, reader.getNumberOfPages());
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("R410A Gaz"));
            assertTrue(text.contains("Toplam Envanter Değeri"));
            assertTrue(text.contains("500,00"));
            assertTrue(text.contains("Envanterin Tahmini Toplam Satış Tutarı"));
            assertTrue(text.contains("550,00"));
            assertTrue(text.contains("Satış fiyatı belirtilmeyen ürünlerde alış fiyatı kullanılmıştır"));
        } finally {
            reader.close();
        }
    }

    private static InventoryDTO item(String name, Integer quantity, Integer criticalLevel,
            BigDecimal buyPrice, BigDecimal sellPrice) {
        InventoryDTO item = new InventoryDTO();
        item.setPartName(name);
        item.setQuantity(quantity);
        item.setCriticalLevel(criticalLevel);
        item.setBuyPrice(buyPrice);
        item.setSellPrice(sellPrice);
        return item;
    }
}
