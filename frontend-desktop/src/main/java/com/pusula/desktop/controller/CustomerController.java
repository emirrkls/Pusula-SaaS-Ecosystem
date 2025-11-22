package com.pusula.desktop.controller;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.List;

public class CustomerController {

    @FXML
    private TableView<CustomerDTO> customersTable;

    @FXML
    private TableColumn<CustomerDTO, String> colName;

    @FXML
    private TableColumn<CustomerDTO, String> colPhone;

    @FXML
    private TableColumn<CustomerDTO, String> colAddress;

    @FXML
    private TableColumn<CustomerDTO, String> colCoordinates;

    private final ObservableList<CustomerDTO> customerList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colCoordinates.setCellValueFactory(new PropertyValueFactory<>("coordinates"));

        customersTable.setItems(customerList);

        loadCustomers();
    }

    @FXML
    private void handleRefresh() {
        loadCustomers();
    }

    @FXML
    private void handleAddCustomer() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/customer_dialog.fxml"));
            javafx.scene.Parent root = loader.load();

            CustomerDialogController controller = loader.getController();
            controller.setOnSaveSuccess(this::loadCustomers);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Add New Customer");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, customersTable.getScene().getWindow(),
                    "Error", "Could not open dialog: " + e.getMessage());
        }
    }

    private void loadCustomers() {
        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);
        api.getAllCustomers().enqueue(new Callback<List<CustomerDTO>>() {
            @Override
            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        customerList.clear();
                        customerList.addAll(response.body());
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, customersTable.getScene().getWindow(),
                                "Error", "Failed to load customers: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, customersTable.getScene().getWindow(),
                            "Network Error", "Could not connect to server: " + t.getMessage());
                });
            }
        });
    }
}
