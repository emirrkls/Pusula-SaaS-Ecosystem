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
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.geometry.Rectangle2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Central, branded feedback system for every desktop workflow. */
public final class NotificationService {
    private static final double MIN_DIALOG_WIDTH = 400;
    private static final double PREFERRED_DIALOG_WIDTH = 520;
    private static final double MAX_DIALOG_WIDTH = 640;
    private static final int LONG_MESSAGE_THRESHOLD = 420;

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
        String displayMessage = kind == Kind.ERROR ? ApiErrorHelper.userFacing(message) : safe(message);
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
        heading.setWrapText(true);
        heading.setTextOverrun(OverrunStyle.CLIP);
        heading.setMinHeight(Region.USE_PREF_SIZE);
        heading.setMaxWidth(Double.MAX_VALUE);
        Label body = new Label(displayMessage);
        body.setWrapText(true);
        body.setMinWidth(0);
        body.setMinHeight(Region.USE_PREF_SIZE);
        body.setPrefWidth(420);
        body.setMaxWidth(Double.MAX_VALUE);
        body.setTextOverrun(OverrunStyle.CLIP);
        body.getStyleClass().add("modern-dialog-message");

        VBox copy = new VBox(7);
        copy.getChildren().add(heading);
        if (displayMessage.length() > LONG_MESSAGE_THRESHOLD || displayMessage.contains("\n")) {
            ScrollPane messageViewport = new ScrollPane(body);
            messageViewport.setFitToWidth(true);
            messageViewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            messageViewport.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            messageViewport.setMaxHeight(240);
            messageViewport.getStyleClass().add("modern-dialog-scroll");
            copy.getChildren().add(messageViewport);
        } else {
            copy.getChildren().add(body);
        }
        copy.setMinWidth(0);
        copy.setMaxWidth(Double.MAX_VALUE);
        HBox content = new HBox(16, icon, copy);
        HBox.setHgrow(copy, Priority.ALWAYS);
        content.setAlignment(Pos.TOP_LEFT);
        content.getStyleClass().add("modern-dialog-content");
        pane.setContent(content);
        pane.setMinWidth(MIN_DIALOG_WIDTH);
        pane.setPrefWidth(PREFERRED_DIALOG_WIDTH);
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.getButtonTypes().setAll(confirmation ? List.of(CANCEL, CONFIRM) : List.of(
                new ButtonType("Tamam", ButtonBar.ButtonData.OK_DONE)));

        pane.getButtonTypes().forEach(type -> {
            if (pane.lookupButton(type) instanceof javafx.scene.control.Button button) {
                button.setMinWidth(112);
                button.setPrefWidth(Region.USE_COMPUTED_SIZE);
                button.setTextOverrun(OverrunStyle.CLIP);
                button.setWrapText(false);
            }
        });

        if (confirmation) {
            String normalizedTitle = safe(title).toLowerCase(java.util.Locale.ROOT);
            boolean destructive = normalizedTitle.contains("sil") || normalizedTitle.contains("iptal")
                    || normalizedTitle.contains("kaldır") || normalizedTitle.contains("geri al");
            pane.lookupButton(CONFIRM).getStyleClass().add(destructive ? "button-danger" : "button-success");
            pane.lookupButton(CANCEL).getStyleClass().add("button-secondary");
        }
        dialog.setOnShown(event -> fitDialogToScreen(dialog, owner));
        return dialog;
    }

    private static void fitDialogToScreen(Dialog<?> dialog, Window owner) {
        DialogPane pane = dialog.getDialogPane();
        pane.applyCss();
        pane.layout();
        if (!(pane.getScene().getWindow() instanceof Stage stage)) return;

        Rectangle2D bounds = boundsFor(owner == null ? stage : owner);
        double availableWidth = Math.max(360, bounds.getWidth() - 48);
        double availableHeight = Math.max(300, bounds.getHeight() - 48);
        double preferredWidth = Math.min(MAX_DIALOG_WIDTH,
                Math.max(MIN_DIALOG_WIDTH, owner == null ? PREFERRED_DIALOG_WIDTH : owner.getWidth() * 0.48));

        stage.sizeToScene();
        stage.setMinWidth(Math.min(MIN_DIALOG_WIDTH, availableWidth));
        stage.setWidth(Math.min(availableWidth, Math.max(preferredWidth, stage.getWidth())));
        stage.setMaxWidth(availableWidth);
        stage.setMaxHeight(availableHeight);
        if (stage.getHeight() > availableHeight) stage.setHeight(availableHeight);
        double targetX = owner == null
                ? bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2
                : owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
        double targetY = owner == null
                ? bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2
                : owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
        stage.setX(Math.max(bounds.getMinX(), Math.min(targetX, bounds.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(bounds.getMinY(), Math.min(targetY, bounds.getMaxY() - stage.getHeight())));
    }

    private static Rectangle2D boundsFor(Window window) {
        List<Screen> screens = Screen.getScreensForRectangle(
                window.getX(), window.getY(), Math.max(window.getWidth(), 1), Math.max(window.getHeight(), 1));
        return (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
    }

    private static void showToast(Window owner, Kind kind, String title, String message) {
        if (owner == null || !owner.isShowing()) return;

        Label icon = new Label(iconFor(kind));
        icon.getStyleClass().addAll("toast-icon", "feedback-icon-" + kind.name().toLowerCase());
        Label heading = new Label(safe(title));
        heading.getStyleClass().add("toast-title");
        heading.setWrapText(true);
        heading.setMinHeight(Region.USE_PREF_SIZE);
        Label body = new Label(safe(message));
        body.setWrapText(true);
        body.setMinHeight(Region.USE_PREF_SIZE);
        body.setMaxWidth(340);
        body.getStyleClass().add("toast-message");
        VBox copy = new VBox(3, heading, body);
        copy.setMinWidth(0);
        copy.setMaxWidth(Double.MAX_VALUE);
        HBox card = new HBox(12, icon, copy);
        HBox.setHgrow(copy, Priority.ALWAYS);
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
        double targetWidth = Math.max(300, Math.min(420, owner.getWidth() - 48));
        card.setPrefWidth(targetWidth);
        double x = owner.getX() + owner.getWidth() - targetWidth - 24;
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
