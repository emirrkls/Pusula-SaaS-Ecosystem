package com.pusula.desktop.controller;

import com.pusula.desktop.api.CommercialDeviceApi;
import com.pusula.desktop.dto.CommercialDeviceDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.SessionManager;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class CommercialDeviceViewController {

    @FXML
    private TableView<CommercialDeviceDTO> devicesTable;
    @FXML
    private TableColumn<CommercialDeviceDTO, String> colBrand;
    @FXML
    private TableColumn<CommercialDeviceDTO, String> colModel;
    @FXML
    private TableColumn<CommercialDeviceDTO, String> colType;
    @FXML
    private TableColumn<CommercialDeviceDTO, Integer> colBtu;
    @FXML
    private TableColumn<CommercialDeviceDTO, String> colGas;
    @FXML
    private TableColumn<CommercialDeviceDTO, Integer> colQuantity;
    @FXML
    private TableColumn<CommercialDeviceDTO, String> colSellingPrice;
    @FXML
    private TableColumn<CommercialDeviceDTO, String> colBuyingPrice;
    @FXML
    private TableColumn<CommercialDeviceDTO, String> colProfit;

    @FXML
    private ComboBox<String> cmbDeviceType;
    @FXML
    private ComboBox<String> cmbBrand;
    @FXML
    private ComboBox<String> cmbBtu;

    @FXML
    private Button btnAdd;
    @FXML
    private Button btnEdit;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnSell;

    private CommercialDeviceApi api;
    private ObservableList<CommercialDeviceDTO> allDevices = FXCollections.observableArrayList();
    private ObservableList<CommercialDeviceDTO> filteredDevices = FXCollections.observableArrayList();
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
        api = RetrofitClient.getClient().create(CommercialDeviceApi.class);

        setupTable();
        setupFilters();
        loadDevices();

        // Hide admin-only buttons for technicians
        if (!SessionManager.isAdmin()) {
            btnAdd.setVisible(false);
            btnAdd.setManaged(false);
            btnEdit.setVisible(false);
            btnEdit.setManaged(false);
            btnDelete.setVisible(false);
            btnDelete.setManaged(false);
        }
    }

    private void setupTable() {
        colBrand.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getBrand()));
        colModel.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getModel()));
        colType.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getDeviceTypeName() != null ? cellData.getValue().getDeviceTypeName() : "-"));
        colBtu.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getBtu()));
        colGas.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getGasType() != null ? cellData.getValue().getGasType() : "-"));
        colQuantity.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getQuantity()));
        colSellingPrice.setCellValueFactory(cellData -> {
            BigDecimal price = cellData.getValue().getSellingPrice();
            return new javafx.beans.property.SimpleStringProperty(
                    price != null ? String.format("%.2f ₺", price) : "-");
        });
        colBuyingPrice.setCellValueFactory(cellData -> {
            BigDecimal price = cellData.getValue().getBuyingPrice();
            return new javafx.beans.property.SimpleStringProperty(
                    price != null ? String.format("%.2f ₺", price) : "-");
        });
        colProfit.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getFormattedProfit() != null ? cellData.getValue().getFormattedProfit() : "-"));

        devicesTable.setItems(filteredDevices);
    }

    private void setupFilters() {
        cmbDeviceType.getItems().add("Tümü");
        cmbBrand.getItems().add("Tümü");
        cmbBtu.getItems().add("Tümü");

        cmbDeviceType.setValue("Tümü");
        cmbBrand.setValue("Tümü");
        cmbBtu.setValue("Tümü");

        cmbDeviceType.setOnAction(e -> applyFilters());
        cmbBrand.setOnAction(e -> applyFilters());
        cmbBtu.setOnAction(e -> applyFilters());
    }

    private void loadDevices() {
        api.getAll().enqueue(new Callback<List<CommercialDeviceDTO>>() {
            @Override
            public void onResponse(Call<List<CommercialDeviceDTO>> call, Response<List<CommercialDeviceDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        allDevices.setAll(response.body());
                        populateFilterDropdowns();
                        applyFilters();
                    });
                }
            }

            @Override
            public void onFailure(Call<List<CommercialDeviceDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Cihazlar yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    private void populateFilterDropdowns() {
        Set<String> types = allDevices.stream()
                .map(CommercialDeviceDTO::getDeviceTypeName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> brands = allDevices.stream()
                .map(CommercialDeviceDTO::getBrand)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> btus = allDevices.stream()
                .map(d -> d.getBtu() != null ? d.getBtu().toString() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        cmbDeviceType.getItems().setAll("Tümü");
        cmbDeviceType.getItems().addAll(types);
        cmbBrand.getItems().setAll("Tümü");
        cmbBrand.getItems().addAll(brands);
        cmbBtu.getItems().setAll("Tümü");
        cmbBtu.getItems().addAll(btus);
    }

    private void applyFilters() {
        String typeFilter = cmbDeviceType.getValue();
        String brandFilter = cmbBrand.getValue();
        String btuFilter = cmbBtu.getValue();

        filteredDevices.setAll(
                allDevices.stream()
                        .filter(d -> "Tümü".equals(typeFilter) || typeFilter == null ||
                                typeFilter.equals(d.getDeviceTypeName()))
                        .filter(d -> "Tümü".equals(brandFilter) || brandFilter == null ||
                                brandFilter.equals(d.getBrand()))
                        .filter(d -> "Tümü".equals(btuFilter) || btuFilter == null ||
                                (d.getBtu() != null && btuFilter.equals(d.getBtu().toString())))
                        .collect(Collectors.toList()));
    }

    @FXML
    private void handleClearFilters() {
        cmbDeviceType.setValue("Tümü");
        cmbBrand.setValue("Tümü");
        cmbBtu.setValue("Tümü");
        applyFilters();
    }

    @FXML
    private void handleAdd() {
        openDialog(null);
    }

    @FXML
    private void handleEdit() {
        CommercialDeviceDTO selected = devicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen düzenlemek için bir cihaz seçin.");
            return;
        }
        openDialog(selected);
    }

    @FXML
    private void handleDelete() {
        CommercialDeviceDTO selected = devicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen silmek için bir cihaz seçin.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Silme Onayı");
        confirm.setHeaderText(selected.getBrand() + " " + selected.getModel() + " cihazını silmek istiyor musunuz?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                api.delete(selected.getId()).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        Platform.runLater(() -> {
                            AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                    "Cihaz silindi.");
                            loadDevices();
                        });
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Platform.runLater(() -> {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                                    "Silme başarısız: " + t.getMessage());
                        });
                    }
                });
            }
        });
    }

    @FXML
    private void handleSell() {
        CommercialDeviceDTO selected = devicesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen satış yapmak için bir cihaz seçin.");
            return;
        }

        if (selected.getQuantity() == null || selected.getQuantity() <= 0) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Bu cihazın stoğu yok.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/commercial_device_sales_dialog.fxml"), bundle);
            Parent root = loader.load();

            CommercialDeviceSalesDialogController dialogController = loader.getController();
            dialogController.setDevice(selected);
            dialogController.setOnSuccess(() -> loadDevices());

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("commercial.sale.title"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Satış dialogu açılamadı: " + e.getMessage());
        }
    }

    private void openDialog(CommercialDeviceDTO device) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/commercial_device_dialog.fxml"), bundle);
            Parent root = loader.load();

            CommercialDeviceDialogController dialogController = loader.getController();
            if (device != null) {
                dialogController.setDevice(device);
            }
            dialogController.setOnSave(() -> loadDevices());

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(device == null ? "Cihaz Ekle" : "Cihaz Düzenle");
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }
}
