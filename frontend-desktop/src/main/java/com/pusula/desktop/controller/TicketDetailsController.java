package com.pusula.desktop.controller;

import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.ServiceUsedPartDTO;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class TicketDetailsController {

    @FXML
    private Label lblStatus;
    @FXML
    private TextArea txtDescription;
    @FXML
    private TextArea txtNotes;
    @FXML
    private ComboBox<UserDTO> comboTechnician;
    @FXML
    private TableView<ServiceUsedPartDTO> partsTable;
    @FXML
    private TableColumn<ServiceUsedPartDTO, String> colPartName;
    @FXML
    private TableColumn<ServiceUsedPartDTO, Integer> colQuantity;
    @FXML
    private TableColumn<ServiceUsedPartDTO, BigDecimal> colPrice;

    private ServiceTicketDTO currentTicket;
    private final ObservableList<ServiceUsedPartDTO> usedPartsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colPartName.setCellValueFactory(new PropertyValueFactory<>("partName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantityUsed"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("sellingPriceSnapshot"));

        partsTable.setItems(usedPartsList);

        loadTechnicians();
    }

    public void setTicket(ServiceTicketDTO ticket) {
        this.currentTicket = ticket;
        updateUI();
        loadUsedParts();
    }

    private void updateUI() {
        if (currentTicket == null)
            return;
        lblStatus.setText(currentTicket.getStatus());
        txtDescription.setText(currentTicket.getDescription());
        txtNotes.setText(currentTicket.getNotes());
    }

    private void loadTechnicians() {
        UserApi api = RetrofitClient.getClient().create(UserApi.class);
        api.getUsers("TECHNICIAN").enqueue(new Callback<List<UserDTO>>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        ObservableList<UserDTO> techs = FXCollections.observableArrayList(response.body());
                        comboTechnician.setItems(techs);

                        if (currentTicket != null && currentTicket.getAssignedTechnicianId() != null) {
                            for (UserDTO u : techs) {
                                if (u.getId().equals(currentTicket.getAssignedTechnicianId())) {
                                    comboTechnician.setValue(u);
                                    break;
                                }
                            }
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                        "Error", "Failed to load technicians"));
            }
        });
    }

    private void loadUsedParts() {
        if (currentTicket == null)
            return;
        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.getUsedParts(currentTicket.getId()).enqueue(new Callback<List<ServiceUsedPartDTO>>() {
            @Override
            public void onResponse(Call<List<ServiceUsedPartDTO>> call, Response<List<ServiceUsedPartDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        usedPartsList.clear();
                        usedPartsList.addAll(response.body());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<ServiceUsedPartDTO>> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                        "Error", "Failed to load parts"));
            }
        });
    }

    @FXML
    private void handleSaveNotes() {
        if (currentTicket == null)
            return;
        currentTicket.setNotes(txtNotes.getText());

        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.updateTicket(currentTicket.getId(), currentTicket).enqueue(new Callback<ServiceTicketDTO>() {
            @Override
            public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                            lblStatus.getScene().getWindow(), "Success", "Notes updated."));
                }
            }

            @Override
            public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                        "Error", t.getMessage()));
            }
        });
    }

    @FXML
    private void handleAssign() {
        UserDTO selectedTech = comboTechnician.getValue();
        if (selectedTech == null || currentTicket == null)
            return;

        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.assignTechnician(currentTicket.getId(), selectedTech.getId()).enqueue(new Callback<ServiceTicketDTO>() {
            @Override
            public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        currentTicket = response.body();
                        updateUI();
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(), "Success",
                                "Technician assigned.");
                    });
                }
            }

            @Override
            public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                        "Error", t.getMessage()));
            }
        });
    }

    @FXML
    private void handleAddPart() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Part");
        dialog.setHeaderText("Enter Inventory ID and Quantity");
        dialog.setContentText("Format: UUID,Quantity");

        dialog.showAndWait().ifPresent(result -> {
            try {
                String[] parts = result.split(",");
                if (parts.length != 2)
                    throw new IllegalArgumentException("Invalid format");

                UUID inventoryId = UUID.fromString(parts[0].trim());
                int quantity = Integer.parseInt(parts[1].trim());

                ServiceUsedPartDTO dto = ServiceUsedPartDTO.builder()
                        .inventoryId(inventoryId)
                        .quantityUsed(quantity)
                        .build();

                ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
                api.addUsedPart(currentTicket.getId(), dto).enqueue(new Callback<ServiceUsedPartDTO>() {
                    @Override
                    public void onResponse(Call<ServiceUsedPartDTO> call, Response<ServiceUsedPartDTO> response) {
                        if (response.isSuccessful()) {
                            Platform.runLater(() -> {
                                loadUsedParts();
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                        "Success", "Part added.");
                            });
                        } else {
                            Platform.runLater(
                                    () -> AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                                            "Error", "Failed to add part: " + response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call<ServiceUsedPartDTO> call, Throwable t) {
                        Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                lblStatus.getScene().getWindow(), "Error", t.getMessage()));
                    }
                });

            } catch (Exception e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(), "Error",
                        "Invalid input: " + e.getMessage());
            }
        });
    }
}
