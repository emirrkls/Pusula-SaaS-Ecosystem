package com.pusula.desktop.controller;

import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.api.VehicleApi;
import com.pusula.desktop.api.VehicleStockApi;
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.dto.VehicleDTO;
import com.pusula.desktop.dto.VehicleStockDTO;
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
    private TableColumn<InventoryDTO, String> colDistribution;

    @FXML
    private javafx.scene.control.TextField searchField;
    // Show All button - optional UI element for clearing filter
    private javafx.scene.control.Button btnShowAll;

    // Vehicle Stocks Tab
    @FXML
    private javafx.scene.control.ComboBox<VehicleDTO> vehicleFilterComboBox;

    @FXML
    private TableView<VehicleStockDTO> vehicleStocksTable;

    @FXML
    private TableColumn<VehicleStockDTO, String> colVehiclePlate;

    @FXML
    private TableColumn<VehicleStockDTO, String> colInventoryName;

    @FXML
    private TableColumn<VehicleStockDTO, Integer> colStockQuantity;

    private final ObservableList<InventoryDTO> inventoryList = FXCollections.observableArrayList();
    private javafx.collections.transformation.FilteredList<InventoryDTO> filteredList;

    private final ObservableList<VehicleStockDTO> vehicleStocksList = FXCollections.observableArrayList();
    private final ObservableList<VehicleDTO> vehiclesList = FXCollections.observableArrayList();
    private javafx.collections.transformation.FilteredList<VehicleStockDTO> filteredVehicleStocksList;

    @FXML
    public void initialize() {
        colPartName.setCellValueFactory(new PropertyValueFactory<>("partName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        // Hide buy price from technicians - show "-" instead
        colBuyPrice.setCellValueFactory(cellData -> {
            if (com.pusula.desktop.util.SessionManager.isTechnician()) {
                return new javafx.beans.property.SimpleObjectProperty<>(null);
            }
            return new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getBuyPrice());
        });
        colBuyPrice.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(java.math.BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else if (com.pusula.desktop.util.SessionManager.isTechnician()) {
                    setText("-");
                } else {
                    setText(item != null ? item.toString() : "0");
                }
            }
        });

        colSellPrice.setCellValueFactory(new PropertyValueFactory<>("sellPrice"));
        colCriticalLevel.setCellValueFactory(new PropertyValueFactory<>("criticalLevel"));

        // Distribution column: shows "Warehouse / Vehicle" breakdown
        colDistribution.setCellValueFactory(cellData -> {
            InventoryDTO item = cellData.getValue();
            Integer warehouse = item.getWarehouseQuantity();
            Integer inVehicle = item.getInVehicleQuantity();
            String display = (warehouse != null ? warehouse : item.getQuantity()) + " / "
                    + (inVehicle != null ? inVehicle : 0);
            return new javafx.beans.property.SimpleStringProperty(display);
        });

        // 1. Search Logic - Filter by Name, Brand, OR Category
        filteredList = new javafx.collections.transformation.FilteredList<>(inventoryList, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(item -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase(java.util.Locale.forLanguageTag("tr-TR"));

                // Search in part name
                if (item.getPartName() != null && item.getPartName()
                        .toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(lowerCaseFilter)) {
                    return true;
                }

                // Search in brand
                if (item.getBrand() != null && item.getBrand().toLowerCase(java.util.Locale.forLanguageTag("tr-TR"))
                        .contains(lowerCaseFilter)) {
                    return true;
                }

                // Search in category
                if (item.getCategory() != null && item.getCategory()
                        .toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(lowerCaseFilter)) {
                    return true;
                }

                return false;
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

        // Setup Vehicle Stocks Tab
        setupVehicleStocksTab();

        loadInventory();
        loadVehicles();
        loadVehicleStocks();
    }

    // ========== CRITICAL STOCK FILTERING ==========

    /**
     * Filters the inventory to show only critical stock items (quantity <=
     * criticalLevel)
     */
    public void filterCriticalStocks() {
        filteredList.setPredicate(item -> {
            if (item == null)
                return false;
            return item.getQuantity() <= item.getCriticalLevel();
        });
        if (btnShowAll != null) {
            btnShowAll.setVisible(true);
            btnShowAll.setManaged(true);
        }
    }

    /**
     * Clears all filters and shows all inventory items
     */
    public void clearFilter() {
        filteredList.setPredicate(item -> true);
        if (searchField != null) {
            searchField.clear();
        }
        if (btnShowAll != null) {
            btnShowAll.setVisible(false);
            btnShowAll.setManaged(false);
        }
    }

    @FXML
    private void handleClearFilter() {
        clearFilter();
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

    // ============ VEHICLE STOCKS TAB ============

    private void setupVehicleStocksTab() {
        // Setup table columns
        colVehiclePlate.setCellValueFactory(new PropertyValueFactory<>("vehicleLicensePlate"));
        colInventoryName.setCellValueFactory(new PropertyValueFactory<>("partName"));
        colStockQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        // Setup vehicle filter ComboBox
        vehicleFilterComboBox.setItems(vehiclesList);
        vehicleFilterComboBox.setConverter(new javafx.util.StringConverter<VehicleDTO>() {
            @Override
            public String toString(VehicleDTO vehicle) {
                if (vehicle == null)
                    return "";
                return vehicle.getLicensePlate()
                        + (vehicle.getDriverName() != null ? " - " + vehicle.getDriverName() : "");
            }

            @Override
            public VehicleDTO fromString(String string) {
                return null;
            }
        });

        // Filter vehicle stocks by selected vehicle
        filteredVehicleStocksList = new javafx.collections.transformation.FilteredList<>(vehicleStocksList, p -> true);
        vehicleFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            filteredVehicleStocksList.setPredicate(stock -> {
                if (newValue == null) {
                    return true; // Show all
                }
                return stock.getVehicleId().equals(newValue.getId());
            });
        });
        vehicleStocksTable.setItems(filteredVehicleStocksList);
    }

    private void loadVehicles() {
        VehicleApi vehicleApi = RetrofitClient.getClient().create(VehicleApi.class);
        vehicleApi.getAll().enqueue(new Callback<List<VehicleDTO>>() {
            @Override
            public void onResponse(Call<List<VehicleDTO>> call, Response<List<VehicleDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        vehiclesList.clear();
                        vehiclesList.addAll(response.body());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<VehicleDTO>> call, Throwable t) {
                System.err.println("Failed to load vehicles: " + t.getMessage());
            }
        });
    }

    private void loadVehicleStocks() {
        VehicleStockApi stockApi = RetrofitClient.getClient().create(VehicleStockApi.class);
        stockApi.getAll().enqueue(new Callback<List<VehicleStockDTO>>() {
            @Override
            public void onResponse(Call<List<VehicleStockDTO>> call, Response<List<VehicleStockDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        vehicleStocksList.clear();
                        // Sort by vehicle plate to group items together
                        List<VehicleStockDTO> sorted = response.body().stream()
                                .sorted((a, b) -> {
                                    String plateA = a.getVehicleLicensePlate() != null ? a.getVehicleLicensePlate()
                                            : "";
                                    String plateB = b.getVehicleLicensePlate() != null ? b.getVehicleLicensePlate()
                                            : "";
                                    return plateA.compareTo(plateB);
                                })
                                .collect(java.util.stream.Collectors.toList());
                        vehicleStocksList.addAll(sorted);

                        // Set up row factory to alternate colors per vehicle
                        setupVehicleStockRowFactory();
                    });
                }
            }

            @Override
            public void onFailure(Call<List<VehicleStockDTO>> call, Throwable t) {
                System.err.println("Failed to load vehicle stocks: " + t.getMessage());
            }
        });
    }

    private void setupVehicleStockRowFactory() {
        vehicleStocksTable.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(VehicleStockDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else {
                    // Alternate background color based on vehicle plate
                    String plate = item.getVehicleLicensePlate() != null ? item.getVehicleLicensePlate() : "";
                    int hash = Math.abs(plate.hashCode());
                    int colorIndex = hash % 4;

                    switch (colorIndex) {
                        case 0 -> setStyle("-fx-background-color: #e8f5e9;"); // Light green
                        case 1 -> setStyle("-fx-background-color: #e3f2fd;"); // Light blue
                        case 2 -> setStyle("-fx-background-color: #fff3e0;"); // Light orange
                        case 3 -> setStyle("-fx-background-color: #f3e5f5;"); // Light purple
                    }
                }
            }
        });
    }

    @FXML
    private void handleRefreshVehicleStocks() {
        loadVehicles();
        loadVehicleStocks();
    }

    @FXML
    private void handleTransferStock() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/transfer_stock_dialog.fxml"));

            // Set resources bundle for localization
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"));
            loader.setResources(bundle);

            javafx.scene.Parent root = loader.load();

            TransferStockDialogController controller = loader.getController();
            controller.setOnTransferSuccess(() -> {
                loadInventory();
                loadVehicleStocks();
            });

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Stok Transfer");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, vehicleStocksTable.getScene().getWindow(),
                    "Hata", "Dialog açılamadı: " + e.getMessage());
        }
    }
}
