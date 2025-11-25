package com.pusula.desktop.controller;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class TicketDialogController {

    @FXML
    private ComboBox<CustomerDTO> customerComboBox;

    @FXML
    private TextField descriptionField;

    @FXML
    private DatePicker datePicker;

    @FXML
    private TextArea notesArea;

    private Runnable onSaveSuccess;

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    @FXML
    public void initialize() {
        configureCustomerComboBox();
        loadCustomers();
    }

    private void configureCustomerComboBox() {
        customerComboBox.setConverter(new StringConverter<CustomerDTO>() {
            @Override
            public String toString(CustomerDTO customer) {
                return customer == null ? "" : customer.getName();
            }

            @Override
            public CustomerDTO fromString(String string) {
                return null; // No need to convert from string to object for read-only combo
            }
        });
    }

    private void loadCustomers() {
        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);
        api.getAllCustomers().enqueue(new Callback<List<CustomerDTO>>() {
            @Override
            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        customerComboBox.setItems(FXCollections.observableArrayList(response.body()));
                    });
                }
            }

            @Override
            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {
                // Silent failure or log
                t.printStackTrace();
            }
        });
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    @FXML
    private void handleSave() {
        CustomerDTO selectedCustomer = customerComboBox.getValue();
        String description = descriptionField.getText();

        if (selectedCustomer == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, descriptionField.getScene().getWindow(),
                    "Validation Error", "Please select a customer.");
            return;
        }

        if (description.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, descriptionField.getScene().getWindow(),
                    "Validation Error", "Description is required.");
            return;
        }

        ServiceTicketDTO newTicket = new ServiceTicketDTO();
        newTicket.setCustomerId(selectedCustomer.getId());
        newTicket.setDescription(description);
        newTicket.setNotes(notesArea.getText());
        newTicket.setStatus("PENDING");

        if (datePicker.getValue() != null) {
            newTicket.setScheduledDate(LocalDateTime.of(datePicker.getValue(), LocalTime.of(9, 0))); // Default to 9 AM
        }

        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.createTicket(newTicket).enqueue(new Callback<ServiceTicketDTO>() {
            @Override
            public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        if (onSaveSuccess != null) {
                            onSaveSuccess.run();
                        }
                        closeDialog();
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, descriptionField.getScene().getWindow(),
                                "Error", "Failed to create ticket: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, descriptionField.getScene().getWindow(),
                            "Network Error", "Could not connect to server: " + t.getMessage());
                });
            }
        });
    }

    private void closeDialog() {
        Stage stage = (Stage) descriptionField.getScene().getWindow();
        stage.close();
    }
}
