package com.pusula.desktop.controller;

import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.api.CurrentAccountApi;
import com.pusula.desktop.api.AccountPartyApi;
import com.pusula.desktop.dto.*;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CurrencyTextField;
import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
    private TableColumn<MonthlySummaryDTO, String> colReportCurrentAccount;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportExpense;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportCarryOver;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportProfit;
    @FXML
    private TableColumn<MonthlySummaryDTO, String> colReportClosingProfit;
    @FXML
    private TableColumn<MonthlySummaryDTO, Void> colReportActions;
    @FXML
    private VBox reportDetailContainer;
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
    @FXML
    private Label analyticsIncomeLabel;
    @FXML
    private Label analyticsExpenseLabel;
    @FXML
    private Label analyticsNetLabel;
    @FXML
    private Label analyticsTopCategoryLabel;
    @FXML
    private VBox expenseCategoryStateBox;
    @FXML
    private Label expenseCategoryStateTitle;
    @FXML
    private Label expenseCategoryStateAmount;
    @FXML
    private Label expenseCategoryStateCaption;

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
    @FXML
    private VBox currentAccountListPane;
    @FXML
    private VBox currentAccountDetailPane;
    @FXML
    private Label currentAccountDetailNameLabel;
    @FXML
    private Label currentAccountDetailBalanceLabel;
    @FXML
    private Label currentAccountTotalChargesLabel;
    @FXML
    private Label currentAccountTotalPaymentsLabel;
    @FXML
    private Label currentAccountTransactionCountLabel;
    @FXML
    private TextField currentAccountHistorySearchField;
    @FXML
    private DatePicker currentAccountHistoryStartDate;
    @FXML
    private DatePicker currentAccountHistoryEndDate;
    @FXML
    private TableView<CurrentAccountHistoryDTO.Transaction> currentAccountHistoryTable;
    @FXML
    private TableColumn<CurrentAccountHistoryDTO.Transaction, String> colCurrentAccountHistoryDate;
    @FXML
    private TableColumn<CurrentAccountHistoryDTO.Transaction, String> colCurrentAccountHistoryType;
    @FXML
    private TableColumn<CurrentAccountHistoryDTO.Transaction, String> colCurrentAccountHistoryDescription;
    @FXML
    private TableColumn<CurrentAccountHistoryDTO.Transaction, String> colCurrentAccountHistoryAmount;
    @FXML
    private TableColumn<CurrentAccountHistoryDTO.Transaction, String> colCurrentAccountHistoryBalance;
    @FXML
    private ProgressIndicator currentAccountHistoryProgress;

    // Inventory Value Card
    @FXML
    private Label inventoryValueLabel;

    // Business Assets Tab Labels
    @FXML
    private Label assetTotalEquityLabel;
    @FXML
    private Label assetInventoryValueLabel;
    @FXML
    private Label assetNetCashLabel;
    @FXML
    private Label assetBreakdownInventoryLabel;
    @FXML
    private Label assetBreakdownIncomeLabel;
    @FXML
    private Label assetBreakdownExpensesLabel;
    @FXML
    private Label assetBreakdownNetCashLabel;
    @FXML
    private Label assetBreakdownTotalLabel;

    private BigDecimal assetInventoryValue = BigDecimal.ZERO;
    private BigDecimal assetNetCashValue = BigDecimal.ZERO;
    private BigDecimal assetTotalIncome = BigDecimal.ZERO;
    private BigDecimal assetTotalExpenses = BigDecimal.ZERO;
    private final ObservableList<CurrentAccountHistoryDTO.Transaction> currentAccountHistory =
            FXCollections.observableArrayList();
    private CurrentAccountDTO selectedCurrentAccount;

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
        setupCurrentAccountHistoryTable();
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
                formatCurrency(cellData.getValue().getAmount())));

        // Compact overflow menu keeps destructive and non-destructive actions clearly separated.
        colTodayActions.setCellFactory(param -> new TableCell<>() {
            private final MenuButton actions = new MenuButton("İşlemler");
            private final MenuItem editItem = new MenuItem("Düzenle");
            private final MenuItem deleteItem = new MenuItem("Sil");

            {
                actions.getItems().addAll(editItem, new SeparatorMenuItem(), deleteItem);
                actions.getStyleClass().addAll("button-sm", "button-secondary", "action-menu-button");
                actions.setAccessibleText("Gider işlemleri");

                editItem.setOnAction(event -> {
                    DailySummaryDTO.ExpenseItemDTO expense = getTableView().getItems().get(getIndex());
                    handleEditExpense(expense);
                });

                deleteItem.setOnAction(event -> {
                    DailySummaryDTO.ExpenseItemDTO expense = getTableView().getItems().get(getIndex());
                    handleDeleteExpense(expense);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actions);
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
        financeApi.getDailySummary(dateStr).enqueue(new Callback<DailySummaryDTO>() {
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
            closeDayButton.setText("Mutabakat Kaydedildi");
            if (!closeDayButton.getStyleClass().contains("day-closed")) {
                closeDayButton.getStyleClass().add("day-closed");
            }
        } else {
            closeDayButton.setText("Mutabakatı Kaydet");
            closeDayButton.getStyleClass().remove("day-closed");
        }
    }

    private void updateNetCashColor(BigDecimal netCash) {
        applyAmountClass(netCashLabel, netCash, null);
    }

    private void checkUpcomingPayments() {
        // Check for both upcoming and overdue payments
        financeApi.getFixedExpenses().enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
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
                            overdueMessageLabel.setText(overdueNames);
                        }

                        // Upcoming and overdue are separate signals; do not repeat overdue items
                        // in a second warning banner.
                        if (upcoming.isEmpty()) {
                            paymentAlertBox.setVisible(false);
                            paymentAlertBox.setManaged(false);
                        } else {
                            paymentAlertBox.setVisible(true);
                            paymentAlertBox.setManaged(true);
                            String message = String.format("%d sabit giderin ödeme günü yaklaşıyor.",
                                    upcoming.size());
                            alertMessageLabel.setText(message);
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
        financeApi.get30DayTotals().enqueue(new Callback<List<DailyTotalDTO>>() {
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
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (DailyTotalDTO total : dailyTotals) {
            String dateStr = total.getDate().format(formatter);
            BigDecimal income = total.getIncome() != null ? total.getIncome() : BigDecimal.ZERO;
            BigDecimal expense = total.getExpense() != null ? total.getExpense() : BigDecimal.ZERO;
            incomeSeries.getData().add(new XYChart.Data<>(dateStr, income));
            expenseSeries.getData().add(new XYChart.Data<>(dateStr, expense));
            totalIncome = totalIncome.add(income);
            totalExpense = totalExpense.add(expense);
        }

        trendChart.getData().addAll(incomeSeries, expenseSeries);
        analyticsIncomeLabel.setText(formatCurrency(totalIncome));
        analyticsExpenseLabel.setText(formatCurrency(totalExpense));
        BigDecimal net = totalIncome.subtract(totalExpense);
        analyticsNetLabel.setText(formatCurrency(net));
        applyAmountClass(analyticsNetLabel, net, null);
    }

    private void loadCategoryPieChart() {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now();

        financeApi.getCategoryReport(startOfMonth.toString(), endOfMonth.toString())
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
        if (breakdown == null) {
            showExpenseCategoryState("Bu ay gider kaydı yok", BigDecimal.ZERO,
                    "Kategori dağılımı kayıt oluştukça burada görünür.");
            analyticsTopCategoryLabel.setText("—");
            return;
        }
        List<Map.Entry<String, BigDecimal>> visibleEntries = breakdown.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();

        if (visibleEntries.isEmpty()) {
            showExpenseCategoryState("Bu ay gider kaydı yok", BigDecimal.ZERO,
                    "Kategori dağılımı kayıt oluştukça burada görünür.");
            analyticsTopCategoryLabel.setText("—");
            return;
        }

        Map.Entry<String, BigDecimal> top = visibleEntries.get(0);
        analyticsTopCategoryLabel.setText(localizeCategory(top.getKey()) + " · " + formatCurrency(top.getValue()));

        if (visibleEntries.size() == 1) {
            showExpenseCategoryState(localizeCategory(top.getKey()), top.getValue(),
                    "Bu ayki giderlerin tamamı bu kategoride.");
            return;
        }

        expenseCategoryStateBox.setVisible(false);
        expenseCategoryStateBox.setManaged(false);
        expensePieChart.setVisible(true);
        expensePieChart.setManaged(true);
        for (Map.Entry<String, BigDecimal> entry : visibleEntries) {
            String categoryKey = "category." + entry.getKey();
            String categoryName = bundle.containsKey(categoryKey) ? bundle.getString(categoryKey) : entry.getKey();

            PieChart.Data slice = new PieChart.Data(
                    categoryName + " (" + formatCurrency(entry.getValue()) + ")",
                    entry.getValue().doubleValue());
            expensePieChart.getData().add(slice);
        }
    }

    private String localizeCategory(String category) {
        String key = "category." + category;
        return bundle.containsKey(key) ? bundle.getString(key) : category;
    }

    private void showExpenseCategoryState(String title, BigDecimal amount, String caption) {
        expensePieChart.setVisible(false);
        expensePieChart.setManaged(false);
        expenseCategoryStateTitle.setText(title);
        expenseCategoryStateAmount.setText(formatCurrency(amount));
        expenseCategoryStateCaption.setText(caption);
        expenseCategoryStateBox.setVisible(true);
        expenseCategoryStateBox.setManaged(true);
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
            stage.setScene(com.pusula.desktop.util.ThemeHelper.createDialogScene(root));
            com.pusula.desktop.util.ThemeHelper.configureDialogStage(stage,
                    todayExpensesTable.getScene().getWindow(),
                    com.pusula.desktop.util.ThemeHelper.DialogProfile.WORKFLOW);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Gider formu açılamadı: " + e.getMessage());
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
            stage.setScene(com.pusula.desktop.util.ThemeHelper.createDialogScene(root));
            com.pusula.desktop.util.ThemeHelper.configureDialogStage(stage,
                    todayExpensesTable.getScene().getWindow(),
                    com.pusula.desktop.util.ThemeHelper.DialogProfile.WORKFLOW);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Gider formu açılamadı: " + e.getMessage());
        }
    }

    private void handleDeleteExpense(DailySummaryDTO.ExpenseItemDTO expenseItem) {
        if (AlertHelper.showConfirmation(todayExpensesTable.getScene().getWindow(), "Gideri Sil",
                expenseItem.getDescription() + " · " + formatCurrency(expenseItem.getAmount()))) {
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
    }

    @FXML
    private void handleCloseDay() {
        if (isDayClosed) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null,
                    bundle.getString("finance.warning"),
                    bundle.getString("dialog.close_day.already_closed"));
            return;
        }

        if (AlertHelper.showConfirmation(todayExpensesTable.getScene().getWindow(),
                "Günlük Mutabakat", String.format(
                        "Bugünün finansal özetini kapatmak istiyor musunuz?\n\nGelir: %s\nGider: %s\nNet: %s\n\nYeni bir kayıt eklenirse özet otomatik olarak yeniden hesaplanır.",
                        todayIncomeLabel.getText(), todayExpenseLabel.getText(), netCashLabel.getText()))) {
            performDayClosing();
        }
    }

    private void performDayClosing() {
        CloseDayRequest request = CloseDayRequest.builder()
                .companyId(com.pusula.desktop.util.SessionManager.getCompanyId())
                .date(currentDate)
                .userId(null)
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
                } else {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            todayExpensesTable.getScene().getWindow(), "Gün kapatılamadı",
                            "Günlük mutabakat kaydedilemedi (" + response.code()
                                    + "). Gün başka bir oturumda kapatılmışsa ekranı yenileyin."));
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
            return "0,00 ₺";
        return String.format(Locale.forLanguageTag("tr-TR"), "%,.2f ₺", amount);
    }

    private void setupReportsTable() {
        colReportPeriod.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDisplayPeriod()));

        // CarryOver column with color coding
        colReportCarryOver.setCellValueFactory(cellData -> {
            java.math.BigDecimal carryOver = cellData.getValue().getCarryOver();
            String text = carryOver != null ? formatCurrency(carryOver) : "0,00 ₺";
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        colReportCarryOver.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    clearAmountClasses(this);
                } else {
                    setText(item);
                    MonthlySummaryDTO dto = getTableRow().getItem();
                    applyAmountClass(this, dto != null ? dto.getCarryOver() : null, null);
                }
            }
        });

        setupColoredCurrencyColumn(colReportIncome,
                MonthlySummaryDTO::getTotalIncome, "amount-positive");

        setupColoredCurrencyColumn(colReportCurrentAccount,
                MonthlySummaryDTO::getCurrentAccountTransferred, "amount-warning");

        setupColoredCurrencyColumn(colReportExpense,
                MonthlySummaryDTO::getTotalProfitExpenses, "amount-negative");

        setupSignedCurrencyColumn(colReportProfit, MonthlySummaryDTO::getNetProfit);
        setupSignedCurrencyColumn(colReportClosingProfit, MonthlySummaryDTO::getClosingCumulativeProfit);

        reportsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> renderReportDetails(selected));

        // Actions column with PDF download button
        colReportActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnPDF = new Button("PDF");
            {
                btnPDF.getStyleClass().addAll("button-sm", "btn-export");
                btnPDF.setAccessibleText("Aylık raporu PDF olarak aç");
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

    private void setupSignedCurrencyColumn(TableColumn<MonthlySummaryDTO, String> column,
            java.util.function.Function<MonthlySummaryDTO, BigDecimal> valueExtractor) {
        column.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(valueExtractor.apply(cellData.getValue()))));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    clearAmountClasses(this);
                    return;
                }
                setText(item);
                MonthlySummaryDTO row = getTableRow().getItem();
                BigDecimal value = row != null ? valueExtractor.apply(row) : null;
                applyAmountClass(this, value, null);
            }
        });
    }

    private void renderReportDetails(MonthlySummaryDTO summary) {
        reportDetailContainer.getChildren().clear();
        if (summary == null) {
            Label empty = new Label("Aylık özeti görmek için arşivden bir dönem seçin.");
            empty.getStyleClass().add("empty-state");
            reportDetailContainer.getChildren().add(empty);
            return;
        }

        Label title = new Label(summary.getDisplayPeriod() + " Özeti");
        title.getStyleClass().add("report-detail-title");
        reportDetailContainer.getChildren().add(title);

        FlowPane metrics = new FlowPane(12, 12);
        metrics.getStyleClass().add("report-metric-grid");
        metrics.getChildren().addAll(
                reportMetricCard("Satış / Ciro", summary.getTotalIncome(), "amount-positive", "Bu ay oluşan toplam satış"),
                reportMetricCard("Cariye Aktarılan", summary.getCurrentAccountTransferred(), "amount-warning", "Cironun henüz tahsil edilmemiş kısmı"),
                reportMetricCard("Toplam Gider", summary.getTotalProfitExpenses(), "amount-negative", "Servis maliyeti ve diğer giderler"),
                reportMetricCard("Aylık Kâr / Zarar", summary.getNetProfit(), signedColor(summary.getNetProfit()),
                        "Satış eksi toplam kârlılık gideri"));
        reportDetailContainer.getChildren().add(metrics);

        HBox details = new HBox(12,
                reportBreakdownCard("Gider Dağılımı",
                        "Servis doğrudan maliyeti", summary.getServiceDirectCost(),
                        "Diğer faaliyet giderleri", summary.getOtherOperatingExpenses()),
                reportBreakdownCard("Birikimli Sonuç",
                        "Geçmiş dönem", summary.getCarryOver(),
                        "Dönem sonu", summary.getClosingCumulativeProfit()));
        for (var child : details.getChildren()) HBox.setHgrow(child, Priority.ALWAYS);
        details.getStyleClass().add("report-breakdown-row");
        reportDetailContainer.getChildren().add(details);
    }

    private VBox reportMetricCard(String labelText, BigDecimal amount, String color, String captionText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("finance-summary-label");
        Label value = new Label(formatCurrency(amount));
        value.getStyleClass().addAll("report-metric-value", color == null ? "amount-neutral" : color);
        Label caption = new Label(captionText);
        caption.setWrapText(true);
        caption.getStyleClass().add("finance-summary-caption");
        VBox card = new VBox(5, label, value, caption);
        card.setPrefWidth(245);
        card.getStyleClass().add("report-metric-card");
        return card;
    }

    private VBox reportBreakdownCard(String titleText,
            String firstLabel, BigDecimal firstAmount, String secondLabel, BigDecimal secondAmount) {
        Label title = new Label(titleText);
        title.getStyleClass().add("finance-ledger-title");
        VBox card = new VBox(7, title);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("report-breakdown-card");
        addCompactReportRow(card, firstLabel, firstAmount);
        addCompactReportRow(card, secondLabel, secondAmount);
        return card;
    }

    private void addCompactReportRow(VBox card, String labelText, BigDecimal amount) {
        Label label = new Label(labelText);
        label.getStyleClass().add("section-caption");
        Label value = new Label(formatCurrency(amount));
        value.getStyleClass().addAll("report-detail-value", signedColor(amount) == null ? "amount-neutral" : signedColor(amount));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        card.getChildren().add(new HBox(8, label, spacer, value));
    }

    private void addReportSection(String title) {
        Label label = new Label(title);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("report-section-title");
        reportDetailContainer.getChildren().add(label);
    }

    private void addReportDetailRow(String labelText, BigDecimal amount, String color) {
        HBox row = new HBox(10);
        row.getStyleClass().add("report-detail-row");
        Label label = new Label(labelText);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label value = new Label(formatCurrency(amount));
        value.getStyleClass().add("report-detail-value");
        value.getStyleClass().add(color != null ? color : "amount-neutral");
        row.getChildren().addAll(label, spacer, value);
        reportDetailContainer.getChildren().add(row);
    }

    private String signedColor(BigDecimal value) {
        if (value == null || value.signum() == 0) return null;
        return value.signum() > 0 ? "amount-positive" : "amount-negative";
    }

    private void applyAmountClass(javafx.scene.Node node, BigDecimal value, String positiveOverride) {
        clearAmountClasses(node);
        if (value == null || value.signum() == 0) {
            node.getStyleClass().add("amount-neutral");
        } else if (value.signum() > 0) {
            node.getStyleClass().add(positiveOverride != null ? positiveOverride : "amount-positive");
        } else {
            node.getStyleClass().add("amount-negative");
        }
    }

    private void clearAmountClasses(javafx.scene.Node node) {
        node.getStyleClass().removeAll(
                "amount-positive", "amount-negative", "amount-warning", "amount-neutral");
    }

    private void setupColoredCurrencyColumn(TableColumn<MonthlySummaryDTO, String> column,
            java.util.function.Function<MonthlySummaryDTO, BigDecimal> valueExtractor,
            String amountStyleClass) {
        column.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(valueExtractor.apply(cellData.getValue()))));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    clearAmountClasses(this);
                    return;
                }
                setText(item);
                MonthlySummaryDTO row = getTableRow().getItem();
                BigDecimal value = row != null ? valueExtractor.apply(row) : null;
                clearAmountClasses(this);
                if (value != null && value.signum() > 0) getStyleClass().add(amountStyleClass);
            }
        });
    }

    private void loadMonthlyReports() {
        financeApi.getMonthlyArchives().enqueue(new Callback<List<MonthlySummaryDTO>>() {
            @Override
            public void onResponse(Call<List<MonthlySummaryDTO>> call, Response<List<MonthlySummaryDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        reportsTable.setItems(FXCollections.observableArrayList(response.body()));
                        if (!reportsTable.getItems().isEmpty()) {
                            reportsTable.getSelectionModel().selectFirst();
                        } else {
                            renderReportDetails(null);
                        }
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
        financeApi.downloadMonthlyPDF(period).enqueue(new Callback<okhttp3.ResponseBody>() {
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
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getAccountName()));

        colAccountBalance.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(cellData.getValue().getBalance())));

        colAccountLastUpdated.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getLastUpdated() != null
                        ? cellData.getValue().getLastUpdated().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                        : "-"));

        currentAccountsTable.setRowFactory(table -> {
            TableRow<CurrentAccountDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showCurrentAccountHistory(row.getItem());
                }
            });
            return row;
        });

        // Actions column with history and collection buttons
        colAccountActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnHistory = new Button(bundle.getString("current_account.history"));
            private final Button btnEdit = new Button(bundle.getString("current_account.collection"));
            private final HBox actions = new HBox(8, btnHistory, btnEdit);

            {
                btnHistory.getStyleClass().addAll("button-sm", "button-secondary");
                btnEdit.getStyleClass().addAll("button-sm", "btn-primary");
                btnHistory.setOnAction(event -> {
                    CurrentAccountDTO account = getTableView().getItems().get(getIndex());
                    showCurrentAccountHistory(account);
                });
                btnEdit.setOnAction(event -> {
                    CurrentAccountDTO account = getTableView().getItems().get(getIndex());
                    handleEditBalance(account);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actions);
            }
        });
    }

    private void setupCurrentAccountHistoryTable() {
        colCurrentAccountHistoryDate.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
                value.getValue().getEffectiveDate() != null
                        ? value.getValue().getEffectiveDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "-"));
        colCurrentAccountHistoryType.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
                currentAccountTransactionLabel(value.getValue().getType())));
        colCurrentAccountHistoryDescription.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
                value.getValue().getDescription() != null ? value.getValue().getDescription() : "-"));
        colCurrentAccountHistoryAmount.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
                signedCurrency(value.getValue().getAmount())));
        colCurrentAccountHistoryBalance.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
                formatCurrency(value.getValue().getBalanceAfter())));

        colCurrentAccountHistoryDescription.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTooltip(empty || value == null || value.isBlank() ? null : new Tooltip(value));
            }
        });
        colCurrentAccountHistoryAmount.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("amount-positive", "amount-negative", "amount-warning");
                if (empty) {
                    setText(null);
                    return;
                }
                setText(value);
                CurrentAccountHistoryDTO.Transaction transaction = getTableRow() != null
                        ? getTableRow().getItem() : null;
                if (transaction != null && transaction.getAmount() != null) {
                    getStyleClass().add(transaction.getAmount().signum() >= 0
                            ? "amount-warning" : "amount-positive");
                }
            }
        });
        currentAccountHistoryTable.setItems(FXCollections.observableArrayList());
        currentAccountHistorySearchField.textProperty().addListener((observable, oldValue, newValue) ->
                applyCurrentAccountHistoryFilters());
    }

    @FXML
    private void handleManageAccountParties() {
        Dialog<Void> dialog = new Dialog<>();
        com.pusula.desktop.util.ThemeHelper.applyToDialog(dialog, currentAccountsTable.getScene().getWindow(),
                com.pusula.desktop.util.ThemeHelper.DialogProfile.DETAIL);
        dialog.setTitle(bundle.getString("account_party.list.title"));
        dialog.setHeaderText(bundle.getString("account_party.list.subtitle"));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType(
                bundle.getString("common.close"), ButtonBar.ButtonData.CANCEL_CLOSE));

        TableView<AccountPartyDTO> table = new TableView<>();
        TableColumn<AccountPartyDTO, String> name = new TableColumn<>("Kurum/Firma");
        name.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getDisplayName()));
        TableColumn<AccountPartyDTO, String> contact = new TableColumn<>("Yetkili / Telefon");
        contact.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
                java.util.stream.Stream.of(row.getValue().getContactPerson(), row.getValue().getPhone())
                        .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" · "))));
        TableColumn<AccountPartyDTO, String> term = new TableColumn<>("Vade");
        term.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
                (row.getValue().getPaymentTermDays() != null ? row.getValue().getPaymentTermDays() : 0) + " gün"));
        table.getColumns().addAll(name, contact, term);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Henüz kurum/firma cari kartı yok."));
        Button add = new Button("+ Yeni Kurum/Firma");
        add.getStyleClass().add("btn-primary");
        VBox content = new VBox(10, add, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);

        AccountPartyApi api = RetrofitClient.getClient().create(AccountPartyApi.class);
        Runnable reload = () -> api.getParties("ORGANIZATION").enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<AccountPartyDTO>> call, Response<List<AccountPartyDTO>> response) {
                Platform.runLater(() -> { if (response.isSuccessful() && response.body() != null)
                    table.setItems(FXCollections.observableArrayList(response.body())); });
            }
            @Override public void onFailure(Call<List<AccountPartyDTO>> call, Throwable throwable) {
                Platform.runLater(() -> table.setPlaceholder(new Label("Kurumlar yüklenemedi: " + throwable.getMessage())));
            }
        });
        add.setOnAction(event -> {
            showAccountPartyCreateDialog(dialog.getDialogPane().getScene().getWindow()).ifPresent(request ->
                    api.create(request).enqueue(new Callback<>() {
                        @Override public void onResponse(Call<AccountPartyDTO> call, Response<AccountPartyDTO> response) {
                            Platform.runLater(() -> { if (response.isSuccessful()) reload.run();
                                else AlertHelper.showAlert(Alert.AlertType.ERROR, dialog.getDialogPane().getScene().getWindow(),
                                        "Kurum oluşturulamadı", "Sunucu yanıtı: " + response.code()); });
                        }
                        @Override public void onFailure(Call<AccountPartyDTO> call, Throwable throwable) {
                            Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                    dialog.getDialogPane().getScene().getWindow(), "Kurum oluşturulamadı", throwable.getMessage()));
                        }
                    }));
        });
        reload.run();
        dialog.showAndWait();
    }

    private java.util.Optional<AccountPartyDTO> showAccountPartyCreateDialog(javafx.stage.Window owner) {
        Dialog<AccountPartyDTO> dialog = new Dialog<>();
        com.pusula.desktop.util.ThemeHelper.applyToDialog(dialog, owner,
                com.pusula.desktop.util.ThemeHelper.DialogProfile.FORM);
        dialog.setTitle(bundle.getString("account_party.create.title"));
        dialog.setHeaderText(bundle.getString("account_party.create.subtitle"));
        ButtonType cancelButton = new ButtonType(bundle.getString("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType saveButtonType = new ButtonType(bundle.getString("btn.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelButton, saveButtonType);

        TextField displayName = new TextField();
        displayName.setPromptText("Örn. Termodinamik Isıtma Sistemleri");
        TextField legalName = new TextField();
        TextField contactPerson = new TextField();
        TextField phone = new TextField();
        TextField taxNumber = new TextField();
        TextField paymentTermDays = new TextField("0");
        paymentTermDays.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,4}") ? change : null));

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10);
        form.addRow(0, new Label("Kurum/firma adı:*"), displayName);
        form.addRow(1, new Label("Resmî unvan:"), legalName);
        form.addRow(2, new Label("Yetkili kişi:"), contactPerson);
        form.addRow(3, new Label("Telefon:"), phone);
        form.addRow(4, new Label("Vergi numarası:"), taxNumber);
        form.addRow(5, new Label("Ödeme vadesi (gün):"), paymentTermDays);
        form.getColumnConstraints().addAll(new javafx.scene.layout.ColumnConstraints(),
                new javafx.scene.layout.ColumnConstraints(320));
        dialog.getDialogPane().setContent(form);

        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(displayName.textProperty().isEmpty());
        dialog.setResultConverter(button -> {
            if (button != saveButtonType) return null;
            return AccountPartyDTO.builder()
                    .partyType("ORGANIZATION")
                    .displayName(displayName.getText().trim())
                    .legalName(blankToNull(legalName.getText()))
                    .contactPerson(blankToNull(contactPerson.getText()))
                    .phone(blankToNull(phone.getText()))
                    .taxNumber(blankToNull(taxNumber.getText()))
                    .paymentTermDays(paymentTermDays.getText().isBlank() ? 0 : Integer.parseInt(paymentTermDays.getText()))
                    .active(true)
                    .build();
        });
        Platform.runLater(displayName::requestFocus);
        return dialog.showAndWait();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void showCurrentAccountHistory(CurrentAccountDTO account) {
        selectedCurrentAccount = account;
        currentAccountDetailNameLabel.setText(account.getAccountName());
        currentAccountDetailBalanceLabel.setText(formatCurrency(account.getBalance()));
        currentAccountTotalChargesLabel.setText("-");
        currentAccountTotalPaymentsLabel.setText("-");
        currentAccountTransactionCountLabel.setText("-");
        currentAccountHistory.clear();
        currentAccountHistoryTable.getItems().clear();
        currentAccountHistorySearchField.clear();
        currentAccountHistoryStartDate.setValue(null);
        currentAccountHistoryEndDate.setValue(null);
        setCurrentAccountHistoryLoading(true);
        showCurrentAccountDetail(true);

        CurrentAccountApi api = RetrofitClient.getClient().create(CurrentAccountApi.class);
        api.getHistory(account.getId()).enqueue(new Callback<CurrentAccountHistoryDTO>() {
            @Override
            public void onResponse(Call<CurrentAccountHistoryDTO> call, Response<CurrentAccountHistoryDTO> response) {
                Platform.runLater(() -> {
                    if (selectedCurrentAccount == null
                            || !java.util.Objects.equals(selectedCurrentAccount.getId(), account.getId())) return;
                    setCurrentAccountHistoryLoading(false);
                    if (!response.isSuccessful() || response.body() == null) {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, currentAccountsTable.getScene().getWindow(),
                                bundle.getString("error.title"),
                                bundle.getString("current_account.history.load_failed") + ": " + response.code());
                        return;
                    }
                    CurrentAccountHistoryDTO history = response.body();
                    currentAccountDetailNameLabel.setText(history.getAccountName());
                    currentAccountDetailBalanceLabel.setText(formatCurrency(history.getCurrentBalance()));
                    currentAccountHistory.setAll(history.getTransactions() != null
                            ? history.getTransactions() : List.of());
                    updateCurrentAccountSummary(history);
                    applyCurrentAccountHistoryFilters();
                });
            }

            @Override
            public void onFailure(Call<CurrentAccountHistoryDTO> call, Throwable throwable) {
                Platform.runLater(() -> {
                    if (selectedCurrentAccount == null
                            || !java.util.Objects.equals(selectedCurrentAccount.getId(), account.getId())) return;
                    setCurrentAccountHistoryLoading(false);
                    AlertHelper.showAlert(Alert.AlertType.ERROR, currentAccountsTable.getScene().getWindow(),
                            bundle.getString("error.title"),
                            bundle.getString("current_account.history.load_failed") + ": " + throwable.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleBackToCurrentAccounts() {
        selectedCurrentAccount = null;
        showCurrentAccountDetail(false);
    }

    @FXML
    private void handleExportCurrentAccountStatement() {
        if (selectedCurrentAccount != null) downloadCurrentAccountStatement(selectedCurrentAccount);
    }

    @FXML
    private void handleApplyCurrentAccountHistoryFilters() {
        applyCurrentAccountHistoryFilters();
    }

    @FXML
    private void handleClearCurrentAccountHistoryFilters() {
        currentAccountHistorySearchField.clear();
        currentAccountHistoryStartDate.setValue(null);
        currentAccountHistoryEndDate.setValue(null);
        applyCurrentAccountHistoryFilters();
    }

    private void showCurrentAccountDetail(boolean showDetail) {
        currentAccountListPane.setVisible(!showDetail);
        currentAccountListPane.setManaged(!showDetail);
        currentAccountDetailPane.setVisible(showDetail);
        currentAccountDetailPane.setManaged(showDetail);
    }

    private void setCurrentAccountHistoryLoading(boolean loading) {
        currentAccountHistoryProgress.setVisible(loading);
        currentAccountHistoryProgress.setManaged(loading);
        currentAccountHistoryTable.setDisable(loading);
    }

    private void updateCurrentAccountSummary(CurrentAccountHistoryDTO history) {
        BigDecimal charges = currentAccountHistory.stream()
                .filter(transaction -> "CHARGE".equals(transaction.getType()))
                .map(CurrentAccountHistoryDTO.Transaction::getAmount)
                .filter(java.util.Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payments = currentAccountHistory.stream()
                .filter(transaction -> "PAYMENT".equals(transaction.getType()))
                .map(CurrentAccountHistoryDTO.Transaction::getAmount)
                .filter(java.util.Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        currentAccountDetailBalanceLabel.setText(formatCurrency(history.getCurrentBalance()));
        currentAccountTotalChargesLabel.setText(formatCurrency(charges));
        currentAccountTotalPaymentsLabel.setText(formatCurrency(payments));
        currentAccountTransactionCountLabel.setText(String.valueOf(currentAccountHistory.size()));
    }

    private void applyCurrentAccountHistoryFilters() {
        String query = currentAccountHistorySearchField.getText() == null ? ""
                : currentAccountHistorySearchField.getText().trim().toLowerCase(Locale.forLanguageTag("tr-TR"));
        LocalDate start = currentAccountHistoryStartDate.getValue();
        LocalDate end = currentAccountHistoryEndDate.getValue();
        List<CurrentAccountHistoryDTO.Transaction> filtered = currentAccountHistory.stream()
                .filter(transaction -> start == null || (transaction.getEffectiveDate() != null
                        && !transaction.getEffectiveDate().isBefore(start)))
                .filter(transaction -> end == null || (transaction.getEffectiveDate() != null
                        && !transaction.getEffectiveDate().isAfter(end)))
                .filter(transaction -> query.isBlank() || currentAccountTransactionSearchText(transaction).contains(query))
                .toList();
        currentAccountHistoryTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private String currentAccountTransactionSearchText(CurrentAccountHistoryDTO.Transaction transaction) {
        return java.util.stream.Stream.of(
                        currentAccountTransactionLabel(transaction.getType()),
                        transaction.getDescription(),
                        transaction.getPaymentMethod(),
                        transaction.getSourceType())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.forLanguageTag("tr-TR"));
    }

    private String signedCurrency(BigDecimal amount) {
        if (amount == null) return formatCurrency(BigDecimal.ZERO);
        return (amount.signum() > 0 ? "+" : "") + formatCurrency(amount);
    }

    private void downloadCurrentAccountStatement(CurrentAccountDTO account) {
        financeApi.downloadCurrentAccountStatementPdf(account.getId()).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            currentAccountsTable.getScene().getWindow(), "Ekstre oluşturulamadı",
                            "Sunucu yanıtı: " + response.code()));
                    return;
                }
                try {
                    byte[] pdf = response.body().bytes();
                    Platform.runLater(() -> saveCurrentAccountStatementPdf(account, pdf));
                } catch (Exception exception) {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            currentAccountsTable.getScene().getWindow(), "Ekstre okunamadı", exception.getMessage()));
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable throwable) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                        currentAccountsTable.getScene().getWindow(), "Ekstre indirilemedi", throwable.getMessage()));
            }
        });
    }

    private void saveCurrentAccountStatementPdf(CurrentAccountDTO account, byte[] pdf) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Cari Hesap Ekstresini Kaydet");
            String safeName = account.getAccountName() == null ? "Cari_Hesap"
                    : account.getAccountName().replaceAll("[^\\p{L}\\p{N}._-]+", "_");
            chooser.setInitialFileName("Cari_Ekstre_" + safeName + ".pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Dosyaları", "*.pdf"));
            File file = chooser.showSaveDialog((Stage) currentAccountsTable.getScene().getWindow());
            if (file != null) {
                Files.write(file.toPath(), pdf);
                AlertHelper.showSuccess(currentAccountsTable.getScene().getWindow(),
                        "Ekstre kaydedildi", "Seçilen cari hesabın tüm hareketleri PDF olarak kaydedildi.");
            }
        } catch (Exception exception) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, currentAccountsTable.getScene().getWindow(),
                    "Ekstre kaydedilemedi", exception.getMessage());
        }
    }

    private String currentAccountTransactionLabel(String type) {
        if (type == null) return bundle.getString("current_account.transaction.adjustment");
        return switch (type) {
            case "CHARGE" -> bundle.getString("current_account.transaction.charge");
            case "PAYMENT" -> bundle.getString("current_account.transaction.payment");
            case "DISCOUNT" -> bundle.getString("current_account.transaction.discount");
            case "REVERSAL" -> bundle.getString("current_account.transaction.reversal");
            default -> bundle.getString("current_account.transaction.adjustment");
        };
    }

    private void loadCurrentAccounts() {
        CurrentAccountApi api = RetrofitClient.getClient().create(CurrentAccountApi.class);
        api.getAll().enqueue(new Callback<List<CurrentAccountDTO>>() {
            @Override
            public void onResponse(Call<List<CurrentAccountDTO>> call, Response<List<CurrentAccountDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
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

    @FXML
    public void handleExportCurrentAccountsPdf() {
        financeApi.downloadOpenCurrentAccountsPdf().enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                            "Hata", "Cari PDF oluşturulamadı: " + response.code()));
                    return;
                }
                try {
                    byte[] pdf = response.body().bytes();
                    Platform.runLater(() -> saveCurrentAccountsPdf(pdf));
                } catch (Exception exception) {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                            "Hata", "Cari PDF okunamadı: " + exception.getMessage()));
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable throwable) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                        "Hata", "Cari PDF indirilemedi: " + throwable.getMessage()));
            }
        });
    }

    private void saveCurrentAccountsPdf(byte[] pdf) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Açık Cari Hesaplar PDF Raporunu Kaydet");
            chooser.setInitialFileName("Acik_Cari_Hesaplar.pdf");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Dosyaları", "*.pdf"));
            File file = chooser.showSaveDialog((Stage) currentAccountsTable.getScene().getWindow());
            if (file != null) {
                Files.write(file.toPath(), pdf);
                AlertHelper.showAlert(Alert.AlertType.INFORMATION, null,
                        "Başarılı", "Cari PDF başarıyla kaydedildi.");
            }
        } catch (Exception exception) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, null,
                    "Hata", "Cari PDF kaydedilemedi: " + exception.getMessage());
        }
    }

    private void handleEditBalance(CurrentAccountDTO account) {
        // Create payment dialog with amount and discount fields
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        com.pusula.desktop.util.ThemeHelper.applyToDialog(dialog, todayExpensesTable.getScene().getWindow(),
                com.pusula.desktop.util.ThemeHelper.DialogProfile.FORM);
        dialog.setTitle(bundle.getString("current_account.payment.title"));
        dialog.setHeaderText(account.getCustomerName() + " - " + bundle.getString("current_account.debt") + ": " +
                String.format("%.2f TL", account.getBalance()));

        ButtonType saveButtonType = new ButtonType(bundle.getString("current_account.payment.save"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType(bundle.getString("btn.cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, cancelButtonType);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));

        CurrencyTextField paymentField = new CurrencyTextField();
        paymentField.setPromptText("0,00");

        CurrencyTextField discountField = new CurrencyTextField();
        discountField.setPromptText("0,00");
        discountField.setText("0");

        grid.add(new Label(bundle.getString("current_account.payment.amount") + ":"), 0, 0);
        grid.add(paymentField, 1, 0);
        grid.add(new Label(bundle.getString("current_account.payment.discount") + ":"), 0, 1);
        grid.add(discountField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
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
        financeApi.getInventoryValue().enqueue(new Callback<Map<String, BigDecimal>>() {
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
        financeApi.getInventoryValue().enqueue(new Callback<Map<String, BigDecimal>>() {
            @Override
            public void onResponse(Call<Map<String, BigDecimal>> call, Response<Map<String, BigDecimal>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    assetInventoryValue = response.body().getOrDefault("totalValue", BigDecimal.ZERO);
                } else {
                    assetInventoryValue = BigDecimal.ZERO;
                }
                Platform.runLater(() -> refreshBusinessAssetsUi());
            }

            @Override
            public void onFailure(Call<Map<String, BigDecimal>> call, Throwable t) {
                System.err.println("Failed to load asset inventory value: " + t.getMessage());
                assetInventoryValue = BigDecimal.ZERO;
                Platform.runLater(() -> refreshBusinessAssetsUi());
            }
        });

        financeApi.getCumulativeSummary().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    assetNetCashValue = toBigDecimal(body.get("totalCash"));
                    assetTotalIncome = toBigDecimal(body.get("totalIncome"));
                    assetTotalExpenses = toBigDecimal(body.get("totalExpenses"));
                } else {
                    assetNetCashValue = BigDecimal.ZERO;
                    assetTotalIncome = BigDecimal.ZERO;
                    assetTotalExpenses = BigDecimal.ZERO;
                }
                Platform.runLater(() -> refreshBusinessAssetsUi());
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                System.err.println("Failed to load cumulative net cash: " + t.getMessage());
                assetNetCashValue = BigDecimal.ZERO;
                assetTotalIncome = BigDecimal.ZERO;
                assetTotalExpenses = BigDecimal.ZERO;
                Platform.runLater(() -> refreshBusinessAssetsUi());
            }
        });
    }

    private void refreshBusinessAssetsUi() {
        BigDecimal inventory = assetInventoryValue != null ? assetInventoryValue : BigDecimal.ZERO;
        BigDecimal netCash = assetNetCashValue != null ? assetNetCashValue : BigDecimal.ZERO;
        BigDecimal income = assetTotalIncome != null ? assetTotalIncome : BigDecimal.ZERO;
        BigDecimal expenses = assetTotalExpenses != null ? assetTotalExpenses : BigDecimal.ZERO;
        BigDecimal totalEquity = inventory.add(netCash);

        if (assetTotalEquityLabel != null) {
            applySignedCurrencyLabel(assetTotalEquityLabel, totalEquity, "finance-ownership-hero-value");
        }
        if (assetInventoryValueLabel != null) {
            assetInventoryValueLabel.setText(formatCurrency(inventory));
        }
        if (assetNetCashLabel != null) {
            applySignedCurrencyLabel(assetNetCashLabel, netCash, "finance-ownership-metric-value");
        }
        if (assetBreakdownInventoryLabel != null) {
            assetBreakdownInventoryLabel.setText(formatCurrency(inventory));
        }
        if (assetBreakdownIncomeLabel != null) {
            assetBreakdownIncomeLabel.setText(formatCurrency(income));
        }
        if (assetBreakdownExpensesLabel != null) {
            assetBreakdownExpensesLabel.setText(formatCurrency(expenses));
        }
        if (assetBreakdownNetCashLabel != null) {
            applySignedCurrencyLabel(assetBreakdownNetCashLabel, netCash, "finance-ownership-row-value");
        }
        if (assetBreakdownTotalLabel != null) {
            applySignedCurrencyLabel(assetBreakdownTotalLabel, totalEquity, "finance-ownership-row-value-strong");
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    private void applySignedCurrencyLabel(Label label, BigDecimal amount, String baseStyleClass) {
        label.setText(formatCurrency(amount));
        label.getStyleClass().removeAll("finance-value-positive", "finance-value-negative");
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            label.getStyleClass().add("finance-value-positive");
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            label.getStyleClass().add("finance-value-negative");
        }
        if (!label.getStyleClass().contains(baseStyleClass)) {
            label.getStyleClass().add(baseStyleClass);
        }
    }
}
