package com.pusula.desktop.controller;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.dto.ExpenseDTO;
import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CurrencyTextField;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Unified expense dialog controller combining daily expense creation
 * and fixed expense payment into a single tabbed dialog.
 */
public class UnifiedExpenseDialogController {

    // ===== Daily Expense Tab =====
    @FXML
    private TabPane modeTabPane;
    @FXML
    private ComboBox<String> comboCategory;
    @FXML
    private CurrencyTextField txtAmount;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextArea txtDescription;
    @FXML
    private ComboBox<FixedExpenseDefinitionDTO> comboLinkedFixedExpense;

    // Store all fixed expenses to filter later
    private List<FixedExpenseDefinitionDTO> allFixedExpenses = FXCollections.observableArrayList();

    // ===== Fixed Expense Tab =====
    @FXML
    private DatePicker dtFixedPaymentDate;
    @FXML
    private TableView<FixedExpenseRow> fixedExpensesTable;
    @FXML
    private TableColumn<FixedExpenseRow, Boolean> colFixedSelect;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedName;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedCategory;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedAmount;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedFrequency;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedPayAmount;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedLinkedInfo;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedDayOfMonth;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedDaysUntil;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFixedStatus;

    private FinanceApi financeApi;
    private ResourceBundle bundle;
    private Runnable onSaveSuccess;

