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

    @FXML
    private javafx.scene.control.TextField searchField;

    private final ObservableList<InventoryDTO> inventoryList = FXCollections.observableArrayList();
    private javafx.collections.transformation.FilteredList<InventoryDTO> filteredList;

    @FXML
    public void initialize() {
        colPartName.setCellValueFactory(new PropertyValueFactory<>("partName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colBuyPrice.setCellValueFactory(new PropertyValueFactory<>("buyPrice"));
        colSellPrice.setCellValueFactory(new PropertyValueFactory<>("sellPrice"));
        colCriticalLevel.setCellValueFactory(new PropertyValueFactory<>("criticalLevel"));

        // 1. Search Logic
        filteredList = new javafx.collections.transformation.FilteredList<>(inventoryList, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(item -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                return item.getPartName().toLowerCase().contains(lowerCaseFilter);
            });
        });
        inventoryTable.setItems(filteredList);

        // 2. Visual Alert (Row Factory) & Double Click
        inventoryTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<InventoryDTO> row = new javafx.scene.control.TableRow<>() {
                @Override
                protected void updateItem(InventoryDTO item, boolean empty) {
                    super.updateItem(item, empty);
                    // Remove all custom style classes first
                    getStyleClass().removeAll("table-row-critical");

                    if (item != null && !empty) {
                        // Add critical style class if quantity is at or below critical level
                        if (item.getQuantity() <= item.getCriticalLevel()) {
                            getStyleClass().add("table-row-critical");
                        }
                    }
                }
            };

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    InventoryDTO rowData = row.getItem();
                    handleEditItem(rowData);
                }
            });
            return row;
        });

        loadInventory();
    }

    @FXML
    private void handleRefresh() {
        loadInventory();
    }

    @FXML
    private void handleAddItem() {
        openDialog(null);
    }

    private void handleEditItem(InventoryDTO item) {
        openDialog(item);
    }

    private void openDialog(InventoryDTO item) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/inventory_dialog.fxml"));

            // Set resources bundle for localization
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"));
            loader.setResources(bundle);

            javafx.scene.Parent root = loader.load();

            InventoryDialogController controller = loader.getController();
            if (item != null) {
                controller.setInventoryItem(item);
            }
            controller.setOnSaveSuccess(this::loadInventory);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle(item == null ? "Add Inventory Item" : "Edit Inventory Item");
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
