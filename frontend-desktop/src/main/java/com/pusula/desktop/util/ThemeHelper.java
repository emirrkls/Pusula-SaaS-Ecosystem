package com.pusula.desktop.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * Central theme management — AtlantaFX base + Pusula brand CSS overlay + dark mode class.
 */
public final class ThemeHelper {

    private static final String STYLES = "/css/styles.css";
    private static final String TABLE_OVERRIDE = "/css/table-override.css";

    private ThemeHelper() {
    }

    /** Shared sizing contract for secondary desktop workspaces. */
    public enum DialogProfile {
        COMPACT(460, 360, 400, 280),
        FORM(640, 580, 520, 420),
        WORKFLOW(820, 720, 620, 500),
        DETAIL(1120, 780, 760, 540);

        private final double width;
        private final double height;
        private final double minWidth;
        private final double minHeight;

        DialogProfile(double width, double height, double minWidth, double minHeight) {
            this.width = width;
            this.height = height;
            this.minWidth = minWidth;
            this.minHeight = minHeight;
        }
    }

    public static void applyGlobalTheme(boolean dark) {
        Application.setUserAgentStylesheet(
                dark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
    }

    public static void applyToScene(Scene scene, boolean dark) {
        applyGlobalTheme(dark);
        ensureStylesheets(scene);
        toggleDarkClass(scene.getRoot(), dark);
        ResponsiveLayoutSupport.install(scene);
    }

    public static void ensureStylesheets(Scene scene) {
        if (scene == null) {
            return;
        }
        String stylesUrl = ThemeHelper.class.getResource(STYLES).toExternalForm();
        String tableUrl = ThemeHelper.class.getResource(TABLE_OVERRIDE).toExternalForm();
        if (!scene.getStylesheets().contains(stylesUrl)) {
            scene.getStylesheets().add(stylesUrl);
        }
        if (!scene.getStylesheets().contains(tableUrl)) {
            scene.getStylesheets().add(tableUrl);
        }
    }

    public static void toggleDarkClass(Parent root, boolean dark) {
        if (root == null) {
            return;
        }
        if (dark) {
            if (!root.getStyleClass().contains("dark-theme")) {
                root.getStyleClass().add("dark-theme");
            }
        } else {
            root.getStyleClass().remove("dark-theme");
        }
    }

    public static boolean isDarkMode() {
        return PreferencesHelper.isDarkMode();
    }

    /** Applies the same branded shell to programmatically constructed form dialogs. */
    public static void applyToDialog(Dialog<?> dialog, Window owner) {
        applyToDialog(dialog, owner, null);
    }

    public static void applyToDialog(Dialog<?> dialog, Window owner, DialogProfile profile) {
        if (dialog == null) return;
        if (owner != null && dialog.getOwner() == null) dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        if (profile != null) pane.getProperties().put("pusula.dialog.profile", profile);
        String stylesUrl = ThemeHelper.class.getResource(STYLES).toExternalForm();
        String tableUrl = ThemeHelper.class.getResource(TABLE_OVERRIDE).toExternalForm();
        if (!pane.getStylesheets().contains(stylesUrl)) pane.getStylesheets().add(stylesUrl);
        if (!pane.getStylesheets().contains(tableUrl)) pane.getStylesheets().add(tableUrl);
        if (!pane.getStyleClass().contains("modern-form-dialog")) pane.getStyleClass().add("modern-form-dialog");
        if (isDarkMode() && !pane.getStyleClass().contains("dark-theme")) pane.getStyleClass().add("dark-theme");
        dialog.setOnShown(event -> prepareVisibleDialog(dialog, owner));
    }

    /** Normalizes wrapping, action sizes and screen bounds after a dialog becomes visible. */
    public static void prepareVisibleDialog(Dialog<?> dialog, Window owner) {
        DialogPane pane = dialog.getDialogPane();
        pane.applyCss();

        if (pane.lookup(".header-panel .label") instanceof Label header) {
            header.setWrapText(true);
            header.setTextOverrun(OverrunStyle.CLIP);
            header.setMinHeight(Region.USE_PREF_SIZE);
            header.setMaxWidth(Double.MAX_VALUE);
        }
        for (var node : pane.lookupAll(".button-bar .button")) {
            if (node instanceof Button button) {
                button.setMinWidth(Region.USE_PREF_SIZE);
                button.setTextOverrun(OverrunStyle.CLIP);
                button.setWrapText(false);
                button.setAccessibleText(button.getText());
            }
        }
        for (var node : pane.lookupAll(".label")) {
            if (node instanceof Label label && label.getParent() instanceof GridPane
                    && (GridPane.getColumnIndex(label) == null || GridPane.getColumnIndex(label) == 0)) {
                label.setWrapText(false);
                label.setMinWidth(Region.USE_PREF_SIZE);
                label.setTextOverrun(OverrunStyle.CLIP);
            }
        }
        for (var buttonType : pane.getButtonTypes()) {
            if (pane.lookupButton(buttonType) instanceof Button button) {
                styleDialogAction(button, buttonType, dialog.getTitle());
            }
        }

        if (!(pane.getScene().getWindow() instanceof Stage stage)) return;
        Rectangle2D bounds = boundsFor(owner == null ? stage : owner);
        double maxWidth = Math.max(420, bounds.getWidth() - 48);
        double maxHeight = Math.max(320, bounds.getHeight() - 48);
        stage.sizeToScene();
        Object configuredProfile = pane.getProperties().get("pusula.dialog.profile");
        if (configuredProfile instanceof DialogProfile profile) {
            stage.setMinWidth(Math.min(profile.minWidth, maxWidth));
            stage.setMinHeight(Math.min(profile.minHeight, maxHeight));
            stage.setWidth(Math.min(profile.width, maxWidth));
            stage.setHeight(Math.min(profile.height, maxHeight));
            stage.setResizable(true);
        }
        stage.setMaxWidth(maxWidth);
        stage.setMaxHeight(maxHeight);
        if (stage.getWidth() > maxWidth) stage.setWidth(maxWidth);
        if (stage.getHeight() > maxHeight) stage.setHeight(maxHeight);
        stage.setX(Math.max(bounds.getMinX(),
                Math.min(stage.getX(), bounds.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(bounds.getMinY(),
                Math.min(stage.getY(), bounds.getMaxY() - stage.getHeight())));
    }

    /** Applies the same adaptive profile to FXML-backed stages. */
    public static void configureDialogStage(Stage stage, Window owner, DialogProfile profile) {
        if (stage == null || profile == null) return;
        if (owner != null && stage.getOwner() == null) stage.initOwner(owner);
        if (stage.getScene() != null && stage.getScene().getRoot() instanceof Region root) {
            root.setMinWidth(0);
            root.setMinHeight(0);
        }
        Rectangle2D bounds = boundsFor(owner == null ? stage : owner);
        double maxWidth = Math.max(420, bounds.getWidth() - 48);
        double maxHeight = Math.max(320, bounds.getHeight() - 48);
        stage.setMinWidth(Math.min(profile.minWidth, maxWidth));
        stage.setMinHeight(Math.min(profile.minHeight, maxHeight));
        stage.setMaxWidth(maxWidth);
        stage.setMaxHeight(maxHeight);
        stage.setWidth(Math.min(profile.width, maxWidth));
        stage.setHeight(Math.min(profile.height, maxHeight));
        stage.setResizable(true);
        stage.setOnShown(event -> {
            Rectangle2D activeBounds = boundsFor(owner == null ? stage : owner);
            double centeredX = owner == null
                    ? activeBounds.getMinX() + (activeBounds.getWidth() - stage.getWidth()) / 2
                    : owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
            double centeredY = owner == null
                    ? activeBounds.getMinY() + (activeBounds.getHeight() - stage.getHeight()) / 2
                    : owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
            stage.setX(Math.max(activeBounds.getMinX(),
                    Math.min(centeredX, activeBounds.getMaxX() - stage.getWidth())));
            stage.setY(Math.max(activeBounds.getMinY(),
                    Math.min(centeredY, activeBounds.getMaxY() - stage.getHeight())));
        });
    }

    private static void styleDialogAction(Button button, javafx.scene.control.ButtonType type, String title) {
        if (button == null || type == null) return;
        button.getStyleClass().removeAll("button-primary", "button-secondary", "button-danger",
                "btn-primary", "btn-secondary", "btn-danger");
        var data = type.getButtonData();
        if (data == null || data.isCancelButton()) {
            button.getStyleClass().add("button-secondary");
            return;
        }
        String normalized = ((title == null ? "" : title) + " "
                + (button.getText() == null ? "" : button.getText())).toLowerCase(java.util.Locale.ROOT);
        boolean destructive = normalized.contains("sil") || normalized.contains("iptal")
                || normalized.contains("kapat") || normalized.contains("reddet")
                || normalized.contains("geri çek");
        button.getStyleClass().add(destructive ? "button-danger" : "button-primary");
    }

    private static Rectangle2D boundsFor(Window window) {
        List<Screen> screens = Screen.getScreensForRectangle(
                window.getX(), window.getY(), Math.max(window.getWidth(), 1), Math.max(window.getHeight(), 1));
        return (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
    }

    /** Scene for modal dialogs — stylesheets + current dark/light preference. */
    public static Scene createDialogScene(Parent root) {
        Scene scene = new Scene(root);
        ensureStylesheets(scene);
        toggleDarkClass(root, isDarkMode());
        ResponsiveLayoutSupport.install(scene);
        return scene;
    }

    public static Scene createDialogScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        ensureStylesheets(scene);
        toggleDarkClass(root, isDarkMode());
        ResponsiveLayoutSupport.install(scene);
        return scene;
    }
}
