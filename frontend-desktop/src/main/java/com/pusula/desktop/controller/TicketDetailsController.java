package com.pusula.desktop.controller;

import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.api.ReportApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.ServiceUsedPartDTO;
import com.pusula.desktop.dto.ServiceTicketExpenseDTO;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.api.ServiceTicketExpenseApi;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.WhatsAppHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import okhttp3.ResponseBody;
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
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;

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
    @FXML
    private Button btnPrintPdf;
    @FXML
    private Label lblCustomerName;
    @FXML
    private Label lblCustomerPhone;
    @FXML
    private Label lblCustomerAddress;

    private java.util.ResourceBundle resourceBundle;

    private ServiceTicketDTO currentTicket;
    private final ObservableList<ServiceUsedPartDTO> usedPartsList = FXCollections.observableArrayList();

    @FXML
    private VBox timelineContainer;

    @FXML
    private Button btnChangeStatus;

    private Long currentUserId;

    // External Expenses UI
    @FXML
    private TableView<ServiceTicketExpenseDTO> expensesTable;
    @FXML
    private TableColumn<ServiceTicketExpenseDTO, String> colExpenseDescription;
    @FXML
    private TableColumn<ServiceTicketExpenseDTO, String> colExpenseSupplier;
    @FXML
    private TableColumn<ServiceTicketExpenseDTO, BigDecimal> colExpenseAmount;
    @FXML
    private TableColumn<ServiceTicketExpenseDTO, Void> colExpenseActions;
    @FXML
    private Button btnAddExpense;

    private final ObservableList<ServiceTicketExpenseDTO> expensesList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Load resource bundle for localization
        resourceBundle = java.util.ResourceBundle.getBundle("i18n.messages",
                Locale.of("tr", "TR"), new UTF8Control());

        // Use explicit cell value factories with debug
        colPartName.setCellValueFactory(cellData -> {
            String name = cellData.getValue().getPartName();
            System.out.println("CellValue partName: " + name);
            return new javafx.beans.property.SimpleStringProperty(name != null ? name : "-");
        });
        colQuantity.setCellValueFactory(cellData -> {
            Integer qty = cellData.getValue().getQuantityUsed();
            System.out.println("CellValue quantity: " + qty);
            return new javafx.beans.property.SimpleObjectProperty<>(qty);
        });
        colPrice.setCellValueFactory(cellData -> {
            java.math.BigDecimal price = cellData.getValue().getSellingPriceSnapshot();
            System.out.println("CellValue price: " + price);
            return new javafx.beans.property.SimpleObjectProperty<>(price);
        });

        partsTable.setItems(usedPartsList);

        // Setup external expenses table
        colExpenseDescription.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDescription()));
        colExpenseSupplier.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getSupplier() != null ? cellData.getValue().getSupplier() : "-"));
        colExpenseAmount.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getAmount()));
        // Action column with delete button
        colExpenseActions.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("Sil");
            {
                deleteBtn.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 2 8;");
                deleteBtn.setOnAction(e -> {
                    ServiceTicketExpenseDTO expense = getTableView().getItems().get(getIndex());
                    deleteExpense(expense);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });
        expensesTable.setItems(expensesList);

        loadTechnicians();

        // Only fetch all users if Admin, to avoid 403 Forbidden for Technicians
        if (com.pusula.desktop.util.SessionManager.isAdmin()) {
            fetchCurrentUser();
        }
    }

    private void fetchCurrentUser() {
        String username = com.pusula.desktop.util.SessionManager.getUsername();
        if (username == null)
            return;

        UserApi api = RetrofitClient.getClient().create(UserApi.class);
        api.getAllUsers().enqueue(new Callback<List<UserDTO>>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (UserDTO user : response.body()) {
                        if (user.getUsername().equals(username)) {
                            currentUserId = user.getId();
                            Platform.runLater(() -> updateUI()); // Refresh UI with user ID
                            break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
                // Log error
            }
        });
    }

    public void setTicket(ServiceTicketDTO ticket) {
        this.currentTicket = ticket;
        updateUI();
        loadUsedParts();
        loadTicketHistory();
        loadCustomerInfo();
        loadExpenses();
    }

    private void loadTicketHistory() {
        if (currentTicket == null)
            return;

        com.pusula.desktop.api.AuditLogApi api = RetrofitClient.getClient()
                .create(com.pusula.desktop.api.AuditLogApi.class);
        api.getTicketTimeline(currentTicket.getId()).enqueue(new Callback<List<com.pusula.desktop.dto.AuditLogDTO>>() {
            @Override
            public void onResponse(Call<List<com.pusula.desktop.dto.AuditLogDTO>> call,
                    Response<List<com.pusula.desktop.dto.AuditLogDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> displayTimeline(response.body()));
                } else if (response.code() == 403) {
                    // Technicians may not have access to audit logs, gracefully hide timeline
                    Platform.runLater(() -> {
                        if (timelineContainer != null) {
                            Label noAccessLabel = new Label("Geçmiş görüntüleme yetkiniz bulunmamaktadır.");
                            noAccessLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #95a5a6;");
                            timelineContainer.getChildren().add(noAccessLabel);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<com.pusula.desktop.dto.AuditLogDTO>> call, Throwable t) {
                // Silently fail for timeline - don't block ticket details from opening
                Platform.runLater(() -> {
                    if (timelineContainer != null) {
                        Label errorLabel = new Label("Geçmiş yüklenemedi.");
                        errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #95a5a6;");
                        timelineContainer.getChildren().add(errorLabel);
                    }
                });
            }
        });
    }

    private void displayTimeline(List<com.pusula.desktop.dto.AuditLogDTO> logs) {
        if (timelineContainer == null)
            return;
        timelineContainer.getChildren().clear();

        if (logs.isEmpty()) {
            Label emptyLabel = new Label(resourceBundle.getString("timeline.no_events"));
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d;");
            timelineContainer.getChildren().add(emptyLabel);
            return;
        }

        for (com.pusula.desktop.dto.AuditLogDTO log : logs) {
            timelineContainer.getChildren().add(createTimelineEntry(log));
        }
    }

    private javafx.scene.Node createTimelineEntry(com.pusula.desktop.dto.AuditLogDTO log) {
        VBox entry = new VBox(5);
        entry.setStyle(
                "-fx-background-color: white; -fx-padding: 15; -fx-border-color: #e0e0e0; -fx-border-radius: 5; -fx-background-radius: 5;");

        // Header: timestamp and user
        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label timeLabel = new Label(formatTimestamp(log.getTimestamp()));
        timeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label userLabel = new Label("👤 " + (log.getUserName() != null ? log.getUserName() : "Sistem"));
        userLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        header.getChildren().addAll(timeLabel, userLabel);

        // Description
        Label descLabel = new Label(translateAction(log.getActionType()) + ": " + log.getDescription());
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 13px;");

        entry.getChildren().addAll(header, descLabel);

        // Old/New values if present
        if (log.getOldValue() != null || log.getNewValue() != null) {
            HBox values = new HBox(20);
            values.setStyle("-fx-padding: 10 0 0 0;");

            if (log.getOldValue() != null && !log.getOldValue().isEmpty()) {
                VBox oldBox = new VBox(3);
                Label oldLabel = new Label("Öncesi:");
                oldLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6;");
                Label oldValueLabel = new Label(log.getOldValue());
                oldValueLabel.setWrapText(true);
                oldValueLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e74c3c;");
                oldBox.getChildren().addAll(oldLabel, oldValueLabel);
                values.getChildren().add(oldBox);
            }

            if (log.getNewValue() != null && !log.getNewValue().isEmpty()) {
                VBox newBox = new VBox(3);
                Label newLabel = new Label("Sonrası:");
                newLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #95a5a6;");
                Label newValueLabel = new Label(log.getNewValue());
                newValueLabel.setWrapText(true);
                newValueLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60;");
                newBox.getChildren().addAll(newLabel, newValueLabel);
                values.getChildren().add(newBox);
            }

            if (!values.getChildren().isEmpty()) {
                entry.getChildren().add(values);
            }
        }

        return entry;
    }

    private String formatTimestamp(java.time.LocalDateTime timestamp) {
        if (timestamp == null)
            return "";
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm",
                new java.util.Locale("tr"));
        return timestamp.format(formatter);
    }

    private String translateAction(String action) {
        if (action == null)
            return "";
        switch (action) {
            case "CREATE":
                return "Oluşturuldu";
            case "UPDATE":
                return "Güncellendi";
            case "DELETE":
                return "Silindi";
            case "ASSIGN":
                return "Atandı";
            case "COMPLETE":
                return "Tamamlandı";
            case "CANCEL":
                return "İptal Edildi";
            case "ADD_PART":
                return "Parça Eklendi";
            default:
                return action;
        }
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

        // Disable assignment controls for Technicians
        if (com.pusula.desktop.util.SessionManager.isTechnician()) {
            comboTechnician.setDisable(true);
        }

        // Status Change Button Logic
        boolean canChangeStatus = false;
        if (com.pusula.desktop.util.SessionManager.isAdmin()) {
            canChangeStatus = true;
        } else if (com.pusula.desktop.util.SessionManager.isTechnician()) {
            // Check if assigned to current user
            if (currentUserId != null && currentTicket.getAssignedTechnicianId() != null
                    && currentUserId.equals(currentTicket.getAssignedTechnicianId())) {
                canChangeStatus = true;
            }
        }
        btnChangeStatus.setVisible(canChangeStatus);
        btnChangeStatus.setManaged(canChangeStatus);

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

    @FXML
    private void handleChangeStatus() {
        if (currentTicket == null)
            return;

        // Create a map for display text -> backend status
        java.util.Map<String, String> statusMap = new java.util.LinkedHashMap<>();
        statusMap.put(getStatusTranslation("PENDING"), "PENDING");
        statusMap.put(getStatusTranslation("IN_PROGRESS"), "IN_PROGRESS");
        statusMap.put(getStatusTranslation("ASSIGNED"), "ASSIGNED");
        statusMap.put(getStatusTranslation("COMPLETED"), "COMPLETED");
        statusMap.put(getStatusTranslation("CANCELLED"), "CANCELLED");

        List<String> choices = new java.util.ArrayList<>(statusMap.keySet());
        String currentStatusDisplay = getStatusTranslation(currentTicket.getStatus());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(currentStatusDisplay, choices);
        dialog.setTitle(resourceBundle.getString("ticket.status.dialog.title"));
        dialog.setHeaderText(resourceBundle.getString("ticket.status.dialog.header"));
        dialog.setContentText(resourceBundle.getString("ticket.status.dialog.content"));

        dialog.showAndWait().ifPresent(newStatusDisplay -> {
            String newStatusCode = statusMap.get(newStatusDisplay);
            if (newStatusCode != null && !newStatusCode.equals(currentTicket.getStatus())) {
                currentTicket.setStatus(newStatusCode);
                ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
                api.updateTicket(currentTicket.getId(), currentTicket).enqueue(new Callback<ServiceTicketDTO>() {
                    @Override
                    public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                        if (response.isSuccessful()) {
                            Platform.runLater(() -> {
                                currentTicket = response.body();
                                updateUI();
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                        resourceBundle.getString("dialog.title.success"),
                                        resourceBundle.getString("msg.update.success"));
                            });
                        } else {
                            // Handle permission errors (403, 500) by showing error and closing window
                            Platform.runLater(() -> {
                                String errorMsg = "Durum güncellenemedi.";
                                if (response.code() == 403 || response.code() == 500) {
                                    errorMsg = "Bu servisi güncelleme yetkiniz bulunmamaktadır. Sadece size atanmış servisleri güncelleyebilirsiniz.";
                                }
                                AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                                        resourceBundle.getString("dialog.title.error"), errorMsg);
                                // Close the ticket details window and return to service list
                                lblStatus.getScene().getWindow().hide();
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                        Platform.runLater(() -> {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                                    resourceBundle.getString("dialog.title.error"),
                                    "Bağlantı hatası: " + t.getMessage());
                            lblStatus.getScene().getWindow().hide();
                        });
                    }
                });
            }
        });
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

                        // Try to find current user ID from this list if not already set (e.g. for
                        // Technicians)
                        String currentUsername = com.pusula.desktop.util.SessionManager.getUsername();
                        if (currentUserId == null && currentUsername != null) {
                            for (UserDTO u : techs) {
                                if (u.getUsername().equals(currentUsername)) {
                                    currentUserId = u.getId();
                                    updateUI(); // Refresh UI to enable buttons
                                    break;
                                }
                            }
                        }

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
        System.out.println("Loading used parts for ticket ID: " + currentTicket.getId());
        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.getUsedParts(currentTicket.getId()).enqueue(new Callback<List<ServiceUsedPartDTO>>() {
            @Override
            public void onResponse(Call<List<ServiceUsedPartDTO>> call, Response<List<ServiceUsedPartDTO>> response) {
                System.out.println("getUsedParts response code: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    System.out.println("Used parts count: " + response.body().size());
                    Platform.runLater(() -> {
                        usedPartsList.clear();
                        usedPartsList.addAll(response.body());
                        System.out.println("usedPartsList size after add: " + usedPartsList.size());
                    });
                } else {
                    System.out.println("getUsedParts unsuccessful or body is null");
                }
            }

            @Override
            public void onFailure(Call<List<ServiceUsedPartDTO>> call, Throwable t) {
                System.err.println("getUsedParts failed: " + t.getMessage());
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                        "Error", "Failed to load parts"));
            }
        });
    }

    private void loadCustomerInfo() {
        if (currentTicket == null || currentTicket.getCustomerId() == null)
            return;

        CustomerApi customerApi = RetrofitClient.getClient().create(CustomerApi.class);
        customerApi.getCustomerById(currentTicket.getCustomerId()).enqueue(new Callback<CustomerDTO>() {
            @Override
            public void onResponse(Call<CustomerDTO> call, Response<CustomerDTO> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CustomerDTO customer = response.body();
                    Platform.runLater(() -> {
                        lblCustomerName.setText(customer.getName() != null ? customer.getName() : "-");
                        lblCustomerPhone.setText(customer.getPhone() != null ? customer.getPhone() : "-");
                        lblCustomerAddress.setText(customer.getAddress() != null ? customer.getAddress() : "-");
                    });
                }
            }

            @Override
            public void onFailure(Call<CustomerDTO> call, Throwable t) {
                // Silently fail - customer info is optional
                Platform.runLater(() -> {
                    lblCustomerName.setText("-");
                    lblCustomerPhone.setText("-");
                    lblCustomerAddress.setText("-");
                });
            }
        });
    }

    @FXML
    private void handleWhatsAppClick() {
        if (lblCustomerPhone.getText() != null && !lblCustomerPhone.getText().trim().isEmpty() && !lblCustomerPhone.getText().equals("-")) {
            WhatsAppHelper.openWhatsApp(lblCustomerPhone.getText());
        } else {
            AlertHelper.showAlert(Alert.AlertType.WARNING, lblStatus.getScene().getWindow(),
                    "Uyarı", "Geçerli bir telefon numarası bulunamadı.");
        }
    }

    // ===== External Expenses Methods =====

    private void loadExpenses() {
        if (currentTicket == null)
            return;

        ServiceTicketExpenseApi api = RetrofitClient.getClient().create(ServiceTicketExpenseApi.class);
        api.getExpenses(currentTicket.getId()).enqueue(new Callback<List<ServiceTicketExpenseDTO>>() {
            @Override
            public void onResponse(Call<List<ServiceTicketExpenseDTO>> call,
                    Response<List<ServiceTicketExpenseDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        expensesList.clear();
                        expensesList.addAll(response.body());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<ServiceTicketExpenseDTO>> call, Throwable t) {
                // Silently fail - expenses are optional
            }
        });
    }

    @FXML
    private void handleAddExpense() {
        if (currentTicket == null)
            return;

        // Create a dialog for adding expense
        Dialog<ServiceTicketExpenseDTO> dialog = new Dialog<>();
        dialog.setTitle("Dış Gider Ekle");
        dialog.setHeaderText("Servis için dış gider bilgilerini girin");

        // Form fields
        TextField descField = new TextField();
        descField.setPromptText("Açıklama (örn: Kompresör, Motor)");
        TextField supplierField = new TextField();
        supplierField.setPromptText("Tedarikçi (İsteğe bağlı)");
        TextField amountField = new TextField();
        amountField.setPromptText("Tutar (₺)");
        TextArea notesField = new TextArea();
        notesField.setPromptText("Notlar (İsteğe bağlı)");
        notesField.setPrefRowCount(2);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Açıklama:"), 0, 0);
        grid.add(descField, 1, 0);
        grid.add(new Label("Tedarikçi:"), 0, 1);
        grid.add(supplierField, 1, 1);
        grid.add(new Label("Tutar (₺):"), 0, 2);
        grid.add(amountField, 1, 2);
        grid.add(new Label("Notlar:"), 0, 3);
        grid.add(notesField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                try {
                    BigDecimal amount = new BigDecimal(amountField.getText().replace(",", "."));
                    return ServiceTicketExpenseDTO.builder()
                            .serviceTicketId(currentTicket.getId())
                            .description(descField.getText())
                            .supplier(supplierField.getText().isEmpty() ? null : supplierField.getText())
                            .amount(amount)
                            .notes(notesField.getText().isEmpty() ? null : notesField.getText())
                            .build();
                } catch (NumberFormatException e) {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, dialog.getDialogPane().getScene().getWindow(),
                            "Hata", "Geçersiz tutar formatı");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(expense -> {
            if (expense != null && expense.getDescription() != null && !expense.getDescription().isEmpty()) {
                saveExpense(expense);
            }
        });
    }

    private void saveExpense(ServiceTicketExpenseDTO expense) {
        ServiceTicketExpenseApi api = RetrofitClient.getClient().create(ServiceTicketExpenseApi.class);
        api.addExpense(currentTicket.getId(), expense).enqueue(new Callback<ServiceTicketExpenseDTO>() {
            @Override
            public void onResponse(Call<ServiceTicketExpenseDTO> call, Response<ServiceTicketExpenseDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        loadExpenses();
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                "Başarılı", "Dış gider eklendi");
                    });
                } else {
                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                            lblStatus.getScene().getWindow(), "Hata", "Gider eklenemedi: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<ServiceTicketExpenseDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                        lblStatus.getScene().getWindow(), "Hata", "Bağlantı hatası: " + t.getMessage()));
            }
        });
    }

    private void deleteExpense(ServiceTicketExpenseDTO expense) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bu dış gideri silmek istediğinizden emin misiniz?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Gider Silme Onayı");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                ServiceTicketExpenseApi api = RetrofitClient.getClient().create(ServiceTicketExpenseApi.class);
                api.deleteExpense(currentTicket.getId(), expense.getId()).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Platform.runLater(() -> loadExpenses());
                        } else {
                            Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                    lblStatus.getScene().getWindow(), "Hata", "Silinemedi: " + response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,
                                lblStatus.getScene().getWindow(), "Hata", t.getMessage()));
                    }
                });
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
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                resourceBundle.getString("dialog.assign.title"),
                                resourceBundle.getString("dialog.assign.message"));
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
        // Create custom dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Admin Doğrulama");
        dialog.setHeaderText("Admin şifresi gerekli");
        // Create PasswordField
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Şifre");
        // Set dialog content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Şifre:"), 0, 0);
        grid.add(passwordField, 1, 0);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });
        dialog.showAndWait().ifPresent(password -> {
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

        // Create custom dialog with GridPane
        Dialog<java.util.Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle(resourceBundle.getString("dialog.complete.title"));
        dialog.setHeaderText(resourceBundle.getString("dialog.complete.header"));

        // Set buttons
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        javafx.scene.control.TextField amountField = new javafx.scene.control.TextField();
        amountField.setPromptText("0.00");

        javafx.scene.control.ComboBox<String> paymentCombo = new javafx.scene.control.ComboBox<>();
        paymentCombo.getItems().addAll(
                resourceBundle.getString("payment.cash"), // Nakit
                resourceBundle.getString("payment.credit_card"), // Kredi Kartı
                resourceBundle.getString("payment.current_account") // Cari Hesap (Veresiye)
        );
        paymentCombo.setValue(resourceBundle.getString("payment.cash")); // Default to cash

        grid.add(new Label(resourceBundle.getString("dialog.complete.amount") + ":"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label(resourceBundle.getString("payment.method") + ":"), 0, 1);
        grid.add(paymentCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Convert result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("amount", amountField.getText());
                result.put("paymentMethod", paymentCombo.getValue());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                BigDecimal amount = new BigDecimal(result.get("amount").toString().trim());
                String paymentMethodDisplay = result.get("paymentMethod").toString();

                // Map display text to backend enum value
                String paymentMethod = "CASH"; // Default
                if (paymentMethodDisplay.equals(resourceBundle.getString("payment.credit_card"))) {
                    paymentMethod = "CREDIT_CARD";
                } else if (paymentMethodDisplay.equals(resourceBundle.getString("payment.current_account"))) {
                    paymentMethod = "CURRENT_ACCOUNT";
                }

                // Create request body as Map
                java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
                requestBody.put("collectedAmount", amount);
                requestBody.put("paymentMethod", paymentMethod);

                ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
                api.completeService(currentTicket.getId(), requestBody).enqueue(new Callback<ServiceTicketDTO>() {
                    @Override
                    public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Platform.runLater(() -> {
                                currentTicket = response.body();
                                updateUI();
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, lblStatus.getScene().getWindow(),
                                        resourceBundle.getString("dialog.title.success"),
                                        resourceBundle.getString("dialog.complete.success"));
                            });
                        } else {
                            Platform.runLater(() -> {
                                AlertHelper.showAlert(Alert.AlertType.ERROR, lblStatus.getScene().getWindow(),
                                        "Hata", "Servis tamamlanamadı: " + response.code());
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

    @FXML
    private void handlePrintPdf() {
        if (currentTicket == null)
            return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("PDF Kaydet");
        fileChooser.setInitialFileName("servis_raporu_" + currentTicket.getId() + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Dosyaları", "*.pdf"));

        File file = fileChooser.showSaveDialog(lblStatus.getScene().getWindow());
        if (file != null) {
            downloadServicePdf(currentTicket.getId(), file);
        }
    }

    private void downloadServicePdf(Long ticketId, File destination) {
        ReportApi reportApi = RetrofitClient.getClient().create(ReportApi.class);
        reportApi.downloadServiceReport(ticketId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        byte[] bytes = response.body().bytes();
                        Files.write(destination.toPath(), bytes);

                        Platform.runLater(() -> {
                            AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                                    lblStatus.getScene().getWindow(),
                                    resourceBundle.getString("dialog.title.success"),
                                    resourceBundle.getString("report.save_success"));

                            // Optional: Auto-open PDF
                            try {
                                if (java.awt.Desktop.isDesktopSupported()) {
                                    java.awt.Desktop.getDesktop().open(destination);
                                }
                            } catch (Exception e) {
                                // Silently fail if can't open
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            AlertHelper.showAlert(Alert.AlertType.ERROR,
                                    lblStatus.getScene().getWindow(),
                                    resourceBundle.getString("dialog.title.error"),
                                    resourceBundle.getString("report.save_error") + ": " + e.getMessage());
                        });
                    }
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR,
                                lblStatus.getScene().getWindow(),
                                resourceBundle.getString("dialog.title.error"),
                                "PDF oluşturulamadı: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR,
                            lblStatus.getScene().getWindow(),
                            resourceBundle.getString("dialog.title.error"),
                            t.getMessage());
                });
            }
        });
    }
}
