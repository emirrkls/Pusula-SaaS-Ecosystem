package com.pusula.desktop.controller;

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
}
