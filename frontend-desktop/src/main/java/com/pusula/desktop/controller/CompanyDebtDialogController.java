package com.pusula.desktop.controller;

import com.pusula.desktop.api.CompanyDebtApi;
import com.pusula.desktop.dto.CompanyDebtDTO;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CompanyDebtDialogController {

    @FXML
    private TextField creditorField;
    @FXML
    private TextField amountField;
    @FXML
    private TextField descriptionField;
    @FXML
    private DatePicker debtDatePicker;
    @FXML
    private DatePicker dueDatePicker;
    @FXML
    private TextField phoneField;
    @FXML
    private TextArea notesArea;

    private CompanyDebtApi api;
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        api = RetrofitClient.getClient().create(CompanyDebtApi.class);
        debtDatePicker.setValue(LocalDate.now());
    }

    public void setOnSave(Runnable callback) {
        this.onSaveCallback = callback;
    }

    @FXML
    private void handleSave() {
        // Validate
        if (creditorField.getText().trim().isEmpty()) {
            showError("Alacaklı adı zorunludur!");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().replace(",", ".").trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Tutar sıfırdan büyük olmalıdır!");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Geçersiz tutar formatı!");
            return;
        }

        CompanyDebtDTO dto = CompanyDebtDTO.builder()
                .companyId(1L)
                .creditorName(creditorField.getText().trim())
                .originalAmount(amount)
                .description(descriptionField.getText().trim())
                .debtDate(debtDatePicker.getValue())
                .dueDate(dueDatePicker.getValue())
                .creditorPhone(phoneField.getText().trim())
                .notes(notesArea.getText())
                .build();

        api.createDebt(dto).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<CompanyDebtDTO> call, Response<CompanyDebtDTO> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) {
                        if (onSaveCallback != null) {
                            onSaveCallback.run();
                        }
                        closeDialog();
                    } else {
                        showError("Kayıt başarısız: " + response.code());
                    }
                });
            }

            @Override
            public void onFailure(Call<CompanyDebtDTO> call, Throwable t) {
                Platform.runLater(() -> showError("Bağlantı hatası: " + t.getMessage()));
            }
        });
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) creditorField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Hata");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
