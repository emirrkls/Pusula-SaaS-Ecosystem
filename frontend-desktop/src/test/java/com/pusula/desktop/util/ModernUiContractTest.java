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
            assertTrue(css.contains(".modern-dialog > .button-bar .button"));
            assertTrue(css.contains("-fx-wrap-text: true"));
            assertTrue(css.contains(".form-card"));
            assertTrue(css.contains(".ticket-action-bar"));
            assertTrue(css.contains(".modern-dialog-scroll"));
            assertTrue(css.contains(".context-menu"));
        }
    }

    @Test
    void modernDialogOverridesComeAfterGenericDialogRules() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/css/styles.css")) {
            assertNotNull(input);
            String css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int genericRule = css.indexOf(".dialog-pane {");
            int hardenedRule = css.lastIndexOf(".dialog-pane.modern-dialog {");
            assertTrue(genericRule >= 0 && hardenedRule > genericRule,
                    "Modern dialog rules must win the CSS cascade");
            assertTrue(css.substring(hardenedRule).contains("-fx-min-width: 112"),
                    "Dialog actions must remain readable instead of using ellipsis");
        }
    }

    @Test
    void commonFormDialogsUseTheSharedActionBar() throws Exception {
        Path viewRoot = Path.of("src", "main", "resources", "view");
        for (String file : java.util.List.of(
                "customer_dialog.fxml", "user_dialog.fxml", "company_debt_dialog.fxml",
                "inventory_dialog.fxml", "business_asset_dialog.fxml", "vehicle_dialog.fxml",
                "commercial_device_dialog.fxml", "commercial_device_sales_dialog.fxml",
                "ticket_dialog.fxml", "transfer_stock_dialog.fxml")) {
            String fxml = Files.readString(viewRoot.resolve(file), StandardCharsets.UTF_8);
            assertTrue(fxml.contains("styleClass=\"dialog-action-bar\""),
                    () -> file + " must use the shared modern action bar");
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

    @Test
    void fxmlViewsDoNotBypassTheSharedDesignSystemWithInlineCss() throws Exception {
        Path viewRoot = Path.of("src", "main", "resources", "view");
        try (var files = Files.walk(viewRoot)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".fxml")).toList()) {
                String fxml = Files.readString(path, StandardCharsets.UTF_8);
                assertFalse(fxml.contains(" style=\""),
                        () -> "Inline CSS must move to styles.css: " + path);
            }
        }
    }

    @Test
    void coreWorkspacesUseStructuredModernSurfaces() throws Exception {
        Path viewRoot = Path.of("src", "main", "resources", "view");
        String finance = Files.readString(viewRoot.resolve("finance_view.fxml"), StandardCharsets.UTF_8);
        String dashboard = Files.readString(viewRoot.resolve("dashboard.fxml"), StandardCharsets.UTF_8);
        String shell = Files.readString(viewRoot.resolve("main_dashboard.fxml"), StandardCharsets.UTF_8);
        String tickets = Files.readString(viewRoot.resolve("service_tickets.fxml"), StandardCharsets.UTF_8);
        String photos = Files.readString(viewRoot.resolve("service_photos.fxml"), StandardCharsets.UTF_8);
        String debts = Files.readString(viewRoot.resolve("company_debts.fxml"), StandardCharsets.UTF_8);

        assertTrue(finance.contains("finance-summary-grid"));
        assertTrue(finance.contains("finance-ledger-card"));
        assertTrue(finance.contains("finance-close-day-bar"));
        assertTrue(finance.contains("finance-analytics-kpis"));
        assertTrue(finance.contains("report-summary-scroll"));
        assertTrue(finance.contains("report-archive-table"));
        assertFalse(finance.contains("btn-danger btn-block"),
                "Closing the day must remain a deliberate compact action, not a full-width banner");
        assertTrue(dashboard.contains("fx:id=\"performanceYAxis\""));
        assertTrue(dashboard.contains("fx:id=\"performanceEmptyBox\""));
        assertTrue(shell.contains("fx:id=\"topbarIdentity\""));
        assertTrue(tickets.contains("date-range-group"));
        assertTrue(photos.contains("filter-control-group"));
        assertTrue(debts.contains("debt-summary-strip"));
        assertTrue(debts.contains("fx:id=\"debtDetailPane\""));
        assertFalse(debts.contains("<SplitPane"),
                "Debt cards and movement history should not compete in a fixed split view");
    }

    private String readSafely(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new AssertionError("Could not inspect " + path, exception);
        }
    }
}
