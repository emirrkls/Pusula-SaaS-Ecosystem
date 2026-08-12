package com.pusula.desktop.util;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.geometry.Rectangle2D;
import java.util.List;

/**
 * Central viewport policy for the desktop client. It keeps action text readable,
 * adds deterministic compact/narrow breakpoints and provides horizontal escape
 * scrolling instead of clipping content on smaller laptop displays or high DPI.
 */
public final class ResponsiveLayoutSupport {
    static final double COMPACT_BREAKPOINT = 1180;
    static final double NARROW_BREAKPOINT = 920;
    private static final String COMPACT_CLASS = "compact-ui";
    private static final String NARROW_CLASS = "narrow-ui";

    private ResponsiveLayoutSupport() {}

    public static void install(Scene scene) {
        if (scene == null || Boolean.TRUE.equals(scene.getProperties().get("pusula.responsive"))) {
            return;
        }
        scene.getProperties().put("pusula.responsive", Boolean.TRUE);
        ChangeListener<Number> listener = (observable, oldValue, newValue) -> applyBreakpoint(scene);
        scene.widthProperty().addListener(listener);
        scene.rootProperty().addListener((observable, oldRoot, newRoot) -> applyBreakpoint(scene));
        scene.windowProperty().addListener((observable, oldWindow, newWindow) -> {
            if (newWindow instanceof Stage stage) configureStage(stage, scene.getRoot());
        });
        applyBreakpoint(scene);
    }

    public static ScrollPane wrapPage(Parent view) {
        prepareControls(view);
        ScrollPane viewport = new ScrollPane(view);
        viewport.getStyleClass().add("responsive-page-viewport");
        viewport.setFitToWidth(true);
        viewport.setFitToHeight(true);
        viewport.setPannable(true);
        viewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        viewport.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        if (view instanceof Region region) {
            region.minWidthProperty().bind(Bindings.max(720, viewport.widthProperty().subtract(4)));
        }
        return viewport;
    }

    static void applyBreakpoint(Scene scene) {
        Parent root = scene.getRoot();
        if (root == null) return;
        toggle(root, COMPACT_CLASS, scene.getWidth() > 0 && scene.getWidth() < COMPACT_BREAKPOINT);
        toggle(root, NARROW_CLASS, scene.getWidth() > 0 && scene.getWidth() < NARROW_BREAKPOINT);
        Platform.runLater(() -> prepareControls(root));
    }

    static void prepareControls(Parent root) {
        if (root == null) return;
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof Button button
                    && !button.getStyleClass().contains("icon-button")
                    && !button.getStyleClass().contains("btn-whatsapp-icon")) {
                button.setMinWidth(Region.USE_PREF_SIZE);
                button.setWrapText(false);
            }
        }
        for (Node node : root.lookupAll(".table-view")) {
            if (node instanceof TableView<?> table) {
                table.setFixedCellSize(-1);
            }
        }
    }

    private static void toggle(Parent root, String styleClass, boolean enabled) {
        if (enabled && !root.getStyleClass().contains(styleClass)) {
            root.getStyleClass().add(styleClass);
        } else if (!enabled) {
            root.getStyleClass().remove(styleClass);
        }
    }

    private static void configureStage(Stage stage, Parent root) {
        boolean dialog = root != null && (root.getStyleClass().contains("dialog-root")
                || root.getStyleClass().contains("dialog-container"));
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(dialog ? 520 : 800, bounds.getWidth()));
        stage.setMinHeight(Math.min(dialog ? 420 : 560, bounds.getHeight()));
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            Rectangle2D activeBounds = boundsFor(stage);
            stage.setMinWidth(Math.min(dialog ? 520 : 800, activeBounds.getWidth()));
            stage.setMinHeight(Math.min(dialog ? 420 : 560, activeBounds.getHeight()));
            if (stage.getWidth() > activeBounds.getWidth()) stage.setWidth(activeBounds.getWidth());
            if (stage.getHeight() > activeBounds.getHeight()) stage.setHeight(activeBounds.getHeight());
            stage.setX(Math.max(activeBounds.getMinX(),
                    Math.min(stage.getX(), activeBounds.getMaxX() - stage.getWidth())));
            stage.setY(Math.max(activeBounds.getMinY(),
                    Math.min(stage.getY(), activeBounds.getMaxY() - stage.getHeight())));
        });
    }

    private static Rectangle2D boundsFor(Stage stage) {
        List<Screen> screens = Screen.getScreensForRectangle(
                stage.getX(), stage.getY(), Math.max(stage.getWidth(), 1), Math.max(stage.getHeight(), 1));
        return (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
    }
}
