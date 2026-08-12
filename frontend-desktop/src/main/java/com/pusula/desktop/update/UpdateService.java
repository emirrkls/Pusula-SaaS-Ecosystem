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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    public static Path downloadMsi(String downloadUrl, String expectedSha256,
                                   UpdateProgressListener listener) throws IOException {
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
                if (totalBytes > 0 && downloaded != totalBytes) {
                    throw new IOException("Güncelleme dosyası eksik indirildi.");
                }
            }
            validateMsi(target);
            validateSha256(target, expectedSha256);
            return target.toAbsolutePath().normalize();
        } finally {
            connection.disconnect();
        }
    }

    public static void launchInstallerAndExit(Path msiPath, Path exePath) throws IOException {
        Path updatesDir = getUpdatesDirectory();
        Files.createDirectories(updatesDir);

        Path scriptFile = updatesDir.resolve("pusula-update.ps1");
        Path logFile = updatesDir.resolve("pusula-update.log");
        String scriptContent = """
                param(
                    [Parameter(Mandatory=$true)][string]$MsiPath,
                    [Parameter(Mandatory=$true)][string]$ExePath,
                    [Parameter(Mandatory=$true)][string]$LogPath
                )
                try {
                    "$(Get-Date -Format o) Update starting: $MsiPath" | Out-File -FilePath $LogPath -Encoding utf8
                    $arguments = @('/i', ('"' + $MsiPath + '"'), '/passive', '/norestart')
                    $installer = Start-Process -FilePath "$env:SystemRoot\\System32\\msiexec.exe" -ArgumentList $arguments -Verb RunAs -Wait -PassThru
                    "$(Get-Date -Format o) MSI exit code: $($installer.ExitCode)" | Out-File -FilePath $LogPath -Append -Encoding utf8
                    if ($installer.ExitCode -notin @(0, 1641, 3010)) { exit $installer.ExitCode }
                    if (Test-Path -LiteralPath $ExePath) {
                        Start-Process -FilePath $ExePath
                    }
                } catch {
                    "$(Get-Date -Format o) Update failed: $($_.Exception.Message)" | Out-File -FilePath $LogPath -Append -Encoding utf8
                    exit 1
                }
                """;
        Files.writeString(scriptFile, scriptContent, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptFile.toString(),
                msiPath.toString(),
                exePath.toString(),
                logFile.toString()
        ).start();
    }

    private static void validateMsi(Path target) throws IOException {
        byte[] expectedHeader = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        if (Files.size(target) < expectedHeader.length) {
            throw new IOException("İndirilen güncelleme geçerli bir MSI dosyası değil.");
        }
        try (InputStream input = Files.newInputStream(target)) {
            for (byte expected : expectedHeader) {
                if (input.read() != Byte.toUnsignedInt(expected)) {
                    throw new IOException("İndirilen güncelleme geçerli bir MSI dosyası değil.");
                }
            }
        }
    }

    private static void validateSha256(Path target, String expectedSha256) throws IOException {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new IOException("Güncelleme doğrulama özeti sunucu tarafından sağlanmadı.");
        }
        String normalized = expectedSha256.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IOException("Güncelleme doğrulama özeti geçersiz.");
        }
        try (InputStream input = Files.newInputStream(target)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    normalized.getBytes(StandardCharsets.US_ASCII))) {
                Files.deleteIfExists(target);
                throw new IOException("Güncelleme dosyasının güvenlik doğrulaması başarısız oldu.");
            }
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 doğrulaması kullanılamıyor.", ex);
        }
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
