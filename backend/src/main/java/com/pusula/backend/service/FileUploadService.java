package com.pusula.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.util.Locale;

@Service
public class FileUploadService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
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

        return "service-photos/" + companyId + "/" + ticketId + "/" + fileName;
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
