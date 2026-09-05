package com.pusula.desktop.controller;

import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.dto.ServicePhotoDTO;
import com.pusula.desktop.dto.ServicePhotoPageDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.ThemeHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Window;
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
    private static final int PAGE_SIZE = 24;

    private final ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
    private final Map<String, String> categories = new LinkedHashMap<>();
    private List<ServicePhotoDTO> loadedPhotos = List.of();
    private TicketPhotoGroup selectedGroup;
    private int currentPage;
    private long totalServiceFiles;
    private boolean hasNextPage;

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
    @FXML private Button loadMoreButton;

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
        requestPage(0, true);
    }

    @FXML
    public void loadNextPage() {
        if (!hasNextPage) return;
        requestPage(currentPage + 1, false);
    }

    private void requestPage(int page, boolean replace) {
        setLoading(true);
        String category = categories.get(categoryFilter.getValue());
        api.getCompanyServicePhotoPage(category,
                startDatePicker.getValue() == null ? null : startDatePicker.getValue().toString(),
                endDatePicker.getValue() == null ? null : endDatePicker.getValue().toString(),
                searchField.getText() == null || searchField.getText().isBlank() ? null : searchField.getText().trim(),
                page, PAGE_SIZE).enqueue(new Callback<>() {
            @Override public void onResponse(Call<ServicePhotoPageDTO> call, Response<ServicePhotoPageDTO> response) {
                Platform.runLater(() -> {
                    setLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        ServicePhotoPageDTO result = response.body();
                        List<ServicePhotoDTO> items = result.getItems() == null ? List.of() : result.getItems();
                        loadedPhotos = replace ? List.copyOf(items) : mergePhotos(loadedPhotos, items);
                        currentPage = result.getPage();
                        totalServiceFiles = result.getTotalServiceFiles();
                        hasNextPage = result.isHasNext();
                        renderArchive();
                    } else {
                        showError("Servis görselleri alınamadı (HTTP " + response.code() + ").");
                    }
                });
            }

            @Override public void onFailure(Call<ServicePhotoPageDTO> call, Throwable throwable) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showError("Servis görselleri alınamadı: " + throwable.getMessage());
                });
            }
        });
    }

    private List<ServicePhotoDTO> mergePhotos(List<ServicePhotoDTO> current, List<ServicePhotoDTO> incoming) {
        Map<Long, ServicePhotoDTO> merged = new LinkedHashMap<>();
        for (ServicePhotoDTO photo : current) merged.put(photo.getId(), photo);
        for (ServicePhotoDTO photo : incoming) merged.put(photo.getId(), photo);
        return List.copyOf(merged.values());
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
        archiveSummaryLabel.setText(groups.size() + " / " + totalServiceFiles
                + " servis dosyası · " + loadedPhotos.size() + " görsel yüklendi");
        loadMoreButton.setVisible(hasNextPage);
        loadMoreButton.setManaged(hasNextPage);
        statusLabel.setText(groups.isEmpty()
                ? "Filtrelere uygun görsel bulunamadı."
                : groups.size() + " servis dosyası listeleniyor.");
    }

    private VBox createFolderCard(TicketPhotoGroup group) {
        ServicePhotoDTO cover = selectCover(group.photos());
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
        StackPane preview = createPreview(photo, PHOTO_WIDTH, 170, false);
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
        return createPreview(photo, width, height, true);
    }

    private StackPane createPreview(ServicePhotoDTO photo, double width, double height, boolean cropToFill) {
        Label placeholder = new Label("Görsel yükleniyor…");
        placeholder.getStyleClass().add("service-photo-placeholder");
        ImageView imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(!cropToFill);
        imageView.setSmooth(true);

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        imageView.setClip(clip);

        try {
            String previewUrl = photo.getThumbnailUrl() == null || photo.getThumbnailUrl().isBlank()
                    ? photo.getUrl() : photo.getThumbnailUrl();
            Image image = new Image(resolvePhotoUrl(previewUrl), width * 2, height * 2, true, true, true);
            imageView.setImage(image);
            image.progressProperty().addListener((observable, oldValue, value) -> {
                if (value.doubleValue() >= 1) {
                    if (cropToFill) applyCoverViewport(imageView, image, width, height);
                    placeholder.setVisible(false);
                }
            });
            image.errorProperty().addListener((observable, oldValue, hasError) -> {
                if (hasError) Platform.runLater(() -> placeholder.setText("Önizleme yüklenemedi"));
            });
            if (image.getProgress() >= 1) {
                if (cropToFill) applyCoverViewport(imageView, image, width, height);
                placeholder.setVisible(false);
            } else if (image.isError()) {
                placeholder.setText("Önizleme yüklenemedi");
            }
        } catch (RuntimeException exception) {
            placeholder.setText("Önizleme yüklenemedi");
        }

        StackPane pane = new StackPane(placeholder, imageView);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        pane.getStyleClass().add("service-photo-preview");
        return pane;
    }

    static ServicePhotoDTO selectCover(List<ServicePhotoDTO> photos) {
        if (photos == null || photos.isEmpty()) throw new IllegalArgumentException("Photo list is empty");
        return photos.stream().filter(photo -> "AFTER".equals(photo.getType())).findFirst()
                .or(() -> photos.stream().filter(photo -> "BEFORE".equals(photo.getType())).findFirst())
                .orElse(photos.getFirst());
    }

    static Rectangle2D coverViewport(double sourceWidth, double sourceHeight,
                                     double targetWidth, double targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return Rectangle2D.EMPTY;
        }
        double sourceRatio = sourceWidth / sourceHeight;
        double targetRatio = targetWidth / targetHeight;
        if (sourceRatio > targetRatio) {
            double viewportWidth = sourceHeight * targetRatio;
            return new Rectangle2D((sourceWidth - viewportWidth) / 2d, 0, viewportWidth, sourceHeight);
        }
        double viewportHeight = sourceWidth / targetRatio;
        return new Rectangle2D(0, (sourceHeight - viewportHeight) / 2d, sourceWidth, viewportHeight);
    }

    private void applyCoverViewport(ImageView imageView, Image image, double width, double height) {
        Rectangle2D viewport = coverViewport(image.getWidth(), image.getHeight(), width, height);
        if (!Rectangle2D.EMPTY.equals(viewport)) imageView.setViewport(viewport);
    }

    private void openPhoto(ServicePhotoDTO selected, List<ServicePhotoDTO> gallery) {
        List<ServicePhotoDTO> photos = gallery == null || gallery.isEmpty() ? List.of(selected) : gallery;
        int[] index = {Math.max(0, photos.indexOf(selected))};

        Dialog<Void> dialog = new Dialog<>();
        ThemeHelper.applyToDialog(dialog, rootPane.getScene().getWindow());
        dialog.setTitle("Servis Görseli");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node defaultClose = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        defaultClose.setVisible(false);
        defaultClose.setManaged(false);

        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
        imageView.setCacheHint(CacheHint.SCALE);
        StackPane imageCanvas = new StackPane(imageView);
        imageCanvas.getStyleClass().add("service-viewer-canvas");

        ScrollPane imageScroll = new ScrollPane(imageCanvas);
        imageScroll.setPannable(true);
        imageScroll.setFitToWidth(false);
        imageScroll.setFitToHeight(false);
        imageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        imageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        imageScroll.getStyleClass().add("service-photo-viewer");

        Label viewerMessage = new Label("Görsel yükleniyor…");
        viewerMessage.getStyleClass().add("service-viewer-message");
        StackPane viewerArea = new StackPane(imageScroll, viewerMessage);
        viewerArea.getStyleClass().add("service-viewer-area");

        Label title = new Label();
        title.getStyleClass().add("service-gallery-title");
        title.setWrapText(true);
        Label meta = new Label();
        meta.getStyleClass().add("section-caption");
        meta.setWrapText(true);

        Label zoomLabel = new Label("Sığdır");
        zoomLabel.getStyleClass().add("service-viewer-zoom-label");

        Button previous = viewerButton("‹", "Önceki görsel (Sol ok)");
        Button next = viewerButton("›", "Sonraki görsel (Sağ ok)");
        Button zoomOut = viewerButton("−", "Uzaklaştır");
        Button zoomIn = viewerButton("+", "Yakınlaştır");
        Button fit = viewerButton("Sığdır", "Görseli pencereye sığdır");
        Button actualSize = viewerButton("%100", "Görseli gerçek piksel boyutunda göster");
        Button download = viewerButton("İndir", "Özgün görseli indir");
        download.getStyleClass().add("btn-primary");
        Button close = viewerButton("Kapat", "Görüntüleyiciyi kapat (Esc)");

        class ZoomController {
            private double baseWidth = 1;
            private double baseHeight = 1;
            private double fitScale = 1;
            private double factor = 1;

            void fitToWindow() {
                Image image = imageView.getImage();
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
                Bounds viewport = imageScroll.getViewportBounds();
                double availableWidth = Math.max(320, viewport.getWidth() - 28);
                double availableHeight = Math.max(240, viewport.getHeight() - 28);
                fitScale = Math.min(1d, Math.min(
                        availableWidth / image.getWidth(), availableHeight / image.getHeight()));
                baseWidth = Math.max(1, image.getWidth() * fitScale);
                baseHeight = Math.max(1, image.getHeight() * fitScale);
                factor = 1;
                resize(-1, -1);
            }

            void actualSize() {
                setFactor(Math.min(8d, 1d / Math.max(0.0001d, fitScale)), -1, -1);
            }

            void toggleActualSize() {
                double actualFactor = Math.min(8d, 1d / Math.max(0.0001d, fitScale));
                if (Math.abs(factor - actualFactor) < 0.04) fitToWindow();
                else actualSize();
            }

            void changeBy(double multiplier, double anchorX, double anchorY) {
                setFactor(factor * multiplier, anchorX, anchorY);
            }

            boolean isFitMode() {
                return Math.abs(factor - 1d) < 0.01d;
            }

            void setFactor(double requested, double anchorX, double anchorY) {
                double newFactor = Math.max(0.5d, Math.min(8d, requested));
                if (Math.abs(newFactor - factor) < 0.001d) return;
                Bounds viewport = imageScroll.getViewportBounds();
                double viewportWidth = viewport.getWidth();
                double viewportHeight = viewport.getHeight();
                double oldContentWidth = Math.max(viewportWidth, imageCanvas.getWidth());
                double oldContentHeight = Math.max(viewportHeight, imageCanvas.getHeight());
                double pointX = anchorX < 0 ? viewportWidth / 2d : anchorX;
                double pointY = anchorY < 0 ? viewportHeight / 2d : anchorY;
                double oldOffsetX = imageScroll.getHvalue() * Math.max(0, oldContentWidth - viewportWidth);
                double oldOffsetY = imageScroll.getVvalue() * Math.max(0, oldContentHeight - viewportHeight);
                double relativeX = oldContentWidth <= 0 ? 0.5d : (oldOffsetX + pointX) / oldContentWidth;
                double relativeY = oldContentHeight <= 0 ? 0.5d : (oldOffsetY + pointY) / oldContentHeight;
                factor = newFactor;
                resize(pointX, pointY);
                Platform.runLater(() -> {
                    double newContentWidth = Math.max(viewportWidth, imageCanvas.getWidth());
                    double newContentHeight = Math.max(viewportHeight, imageCanvas.getHeight());
                    double horizontalRange = Math.max(0, newContentWidth - viewportWidth);
                    double verticalRange = Math.max(0, newContentHeight - viewportHeight);
                    if (horizontalRange > 0) {
                        imageScroll.setHvalue(clamp01((relativeX * newContentWidth - pointX) / horizontalRange));
                    }
                    if (verticalRange > 0) {
                        imageScroll.setVvalue(clamp01((relativeY * newContentHeight - pointY) / verticalRange));
                    }
                });
            }

            void resize(double anchorX, double anchorY) {
                Bounds viewport = imageScroll.getViewportBounds();
                double displayWidth = Math.max(1, baseWidth * factor);
                double displayHeight = Math.max(1, baseHeight * factor);
                imageView.setFitWidth(displayWidth);
                imageView.setFitHeight(displayHeight);
                imageCanvas.setPrefSize(
                        Math.max(viewport.getWidth(), displayWidth),
                        Math.max(viewport.getHeight(), displayHeight));
                double actualPercent = fitScale * factor * 100d;
                zoomLabel.setText(Math.round(actualPercent) + "%");
            }
        }
        ZoomController zoom = new ZoomController();

        imageScroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) return;
            zoom.changeBy(event.getDeltaY() > 0 ? 1.16d : 1d / 1.16d, event.getX(), event.getY());
            event.consume();
        });
        imageCanvas.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                zoom.toggleActualSize();
                event.consume();
            }
        });

        HBox filmstrip = new HBox(8);
        filmstrip.setAlignment(Pos.CENTER_LEFT);
        filmstrip.getStyleClass().add("service-viewer-filmstrip");
        List<StackPane> filmstripItems = new ArrayList<>();
        Runnable[] refreshHolder = new Runnable[1];
        for (int itemIndex = 0; itemIndex < photos.size(); itemIndex++) {
            int targetIndex = itemIndex;
            StackPane thumbnail = createPreview(photos.get(itemIndex), 76, 54);
            thumbnail.getStyleClass().add("service-viewer-thumb");
            thumbnail.setCursor(Cursor.HAND);
            thumbnail.setOnMouseClicked(event -> {
                index[0] = targetIndex;
                refreshHolder[0].run();
            });
            filmstripItems.add(thumbnail);
            filmstrip.getChildren().add(thumbnail);
        }
        ScrollPane filmstripScroll = new ScrollPane(filmstrip);
        filmstripScroll.setFitToHeight(true);
        filmstripScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        filmstripScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        filmstripScroll.setMaxHeight(82);
        filmstripScroll.getStyleClass().add("service-viewer-filmstrip-scroll");

        Runnable refresh = () -> {
            ServicePhotoDTO photo = photos.get(index[0]);
            title.setText(photo.getTypeLabel() + " · " + (index[0] + 1) + "/" + photos.size());
            meta.setText(safe(photo.getNote(), "Görsel notu yok") + "  •  "
                    + safe(photo.getUploadedByName(), "Ekleyen belirtilmemiş") + "  •  "
                    + formatDate(photo.getUploadedAt()));
            imageScroll.setHvalue(0.5);
            imageScroll.setVvalue(0.5);
            viewerMessage.setText("Görsel yükleniyor…");
            viewerMessage.setVisible(true);
            Image image = new Image(resolvePhotoUrl(photo.getUrl()), true);
            imageView.setImage(image);
            image.progressProperty().addListener((observable, oldValue, progress) -> {
                if (progress.doubleValue() >= 1) Platform.runLater(() -> {
                    viewerMessage.setVisible(false);
                    zoom.fitToWindow();
                });
            });
            image.errorProperty().addListener((observable, oldValue, failed) -> {
                if (failed) Platform.runLater(() -> {
                    viewerMessage.setText("Görsel yüklenemedi");
                    viewerMessage.setVisible(true);
                });
            });
            if (image.getProgress() >= 1) {
                viewerMessage.setVisible(false);
                Platform.runLater(zoom::fitToWindow);
            } else if (image.isError()) {
                viewerMessage.setText("Görsel yüklenemedi");
            }
            previous.setDisable(index[0] == 0);
            next.setDisable(index[0] == photos.size() - 1);
            for (int i = 0; i < filmstripItems.size(); i++) {
                filmstripItems.get(i).getStyleClass().remove("service-viewer-thumb-selected");
                if (i == index[0]) filmstripItems.get(i).getStyleClass().add("service-viewer-thumb-selected");
            }
            dialog.setTitle(safe(photo.getCustomerName(), "Müşteri") + " · Fiş #" + photo.getTicketId());
        };
        refreshHolder[0] = refresh;

        previous.setOnAction(event -> { if (index[0] > 0) { index[0]--; refresh.run(); } });
        next.setOnAction(event -> { if (index[0] < photos.size() - 1) { index[0]++; refresh.run(); } });
        zoomOut.setOnAction(event -> zoom.changeBy(1d / 1.2d, -1, -1));
        zoomIn.setOnAction(event -> zoom.changeBy(1.2d, -1, -1));
        fit.setOnAction(event -> zoom.fitToWindow());
        actualSize.setOnAction(event -> zoom.actualSize());
        download.setOnAction(event -> downloadPhoto(photos.get(index[0])));
        close.setOnAction(event -> dialog.close());

        HBox navigationControls = new HBox(7, previous, next);
        HBox zoomControls = new HBox(7, zoomOut, zoomLabel, zoomIn, fit, actualSize);
        HBox actionControls = new HBox(7, download, close);
        navigationControls.setAlignment(Pos.CENTER_LEFT);
        zoomControls.setAlignment(Pos.CENTER_LEFT);
        actionControls.setAlignment(Pos.CENTER_LEFT);
        FlowPane controls = new FlowPane(12, 8, navigationControls, zoomControls, actionControls);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("service-viewer-controls");
        VBox header = new VBox(5, title, meta, controls);
        header.getStyleClass().add("service-viewer-header");

        BorderPane pane = new BorderPane(viewerArea);
        pane.setTop(header);
        pane.setBottom(filmstripScroll);
        pane.getStyleClass().add("service-viewer-root");
        BorderPane.setMargin(header, new javafx.geometry.Insets(0, 0, 8, 0));
        BorderPane.setMargin(filmstripScroll, new javafx.geometry.Insets(8, 0, 0, 0));

        Window owner = rootPane.getScene().getWindow();
        List<Screen> screens = Screen.getScreensForRectangle(
                owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
        Rectangle2D visualBounds = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
        pane.setPrefSize(Math.max(680, Math.min(1280, visualBounds.getWidth() * 0.92)),
                Math.max(520, Math.min(900, visualBounds.getHeight() * 0.90)));
        dialog.getDialogPane().setContent(pane);
        dialog.setResizable(true);
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.LEFT && index[0] > 0) {
                index[0]--;
                refresh.run();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT && index[0] < photos.size() - 1) {
                index[0]++;
                refresh.run();
                event.consume();
            }
        });
        imageScroll.viewportBoundsProperty().addListener((observable, oldBounds, newBounds) -> {
            if (imageView.getImage() != null && zoom.isFitMode()) Platform.runLater(zoom::fitToWindow);
        });
        refresh.run();
        dialog.setOnShown(event -> Platform.runLater(zoom::fitToWindow));
        dialog.showAndWait();
    }

    private Button viewerButton(String text, String tooltip) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("service-viewer-button");
        return button;
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
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
        loadMoreButton.setDisable(loading);
    }

    private void showError(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, rootPane.getScene().getWindow(), "Hata", message);
    }
}
