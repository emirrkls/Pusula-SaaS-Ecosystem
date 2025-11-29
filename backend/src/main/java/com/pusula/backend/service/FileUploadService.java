package com.pusula.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final String UPLOAD_DIR = "uploads";

    public String uploadCompanyLogo(Long companyId, MultipartFile file) throws IOException {
        String fileName = "logo_" + UUID.randomUUID() + getFileExtension(file.getOriginalFilename());
        Path uploadPath = Paths.get(UPLOAD_DIR, "companies", companyId.toString());

        // Create directories if they don't exist
        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Return relative path
        return "companies/" + companyId + "/" + fileName;
    }

    public String uploadUserSignature(Long userId, MultipartFile file) throws IOException {
        String fileName = "signature_" + UUID.randomUUID() + getFileExtension(file.getOriginalFilename());
        Path uploadPath = Paths.get(UPLOAD_DIR, "signatures", userId.toString());

        // Create directories if they don't exist
        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Return relative path
        return "signatures/" + userId + "/" + fileName;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
