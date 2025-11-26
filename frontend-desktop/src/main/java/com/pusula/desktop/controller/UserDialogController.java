package com.pusula.desktop.controller;

import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.util.UTF8Control;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

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

    private UserDTO result;
    private boolean isEditMode = false;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());

        // Populate role dropdown
        cmbRole.setItems(FXCollections.observableArrayList("ADMIN", "TECHNICIAN"));
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
}
