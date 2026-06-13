package com.pusula.desktop.controller;

import com.pusula.desktop.api.AuthApi;
import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.AuthResponse;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.util.KeyboardShortcutHelper;
import com.pusula.desktop.util.PreferencesHelper;
import com.pusula.desktop.util.SessionManager;
import com.pusula.desktop.util.ThemeHelper;
import com.pusula.desktop.util.ViewTransitionHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.Map;

public class MainDashboardController {
    @FXML
    private StackPane contentArea;
    @FXML
    private Label userLabel;
    @FXML
    private Label userRoleLabel;
    @FXML
    private Label userAvatarLabel;
    @FXML
    private Label pageTitleLabel;
    @FXML
    private Label pageSubtitleLabel;
    @FXML
    private Button btnThemeToggle;
    @FXML
    private Button navDashboard;
    @FXML
    private Button navTickets;
    @FXML
    private Button navInventory;
    @FXML
    private Button navCustomers;
    @FXML
    private Button btnFinance;
    @FXML
    private Button btnSettings;
    @FXML
    private Button btnActivityLog;
    @FXML
    private Button btnCommercial;
    @FXML
    private Button btnProposals;

    private boolean isDark = false;
    private Button activeNavButton;

    // Load saved theme preference
    {
        isDark = PreferencesHelper.isDarkMode();
        ThemeHelper.applyGlobalTheme(isDark);
    }

    // Screensaver fields
    private static final int IDLE_TIMEOUT_SECONDS = 10; // 10 seconds for testing
    private PauseTransition idleTimer;
    private Parent screensaverView;
    private ScreensaverController screensaverController;

