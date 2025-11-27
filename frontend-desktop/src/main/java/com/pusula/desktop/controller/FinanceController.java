package com.pusula.desktop.controller;

import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.dto.*;
import com.pusula.desktop.util.AlertHelper;
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
    private javafx.scene.layout.VBox paymentAlertBox; // Alert container
    @FXML
    private Label alertMessageLabel; // Alert message text
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
        financeApi.getUpcomingFixedExpenses(1L, 3).enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
            @Override
            public void onResponse(Call<List<FixedExpenseDefinitionDTO>> call,
                    Response<List<FixedExpenseDefinitionDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<FixedExpenseDefinitionDTO> upcoming = response.body();
                    Platform.runLater(() -> {
                        if (upcoming == null || upcoming.isEmpty()) {
                            paymentAlertBox.setVisible(false);
                            paymentAlertBox.setManaged(false);
                        } else {
                            paymentAlertBox.setVisible(true);
                            paymentAlertBox.setManaged(true);
                            String message = String.format("Dikkat: %d adet sabit giderin ödeme günü yaklaşıyor!",
                                    upcoming.size());
                            alertMessageLabel.setText(message);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<FixedExpenseDefinitionDTO>> call, Throwable t) {
                System.err.println("Failed to load upcoming expenses: " + t.getMessage());
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
    private void handleAddExpense() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            ExpenseDialogController controller = loader.getController();
            controller.setOnSaveSuccess(() -> {
                loadDailySummary(currentDate);
                loadCategoryPieChart();
                load30DayTrends();
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("finance.add_expense"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", "Gider ekle penceresi açılamadı!");
        }
    }

    @FXML
    private void handlePayFixedExpense() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/pay_fixed_expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            PayFixedExpenseDialogController controller = loader.getController();
            controller.setOnSuccess(() -> {
                loadDailySummary(currentDate);
                loadCategoryPieChart();
                load30DayTrends();
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("finance.pay_fixed_expense_dialog.title"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", "Sabit gider ödeme penceresi açılamadı!");
        }
    }

    private void handleEditExpense(DailySummaryDTO.ExpenseItemDTO expenseItem) {
        try {
            // Convert ExpenseItemDTO to ExpenseDTO
            ExpenseDTO expense = new ExpenseDTO();
            expense.setId(expenseItem.getId());
            expense.setCategory(expenseItem.getCategory());
            expense.setDescription(expenseItem.getDescription());
            expense.setAmount(expenseItem.getAmount());
            expense.setDate(currentDate.toString());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            ExpenseDialogController controller = loader.getController();
            controller.setExpenseToEdit(expense); // Set edit mode
            controller.setOnSaveSuccess(() -> {
                loadDailySummary(currentDate);
                loadCategoryPieChart();
                load30DayTrends();
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Gider Düzenle");
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", "Gider düzenleme penceresi açılamadı!");
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
}
