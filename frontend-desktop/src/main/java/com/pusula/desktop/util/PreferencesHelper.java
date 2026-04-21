package com.pusula.desktop.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * PreferencesHelper - Persists user preferences to disk.
 * Stores settings like theme preference, window size, etc.
 */
public class PreferencesHelper {

    private static final String PREFS_DIR = System.getProperty("user.home") + "/.pusula";
    private static final String PREFS_FILE = PREFS_DIR + "/preferences.properties";

    // Preference keys
    public static final String KEY_DARK_MODE = "theme.darkMode";
    public static final String KEY_WINDOW_MAXIMIZED = "window.maximized";
    public static final String KEY_WINDOW_WIDTH = "window.width";
    public static final String KEY_WINDOW_HEIGHT = "window.height";

    private static Properties properties = null;

    // =========================================
    // INITIALIZATION
    // =========================================

    /**
     * Loads preferences from disk. Called once at startup.
     */
    private static void ensureLoaded() {
        if (properties != null)
            return;

        properties = new Properties();

        try {
            Path prefsPath = Paths.get(PREFS_FILE);
            if (Files.exists(prefsPath)) {
                try (InputStream is = Files.newInputStream(prefsPath)) {
                    properties.load(is);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load preferences: " + e.getMessage());
        }
    }

    /**
     * Saves preferences to disk.
     */
    private static void save() {
        try {
            // Ensure directory exists
            Path dir = Paths.get(PREFS_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            try (OutputStream os = Files.newOutputStream(Paths.get(PREFS_FILE))) {
                properties.store(os, "Pusula Desktop Preferences");
            }
        } catch (IOException e) {
            System.err.println("Could not save preferences: " + e.getMessage());
        }
    }

    // =========================================
    // THEME PREFERENCES
    // =========================================

    /**
     * Gets the dark mode preference.
     * 
     * @return true if dark mode is enabled, false for light mode (default)
     */
    public static boolean isDarkMode() {
        ensureLoaded();
        return Boolean.parseBoolean(properties.getProperty(KEY_DARK_MODE, "false"));
    }

    /**
     * Sets the dark mode preference.
     * 
     * @param darkMode true for dark mode, false for light mode
     */
    public static void setDarkMode(boolean darkMode) {
        ensureLoaded();
        properties.setProperty(KEY_DARK_MODE, String.valueOf(darkMode));
        save();
    }

    // =========================================
    // WINDOW PREFERENCES
    // =========================================

    /**
     * Gets whether the window was maximized.
     */
    public static boolean isWindowMaximized() {
        ensureLoaded();
        return Boolean.parseBoolean(properties.getProperty(KEY_WINDOW_MAXIMIZED, "true"));
    }

    /**
     * Sets the window maximized state.
     */
    public static void setWindowMaximized(boolean maximized) {
        ensureLoaded();
        properties.setProperty(KEY_WINDOW_MAXIMIZED, String.valueOf(maximized));
        save();
    }

    /**
     * Gets the window width preference.
     */
    public static double getWindowWidth() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty(KEY_WINDOW_WIDTH, "1200"));
    }

    /**
     * Sets the window width preference.
     */
    public static void setWindowWidth(double width) {
        ensureLoaded();
        properties.setProperty(KEY_WINDOW_WIDTH, String.valueOf(width));
        save();
    }

    /**
     * Gets the window height preference.
     */
    public static double getWindowHeight() {
        ensureLoaded();
        return Double.parseDouble(properties.getProperty(KEY_WINDOW_HEIGHT, "800"));
    }

    /**
     * Sets the window height preference.
     */
    public static void setWindowHeight(double height) {
        ensureLoaded();
        properties.setProperty(KEY_WINDOW_HEIGHT, String.valueOf(height));
        save();
    }

    // =========================================
    // GENERIC GETTERS/SETTERS
    // =========================================

    /**
     * Gets a string preference.
     */
    public static String getString(String key, String defaultValue) {
        ensureLoaded();
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Sets a string preference.
     */
    public static void setString(String key, String value) {
        ensureLoaded();
        properties.setProperty(key, value);
        save();
    }

    /**
     * Gets a boolean preference.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        ensureLoaded();
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    /**
     * Sets a boolean preference.
     */
    public static void setBoolean(String key, boolean value) {
        ensureLoaded();
        properties.setProperty(key, String.valueOf(value));
        save();
    }
}
