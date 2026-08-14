package com.pusula.backend.service;

import com.pusula.backend.entity.User;
import com.pusula.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class StorageUsageService {

    private final UserRepository userRepository;
    private final Path uploadRoot;

    public StorageUsageService(UserRepository userRepository,
            @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.userRepository = userRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public long getUsageBytes(Long companyId) {
        long total = directorySize(uploadRoot.resolve("companies").resolve(companyId.toString()));
        total += directorySize(uploadRoot.resolve("service-photos").resolve(companyId.toString()));

        List<User> users = userRepository.findByCompanyId(companyId);
        for (User user : users) {
            total += directorySize(uploadRoot.resolve("signatures").resolve(user.getId().toString()));
        }
        return total;
    }

    private long directorySize(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0L;
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ignored) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
