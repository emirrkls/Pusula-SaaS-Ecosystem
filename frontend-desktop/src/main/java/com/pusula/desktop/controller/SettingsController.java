package com.pusula.desktop.controller;

import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
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

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class SettingsController {

    @FXML
    private TableView<FixedExpenseDefinitionDTO> fixedExpensesTable;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colName;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colAmount;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colCategory;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colDayOfMonth;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colDescription;

    private FinanceApi financeApi;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
        financeApi = RetrofitClient.getClient().create(FinanceApi.class);

        setupTable();
        loadFixedExpenses();
    }

    private void setupTable() {
        colName.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));

        colAmount.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f ₺", cellData.getValue().getDefaultAmount())));

        colCategory.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getCategory();
            String key = "category." + category;
            String localized = bundle.containsKey(key) ? bundle.getString(key) : category;
            return new javafx.beans.property.SimpleStringProperty(localized);
        });

        colDayOfMonth.setCellValueFactory(cellData -> {
            Integer day = cellData.getValue().getDayOfMonth();
            return new javafx.beans.property.SimpleStringProperty(day != null ? day.toString() : "-");
        });

        colDescription.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getDescription() != null ? cellData.getValue().getDescription() : ""));
    }

    private void loadFixedExpenses() {
        financeApi.getFixedExpenses(1L).enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
            @Override
            public void onResponse(Call<List<FixedExpenseDefinitionDTO>> call,
                    Response<List<FixedExpenseDefinitionDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        fixedExpensesTable.setItems(FXCollections.observableArrayList(response.body()));
                    });
                }
            }

            @Override
            public void onFailure(Call<List<FixedExpenseDefinitionDTO>> call, Throwable t) {
                System.err.println("Failed to load fixed expenses: " + t.getMessage());
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Sabit giderler yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleAddFixedExpense() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/fixed_expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            FixedExpenseDialogController dialogController = loader.getController();
            dialogController.setOnSave(() -> {
                FixedExpenseDefinitionDTO result = dialogController.getResult();
                if (result != null) {
                    createFixedExpense(result);
                }
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("settings.add_fixed_expense"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditFixedExpense() {
        FixedExpenseDefinitionDTO selected = fixedExpensesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen düzenlemek için bir sabit gider seçin.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/fixed_expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            FixedExpenseDialogController dialogController = loader.getController();
            dialogController.setFixedExpense(selected);
            dialogController.setOnSave(() -> {
                FixedExpenseDefinitionDTO result = dialogController.getResult();
                if (result != null) {
                    updateFixedExpense(result);
                }
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("settings.edit_fixed_expense"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteFixedExpense() {
        FixedExpenseDefinitionDTO selected = fixedExpensesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen silmek için bir sabit gider seçin.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Silme Onayı");
        confirmation.setHeaderText(selected.getName() + " sabit giderini silmek istediğinize emin misiniz?");
        confirmation.setContentText("Bu işlem geri alınamaz.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteFixedExpense(selected.getId());
            }
        });
    }

    private void createFixedExpense(FixedExpenseDefinitionDTO expense) {
        financeApi.createFixedExpense(expense).enqueue(new Callback<FixedExpenseDefinitionDTO>() {
            @Override
            public void onResponse(Call<FixedExpenseDefinitionDTO> call, Response<FixedExpenseDefinitionDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Sabit gider başarıyla eklendi!");
                        loadFixedExpenses();
                    });
                }
            }

            @Override
            public void onFailure(Call<FixedExpenseDefinitionDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Ekleme işlemi başarısız: " + t.getMessage());
                });
            }
        });
    }

    private void updateFixedExpense(FixedExpenseDefinitionDTO expense) {
        financeApi.updateFixedExpense(expense.getId(), expense).enqueue(new Callback<FixedExpenseDefinitionDTO>() {
            @Override
            public void onResponse(Call<FixedExpenseDefinitionDTO> call, Response<FixedExpenseDefinitionDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Sabit gider başarıyla güncellendi!");
                        loadFixedExpenses();
                    });
                }
            }

            @Override
            public void onFailure(Call<FixedExpenseDefinitionDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Güncelleme işlemi başarısız: " + t.getMessage());
                });
            }
        });
    }

    private void deleteFixedExpense(Long id) {
        financeApi.deleteFixedExpense(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Sabit gider başarıyla silindi!");
                        loadFixedExpenses();
                    });
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Silme işlemi başarısız: " + t.getMessage());
                });
            }
        });
    }
}
