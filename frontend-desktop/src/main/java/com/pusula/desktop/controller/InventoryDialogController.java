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
import javafx.collections.FXCollections;

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
    private ComboBox<String> unitOfMeasureCombo;

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

    @FXML
    public void initialize() {
        unitOfMeasureCombo.setItems(FXCollections.observableArrayList(
                "Adet", "Kilogram", "Gram", "Metre", "Litre"));
        unitOfMeasureCombo.setValue("Adet");
    }

    public void setInventoryItem(InventoryDTO item) {
        this.currentItem = item;

        // Update dialog title based on add or edit mode
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", new Locale("tr", "TR"));
        if (item != null) {
            titleLabel.setText(bundle.getString("inventory.form.title.edit"));
            partNameField.setText(item.getPartName());
            quantityField.setText(String.valueOf(item.getQuantity()));
            criticalLevelField.setText(String.valueOf(item.getCriticalLevel()));
            unitOfMeasureCombo.setValue(unitDisplay(item.getUnitOfMeasure()));
            buyPriceField.setRawValue(item.getBuyPrice());
            sellPriceField.setRawValue(item.getSellPrice());
            if (item.getBrand() != null)
                brandField.setText(item.getBrand());
            if (item.getCategory() != null)
                categoryField.setText(item.getCategory());

            // Display stock distribution
            BigDecimal warehouseQty = item.getWarehouseQuantity() != null ? item.getWarehouseQuantity() : item.getQuantity();
            BigDecimal vehicleQty = item.getInVehicleQuantity() != null ? item.getInVehicleQuantity() : BigDecimal.ZERO;

            if (warehouseQtyField != null) {
                warehouseQtyField.setText(String.valueOf(warehouseQty));
            }
            if (vehicleQtyField != null) {
                vehicleQtyField.setText(String.valueOf(vehicleQty));
            }

            // Show vehicle distribution details if there are items in vehicles
            if (vehicleQty.signum() > 0 && item.getVehicleDistribution() != null && !item.getVehicleDistribution().isEmpty()) {
                if (vehicleDistributionBox != null) {
                    vehicleDistributionBox.setVisible(true);
                    vehicleDistributionBox.setManaged(true);
                }
                if (vehicleDistributionLabel != null) {
                    StringBuilder sb = new StringBuilder();
                    for (var vd : item.getVehicleDistribution()) {
                        if (sb.length() > 0)
                            sb.append(", ");
                        sb.append(vd.getVehiclePlate()).append(": ").append(formatQuantity(vd.getQuantity()))
                                .append(" ").append(unitShort(item.getUnitOfMeasure()));
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
                    "Eksik Bilgi", "Parça adı, adet ve alış fiyatı zorunludur.");
            return;
        }

        try {
            BigDecimal quantity = parseQuantity(quantityStr);
            BigDecimal criticalLevel = criticalLevelStr.isEmpty() ? BigDecimal.ZERO : parseQuantity(criticalLevelStr);
            String unit = unitCode(unitOfMeasureCombo.getValue());
            if ("ADET".equals(unit) && (hasFraction(quantity) || hasFraction(criticalLevel))) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, partNameField.getScene().getWindow(),
                        "Geçersiz Miktar", "Adet biriminde stok ve kritik seviye tam sayı olmalıdır.");
                return;
            }
            InventoryDTO itemToSave = currentItem != null ? currentItem : new InventoryDTO();
            itemToSave.setPartName(partName);
            itemToSave.setQuantity(quantity);
            itemToSave.setCriticalLevel(criticalLevel);
            itemToSave.setUnitOfMeasure(unit);
            itemToSave.setBuyPrice(buyPriceField.getRawValue());
            itemToSave.setSellPrice(sellPriceField.isEmpty() ? BigDecimal.ZERO : sellPriceField.getRawValue());
            itemToSave.setBrand(brandField.getText().isEmpty() ? null : brandField.getText());
            itemToSave.setCategory(categoryField.getText().isEmpty() ? null : categoryField.getText());

            // Set warehouse quantity from the editable field
            if (warehouseQtyField != null && !warehouseQtyField.getText().isEmpty()) {
                BigDecimal warehouseQuantity = parseQuantity(warehouseQtyField.getText());
                if ("ADET".equals(unit) && hasFraction(warehouseQuantity)) {
                    AlertHelper.showAlert(Alert.AlertType.WARNING, partNameField.getScene().getWindow(),
                            "Geçersiz Miktar", "Adet biriminde depo miktarı tam sayı olmalıdır.");
                    return;
                }
                itemToSave.setWarehouseQuantity(warehouseQuantity);
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
                                    "Hata", com.pusula.desktop.util.ApiErrorHelper.message(
                                            response, "Ürün kaydedilemedi (HTTP " + response.code() + ")."));
                        });
                    }
                }

                @Override
                public void onFailure(Call<InventoryDTO> call, Throwable t) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, partNameField.getScene().getWindow(),
                                "Bağlantı Hatası", "Sunucuya bağlanılamadı: " + t.getMessage());
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
                    "Geçersiz Bilgi", "Adet ve fiyat alanlarına geçerli sayılar girin.");
        }
    }

    private void closeDialog() {
        Stage stage = (Stage) partNameField.getScene().getWindow();
        stage.close();
    }

    private static boolean hasFraction(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() > 0;
    }

    private static BigDecimal parseQuantity(String value) {
        return new BigDecimal(value.trim().replace(',', '.'));
    }

    private static String formatQuantity(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String unitCode(String display) {
        return switch (display == null ? "Adet" : display) {
            case "Kilogram" -> "KG";
            case "Gram" -> "GRAM";
            case "Metre" -> "METRE";
            case "Litre" -> "LITRE";
            default -> "ADET";
        };
    }

    private static String unitDisplay(String code) {
        return switch (code == null ? "ADET" : code) {
            case "KG" -> "Kilogram";
            case "GRAM" -> "Gram";
            case "METRE" -> "Metre";
            case "LITRE" -> "Litre";
            default -> "Adet";
        };
    }

    private static String unitShort(String code) {
        return switch (code == null ? "ADET" : code) {
            case "KG" -> "kg";
            case "GRAM" -> "gr";
            case "METRE" -> "m";
            case "LITRE" -> "lt";
            default -> "adet";
        };
    }
}
