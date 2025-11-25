package com.pusula.desktop.controller;

import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
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
    private TextField buyPriceField;

    @FXML
    private TextField sellPriceField;

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
            buyPriceField.setText(item.getBuyPrice().toString());
            sellPriceField.setText(item.getSellPrice().toString());
        } else {
            titleLabel.setText(bundle.getString("inventory.form.title.add"));
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
        String buyPriceStr = buyPriceField.getText();
        String sellPriceStr = sellPriceField.getText();

        if (partName.isEmpty() || quantityStr.isEmpty() || buyPriceStr.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, partNameField.getScene().getWindow(),
                    "Validation Error", "Part Name, Quantity, and Buy Price are required.");
            return;
        }

        try {
            InventoryDTO itemToSave = currentItem != null ? currentItem : new InventoryDTO();
            itemToSave.setPartName(partName);
            itemToSave.setQuantity(Integer.parseInt(quantityStr));
            itemToSave.setCriticalLevel(criticalLevelStr.isEmpty() ? 0 : Integer.parseInt(criticalLevelStr));
            itemToSave.setBuyPrice(new BigDecimal(buyPriceStr));
            itemToSave.setSellPrice(sellPriceStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(sellPriceStr));

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
