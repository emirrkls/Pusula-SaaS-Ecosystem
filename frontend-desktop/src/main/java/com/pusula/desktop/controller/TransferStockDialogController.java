package com.pusula.desktop.controller;

import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.api.VehicleApi;
import com.pusula.desktop.api.VehicleStockApi;
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.dto.VehicleDTO;
import com.pusula.desktop.dto.VehicleStockDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransferStockDialogController {

    @FXML
    private RadioButton radioToVehicle;
    @FXML
    private RadioButton radioToWarehouse;
    @FXML
    private ComboBox<VehicleDTO> vehicleComboBox;
    @FXML
    private ComboBox<InventoryDTO> inventoryComboBox;
    @FXML
    private Label warehouseStockLabel;
    @FXML
    private Label vehicleStockLabel;
    @FXML
    private Spinner<Integer> quantitySpinner;
    @FXML
    private Button transferButton;
    @FXML
    private ListView<String> vehicleStockListView;
    @FXML
    private Label vehicleStockPreviewLabel;

    private final ObservableList<VehicleDTO> vehicleList = FXCollections.observableArrayList();
    private final ObservableList<InventoryDTO> inventoryList = FXCollections.observableArrayList();
    private final ObservableList<InventoryDTO> filteredInventoryList = FXCollections.observableArrayList();
    private final ObservableList<String> vehicleStockItems = FXCollections.observableArrayList();

    private VehicleStockApi vehicleStockApi;
    private Runnable onTransferSuccess;

    @FXML
    public void initialize() {
        vehicleStockApi = RetrofitClient.getClient().create(VehicleStockApi.class);

        // Setup vehicle ComboBox
        vehicleComboBox.setItems(vehicleList);
        vehicleComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(VehicleDTO v) {
                if (v == null)
                    return "";
                return v.getLicensePlate() + (v.getDriverName() != null ? " - " + v.getDriverName() : "");
            }

            @Override
            public VehicleDTO fromString(String s) {
                return null;
            }
        });

        // Setup inventory ComboBox with search/filter
        inventoryComboBox.setItems(filteredInventoryList);
        inventoryComboBox.setEditable(true);
        inventoryComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(InventoryDTO i) {
                if (i == null)
                    return "";
                return i.getPartName() + (i.getBrand() != null ? " (" + i.getBrand() + ")" : "");
            }

            @Override
            public InventoryDTO fromString(String s) {
                if (s == null || s.isEmpty())
                    return null;
                // Find matching item by name (case-insensitive, partial match)
                String lower = s.toLowerCase(java.util.Locale.forLanguageTag("tr-TR"));
                return inventoryList.stream()
                        .filter(i -> {
                            String display = i.getPartName() + (i.getBrand() != null ? " (" + i.getBrand() + ")" : "");
                            return display.toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(lower);
                        })
                        .findFirst()
                        .orElse(null);
            }
        });

        // Add search/filter listener - only filter while user is actively typing
        inventoryComboBox.getEditor().setOnKeyReleased(event -> {
            String text = inventoryComboBox.getEditor().getText();
            if (text == null || text.isEmpty()) {
                filteredInventoryList.setAll(inventoryList);
            } else {
                String lowerFilter = text.toLowerCase(java.util.Locale.forLanguageTag("tr-TR"));
                filteredInventoryList.setAll(
                        inventoryList.stream()
                                .filter(item -> {
                                    String partName = item.getPartName() != null
                                            ? item.getPartName().toLowerCase(java.util.Locale.forLanguageTag("tr-TR"))
                                            : "";
                                    String brand = item.getBrand() != null
                                            ? item.getBrand().toLowerCase(java.util.Locale.forLanguageTag("tr-TR"))
                                            : "";
                                    return partName.contains(lowerFilter) || brand.contains(lowerFilter);
                                })
                                .collect(java.util.stream.Collectors.toList()));
            }
            if (!filteredInventoryList.isEmpty()) {
                inventoryComboBox.show();
            }
        });

        // When an item is selected from dropdown, update the value properly
        inventoryComboBox.setOnAction(event -> {
            InventoryDTO selected = inventoryComboBox.getValue();
            if (selected != null) {
                inventoryComboBox.getEditor().setText(selected.getPartName() +
                        (selected.getBrand() != null ? " (" + selected.getBrand() + ")" : ""));
            }
            updateStockLabels();
        });

        // Setup quantity spinner
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1);
        quantitySpinner.setValueFactory(valueFactory);

        // Setup vehicle stock list view
        vehicleStockListView.setItems(vehicleStockItems);
        vehicleStockListView.setPlaceholder(new javafx.scene.control.Label("Araç seçiniz..."));

        // Update stock labels and vehicle preview when selections change
        vehicleComboBox.valueProperty().addListener((obs, old, newVal) -> {
            updateStockLabels();
            updateVehicleStockPreview();
        });
        radioToVehicle.selectedProperty().addListener((obs, old, newVal) -> updateStockLabels());

        // Load data
        loadVehicles();
        loadInventory();
    }

    private void loadVehicles() {
        VehicleApi api = RetrofitClient.getClient().create(VehicleApi.class);
        api.getAll().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<VehicleDTO>> call, Response<List<VehicleDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        vehicleList.clear();
                        vehicleList.addAll(response.body());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<VehicleDTO>> call, Throwable t) {
                System.err.println("Failed to load vehicles: " + t.getMessage());
            }
        });
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
                        filteredInventoryList.setAll(inventoryList);
                    });
                }
            }

            @Override
            public void onFailure(Call<List<InventoryDTO>> call, Throwable t) {
                System.err.println("Failed to load inventory: " + t.getMessage());
            }
        });
    }

    private void updateStockLabels() {
        InventoryDTO selectedItem = inventoryComboBox.getValue();
        VehicleDTO selectedVehicle = vehicleComboBox.getValue();

        if (selectedItem != null) {
            // Warehouse stock from InventoryDTO - use warehouseQuantity if available, else
            // total quantity
            Integer warehouseQty = selectedItem.getWarehouseQuantity();
            Integer totalQty = selectedItem.getQuantity();
            int displayQty = warehouseQty != null ? warehouseQty : (totalQty != null ? totalQty : 0);
            warehouseStockLabel.setText(String.valueOf(displayQty));
            System.out.println("Selected item: " + selectedItem.getPartName() + " - Warehouse: " + displayQty
                    + " (warehouseQty=" + warehouseQty + ", totalQty=" + totalQty + ")");

            // Vehicle stock - need to fetch from vehicle stocks
            if (selectedVehicle != null) {
                vehicleStockApi.getByVehicle(selectedVehicle.getId()).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<List<VehicleStockDTO>> call, Response<List<VehicleStockDTO>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Platform.runLater(() -> {
                                int vehicleQty = response.body().stream()
                                        .filter(vs -> vs.getInventoryId() != null
                                                && vs.getInventoryId().equals(selectedItem.getId()))
                                        .mapToInt(vs -> vs.getQuantity() != null ? vs.getQuantity() : 0)
                                        .sum();
                                vehicleStockLabel.setText(String.valueOf(vehicleQty));
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<List<VehicleStockDTO>> call, Throwable t) {
                        Platform.runLater(() -> vehicleStockLabel.setText("?"));
                    }
                });
            } else {
                vehicleStockLabel.setText("0");
            }
        } else {
            warehouseStockLabel.setText("0");
            vehicleStockLabel.setText("0");
        }
    }

    private void updateVehicleStockPreview() {
        VehicleDTO selectedVehicle = vehicleComboBox.getValue();

        if (selectedVehicle == null) {
            vehicleStockItems.clear();
            vehicleStockPreviewLabel.setText("Araç Mevcut Stoku:");
            return;
        }

        vehicleStockPreviewLabel.setText("Araç Mevcut Stoku: " + selectedVehicle.getLicensePlate());

        vehicleStockApi.getByVehicle(selectedVehicle.getId()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<VehicleStockDTO>> call, Response<List<VehicleStockDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        vehicleStockItems.clear();
                        List<VehicleStockDTO> stocks = response.body();
                        if (stocks.isEmpty()) {
                            vehicleStockItems.add("(Bu araçta stok yok)");
                        } else {
                            for (VehicleStockDTO stock : stocks) {
                                String partName = stock.getPartName() != null ? stock.getPartName()
                                        : "Bilinmeyen Parça";
                                int qty = stock.getQuantity() != null ? stock.getQuantity() : 0;
                                vehicleStockItems.add(partName + " → " + qty + " adet");
                            }
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<VehicleStockDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    vehicleStockItems.clear();
                    vehicleStockItems.add("(Stok bilgisi alınamadı)");
                });
            }
        });
    }

    public void setOnTransferSuccess(Runnable callback) {
        this.onTransferSuccess = callback;
    }

    @FXML
    private void handleTransfer() {
        VehicleDTO vehicle = vehicleComboBox.getValue();
        InventoryDTO item = inventoryComboBox.getValue();
        Integer quantity = quantitySpinner.getValue();

        if (vehicle == null || item == null || quantity == null || quantity <= 0) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, transferButton.getScene().getWindow(),
                    "Eksik Bilgi", "Lütfen araç, parça ve miktar seçiniz.");
            return;
        }

        boolean toVehicle = radioToVehicle.isSelected();

        if (toVehicle) {
            // Transfer from warehouse to vehicle - create or update vehicle stock
            Map<String, Object> request = new HashMap<>();
            request.put("vehicleId", vehicle.getId());
            request.put("inventoryId", item.getId());
            request.put("quantity", quantity);

            vehicleStockApi.create(request).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<VehicleStockDTO> call, Response<VehicleStockDTO> response) {
                    Platform.runLater(() -> {
                        if (response.isSuccessful()) {
                            AlertHelper.showAlert(Alert.AlertType.INFORMATION, transferButton.getScene().getWindow(),
                                    "Başarılı", quantity + " adet " + item.getPartName() + " araca transfer edildi.");
                            if (onTransferSuccess != null)
                                onTransferSuccess.run();
                            closeDialog();
                        } else {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, transferButton.getScene().getWindow(),
                                    "Hata", "Transfer başarısız: " + response.code());
                        }
                    });
                }

                @Override
                public void onFailure(Call<VehicleStockDTO> call, Throwable t) {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            transferButton.getScene().getWindow(), "Hata", "Bağlantı hatası: " + t.getMessage()));
                }
            });
        } else {
            // Transfer from vehicle to warehouse - decrease vehicle stock
            // First we need to find the vehicle stock record
            vehicleStockApi.getByVehicle(vehicle.getId()).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<List<VehicleStockDTO>> call, Response<List<VehicleStockDTO>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        VehicleStockDTO stock = response.body().stream()
                                .filter(vs -> vs.getInventoryId().equals(item.getId()))
                                .findFirst()
                                .orElse(null);

                        if (stock == null || stock.getQuantity() < quantity) {
                            Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.WARNING,
                                    transferButton.getScene().getWindow(), "Yetersiz Stok",
                                    "Araçta yeterli stok yok."));
                            return;
                        }

                        // Update with reduced quantity
                        Map<String, Object> updateRequest = new HashMap<>();
                        updateRequest.put("vehicleId", vehicle.getId());
                        updateRequest.put("inventoryId", item.getId());
                        updateRequest.put("quantity", stock.getQuantity() - quantity);

                        vehicleStockApi.update(stock.getId(), updateRequest).enqueue(new Callback<>() {
                            @Override
                            public void onResponse(Call<VehicleStockDTO> call2, Response<VehicleStockDTO> response2) {
                                Platform.runLater(() -> {
                                    if (response2.isSuccessful()) {
                                        AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                                                transferButton.getScene().getWindow(), "Başarılı",
                                                quantity + " adet " + item.getPartName() + " depoya transfer edildi.");
                                        if (onTransferSuccess != null)
                                            onTransferSuccess.run();
                                        closeDialog();
                                    } else {
                                        AlertHelper.showAlert(Alert.AlertType.ERROR,
                                                transferButton.getScene().getWindow(), "Hata",
                                                "Transfer başarısız: " + response2.code());
                                    }
                                });
                            }

                            @Override
                            public void onFailure(Call<VehicleStockDTO> call2, Throwable t) {
                                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                        transferButton.getScene().getWindow(), "Hata",
                                        "Bağlantı hatası: " + t.getMessage()));
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<List<VehicleStockDTO>> call, Throwable t) {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            transferButton.getScene().getWindow(), "Hata",
                            "Stok bilgisi alınamadı: " + t.getMessage()));
                }
            });
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) transferButton.getScene().getWindow();
        stage.close();
    }
}