    @FXML
    public void initialize() {
        setupUserProfile();
        setupNavigationIcons();
        setupThemeToggleIcon();

        // Hide Finance, Settings and Activity Log for Technicians
        if (SessionManager.isTechnician()) {
            hideButton(btnFinance);
            hideButton(btnSettings);
            hideButton(btnActivityLog);
        }

        contentArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                ThemeHelper.applyToScene(newScene, isDark);
            }
        });

        setupIdleTimer();
        showDashboard();
        Platform.runLater(this::registerKeyboardShortcuts);
    }

    private void hideButton(Button button) {
        if (button != null) {
            button.setVisible(false);
            button.setManaged(false);
        }
    }

    private void setupUserProfile() {
        String username = SessionManager.getUsername();
        if (username != null) {
            userLabel.setText(username);
            userAvatarLabel.setText(username.substring(0, 1).toUpperCase());
        }
        String role = SessionManager.getUserRole();
        if (role != null) {
            userRoleLabel.setText(switch (role) {
                case "ADMIN" -> "Yönetici";
                case "TECHNICIAN" -> "Teknisyen";
                default -> role;
            });
        }
    }

    private void setupNavigationIcons() {
        setNavIcon(navDashboard, MaterialDesignV.VIEW_DASHBOARD);
        setNavIcon(navTickets, MaterialDesignC.CLIPBOARD_TEXT);
        setNavIcon(navInventory, MaterialDesignP.PACKAGE_VARIANT);
        setNavIcon(navCustomers, MaterialDesignA.ACCOUNT_MULTIPLE);
        setNavIcon(btnCommercial, MaterialDesignA.AIR_CONDITIONER);
        setNavIcon(btnProposals, MaterialDesignF.FILE_DOCUMENT);
        setNavIcon(btnFinance, MaterialDesignC.CASH_MULTIPLE);
        setNavIcon(btnSettings, MaterialDesignC.COG);
        setNavIcon(btnActivityLog, MaterialDesignH.HISTORY);
    }

    private void setNavIcon(Button button, org.kordamp.ikonli.Ikon icon) {
        if (button == null) {
            return;
        }
        FontIcon fontIcon = FontIcon.of(icon, 18);
        button.setGraphic(fontIcon);
    }

    private void setupThemeToggleIcon() {
        updateThemeToggleIcon();
    }

    private void updateThemeToggleIcon() {
        org.kordamp.ikonli.Ikon iconCode = isDark ? MaterialDesignW.WEATHER_SUNNY : MaterialDesignW.WEATHER_NIGHT;
        FontIcon icon = FontIcon.of(iconCode, 18);
        btnThemeToggle.setGraphic(icon);
        btnThemeToggle.setTooltip(new Tooltip(isDark ? "Açık tema" : "Koyu tema"));
    }

    private void setActiveNav(Button button) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("active");
        }
        activeNavButton = button;
        if (activeNavButton != null && !activeNavButton.getStyleClass().contains("active")) {
            activeNavButton.getStyleClass().add("active");
        }
    }

    private void loadContent(Parent view, String title, String subtitle, Button navButton) {
        setActiveNav(navButton);
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle != null ? subtitle : "");
        ViewTransitionHelper.swapContent(contentArea, view);
    }

    private java.util.ResourceBundle bundle() {
        return java.util.ResourceBundle.getBundle("i18n.messages",
                new java.util.Locale("tr", "TR"), new UTF8Control());
    }

    /**
     * Registers global keyboard shortcuts for navigation.
     * Ctrl+D: Dashboard, Ctrl+S: Service, Ctrl+I: Inventory, etc.
     */
    private void registerKeyboardShortcuts() {
        Scene scene = contentArea.getScene();
        if (scene == null)
            return;

        KeyboardShortcutHelper.registerGlobalShortcuts(scene, new KeyboardShortcutHelper.ShortcutHandler() {
            @Override
            public void onDashboard() {
                showDashboard();
            }

            @Override
            public void onServiceManagement() {
                showServiceManagement();
            }

            @Override
            public void onInventory() {
                showInventory();
            }

            @Override
            public void onFinance() {
                if (!SessionManager.isTechnician()) {
                    showFinance();
                }
            }

            @Override
            public void onCustomers() {
                showCustomers();
            }

            @Override
            public void onLogout() {
                handleLogout();
            }

            @Override
            public void onRefresh() {
                showDashboard(); // Reload current view
            }

            @Override
            public void onToggleFullscreen() {
                javafx.stage.Stage stage = (javafx.stage.Stage) contentArea.getScene().getWindow();
                stage.setFullScreen(!stage.isFullScreen());
            }

            @Override
            public void onToggleTheme() {
                toggleTheme();
            }
        });
    }

    @FXML
    private void toggleTheme() {
        // Get the root scene node for animation
        Scene scene = contentArea.getScene();
        if (scene == null)
            return;

        Parent root = scene.getRoot();

        // Phase 1: Fade Out (250ms)
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(250), root);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(event -> {
            isDark = !isDark;
            ThemeHelper.applyToScene(scene, isDark);
            PreferencesHelper.setDarkMode(isDark);
            updateThemeToggleIcon();

            javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(250), root);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });

        // Start the transition sequence
        fadeOut.play();
    }

    @FXML
    public void showDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dashboard.fxml"), bundle());
            Parent view = loader.load();
            DashboardController controller = loader.getController();
            controller.setMainController(this);
            loadContent(view, bundle().getString("menu.dashboard"), "Operasyon özeti", navDashboard);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showServiceManagement() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/service_tickets.fxml"), bundle());
            Parent view = loader.load();
            loadContent(view, bundle().getString("tickets.title"), "Servis fişlerini yönetin", navTickets);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showInventory() {
        showInventory(false);
    }

    public void showInventory(boolean filterCritical) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/inventory.fxml"), bundle());
            Parent view = loader.load();
            if (filterCritical) {
                InventoryController controller = loader.getController();
                controller.filterCriticalStocks();
            }
            loadContent(view, bundle().getString("menu.inventory"), "Stok ve araç envanteri", navInventory);
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, contentArea.getScene().getWindow(),
                    "Hata", "Envanter yüklenemedi: " + e.getMessage());
        }
    }

    @FXML
    public void showCustomers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/customers.fxml"), bundle());
            Parent view = loader.load();
            loadContent(view, bundle().getString("menu.customers"), "Müşteri kayıtları", navCustomers);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showCommercialDevices() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/commercial_device_view.fxml"), bundle());
            Parent view = loader.load();
            loadContent(view, bundle().getString("menu.commercial"), "Ticari cihaz satışları", btnCommercial);
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, contentArea.getScene().getWindow(),
                    "Hata", "Cihaz satış yüklenemedi: " + e.getMessage());
        }
    }

    @FXML
    public void showFinance() {
        // Double authentication for Finance
        if (SessionManager.isAdmin()) {
            showPasswordVerificationDialog(() -> navigateToFinance());
        }
    }

    @FXML
    public void showProposals() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/proposal_view.fxml"), bundle());
            Parent content = loader.load();
            loadContent(content, "Teklifler", "Teklif yönetimi", btnProposals);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showSettings() {
        // Double authentication for Settings
        if (SessionManager.isAdmin()) {
            showPasswordVerificationDialog(() -> navigateToSettings());
        }
    }

    @FXML
    private void showActivityLog() {
        // Double authentication for Activity Log (Admin only)
        if (SessionManager.isAdmin()) {
            showPasswordVerificationDialog(() -> navigateToActivityLog());
        } else {
            AlertHelper.showAlert(Alert.AlertType.WARNING, contentArea.getScene().getWindow(),
                    "Erişim Reddedildi", "Bu işlem için yönetici yetkisi gereklidir.");
        }
    }

    private void navigateToFinance() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/finance_view.fxml"), bundle());
            Parent view = loader.load();
            loadContent(view, bundle().getString("menu.finance"), "Gelir, gider ve raporlar", btnFinance);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void navigateToSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/settings_view.fxml"), bundle());
            Parent view = loader.load();
            loadContent(view, bundle().getString("menu.settings"), "Uygulama ayarları", btnSettings);
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, contentArea.getScene().getWindow(),
                    "Hata", "Ayarlar yüklenemedi: " + e.getMessage());
        }
    }

    private void navigateToActivityLog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/activity_log.fxml"), bundle());
            Parent view = loader.load();
            loadContent(view, bundle().getString("menu.activity_log"), "Sistem aktivite kayıtları", btnActivityLog);
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, contentArea.getScene().getWindow(),
                    "Hata", "Aktivite geçmişi yüklenemedi: " + e.getMessage());
        }
    }

    private void showPasswordVerificationDialog(Runnable onSuccess) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Şifre Doğrulama");
        dialog.setHeaderText("Bu alana erişmek için şifrenizi giriniz");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Şifre");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Şifre:"), 0, 0);
        grid.add(passwordField, 1, 0);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(password -> {
            verifyPassword(password, onSuccess);
        });
    }

    private void verifyPassword(String password, Runnable onSuccess) {
        AuthRequest authRequest = new AuthRequest(SessionManager.getUsername(), password);
        AuthApi authApi = RetrofitClient.getClient().create(AuthApi.class);

        authApi.verifyPassword(authRequest).enqueue(new Callback<Map<String, Boolean>>() {
            @Override
            public void onResponse(Call<Map<String, Boolean>> call, Response<Map<String, Boolean>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null &&
                            Boolean.TRUE.equals(response.body().get("valid"))) {
                        // Password is correct, execute success callback
                        onSuccess.run();
                    } else {
                        // Password is incorrect
                        AlertHelper.showAlert(Alert.AlertType.ERROR,
                                contentArea.getScene().getWindow(),
                                "Hata",
                                "Erişim Reddedildi: Şifre yanlış.");
                    }
                });
            }

            @Override
            public void onFailure(Call<Map<String, Boolean>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR,
                            contentArea.getScene().getWindow(),
                            "Hata",
                            "Doğrulama başarısız: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleLogout() {
        // Stop idle timer before logout
        if (idleTimer != null) {
            idleTimer.stop();
        }
        hideScreensaver();

        SessionManager.clearSession();
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"), bundle);
            Parent root = loader.load();
            Stage stage = (Stage) userLabel.getScene().getWindow();
            Scene scene = new Scene(root, 960, 640);
            ThemeHelper.applyToScene(scene, ThemeHelper.isDarkMode());
            stage.setScene(scene);
            stage.setMaximized(false);
            stage.setTitle(bundle.getString("app.title"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =================== SCREENSAVER METHODS ===================

    private void setupIdleTimer() {
        idleTimer = new PauseTransition(Duration.seconds(IDLE_TIMEOUT_SECONDS));
        idleTimer.setOnFinished(e -> {
            // Only show screensaver if no other windows are currently focused
            boolean hasActiveWindow = javafx.stage.Window.getWindows().stream()
                    .filter(w -> w instanceof Stage)
                    .anyMatch(w -> ((Stage) w).isFocused());

            if (!hasActiveWindow || isMainWindowFocused()) {
                showScreensaver();
            } else {
                // Another window is active, reset the timer
                resetIdleTimer();
            }
        });

        // Reset timer on any user activity in the main scene
        contentArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(MouseEvent.ANY, event -> resetIdleTimer());
                newScene.addEventFilter(KeyEvent.ANY, event -> {
                    resetIdleTimer();
                    // Hide screensaver on any key press
                    if (screensaverView != null && contentArea.getChildren().contains(screensaverView)) {
                        hideScreensaver();
                    }
                });
            }
        });

        // Track all window activity across the application
        javafx.stage.Window.getWindows()
                .addListener((javafx.collections.ListChangeListener<javafx.stage.Window>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            for (javafx.stage.Window window : change.getAddedSubList()) {
                                if (window instanceof Stage) {
                                    registerWindowActivityListeners((Stage) window);
                                }
                            }
                        }
                    }
                });

        idleTimer.play();
    }

    private boolean isMainWindowFocused() {
        Scene scene = contentArea.getScene();
        if (scene != null && scene.getWindow() instanceof Stage) {
            return ((Stage) scene.getWindow()).isFocused();
        }
        return false;
    }

    private void registerWindowActivityListeners(Stage stage) {
        // Reset idle timer when any child window gets activity
        stage.addEventFilter(MouseEvent.ANY, event -> resetIdleTimer());
        stage.addEventFilter(KeyEvent.ANY, event -> {
            resetIdleTimer();
            // Also hide screensaver if visible
            if (screensaverView != null && contentArea.getChildren().contains(screensaverView)) {
                Platform.runLater(this::hideScreensaver);
            }
        });
        // Also reset when window gains focus
        stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                resetIdleTimer();
            }
        });
    }

    private void resetIdleTimer() {
        if (idleTimer != null) {
            idleTimer.playFromStart();
        }
    }

    private void showScreensaver() {
        try {
            if (screensaverView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/screensaver_view.fxml"));
                screensaverView = loader.load();
                screensaverController = loader.getController();

                // Hide screensaver on click
                screensaverView.setOnMouseClicked(e -> hideScreensaver());
            }

            // Add screensaver overlay
            if (!contentArea.getChildren().contains(screensaverView)) {
                contentArea.getChildren().add(screensaverView);
                screensaverController.start();
            }
        } catch (IOException e) {
            System.err.println("Failed to load screensaver: " + e.getMessage());
        }
    }

    private void hideScreensaver() {
        if (screensaverView != null && contentArea.getChildren().contains(screensaverView)) {
            if (screensaverController != null) {
                screensaverController.stop();
            }
            contentArea.getChildren().remove(screensaverView);
        }
        resetIdleTimer();
    }
}