package com.pusula.desktop.controller;

import com.pusula.desktop.api.AuthApi;
import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.AuthResponse;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.util.SessionManager;
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
import javafx.stage.Stage;
import javafx.util.Duration;
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
    private Button btnFinance;
    @FXML
    private Button btnSettings;
    @FXML
    private Button btnActivityLog;

    private boolean isDark = false;

    // Screensaver fields
    private static final int IDLE_TIMEOUT_SECONDS = 10; // 10 seconds for testing
    private PauseTransition idleTimer;
    private Parent screensaverView;
    private ScreensaverController screensaverController;

    @FXML
    public void initialize() {
        if (SessionManager.getUsername() != null) {
            userLabel.setText("Welcome, " + SessionManager.getUsername());
        }

        // Hide Finance, Settings and Activity Log for Technicians
        if (SessionManager.isTechnician()) {
            if (btnFinance != null) {
                btnFinance.setVisible(false);
                btnFinance.setManaged(false);
            }
            if (btnSettings != null) {
                btnSettings.setVisible(false);
                btnSettings.setManaged(false);
            }
            if (btnActivityLog != null) {
                btnActivityLog.setVisible(false);
                btnActivityLog.setManaged(false);
            }
        }

        // Setup screensaver idle detection
        setupIdleTimer();

        showDashboard();
    }

    @FXML
    private void toggleTheme() {
        if (isDark) {
            javafx.application.Application.setUserAgentStylesheet(
                    new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
            isDark = false;
        } else {
            javafx.application.Application.setUserAgentStylesheet(
                    new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
            isDark = true;
        }
    }

    @FXML
    public void showDashboard() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dashboard.fxml"), bundle);
            Parent view = loader.load();
            DashboardController controller = loader.getController();
            controller.setMainController(this);
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showServiceManagement() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/service_tickets.fxml"), bundle);
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
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
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/inventory.fxml"), bundle);
            Parent view = loader.load();

            // Get controller and apply filter if requested
            if (filterCritical) {
                InventoryController controller = loader.getController();
                controller.filterCriticalStocks();
            }

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, contentArea.getScene().getWindow(),
                    "Hata", "Envanter yüklenemedi: " + e.getMessage());
        }
    }

    @FXML
    public void showCustomers() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/customers.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showCommercialDevices() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/commercial_device_view.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/proposal_view.fxml"));
            loader.setResources(java.util.ResourceBundle.getBundle("i18n.messages_tr"));
            Parent content = loader.load();
            contentArea.getChildren().setAll(content);
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
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/finance_view.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void navigateToSettings() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/settings_view.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, contentArea.getScene().getWindow(),
                    "Hata", "Ayarlar yüklenemedi: " + e.getMessage());
        }
    }

    private void navigateToActivityLog() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/activity_log.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
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
            stage.setScene(new Scene(root));
            stage.setTitle("Pusula - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =================== SCREENSAVER METHODS ===================

    private void setupIdleTimer() {
        idleTimer = new PauseTransition(Duration.seconds(IDLE_TIMEOUT_SECONDS));
        idleTimer.setOnFinished(e -> showScreensaver());

        // Reset timer on any user activity
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

        idleTimer.play();
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