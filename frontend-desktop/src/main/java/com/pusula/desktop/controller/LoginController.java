package com.pusula.desktop.controller;

import com.pusula.desktop.api.AuthApi;
import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.AuthResponse;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginController {
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, usernameField.getScene().getWindow(), "Form Error",
                    "Please enter username and password");
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
                    SessionManager.setSession(token, username, role);

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
                        AlertHelper.showAlert(Alert.AlertType.ERROR, usernameField.getScene().getWindow(),
                                "Login Failed", "Invalid credentials or server error. Code: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, usernameField.getScene().getWindow(), "Network Error",
                            "Could not connect to server: " + t.getMessage());
                });
            }
        });
    }
}
