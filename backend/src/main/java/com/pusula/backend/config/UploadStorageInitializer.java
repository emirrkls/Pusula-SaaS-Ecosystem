package com.pusula.backend.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UploadStorageInitializer {

    private static final Logger log = LoggerFactory.getLogger(UploadStorageInitializer.class);

    @PostConstruct
    void ensureUploadDirectories() throws IOException {
        for (String dir : new String[] {
                "uploads",
                "uploads/service-photos",
                "uploads/signatures",
                "uploads/companies"
        }) {
            Path path = Paths.get(dir);
            Files.createDirectories(path);
            log.info("Upload directory ready: {}", path.toAbsolutePath());
        }
    }
}
