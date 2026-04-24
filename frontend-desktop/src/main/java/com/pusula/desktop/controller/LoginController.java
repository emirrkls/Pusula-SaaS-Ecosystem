package com.pusula.desktop.controller;

import com.pusula.desktop.api.AuthApi;
import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.AuthResponse;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.AnimationHelper;
import com.pusula.desktop.util.KeyboardShortcutHelper;
import com.pusula.desktop.util.NotificationHelper;
import com.pusula.desktop.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.pusula.desktop.api.UpdateApi;
import com.pusula.desktop.dto.UpdateInfoDTO;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class LoginController {
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private VBox loginForm;

    @FXML
    private VBox alertContainer;

    @FXML
    public void initialize() {
        // Apply bounce animation to login button
        if (loginButton != null) {
            AnimationHelper.applyButtonBounce(loginButton);
            // Set as default button (Enter key triggers it)
            loginButton.setDefaultButton(true);
        }

        // Fade in the login form on load
        if (loginForm != null) {
            AnimationHelper.fadeInUp(loginForm, 100);
        }

        // Enter key on password field triggers login
        if (passwordField != null && loginButton != null) {
            KeyboardShortcutHelper.enterToSubmit(passwordField, loginButton);
        }

        checkForUpdates();
    }

    private void checkForUpdates() {
        UpdateApi updateApi = RetrofitClient.getClient().create(UpdateApi.class);
        updateApi.getLatestVersion().enqueue(new Callback<UpdateInfoDTO>() {
            @Override
            public void onResponse(Call<UpdateInfoDTO> call, Response<UpdateInfoDTO> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UpdateInfoDTO dto = response.body();
                    String currentVersion = "3.0.0"; // Placeholder for current app version
                    if (dto.getLatestVersion() != null && dto.getLatestVersion().compareTo(currentVersion) > 0) {
                        Platform.runLater(() -> showUpdateBanner(dto));
                    }
                }
            }

            @Override
            public void onFailure(Call<UpdateInfoDTO> call, Throwable t) {
                // Silently ignore if network fails for update check
            }
        });
    }

    private void showUpdateBanner(UpdateInfoDTO dto) {
        if (alertContainer == null) return;
        alertContainer.getChildren().clear();

        HBox banner = new HBox(15);
        banner.getStyleClass().add("update-banner-modern");
        banner.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label("✨");
        iconLabel.setStyle("-fx-font-size: 24px;");

        VBox textVBox = new VBox(2);
        Label titleLabel = new Label("Yeni Güncelleme Mevcut! (" + dto.getLatestVersion() + ")");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");
        Label descLabel = new Label(dto.getReleaseNotes() != null ? dto.getReleaseNotes() : "Daha iyi bir deneyim için uygulamayı güncelleyin.");
        descLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 12px;");
        textVBox.getChildren().addAll(titleLabel, descLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button downloadBtn = new Button("Hemen İndir");
        downloadBtn.getStyleClass().add("update-btn");
        downloadBtn.setOnAction(e -> {
            try {
                if (dto.getDownloadUrl() != null) {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(dto.getDownloadUrl()));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        banner.getChildren().addAll(iconLabel, textVBox, spacer, downloadBtn);
        alertContainer.getChildren().add(banner);
        alertContainer.setVisible(true);
        alertContainer.setManaged(true);

        AnimationHelper.fadeInUp(alertContainer, 300);
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            // Shake animation to indicate error
            AnimationHelper.shake(usernameField.getParent());
            NotificationHelper.showError("Kullanıcı adı ve şifre gerekli!");
            return;
        }

        AuthApi authApi = RetrofitClient.getClient().create(AuthApi.class);
        AuthRequest request = new AuthRequest(username, password);

        authApi.authenticate(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String token = response.body().getToken();
                    String role = response.body().getRole();
                    Long companyId = response.body().getCompanyId();
                    SessionManager.setSession(token, username, role, companyId);

                    Platform.runLater(() -> {
                        try {
                            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                                    new java.util.Locale("tr", "TR"));
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main_dashboard.fxml"),
                                    bundle);
                            Parent root = loader.load();
                            Stage stage = (Stage) usernameField.getScene().getWindow();
                            Scene scene = new Scene(root);
                            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                            scene.getStylesheets()
                                    .add(getClass().getResource("/css/table-override.css").toExternalForm());
                            stage.setScene(scene);
                            stage.setTitle(bundle.getString("app.title"));
                            stage.setMaximized(true);
                        } catch (Exception e) {
                            e.printStackTrace();
                            AlertHelper.showAlert(Alert.AlertType.ERROR, usernameField.getScene().getWindow(), "Error",
                                    "Could not load dashboard: " + e.getMessage());
                        }
                    });
                } else {
                    System.err.println("Login Failed. Status Code: " + response.code());
                    try {
                        if (response.errorBody() != null) {
                            System.err.println("Error Body: " + response.errorBody().string());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Platform.runLater(() -> {
                        // Shake the login form on failed login
                        AnimationHelper.shake(usernameField.getParent());
                        NotificationHelper.showError("Giriş başarısız! Kullanıcı adı veya şifre hatalı.");
                    });
                }
            }

            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Platform.runLater(() -> {
                    AnimationHelper.shake(usernameField.getParent());
                    NotificationHelper.showError("Sunucuya bağlanılamadı: " + t.getMessage());
                });
            }
        });
    }
}
