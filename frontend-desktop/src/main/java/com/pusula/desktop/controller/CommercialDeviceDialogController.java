package com.pusula.desktop.controller;

import com.pusula.desktop.api.CommercialDeviceApi;
import com.pusula.desktop.dto.CommercialDeviceDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CurrencyTextField;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;

public class CommercialDeviceDialogController {

    @FXML
    private TextField txtBrand;
    @FXML
    private TextField txtModel;
    @FXML
    private ComboBox<String> cmbDeviceType;
    @FXML
    private TextField txtBtu;
    @FXML
    private ComboBox<String> cmbGasType;
    @FXML
    private TextField txtQuantity;
    @FXML
    private CurrencyTextField txtBuyingPrice;
    @FXML
    private CurrencyTextField txtSellingPrice;

    private CommercialDeviceApi api;
    private CommercialDeviceDTO device;
    private Runnable onSave;

    @FXML
    public void initialize() {
        api = RetrofitClient.getClient().create(CommercialDeviceApi.class);

        // Populate gas types
        cmbGasType.setItems(FXCollections.observableArrayList(
                "R32", "R410A", "R22", "R134a", "R290", "Doğalgaz", "LPG", "Diğer"));

        // Device types - only Klima and Isı Pompası for now
        cmbDeviceType.setItems(FXCollections.observableArrayList(
                "Klima", "Isı Pompası"));
    }

    public void setDevice(CommercialDeviceDTO device) {
        this.device = device;
        if (device != null) {
            txtBrand.setText(device.getBrand());
            txtModel.setText(device.getModel());
            cmbDeviceType.setValue(device.getDeviceTypeName());
            if (device.getBtu() != null) {
                txtBtu.setText(device.getBtu().toString());
            }
            cmbGasType.setValue(device.getGasType());
            txtQuantity.setText(device.getQuantity() != null ? device.getQuantity().toString() : "0");
            if (device.getBuyingPrice() != null) {
                txtBuyingPrice.setRawValue(device.getBuyingPrice());
            }
            if (device.getSellingPrice() != null) {
                txtSellingPrice.setRawValue(device.getSellingPrice());
            }
        }
    }

    public void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }

    @FXML
    private void handleSave() {
        if (!validate()) {
            return;
        }

        CommercialDeviceDTO dto = new CommercialDeviceDTO();
        dto.setBrand(txtBrand.getText().trim());
        dto.setModel(txtModel.getText().trim());
        // Note: DeviceType matching by name would need backend support or we skip it
        // for now
        try {
            if (!txtBtu.getText().trim().isEmpty()) {
                dto.setBtu(Integer.parseInt(txtBtu.getText().trim()));
            }
        } catch (NumberFormatException e) {
            // Ignore invalid BTU
        }
        dto.setGasType(cmbGasType.getValue());
        try {
            dto.setQuantity(Integer.parseInt(txtQuantity.getText().trim()));
        } catch (NumberFormatException e) {
            dto.setQuantity(0);
        }
        try {
            dto.setBuyingPrice(txtBuyingPrice.getRawValue());
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Geçerli bir alış fiyatı girin.");
            return;
        }
        try {
            dto.setSellingPrice(txtSellingPrice.getRawValue());
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Geçerli bir satış fiyatı girin.");
            return;
        }

        Callback<CommercialDeviceDTO> callback = new Callback<CommercialDeviceDTO>() {
            @Override
            public void onResponse(Call<CommercialDeviceDTO> call, Response<CommercialDeviceDTO> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Cihaz kaydedildi.");
                        if (onSave != null) {
                            onSave.run();
                        }
                        closeDialog();
                    } else {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                                "Kayıt başarısız: " + response.code());
                    }
                });
            }

            @Override
            public void onFailure(Call<CommercialDeviceDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Kayıt başarısız: " + t.getMessage());
                });
            }
        };

        if (device != null && device.getId() != null) {
            api.update(device.getId(), dto).enqueue(callback);
        } else {
            api.create(dto).enqueue(callback);
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private boolean validate() {
        if (txtBrand.getText().trim().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Marka boş olamaz.");
            return false;
        }
        if (txtModel.getText().trim().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Model boş olamaz.");
            return false;
        }
        if (txtBuyingPrice.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Alış fiyatı boş olamaz.");
            return false;
        }
        if (txtSellingPrice.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Satış fiyatı boş olamaz.");
            return false;
        }
        return true;
    }

    private void closeDialog() {
        Stage stage = (Stage) txtBrand.getScene().getWindow();
        stage.close();
    }
}