    // Edit mode support for daily expenses
    private ExpenseDTO expenseToEdit = null;
    private boolean isEditMode = false;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages",
                Locale.forLanguageTag("tr-TR"), new UTF8Control());
        financeApi = RetrofitClient.getClient().create(FinanceApi.class);

        setupDailyExpenseTab();
        setupFixedExpenseTab();
        loadFixedExpenses();
    }

    // ==========================================
    // DAILY EXPENSE TAB
    // ==========================================

    private void setupDailyExpenseTab() {
        comboCategory.setItems(FXCollections.observableArrayList(
                "RENT", "SALARY", "BILLS", "FUEL", "FOOD", "TAX", "MATERIAL", "OTHER"));

        comboCategory.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String enumValue) {
                if (enumValue == null) return "";
                String key = "category." + enumValue;
                return bundle.containsKey(key) ? bundle.getString(key) : enumValue;
            }

            @Override
            public String fromString(String displayText) {
                if (displayText == null) return null;
                for (String enumValue : comboCategory.getItems()) {
                    String key = "category." + enumValue;
                    if (bundle.containsKey(key) && bundle.getString(key).equals(displayText)) {
                        return enumValue;
                    }
                }
                return displayText;
            }
        });

        comboCategory.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && allFixedExpenses != null) {
                List<FixedExpenseDefinitionDTO> filtered = allFixedExpenses.stream()
                        .filter(f -> f.getCategory().equals(newVal))
                        .collect(Collectors.toList());
                comboLinkedFixedExpense.setItems(FXCollections.observableArrayList(filtered));
            } else {
                comboLinkedFixedExpense.setItems(FXCollections.observableArrayList());
            }
        });

        comboLinkedFixedExpense.setConverter(new StringConverter<FixedExpenseDefinitionDTO>() {
            @Override
            public String toString(FixedExpenseDefinitionDTO dto) {
                return dto == null ? "" : dto.getName();
            }

            @Override
            public FixedExpenseDefinitionDTO fromString(String string) {
                return null;
            }
        });

        datePicker.setValue(LocalDate.now());
    }

    @FXML
    private void handleDailySave() {
        if (!validateDailyInput()) return;

        ExpenseDTO expense = expenseToEdit != null ? expenseToEdit : new ExpenseDTO();
        expense.setCompanyId(com.pusula.desktop.util.SessionManager.getCompanyId());
        expense.setCategory(comboCategory.getValue());
        expense.setAmount(txtAmount.getRawValue());
        expense.setDescription(txtDescription.getText());
        expense.setDate(datePicker.getValue().toString());

        if (comboLinkedFixedExpense.getValue() != null) {
            expense.setFixedExpenseId(comboLinkedFixedExpense.getValue().getId());
        } else if (!isEditMode && allFixedExpenses != null) {
            // Akıllı Uyarı Sistemi (Gelişmiş Seçim Ekranı)
            java.util.List<FixedExpenseDefinitionDTO> unpaidMatches = allFixedExpenses.stream()
                    .filter(f -> f.getCategory().equals(comboCategory.getValue()))
                    .filter(f -> !f.isPaidThisMonth())
                    .collect(Collectors.toList());

            if (!unpaidMatches.isEmpty()) {
                String categoryLocalized = bundle.containsKey("category." + comboCategory.getValue()) 
                        ? bundle.getString("category." + comboCategory.getValue()) 
                        : comboCategory.getValue();

                Dialog<ButtonType> dialog = new Dialog<>();
                dialog.setTitle("Akıllı Eşleştirme Önerisi");
                dialog.setHeaderText("Ödenmemiş Sabit Giderler Bulundu");
                
                ButtonType btnMatch = new ButtonType("Seçili Olanla Eşleştir", ButtonBar.ButtonData.OK_DONE);
                ButtonType btnStandalone = new ButtonType("Hiçbiriyle Eşleştirme", ButtonBar.ButtonData.OTHER);
                ButtonType btnCancel = new ButtonType("İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
                
                dialog.getDialogPane().getButtonTypes().addAll(btnMatch, btnStandalone, btnCancel);
                
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(10);
                javafx.scene.control.Label label = new javafx.scene.control.Label(
                    String.format("Bu kategoride (%s) ödenmemiş sabit giderleriniz bulunmaktadır.\nLütfen eşleştirmek istediğiniz faturayı seçin:", categoryLocalized));
                
                ComboBox<FixedExpenseDefinitionDTO> choiceBox = new ComboBox<>();
                choiceBox.getItems().addAll(unpaidMatches);
                choiceBox.getSelectionModel().selectFirst();
                choiceBox.setMaxWidth(Double.MAX_VALUE);
                choiceBox.setStyle("-fx-font-size: 14px; -fx-padding: 5px;");
                
                choiceBox.setConverter(new StringConverter<FixedExpenseDefinitionDTO>() {
                    @Override
                    public String toString(FixedExpenseDefinitionDTO dto) {
                        return dto == null ? "" : dto.getName() + " (" + String.format("%.2f \u20ba", dto.getDefaultAmount()) + ")";
                    }
                    @Override
                    public FixedExpenseDefinitionDTO fromString(String string) {
                        return null;
                    }
                });
                
                vbox.getChildren().addAll(label, choiceBox);
                dialog.getDialogPane().setContent(vbox);
                
                java.util.Optional<ButtonType> result = dialog.showAndWait();
                if (result.isPresent()) {
                    if (result.get() == btnMatch) {
                        FixedExpenseDefinitionDTO selected = choiceBox.getValue();
                        if (selected != null) {
                            expense.setFixedExpenseId(selected.getId());
                        }
                    } else if (result.get() == btnCancel) {
                        return; // İşlemi tamamen iptal et
                    }
                    // btnStandalone seçilirse hiçbir şey yapma, ID eklemeden normal kaydet
                }
            }
        }

        Call<ExpenseDTO> call = isEditMode
                ? financeApi.updateExpense(expense.getId(), expense)
                : financeApi.addExpense(expense);

        call.enqueue(new Callback<ExpenseDTO>() {
            @Override
            public void onResponse(Call<ExpenseDTO> call, Response<ExpenseDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        if (onSaveSuccess != null) onSaveSuccess.run();
                        closeDialog();
                    });
                } else {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            txtAmount.getScene().getWindow(),
                            bundle.getString("unified_expense.error.save_failed"),
                            bundle.getString("unified_expense.error.save_failed") + ": " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<ExpenseDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                        txtAmount.getScene().getWindow(),
                        bundle.getString("unified_expense.error.connection"),
                        bundle.getString("unified_expense.error.connection") + ": " + t.getMessage()));
            }
        });
    }

    @FXML
    private void handleDailyCancel() {
        closeDialog();
    }

    private boolean validateDailyInput() {
        if (comboCategory.getValue() == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, txtAmount.getScene().getWindow(),
                    "Warning", bundle.getString("unified_expense.validation.select_category"));
            return false;
        }
        if (txtAmount.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, txtAmount.getScene().getWindow(),
                    "Warning", bundle.getString("unified_expense.validation.enter_amount"));
            return false;
        }
        if (!txtAmount.isValidAmount()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, txtAmount.getScene().getWindow(),
                    "Warning", bundle.getString("unified_expense.validation.invalid_amount"));
            return false;
        }
        return true;
    }

    // ==========================================
    // FIXED EXPENSE TAB
    // ==========================================

    private void setupFixedExpenseTab() {
        dtFixedPaymentDate.setValue(LocalDate.now());

        // Checkbox column
        colFixedSelect.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        colFixedSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colFixedSelect));
        colFixedSelect.setEditable(true);
        fixedExpensesTable.setEditable(true);

        // Row factory for status highlighting
        fixedExpensesTable.setRowFactory(tv -> new TableRow<FixedExpenseRow>() {
            {
                selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                    updateRowStyle(getItem(), isNowSelected);
                });
            }

            @Override
            protected void updateItem(FixedExpenseRow item, boolean empty) {
                super.updateItem(item, empty);
                updateRowStyle(item, isSelected());
            }

            private void updateRowStyle(FixedExpenseRow item, boolean selected) {
                if (item == null) {
                    setStyle("");
                    return;
                }
                if (selected) {
                    setStyle("-fx-background-color: #334155; -fx-text-fill: white;");
                } else if (item.getDto().isPaidThisMonth()) {
                    setStyle("-fx-background-color: #dcfce7;");
                } else if (item.getDto().getPaidAmountThisMonth() != null && item.getDto().getPaidAmountThisMonth().compareTo(BigDecimal.ZERO) > 0) {
                    setStyle("-fx-background-color: #fef08a;"); // Yellow for partial
                } else if (item.getDaysUntilDue() < 0) {
                    setStyle("-fx-background-color: #fee2e2;");
                } else if (item.getDaysUntilDue() >= 0 && item.getDaysUntilDue() <= 3) {
                    setStyle("-fx-background-color: #fef3c7;");
                } else {
                    setStyle("");
                }
            }
        });

        // Name
        colFixedName.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDto().getName()));

        // Category
        colFixedCategory.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getDto().getCategory();
            String key = "category." + category;
            String localized = bundle.containsKey(key) ? bundle.getString(key) : category;
            return new SimpleStringProperty(localized);
        });

        // Default Amount
        colFixedAmount.setCellValueFactory(cellData -> new SimpleStringProperty(
                String.format("%.2f \u20ba", cellData.getValue().getDto().getDefaultAmount())));

        // Frequency
        colFixedFrequency.setCellValueFactory(cellData -> {
            String freq = cellData.getValue().getDto().getFrequency();
            String freqKey = "WEEKLY".equals(freq) ? "frequency.weekly" : "frequency.monthly";
            String localized = bundle.containsKey(freqKey) ? bundle.getString(freqKey) : freq;
            return new SimpleStringProperty(localized);
        });

        // Editable Pay Amount
        colFixedPayAmount.setCellFactory(column -> new TableCell<FixedExpenseRow, String>() {
            private final CurrencyTextField textField = new CurrencyTextField();
            {
                textField.setStyle("-fx-padding: 2; -fx-font-size: 12px;");
                textField.textProperty().addListener((obs, oldVal, newVal) -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        getTableRow().getItem().setPayAmount(newVal);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    FixedExpenseRow row = getTableRow().getItem();
                    if (row.getDto().isPaidThisMonth()) {
                        textField.setDisable(true);
                        textField.setText("");
                    } else {
                        textField.setDisable(false);
                        textField.setText(row.getPayAmount());
                        textField.setPromptText(String.format("%.2f", row.getDto().getDefaultAmount()));
                    }
                    setGraphic(textField);
                }
            }
        });
        colFixedPayAmount.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getPayAmount()));

        // Linked Payment Info
        colFixedLinkedInfo.setCellValueFactory(cellData -> {
            FixedExpenseDefinitionDTO dto = cellData.getValue().getDto();
            if (dto.getLinkedPaymentsThisMonth() != null
                    && dto.getLinkedPaymentsThisMonth().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remaining = dto.getDefaultAmount().subtract(dto.getLinkedPaymentsThisMonth());
                if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
                String info = String.format("%s: %.0f\u20ba\nKalan: %.0f\u20ba",
                        dto.getLinkedExpenseName() != null ? dto.getLinkedExpenseName() : "Connected",
                        dto.getLinkedPaymentsThisMonth(), remaining);
                return new SimpleStringProperty(info);
            }
            return new SimpleStringProperty("-");
        });

        // Day of Month
        colFixedDayOfMonth.setCellValueFactory(cellData -> {
            Integer day = cellData.getValue().getDto().getDayOfMonth();
            return new SimpleStringProperty(day != null ? day.toString() : "-");
        });

        // Days Until Due
        colFixedDaysUntil.setCellValueFactory(cellData -> {
            int daysUntil = cellData.getValue().getDaysUntilDue();
            String text;
            if (daysUntil < 0) text = bundle.containsKey("days.overdue") ? bundle.getString("days.overdue") : "Overdue";
            else if (daysUntil == 0) text = bundle.containsKey("days.today") ? bundle.getString("days.today") : "Today";
            else text = daysUntil + " " + (bundle.containsKey("days.remaining") ? bundle.getString("days.remaining") : "days");
            return new SimpleStringProperty(text);
        });

        // Status
        colFixedStatus.setCellValueFactory(cellData -> {
            FixedExpenseDefinitionDTO dto = cellData.getValue().getDto();
            boolean isPaid = dto.isPaidThisMonth();
            BigDecimal paidAmount = dto.getPaidAmountThisMonth();
            BigDecimal defaultAmount = dto.getDefaultAmount();

            String status = "";
            if (isPaid) {
                status = bundle.containsKey("finance.pay_fixed_expense_dialog.paid") ? bundle.getString("finance.pay_fixed_expense_dialog.paid") : "Ödendi";
            } else if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remaining = defaultAmount.subtract(paidAmount);
                if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
                status = "Kısmi (Kalan: " + String.format("%.0f", remaining) + "\u20ba)";
            }
            return new SimpleStringProperty(status);
        });
    }

    private void loadFixedExpenses() {
        financeApi.getFixedExpenses().enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
            @Override
            public void onResponse(Call<List<FixedExpenseDefinitionDTO>> call,
                                   Response<List<FixedExpenseDefinitionDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        allFixedExpenses = response.body();
                        List<FixedExpenseRow> rows = allFixedExpenses.stream()
                                .map(dto -> new FixedExpenseRow(dto, calculateDaysUntilDue(dto.getDayOfMonth())))
                                .collect(Collectors.toList());
                        fixedExpensesTable.setItems(FXCollections.observableArrayList(rows));
                        fixedExpensesTable.getItems().forEach(row -> {
                            if (row.getDto().isPaidThisMonth()) {
                                row.selectedProperty().setValue(false);
                                row.setSelectable(false);
                            }
                        });
                        
                        // Update combo box if category is already selected
                        if (comboCategory.getValue() != null) {
                            List<FixedExpenseDefinitionDTO> filtered = allFixedExpenses.stream()
                                    .filter(f -> f.getCategory().equals(comboCategory.getValue()))
                                    .collect(Collectors.toList());
                            comboLinkedFixedExpense.setItems(FXCollections.observableArrayList(filtered));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<FixedExpenseDefinitionDTO>> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                        bundle.getString("unified_expense.error.load_failed"),
                        bundle.getString("unified_expense.error.load_failed") + ": " + t.getMessage()));
            }
        });
    }

    private int calculateDaysUntilDue(Integer dayOfMonth) {
        if (dayOfMonth == null) return 999;
        int currentDay = LocalDate.now().getDayOfMonth();
        return dayOfMonth - currentDay;
    }

    @FXML
    private void handleFixedPay() {
        List<FixedExpenseRow> selectedRows = fixedExpensesTable.getItems().stream()
                .filter(row -> row.selectedProperty().get() && !row.getDto().isPaidThisMonth())
                .collect(Collectors.toList());

        if (selectedRows.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Warning",
                    bundle.getString("unified_expense.warning.no_selection"));
            return;
        }

        LocalDate paymentDate = dtFixedPaymentDate.getValue();
        if (paymentDate == null) paymentDate = LocalDate.now();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(selectedRows.size());
        final LocalDate finalDate = paymentDate;

        for (FixedExpenseRow row : selectedRows) {
            BigDecimal customAmount = null;
            if (row.getPayAmount() != null && !row.getPayAmount().trim().isEmpty()) {
                try {
                    String amountStr = row.getPayAmount();
                    customAmount = CurrencyTextField.parseTurkishCurrency(amountStr);
                } catch (NumberFormatException e) {
                    AlertHelper.showAlert(Alert.AlertType.WARNING, null,
                            bundle.getString("unified_expense.error.invalid_amount"),
                            bundle.getString("unified_expense.error.invalid_amount") + ": " + row.getDto().getName());
                    continue;
                }
            }

            final BigDecimal finalAmount = customAmount;
            financeApi.payFixedExpense(row.getDto().getId(), finalDate.toString(), finalAmount)
                    .enqueue(new Callback<ExpenseDTO>() {
                        @Override
                        public void onResponse(Call<ExpenseDTO> call, Response<ExpenseDTO> response) {
                            if (response.isSuccessful()) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                            checkCompletion(successCount.get(), failureCount.get(), totalCount.get());
                        }

                        @Override
                        public void onFailure(Call<ExpenseDTO> call, Throwable t) {
                            failureCount.incrementAndGet();
                            checkCompletion(successCount.get(), failureCount.get(), totalCount.get());
                        }
                    });
        }
    }

    private void checkCompletion(int success, int failure, int total) {
        if (success + failure == total) {
            Platform.runLater(() -> {
                if (success > 0) {
                    AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "OK",
                            success + " " + bundle.getString("unified_expense.success.paid"));
                    if (onSaveSuccess != null) onSaveSuccess.run();
                }
                if (success > 0 || failure == total) {
                    closeDialog();
                }
            });
        }
    }

    @FXML
    private void handleFixedCancel() {
        closeDialog();
    }

    // ==========================================
    // SHARED
    // ==========================================

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    /**
     * For edit mode: pre-fill the daily expense form and switch to daily tab
     */
    public void setExpenseToEdit(ExpenseDTO expense) {
        this.expenseToEdit = expense;
        this.isEditMode = true;

        comboCategory.setValue(expense.getCategory());
        txtAmount.setRawValue(expense.getAmount());
        txtDescription.setText(expense.getDescription());
        datePicker.setValue(LocalDate.parse(expense.getDate()));

        // Switch to daily tab
        modeTabPane.getSelectionModel().select(0);
    }

    /**
     * Switch to fixed expense tab (called from alert buttons)
     */
    public void switchToFixedTab() {
        modeTabPane.getSelectionModel().select(1);
    }

    private void closeDialog() {
        Stage stage = (Stage) modeTabPane.getScene().getWindow();
        stage.close();
    }

    // ==========================================
    // INNER CLASS: FixedExpenseRow
    // ==========================================

    public static class FixedExpenseRow {
        private final FixedExpenseDefinitionDTO dto;
        private final int daysUntilDue;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private boolean selectable = true;
        private String payAmount = "";

        public FixedExpenseRow(FixedExpenseDefinitionDTO dto, int daysUntilDue) {
            this.dto = dto;
            this.daysUntilDue = daysUntilDue;
        }

        public FixedExpenseDefinitionDTO getDto() { return dto; }
        public int getDaysUntilDue() { return daysUntilDue; }
        public SimpleBooleanProperty selectedProperty() { return selected; }

        public void setSelectable(boolean selectable) {
            this.selectable = selectable;
            if (!selectable) selected.set(false);
        }

        public String getPayAmount() { return payAmount; }
        public void setPayAmount(String payAmount) { this.payAmount = payAmount; }
    }
}
