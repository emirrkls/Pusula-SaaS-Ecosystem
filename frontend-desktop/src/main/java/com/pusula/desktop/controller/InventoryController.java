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

import com.pusula.desktop.util.TableUiHelper;

import com.pusula.desktop.util.ThemeHelper;

import com.pusula.desktop.util.UTF8Control;

import javafx.application.Platform;

import javafx.beans.property.SimpleObjectProperty;

import javafx.beans.property.SimpleStringProperty;

import javafx.collections.FXCollections;

import javafx.collections.ObservableList;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;

import javafx.scene.control.*;

import javafx.scene.layout.HBox;

import javafx.scene.layout.VBox;

import javafx.stage.Stage;

import retrofit2.Call;

import retrofit2.Callback;

import retrofit2.Response;



import java.math.BigDecimal;

import java.util.List;

import java.util.Locale;

import java.util.ResourceBundle;



public class InventoryController {



    enum StockFilter {

        ALL, CRITICAL, IN_WAREHOUSE, IN_VEHICLE

    }



    @FXML private TableView<InventoryDTO> inventoryTable;

    @FXML private TableColumn<InventoryDTO, String> colPartName;

    @FXML private TableColumn<InventoryDTO, String> colMeta;

    @FXML private TableColumn<InventoryDTO, Integer> colQuantity;

    @FXML private TableColumn<InventoryDTO, BigDecimal> colBuyPrice;

    @FXML private TableColumn<InventoryDTO, BigDecimal> colSellPrice;

    @FXML private TableColumn<InventoryDTO, Integer> colCriticalLevel;

    @FXML private TableColumn<InventoryDTO, Void> colDistribution;

    @FXML private TableColumn<InventoryDTO, Void> colActions;

    @FXML private TextField searchField;

    @FXML private HBox filterChipContainer;

    @FXML private Label resultCountLabel;

    @FXML private VBox emptyStateBox;

    @FXML private ComboBox<VehicleDTO> vehicleFilterComboBox;

    @FXML private TableView<VehicleStockDTO> vehicleStocksTable;

    @FXML private TableColumn<VehicleStockDTO, String> colVehiclePlate;

    @FXML private TableColumn<VehicleStockDTO, String> colInventoryName;

    @FXML private TableColumn<VehicleStockDTO, Integer> colStockQuantity;



    private final ObservableList<InventoryDTO> inventoryList = FXCollections.observableArrayList();

    private javafx.collections.transformation.FilteredList<InventoryDTO> filteredList;

    private final ObservableList<VehicleStockDTO> vehicleStocksList = FXCollections.observableArrayList();

    private final ObservableList<VehicleDTO> vehiclesList = FXCollections.observableArrayList();

    private javafx.collections.transformation.FilteredList<VehicleStockDTO> filteredVehicleStocksList;



    private StockFilter currentFilter = StockFilter.ALL;

    private final ToggleGroup filterToggleGroup = new ToggleGroup();

    private ResourceBundle bundle;



    @FXML

    public void initialize() {

        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());



