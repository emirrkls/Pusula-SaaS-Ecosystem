package com.pusula.desktop.controller;

import com.pusula.desktop.api.CommercialDeviceApi;
import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.dto.*;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.CurrencyTextField;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class CommercialDeviceSalesDialogController {

    @FXML
    private Label deviceInfoLabel;
    @FXML
    private ComboBox<CustomerDTO> customerComboBox;
    @FXML
    private CurrencyTextField sellingPriceField;
    @FXML
    private ComboBox<String> paymentMethodComboBox;
    @FXML
    private DatePicker saleDatePicker;

    private CommercialDeviceDTO device;
    private CommercialDeviceApi commercialDeviceApi;
    private CustomerApi customerApi;
    private ResourceBundle bundle;
    private Runnable onSuccess;
    private ObservableList<CustomerDTO> allCustomers;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
        commercialDeviceApi = RetrofitClient.getClient().create(CommercialDeviceApi.class);
        customerApi = RetrofitClient.getClient().create(CustomerApi.class);
        allCustomers = FXCollections.observableArrayList();

        // Setup payment method combo
        paymentMethodComboBox.setItems(FXCollections.observableArrayList(
                "Nakit", "Kredi Kartı", "Cari Hesap"));
        paymentMethodComboBox.getSelectionModel().selectFirst();

        // Setup date picker default
        saleDatePicker.setValue(LocalDate.now());

        // Setup searchable customer combo
        configureCustomerComboBox();
        loadCustomers();
    }

    private void configureCustomerComboBox() {
        customerComboBox.setEditable(true);
        customerComboBox.setConverter(new StringConverter<CustomerDTO>() {
            @Override
            public String toString(CustomerDTO customer) {
                if (customer == null)
                    return "";
                String phone = customer.getPhone() != null ? customer.getPhone() : "-";
                return customer.getName() + " - " + phone;
            }

            @Override
            public CustomerDTO fromString(String string) {
                if (string == null || string.isEmpty())
                    return null;
                return allCustomers.stream()
                        .filter(c -> {
                            String phone = c.getPhone() != null ? c.getPhone() : "-";
                            return (c.getName() + " - " + phone).equals(string);
                        })
                        .findFirst()
                        .orElse(null);
            }
        });

        // Add listener for filtering by name or phone
        customerComboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            // If text matches current selection, don't filter
            if (customerComboBox.getSelectionModel().getSelectedItem() != null) {
                String selectedString = customerComboBox.getConverter()
                        .toString(customerComboBox.getSelectionModel().getSelectedItem());
                if (selectedString.equalsIgnoreCase(newValue)) {
                    return;
                }
            }

            if (newValue == null || newValue.isEmpty()) {
                customerComboBox.setItems(allCustomers);
            } else {
                String search = newValue.toLowerCase();
                ObservableList<CustomerDTO> filtered = allCustomers
                        .filtered(customer -> {
                            boolean nameMatch = customer.getName() != null &&
                                    customer.getName().toLowerCase().contains(search);
                            boolean phoneMatch = customer.getPhone() != null &&
                                    customer.getPhone().toLowerCase().contains(search);
                            return nameMatch || phoneMatch;
                        });
                customerComboBox.setItems(filtered);
                if (!customerComboBox.isShowing()) {
                    customerComboBox.show();
                }
            }
        });
    }

    public void setDevice(CommercialDeviceDTO device) {
        this.device = device;
        deviceInfoLabel.setText(String.format("%s %s - %s BTU",
                device.getBrand(), device.getModel(),
                device.getBtu() != null ? device.getBtu() : "-"));
        if (device.getSellingPrice() != null) {
            sellingPriceField.setRawValue(device.getSellingPrice());
        }
    }

    public void setOnSuccess(Runnable onSuccess) {
        this.onSuccess = onSuccess;
    }

    private void loadCustomers() {
        customerApi.getAllCustomers().enqueue(new Callback<List<CustomerDTO>>() {
            @Override
            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        allCustomers.setAll(response.body());
                        customerComboBox.setItems(allCustomers);
                    });
                }
            }

            @Override
            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Müşteriler yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleAddCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/customer_dialog.fxml"),
                    ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control()));
            Parent root = loader.load();
            CustomerDialogController dialogController = loader.getController();
            dialogController.setOnSaveSuccess((savedCustomer) -> {
                // Refresh customers and auto-select the new one
                loadCustomers();
                Platform.runLater(() -> {
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
            Stage dialogStage = new Stage();
            dialogStage.setTitle(bundle.getString("customer.dialog.title"));
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(customerComboBox.getScene().getWindow());
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Müşteri dialogu açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleSale() {
        // Validate
        CustomerDTO selectedCustomer = customerComboBox.getValue();
        if (selectedCustomer == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen bir müşteri seçin.");
            return;
        }

        BigDecimal price;
        try {
            price = sellingPriceField.getRawValue();
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Geçersiz satış fiyatı.");
            return;
        }

        String paymentMethodDisplay = paymentMethodComboBox.getValue();
        String paymentMethod = convertPaymentMethod(paymentMethodDisplay);

        LocalDate saleDate = saleDatePicker.getValue();
        if (saleDate == null) {
            saleDate = LocalDate.now();
        }

        SaleRequestDTO request = SaleRequestDTO.builder()
                .deviceId(device.getId())
                .customerId(selectedCustomer.getId())
                .sellingPrice(price)
                .paymentMethod(paymentMethod)
                .saleDate(saleDate)
                .build();

        commercialDeviceApi.processSale(request).enqueue(new Callback<SaleResponseDTO>() {
            @Override
            public void onResponse(Call<SaleResponseDTO> call, Response<SaleResponseDTO> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        SaleResponseDTO result = response.body();
                        if (result.isSuccess()) {
                            AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı", result.getMessage());
                            if (onSuccess != null)
                                onSuccess.run();
                            closeDialog();
                        } else {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", result.getMessage());
                        }
                    } else {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                                "Satış başarısız oldu.");
                    }
                });
            }

            @Override
            public void onFailure(Call<SaleResponseDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Bağlantı Hatası",
                            "Sunucuya bağlanılamadı: " + t.getMessage());
                });
            }
        });
    }

    private String convertPaymentMethod(String display) {
        if ("Nakit".equals(display))
            return "CASH";
        if ("Kredi Kartı".equals(display))
            return "CREDIT_CARD";
        if ("Cari Hesap".equals(display))
            return "CURRENT_ACCOUNT";
        return "CASH";
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) customerComboBox.getScene().getWindow();
        stage.close();
    }
}
