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
import com.pusula.desktop.util.ThemeHelper;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.pusula.desktop.api.UpdateApi;
import com.pusula.desktop.dto.UpdateInfoDTO;
import com.pusula.desktop.update.UpdateService;
import com.pusula.desktop.update.UpdateUiHelper;
import com.pusula.desktop.util.AppVersion;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import javafx.scene.control.ProgressBar;
import javafx.stage.StageStyle;
import java.nio.file.Path;
import java.util.Optional;

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
    private BorderPane loginRoot;

    @FXML
    private Label appVersionLabel;

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

        if (appVersionLabel != null) {
            appVersionLabel.setText("v" + AppVersion.get());
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
                    if (dto.getLatestVersion() != null && AppVersion.isRemoteNewer(dto.getLatestVersion())) {
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
        alertContainer.setMaxWidth(Double.MAX_VALUE);

        VBox banner = UpdateUiHelper.createUpdateBanner(
                dto,
                () -> startInAppUpdate(dto),
                () -> openBrowserDownload(dto));

        banner.maxWidthProperty().bind(
                Bindings.min(440, alertContainer.widthProperty()));
        banner.prefWidthProperty().bind(banner.maxWidthProperty());

        alertContainer.getChildren().add(banner);
        alertContainer.setVisible(true);
        alertContainer.setManaged(true);

        AnimationHelper.fadeInUp(alertContainer, 300);
    }

    private void openBrowserDownload(UpdateInfoDTO dto) {
        try {
            if (dto.getDownloadUrl() != null) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(dto.getDownloadUrl()));
            }
        } catch (Exception ex) {
            NotificationHelper.showError("İndirme bağlantısı açılamadı: " + ex.getMessage());
        }
    }

    private void startInAppUpdate(UpdateInfoDTO dto) {
        if (dto.getDownloadUrl() == null || dto.getDownloadUrl().isBlank()) {
            NotificationHelper.showError("Güncelleme adresi tanımlı değil.");
            return;
        }

        if (!UpdateService.isRunningFromNativePackage()) {
            NotificationHelper.showError("Uygulama içi güncelleme yalnızca kurulu MSI sürümünde çalışır.");
            openBrowserDownload(dto);
            return;
        }

        Optional<Path> exePath = UpdateService.resolveInstalledExePath();
        if (exePath.isEmpty()) {
            NotificationHelper.showError("Kurulum yolu bulunamadı.");
            return;
        }

        if (!UpdateUiHelper.showUpdateConfirmation(
                usernameField != null && usernameField.getScene() != null
                        ? (Stage) usernameField.getScene().getWindow()
                        : null,
                dto.getLatestVersion())) {
            return;
        }

        Stage progressStage = new Stage(StageStyle.UTILITY);
        Stage owner = usernameField != null && usernameField.getScene() != null
                ? (Stage) usernameField.getScene().getWindow()
                : null;
        if (owner != null) {
            progressStage.initOwner(owner);
        }
        progressStage.setTitle("Güncelleniyor");
        progressStage.setResizable(false);

        Label statusLabel = new Label("Güncelleme indiriliyor...");
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(360);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        VBox progressRoot = new VBox(12, statusLabel, progressBar);
        progressRoot.setStyle("-fx-padding: 20;");
        progressStage.setScene(ThemeHelper.createDialogScene(progressRoot, 420, 120));
        progressStage.show();

        Thread downloadThread = new Thread(() -> {
            try {
                Path msiPath = UpdateService.downloadMsi(dto.getDownloadUrl(), (downloaded, total) ->
                        Platform.runLater(() -> {
                            if (total > 0) {
                                progressBar.setProgress((double) downloaded / total);
                                statusLabel.setText("İndiriliyor… %d%%".formatted(Math.min(100, (int) ((downloaded * 100) / total))));
                            } else {
                                progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                            }
                        }));

                Platform.runLater(() -> {
                    statusLabel.setText("Kurulum başlatılıyor…");
                    progressBar.setProgress(1);
                });

                UpdateService.launchInstallerAndExit(msiPath, exePath.get());

                Platform.runLater(() -> {
                    progressStage.close();
                    Platform.exit();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressStage.close();
                    NotificationHelper.showError("Güncelleme başarısız: " + ex.getMessage());
                });
            }
        }, "pusula-update-download");
        downloadThread.setDaemon(true);
        downloadThread.start();
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

        SessionManager.clearSession();

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
                            Scene scene = new Scene(root, 1200, 720);
                            ThemeHelper.applyToScene(scene, ThemeHelper.isDarkMode());
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
                        AnimationHelper.shake(usernameField.getParent());
                        String message = switch (response.code()) {
                            case 401 -> "Giriş başarısız! Kullanıcı adı veya şifre hatalı.";
                            case 403 -> "Giriş reddedildi. Hesabınızın yetkisi yok veya oturum çakışması oluştu. Tekrar deneyin.";
                            case 429 -> "Çok fazla başarısız deneme. Lütfen bir süre sonra tekrar deneyin.";
                            default -> "Giriş başarısız! Sunucu yanıtı: " + response.code();
                        };
                        NotificationHelper.showError(message);
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
