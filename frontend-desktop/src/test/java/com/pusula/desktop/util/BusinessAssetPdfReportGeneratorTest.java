package com.pusula.desktop.util;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.pusula.desktop.dto.BusinessAssetDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAssetPdfReportGeneratorTest {
    private static final Color NAVY = new Color(30, 58, 95);
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
        writeSample("business-assets-navy-sample.pdf", output);

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

    @Test
    void bodyFontsUseNavyInsteadOfBlack() throws Exception {
        assertEquals(NAVY, fontColor("NORMAL_FONT"));
        assertEquals(NAVY, fontColor("BOLD_FONT"));
    }

    private static Color fontColor(String fieldName) throws Exception {
        Field field = BusinessAssetPdfReportGenerator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((com.lowagie.text.Font) field.get(null)).getColor();
    }

    private static void writeSample(String name, Path source) throws Exception {
        if (Boolean.getBoolean("writeReportSamples")) {
            Path directory = Path.of("target", "report-samples");
            Files.createDirectories(directory);
            Files.copy(source, directory.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
