package com.pusula.desktop.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {

    private static final String EXE_NAME = "Pusula Servis Yonetimi.exe";
    private static final String VERSION = loadVersion();

    private AppVersion() {
    }

    public static String get() {
        return VERSION;
    }

    public static String getExeName() {
        return EXE_NAME;
    }

    public static boolean isRemoteNewer(String remoteVersion) {
        return VersionComparator.compare(remoteVersion, VERSION) > 0;
    }

    private static String loadVersion() {
        Properties properties = new Properties();
        try (InputStream in = AppVersion.class.getResourceAsStream("/app-version.properties")) {
            if (in != null) {
                properties.load(in);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "3.0.0";
    }
}
