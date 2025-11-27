package com.pusula.desktop.controller;

import com.pusula.desktop.api.AuthApi;
import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.AuthResponse;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
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
    private boolean isDark = false;

    @FXML
    public void initialize() {
        if (SessionManager.getUsername() != null) {
            userLabel.setText("Welcome, " + SessionManager.getUsername());
        }

        // Hide Finance and Settings for Technicians
        if (SessionManager.isTechnician()) {
            btnFinance.setVisible(false);
            btnFinance.setManaged(false);
            btnSettings.setVisible(false);
            btnSettings.setManaged(false);
        }

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
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/inventory.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
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
    public void showFinance() {
        // Double authentication for Finance
        if (SessionManager.isAdmin()) {
            showPasswordVerificationDialog(() -> navigateToFinance());
        }
    }

    @FXML
    private void showSettings() {
        // Double authentication for Settings
        if (SessionManager.isAdmin()) {
            showPasswordVerificationDialog(() -> navigateToSettings());
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
}