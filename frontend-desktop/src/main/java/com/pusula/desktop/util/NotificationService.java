package com.pusula.desktop.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Central, branded feedback system for every desktop workflow. */
public final class NotificationService {
    public enum Kind { SUCCESS, INFO, WARNING, ERROR }

    private static final Map<Window, List<Popup>> ACTIVE_TOASTS = new WeakHashMap<>();
    private static final ButtonType CONFIRM = new ButtonType("Onayla", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL = new ButtonType("Vazgeç", ButtonBar.ButtonData.CANCEL_CLOSE);

    private NotificationService() {}

    public static void toast(Window owner, Kind kind, String title, String message) {
        runOnFxThread(() -> showToast(resolveOwner(owner), kind, title, message));
    }

    public static void modal(Window owner, Kind kind, String title, String message) {
        runOnFxThread(() -> createDialog(resolveOwner(owner), kind, title, message, false).show());
    }

    public static boolean confirm(Window owner, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Onay penceresi JavaFX uygulama iş parçacığında açılmalıdır.");
        }
        Dialog<ButtonType> dialog = createDialog(resolveOwner(owner), Kind.WARNING, title, message, true);
        return dialog.showAndWait().orElse(CANCEL) == CONFIRM;
    }

    private static Dialog<ButtonType> createDialog(Window owner, Kind kind, String title,
                                                    String message, boolean confirmation) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        if (owner != null) dialog.initOwner(owner);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(NotificationService.class.getResource("/css/styles.css").toExternalForm());
        pane.getStyleClass().addAll("modern-dialog", "modern-dialog-" + kind.name().toLowerCase());
        if (ThemeHelper.isDarkMode()) pane.getStyleClass().add("dark-theme");
        pane.setHeader(null);
        pane.setGraphic(null);

        Label icon = new Label(iconFor(kind));
        icon.getStyleClass().addAll("modern-dialog-icon", "feedback-icon-" + kind.name().toLowerCase());
        Label heading = new Label(safe(title));
        heading.getStyleClass().add("modern-dialog-title");
        Label body = new Label(safe(message));
        body.setWrapText(true);
        body.getStyleClass().add("modern-dialog-message");

        VBox copy = new VBox(7, heading, body);
        copy.setMaxWidth(Double.MAX_VALUE);
        HBox content = new HBox(16, icon, copy);
        content.setAlignment(Pos.TOP_LEFT);
        content.getStyleClass().add("modern-dialog-content");
        pane.setContent(content);
        pane.getButtonTypes().setAll(confirmation ? List.of(CANCEL, CONFIRM) : List.of(
                new ButtonType("Tamam", ButtonBar.ButtonData.OK_DONE)));

        if (confirmation) {
            String normalizedTitle = safe(title).toLowerCase(java.util.Locale.ROOT);
            boolean destructive = normalizedTitle.contains("sil") || normalizedTitle.contains("iptal")
                    || normalizedTitle.contains("kaldır") || normalizedTitle.contains("geri al");
            pane.lookupButton(CONFIRM).getStyleClass().add(destructive ? "button-danger" : "button-success");
            pane.lookupButton(CANCEL).getStyleClass().add("button-secondary");
        }
        return dialog;
    }

    private static void showToast(Window owner, Kind kind, String title, String message) {
        if (owner == null || !owner.isShowing()) return;

        Label icon = new Label(iconFor(kind));
        icon.getStyleClass().addAll("toast-icon", "feedback-icon-" + kind.name().toLowerCase());
        Label heading = new Label(safe(title));
        heading.getStyleClass().add("toast-title");
        Label body = new Label(safe(message));
        body.setWrapText(true);
        body.setMaxWidth(330);
        body.getStyleClass().add("toast-message");
        VBox copy = new VBox(3, heading, body);
        HBox card = new HBox(12, icon, copy);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().addAll("toast-card", "toast-" + kind.name().toLowerCase());
        card.getStylesheets().add(NotificationService.class.getResource("/css/styles.css").toExternalForm());

        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.getContent().add(card);
        card.applyCss();
        card.autosize();

        List<Popup> active = ACTIVE_TOASTS.computeIfAbsent(owner, ignored -> new ArrayList<>());
        active.removeIf(existing -> !existing.isShowing());
        double x = owner.getX() + owner.getWidth() - Math.max(card.prefWidth(-1), 380) - 24;
        double y = owner.getY() + 74 + (active.size() * 92);
        popup.show(owner, Math.max(owner.getX() + 16, x), y);
        active.add(popup);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(160), card);
        card.setOpacity(0);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        PauseTransition visible = new PauseTransition(Duration.seconds(kind == Kind.ERROR ? 6 : 4));
        visible.setOnFinished(event -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(220), card);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(done -> {
                popup.hide();
                active.remove(popup);
            });
            fadeOut.play();
        });
        visible.play();
    }

    private static Window resolveOwner(Window requested) {
        if (requested != null) return requested;
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(Window::isFocused)
                .findFirst()
                .orElseGet(() -> Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null));
    }

    private static String iconFor(Kind kind) {
        return switch (kind) {
            case SUCCESS -> "✓";
            case INFO -> "i";
            case WARNING -> "!";
            case ERROR -> "×";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }
}
