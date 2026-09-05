package com.pusula.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileUploadServiceThumbnailTest {

    @TempDir Path temporaryDirectory;

    @Test
    void createsAndDeletesBoundedThumbnailAlongsideOriginal() throws Exception {
        FileUploadService service = new FileUploadService(mock(FeatureService.class), temporaryDirectory.toString());
        BufferedImage source = new BufferedImage(1400, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "jpg", bytes);

        String original = service.uploadServicePhoto(4L, 75L, "AFTER",
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes.toByteArray()));
        String thumbnail = service.getOrCreateServicePhotoThumbnail("/uploads/" + original);

        assertNotNull(thumbnail);
        Path thumbnailFile = temporaryDirectory.resolve(thumbnail);
        assertTrue(Files.isRegularFile(thumbnailFile));
        BufferedImage decoded = ImageIO.read(thumbnailFile.toFile());
        assertEquals(720, decoded.getWidth());
        assertEquals(360, decoded.getHeight());

        service.deleteServicePhotoAndThumbnail("/uploads/" + original);
        assertFalse(Files.exists(temporaryDirectory.resolve(original)));
        assertFalse(Files.exists(thumbnailFile));
    }
}
