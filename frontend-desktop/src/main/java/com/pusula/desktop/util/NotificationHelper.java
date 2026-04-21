package com.pusula.desktop.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * NotificationHelper - Modern toast/snackbar notification system.
 * Replaces old-school Alert popups with sleek, non-blocking notifications.
 */
public class NotificationHelper {

    private static Stage currentToast = null;
    private static final int TOAST_DURATION_MS = 3000;
    private static final int ANIMATION_DURATION_MS = 200;

    // =========================================
    // SUCCESS NOTIFICATION (Green)
    // =========================================

    /**
     * Shows a success toast notification.
     * 
     * @param message The message to display
     */
    public static void showSuccess(String message) {
        showToast(message, ToastType.SUCCESS);
    }

    // =========================================
    // ERROR NOTIFICATION (Red)
    // =========================================

    /**
     * Shows an error toast notification.
     * 
     * @param message The message to display
     */
    public static void showError(String message) {
        showToast(message, ToastType.ERROR);
    }

    // =========================================
    // WARNING NOTIFICATION (Orange)
    // =========================================

    /**
     * Shows a warning toast notification.
     * 
     * @param message The message to display
     */
    public static void showWarning(String message) {
        showToast(message, ToastType.WARNING);
    }

    // =========================================
    // INFO NOTIFICATION (Blue)
    // =========================================

    /**
     * Shows an info toast notification.
     * 
     * @param message The message to display
     */
    public static void showInfo(String message) {
        showToast(message, ToastType.INFO);
    }

    // =========================================
    // CORE TOAST IMPLEMENTATION
    // =========================================

    private static void showToast(String message, ToastType type) {
        Platform.runLater(() -> {
            // Close any existing toast
            if (currentToast != null && currentToast.isShowing()) {
                currentToast.close();
            }

            // Create toast content
            Label icon = new Label(type.icon);
            icon.setStyle("-fx-font-size: 18px;");

            Label messageLabel = new Label(message);
            messageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: 500;");

            HBox toastBox = new HBox(12, icon, messageLabel);
            toastBox.setAlignment(Pos.CENTER_LEFT);
            toastBox.setPadding(new Insets(14, 24, 14, 20));
            toastBox.setStyle(
                    "-fx-background-color: " + type.bgColor + ";" +
                            "-fx-background-radius: 10px;" +
                            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 15, 0, 0, 5);");

            StackPane root = new StackPane(toastBox);
            root.setStyle("-fx-background-color: transparent;");
            root.setPadding(new Insets(10));

            // Create stage
            Stage toastStage = new Stage();
            toastStage.initStyle(StageStyle.TRANSPARENT);
            toastStage.setAlwaysOnTop(true);

            Scene scene = new Scene(root);
            scene.setFill(null);
            toastStage.setScene(scene);

            // Position at bottom-right of screen
            javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
            javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();

            toastStage.setX(bounds.getMaxX() - 350);
            toastStage.setY(bounds.getMaxY() - 100);

            // Store reference
            currentToast = toastStage;

            // Initial state (for animation)
            toastBox.setOpacity(0);
            toastBox.setTranslateX(50);

            toastStage.show();

            // Slide in animation
            FadeTransition fadeIn = new FadeTransition(Duration.millis(ANIMATION_DURATION_MS), toastBox);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(ANIMATION_DURATION_MS), toastBox);
            slideIn.setFromX(50);
            slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition showAnimation = new ParallelTransition(fadeIn, slideIn);

            // Fade out animation (after delay)
            PauseTransition delay = new PauseTransition(Duration.millis(TOAST_DURATION_MS));

            FadeTransition fadeOut = new FadeTransition(Duration.millis(ANIMATION_DURATION_MS), toastBox);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(ANIMATION_DURATION_MS), toastBox);
            slideOut.setFromX(0);
            slideOut.setToX(50);

            ParallelTransition hideAnimation = new ParallelTransition(fadeOut, slideOut);
            hideAnimation.setOnFinished(e -> toastStage.close());

            // Chain animations: show -> wait -> hide
            SequentialTransition sequence = new SequentialTransition(showAnimation, delay, hideAnimation);
            sequence.play();
        });
    }

    // =========================================
    // TOAST TYPES ENUM
    // =========================================

    private enum ToastType {
        SUCCESS("#10B981", "✓"), // Green
        ERROR("#EF4444", "✕"), // Red
        WARNING("#F59E0B", "⚠"), // Orange
        INFO("#3B82F6", "ℹ"); // Blue

        final String bgColor;
        final String icon;

        ToastType(String bgColor, String icon) {
            this.bgColor = bgColor;
            this.icon = icon;
        }
    }
}
