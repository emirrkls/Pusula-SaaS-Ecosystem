package com.pusula.desktop.update;

import com.pusula.desktop.util.AppVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;

public final class UpdateService {

    private static final String UPDATES_DIR = "Pusula" + java.io.File.separator + "Updates";

    private UpdateService() {
    }

    public static boolean isRunningFromNativePackage() {
        return resolveInstalledExePath().isPresent();
    }

    public static Optional<Path> resolveInstalledExePath() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return Optional.empty();
        }

        Path installRoot = Path.of(javaHome).getParent();
        if (installRoot == null) {
            return Optional.empty();
        }

        Path exe = installRoot.resolve(AppVersion.getExeName());
        return Files.isRegularFile(exe) ? Optional.of(exe.toAbsolutePath().normalize()) : Optional.empty();
    }

    public static Path getUpdatesDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData != null && !localAppData.isBlank()
                ? Path.of(localAppData)
                : Path.of(System.getProperty("user.home"), "AppData", "Local");
        return base.resolve(UPDATES_DIR);
    }

    public static Path downloadMsi(String downloadUrl, UpdateProgressListener listener) throws IOException {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IOException("İndirme adresi tanımlı değil.");
        }

        Path updatesDir = getUpdatesDirectory();
        Files.createDirectories(updatesDir);

        URL url = URI.create(downloadUrl.trim()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(120_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "PusulaDesktop/" + AppVersion.get());

        try {
            connection.connect();
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new IOException("Sunucu hatası: HTTP " + status);
            }

            String fileName = fileNameFromUrl(downloadUrl);
            Path target = updatesDir.resolve(fileName);

            long totalBytes = connection.getContentLengthLong();
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(
                         target,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    downloaded += read;
                    if (listener != null) {
                        listener.onProgress(downloaded, totalBytes);
                    }
                }
            }
            return target.toAbsolutePath().normalize();
        } finally {
            connection.disconnect();
        }
    }

    public static void launchInstallerAndExit(Path msiPath, Path exePath) throws IOException {
        Path updatesDir = getUpdatesDirectory();
        Files.createDirectories(updatesDir);

        Path batchFile = updatesDir.resolve("pusula-update.bat");
        String batchContent = """
                @echo off
                start /wait msiexec.exe /i "%~1" /passive /norestart
                if exist "%~2" start "" "%~2"
                """;
        Files.writeString(batchFile, batchContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        new ProcessBuilder(
                "cmd.exe",
                "/c",
                "start",
                "",
                "cmd.exe",
                "/c",
                batchFile.toString(),
                msiPath.toString(),
                exePath.toString()
        ).start();
    }

    private static String fileNameFromUrl(String downloadUrl) {
        String path = URI.create(downloadUrl.trim()).getPath();
        if (path != null) {
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash < path.length() - 1) {
                String name = path.substring(slash + 1);
                if (!name.isBlank()) {
                    return sanitizeFileName(name);
                }
            }
        }
        return "Pusula-Servis-Yonetimi-update.msi";
    }

    private static String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!sanitized.toLowerCase(Locale.ROOT).endsWith(".msi")) {
            sanitized = sanitized + ".msi";
        }
        return sanitized;
    }
}
