package com.pusula.desktop.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class ModernUiContractTest {
    @Test
    void designSystemContainsFeedbackAndSemanticComponents() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/css/styles.css")) {
            assertNotNull(input);
            String css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(css.contains(".toast-card"));
            assertTrue(css.contains(".modern-dialog"));
            assertTrue(css.contains(".form-card"));
            assertTrue(css.contains(".ticket-action-bar"));
        }
    }

    @Test
    void productionControllersDoNotConstructNativeAlertsDirectly() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        try (var files = Files.walk(sourceRoot)) {
            boolean directAlert = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::readSafely)
                    .anyMatch(source -> source.contains("new Alert("));
            assertFalse(directAlert, "All feedback must go through NotificationService/AlertHelper");
        }
    }

    @Test
    void productionControllersUseDesignClassesInsteadOfInlineStyles() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        try (var files = Files.walk(sourceRoot)) {
            boolean inlineStyle = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::readSafely)
                    .anyMatch(source -> source.contains(".setStyle("));
            assertFalse(inlineStyle, "Controller styles must live in the shared design system");
        }
    }

    @Test
    void fxmlViewsRemainWellFormed() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        Path viewRoot = Path.of("src", "main", "resources", "view");
        try (var files = Files.walk(viewRoot)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".fxml")).toList()) {
                assertDoesNotThrow(() -> factory.newDocumentBuilder().parse(path.toFile()),
                        () -> "Malformed FXML: " + path);
            }
        }
    }

    private String readSafely(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new AssertionError("Could not inspect " + path, exception);
        }
    }
}
