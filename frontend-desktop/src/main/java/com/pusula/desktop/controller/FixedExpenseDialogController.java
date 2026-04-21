package com.pusula.desktop.controller;

import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import com.pusula.desktop.util.UTF8Control;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.List;
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
    @FXML
    private Label dayLabel;
    @FXML
    private ComboBox<String> dayOfWeekCombo;
    @FXML
    private ComboBox<LinkedExpenseOption> linkedExpenseCombo;

    private ResourceBundle bundle;
    private FixedExpenseDefinitionDTO fixedExpense;
    private Runnable onSaveCallback;
    private List<FixedExpenseDefinitionDTO> availableExpenses;

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

            // Add listener to toggle day of month/week visibility
            frequencyCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                boolean isWeekly = newVal != null && newVal.equals(bundle.getString("expense.frequency.weekly"));
                updateDayInputVisibility(isWeekly);
            });
        }

        // Setup day of week combo with Turkish day names
        if (dayOfWeekCombo != null) {
            dayOfWeekCombo.setItems(FXCollections.observableArrayList(
                    "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"));
            dayOfWeekCombo.setValue("Pazartesi"); // Default to Monday
        }
    }

    private void updateDayInputVisibility(boolean isWeekly) {
        if (dayLabel != null) {
            dayLabel.setText(isWeekly ? "Haftanın Günü" : bundle.getString("settings.day_of_month"));
        }
        if (daySpinner != null) {
            daySpinner.setVisible(!isWeekly);
            daySpinner.setManaged(!isWeekly);
        }
        if (dayOfWeekCombo != null) {
            dayOfWeekCombo.setVisible(isWeekly);
            dayOfWeekCombo.setManaged(isWeekly);
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

            // Set linked expense if exists
            if (expense.getLinkedExpenseId() != null && linkedExpenseCombo != null) {
                for (LinkedExpenseOption opt : linkedExpenseCombo.getItems()) {
                    if (opt.id != null && opt.id.equals(expense.getLinkedExpenseId())) {
                        linkedExpenseCombo.setValue(opt);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Set available expenses for linking (monthly can link to weekly)
     */
    public void setAvailableExpenses(List<FixedExpenseDefinitionDTO> expenses) {
        this.availableExpenses = expenses;
        if (linkedExpenseCombo != null && expenses != null) {
            linkedExpenseCombo.getItems().clear();
            // Add empty option first
            linkedExpenseCombo.getItems().add(new LinkedExpenseOption(null, "(Bağlantı Yok)"));

            // Add weekly expenses as options for linking
            for (FixedExpenseDefinitionDTO exp : expenses) {
                if ("WEEKLY".equals(exp.getFrequency())) {
                    linkedExpenseCombo.getItems()
                            .add(new LinkedExpenseOption(exp.getId(), exp.getName() + " (Haftalık)"));
                }
            }

            // Select first (no link) by default
            if (!linkedExpenseCombo.getItems().isEmpty()) {
                linkedExpenseCombo.setValue(linkedExpenseCombo.getItems().get(0));
            }
        }
    }

    // Helper class for linked expense combo
    private static class LinkedExpenseOption {
        private final Long id;
        private final String name;

        public LinkedExpenseOption(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
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
                    // For weekly, store day of week as 1-7 (Monday=1, Sunday=7)
                    if (dayOfWeekCombo != null && dayOfWeekCombo.getValue() != null) {
                        int dayIndex = dayOfWeekCombo.getSelectionModel().getSelectedIndex() + 1;
                        fixedExpense.setDayOfMonth(dayIndex); // Using dayOfMonth field for day of week
                    }
                } else {
                    fixedExpense.setFrequency("MONTHLY");
                }
            } else {
                // Default to MONTHLY if combobox doesn't exist
                fixedExpense.setFrequency("MONTHLY");
            }

            // Set linked expense
            if (linkedExpenseCombo != null && linkedExpenseCombo.getValue() != null) {
                LinkedExpenseOption selected = linkedExpenseCombo.getValue();
                fixedExpense.setLinkedExpenseId(selected.id);
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
