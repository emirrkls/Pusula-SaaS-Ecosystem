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

        setupTable();
        loadFixedExpenses();
    }

    private void setupTable() {
        // Checkbox column
        colSelect.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
        colSelect.setEditable(true);
        expensesTable.setEditable(true);
        // Name column
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDto().getName()));
        // Category column
        colCategory.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getDto().getCategory();
            String key = "category." + category;
            String localized = bundle.containsKey(key) ? bundle.getString(key) : category;
            return new SimpleStringProperty(localized);
        });
        // Amount column
        colAmount.setCellValueFactory(cellData -> new SimpleStringProperty(
                String.format("%.2f ₺", cellData.getValue().getDto().getDefaultAmount())));
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
        // Row factory for highlighting
        expensesTable.setRowFactory(tv -> new TableRow<FixedExpenseRow>() {
            @Override
            protected void updateItem(FixedExpenseRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    if (item.getDto().isPaidThisMonth()) {
                        // Gray out paid expenses
                        setStyle("-fx-background-color: #ecf0f1; -fx-opacity: 0.7;");
                    } else if (item.getDaysUntilDue() >= 0 && item.getDaysUntilDue() <= 3) {
                        // Highlight upcoming (within 3 days)
                        setStyle("-fx-background-color: #fff3cd;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
    }

    private void loadFixedExpenses() {
        financeApi.getFixedExpenses(1L).enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
            @Override
            public void onResponse(Call<List<FixedExpenseDefinitionDTO>> call,
                    Response<List<FixedExpenseDefinitionDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
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
        LocalDate dueDate = LocalDate.of(today.getYear(), today.getMonth(), dayOfMonth);
        if (dueDate.isBefore(today)) {
            dueDate = dueDate.plusMonths(1);
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
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
            financeApi.payFixedExpense(row.getDto().getId(), 1L, finalDate.toString())
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
    }
}
