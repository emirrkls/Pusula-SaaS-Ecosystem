package com.pusula.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Iterator;

@Service
public class FileUploadService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int THUMBNAIL_MAX_EDGE = 720;
    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);
    private final FeatureService featureService;
    private final Path uploadRoot;

    public FileUploadService(FeatureService featureService,
            @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.featureService = featureService;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String uploadCompanyLogo(Long companyId, MultipartFile file, String previousPath) throws IOException {
        ValidatedImage image = validateImage(file);
        featureService.checkStorageQuota(companyId, additionalBytes(image.bytes().length, previousPath));
        String fileName = "logo_" + UUID.randomUUID() + image.extension();
        Path uploadPath = uploadRoot.resolve("companies").resolve(companyId.toString());

        // Create directories if they don't exist
        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(new ByteArrayInputStream(image.bytes()), filePath, StandardCopyOption.REPLACE_EXISTING);
        deletePrevious(previousPath, filePath);

        // Return relative path
        return "companies/" + companyId + "/" + fileName;
    }

    public String uploadUserSignature(Long companyId, Long userId, MultipartFile file, String previousPath) throws IOException {
        ValidatedImage image = validateImage(file);
        featureService.checkStorageQuota(companyId, additionalBytes(image.bytes().length, previousPath));
        String fileName = "signature_" + UUID.randomUUID() + image.extension();
        Path uploadPath = uploadRoot.resolve("signatures").resolve(userId.toString());

        // Create directories if they don't exist
        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(new ByteArrayInputStream(image.bytes()), filePath, StandardCopyOption.REPLACE_EXISTING);
        deletePrevious(previousPath, filePath);

        // Return relative path
        return "signatures/" + userId + "/" + fileName;
    }

    public String uploadServicePhoto(Long companyId, Long ticketId, String type, MultipartFile file) throws IOException {
        ValidatedImage image = validateImage(file);
        featureService.checkStorageQuota(companyId, image.bytes().length);
        String safeType = type == null ? "photo" : type.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        String fileName = safeType + "_" + UUID.randomUUID() + image.extension();
        Path uploadPath = uploadRoot.resolve("service-photos")
                .resolve(companyId.toString()).resolve(ticketId.toString());

        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(new ByteArrayInputStream(image.bytes()), filePath, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = "service-photos/" + companyId + "/" + ticketId + "/" + fileName;
        createThumbnailIfPossible(relativePath);

        return relativePath;
    }

    /**
     * Returns a cache-style thumbnail path for a stored service photo. Existing
     * photos are backfilled lazily; unsupported formats safely fall back to the
     * original URL at the DTO layer.
     */
    public String getOrCreateServicePhotoThumbnail(String storedUrl) {
        String relativePath = normalizeServicePhotoPath(storedUrl);
        if (relativePath == null) return null;
        return createThumbnailIfPossible(relativePath);
    }

    public void deleteServicePhotoAndThumbnail(String storedUrl) {
        String relativePath = normalizeServicePhotoPath(storedUrl);
        if (relativePath == null) return;
        Path original = resolveInsideUploadRoot(relativePath);
        Path thumbnail = resolveInsideUploadRoot(thumbnailRelativePath(relativePath));
        try {
            Files.deleteIfExists(original);
            Files.deleteIfExists(thumbnail);
        } catch (IOException exception) {
            log.warn("Service photo or thumbnail could not be deleted: {}", relativePath, exception);
        }
    }

    private String createThumbnailIfPossible(String relativePath) {
        Path source = resolveInsideUploadRoot(relativePath);
        String thumbnailRelative = thumbnailRelativePath(relativePath);
        Path target = resolveInsideUploadRoot(thumbnailRelative);
        if (!Files.isRegularFile(source)) return null;
        if (Files.isRegularFile(target)) return thumbnailRelative;

        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null || original.getWidth() <= 0 || original.getHeight() <= 0) {
                return null;
            }
            double scale = Math.min(1d, (double) THUMBNAIL_MAX_EDGE
                    / Math.max(original.getWidth(), original.getHeight()));
            int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
            BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(original, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }

            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
            writeJpeg(thumbnail, temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                Files.deleteIfExists(temporary);
            }
            return thumbnailRelative;
        } catch (IOException | RuntimeException exception) {
            log.warn("Thumbnail could not be generated for {}", relativePath, exception);
            return null;
        }
    }

    private void writeJpeg(BufferedImage image, Path destination) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG writer is unavailable");
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(destination.toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(0.82f);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private String normalizeServicePhotoPath(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) return null;
        String value = storedUrl.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) value = value.substring(0, queryIndex);
        if (value.startsWith("/uploads/")) value = value.substring("/uploads/".length());
        else if (value.startsWith("uploads/")) value = value.substring("uploads/".length());
        if (!value.startsWith("service-photos/")) return null;
        Path normalized = Path.of(value).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) return null;
        return normalized.toString().replace('\\', '/');
    }

    private String thumbnailRelativePath(String servicePhotoRelativePath) {
        String suffix = servicePhotoRelativePath.substring("service-photos/".length());
        return "service-photo-thumbnails/" + suffix + ".thumb.jpg";
    }

    private Path resolveInsideUploadRoot(String relativePath) {
        Path resolved = uploadRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Upload path is outside the configured root");
        }
        return resolved;
    }

    private ValidatedImage validateImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Yüklenecek görsel bulunamadı.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Görsel boyutu 5 MB'dan büyük olamaz.");
        }
        byte[] bytes = file.getBytes();
        String extension = detectImageExtension(bytes);
        if (extension == null) {
            throw new IllegalArgumentException("Yalnızca gerçek JPG, PNG veya WEBP görselleri yüklenebilir.");
        }
        return new ValidatedImage(bytes, extension);
    }

    private String detectImageExtension(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E
                && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A
                && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return ".png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return ".webp";
        }
        return null;
    }

    private long additionalBytes(long newSize, String previousPath) {
        Path previous = resolveStoredPath(previousPath);
        if (previous == null || !Files.isRegularFile(previous)) {
            return newSize;
        }
        try {
            return Math.max(0L, newSize - Files.size(previous));
        } catch (IOException ignored) {
            return newSize;
        }
    }

    private void deletePrevious(String previousPath, Path replacement) {
        Path previous = resolveStoredPath(previousPath);
        if (previous == null || previous.equals(replacement.toAbsolutePath().normalize())) {
            return;
        }
        try {
            Files.deleteIfExists(previous);
        } catch (IOException ignored) {
            // The new upload succeeded; an orphaned old file must not fail the request.
        }
    }

    private Path resolveStoredPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path resolved = uploadRoot.resolve(relativePath).normalize();
        return resolved.startsWith(uploadRoot) ? resolved : null;
    }

    private record ValidatedImage(byte[] bytes, String extension) {}
}
