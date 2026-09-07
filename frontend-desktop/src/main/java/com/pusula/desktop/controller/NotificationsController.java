package com.pusula.desktop.controller;

import com.pusula.desktop.api.NotificationApi;
import com.pusula.desktop.dto.NotificationDTO;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.pusula.desktop.util.AlertHelper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class NotificationsController {
    @FXML private VBox notificationList;
    @FXML private Label emptyLabel;
    @FXML private Label summaryLabel;
    @FXML private Button markAllButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> readFilter;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ProgressIndicator loadingIndicator;
    private final NotificationApi api = RetrofitClient.getClient().create(NotificationApi.class);
    private MainDashboardController mainController;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final DateTimeFormatter sectionDateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("tr-TR"));
    private List<NotificationDTO> allItems = List.of();

    public void setMainController(MainDashboardController mainController) { this.mainController = mainController; }

    @FXML public void initialize() {
        readFilter.setItems(FXCollections.observableArrayList("Tümü", "Okunmamış", "Okundu"));
        readFilter.getSelectionModel().selectFirst();
        categoryFilter.setItems(FXCollections.observableArrayList("Tüm kategoriler"));
        categoryFilter.getSelectionModel().selectFirst();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> renderFiltered());
        readFilter.valueProperty().addListener((observable, oldValue, newValue) -> renderFiltered());
        categoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> renderFiltered());
        load();
    }

    @FXML private void load() {
        setLoading(true);
        api.list().enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<NotificationDTO>> call, Response<List<NotificationDTO>> response) {
                Platform.runLater(() -> {
                    setLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        allItems = response.body().stream()
                                .sorted(Comparator.comparing(NotificationDTO::getCreatedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                                .toList();
                        rebuildCategoryFilter();
                        renderFiltered();
                    } else {
                        allItems = List.of();
                        showLoadError("Bildirimler alınamadı (HTTP " + response.code() + ").");
                    }
                });
            }
            @Override public void onFailure(Call<List<NotificationDTO>> call, Throwable throwable) {
                Platform.runLater(() -> {
                    setLoading(false);
                    allItems = List.of();
                    showLoadError("Bildirimler yüklenemedi. Bağlantınızı kontrol edip yeniden deneyin.");
                });
            }
        });
    }

    private void rebuildCategoryFilter() {
        String selected = categoryFilter.getValue();
        List<String> values = new ArrayList<>();
        values.add("Tüm kategoriler");
        allItems.stream().map(NotificationDTO::getCategory).filter(Objects::nonNull)
                .filter(value -> !value.isBlank()).distinct().sorted().forEach(values::add);
        categoryFilter.setItems(FXCollections.observableArrayList(values));
        categoryFilter.getSelectionModel().select(values.contains(selected) ? selected : "Tüm kategoriler");
    }

    private void renderFiltered() {
        if (notificationList == null) return;
        String query = searchField.getText() == null ? ""
                : searchField.getText().trim().toLowerCase(Locale.forLanguageTag("tr-TR"));
        String readMode = readFilter.getValue();
        String category = categoryFilter.getValue();
        List<NotificationDTO> filtered = allItems.stream()
                .filter(item -> switch (readMode == null ? "Tümü" : readMode) {
                    case "Okunmamış" -> !item.isRead();
                    case "Okundu" -> item.isRead();
                    default -> true;
                })
                .filter(item -> category == null || "Tüm kategoriler".equals(category)
                        || category.equals(item.getCategory()))
                .filter(item -> query.isEmpty() || searchable(item.getTitle()).contains(query)
                        || searchable(item.getMessage()).contains(query))
                .toList();
        render(filtered);
    }

    private void render(List<NotificationDTO> items) {
        notificationList.getChildren().clear();
        long unread = allItems.stream().filter(item -> !item.isRead()).count();
        summaryLabel.setText(items.size() + " bildirim gösteriliyor"
                + (unread == 0 ? " · Tümü okundu" : " · " + unread + " okunmamış"));
        markAllButton.setVisible(unread > 0); markAllButton.setManaged(unread > 0);
        emptyLabel.setVisible(items.isEmpty()); emptyLabel.setManaged(items.isEmpty());
        String previousSection = null;
        for (NotificationDTO item : items) {
            String section = dateSection(item.getCreatedAt());
            if (!section.equals(previousSection)) {
                Label heading = new Label(section);
                heading.getStyleClass().add("notification-date-heading");
                notificationList.getChildren().add(heading);
                previousSection = section;
            }
            notificationList.getChildren().add(card(item));
        }
    }

    private String searchable(String value) {
        return value == null ? "" : value.toLowerCase(Locale.forLanguageTag("tr-TR"));
    }

    private String dateSection(LocalDateTime createdAt) {
        if (createdAt == null) return "Tarih belirtilmemiş";
        LocalDate date = createdAt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Bugün";
        if (date.equals(today.minusDays(1))) return "Dün";
        return date.format(sectionDateFormatter);
    }

    private HBox card(NotificationDTO item) {
        Label indicator = new Label();
        indicator.getStyleClass().add(item.isRead() ? "notification-dot-read" : "notification-dot");
        Label title = new Label(item.getTitle()); title.getStyleClass().add("notification-title");
        Label message = new Label(item.getMessage()); message.setWrapText(true); message.getStyleClass().add("notification-message");
        String date = item.getCreatedAt() == null ? "" : item.getCreatedAt().format(dateFormatter);
        Label metadata = new Label(date); metadata.getStyleClass().add("notification-meta");
        VBox text = new VBox(4, title, message, metadata); HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, indicator, text); row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().addAll("notification-card", item.isRead() ? "notification-read" : "notification-unread");
        row.setOnMouseClicked(event -> open(item));
        return row;
    }

    private void open(NotificationDTO item) {
        if (!item.isRead()) {
            api.markRead(item.getId()).enqueue(new Callback<>() {
                @Override public void onResponse(Call<NotificationDTO> call, Response<NotificationDTO> response) {
                    if (response.isSuccessful()) {
                        item.setRead(true);
                        Platform.runLater(() -> { renderFiltered(); refreshShell(); });
                    }
                }
                @Override public void onFailure(Call<NotificationDTO> call, Throwable throwable) {
                    Platform.runLater(() -> showError("Bildirim okundu olarak işaretlenemedi."));
                }
            });
        }
        if ("TICKET".equals(item.getReferenceType()) && mainController != null) {
            mainController.openTicketFromNotification(item.getReferenceId());
        } else if (item.getReferenceType() != null && item.getReferenceType().startsWith("NETWORK_") && mainController != null) {
            mainController.showServiceNetwork(item.getReferenceType(),item.getReferenceId());
        }
    }

    @FXML private void markAllRead() {
        api.markAllRead().enqueue(new Callback<>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                Platform.runLater(() -> { load(); refreshShell(); });
            }
            @Override public void onFailure(Call<Void> call, Throwable throwable) {
                Platform.runLater(() -> showError("Bildirimler güncellenemedi."));
            }
        });
    }

    private void refreshShell() { if (mainController != null) Platform.runLater(mainController::refreshNotificationBadge); }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        notificationList.setDisable(loading);
    }

    private void showLoadError(String message) {
        notificationList.getChildren().clear();
        emptyLabel.setText(message);
        emptyLabel.setVisible(true);
        emptyLabel.setManaged(true);
        summaryLabel.setText("Bildirimler alınamadı");
    }

    private void showError(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR,
                notificationList.getScene() != null ? notificationList.getScene().getWindow() : null,
                "Bildirimler", message);
    }
}
