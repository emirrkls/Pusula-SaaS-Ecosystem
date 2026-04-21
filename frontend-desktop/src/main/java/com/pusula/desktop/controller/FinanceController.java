package com.pusula.desktop.controller;

import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.api.CurrentAccountApi;
import com.pusula.desktop.dto.*;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CurrencyTextField;
import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class FinanceController {

    // Left Panel - Daily Operations
    @FXML
    private Label currentDateLabel;
    @FXML
    private javafx.scene.layout.HBox paymentAlertBox;
    @FXML
    private Label alertMessageLabel;
    @FXML
    private javafx.scene.layout.HBox overduePaymentBox;
    @FXML
    private Label overdueMessageLabel;
    @FXML
    private TableView<DailySummaryDTO.ExpenseItemDTO> todayExpensesTable;
    @FXML
    private TableColumn<DailySummaryDTO.ExpenseItemDTO, String> colTodayCategory;
    @FXML
    private TableColumn<DailySummaryDTO.ExpenseItemDTO, String> colTodayDescription;
    @FXML
    private TableColumn<DailySummaryDTO.ExpenseItemDTO, String> colTodayAmount;
    @FXML
    private TableColumn<DailySummaryDTO.ExpenseItemDTO, Void> colTodayActions;
    @FXML
    private Label todayExpenseLabel;
    @FXML
    private TableView<DailySummaryDTO.IncomeItemDTO> todayIncomesTable;
    @FXML
    private TableColumn<DailySummaryDTO.IncomeItemDTO, String> colIncomeCustomer;
    @FXML
    private TableColumn<DailySummaryDTO.IncomeItemDTO, String> colIncomeTicket;
    @FXML
    private TableColumn<DailySummaryDTO.IncomeItemDTO, String> colIncomeAmount;
    @FXML
    private TableView<MonthlySummaryDTO> reportsTable;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportPeriod;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportIncome;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportExpense;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportCarryOver;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportProfit;
    @FXML
    private TableColumn<MonthlySummaryDTO, Void> colReportActions;
    @FXML
    private Label todayIncomeLabel;
    @FXML
    private Label netCashLabel;
    @FXML
    private Button closeDayButton;

    // Right Panel - Analytics
    @FXML
    private LineChart<String, Number> trendChart;
    @FXML
    private PieChart expensePieChart;

    // Current Accounts Tab
    @FXML
    private TableView<CurrentAccountDTO> currentAccountsTable;
    @FXML
    private TableColumn<CurrentAccountDTO, String> colAccountCustomer;
    @FXML
    private TableColumn<CurrentAccountDTO, String> colAccountBalance;
    @FXML
    private TableColumn<CurrentAccountDTO, String> colAccountLastUpdated;
    @FXML
    private TableColumn<CurrentAccountDTO, Void> colAccountActions;

    // Inventory Value Card
    @FXML
    private Label inventoryValueLabel;

    // Business Assets Tab Labels
    @FXML
    private Label assetInventoryValueLabel;

    @FXML
    private Label assetNetCashLabel;

    private FinanceApi financeApi;
    private ResourceBundle bundle;
    private LocalDate currentDate;
    private boolean isDayClosed = false;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
        financeApi = RetrofitClient.getClient().create(FinanceApi.class);
        currentDate = LocalDate.now();

        setupDateDisplay();
        setupTodayExpensesTable();
        setupTodayIncomesTable();

        loadDailySummary(currentDate);
        checkUpcomingPayments();
        load30DayTrends();
        loadCategoryPieChart();
        setupReportsTable();
        loadMonthlyReports();
        setupCurrentAccountsTable();
        loadCurrentAccounts();
        loadInventoryValue();
        loadBusinessAssets();
    }

    private void setupDateDisplay() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("tr-TR"));
        currentDateLabel.setText(currentDate.format(formatter));
    }

    private void setupTodayExpensesTable() {
        colTodayCategory.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getCategory();
            String key = "category." + category;
            String localizedCategory = bundle.containsKey(key) ? bundle.getString(key) : category;
            return new javafx.beans.property.SimpleStringProperty(localizedCategory);
        });

        colTodayDescription.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDescription()));

        colTodayAmount.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f ₺", cellData.getValue().getAmount())));

        // Actions column with Edit and Delete buttons
        colTodayActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("✏️");
            private final Button btnDelete = new Button("🗑️");
            private final javafx.scene.layout.HBox hbox = new javafx.scene.layout.HBox(5, btnEdit, btnDelete);

            {
                btnEdit.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 14px;");
                btnDelete.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 14px;");

                btnEdit.setOnAction(event -> {
                    DailySummaryDTO.ExpenseItemDTO expense = getTableView().getItems().get(getIndex());
                    handleEditExpense(expense);
                });

                btnDelete.setOnAction(event -> {
                    DailySummaryDTO.ExpenseItemDTO expense = getTableView().getItems().get(getIndex());
                    handleDeleteExpense(expense);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : hbox);
            }
        });
    }

    private void setupTodayIncomesTable() {
        colIncomeCustomer.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCustomerName()));

        colIncomeTicket.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty("#" + cellData.getValue().getTicketId()));

        colIncomeAmount.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getAmount())));
    }

    private void loadDailySummary(LocalDate date) {
        String dateStr = date.toString();
        financeApi.getDailySummary(1L, dateStr).enqueue(new Callback<DailySummaryDTO>() {
            @Override
            public void onResponse(Call<DailySummaryDTO> call, Response<DailySummaryDTO> response) {
                if (response.isSuccessful() && response.body() != null) {
                    DailySummaryDTO summary = response.body();
                    Platform.runLater(() -> {
                        updateDailySummaryUI(summary);
                    });
                }
            }

            @Override
            public void onFailure(Call<DailySummaryDTO> call, Throwable t) {
                System.err.println("Failed to load daily summary: " + t.getMessage());
            }
        });
    }

    private void updateDailySummaryUI(DailySummaryDTO summary) {
        // Update expense table
        todayExpensesTable.setItems(FXCollections.observableArrayList(summary.getExpenseDetails()));
        todayExpenseLabel.setText(formatCurrency(summary.getTotalExpense()));

        // Update income table
        todayIncomesTable.setItems(FXCollections.observableArrayList(summary.getIncomeDetails()));
        todayIncomeLabel.setText(formatCurrency(summary.getTotalIncome()));

        // Update net cash
        netCashLabel.setText(formatCurrency(summary.getNetCash()));
        updateNetCashColor(summary.getNetCash());

        isDayClosed = summary.isClosed();
        closeDayButton.setDisable(isDayClosed);
        if (isDayClosed) {
            closeDayButton.setText(bundle.getString("finance.already_closed"));
            closeDayButton.setStyle(
                    "-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px; -fx-padding: 12;");
        }
    }

    private void updateNetCashColor(BigDecimal netCash) {
        if (netCash.compareTo(BigDecimal.ZERO) > 0) {
            netCashLabel.setStyle("-fx-text-fill: #27ae60;");
        } else if (netCash.compareTo(BigDecimal.ZERO) < 0) {
            netCashLabel.setStyle("-fx-text-fill: #e74c3c;");
        } else {
            netCashLabel.setStyle("-fx-text-fill: #34495e;");
        }
    }

    private void checkUpcomingPayments() {
        // Check for both upcoming and overdue payments
        financeApi.getFixedExpenses(1L).enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
            @Override
            public void onResponse(Call<List<FixedExpenseDefinitionDTO>> call,
                    Response<List<FixedExpenseDefinitionDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<FixedExpenseDefinitionDTO> allExpenses = response.body();
                    int currentDay = LocalDate.now().getDayOfMonth();

                    // Filter overdue (unpaid AND due day has passed)
                    List<FixedExpenseDefinitionDTO> overdue = allExpenses.stream()
                            .filter(e -> !e.isPaidThisMonth() && e.getDayOfMonth() != null
                                    && e.getDayOfMonth() < currentDay)
                            .collect(Collectors.toList());

                    // Filter upcoming (unpaid AND due within 3 days)
                    List<FixedExpenseDefinitionDTO> upcoming = allExpenses.stream()
                            .filter(e -> !e.isPaidThisMonth() && e.getDayOfMonth() != null)
                            .filter(e -> e.getDayOfMonth() >= currentDay && e.getDayOfMonth() <= currentDay + 3)
                            .collect(Collectors.toList());

                    // Debug logging
                    System.out.println("=== Overdue/Upcoming Check ===");
                    System.out.println("Current day: " + currentDay);
                    System.out.println("Total expenses: " + allExpenses.size());
                    System.out.println("Overdue count: " + overdue.size());
                    System.out.println("Upcoming count: " + upcoming.size());
                    for (FixedExpenseDefinitionDTO e : allExpenses) {
                        System.out.println("  - " + e.getName() + " | Day: " + e.getDayOfMonth() + " | Paid: "
                                + e.isPaidThisMonth());
                    }

                    Platform.runLater(() -> {
                        // Handle OVERDUE (RED alert)
                        if (overdue.isEmpty()) {
                            overduePaymentBox.setVisible(false);
                            overduePaymentBox.setManaged(false);
                        } else {
                            overduePaymentBox.setVisible(true);
                            overduePaymentBox.setManaged(true);
                            StringBuilder sb = new StringBuilder();
                            for (FixedExpenseDefinitionDTO exp : overdue) {
                                int daysLate = currentDay - exp.getDayOfMonth();
                                sb.append(exp.getName()).append(" (").append(daysLate).append(" gün gecikti), ");
                            }
                            String overdueNames = sb.length() > 2 ? sb.substring(0, sb.length() - 2) : sb.toString();
                            overdueMessageLabel.setText("🚨 Geciken ödemeler: " + overdueNames);
                        }

                        // Handle UPCOMING (YELLOW alert) - also show if there are any overdue items
                        if (upcoming.isEmpty() && overdue.isEmpty()) {
                            paymentAlertBox.setVisible(false);
                            paymentAlertBox.setManaged(false);
                        } else if (!upcoming.isEmpty()) {
                            paymentAlertBox.setVisible(true);
                            paymentAlertBox.setManaged(true);
                            String message = String.format("Dikkat: %d adet sabit giderin ödeme günü yaklaşıyor!",
                                    upcoming.size());
                            alertMessageLabel.setText(message);
                        } else {
                            // If only overdue items, show yellow alert as a reminder too
                            paymentAlertBox.setVisible(true);
                            paymentAlertBox.setManaged(true);
                            alertMessageLabel.setText("⚠️ Ödenmemiş giderler mevcut - Sabit Gider Öde'ye tıklayın.");
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<FixedExpenseDefinitionDTO>> call, Throwable t) {
                System.err.println("Failed to load fixed expenses: " + t.getMessage());
            }
        });
    }

    // Removed: Individual pay button handler - now using centralized dialog only

    private void load30DayTrends() {
        financeApi.get30DayTotals(1L).enqueue(new Callback<List<DailyTotalDTO>>() {
            @Override
            public void onResponse(Call<List<DailyTotalDTO>> call, Response<List<DailyTotalDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        populateTrendChart(response.body());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<DailyTotalDTO>> call, Throwable t) {
                System.err.println("Failed to load 30-day trends: " + t.getMessage());
            }
        });
    }

    private void populateTrendChart(List<DailyTotalDTO> dailyTotals) {
        trendChart.getData().clear();

        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName(bundle.getString("chart.income_series"));

        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName(bundle.getString("chart.expense_series"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");

        for (DailyTotalDTO total : dailyTotals) {
            String dateStr = total.getDate().format(formatter);
            incomeSeries.getData().add(new XYChart.Data<>(dateStr, total.getIncome()));
            expenseSeries.getData().add(new XYChart.Data<>(dateStr, total.getExpense()));
        }

        trendChart.getData().addAll(incomeSeries, expenseSeries);
    }

    private void loadCategoryPieChart() {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now();

        financeApi.getCategoryReport(1L, startOfMonth.toString(), endOfMonth.toString())
                .enqueue(new Callback<CategoryReportDTO>() {
                    @Override
                    public void onResponse(Call<CategoryReportDTO> call, Response<CategoryReportDTO> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Platform.runLater(() -> {
                                updateExpensePieChart(response.body().getBreakdown());
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<CategoryReportDTO> call, Throwable t) {
                        System.err.println("Failed to load category report: " + t.getMessage());
                    }
                });
    }

    private void updateExpensePieChart(Map<String, BigDecimal> breakdown) {
        expensePieChart.getData().clear();

        for (Map.Entry<String, BigDecimal> entry : breakdown.entrySet()) {
            String categoryKey = "category." + entry.getKey();
            String categoryName = bundle.containsKey(categoryKey) ? bundle.getString(categoryKey) : entry.getKey();

            PieChart.Data slice = new PieChart.Data(
                    categoryName + " (" + formatCurrency(entry.getValue()) + ")",
                    entry.getValue().doubleValue());
            expensePieChart.getData().add(slice);
        }
    }

    @FXML
    private void handleAddExpenseUnified() {
        openUnifiedExpenseDialog(false);
    }

    private void openUnifiedExpenseDialog(boolean switchToFixedTab) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/unified_expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            UnifiedExpenseDialogController controller = loader.getController();
            controller.setOnSaveSuccess(() -> {
                loadDailySummary(currentDate);
                loadCategoryPieChart();
                load30DayTrends();
                checkUpcomingPayments();
            });

            if (switchToFixedTab) {
                controller.switchToFixedTab();
            }

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("unified_expense.title"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Error",
                    "Could not open expense dialog: " + e.getMessage());
        }
    }

    private void handleEditExpense(DailySummaryDTO.ExpenseItemDTO expenseItem) {
        try {
            ExpenseDTO expense = new ExpenseDTO();
            expense.setId(expenseItem.getId());
            expense.setCategory(expenseItem.getCategory());
            expense.setDescription(expenseItem.getDescription());
            expense.setAmount(expenseItem.getAmount());
            expense.setDate(currentDate.toString());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/unified_expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            UnifiedExpenseDialogController controller = loader.getController();
            controller.setExpenseToEdit(expense);
            controller.setOnSaveSuccess(() -> {
                loadDailySummary(currentDate);
                loadCategoryPieChart();
                load30DayTrends();
            });

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("unified_expense.title"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Error",
                    "Could not open expense dialog: " + e.getMessage());
        }
    }

    private void handleDeleteExpense(DailySummaryDTO.ExpenseItemDTO expenseItem) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Gider Sil");
        confirm.setHeaderText("Bu gideri silmek istediğinizden emin misiniz?");
        confirm.setContentText(expenseItem.getDescription() + " - " + formatCurrency(expenseItem.getAmount()));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                financeApi.deleteExpense(expenseItem.getId()).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Platform.runLater(() -> {
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, null,
                                        "Başarılı", "Gider silindi!");
                                loadDailySummary(currentDate);
                                loadCategoryPieChart();
                                load30DayTrends();
                            });
                        } else {
                            Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                    null, "Hata", "Gider silinemedi!"));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                null, "Hata", "Bağlantı hatası: " + t.getMessage()));
                    }
                });
            }
        });
    }

    @FXML
    private void handleCloseDay() {
        if (isDayClosed) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null,
                    bundle.getString("finance.warning"),
                    bundle.getString("dialog.close_day.already_closed"));
            return;
        }

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle(bundle.getString("finance.close_day"));
        confirmationAlert.setHeaderText(bundle.getString("dialog.close_day.confirm"));
        confirmationAlert.setContentText(String.format(
                "Gider: %s\nNet Kasa: %s",
                todayExpenseLabel.getText(),
                netCashLabel.getText()));

        confirmationAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                performDayClosing();
            }
        });
    }

    private void performDayClosing() {
        CloseDayRequest request = CloseDayRequest.builder()
                .companyId(1L)
                .date(currentDate)
                .userId(1L)
                .build();

        financeApi.closeDay(request).enqueue(new Callback<DailyClosingDTO>() {
            @Override
            public void onResponse(Call<DailyClosingDTO> call, Response<DailyClosingDTO> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null,
                                bundle.getString("finance.success"),
                                bundle.getString("dialog.close_day.success"));
                        loadDailySummary(currentDate);
                    });
                }
            }

            @Override
            public void onFailure(Call<DailyClosingDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                            "Hata", bundle.getString("dialog.close_day.error") + "\n" + t.getMessage());
                });
            }
        });
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "0.00 ₺";
        return String.format("%.2f ₺", amount);
    }

    private void setupReportsTable() {
        colReportPeriod.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDisplayPeriod()));

        // CarryOver column with color coding
        colReportCarryOver.setCellValueFactory(cellData -> {
            java.math.BigDecimal carryOver = cellData.getValue().getCarryOver();
            String text = carryOver != null ? formatCurrency(carryOver) : "0.00 ₺";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        colReportCarryOver.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    MonthlySummaryDTO dto = getTableRow().getItem();
                    if (dto != null && dto.getCarryOver() != null) {
                        if (dto.getCarryOver().compareTo(java.math.BigDecimal.ZERO) > 0) {
                            setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        } else if (dto.getCarryOver().compareTo(java.math.BigDecimal.ZERO) < 0) {
                            setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            }
        });

        colReportIncome.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getTotalIncome())));

        colReportExpense.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getTotalExpense())));

        colReportProfit.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getNetProfit())));

        // Actions column with PDF download button
        colReportActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnPDF = new Button("📄 PDF");
            {
                btnPDF.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand;");
                btnPDF.setOnAction(event -> {
                    MonthlySummaryDTO summary = getTableView().getItems().get(getIndex());
                    handleDownloadPDF(summary.getPeriod());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnPDF);
            }
        });
    }

    private void loadMonthlyReports() {
        financeApi.getMonthlyArchives(1L).enqueue(new Callback<List<MonthlySummaryDTO>>() {
            @Override
            public void onResponse(Call<List<MonthlySummaryDTO>> call, Response<List<MonthlySummaryDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        reportsTable.setItems(FXCollections.observableArrayList(response.body()));
                    });
                }
            }

            @Override
            public void onFailure(Call<List<MonthlySummaryDTO>> call, Throwable t) {
                System.err.println("Failed to load reports: " + t.getMessage());
            }
        });
    }

    private void handleDownloadPDF(String period) {
        financeApi.downloadMonthlyPDF(period, 1L).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        // Get PDF bytes first (on background thread)
                        final byte[] pdfBytes = response.body().bytes();

                        // Then show file chooser on JavaFX thread
                        Platform.runLater(() -> {
                            try {
                                FileChooser fileChooser = new FileChooser();
                                fileChooser.setTitle("PDF Kaydet");
                                fileChooser.setInitialFileName("mali_rapor_" + period + ".pdf");
                                fileChooser.getExtensionFilters().add(
                                        new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

                                javafx.stage.Stage stage = (javafx.stage.Stage) reportsTable.getScene().getWindow();
                                File file = fileChooser.showSaveDialog(stage);

                                if (file != null) {
                                    // Write PDF bytes to file
                                    Files.write(file.toPath(), pdfBytes);
                                    AlertHelper.showAlert(Alert.AlertType.INFORMATION, null,
                                            "Başarılı", "PDF başarıyla kaydedildi!");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                                        "Hata", "PDF kaydedilemedi: " + e.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        Platform.runLater(() -> {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                                    "Hata", "PDF okunamadı: " + e.getMessage());
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                            "Hata", "PDF indirilemedi: " + t.getMessage());
                });
            }
        });
    }

    // ========== CURRENT ACCOUNTS TAB ==========

    private void setupCurrentAccountsTable() {
        colAccountCustomer.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCustomerName()));

        colAccountBalance.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getBalance())));

        colAccountLastUpdated.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getLastUpdated() != null
                        ? cellData.getValue().getLastUpdated().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                        : "-"));

        // Actions column with Edit Balance button
        colAccountActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Düzenle");

            {
                btnEdit.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
                btnEdit.setOnAction(event -> {
                    CurrentAccountDTO account = getTableView().getItems().get(getIndex());
                    handleEditBalance(account);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnEdit);
            }
        });
    }

    private void loadCurrentAccounts() {
        System.out.println("=== loadCurrentAccounts() called ===");
        CurrentAccountApi api = RetrofitClient.getClient().create(CurrentAccountApi.class);
        System.out.println("Calling API: " + RetrofitClient.BASE_URL + "api/current-accounts");
        api.getAll(1L).enqueue(new Callback<List<CurrentAccountDTO>>() {
            @Override
            public void onResponse(Call<List<CurrentAccountDTO>> call, Response<List<CurrentAccountDTO>> response) {
                System.out.println("Response received: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    System.out.println("Success! Found " + response.body().size() + " accounts");
                    Platform.runLater(() -> {
                        currentAccountsTable.setItems(FXCollections.observableArrayList(response.body()));
                    });
                } else {
                    System.err.println("API call failed: " + response.code() + " - " + response.message());
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                                "Hata", "Cari hesaplar yüklenemedi: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<CurrentAccountDTO>> call, Throwable t) {
                System.err.println("Failed to load current accounts: " + t.getMessage());
                t.printStackTrace();
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                            "Bağlantı Hatası", "Cari hesaplar yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    private void handleEditBalance(CurrentAccountDTO account) {
        // Create payment dialog with amount and discount fields
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Cari Hesap Ödemesi");
        dialog.setHeaderText(account.getCustomerName() + " - Borç: " +
                String.format("%.2f TL", account.getBalance()));

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        CurrencyTextField paymentField = new CurrencyTextField();
        paymentField.setPromptText("0,00");

        CurrencyTextField discountField = new CurrencyTextField();
        discountField.setPromptText("0,00");
        discountField.setText("0");

        grid.add(new Label("Ödeme Tutarı:"), 0, 0);
        grid.add(paymentField, 1, 0);
        grid.add(new Label("İndirim:"), 0, 1);
        grid.add(discountField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("payment", paymentField.getText());
                result.put("discount", discountField.getText());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                BigDecimal payment = paymentField.getRawValue();
                BigDecimal discount = discountField.getRawValue();

                Map<String, Object> requestBody = new java.util.HashMap<>();
                requestBody.put("paymentAmount", payment);
                requestBody.put("discount", discount);

                CurrentAccountApi api = RetrofitClient.getClient().create(CurrentAccountApi.class);
                api.payDebt(account.getId(), requestBody).enqueue(new Callback<CurrentAccountDTO>() {
                    @Override
                    public void onResponse(Call<CurrentAccountDTO> call, Response<CurrentAccountDTO> response) {
                        if (response.isSuccessful()) {
                            Platform.runLater(() -> {
                                loadCurrentAccounts(); // Refresh table
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, null,
                                        "Başarılı", "Ödeme kaydedildi.");
                            });
                        } else {
                            Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                                    "Hata", "Ödeme kaydedilemedi: " + response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call<CurrentAccountDTO> call, Throwable t) {
                        Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                                "Hata", t.getMessage()));
                    }
                });
            } catch (Exception e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                        "Hata", "Geçersiz tutar: " + e.getMessage());
            }
        });
    }

    // ========== INVENTORY VALUE CARD ==========

    private void loadInventoryValue() {
        financeApi.getInventoryValue(1L).enqueue(new Callback<Map<String, BigDecimal>>() {
            @Override
            public void onResponse(Call<Map<String, BigDecimal>> call, Response<Map<String, BigDecimal>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Extract totalValue from the response map
                    BigDecimal totalValue = response.body().getOrDefault("totalValue", BigDecimal.ZERO);

                    Platform.runLater(() -> {
                        if (inventoryValueLabel != null) {
                            inventoryValueLabel.setText(formatCurrency(totalValue));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<Map<String, BigDecimal>> call, Throwable t) {
                System.err.println("Failed to load inventory value: " + t.getMessage());
                Platform.runLater(() -> {
                    if (inventoryValueLabel != null) {
                        inventoryValueLabel.setText("₺0.00");
                    }
                });
            }
        });
    }

    // ========== BUSINESS ASSETS TAB ==========

    private void loadBusinessAssets() {
        // Load inventory value for assets tab
        financeApi.getInventoryValue(1L).enqueue(new Callback<Map<String, BigDecimal>>() {
            @Override
            public void onResponse(Call<Map<String, BigDecimal>> call, Response<Map<String, BigDecimal>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BigDecimal totalValue = response.body().getOrDefault("totalValue", BigDecimal.ZERO);
                    Platform.runLater(() -> {
                        if (assetInventoryValueLabel != null) {
                            assetInventoryValueLabel.setText(formatCurrency(totalValue));
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<Map<String, BigDecimal>> call, Throwable t) {
                System.err.println("Failed to load asset inventory value: " + t.getMessage());
                Platform.runLater(() -> {
                    if (assetInventoryValueLabel != null) {
                        assetInventoryValueLabel.setText("₺0.00");
                    }
                });
            }
        });

        // Load net cash value for assets tab (CUMULATIVE, not just today)
        financeApi.getCumulativeSummary(1L).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object totalCashObj = response.body().get("totalCash");
                    BigDecimal totalCash = totalCashObj != null
                            ? new BigDecimal(totalCashObj.toString())
                            : BigDecimal.ZERO;
                    Platform.runLater(() -> {
                        if (assetNetCashLabel != null) {
                            assetNetCashLabel.setText(formatCurrency(totalCash));
                            // Color code: green for positive, red for negative
                            // Preserve the 32px font size from FXML
                            if (totalCash.compareTo(BigDecimal.ZERO) > 0) {
                                assetNetCashLabel.setStyle(
                                        "-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 32px;");
                            } else if (totalCash.compareTo(BigDecimal.ZERO) < 0) {
                                assetNetCashLabel.setStyle(
                                        "-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 32px;");
                            } else {
                                assetNetCashLabel
                                        .setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 32px;");
                            }
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                System.err.println("Failed to load cumulative net cash: " + t.getMessage());
                Platform.runLater(() -> {
                    if (assetNetCashLabel != null) {
                        assetNetCashLabel.setText("₺0.00");
                    }
                });
            }
        });
    }
}
