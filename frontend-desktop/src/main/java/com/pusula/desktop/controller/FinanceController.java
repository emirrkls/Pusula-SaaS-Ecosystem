package com.pusula.desktop.controller;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.dto.ExpenseDTO;
import com.pusula.desktop.dto.FinancialSummaryDTO;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.ResourceBundle;

public class FinanceController {

    @FXML
    private Label incomeLabel;
    @FXML
    private Label expenseLabel;
    @FXML
    private Label profitLabel;
    @FXML
    private TableView<ExpenseDTO> expensesTable;
    @FXML
    private TableColumn<ExpenseDTO, String> colDate;
    @FXML
    private TableColumn<ExpenseDTO, String> colCategory;
    @FXML
    private TableColumn<ExpenseDTO, String> colDescription;
    @FXML
    private TableColumn<ExpenseDTO, String> colAmount;

    private FinanceApi financeApi;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", new java.util.Locale("tr", "TR"), new UTF8Control());
        financeApi = RetrofitClient.getClient().create(FinanceApi.class);

        setupTable();
        loadData();
    }

    private void setupTable() {
        colDate.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDate()));

        colCategory.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getCategory();
            String key = "category." + category;
            String localizedCategory = bundle.containsKey(key) ? bundle.getString(key) : category;
            return new javafx.beans.property.SimpleStringProperty(localizedCategory);
        });

        colDescription.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDescription()));

        colAmount.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f ₺", cellData.getValue().getAmount())));
    }

    private void loadData() {
        // Load Financial Summary
        financeApi.getSummary(1L, "MONTHLY").enqueue(new Callback<FinancialSummaryDTO>() {
            @Override
            public void onResponse(Call<FinancialSummaryDTO> call, Response<FinancialSummaryDTO> response) {
                if (response.isSuccessful() && response.body() != null) {
                    FinancialSummaryDTO summary = response.body();
                    Platform.runLater(() -> {
                        incomeLabel.setText(formatCurrency(summary.getTotalIncome()));
                        expenseLabel.setText(formatCurrency(summary.getTotalExpense()));
                        profitLabel.setText(formatCurrency(summary.getNetProfit()));
                    });
                }
            }

            @Override
            public void onFailure(Call<FinancialSummaryDTO> call, Throwable t) {
                System.err.println("Failed to load financial summary: " + t.getMessage());
            }
        });

        // Load Recent Expenses
        financeApi.getExpenses(1L).enqueue(new Callback<List<ExpenseDTO>>() {
            @Override
            public void onResponse(Call<List<ExpenseDTO>> call, Response<List<ExpenseDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        expensesTable.setItems(FXCollections.observableArrayList(response.body()));
                    });
                }
            }

            @Override
            public void onFailure(Call<List<ExpenseDTO>> call, Throwable t) {
                System.err.println("Failed to load expenses: " + t.getMessage());
            }
        });
    }

    @FXML
    private void handleAddExpense() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            ExpenseDialogController controller = loader.getController();
            controller.setOnSaveSuccess(this::loadData);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Gider Ekle");
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", "Gider ekle penceresi açılamadı!");
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "0.00 ₺";
        return String.format("%.2f ₺", amount);
    }
}
