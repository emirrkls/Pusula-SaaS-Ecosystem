package com.pusula.desktop.controller;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.dto.CustomerDTO;
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

public class CustomerDialogController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField addressField;

    @FXML
    private TextField coordinatesField;

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
        String name = nameField.getText();
        String phone = phoneField.getText();
        String address = addressField.getText();
        String coordinates = coordinatesField.getText();

        if (name.isEmpty() || phone.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, nameField.getScene().getWindow(),
                    "Validation Error", "Name and Phone are required.");
            return;
        }

        CustomerDTO newCustomer = new CustomerDTO();
        newCustomer.setName(name);
        newCustomer.setPhone(phone);
        newCustomer.setAddress(address);
        newCustomer.setCoordinates(coordinates);

        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);
        api.createCustomer(newCustomer).enqueue(new Callback<CustomerDTO>() {
            @Override
            public void onResponse(Call<CustomerDTO> call, Response<CustomerDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        if (onSaveSuccess != null) {
                            onSaveSuccess.run();
                        }
                        closeDialog();
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, nameField.getScene().getWindow(),
                                "Error", "Failed to create customer: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<CustomerDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, nameField.getScene().getWindow(),
                            "Network Error", "Could not connect to server: " + t.getMessage());
                });
            }
        });
    }

    private void closeDialog() {
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }
}
