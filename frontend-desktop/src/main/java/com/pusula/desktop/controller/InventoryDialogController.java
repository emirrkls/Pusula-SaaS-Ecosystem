package com.pusula.desktop.controller;

import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CurrencyTextField;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class InventoryDialogController {

    @FXML
    private Label titleLabel;

    @FXML
    private TextField partNameField;

    @FXML
    private TextField quantityField;

    @FXML
    private TextField criticalLevelField;

    @FXML
    private CurrencyTextField buyPriceField;

    @FXML
    private CurrencyTextField sellPriceField;

    @FXML
    private TextField brandField;

    @FXML
    private TextField categoryField;

    @FXML
    private TextField warehouseQtyField;

    @FXML
    private TextField vehicleQtyField;

    @FXML
    private VBox vehicleDistributionBox;

    @FXML
    private Label vehicleDistributionLabel;

    private InventoryDTO currentItem;
    private Runnable onSaveSuccess;

    public void setInventoryItem(InventoryDTO item) {
        this.currentItem = item;

        // Update dialog title based on add or edit mode
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", new Locale("tr", "TR"));
        if (item != null) {
            titleLabel.setText(bundle.getString("inventory.form.title.edit"));
            partNameField.setText(item.getPartName());
            quantityField.setText(String.valueOf(item.getQuantity()));
            criticalLevelField.setText(String.valueOf(item.getCriticalLevel()));
            buyPriceField.setRawValue(item.getBuyPrice());
            sellPriceField.setRawValue(item.getSellPrice());
            if (item.getBrand() != null)
                brandField.setText(item.getBrand());
            if (item.getCategory() != null)
                categoryField.setText(item.getCategory());

            // Display stock distribution
            int warehouseQty = item.getWarehouseQuantity() != null ? item.getWarehouseQuantity() : item.getQuantity();
            int vehicleQty = item.getInVehicleQuantity() != null ? item.getInVehicleQuantity() : 0;

            if (warehouseQtyField != null) {
                warehouseQtyField.setText(String.valueOf(warehouseQty));
            }
            if (vehicleQtyField != null) {
                vehicleQtyField.setText(String.valueOf(vehicleQty));
            }

            // Show vehicle distribution details if there are items in vehicles
            if (vehicleQty > 0 && item.getVehicleDistribution() != null && !item.getVehicleDistribution().isEmpty()) {
                if (vehicleDistributionBox != null) {
                    vehicleDistributionBox.setVisible(true);
                    vehicleDistributionBox.setManaged(true);
                }
                if (vehicleDistributionLabel != null) {
                    StringBuilder sb = new StringBuilder();
                    for (var vd : item.getVehicleDistribution()) {
                        if (sb.length() > 0)
                            sb.append(", ");
                        sb.append(vd.getVehiclePlate()).append(": ").append(vd.getQuantity()).append(" adet");
                    }
                    vehicleDistributionLabel.setText(sb.toString());
                }
            }
        } else {
            titleLabel.setText(bundle.getString("inventory.form.title.add"));
            // Default values for new item
            if (warehouseQtyField != null)
                warehouseQtyField.setText("0");
            if (vehicleQtyField != null)
                vehicleQtyField.setText("0");
        }
    }

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    @FXML
    private void handleSave() {
        String partName = partNameField.getText();
        String quantityStr = quantityField.getText();
        String criticalLevelStr = criticalLevelField.getText();

        if (partName.isEmpty() || quantityStr.isEmpty() || buyPriceField.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, partNameField.getScene().getWindow(),
                    "Validation Error", "Part Name, Quantity, and Buy Price are required.");
            return;
        }

        try {
            InventoryDTO itemToSave = currentItem != null ? currentItem : new InventoryDTO();
            itemToSave.setPartName(partName);
            itemToSave.setQuantity(Integer.parseInt(quantityStr));
            itemToSave.setCriticalLevel(criticalLevelStr.isEmpty() ? 0 : Integer.parseInt(criticalLevelStr));
            itemToSave.setBuyPrice(buyPriceField.getRawValue());
            itemToSave.setSellPrice(sellPriceField.isEmpty() ? BigDecimal.ZERO : sellPriceField.getRawValue());
            itemToSave.setBrand(brandField.getText().isEmpty() ? null : brandField.getText());
            itemToSave.setCategory(categoryField.getText().isEmpty() ? null : categoryField.getText());

            // Set warehouse quantity from the editable field
            if (warehouseQtyField != null && !warehouseQtyField.getText().isEmpty()) {
                itemToSave.setWarehouseQuantity(Integer.parseInt(warehouseQtyField.getText()));
            }

            InventoryApi api = RetrofitClient.getClient().create(InventoryApi.class);
            Callback<InventoryDTO> callback = new Callback<>() {
                @Override
                public void onResponse(Call<InventoryDTO> call, Response<InventoryDTO> response) {
                    if (response.isSuccessful()) {
                        Platform.runLater(() -> {
                            if (onSaveSuccess != null) {
                                onSaveSuccess.run();
                            }
                            closeDialog();
                        });
                    } else {
                        Platform.runLater(() -> {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, partNameField.getScene().getWindow(),
                                    "Error", "Failed to save item: " + response.code());
                        });
                    }
                }

                @Override
                public void onFailure(Call<InventoryDTO> call, Throwable t) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, partNameField.getScene().getWindow(),
                                "Network Error", "Could not connect to server: " + t.getMessage());
                    });
                }
            };

            if (itemToSave.getId() != null) {
                api.updateInventory(itemToSave.getId(), itemToSave).enqueue(callback);
            } else {
                api.createInventory(itemToSave).enqueue(callback);
            }

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, partNameField.getScene().getWindow(),
                    "Validation Error", "Please enter valid numbers for Quantity and Price.");
        }
    }

    private void closeDialog() {
        Stage stage = (Stage) partNameField.getScene().getWindow();
        stage.close();
    }
}
