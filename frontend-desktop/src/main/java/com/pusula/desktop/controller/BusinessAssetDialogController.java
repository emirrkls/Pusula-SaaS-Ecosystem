package com.pusula.desktop.controller;

import com.pusula.desktop.api.BusinessAssetApi;
import com.pusula.desktop.dto.BusinessAssetDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CurrencyTextField;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.util.List;

public class BusinessAssetDialogController {
    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField categoryField;
    @FXML private TextField quantityField;
    @FXML private ComboBox<String> conditionCombo;
    @FXML private TextField serialNumberField;
    @FXML private TextField locationField;
    @FXML private TextField assignedToField;
    @FXML private DatePicker purchaseDatePicker;
    @FXML private CurrencyTextField purchasePriceField;
    @FXML private TextArea notesArea;

    private BusinessAssetDTO currentAsset;
    private Runnable onSaveSuccess;

    @FXML
    public void initialize() {
        conditionCombo.getItems().setAll("ACTIVE", "MAINTENANCE", "BROKEN", "RETIRED");
        conditionCombo.setConverter(new StringConverter<>() {
            @Override public String toString(String value) { return conditionLabel(value); }
            @Override public String fromString(String value) { return value; }
        });
        conditionCombo.setValue("ACTIVE");
        quantityField.setText("1");
    }

    public void setAsset(BusinessAssetDTO asset) {
        currentAsset = asset;
        if (asset == null) return;
        titleLabel.setText("Takım / Demirbaş Düzenle");
        nameField.setText(asset.getAssetName());
        categoryField.setText(asset.getCategory());
        quantityField.setText(String.valueOf(asset.getQuantity() == null ? 1 : asset.getQuantity()));
        conditionCombo.setValue(asset.getCondition() == null ? "ACTIVE" : asset.getCondition());
        serialNumberField.setText(asset.getSerialNumber());
        locationField.setText(asset.getLocation());
        assignedToField.setText(asset.getAssignedTo());
        purchaseDatePicker.setValue(asset.getPurchaseDate());
        purchasePriceField.setRawValue(asset.getPurchasePrice());
        notesArea.setText(asset.getNotes());
    }

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            warn("Ad alanı zorunludur.");
            return;
        }

        try {
            int quantity = Integer.parseInt(quantityField.getText().trim());
            if (quantity < 1) {
                warn("Adet en az 1 olmalıdır.");
                return;
            }

            BusinessAssetDTO asset = currentAsset == null ? new BusinessAssetDTO() : currentAsset;
            asset.setAssetName(name);
            asset.setCategory(clean(categoryField.getText()));
            asset.setQuantity(quantity);
            asset.setCondition(conditionCombo.getValue() == null ? "ACTIVE" : conditionCombo.getValue());
            asset.setSerialNumber(clean(serialNumberField.getText()));
            asset.setLocation(clean(locationField.getText()));
            asset.setAssignedTo(clean(assignedToField.getText()));
            asset.setPurchaseDate(purchaseDatePicker.getValue());
            asset.setPurchasePrice(purchasePriceField.isEmpty() ? BigDecimal.ZERO : purchasePriceField.getRawValue());
            asset.setNotes(clean(notesArea.getText()));

            BusinessAssetApi api = RetrofitClient.getClient().create(BusinessAssetApi.class);
            Callback<BusinessAssetDTO> callback = new Callback<>() {
                @Override
                public void onResponse(Call<BusinessAssetDTO> call, Response<BusinessAssetDTO> response) {
                    Platform.runLater(() -> {
                        if (response.isSuccessful()) {
                            if (onSaveSuccess != null) onSaveSuccess.run();
                            close();
                        } else {
                            error("Kayıt kaydedilemedi (HTTP " + response.code() + ").");
                        }
                    });
                }

                @Override
                public void onFailure(Call<BusinessAssetDTO> call, Throwable throwable) {
                    Platform.runLater(() -> error("Sunucuya bağlanılamadı: " + throwable.getMessage()));
                }
            };

            if (asset.getId() == null) api.create(asset).enqueue(callback);
            else api.update(asset.getId(), asset).enqueue(callback);
        } catch (NumberFormatException e) {
            warn("Adet geçerli bir tam sayı olmalıdır.");
        }
    }

    @FXML private void handleCancel() { close(); }

    private void warn(String message) {
        AlertHelper.showAlert(Alert.AlertType.WARNING, nameField.getScene().getWindow(), "Eksik Bilgi", message);
    }

    private void error(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, nameField.getScene().getWindow(), "Hata", message);
    }

    private void close() { ((Stage) nameField.getScene().getWindow()).close(); }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String conditionLabel(String condition) {
        if (condition == null) return "—";
        return switch (condition) {
            case "ACTIVE" -> "Kullanımda";
            case "MAINTENANCE" -> "Bakımda";
            case "BROKEN" -> "Arızalı";
            case "RETIRED" -> "Kullanım Dışı";
            default -> condition;
        };
    }
}
