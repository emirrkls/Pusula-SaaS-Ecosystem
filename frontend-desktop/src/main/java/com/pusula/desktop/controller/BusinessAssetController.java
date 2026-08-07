package com.pusula.desktop.controller;

import com.pusula.desktop.api.BusinessAssetApi;
import com.pusula.desktop.dto.BusinessAssetDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.BusinessAssetPdfReportGenerator;
import com.pusula.desktop.util.SessionManager;
import com.pusula.desktop.util.TableUiHelper;
import com.pusula.desktop.util.ThemeHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class BusinessAssetController {
    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private TableView<BusinessAssetDTO> assetTable;
    @FXML private TableColumn<BusinessAssetDTO, String> colAssetName;
    @FXML private TableColumn<BusinessAssetDTO, String> colAssetCategory;
    @FXML private TableColumn<BusinessAssetDTO, Integer> colAssetQuantity;
    @FXML private TableColumn<BusinessAssetDTO, String> colAssetCondition;
    @FXML private TableColumn<BusinessAssetDTO, String> colAssetSerial;
    @FXML private TableColumn<BusinessAssetDTO, String> colAssetAssignment;
    @FXML private TableColumn<BusinessAssetDTO, LocalDate> colAssetPurchaseDate;
    @FXML private TableColumn<BusinessAssetDTO, BigDecimal> colAssetPurchasePrice;
    @FXML private TableColumn<BusinessAssetDTO, Void> colAssetActions;
    @FXML private TextField assetSearchField;
    @FXML private Label assetResultLabel;
    @FXML private Button addAssetButton;
    @FXML private Button exportAssetButton;

    private final ObservableList<BusinessAssetDTO> assets = FXCollections.observableArrayList();
    private FilteredList<BusinessAssetDTO> filteredAssets;

    @FXML
    public void initialize() {
        configureColumns();
        filteredAssets = new FilteredList<>(assets, item -> true);
        SortedList<BusinessAssetDTO> sorted = new SortedList<>(filteredAssets);
        sorted.comparatorProperty().bind(assetTable.comparatorProperty());
        assetTable.setItems(sorted);
        assetTable.setFixedCellSize(48);
        assetSearchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch());

        if (SessionManager.isTechnician()) {
            addAssetButton.setDisable(true);
            exportAssetButton.setDisable(true);
            assetSearchField.setDisable(true);
            assetResultLabel.setText("Takım / demirbaş yönetimi yalnızca yöneticilere açıktır.");
            return;
        }
        loadAssets();
    }

    private void configureColumns() {
        colAssetName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAssetName()));
        colAssetCategory.setCellValueFactory(cell -> new SimpleStringProperty(orDash(cell.getValue().getCategory())));
        colAssetQuantity.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getQuantity()));
        colAssetCondition.setCellValueFactory(cell -> new SimpleStringProperty(
                BusinessAssetDialogController.conditionLabel(cell.getValue().getCondition())));
        colAssetSerial.setCellValueFactory(cell -> new SimpleStringProperty(orDash(cell.getValue().getSerialNumber())));
        colAssetAssignment.setCellValueFactory(cell -> {
            String location = orDash(cell.getValue().getLocation());
            String assigned = cell.getValue().getAssignedTo();
            return new SimpleStringProperty(assigned == null || assigned.isBlank()
                    ? location : location + " / " + assigned);
        });
        colAssetPurchaseDate.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getPurchaseDate()));
        colAssetPurchaseDate.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LocalDate value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? "—" : DATE.format(value));
            }
        });
        colAssetPurchasePrice.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getPurchasePrice()));
        colAssetPurchasePrice.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : TableUiHelper.formatCurrency(value));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        colAssetActions.setCellFactory(column -> new TableCell<>() {
            private final Button edit = new Button("Düzenle");
            private final Button delete = new Button("Sil");
            private final HBox box = new HBox(6, edit, delete);
            {
                edit.getStyleClass().add("btn-secondary");
                delete.getStyleClass().add("btn-danger");
                edit.setOnAction(event -> openDialog(getTableView().getItems().get(getIndex())));
                delete.setOnAction(event -> deleteAsset(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    static boolean matchesSearch(BusinessAssetDTO asset, String search) {
        if (search == null || search.isBlank()) return true;
        String haystack = String.join(" ", normalize(asset.getAssetName()), normalize(asset.getCategory()),
                normalize(asset.getCondition()), normalize(asset.getSerialNumber()), normalize(asset.getLocation()),
                normalize(asset.getAssignedTo()), normalize(asset.getNotes()), normalize(asset.getQuantity()),
                normalize(asset.getPurchaseDate()));
        for (String token : normalize(search).split("\\s+")) {
            if (!token.isBlank() && !haystack.contains(token)) return false;
        }
        return true;
    }

    private void applySearch() {
        filteredAssets.setPredicate(asset -> matchesSearch(asset, assetSearchField.getText()));
        updateResultLabel();
        if (!filteredAssets.isEmpty()) assetTable.scrollTo(0);
    }

    @FXML private void handleAddAsset() { openDialog(null); }
    @FXML private void handleRefreshAssets() { loadAssets(); }
    @FXML private void handleClearAssetSearch() { assetSearchField.clear(); }

    @FXML
    private void handleExportAssetsPdf() {
        if (assets.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, assetTable.getScene().getWindow(),
                    "Kayıt Yok", "PDF'e aktarılacak takım veya demirbaş bulunmuyor.");
            return;
        }
        BusinessAssetPdfReportGenerator.generate((Stage) assetTable.getScene().getWindow(), List.copyOf(assets));
    }

    private void openDialog(BusinessAssetDTO asset) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/business_asset_dialog.fxml"));
            Stage stage = new Stage();
            stage.setScene(ThemeHelper.createDialogScene(loader.load()));
            stage.setTitle(asset == null ? "Takım / Demirbaş Ekle" : "Takım / Demirbaş Düzenle");
            stage.initOwner(assetTable.getScene().getWindow());
            stage.initModality(Modality.APPLICATION_MODAL);
            BusinessAssetDialogController controller = loader.getController();
            controller.setAsset(asset);
            controller.setOnSaveSuccess(this::loadAssets);
            stage.showAndWait();
        } catch (Exception e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, assetTable.getScene().getWindow(),
                    "Hata", "Form açılamadı: " + e.getMessage());
        }
    }

    private void deleteAsset(BusinessAssetDTO asset) {
        if (!AlertHelper.showConfirmation(assetTable.getScene().getWindow(), "Kaydı Sil",
                asset.getAssetName() + " kaydı silinsin mi?")) return;
        api().delete(asset.getId()).enqueue(new Callback<>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) loadAssets();
                    else showLoadError("Kayıt silinemedi", response.code());
                });
            }
            @Override public void onFailure(Call<Void> call, Throwable throwable) {
                Platform.runLater(() -> showNetworkError(throwable));
            }
        });
    }

    private void loadAssets() {
        api().getAll().enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<BusinessAssetDTO>> call,
                    Response<List<BusinessAssetDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        assets.setAll(response.body());
                        applySearch();
                    } else showLoadError("Takım / demirbaş listesi yüklenemedi", response.code());
                });
            }
            @Override public void onFailure(Call<List<BusinessAssetDTO>> call, Throwable throwable) {
                Platform.runLater(() -> showNetworkError(throwable));
            }
        });
    }

    private void updateResultLabel() {
        int units = assets.stream().mapToInt(a -> a.getQuantity() == null ? 0 : a.getQuantity()).sum();
        assetResultLabel.setText(filteredAssets.size() + " kayıt · " + units + " toplam adet");
    }

    private void showLoadError(String message, int code) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, assetTable.getScene().getWindow(), "Hata",
                message + " (HTTP " + code + ").");
    }

    private void showNetworkError(Throwable throwable) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, assetTable.getScene().getWindow(), "Bağlantı Hatası",
                "Sunucuya bağlanılamadı: " + throwable.getMessage());
    }

    private BusinessAssetApi api() { return RetrofitClient.getClient().create(BusinessAssetApi.class); }
    private static String normalize(Object value) { return value == null ? "" : value.toString().trim().toLowerCase(TR); }
    private static String orDash(String value) { return value == null || value.isBlank() ? "—" : value; }
}
