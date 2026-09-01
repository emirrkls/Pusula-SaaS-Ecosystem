package com.pusula.desktop.controller;

import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.dto.ServicePhotoDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.ThemeHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServicePhotosController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final double FOLDER_WIDTH = 310;
    private static final double PHOTO_WIDTH = 250;

    private final ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
    private final Map<String, String> categories = new LinkedHashMap<>();
    private List<ServicePhotoDTO> loadedPhotos = List.of();
    private TicketPhotoGroup selectedGroup;

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ScrollPane folderScroll;
    @FXML private FlowPane folderFlow;
    @FXML private Label archiveSummaryLabel;
    @FXML private Label emptyArchiveLabel;
    @FXML private VBox galleryPane;
    @FXML private ScrollPane galleryScroll;
    @FXML private FlowPane galleryFlow;
    @FXML private Label galleryTitleLabel;
    @FXML private Label galleryMetaLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingIndicator;

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

        folderFlow.prefWrapLengthProperty().bind(folderScroll.widthProperty().subtract(32));
        galleryFlow.prefWrapLengthProperty().bind(galleryScroll.widthProperty().subtract(32));
        searchField.setOnAction(event -> loadPhotos());
        loadPhotos();
    }

    @FXML
    public void loadPhotos() {
        setLoading(true);
        String category = categories.get(categoryFilter.getValue());
        api.getCompanyServicePhotos(category,
                startDatePicker.getValue() == null ? null : startDatePicker.getValue().toString(),
                endDatePicker.getValue() == null ? null : endDatePicker.getValue().toString(),
                searchField.getText() == null || searchField.getText().isBlank() ? null : searchField.getText().trim(),
                1000).enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<ServicePhotoDTO>> call, Response<List<ServicePhotoDTO>> response) {
                Platform.runLater(() -> {
                    setLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        loadedPhotos = List.copyOf(response.body());
                        renderArchive();
                    } else {
                        showError("Servis görselleri alınamadı (HTTP " + response.code() + ").");
                    }
                });
            }

            @Override public void onFailure(Call<List<ServicePhotoDTO>> call, Throwable throwable) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showError("Servis görselleri alınamadı: " + throwable.getMessage());
                });
            }
        });
    }

    @FXML
    public void showArchive() {
        selectedGroup = null;
        galleryPane.setVisible(false);
        galleryPane.setManaged(false);
        folderScroll.setVisible(true);
        folderScroll.setManaged(true);
        statusLabel.setText("Servis dosyası seçerek içindeki görselleri görüntüleyebilirsiniz.");
    }

    private void renderArchive() {
        showArchive();
        List<TicketPhotoGroup> groups = groupPhotos(loadedPhotos);
        folderFlow.getChildren().clear();
        for (TicketPhotoGroup group : groups) {
            folderFlow.getChildren().add(createFolderCard(group));
        }
        emptyArchiveLabel.setVisible(groups.isEmpty());
        emptyArchiveLabel.setManaged(groups.isEmpty());
        archiveSummaryLabel.setText(groups.size() + " servis dosyası · " + loadedPhotos.size() + " görsel");
        statusLabel.setText(groups.isEmpty()
                ? "Filtrelere uygun görsel bulunamadı."
                : groups.size() + " servis dosyası listeleniyor.");
    }

    private VBox createFolderCard(TicketPhotoGroup group) {
        ServicePhotoDTO cover = group.photos().getFirst();
        StackPane preview = createPreview(cover, FOLDER_WIDTH, 155);

        Label countBadge = new Label(group.photos().size() + " görsel");
        countBadge.getStyleClass().add("service-photo-count");
        StackPane.setAlignment(countBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(countBadge, new javafx.geometry.Insets(10));
        preview.getChildren().add(countBadge);

        Label customer = new Label(safe(group.customerName(), "Müşteri"));
        customer.getStyleClass().add("service-folder-customer");
        customer.setMaxWidth(FOLDER_WIDTH - 28);

        Label description = new Label("#" + group.ticketId() + " · " + safe(group.description(), "Servis iş emri"));
        description.getStyleClass().add("service-folder-description");
        description.setWrapText(true);
        description.setMaxHeight(42);

        Label date = new Label(formatDate(group.serviceDate()));
        date.getStyleClass().add("service-folder-meta");

        VBox card = new VBox(7, preview, customer, description, date);
        card.setPrefWidth(FOLDER_WIDTH);
        card.setMaxWidth(FOLDER_WIDTH);
        card.getStyleClass().add("service-folder-card");
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> openGroup(group));
        return card;
    }

    private void openGroup(TicketPhotoGroup group) {
        selectedGroup = group;
        folderScroll.setVisible(false);
        folderScroll.setManaged(false);
        galleryPane.setVisible(true);
        galleryPane.setManaged(true);
        galleryTitleLabel.setText(safe(group.customerName(), "Müşteri") + " · Fiş #" + group.ticketId());
        galleryMetaLabel.setText(safe(group.description(), "Servis iş emri") + " · "
                + formatDate(group.serviceDate()) + " · " + group.photos().size() + " görsel");
        galleryFlow.getChildren().clear();
        for (ServicePhotoDTO photo : group.photos()) {
            galleryFlow.getChildren().add(createPhotoCard(photo, group.photos()));
        }
        galleryScroll.setVvalue(0);
        statusLabel.setText("Görseli büyütmek için karta tıklayın; tekil indirme kart üzerinden yapılabilir.");
    }

    private VBox createPhotoCard(ServicePhotoDTO photo, List<ServicePhotoDTO> gallery) {
        StackPane preview = createPreview(photo, PHOTO_WIDTH, 170);
        Label category = new Label(photo.getTypeLabel());
        category.getStyleClass().add("service-photo-category");

        Label note = new Label(safe(photo.getNote(), "Görsel notu eklenmemiş"));
        note.getStyleClass().add("service-photo-note");
        note.setWrapText(true);
        note.setMaxHeight(40);

        Label uploader = new Label(safe(photo.getUploadedByName(), "Ekleyen belirtilmemiş"));
        uploader.getStyleClass().add("service-folder-meta");

        Button download = new Button("İndir");
        download.getStyleClass().add("btn-sm");
        download.setOnAction(event -> {
            event.consume();
            downloadPhoto(photo);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, uploader, spacer, download);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(7, preview, category, note, footer);
        card.setPrefWidth(PHOTO_WIDTH);
        card.setMaxWidth(PHOTO_WIDTH);
        card.getStyleClass().add("service-photo-card");
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> openPhoto(photo, gallery));
        return card;
    }

    private StackPane createPreview(ServicePhotoDTO photo, double width, double height) {
        Label placeholder = new Label("Görsel yükleniyor…");
        placeholder.getStyleClass().add("service-photo-placeholder");
        ImageView imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        imageView.setClip(clip);

        try {
            Image image = new Image(resolvePhotoUrl(photo.getUrl()), width, height, false, true, true);
            imageView.setImage(image);
            image.progressProperty().addListener((observable, oldValue, value) -> {
                if (value.doubleValue() >= 1) placeholder.setVisible(false);
            });
            image.errorProperty().addListener((observable, oldValue, hasError) -> {
                if (hasError) Platform.runLater(() -> placeholder.setText("Önizleme yüklenemedi"));
            });
        } catch (RuntimeException exception) {
            placeholder.setText("Önizleme yüklenemedi");
        }

        StackPane pane = new StackPane(placeholder, imageView);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        pane.getStyleClass().add("service-photo-preview");
        return pane;
    }

    private void openPhoto(ServicePhotoDTO selected, List<ServicePhotoDTO> gallery) {
        List<ServicePhotoDTO> photos = gallery == null || gallery.isEmpty() ? List.of(selected) : gallery;
        int[] index = {Math.max(0, photos.indexOf(selected))};

        Dialog<Void> dialog = new Dialog<>();
        ThemeHelper.applyToDialog(dialog, rootPane.getScene().getWindow());
        dialog.setTitle("Servis Görseli");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        ScrollPane imageScroll = new ScrollPane(imageView);
        imageScroll.setPannable(true);
        imageScroll.setFitToWidth(false);
        imageScroll.setFitToHeight(false);
        imageScroll.getStyleClass().add("service-photo-viewer");

        Label title = new Label();
        title.getStyleClass().add("service-gallery-title");
        Label meta = new Label();
        meta.getStyleClass().add("section-caption");

        Slider zoom = new Slider(0.5, 5, 1);
        zoom.setPrefWidth(190);
        zoom.valueProperty().addListener((observable, oldValue, newValue) -> {
            imageView.setFitWidth(1000 * newValue.doubleValue());
            imageView.setFitHeight(680 * newValue.doubleValue());
        });

        Button previous = new Button("← Önceki");
        Button next = new Button("Sonraki →");
        Button reset = new Button("Boyutu Sıfırla");
        Button download = new Button("İndir");
        download.getStyleClass().add("btn-primary");

        Runnable refresh = () -> {
            ServicePhotoDTO photo = photos.get(index[0]);
            title.setText(photo.getTypeLabel() + " · " + (index[0] + 1) + "/" + photos.size());
            meta.setText(safe(photo.getNote(), "Görsel notu yok") + " · "
                    + safe(photo.getUploadedByName(), "Ekleyen belirtilmemiş"));
            zoom.setValue(1);
            imageScroll.setHvalue(0.5);
            imageScroll.setVvalue(0.5);
            Image image = new Image(resolvePhotoUrl(photo.getUrl()), true);
            imageView.setImage(image);
            previous.setDisable(index[0] == 0);
            next.setDisable(index[0] == photos.size() - 1);
            dialog.setTitle(safe(photo.getCustomerName(), "Müşteri") + " · Fiş #" + photo.getTicketId());
        };

        previous.setOnAction(event -> { if (index[0] > 0) { index[0]--; refresh.run(); } });
        next.setOnAction(event -> { if (index[0] < photos.size() - 1) { index[0]++; refresh.run(); } });
        reset.setOnAction(event -> zoom.setValue(1));
        download.setOnAction(event -> downloadPhoto(photos.get(index[0])));

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox controls = new HBox(9, previous, next, headerSpacer, new Label("Yakınlaştır:"), zoom, reset, download);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(4, title, meta, controls);

        BorderPane pane = new BorderPane(imageScroll);
        pane.setTop(header);
        pane.setPrefSize(1120, 800);
        BorderPane.setMargin(header, new javafx.geometry.Insets(0, 0, 10, 0));
        dialog.getDialogPane().setContent(pane);
        dialog.setResizable(true);
        refresh.run();
        dialog.showAndWait();
    }

    private void downloadPhoto(ServicePhotoDTO photo) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Servis Görselini Kaydet");
        chooser.setInitialFileName("servis-" + photo.getTicketId() + "-" + photo.getId() + extensionFor(photo.getUrl()));
        File destination = chooser.showSaveDialog(rootPane.getScene().getWindow());
        if (destination == null) return;

        Thread thread = new Thread(() -> {
            try (InputStream input = URI.create(resolvePhotoUrl(photo.getUrl())).toURL().openStream()) {
                Files.copy(input, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                        rootPane.getScene().getWindow(), "Tamamlandı", "Görsel kaydedildi."));
            } catch (Exception exception) {
                Platform.runLater(() -> showError("Görsel indirilemedi: " + exception.getMessage()));
            }
        }, "service-photo-download");
        thread.setDaemon(true);
        thread.start();
    }

    static List<TicketPhotoGroup> groupPhotos(List<ServicePhotoDTO> photos) {
        Map<Long, List<ServicePhotoDTO>> byTicket = new LinkedHashMap<>();
        for (ServicePhotoDTO photo : photos == null ? List.<ServicePhotoDTO>of() : photos) {
            if (photo != null && photo.getTicketId() != null) {
                byTicket.computeIfAbsent(photo.getTicketId(), ignored -> new ArrayList<>()).add(photo);
            }
        }
        return byTicket.entrySet().stream()
                .map(entry -> {
                    List<ServicePhotoDTO> ticketPhotos = entry.getValue().stream()
                            .sorted(Comparator.comparing(ServicePhotoDTO::getUploadedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    ServicePhotoDTO first = ticketPhotos.getFirst();
                    LocalDateTime serviceDate = ticketPhotos.stream()
                            .map(ServicePhotoDTO::getServiceDate)
                            .filter(java.util.Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(first.getUploadedAt());
                    return new TicketPhotoGroup(entry.getKey(), first.getCustomerName(),
                            first.getTicketDescription(), serviceDate, ticketPhotos);
                })
                .sorted(Comparator.comparing(TicketPhotoGroup::serviceDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TicketPhotoGroup::ticketId, Comparator.reverseOrder()))
                .toList();
    }

    record TicketPhotoGroup(Long ticketId, String customerName, String description,
                            LocalDateTime serviceDate, List<ServicePhotoDTO> photos) {}

    static String resolvePhotoUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Görsel URL'si boş.");
        }
        URI value = URI.create(rawUrl.trim());
        if (value.isAbsolute()) return value.toString();
        return URI.create(RetrofitClient.BASE_URL).resolve(value).toString();
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "Tarih belirtilmemiş" : DATE_FORMAT.format(value);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String extensionFor(String url) {
        String value = url == null ? "" : url.toLowerCase();
        if (value.contains(".png")) return ".png";
        if (value.contains(".jpeg")) return ".jpeg";
        if (value.contains(".webp")) return ".webp";
        return ".jpg";
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        folderScroll.setDisable(loading);
        galleryPane.setDisable(loading);
    }

    private void showError(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, rootPane.getScene().getWindow(), "Hata", message);
    }
}
