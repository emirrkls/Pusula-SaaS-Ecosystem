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
import javafx.scene.control.TextField;
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

    @FXML
    private TextField searchField;

    private final ObservableList<CustomerDTO> customerList = FXCollections.observableArrayList();
    private javafx.collections.transformation.FilteredList<CustomerDTO> filteredList;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colCoordinates.setCellValueFactory(new PropertyValueFactory<>("coordinates"));

        // Setup search/filter logic
        filteredList = new javafx.collections.transformation.FilteredList<>(customerList, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(customer -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();

                // Search by name
                if (customer.getName() != null && customer.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }

                // Search by phone number
                if (customer.getPhone() != null && customer.getPhone().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }

                return false;
            });
        });

        customersTable.setItems(filteredList);

        customersTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<CustomerDTO> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    CustomerDTO rowData = row.getItem();
                    handleEditCustomer(rowData);
                }
            });
            return row;
        });

        loadCustomers();
    }

    private void handleEditCustomer(CustomerDTO customer) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/customer_detail.fxml"));
            javafx.scene.Parent root = loader.load();

            CustomerDetailController controller = loader.getController();
            controller.setCustomer(customer);
            controller.setOnSaveSuccess(this::loadCustomers);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Customer Details");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, customersTable.getScene().getWindow(),
                    "Error", "Could not open details: " + e.getMessage());
        }
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
