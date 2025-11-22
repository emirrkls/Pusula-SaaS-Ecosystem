package com.pusula.desktop.controller;

import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;

public class InventoryDialogController {

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

    private Runnable onSaveSuccess;

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
            InventoryDTO newItem = new InventoryDTO();
            newItem.setPartName(partName);
            newItem.setQuantity(Integer.parseInt(quantityStr));
            newItem.setCriticalLevel(criticalLevelStr.isEmpty() ? 0 : Integer.parseInt(criticalLevelStr));
            newItem.setBuyPrice(new BigDecimal(buyPriceStr));
            newItem.setSellPrice(sellPriceStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(sellPriceStr));

            InventoryApi api = RetrofitClient.getClient().create(InventoryApi.class);
            api.createInventory(newItem).enqueue(new Callback<InventoryDTO>() {
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
                                    "Error", "Failed to create item: " + response.code());
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
            });

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
