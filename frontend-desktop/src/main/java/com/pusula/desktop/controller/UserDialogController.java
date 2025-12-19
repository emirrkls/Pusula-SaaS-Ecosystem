package com.pusula.desktop.controller;

import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public class UserDialogController {

    @FXML
    private TextField txtUsername;
    @FXML
    private TextField txtFullName;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private Label lblPassword;
    @FXML
    private ComboBox<String> cmbRole;
    @FXML
    private Button btnSelectSignature;
    @FXML
    private Label lblSignatureFilename;

    private UserDTO result;
    private boolean isEditMode = false;
    private ResourceBundle bundle;
    private File selectedSignatureFile;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());

        // Populate role dropdown
        cmbRole.setItems(FXCollections.observableArrayList("COMPANY_ADMIN", "TECHNICIAN"));
        cmbRole.getSelectionModel().selectFirst();
    }

    /**
     * Set user for editing (makes password optional)
     */
    public void setUser(UserDTO user) {
        if (user != null) {
            isEditMode = true;
            txtUsername.setText(user.getUsername());
            txtFullName.setText(user.getFullName());
            cmbRole.setValue(user.getRole());

            // Make password optional in edit mode
            lblPassword.setText(bundle.getString("settings.password_optional"));
            txtPassword.setPromptText(bundle.getString("settings.leave_empty_keep_current"));

            // Store ID for update
            result = UserDTO.builder()
                    .id(user.getId())
                    .build();
        }
    }

    @FXML
    private void handleSave() {
        if (!validateInput()) {
            return;
        }

        // Build result DTO
        UserDTO.UserDTOBuilder builder = UserDTO.builder()
                .username(txtUsername.getText().trim())
                .fullName(txtFullName.getText().trim())
                .role(cmbRole.getValue());

        // Include password if provided
        String password = txtPassword.getText();
        if (!password.isEmpty()) {
            builder.password(password);
        }

        // Preserve ID if editing
        if (isEditMode && result != null) {
            builder.id(result.getId());
        }

        result = builder.build();
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        result = null;
        closeDialog();
    }

    private boolean validateInput() {
        if (txtUsername.getText().trim().isEmpty()) {
            showAlert("Kullanıcı adı boş olamaz!");
            return false;
        }

        if (txtFullName.getText().trim().isEmpty()) {
            showAlert("Ad Soyad boş olamaz!");
            return false;
        }

        // Password required only in create mode
        if (!isEditMode && txtPassword.getText().isEmpty()) {
            showAlert("Şifre boş olamaz!");
            return false;
        }

        // Password length check if provided
        if (!txtPassword.getText().isEmpty() && txtPassword.getText().length() < 6) {
            showAlert("Şifre en az 6 karakter olmalıdır!");
            return false;
        }

        if (cmbRole.getValue() == null) {
            showAlert("Lütfen bir rol seçin!");
            return false;
        }

        return true;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Uyarı");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void closeDialog() {
        Stage stage = (Stage) txtUsername.getScene().getWindow();
        stage.close();
    }

    public UserDTO getResult() {
        return result;
    }

    public File getSelectedSignatureFile() {
        return selectedSignatureFile;
    }

    @FXML
    private void handleSelectSignature() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("İmza Dosyası Seç");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Resim Dosyaları", "*.png", "*.jpg", "*.jpeg"));

        selectedSignatureFile = fileChooser.showOpenDialog(txtUsername.getScene().getWindow());

        if (selectedSignatureFile != null) {
            lblSignatureFilename.setText(selectedSignatureFile.getName());
        }
    }

    private void uploadSignature(Long userId) {
        if (selectedSignatureFile == null) {
            return; // No file selected, skip upload
        }

        RequestBody requestFile = RequestBody.create(selectedSignatureFile, MediaType.parse("image/*"));
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", selectedSignatureFile.getName(),
                requestFile);

        UserApi userApi = RetrofitClient.getClient().create(UserApi.class);
        userApi.uploadSignature(userId, body).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, txtUsername.getScene().getWindow(),
                                "Başarılı", "İmza başarıyla yüklendi!");
                    } else {
                        String errorMsg = response.message();
                        try {
                            if (response.errorBody() != null) {
                                errorMsg += " " + response.errorBody().string();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        AlertHelper.showAlert(Alert.AlertType.WARNING, txtUsername.getScene().getWindow(),
                                "Hata", "İmza yüklenemedi: " + errorMsg);
                    }
                });
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, txtUsername.getScene().getWindow(),
                            "Hata", "Ağ hatası: " + t.getMessage());
                });
            }
        });
    }
}
