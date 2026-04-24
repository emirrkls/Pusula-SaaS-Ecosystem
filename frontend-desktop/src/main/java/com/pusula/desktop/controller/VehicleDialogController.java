package com.pusula.desktop.controller;

import com.pusula.desktop.api.VehicleApi;
import com.pusula.desktop.dto.VehicleDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.Locale;
import java.util.ResourceBundle;

public class VehicleDialogController {

    @FXML
    private Label titleLabel;

    @FXML
    private TextField plateField;

    @FXML
    private TextField driverField;

    @FXML
    private CheckBox activeCheckBox;

    private VehicleDTO currentVehicle;
    private Runnable onSaveSuccess;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
    }

    public void setVehicle(VehicleDTO vehicle) {
        this.currentVehicle = vehicle;

        if (vehicle != null) {
            titleLabel.setText(bundle.getString("vehicle.edit"));
            plateField.setText(vehicle.getLicensePlate());
            driverField.setText(vehicle.getDriverName());
            activeCheckBox.setSelected(vehicle.getIsActive() != null ? vehicle.getIsActive() : true);
        } else {
            titleLabel.setText(bundle.getString("vehicle.add"));
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
        String licensePlate = plateField.getText().trim();
        String driverName = driverField.getText().trim();

        if (licensePlate.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, plateField.getScene().getWindow(),
                    bundle.getString("error.title"), bundle.getString("vehicle.error.plate_required"));
            return;
        }

        VehicleDTO vehicleToSave = currentVehicle != null ? currentVehicle : new VehicleDTO();
        vehicleToSave.setLicensePlate(licensePlate);
        vehicleToSave.setDriverName(driverName.isEmpty() ? null : driverName);
        vehicleToSave.setIsActive(activeCheckBox.isSelected());

        VehicleApi api = RetrofitClient.getClient().create(VehicleApi.class);

        Call<VehicleDTO> call;
        if (vehicleToSave.getId() != null) {
            call = api.update(vehicleToSave.getId(), vehicleToSave);
        } else {
            call = api.create(vehicleToSave); // Using company ID 1
        }

        call.enqueue(new Callback<VehicleDTO>() {
            @Override
            public void onResponse(Call<VehicleDTO> callResponse, Response<VehicleDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        // Show success notification
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                                plateField.getScene().getWindow(),
                                "Başarılı",
                                bundle.getString("vehicle.save.success"));

                        if (onSaveSuccess != null) {
                            onSaveSuccess.run();
                        }
                        closeDialog();
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, plateField.getScene().getWindow(),
                                bundle.getString("error.title"),
                                bundle.getString("error.save_failed") + ": " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<VehicleDTO> callResponse, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, plateField.getScene().getWindow(),
                            bundle.getString("error.network"), t.getMessage());
                });
            }
        });
    }

    private void closeDialog() {
        Stage stage = (Stage) plateField.getScene().getWindow();
        stage.close();
    }
}
