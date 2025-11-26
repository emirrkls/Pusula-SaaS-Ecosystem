package com.pusula.desktop.controller;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.dto.ExpenseDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ResourceBundle;

public class ExpenseDialogController {

    @FXML
    private ComboBox<String> comboCategory;
    @FXML
    private TextField txtAmount;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextArea txtDescription;

    private Runnable onSaveSuccess;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        // Load resource bundle for Turkish localization
        bundle = ResourceBundle.getBundle("i18n.messages",
                Locale.forLanguageTag("tr-TR"), new UTF8Control());

        // Populate categories with all enum values
        comboCategory.setItems(FXCollections.observableArrayList(
                "RENT", "SALARY", "BILLS", "FUEL", "FOOD", "TAX", "MATERIAL", "OTHER"));

        // Set StringConverter to display Turkish names
        comboCategory.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String enumValue) {
                if (enumValue == null)
                    return "";
                String key = "category." + enumValue;
                return bundle.containsKey(key) ? bundle.getString(key) : enumValue;
            }

            @Override
            public String fromString(String displayText) {
                // Reverse lookup: Turkish text -> Enum value
                if (displayText == null)
                    return null;
                for (String enumValue : comboCategory.getItems()) {
                    String key = "category." + enumValue;
                    if (bundle.containsKey(key) &&
                            bundle.getString(key).equals(displayText)) {
                        return enumValue;
                    }
                }
                return displayText; // Fallback to original if not found
            }
        });

        datePicker.setValue(LocalDate.now());
    }

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    @FXML
    private void handleSave() {
        if (!validateInput())
            return;

        ExpenseDTO expense = new ExpenseDTO();
        expense.setCompanyId(1L);
        expense.setCategory(comboCategory.getValue());
        expense.setAmount(new BigDecimal(txtAmount.getText()));
        expense.setDescription(txtDescription.getText());
        expense.setDate(datePicker.getValue().toString()); // Convert LocalDate to String

        FinanceApi api = RetrofitClient.getClient().create(FinanceApi.class);
        api.addExpense(expense).enqueue(new Callback<ExpenseDTO>() {
            @Override
            public void onResponse(Call<ExpenseDTO> call, Response<ExpenseDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        if (onSaveSuccess != null)
                            onSaveSuccess.run();
                        closeDialog();
                    });
                } else {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            txtAmount.getScene().getWindow(), "Hata",
                            "Gider kaydedilemedi: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<ExpenseDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                        txtAmount.getScene().getWindow(), "Hata",
                        "Bağlantı hatası: " + t.getMessage()));
            }
        });
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) txtAmount.getScene().getWindow();
        stage.close();
    }

    private boolean validateInput() {
        if (comboCategory.getValue() == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, txtAmount.getScene().getWindow(),
                    "Uyarı", "Lütfen kategori seçin.");
            return false;
        }
        if (txtAmount.getText().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, txtAmount.getScene().getWindow(),
                    "Uyarı", "Lütfen tutar girin.");
            return false;
        }
        try {
            new BigDecimal(txtAmount.getText());
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, txtAmount.getScene().getWindow(),
                    "Uyarı", "Geçersiz tutar formatı.");
            return false;
        }
        return true;
    }
}
