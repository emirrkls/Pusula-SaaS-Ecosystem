package com.pusula.desktop.controller;

import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.dto.ServicePhotoDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.ThemeHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServicePhotosController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
    private final Map<String, String> categories = new LinkedHashMap<>();

    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TableView<ServicePhotoDTO> photoTable;
    @FXML private TableColumn<ServicePhotoDTO, LocalDateTime> colServiceDate;
    @FXML private TableColumn<ServicePhotoDTO, Number> colTicket;
    @FXML private TableColumn<ServicePhotoDTO, String> colCustomer;
    @FXML private TableColumn<ServicePhotoDTO, String> colDescription;
    @FXML private TableColumn<ServicePhotoDTO, String> colCategory;
    @FXML private TableColumn<ServicePhotoDTO, String> colNote;
    @FXML private TableColumn<ServicePhotoDTO, String> colUploader;

    @FXML
    public void initialize() {
        categories.put("Tümü", null);
        categories.put("İşlem Öncesi", "BEFORE");
        categories.put("İşlem Sonrası", "AFTER");
        categories.put("İç Ünite Seri No", "INDOOR_UNIT_SERIAL");
        categories.put("Dış Ünite Seri No", "OUTDOOR_UNIT_SERIAL");
        categories.put("Cihaz Etiketi", "DEVICE_LABEL");
        categories.put("Arıza Detayı", "FAULT_DETAIL");
        categories.put("Montaj / Tesisat", "INSTALLATION");
        categories.put("Diğer", "OTHER");
        categoryFilter.getItems().setAll(categories.keySet());
        categoryFilter.getSelectionModel().selectFirst();

        colServiceDate.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getServiceDate()));
        colServiceDate.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LocalDateTime value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? "" : DATE_FORMAT.format(value));
            }
        });
        colTicket.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getTicketId()));
        colCustomer.setCellValueFactory(cell -> text(cell.getValue().getCustomerName()));
        colDescription.setCellValueFactory(cell -> text(cell.getValue().getTicketDescription()));
        colCategory.setCellValueFactory(cell -> text(cell.getValue().getTypeLabel()));
        colNote.setCellValueFactory(cell -> text(cell.getValue().getNote()));
        colUploader.setCellValueFactory(cell -> text(cell.getValue().getUploadedByName()));
        photoTable.setRowFactory(table -> {
            TableRow<ServicePhotoDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) openPhoto(row.getItem()); });
            return row;
        });
        searchField.setOnAction(event -> loadPhotos());
        loadPhotos();
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    @FXML
    public void loadPhotos() {
        photoTable.setDisable(true);
        String category = categories.get(categoryFilter.getValue());
        api.getCompanyServicePhotos(category,
                startDatePicker.getValue() == null ? null : startDatePicker.getValue().toString(),
                endDatePicker.getValue() == null ? null : endDatePicker.getValue().toString(),
                searchField.getText() == null || searchField.getText().isBlank() ? null : searchField.getText().trim(),
                1000).enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<ServicePhotoDTO>> call, Response<List<ServicePhotoDTO>> response) {
                Platform.runLater(() -> {
                    photoTable.setDisable(false);
                    if (response.isSuccessful() && response.body() != null) photoTable.getItems().setAll(response.body());
                    else showError("Servis görselleri alınamadı (HTTP " + response.code() + ").");
                });
            }
            @Override public void onFailure(Call<List<ServicePhotoDTO>> call, Throwable throwable) {
                Platform.runLater(() -> { photoTable.setDisable(false); showError("Servis görselleri alınamadı: " + throwable.getMessage()); });
            }
        });
    }

    @FXML public void openSelected() {
        ServicePhotoDTO selected = photoTable.getSelectionModel().getSelectedItem();
        if (selected != null) openPhoto(selected);
    }

    private void openPhoto(ServicePhotoDTO photo) {
        Dialog<Void> dialog = new Dialog<>();
        ThemeHelper.applyToDialog(dialog, photoTable.getScene().getWindow());
        dialog.setTitle(photo.getCustomerName() + " · Fiş #" + photo.getTicketId());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ImageView imageView = new ImageView(new Image(photo.getUrl(), true));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(1050);
        imageView.setFitHeight(720);
        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        Slider zoom = new Slider(0.5, 5, 1);
        zoom.setPrefWidth(220);
        zoom.valueProperty().addListener((observable, oldValue, newValue) -> {
            imageView.setFitWidth(1050 * newValue.doubleValue());
            imageView.setFitHeight(720 * newValue.doubleValue());
        });
        Button resetZoom = new Button("Boyutu Sıfırla");
        resetZoom.setOnAction(event -> zoom.setValue(1));
        HBox controls = new HBox(10, new Label("Yakınlaştır:"), zoom, resetZoom);
        BorderPane pane = new BorderPane(scrollPane);
        pane.setTop(controls);
        pane.setPrefSize(1100, 780);
        dialog.getDialogPane().setContent(pane);
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    @FXML public void downloadSelected() {
        ServicePhotoDTO photo = photoTable.getSelectionModel().getSelectedItem();
        if (photo == null) { showError("Önce indirilecek görseli seçin."); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Servis Görselini Kaydet");
        chooser.setInitialFileName("servis-" + photo.getTicketId() + "-" + photo.getId() + ".jpg");
        File destination = chooser.showSaveDialog(photoTable.getScene().getWindow());
        if (destination == null) return;
        Thread thread = new Thread(() -> {
            try (InputStream input = URI.create(photo.getUrl()).toURL().openStream()) {
                Files.copy(input, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                        photoTable.getScene().getWindow(), "Tamamlandı", "Görsel kaydedildi."));
            } catch (Exception e) { Platform.runLater(() -> showError("Görsel indirilemedi: " + e.getMessage())); }
        }, "service-photo-download");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, photoTable.getScene().getWindow(), "Hata", message);
    }
}
