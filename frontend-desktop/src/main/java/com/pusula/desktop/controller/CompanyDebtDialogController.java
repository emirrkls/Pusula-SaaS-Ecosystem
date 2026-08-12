package com.pusula.desktop.controller;

import com.pusula.desktop.api.CompanyDebtApi;
import com.pusula.desktop.dto.CompanyDebtDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.CurrencyTextField;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
    private CurrencyTextField amountField;
    @FXML
    private TextField descriptionField;
    @FXML
    private ComboBox<CategoryOption> categoryComboBox;
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
        debtDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
        categoryComboBox.setItems(FXCollections.observableArrayList(
                new CategoryOption("MATERIAL", "Malzeme"),
                new CategoryOption("RENT", "Kira"),
                new CategoryOption("BILLS", "Faturalar"),
                new CategoryOption("FUEL", "Yakıt"),
                new CategoryOption("FOOD", "Yemek"),
                new CategoryOption("SALARY", "Maaş"),
                new CategoryOption("TAX", "Vergi"),
                new CategoryOption("OTHER", "Diğer")));
        categoryComboBox.getSelectionModel().selectLast();
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

        if (amountField.isEmpty()) {
            showError("Tutar alanı boş bırakılamaz!");
            return;
        }
        if (!amountField.isValidAmount()) {
            showError("Tutar sıfırdan büyük olmalıdır!");
            return;
        }
        BigDecimal amount = amountField.getRawValue();

        CompanyDebtDTO dto = CompanyDebtDTO.builder()
                .companyId(com.pusula.desktop.util.SessionManager.getCompanyId())
                .creditorName(creditorField.getText().trim())
                .originalAmount(amount)
                .expenseCategory(categoryComboBox.getValue().code())
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
        AlertHelper.showAlert(Alert.AlertType.ERROR, creditorField.getScene().getWindow(), "Hata", message);
    }

    private record CategoryOption(String code, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
