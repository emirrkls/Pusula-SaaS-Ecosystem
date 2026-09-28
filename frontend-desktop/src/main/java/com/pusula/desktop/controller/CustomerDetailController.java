package com.pusula.desktop.controller;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.CustomerWhatsAppConsentDTO;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.UpdateCustomerWhatsAppConsentRequest;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.dto.WhatsAppConsentSource;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.ApiErrorHelper;
import com.pusula.desktop.util.SessionManager;
import com.pusula.desktop.util.WhatsAppHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.format.DateTimeFormatter;
import java.text.MessageFormat;
import java.util.List;

public class CustomerDetailController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField addressField;

    @FXML
    private Button btnWhatsApp;

    @FXML
    private VBox whatsappConsentPane;

    @FXML
    private Label whatsappConsentStatusLabel;

    @FXML
    private Label whatsappConsentDetailLabel;

    @FXML
    private ComboBox<ConsentSourceOption> whatsappConsentSourceCombo;

    @FXML
    private Button btnGrantWhatsAppConsent;

    @FXML
    private Button btnRevokeWhatsAppConsent;

    @FXML
    private ProgressIndicator whatsappConsentProgress;

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

    @FXML
    private TableColumn<ServiceTicketDTO, Void> colHistoryActions;

    private CustomerDTO currentCustomer;
    private Runnable onSaveSuccess;
    private java.util.ResourceBundle resourceBundle;
    private java.util.Map<Long, String> technicianMap = new java.util.HashMap<>();
    private CustomerWhatsAppConsentDTO whatsappConsent;
    private boolean whatsappConsentBusy;
    private static final DateTimeFormatter CONSENT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        // Load resource bundle
        resourceBundle = java.util.ResourceBundle.getBundle("i18n.messages",
                java.util.Locale.of("tr", "TR"), new com.pusula.desktop.util.UTF8Control());
        setupWhatsAppButton();
        setupWhatsAppConsentControls();
        setupTable();
        loadTechnicians();
    }

    private void setupWhatsAppConsentControls() {
        boolean admin = SessionManager.isAdmin();
        whatsappConsentPane.setVisible(admin);
        whatsappConsentPane.setManaged(admin);
        if (!admin) return;

        whatsappConsentSourceCombo.setItems(FXCollections.observableArrayList(
                sourceOption(WhatsAppConsentSource.WRITTEN_FORM, "customer.whatsapp_consent.source.written"),
                sourceOption(WhatsAppConsentSource.VERBAL_CONFIRMATION, "customer.whatsapp_consent.source.verbal"),
                sourceOption(WhatsAppConsentSource.DIGITAL_FORM, "customer.whatsapp_consent.source.digital"),
                sourceOption(WhatsAppConsentSource.WHATSAPP_CONVERSATION, "customer.whatsapp_consent.source.whatsapp"),
                sourceOption(WhatsAppConsentSource.OTHER, "customer.whatsapp_consent.source.other")));
        whatsappConsentSourceCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                updateWhatsAppConsentActions());
        setWhatsAppConsentBusy(false);
        renderWhatsAppConsent(null);
    }

    private ConsentSourceOption sourceOption(WhatsAppConsentSource source, String key) {
        return new ConsentSourceOption(source, resourceBundle.getString(key));
    }

    private void setupWhatsAppButton() {
        FontIcon icon = FontIcon.of(MaterialDesignW.WHATSAPP, 16);
        icon.setIconColor(Color.WHITE);
        btnWhatsApp.setGraphic(icon);
        btnWhatsApp.setTooltip(new Tooltip("WhatsApp"));
    }

    private void setupTable() {
        colDate.setCellValueFactory(cellData -> {
            var date = cellData.getValue().getScheduledDate() != null
                    ? cellData.getValue().getScheduledDate() : cellData.getValue().getCreatedAt();
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
                    getStyleClass().removeIf(c -> c.startsWith("status-text-"));
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

                    setText(translatedStatus);
                    getStyleClass().removeIf(c -> c.startsWith("status-text-"));
                    getStyleClass().add(switch (status) {
                        case "COMPLETED" -> "status-text-completed";
                        case "IN_PROGRESS", "ASSIGNED" -> "status-text-active";
                        case "CANCELLED" -> "status-text-cancelled";
                        default -> "status-text-pending";
                    });
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
        colHistoryActions.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            private final Button detailButton = new Button("Aç");
            {
                detailButton.getStyleClass().addAll("button-sm", "button-secondary");
                detailButton.setOnAction(event -> {
                    ServiceTicketDTO ticket = getTableRow() != null ? getTableRow().getItem() : null;
                    if (ticket != null) openTicketDetails(ticket);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : detailButton);
            }
        });
        historyTable.setPlaceholder(new javafx.scene.control.Label("İşlem geçmişi yükleniyor…"));
        historyTable.setRowFactory(table -> {
            javafx.scene.control.TableRow<ServiceTicketDTO> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openTicketDetails(row.getItem());
            });
            return row;
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
            if (SessionManager.isAdmin()) loadWhatsAppConsent(customer.getId());
        }
    }

    private void loadWhatsAppConsent(Long customerId) {
        setWhatsAppConsentBusy(true);
        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);
        api.getWhatsAppConsent(customerId).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<CustomerWhatsAppConsentDTO> call,
                    Response<CustomerWhatsAppConsentDTO> response) {
                Platform.runLater(() -> {
                    setWhatsAppConsentBusy(false);
                    if (response.isSuccessful() && response.body() != null) {
                        renderWhatsAppConsent(response.body());
                    } else {
                        renderWhatsAppConsentError();
                    }
                });
            }

            @Override
            public void onFailure(Call<CustomerWhatsAppConsentDTO> call, Throwable throwable) {
                Platform.runLater(() -> {
                    setWhatsAppConsentBusy(false);
                    renderWhatsAppConsentError();
                });
            }
        });
    }

    private void renderWhatsAppConsent(CustomerWhatsAppConsentDTO consent) {
        whatsappConsent = consent;
        whatsappConsentStatusLabel.getStyleClass().removeAll(
                "status-badge-pending", "status-badge-completed", "status-badge-cancelled");

        if (consent == null) {
            whatsappConsentStatusLabel.setText(resourceBundle.getString("customer.whatsapp_consent.status.not_granted"));
            whatsappConsentStatusLabel.getStyleClass().add("status-badge-pending");
            whatsappConsentDetailLabel.setText(resourceBundle.getString("customer.whatsapp_consent.not_granted_hint"));
        } else if (consent.optedIn()) {
            whatsappConsentStatusLabel.setText(resourceBundle.getString("customer.whatsapp_consent.status.granted"));
            whatsappConsentStatusLabel.getStyleClass().add("status-badge-completed");
            whatsappConsentDetailLabel.setText(consentDetail(consent));
        } else if (consent.optedOutAt() != null) {
            whatsappConsentStatusLabel.setText(resourceBundle.getString("customer.whatsapp_consent.status.revoked"));
            whatsappConsentStatusLabel.getStyleClass().add("status-badge-cancelled");
            whatsappConsentDetailLabel.setText(consentDetail(consent));
        } else {
            whatsappConsentStatusLabel.setText(resourceBundle.getString("customer.whatsapp_consent.status.not_granted"));
            whatsappConsentStatusLabel.getStyleClass().add("status-badge-pending");
            whatsappConsentDetailLabel.setText(resourceBundle.getString("customer.whatsapp_consent.not_granted_hint"));
        }
        updateWhatsAppConsentActions();
    }

    private void renderWhatsAppConsentError() {
        whatsappConsent = null;
        whatsappConsentStatusLabel.getStyleClass().removeAll(
                "status-badge-pending", "status-badge-completed", "status-badge-cancelled");
        whatsappConsentStatusLabel.getStyleClass().add("status-badge-cancelled");
        whatsappConsentStatusLabel.setText(resourceBundle.getString("customer.whatsapp_consent.status.unavailable"));
        whatsappConsentDetailLabel.setText(resourceBundle.getString("customer.whatsapp_consent.load_failed"));
        updateWhatsAppConsentActions();
    }

    private String consentDetail(CustomerWhatsAppConsentDTO consent) {
        java.util.List<String> details = new java.util.ArrayList<>();
        if (consent.source() != null) {
            details.add(format("customer.whatsapp_consent.source_detail", sourceLabel(consent.source())));
        }
        if (consent.optedInAt() != null) {
            details.add(format("customer.whatsapp_consent.granted_at",
                    consent.optedInAt().format(CONSENT_DATE_FORMAT)));
        }
        if (consent.optedOutAt() != null) {
            details.add(format("customer.whatsapp_consent.revoked_at",
                    consent.optedOutAt().format(CONSENT_DATE_FORMAT)));
        }
        return String.join("  ·  ", details);
    }

    private String sourceLabel(WhatsAppConsentSource source) {
        return switch (source) {
            case WRITTEN_FORM -> resourceBundle.getString("customer.whatsapp_consent.source.written");
            case VERBAL_CONFIRMATION -> resourceBundle.getString("customer.whatsapp_consent.source.verbal");
            case DIGITAL_FORM -> resourceBundle.getString("customer.whatsapp_consent.source.digital");
            case WHATSAPP_CONVERSATION -> resourceBundle.getString("customer.whatsapp_consent.source.whatsapp");
            case OTHER -> resourceBundle.getString("customer.whatsapp_consent.source.other");
        };
    }

    private String format(String key, Object... values) {
        return MessageFormat.format(resourceBundle.getString(key), values);
    }

    private void setWhatsAppConsentBusy(boolean busy) {
        whatsappConsentBusy = busy;
        whatsappConsentProgress.setVisible(busy);
        whatsappConsentProgress.setManaged(busy);
        updateWhatsAppConsentActions();
    }

    private void updateWhatsAppConsentActions() {
        if (!SessionManager.isAdmin()) return;
        boolean optedIn = whatsappConsent != null && whatsappConsent.optedIn();
        btnGrantWhatsAppConsent.setDisable(whatsappConsentBusy || optedIn
                || whatsappConsentSourceCombo.getValue() == null);
        btnRevokeWhatsAppConsent.setDisable(whatsappConsentBusy || !optedIn);
        whatsappConsentSourceCombo.setDisable(whatsappConsentBusy || optedIn);
    }

    @FXML
    private void handleGrantWhatsAppConsent() {
        if (!SessionManager.isAdmin() || currentCustomer == null || whatsappConsentBusy) return;
        ConsentSourceOption selected = whatsappConsentSourceCombo.getValue();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, nameField.getScene().getWindow(),
                    resourceBundle.getString("customer.whatsapp_consent.source_required_title"),
                    resourceBundle.getString("customer.whatsapp_consent.source_required_message"));
            return;
        }
        updateWhatsAppConsent(new UpdateCustomerWhatsAppConsentRequest(true, selected.source()), true);
    }

    @FXML
    private void handleRevokeWhatsAppConsent() {
        if (!SessionManager.isAdmin() || currentCustomer == null || whatsappConsentBusy) return;
        boolean confirmed = AlertHelper.showConfirmation(nameField.getScene().getWindow(),
                resourceBundle.getString("customer.whatsapp_consent.revoke_confirm_title"),
                resourceBundle.getString("customer.whatsapp_consent.revoke_confirm_message"));
        if (confirmed) {
            updateWhatsAppConsent(new UpdateCustomerWhatsAppConsentRequest(false, null), false);
        }
    }

    private void updateWhatsAppConsent(UpdateCustomerWhatsAppConsentRequest request, boolean granting) {
        setWhatsAppConsentBusy(true);
        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);
        api.updateWhatsAppConsent(currentCustomer.getId(), request).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<CustomerWhatsAppConsentDTO> call,
                    Response<CustomerWhatsAppConsentDTO> response) {
                Platform.runLater(() -> {
                    setWhatsAppConsentBusy(false);
                    if (response.isSuccessful() && response.body() != null) {
                        renderWhatsAppConsent(response.body());
                        AlertHelper.showSuccess(nameField.getScene().getWindow(),
                                resourceBundle.getString("customer.whatsapp_consent.success_title"),
                                resourceBundle.getString(granting
                                        ? "customer.whatsapp_consent.grant_success"
                                        : "customer.whatsapp_consent.revoke_success"));
                    } else {
                        String message = ApiErrorHelper.message(response,
                                resourceBundle.getString("customer.whatsapp_consent.update_failed"));
                        showWhatsAppConsentError(message);
                    }
                });
            }

            @Override
            public void onFailure(Call<CustomerWhatsAppConsentDTO> call, Throwable throwable) {
                Platform.runLater(() -> {
                    setWhatsAppConsentBusy(false);
                    showWhatsAppConsentError(resourceBundle.getString("customer.whatsapp_consent.update_failed"));
                });
            }
        });
    }

    private void showWhatsAppConsentError(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, nameField.getScene().getWindow(),
                resourceBundle.getString("customer.whatsapp_consent.error_title"), message);
    }

    private record ConsentSourceOption(WhatsAppConsentSource source, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    private void loadServiceHistory(Long customerId) {
        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.getCustomerTickets(customerId).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<ServiceTicketDTO>> call, Response<List<ServiceTicketDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ServiceTicketDTO> history = response.body();
                    Platform.runLater(() -> historyTable.setItems(FXCollections.observableArrayList(history)));
                } else {
                    Platform.runLater(() -> historyTable.setPlaceholder(
                            new javafx.scene.control.Label("İşlem geçmişi yüklenemedi: " + response.code())));
                }
            }

            @Override
            public void onFailure(Call<List<ServiceTicketDTO>> call, Throwable t) {
                Platform.runLater(() -> historyTable.setPlaceholder(
                        new javafx.scene.control.Label("İşlem geçmişi yüklenemedi.")));
            }
        });
    }

    private void openTicketDetails(ServiceTicketDTO ticket) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/ticket_details.fxml"), resourceBundle);
            javafx.scene.Parent root = loader.load();
            TicketDetailsController controller = loader.getController();
            controller.setTicket(ticket);
            Stage stage = new Stage();
            stage.setTitle("Servis Fişi - " + ticket.getId());
            stage.setScene(com.pusula.desktop.util.ThemeHelper.createDialogScene(root, 900, 720));
            stage.initOwner(historyTable.getScene().getWindow());
            com.pusula.desktop.util.ThemeHelper.configureDialogStage(stage, historyTable.getScene().getWindow(),
                    com.pusula.desktop.util.ThemeHelper.DialogProfile.DETAIL);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            if (currentCustomer != null) loadServiceHistory(currentCustomer.getId());
        } catch (Exception exception) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, historyTable.getScene().getWindow(),
                    "Detay açılamadı", exception.getMessage());
        }
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
                            nameField.getScene().getWindow(), "Hata", "Müşteri bilgileri kaydedilemedi."));
                }
            }

            @Override
            public void onFailure(Call<CustomerDTO> call, Throwable t) {
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR, nameField.getScene().getWindow(),
                        "Bağlantı Hatası", "Sunucuya bağlanılamadı."));
            }
        });
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    @FXML
    private void handleWhatsAppClick() {
        if (currentCustomer != null && phoneField.getText() != null && !phoneField.getText().trim().isEmpty()) {
            WhatsAppHelper.openWhatsApp(phoneField.getText());
        } else {
            AlertHelper.showAlert(Alert.AlertType.WARNING, phoneField.getScene().getWindow(),
                    "Uyarı", "Geçerli bir telefon numarası bulunamadı.");
        }
    }

    private void closeDialog() {
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }
}
