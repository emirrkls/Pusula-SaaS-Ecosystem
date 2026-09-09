package com.pusula.desktop.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Central, branded feedback system for every desktop workflow. */
public final class NotificationService {
    private static final double MIN_DIALOG_WIDTH = 360;
    private static final double PREFERRED_DIALOG_WIDTH = 520;
    private static final double MAX_DIALOG_WIDTH = 620;
    private static final int LONG_MESSAGE_THRESHOLD = 420;

    public enum Kind { SUCCESS, INFO, WARNING, ERROR }

    private static final Map<Window, List<Popup>> ACTIVE_TOASTS = new WeakHashMap<>();

    private NotificationService() {}

    public static void toast(Window owner, Kind kind, String title, String message) {
        runOnFxThread(() -> showToast(resolveOwner(owner), kind, title, message));
    }

    public static void modal(Window owner, Kind kind, String title, String message) {
        runOnFxThread(() -> showModal(resolveOwner(owner), kind, title, message, false));
    }

    public static boolean confirm(Window owner, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Onay penceresi JavaFX uygulama iş parçacığında açılmalıdır.");
        }
        return showModal(resolveOwner(owner), Kind.WARNING, title, message, true);
    }

    public static Optional<String> promptSecret(Window owner, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Şifre penceresi JavaFX uygulama iş parçacığında açılmalıdır.");
        }
        Window resolvedOwner = resolveOwner(owner);
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(resolvedOwner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        if (resolvedOwner != null) stage.initOwner(resolvedOwner);
        StageHelper.setIcon(stage);
        stage.setTitle(safe(title));
        stage.setResizable(false);

        AtomicReference<String> result = new AtomicReference<>();
        Label icon = new Label("●");
        icon.getStyleClass().addAll("feedback-modal-icon", "feedback-icon-info");
        Label heading = new Label(safe(title));
        heading.getStyleClass().add("feedback-modal-title");
        heading.setWrapText(true);
        Label body = new Label(safe(message));
        body.getStyleClass().add("feedback-modal-message");
        body.setWrapText(true);
        PasswordField password = new PasswordField();
        password.setPromptText("Şifre");
        password.setAccessibleText("Şifre");
        password.getStyleClass().add("feedback-modal-field");
        VBox copy = new VBox(8, heading, body, password);
        copy.setMinWidth(0);
        copy.setMaxWidth(Double.MAX_VALUE);

        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("feedback-modal-close");
        closeButton.setAccessibleText("Pencereyi kapat");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(event -> stage.close());
        HBox content = new HBox(16, icon, copy, closeButton);
        HBox.setHgrow(copy, Priority.ALWAYS);
        content.setAlignment(Pos.TOP_LEFT);
        content.getStyleClass().add("feedback-modal-content");

        Button cancel = actionButton("Vazgeç", "button-secondary");
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> stage.close());
        Button submit = actionButton("Devam Et", "button-primary");
        submit.setDefaultButton(true);
        submit.disableProperty().bind(password.textProperty().isEmpty());
        submit.setOnAction(event -> {
            result.set(password.getText());
            stage.close();
        });
        HBox actions = new HBox(10, cancel, submit);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("feedback-modal-actions");

        VBox card = new VBox(content, actions);
        card.getStyleClass().addAll("feedback-modal-card", "feedback-modal-info");
        card.setMinWidth(MIN_DIALOG_WIDTH);
        card.setPrefWidth(preferredModalWidth(resolvedOwner));
        card.setMaxWidth(MAX_DIALOG_WIDTH);
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("feedback-modal-stage");
        if (ThemeHelper.isDarkMode()) root.getStyleClass().add("dark-theme");
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        ThemeHelper.ensureStylesheets(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setOnShown(event -> {
            fitModalToScreen(stage, resolvedOwner);
            Platform.runLater(password::requestFocus);
        });
        stage.showAndWait();
        return Optional.ofNullable(result.get());
    }

    /**
     * Shows feedback in a Pusula-owned surface instead of JavaFX DialogPane. DialogPane delegates
     * action placement to the platform ButtonBar, which made actions cling to outer corners and
     * produced different layouts on different Windows display scales.
     */
    private static boolean showModal(Window owner, Kind kind, String title,
                                     String message, boolean confirmation) {
        String displayMessage = kind == Kind.ERROR ? ApiErrorHelper.userFacing(message) : safe(message);
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        StageHelper.setIcon(stage);
        stage.setTitle(safe(title));
        stage.setResizable(false);

        AtomicBoolean confirmed = new AtomicBoolean(false);

        Label icon = new Label(iconFor(kind));
        icon.getStyleClass().addAll("feedback-modal-icon", "feedback-icon-" + kind.name().toLowerCase());
        Label heading = new Label(safe(title));
        heading.getStyleClass().add("feedback-modal-title");
        heading.setWrapText(true);
        heading.setTextOverrun(OverrunStyle.CLIP);
        heading.setMinHeight(Region.USE_PREF_SIZE);
        heading.setMaxWidth(Double.MAX_VALUE);
        Label body = new Label(displayMessage);
        body.setWrapText(true);
        body.setMinWidth(0);
        body.setMinHeight(Region.USE_PREF_SIZE);
        body.setMaxWidth(Double.MAX_VALUE);
        body.setTextOverrun(OverrunStyle.CLIP);
        body.getStyleClass().add("feedback-modal-message");

        VBox copy = new VBox(7);
        copy.getChildren().add(heading);
        if (displayMessage.length() > LONG_MESSAGE_THRESHOLD || displayMessage.contains("\n")) {
            ScrollPane messageViewport = new ScrollPane(body);
            messageViewport.setFitToWidth(true);
            messageViewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            messageViewport.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            messageViewport.setMaxHeight(240);
            messageViewport.getStyleClass().add("feedback-modal-scroll");
            copy.getChildren().add(messageViewport);
        } else {
            copy.getChildren().add(body);
        }
        copy.setMinWidth(0);
        copy.setMaxWidth(Double.MAX_VALUE);
        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("feedback-modal-close");
        closeButton.setAccessibleText("Pencereyi kapat");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(event -> stage.close());

        HBox content = new HBox(16, icon, copy, closeButton);
        HBox.setHgrow(copy, Priority.ALWAYS);
        content.setAlignment(Pos.TOP_LEFT);
        content.getStyleClass().add("feedback-modal-content");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("feedback-modal-actions");

        if (confirmation) {
            String normalizedTitle = safe(title).toLowerCase(java.util.Locale.ROOT);
            boolean destructive = normalizedTitle.contains("sil") || normalizedTitle.contains("iptal")
                    || normalizedTitle.contains("kaldır") || normalizedTitle.contains("geri al");
            Button cancelButton = actionButton("Vazgeç", "button-secondary");
            cancelButton.setCancelButton(true);
            cancelButton.setOnAction(event -> stage.close());
            Button confirmButton = actionButton("Onayla", destructive ? "button-danger" : "button-success");
            confirmButton.setDefaultButton(true);
            confirmButton.setOnAction(event -> {
                confirmed.set(true);
                stage.close();
            });
            actions.getChildren().addAll(cancelButton, confirmButton);
        } else {
            Button okButton = actionButton("Tamam", "button-primary");
            okButton.setDefaultButton(true);
            okButton.setOnAction(event -> stage.close());
            actions.getChildren().add(okButton);
        }

        VBox card = new VBox(content, actions);
        card.getStyleClass().addAll("feedback-modal-card", "feedback-modal-" + kind.name().toLowerCase());
        card.setMinWidth(MIN_DIALOG_WIDTH);
        card.setPrefWidth(preferredModalWidth(owner));
        card.setMaxWidth(MAX_DIALOG_WIDTH);

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("feedback-modal-stage");
        if (ThemeHelper.isDarkMode()) root.getStyleClass().add("dark-theme");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        ThemeHelper.ensureStylesheets(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setOnShown(event -> fitModalToScreen(stage, owner));
        stage.showAndWait();
        return confirmed.get();
    }

    private static double preferredModalWidth(Window owner) {
        if (owner == null || owner.getWidth() <= 0) return PREFERRED_DIALOG_WIDTH;
        return Math.min(MAX_DIALOG_WIDTH, Math.max(MIN_DIALOG_WIDTH, owner.getWidth() * 0.46));
    }

    private static Button actionButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setMinWidth(112);
        button.setMinHeight(40);
        button.setTextOverrun(OverrunStyle.CLIP);
        button.setWrapText(false);
        String accessible = switch (text) {
            case "Onayla" -> "İşlemi onayla";
            case "Vazgeç" -> "İşlemden vazgeç";
            default -> text;
        };
        button.setAccessibleText(accessible);
        return button;
    }

    private static void fitModalToScreen(Stage stage, Window owner) {
        if (stage.getScene() == null || stage.getScene().getRoot() == null) return;
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        Rectangle2D bounds = boundsFor(owner == null ? stage : owner);
        double availableWidth = Math.max(360, bounds.getWidth() - 48);
        double availableHeight = Math.max(300, bounds.getHeight() - 48);
        stage.sizeToScene();
        stage.setWidth(Math.min(availableWidth, stage.getWidth()));
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
