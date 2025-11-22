package com.pusula.desktop.controller;

import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.PdfReportGenerator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.util.List;

public class InventoryController {

    @FXML
    private TableView<InventoryDTO> inventoryTable;

    @FXML
    private TableColumn<InventoryDTO, String> colPartName;

    @FXML
    private TableColumn<InventoryDTO, Integer> colQuantity;

    @FXML
    private TableColumn<InventoryDTO, BigDecimal> colBuyPrice;

    @FXML
    private TableColumn<InventoryDTO, BigDecimal> colSellPrice;

    @FXML
    private TableColumn<InventoryDTO, Integer> colCriticalLevel;

    private final ObservableList<InventoryDTO> inventoryList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colPartName.setCellValueFactory(new PropertyValueFactory<>("partName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colBuyPrice.setCellValueFactory(new PropertyValueFactory<>("buyPrice"));
        colSellPrice.setCellValueFactory(new PropertyValueFactory<>("sellPrice"));
        colCriticalLevel.setCellValueFactory(new PropertyValueFactory<>("criticalLevel"));

        inventoryTable.setItems(inventoryList);

        loadInventory();
    }

    @FXML
    private void handleRefresh() {
        loadInventory();
    }

    @FXML
    private void handleAddItem() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/inventory_dialog.fxml"));
            javafx.scene.Parent root = loader.load();

            InventoryDialogController controller = loader.getController();
            controller.setOnSaveSuccess(this::loadInventory);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Add Inventory Item");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, inventoryTable.getScene().getWindow(),
                    "Error", "Could not open dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportPdf() {
        if (inventoryList.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, inventoryTable.getScene().getWindow(),
                    "No Data", "There is no inventory to export.");
            return;
        }
        PdfReportGenerator.generateInventoryReport(
                (Stage) inventoryTable.getScene().getWindow(),
                inventoryList);
    }

    private void loadInventory() {
        InventoryApi api = RetrofitClient.getClient().create(InventoryApi.class);
        api.getAllInventory().enqueue(new Callback<List<InventoryDTO>>() {
            @Override
            public void onResponse(Call<List<InventoryDTO>> call, Response<List<InventoryDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        inventoryList.clear();
                        inventoryList.addAll(response.body());
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, inventoryTable.getScene().getWindow(),
                                "Error", "Failed to load inventory: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<InventoryDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, inventoryTable.getScene().getWindow(),
                            "Network Error", "Could not connect to server: " + t.getMessage());
                });
            }
        });
    }
}
