package com.pusula.desktop.controller;

import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import com.pusula.desktop.util.UTF8Control;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.ResourceBundle;

public class FixedExpenseDialogController {

    @FXML
    private TextField nameField;
    @FXML
    private TextField amountField;
    @FXML
    private ComboBox<String> categoryComboBox;
    @FXML
    private Spinner<Integer> daySpinner;
    @FXML
    private TextField descriptionField;
    @FXML
    private ComboBox<String> frequencyCombo;

    private ResourceBundle bundle;
    private FixedExpenseDefinitionDTO fixedExpense;
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        // Load resource bundle with UTF-8 support for Turkish characters
        bundle = ResourceBundle.getBundle("i18n.messages",
                Locale.forLanguageTag("tr-TR"),
                new UTF8Control());

        // Setup category combobox with enum values
        categoryComboBox.setItems(FXCollections.observableArrayList(
                "RENT", "SALARY", "BILLS", "FUEL", "FOOD", "TAX", "MATERIAL", "OTHER"));

        // Setup day spinner (1-31)
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 31, 1);
        daySpinner.setValueFactory(valueFactory);

        // Use converter to show localized category names
        categoryComboBox.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String category) {
                if (category == null)
                    return "";
                String key = "category." + category;
                return bundle.containsKey(key) ? bundle.getString(key) : category;
            }

            @Override
            public String fromString(String string) {
                // Find category by localized name
                for (String category : categoryComboBox.getItems()) {
                    String key = "category." + category;
                    if (bundle.containsKey(key) && bundle.getString(key).equals(string)) {
                        return category;
                    }
                }
                return string;
            }
        });

        categoryComboBox.getSelectionModel().selectFirst();

        // Setup frequency combobox with localized options
        if (frequencyCombo != null) {
            frequencyCombo.setItems(FXCollections.observableArrayList(
                    bundle.getString("expense.frequency.monthly"),
                    bundle.getString("expense.frequency.weekly")));
            frequencyCombo.setValue(bundle.getString("expense.frequency.monthly")); // Default to Monthly
        }
    }

    public void setFixedExpense(FixedExpenseDefinitionDTO expense) {
        this.fixedExpense = expense;
        if (expense != null) {
            // Edit mode - populate fields
            nameField.setText(expense.getName());
            amountField.setText(expense.getDefaultAmount().toString());
            categoryComboBox.setValue(expense.getCategory());
            if (expense.getDayOfMonth() != null) {
                daySpinner.getValueFactory().setValue(expense.getDayOfMonth());
            }
            descriptionField.setText(expense.getDescription() != null ? expense.getDescription() : "");
        }
    }

    public void setOnSave(Runnable callback) {
        this.onSaveCallback = callback;
    }

    public FixedExpenseDefinitionDTO getResult() {
        return fixedExpense;
    }

    @FXML
    private void handleSave() {
        // Validate
        if (nameField.getText().trim().isEmpty()) {
            showError("İsim alanı boş bırakılamaz!");
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(amountField.getText().trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Tutar sıfırdan büyük olmalıdır!");
                return;
            }

            // Build DTO
            if (fixedExpense == null) {
                fixedExpense = new FixedExpenseDefinitionDTO();
                fixedExpense.setCompanyId(1L);
            }

            fixedExpense.setName(nameField.getText().trim());
            fixedExpense.setDefaultAmount(amount);
            fixedExpense.setCategory(categoryComboBox.getValue());
            fixedExpense.setDayOfMonth(daySpinner.getValue());
            fixedExpense.setDescription(descriptionField.getText().trim());

            // Map frequency selection to MONTHLY/WEEKLY
            if (frequencyCombo != null && frequencyCombo.getValue() != null) {
                String selectedFreq = frequencyCombo.getValue();
                if (selectedFreq.equals(bundle.getString("expense.frequency.weekly"))) {
                    fixedExpense.setFrequency("WEEKLY");
                } else {
                    fixedExpense.setFrequency("MONTHLY");
                }
            } else {
                // Default to MONTHLY if combobox doesn't exist
                fixedExpense.setFrequency("MONTHLY");
            }

            if (onSaveCallback != null) {
                onSaveCallback.run();
            }

            closeDialog();
        } catch (NumberFormatException e) {
            showError("Geçersiz tutar formatı!");
        }
    }

    @FXML
    private void handleCancel() {
        fixedExpense = null;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) nameField.getScene().getWindow();
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
