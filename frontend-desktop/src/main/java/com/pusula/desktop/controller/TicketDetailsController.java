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
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.dto.AuthRequest;
import com.pusula.desktop.dto.AuthResponse;
import com.pusula.desktop.api.AuthApi;
import com.pusula.desktop.util.UTF8Control;
import java.util.Locale;

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
    @FXML
    private Button btnCompleteService;
    @FXML
    private Button btnCancelService;
    @FXML
    private Button btnAddPart;
    @FXML
    private Button btnEditCompleted;
    @FXML
    private Label lblParentLink;
    @FXML
    private Button btnCreateRecall;
    private java.util.ResourceBundle resourceBundle;

    private ServiceTicketDTO currentTicket;
    private final ObservableList<ServiceUsedPartDTO> usedPartsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Load resource bundle for localization
        resourceBundle = java.util.ResourceBundle.getBundle("i18n.messages",
                Locale.of("tr", "TR"), new UTF8Control());
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

        // Translate and display status
        lblStatus.setText(getStatusTranslation(currentTicket.getStatus()));
        txtDescription.setText(currentTicket.getDescription());
        txtNotes.setText(currentTicket.getNotes());
        // Check if ticket is completed
        boolean isCompleted = "COMPLETED".equals(currentTicket.getStatus());
        boolean isCancelled = "CANCELLED".equals(currentTicket.getStatus());
        boolean isClosed = isCompleted || isCancelled;
        // Show recall button only for COMPLETED tickets
        btnCreateRecall.setVisible(isCompleted);
        btnCreateRecall.setManaged(isCompleted);
        // Show edit completed button only for COMPLETED tickets
        btnEditCompleted.setVisible(isCompleted);
        btnEditCompleted.setManaged(isCompleted);
        // Disable action buttons for closed tickets
        btnCompleteService.setVisible(!isClosed);
        btnCompleteService.setManaged(!isClosed);
        btnCancelService.setVisible(!isClosed);
        btnCancelService.setManaged(!isClosed);
        btnAddPart.setDisable(isClosed);

        // Disable editing for closed tickets
        txtNotes.setEditable(!isClosed);
        comboTechnician.setDisable(isClosed);
        // Show parent link if this is a follow-up ticket
        if (currentTicket.getParentTicketId() != null) {
            lblParentLink.setText("Bu servis #" + currentTicket.getParentTicketId() + " nolu fişin devamıdır");
            lblParentLink.setVisible(true);
            lblParentLink.setManaged(true);
            lblParentLink.setOnMouseClicked(e -> openParentTicket(currentTicket.getParentTicketId()));
        } else {
            lblParentLink.setVisible(false);
            lblParentLink.setManaged(false);
        }
    }

    private String getStatusTranslation(String status) {
        if (status == null)
            return "";
        switch (status) {
            case "PENDING":
                return resourceBundle.getString("status.pending");
            case "IN_PROGRESS":
                return resourceBundle.getString("status.in_progress");
            case "COMPLETED":
                return resourceBundle.getString("status.completed");
            case "CANCELLED":
                return resourceBundle.getString("status.cancelled");
            case "ASSIGNED":
                return resourceBundle.getString("status.assigned");
            default:
                return status;
        }
    }

    private void loadTechnicians() {
        UserApi api = RetrofitClient.getClient().create(UserApi.class);
        api.getTechnicians().enqueue(new Callback<List<UserDTO>>() {
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
        try {
            // Load the part selection dialog
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/part_selection_dialog.fxml"), resourceBundle);
            javafx.scene.Parent root = loader.load();
            PartSelectionDialogController controller = loader.getController();

            // Create a new stage for the dialog
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle(resourceBundle.getString("part.selection.title"));
            dialogStage.setScene(new javafx.scene.Scene(root));
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(lblStatus.getScene().getWindow());

            // Set callback for when part is selected
            controller.setOnPartSelected(() -> {
                InventoryDTO selectedPart = controller.getSelectedPart();
                Integer quantity = controller.getSelectedQuantity();

                if (selectedPart != null && quantity != null) {
                    addPartToTicket(selectedPart.getId(), quantity);
                }
            });

            dialogStage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                    resourceBundle.getString("dialog.title.error"),
                    "Failed to open part selection: " + e.getMessage());
        }
    }

    private void addPartToTicket(Long inventoryId, int quantity) {
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
                                resourceBundle.getString("dialog.title.success"),
                                resourceBundle.getString("msg.save.success"));
                    });
                } else {
                    Platform.runLater(
                            () -> AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                                    resourceBundle.getString("dialog.title.error"),
                                    "Failed to add part: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<ServiceUsedPartDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                        lblStatus.getScene().getWindow(),
                        resourceBundle.getString("dialog.title.error"),
                        t.getMessage()));
            }
        });
    }

    @FXML
    private void handleEditCompleted() {
        if (currentTicket == null || !"COMPLETED".equals(currentTicket.getStatus()))
            return;
        // Show password dialog
        TextInputDialog passwordDialog = new TextInputDialog();
        passwordDialog.setTitle(resourceBundle.getString("dialog.admin.title"));
        passwordDialog.setHeaderText(resourceBundle.getString("dialog.admin.header"));
        passwordDialog.setContentText(resourceBundle.getString("dialog.admin.content"));

        // Make it a password field
        passwordDialog.getEditor().setPromptText("••••••••");

        passwordDialog.showAndWait().ifPresent(password -> {
            // Verify password against admin user
            verifyAdminPassword(password);
        });
    }

    private void verifyAdminPassword(String password) {
        // Attempt to authenticate with admin credentials
        AuthRequest authRequest = new AuthRequest("admin", password);

        AuthApi authApi = RetrofitClient.getClient().create(AuthApi.class);
        authApi.authenticate(authRequest).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        // Password is correct, enable editing
                        enableEditingForCompletedTicket();
                    } else {
                        // Password is incorrect
                        AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                                resourceBundle.getString("dialog.title.error"),
                                resourceBundle.getString("dialog.admin.error"));
                    }
                });
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                            resourceBundle.getString("dialog.title.error"),
                            resourceBundle.getString("dialog.admin.error"));
                });
            }
        });
    }

    private void enableEditingForCompletedTicket() {
        // Re-enable editing fields
        txtNotes.setEditable(true);
        comboTechnician.setDisable(false);
        btnAddPart.setDisable(false);

        // Hide the edit button since we're now in edit mode
        btnEditCompleted.setVisible(false);
        btnEditCompleted.setManaged(false);

        // Show a save button or update existing button
        AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                resourceBundle.getString("dialog.title.info"),
                "Düzenleme modu etkinleştirildi. Değişiklikleri kaydetmek için 'Notları Kaydet' butonunu kullanın.");
    }

    private void openParentTicket(Long parentId) {
        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.getAllTickets().enqueue(new Callback<List<ServiceTicketDTO>>() {
            @Override
            public void onResponse(Call<List<ServiceTicketDTO>> call, Response<List<ServiceTicketDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ServiceTicketDTO parent = response.body().stream()
                            .filter(t -> t.getId().equals(parentId))
                            .findFirst()
                            .orElse(null);
                    if (parent != null) {
                        Platform.runLater(() -> setTicket(parent));
                    }
                }
            }

            @Override
            public void onFailure(Call<List<ServiceTicketDTO>> call, Throwable t) {
                // Silently fail or log
            }
        });
    }

    @FXML
    private void handleCreateRecall() {
        if (currentTicket == null)
            return;

        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.createFollowUp(currentTicket.getId()).enqueue(new Callback<ServiceTicketDTO>() {
            @Override
            public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                resourceBundle.getString("dialog.title.success"),
                                "Yeni servis fişi oluşturuldu: #" + response.body().getId());
                        // Refresh to show the new ticket created
                    });
                }
            }

            @Override
            public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                            resourceBundle.getString("dialog.title.error"),
                            "Yeni servis oluşturulamadı: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleCancelService() {
        if (currentTicket == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Service");
        confirm.setHeaderText("Are you sure you want to cancel this service?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
                api.cancelService(currentTicket.getId()).enqueue(new Callback<ServiceTicketDTO>() {
                    @Override
                    public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Platform.runLater(() -> {
                                currentTicket = response.body();
                                updateUI();
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                        "Success", "Service cancelled.");
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                        Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                lblStatus.getScene().getWindow(), "Error", t.getMessage()));
                    }
                });
            }
        });
    }

    @FXML
    private void handleCompleteService() {
        if (currentTicket == null)
            return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Complete Service");
        dialog.setHeaderText("Enter service charge amount");
        dialog.setContentText("Amount:");

        dialog.showAndWait().ifPresent(result -> {
            try {
                BigDecimal amount = new BigDecimal(result.trim());

                ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
                api.completeService(currentTicket.getId(), amount).enqueue(new Callback<ServiceTicketDTO>() {
                    @Override
                    public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Platform.runLater(() -> {
                                currentTicket = response.body();
                                updateUI();
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                        "Success", "Service completed.");
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                        Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                lblStatus.getScene().getWindow(), "Error", t.getMessage()));
                    }
                });
            } catch (Exception e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                        "Error", "Invalid amount: " + e.getMessage());
            }
        });
    }
}
