package com.pusula.desktop.util;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.pusula.desktop.dto.BusinessAssetDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAssetPdfReportGeneratorTest {
    @TempDir Path tempDirectory;

    @Test
    void writesReadableLandscapePdfWithTurkishTextAndTotals() throws Exception {
        BusinessAssetDTO asset = new BusinessAssetDTO();
        asset.setAssetName("Şarjlı Matkap");
        asset.setCategory("Elektrikli El Aleti");
        asset.setQuantity(2);
        asset.setCondition("ACTIVE");
        asset.setSerialNumber("TR-001");
        asset.setLocation("İzmir Depo");
        asset.setAssignedTo("Uğur");
        asset.setPurchaseDate(LocalDate.of(2026, 8, 1));
        asset.setPurchasePrice(new BigDecimal("12500.00"));

        Path output = tempDirectory.resolve("assets.pdf");
        BusinessAssetPdfReportGenerator.writeReport(output.toFile(), List.of(asset));

        assertTrue(Files.size(output) > 0);
        PdfReader reader = new PdfReader(output.toString());
        try {
            assertEquals(1, reader.getNumberOfPages());
            assertTrue(reader.getPageSizeWithRotation(1).getWidth() > reader.getPageSizeWithRotation(1).getHeight());
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("Şarjlı Matkap"));
            assertTrue(text.contains("Envanter Değeri"));
            assertTrue(text.contains("25.000,00"));
        } finally {
            reader.close();
        }
    }
}
