package com.pusula.desktop.controller;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CustomerSearchSupport;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

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
    private ObservableList<CustomerDTO> allCustomers;
    private ObservableList<CustomerDTO> filteredCustomers;
    private ResourceBundle bundle;

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
        allCustomers = FXCollections.observableArrayList();
        filteredCustomers = FXCollections.observableArrayList();
        configureCustomerComboBox();
        loadCustomers();
    }

    private void configureCustomerComboBox() {
        customerComboBox.setEditable(true);
        customerComboBox.setItems(filteredCustomers);
        customerComboBox.setConverter(new StringConverter<CustomerDTO>() {
            @Override
            public String toString(CustomerDTO customer) {
                return CustomerSearchSupport.displayText(customer);
            }

            @Override
            public CustomerDTO fromString(String string) {
                if (string == null || string.isEmpty())
                    return null;
                return allCustomers.stream()
                        .filter(c -> CustomerSearchSupport.displayText(c).equalsIgnoreCase(string.trim()))
                        .findFirst()
                        .orElse(null);
            }
        });

        customerComboBox.getEditor().setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN
                    || event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB
                    || event.getCode() == KeyCode.ESCAPE) {
                return;
            }

            String query = customerComboBox.getEditor().getText();
            CustomerDTO selected = customerComboBox.getSelectionModel().getSelectedItem();
            if (selected != null && !CustomerSearchSupport.displayText(selected).equals(query)) {
                customerComboBox.getSelectionModel().clearSelection();
                customerComboBox.setValue(null);
                customerComboBox.getEditor().setText(query);
                customerComboBox.getEditor().positionCaret(query.length());
            }

            applyCustomerFilter(query);
        });
    }

    private void applyCustomerFilter(String query) {
        filteredCustomers.setAll(allCustomers.stream()
                .filter(customer -> CustomerSearchSupport.matches(customer, query))
                .toList());

        if (filteredCustomers.isEmpty()) {
            customerComboBox.hide();
        } else if (!customerComboBox.isShowing()) {
            customerComboBox.show();
        }
    }

    private void loadCustomers() {
        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);
        api.getAllCustomers().enqueue(new Callback<List<CustomerDTO>>() {
            @Override
            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        allCustomers.setAll(response.body());
                        applyCustomerFilter(customerComboBox.getEditor().getText());
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
    private void handleAddCustomer() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/customer_dialog.fxml"),
                    ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control()));
            javafx.scene.Parent root = loader.load();
            CustomerDialogController dialogController = loader.getController();
            dialogController.setOnSaveSuccess((savedCustomer) -> {
                // Refresh customers and auto-select the new one
                loadCustomers();
                Platform.runLater(() -> {
                    // Wait a bit for the data to load then select the new customer
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            Platform.runLater(() -> {
                                allCustomers.stream()
                                        .filter(c -> c.getId().equals(savedCustomer.getId()))
                                        .findFirst()
                                        .ifPresent(c -> customerComboBox.setValue(c));
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                });
            });
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle(bundle.getString("customer.dialog.title"));
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(customerComboBox.getScene().getWindow());
            dialogStage.setScene(com.pusula.desktop.util.ThemeHelper.createDialogScene(root));
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, customerComboBox.getScene().getWindow(),
                    "Hata", "Müşteri formu açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        CustomerDTO selectedCustomer = customerComboBox.getValue();
        String description = descriptionField.getText();

        if (selectedCustomer == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, descriptionField.getScene().getWindow(),
                    "Eksik Bilgi", "Lütfen bir müşteri seçin.");
            return;
        }

        if (description.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, descriptionField.getScene().getWindow(),
                    "Eksik Bilgi", "Servis açıklaması zorunludur.");
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
                                "Hata", "Servis fişi oluşturulamadı: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<ServiceTicketDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, descriptionField.getScene().getWindow(),
                            "Bağlantı Hatası", "Sunucuya bağlanılamadı: " + t.getMessage());
                });
            }
        });
    }

    private void closeDialog() {
        Stage stage = (Stage) descriptionField.getScene().getWindow();
        stage.close();
    }
}
