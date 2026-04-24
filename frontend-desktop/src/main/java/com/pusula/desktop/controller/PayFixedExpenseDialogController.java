package com.pusula.desktop.controller;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.dto.ExpenseDTO;
import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PayFixedExpenseDialogController {
    @FXML
    private TableView<FixedExpenseRow> expensesTable;
    @FXML
    private TableColumn<FixedExpenseRow, Boolean> colSelect;
    @FXML
    private TableColumn<FixedExpenseRow, String> colName;
    @FXML
    private TableColumn<FixedExpenseRow, String> colCategory;
    @FXML
    private TableColumn<FixedExpenseRow, String> colAmount;
    @FXML
    private TableColumn<FixedExpenseRow, String> colFrequency;
    @FXML
    private TableColumn<FixedExpenseRow, String> colPayAmount;
    @FXML
    private TableColumn<FixedExpenseRow, String> colLinkedInfo;
    @FXML
    private TableColumn<FixedExpenseRow, String> colDayOfMonth;
    @FXML
    private TableColumn<FixedExpenseRow, String> colDaysUntil;
    @FXML
    private TableColumn<FixedExpenseRow, String> colStatus;
    @FXML
    private DatePicker dtPaymentDate;
    private FinanceApi financeApi;
    private ResourceBundle bundle;
    private Runnable onSuccess;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", new java.util.Locale("tr", "TR"), new UTF8Control());
        financeApi = RetrofitClient.getClient().create(FinanceApi.class);

        // Initialize date picker to today
        dtPaymentDate.setValue(LocalDate.now());

        System.out.println("=== PayFixedExpenseDialogController INITIALIZED - ROW FACTORY DISABLED ===");

        setupTable();
        loadFixedExpenses();
    }

    private void setupTable() {
        // Checkbox column
        colSelect.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
        colSelect.setEditable(true);
        expensesTable.setEditable(true);

        // Row factory for status highlighting
        expensesTable.setRowFactory(tv -> new TableRow<FixedExpenseRow>() {
            {
                // Add listener for selection changes
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
                    // Force dark blue background with white text when selected
                    setStyle("-fx-background-color: #334155; -fx-text-fill: white;");
                } else if (item.getDto().isPaidThisMonth()) {
                    setStyle("-fx-background-color: #dcfce7;"); // Light green for paid
                } else if (item.getDaysUntilDue() < 0) {
                    setStyle("-fx-background-color: #fee2e2;"); // Light red for overdue
                } else if (item.getDaysUntilDue() >= 0 && item.getDaysUntilDue() <= 3) {
                    setStyle("-fx-background-color: #fef3c7;"); // Light yellow for upcoming
                } else {
                    setStyle("");
                }
            }
        });

        // Name column
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDto().getName()));
        // Category column
        colCategory.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getDto().getCategory();
            String key = "category." + category;
            String localized = bundle.containsKey(key) ? bundle.getString(key) : category;
            return new SimpleStringProperty(localized);
        });
        // Default Amount column
        colAmount.setCellValueFactory(cellData -> new SimpleStringProperty(
                String.format("%.2f ₺", cellData.getValue().getDto().getDefaultAmount())));
        // Frequency column
        colFrequency.setCellValueFactory(cellData -> {
            String freq = cellData.getValue().getDto().getFrequency();
            String localized = "WEEKLY".equals(freq) ? "Haftalık" : "Aylık";
            return new SimpleStringProperty(localized);
        });
        // Editable Pay Amount column with TextField
        colPayAmount.setCellFactory(column -> new TableCell<FixedExpenseRow, String>() {
            private final TextField textField = new TextField();
            {
                textField.setStyle("-fx-padding: 2; -fx-font-size: 12px;");
                textField.textProperty().addListener((obs, oldVal, newVal) -> {
                    if (getTableRow() != null && getTableRow().getItem() != null) {
                        FixedExpenseRow row = getTableRow().getItem();
                        row.setPayAmount(newVal);
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
        colPayAmount.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPayAmount()));

        // Linked Payment Info column - shows deduction info for linked expenses
        colLinkedInfo.setCellValueFactory(cellData -> {
            FixedExpenseDefinitionDTO dto = cellData.getValue().getDto();
            if (dto.getLinkedPaymentsThisMonth() != null
                    && dto.getLinkedPaymentsThisMonth().compareTo(java.math.BigDecimal.ZERO) > 0) {
                // Calculate remaining
                java.math.BigDecimal remaining = dto.getDefaultAmount().subtract(dto.getLinkedPaymentsThisMonth());
                if (remaining.compareTo(java.math.BigDecimal.ZERO) < 0) {
                    remaining = java.math.BigDecimal.ZERO;
                }
                String info = String.format("%s: %.0f₺\nKalan: %.0f₺",
                        dto.getLinkedExpenseName() != null ? dto.getLinkedExpenseName() : "Bağlı",
                        dto.getLinkedPaymentsThisMonth(),
                        remaining);
                return new SimpleStringProperty(info);
            }
            return new SimpleStringProperty("-");
        });

        // Day of Month column
        colDayOfMonth.setCellValueFactory(cellData -> {
            Integer day = cellData.getValue().getDto().getDayOfMonth();
            return new SimpleStringProperty(day != null ? day.toString() : "-");
        });
        // Days Until column
        colDaysUntil.setCellValueFactory(cellData -> {
            int daysUntil = cellData.getValue().getDaysUntilDue();
            String text = daysUntil < 0 ? "Geçti" : daysUntil == 0 ? "Bugün" : daysUntil + " gün";
            return new SimpleStringProperty(text);
        });
        // Status column
        colStatus.setCellValueFactory(cellData -> {
            boolean isPaid = cellData.getValue().getDto().isPaidThisMonth();
            String status = isPaid ? bundle.getString("finance.pay_fixed_expense_dialog.paid") : "";
            return new SimpleStringProperty(status);
        });
        // Row factory DISABLED FOR DEBUGGING - testing if default selection works
    }

    private void loadFixedExpenses() {
        financeApi.getFixedExpenses().enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
            @Override
            public void onResponse(Call<List<FixedExpenseDefinitionDTO>> call,
                    Response<List<FixedExpenseDefinitionDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Debug: Print isPaidThisMonth values
                    System.out.println("=== Fixed Expenses API Response ===");
                    for (FixedExpenseDefinitionDTO dto : response.body()) {
                        System.out.println("  " + dto.getName() + " | isPaidThisMonth: " + dto.isPaidThisMonth()
                                + " | dayOfMonth: " + dto.getDayOfMonth());
                    }

                    Platform.runLater(() -> {
                        List<FixedExpenseRow> rows = response.body().stream()
                                .map(dto -> new FixedExpenseRow(dto, calculateDaysUntilDue(dto.getDayOfMonth())))
                                .collect(Collectors.toList());
                        expensesTable.setItems(FXCollections.observableArrayList(rows));
                        // Disable selection for already paid expenses
                        expensesTable.getItems().forEach(row -> {
                            if (row.getDto().isPaidThisMonth()) {
                                row.selectedProperty().setValue(false);
                                row.setSelectable(false);
                            }
                        });
                    });
                }
            }

            @Override
            public void onFailure(Call<List<FixedExpenseDefinitionDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Sabit giderler yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    private int calculateDaysUntilDue(Integer dayOfMonth) {
        if (dayOfMonth == null)
            return 999; // No due date
        LocalDate today = LocalDate.now();
        int currentDay = today.getDayOfMonth();
        // Return negative if overdue, positive if upcoming
        return dayOfMonth - currentDay;
    }

    @FXML
    private void handlePay() {
        List<FixedExpenseRow> selectedRows = expensesTable.getItems().stream()
                .filter(row -> row.selectedProperty().get() && !row.getDto().isPaidThisMonth())
                .collect(Collectors.toList());
        if (selectedRows.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    bundle.getString("finance.pay_fixed_expense_dialog.no_selection"));
            return;
        }

        // Get selected payment date
        LocalDate paymentDate = dtPaymentDate.getValue();
        if (paymentDate == null) {
            paymentDate = LocalDate.now();
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(selectedRows.size());
        final LocalDate finalDate = paymentDate;

        for (FixedExpenseRow row : selectedRows) {
            // Parse custom amount if provided, otherwise pass null to use default
            BigDecimal customAmount = null;
            if (row.getPayAmount() != null && !row.getPayAmount().trim().isEmpty()) {
                try {
                    // Handle both comma and dot as decimal separator
                    String amountStr = row.getPayAmount().replace(",", ".").trim();
                    customAmount = new BigDecimal(amountStr);
                } catch (NumberFormatException e) {
                    AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                            "Geçersiz tutar: " + row.getDto().getName());
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
                                // Try to extract error message from response
                                try {
                                    String errorBody = response.errorBody().string();
                                    // Parse JSON to get error message
                                    if (errorBody.contains("\"error\"")) {
                                        int start = errorBody.indexOf("\"error\":\"") + 9;
                                        int end = errorBody.indexOf("\"", start);
                                        String errorMsg = errorBody.substring(start, end);
                                        Platform.runLater(() -> {
                                            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", errorMsg);
                                        });
                                    }
                                } catch (Exception e) {
                                    // Fallback error message
                                    Platform.runLater(() -> {
                                        AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                                                "Gider ödemesi başarısız oldu!");
                                    });
                                }
                            }
                            checkCompletion(successCount.get(), failureCount.get(), totalCount.get());
                        }

                        @Override
                        public void onFailure(Call<ExpenseDTO> call, Throwable t) {
                            failureCount.incrementAndGet();
                            Platform.runLater(() -> {
                                AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                                        "Bağlantı hatası: " + t.getMessage());
                            });
                            checkCompletion(successCount.get(), failureCount.get(), totalCount.get());
                        }
                    });
        }
    }

    private void checkCompletion(int success, int failure, int total) {
        if (success + failure == total) {
            Platform.runLater(() -> {
                if (success > 0) {
                    AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                            success + " gider başarıyla ödendi!");
                    if (onSuccess != null)
                        onSuccess.run();
                }
                if (success > 0 || failure == total) {
                    closeDialog();
                }
            });
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) expensesTable.getScene().getWindow();
        stage.close();
    }

    public void setOnSuccess(Runnable onSuccess) {
        this.onSuccess = onSuccess;
    }

    // Inner class for table rows
    public static class FixedExpenseRow {
        private final FixedExpenseDefinitionDTO dto;
        private final int daysUntilDue;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private boolean selectable = true;
        private String payAmount = ""; // Custom payment amount entered by user

        public FixedExpenseRow(FixedExpenseDefinitionDTO dto, int daysUntilDue) {
            this.dto = dto;
            this.daysUntilDue = daysUntilDue;
        }

        public FixedExpenseDefinitionDTO getDto() {
            return dto;
        }

        public int getDaysUntilDue() {
            return daysUntilDue;
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }

        public void setSelectable(boolean selectable) {
            this.selectable = selectable;
            if (!selectable)
                selected.set(false);
        }

        public String getPayAmount() {
            return payAmount;
        }

        public void setPayAmount(String payAmount) {
            this.payAmount = payAmount;
        }
    }
}
