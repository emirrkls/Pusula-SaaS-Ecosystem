package com.pusula.desktop.controller;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class CustomerDetailController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField addressField;

    @FXML
    private TableView<ServiceTicketDTO> historyTable;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colDate;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colIssue;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colStatus;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colTechnician;

    private CustomerDTO currentCustomer;
    private Runnable onSaveSuccess;
    private java.util.ResourceBundle resourceBundle;
    private java.util.Map<Long, String> technicianMap = new java.util.HashMap<>();

    @FXML
    public void initialize() {
        // Load resource bundle
        resourceBundle = java.util.ResourceBundle.getBundle("i18n.messages",
                java.util.Locale.of("tr", "TR"), new com.pusula.desktop.util.UTF8Control());
        setupTable();
        loadTechnicians();
    }

    private void setupTable() {
        colDate.setCellValueFactory(cellData -> {
            var date = cellData.getValue().getCreatedAt();
            return new SimpleStringProperty(
                    date != null ? date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "-");
        });
        colIssue.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDescription()));

        // Status column - set value factory first, then cell factory for styling
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        colStatus.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    // Translate status
                    String translatedStatus = switch (status) {
                        case "PENDING" -> resourceBundle.getString("status.pending");
                        case "IN_PROGRESS" -> resourceBundle.getString("status.in_progress");
                        case "COMPLETED" -> resourceBundle.getString("status.completed");
                        case "CANCELLED" -> resourceBundle.getString("status.cancelled");
                        case "ASSIGNED" -> resourceBundle.getString("status.assigned");
                        default -> status;
                    };

                    // Apply colors
                    String color = switch (status) {
                        case "COMPLETED" -> "-fx-text-fill: #2ecc71; -fx-font-weight: bold;";
                        case "IN_PROGRESS", "ASSIGNED" -> "-fx-text-fill: #3498db; -fx-font-weight: bold;";
                        case "CANCELLED" -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                        case "PENDING" -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                        default -> "";
                    };

                    setText(translatedStatus);
                    setStyle(color);
                }
            }
        });

        colTechnician.setCellValueFactory(cellData -> {
            Long techId = cellData.getValue().getAssignedTechnicianId();
            if (techId == null) {
                return new SimpleStringProperty("Atanmamış");
            }
            String techName = technicianMap.get(techId);
            return new SimpleStringProperty(techName != null ? techName : "Atanmamış");
        });
    }

    private void loadTechnicians() {
        UserApi api = RetrofitClient.getClient().create(UserApi.class);
        api.getTechnicians().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (UserDTO user : response.body()) {
                        technicianMap.put(user.getId(), user.getFullName());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
                System.err.println("Failed to load technicians: " + t.getMessage());
            }
        });
    }

    public void setCustomer(CustomerDTO customer) {
        this.currentCustomer = customer;
        if (customer != null) {
            nameField.setText(customer.getName());
            phoneField.setText(customer.getPhone());
            addressField.setText(customer.getAddress());
            loadServiceHistory(customer.getId());
        }
    }

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    private void loadServiceHistory(Long customerId) {
        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.getAllTickets().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<ServiceTicketDTO>> call, Response<List<ServiceTicketDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ServiceTicketDTO> history = response.body().stream()
                            .filter(t -> t.getCustomerId().equals(customerId))
                            .toList();
                    Platform.runLater(() -> historyTable.setItems(FXCollections.observableArrayList(history)));
                }
            }

            @Override
            public void onFailure(Call<List<ServiceTicketDTO>> call, Throwable t) {
                System.err.println("Failed to load history: " + t.getMessage());
            }
        });
    }

    @FXML
    private void handleSave() {
        if (currentCustomer == null)
            return;

        currentCustomer.setName(nameField.getText());
        currentCustomer.setPhone(phoneField.getText());
        currentCustomer.setAddress(addressField.getText());

        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);
        api.updateCustomer(currentCustomer.getId(), currentCustomer).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<CustomerDTO> call, Response<CustomerDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        if (onSaveSuccess != null)
                            onSaveSuccess.run();
                        closeDialog();
                    });
                } else {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            nameField.getScene().getWindow(), "Error", "Failed to save"));
                }
            }

            @Override
            public void onFailure(Call<CustomerDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, nameField.getScene().getWindow(),
                        "Error", "Network error"));
            }
        });
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }
}