        colPartName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("partName"));



        colMeta.setCellValueFactory(cell -> {

            InventoryDTO item = cell.getValue();

            String brand = item.getBrand() != null ? item.getBrand() : "";

            String category = item.getCategory() != null ? item.getCategory() : "";

            String meta = brand.isBlank() && category.isBlank() ? "—"

                    : (brand + (brand.isBlank() || category.isBlank() ? "" : " · ") + category);

            return new SimpleStringProperty(meta);

        });



        colQuantity.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("quantity"));

        colQuantity.setCellFactory(col -> new TableCell<>() {

            @Override

            protected void updateItem(Integer item, boolean empty) {

                super.updateItem(item, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {

                    setGraphic(null);

                    setText(null);

                    return;

                }

                InventoryDTO row = getTableRow().getItem();

                setGraphic(TableUiHelper.createCriticalBadge(item != null ? item : 0, row.getCriticalLevel()));

                setText(null);

            }

        });



        colBuyPrice.setCellValueFactory(cell -> {

            if (com.pusula.desktop.util.SessionManager.isTechnician()) {

                return new SimpleObjectProperty<>(null);

            }

            return new SimpleObjectProperty<>(cell.getValue().getBuyPrice());

        });

        colBuyPrice.setCellFactory(column -> moneyCell(true));

        colSellPrice.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("sellPrice"));

        colSellPrice.setCellFactory(column -> moneyCell(false));

        colCriticalLevel.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("criticalLevel"));



        colDistribution.setCellFactory(col -> new TableCell<>() {

            @Override

            protected void updateItem(Void item, boolean empty) {

                super.updateItem(item, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {

                    setGraphic(null);

                    return;

                }

                InventoryDTO row = getTableRow().getItem();

                int wh = row.getWarehouseQuantity() != null ? row.getWarehouseQuantity() : row.getQuantity();

                int veh = row.getInVehicleQuantity() != null ? row.getInVehicleQuantity() : 0;

                setGraphic(TableUiHelper.createDistributionPills(wh, veh));

            }

        });



        colActions.setCellFactory(col -> new TableCell<>() {

            private final Button editBtn = new Button("✎");

            {

                editBtn.getStyleClass().addAll("btn-icon-sm", "btn-icon-primary");

                editBtn.setOnAction(e -> {

                    InventoryDTO item = getTableView().getItems().get(getIndex());

                    if (item != null) {

                        handleEditItem(item);

                    }

                });

            }



            @Override

            protected void updateItem(Void item, boolean empty) {

                super.updateItem(item, empty);

                setGraphic(empty ? null : editBtn);

            }

        });



        filteredList = new javafx.collections.transformation.FilteredList<>(inventoryList, p -> true);

        searchField.textProperty().addListener((o, ov, nv) -> updatePredicate());

        inventoryTable.setItems(filteredList);

        filteredList.addListener((javafx.collections.ListChangeListener<InventoryDTO>) c -> updateCounts());



        inventoryTable.setRowFactory(tv -> {

            TableRow<InventoryDTO> row = new TableRow<>() {

                @Override

                protected void updateItem(InventoryDTO item, boolean empty) {

                    super.updateItem(item, empty);

                    getStyleClass().remove("table-row-critical");

                    if (item != null && !empty && item.getCriticalLevel() != null

                            && item.getQuantity() <= item.getCriticalLevel()) {

                        getStyleClass().add("table-row-critical");

                    }

                }

            };

            row.setOnMouseClicked(event -> {

                if (!row.isEmpty() && row.getItem() != null) {

                    inventoryTable.getSelectionModel().select(row.getItem());

                    handleEditItem(row.getItem());

                }

            });

            return row;

        });



        setupFilterChips();

        setupVehicleStocksTab();

        loadInventory();

        loadVehicles();

        loadVehicleStocks();

    }



    private TableCell<InventoryDTO, BigDecimal> moneyCell(boolean hideForTechnician) {

        return new TableCell<>() {

            @Override

            protected void updateItem(BigDecimal item, boolean empty) {

                super.updateItem(item, empty);

                if (empty) {

                    setText(null);

                    setAlignment(Pos.CENTER_RIGHT);

                    return;

                }

                if (hideForTechnician && com.pusula.desktop.util.SessionManager.isTechnician()) {

                    setText("-");

                } else {

                    setText(TableUiHelper.formatCurrency(item));

                }

                setAlignment(Pos.CENTER_RIGHT);

            }

        };

    }



    private void setupFilterChips() {

        filterChipContainer.getChildren().clear();

        addChip("Tümü", StockFilter.ALL);

        addChip("Kritik", StockFilter.CRITICAL);

        addChip("Depoda", StockFilter.IN_WAREHOUSE);

        addChip("Araçta", StockFilter.IN_VEHICLE);

        if (filterChipContainer.getChildren().getFirst() instanceof ToggleButton first) {

            first.setSelected(true);

        }

    }



    private void addChip(String label, StockFilter filter) {

        ToggleButton chip = new ToggleButton(label);

        chip.getStyleClass().add("filter-chip");

        chip.setUserData(filter);

        chip.setToggleGroup(filterToggleGroup);

        chip.selectedProperty().addListener((obs, was, is) -> {

            if (is) {

                currentFilter = filter;

                updatePredicate();

            }

        });

        filterChipContainer.getChildren().add(chip);

    }



    private void updatePredicate() {

        String search = searchField.getText();

        filteredList.setPredicate(item -> matchesChip(item) && matchesSearch(item, search));

    }



    private boolean matchesChip(InventoryDTO item) {

        if (item == null) {

            return false;

        }

        int wh = item.getWarehouseQuantity() != null ? item.getWarehouseQuantity() : item.getQuantity();

        int veh = item.getInVehicleQuantity() != null ? item.getInVehicleQuantity() : 0;

        return switch (currentFilter) {

            case ALL -> true;

            case CRITICAL -> item.getCriticalLevel() != null && item.getQuantity() <= item.getCriticalLevel();

            case IN_WAREHOUSE -> wh > 0;

            case IN_VEHICLE -> veh > 0;

        };

    }



    private boolean matchesSearch(InventoryDTO item, String search) {

        if (search == null || search.isBlank()) {

            return true;

        }

        String q = search.toLowerCase(Locale.forLanguageTag("tr-TR"));

        return contains(item.getPartName(), q) || contains(item.getBrand(), q) || contains(item.getCategory(), q);

    }



    private boolean contains(String value, String q) {

        return value != null && value.toLowerCase(Locale.forLanguageTag("tr-TR")).contains(q);

    }



    private void updateCounts() {

        long critical = inventoryList.stream()

                .filter(i -> i.getCriticalLevel() != null && i.getQuantity() <= i.getCriticalLevel())

                .count();

        resultCountLabel.setText(filteredList.size() + " parça · " + critical + " kritik");

        boolean empty = filteredList.isEmpty();

        emptyStateBox.setVisible(empty);

        emptyStateBox.setManaged(empty);

        inventoryTable.setVisible(!empty);

    }



    public void filterCriticalStocks() {

        currentFilter = StockFilter.CRITICAL;

        filterChipContainer.getChildren().stream()

                .filter(n -> n instanceof ToggleButton tb && tb.getUserData() == StockFilter.CRITICAL)

                .findFirst()

                .ifPresent(n -> ((ToggleButton) n).setSelected(true));

        updatePredicate();

    }



    public void clearFilter() {

        currentFilter = StockFilter.ALL;

        searchField.clear();

        if (filterChipContainer.getChildren().getFirst() instanceof ToggleButton first) {

            first.setSelected(true);

        }

        updatePredicate();

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

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/inventory_dialog.fxml"), bundle);

            javafx.scene.Parent root = loader.load();

            InventoryDialogController controller = loader.getController();

            if (item != null) {

                controller.setInventoryItem(item);

            }

            controller.setOnSaveSuccess(this::loadInventory);

            Stage stage = new Stage();

            stage.setTitle(item == null ? "Add Inventory Item" : "Edit Inventory Item");

            stage.setScene(ThemeHelper.createDialogScene(root));

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

        PdfReportGenerator.generateInventoryReport((Stage) inventoryTable.getScene().getWindow(), inventoryList);

    }



    private void loadInventory() {

        InventoryApi api = RetrofitClient.getClient().create(InventoryApi.class);

        api.getAllInventory().enqueue(new Callback<>() {

            @Override

            public void onResponse(Call<List<InventoryDTO>> call, Response<List<InventoryDTO>> response) {

                if (response.isSuccessful() && response.body() != null) {

                    Platform.runLater(() -> {

                        inventoryList.clear();

                        inventoryList.addAll(response.body());

                        updatePredicate();

                    });

                } else {

                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,

                            inventoryTable.getScene().getWindow(), "Error",

                            "Failed to load inventory: " + response.code()));

                }

            }



            @Override

            public void onFailure(Call<List<InventoryDTO>> call, Throwable t) {

                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,

                        inventoryTable.getScene().getWindow(), "Network Error",

                        "Could not connect to server: " + t.getMessage()));

            }

        });

    }



    private void setupVehicleStocksTab() {

        colVehiclePlate.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("vehicleLicensePlate"));

        colInventoryName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("partName"));

        colStockQuantity.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("quantity"));



        vehicleFilterComboBox.setItems(vehiclesList);

        vehicleFilterComboBox.setConverter(new javafx.util.StringConverter<>() {

            @Override

            public String toString(VehicleDTO vehicle) {

                if (vehicle == null) return "";

                return vehicle.getLicensePlate()

                        + (vehicle.getDriverName() != null ? " - " + vehicle.getDriverName() : "");

            }



            @Override

            public VehicleDTO fromString(String string) {

                return null;

            }

        });



        filteredVehicleStocksList = new javafx.collections.transformation.FilteredList<>(vehicleStocksList, p -> true);

        vehicleFilterComboBox.valueProperty().addListener((o, ov, nv) -> filteredVehicleStocksList.setPredicate(stock ->

                nv == null || stock.getVehicleId().equals(nv.getId())));

        vehicleStocksTable.setItems(filteredVehicleStocksList);

    }



    private void loadVehicles() {

        VehicleApi vehicleApi = RetrofitClient.getClient().create(VehicleApi.class);

        vehicleApi.getAll().enqueue(new Callback<>() {

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

        stockApi.getAll().enqueue(new Callback<>() {

            @Override

            public void onResponse(Call<List<VehicleStockDTO>> call, Response<List<VehicleStockDTO>> response) {

                if (response.isSuccessful() && response.body() != null) {

                    Platform.runLater(() -> {

                        vehicleStocksList.clear();

                        List<VehicleStockDTO> sorted = response.body().stream()

                                .sorted((a, b) -> {

                                    String plateA = a.getVehicleLicensePlate() != null ? a.getVehicleLicensePlate() : "";

                                    String plateB = b.getVehicleLicensePlate() != null ? b.getVehicleLicensePlate() : "";

                                    return plateA.compareTo(plateB);

                                })

                                .toList();

                        vehicleStocksList.addAll(sorted);

                    });

                }

            }



            @Override

            public void onFailure(Call<List<VehicleStockDTO>> call, Throwable t) {

                System.err.println("Failed to load vehicle stocks: " + t.getMessage());

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

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/transfer_stock_dialog.fxml"), bundle);

            javafx.scene.Parent root = loader.load();

            TransferStockDialogController controller = loader.getController();

            controller.setOnTransferSuccess(() -> {

                loadInventory();

                loadVehicleStocks();

            });

            Stage stage = new Stage();

            stage.setTitle("Stok Transfer");

            stage.setScene(ThemeHelper.createDialogScene(root));

            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            stage.showAndWait();

        } catch (Exception e) {

            e.printStackTrace();

            AlertHelper.showAlert(Alert.AlertType.ERROR, vehicleStocksTable.getScene().getWindow(),

                    "Hata", "Dialog açılamadı: " + e.getMessage());

        }

    }

}


