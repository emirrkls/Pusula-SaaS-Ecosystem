package com.pusula.desktop.controller;

import com.pusula.desktop.api.NotificationApi;
import com.pusula.desktop.dto.NotificationDTO;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NotificationsController {
    @FXML private VBox notificationList;
    @FXML private Label emptyLabel;
    @FXML private Label summaryLabel;
    @FXML private Button markAllButton;
    private final NotificationApi api = RetrofitClient.getClient().create(NotificationApi.class);
    private MainDashboardController mainController;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public void setMainController(MainDashboardController mainController) { this.mainController = mainController; }

    @FXML public void initialize() { load(); }

    private void load() {
        api.list().enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<NotificationDTO>> call, Response<List<NotificationDTO>> response) {
                Platform.runLater(() -> render(response.isSuccessful() && response.body() != null ? response.body() : List.of()));
            }
            @Override public void onFailure(Call<List<NotificationDTO>> call, Throwable throwable) {
                Platform.runLater(() -> { emptyLabel.setText("Bildirimler yüklenemedi."); emptyLabel.setVisible(true); });
            }
        });
    }

    private void render(List<NotificationDTO> items) {
        notificationList.getChildren().clear();
        long unread = items.stream().filter(item -> !item.isRead()).count();
        summaryLabel.setText(unread == 0 ? "Tüm bildirimler okundu" : unread + " okunmamış bildirim");
        markAllButton.setVisible(unread > 0); markAllButton.setManaged(unread > 0);
        emptyLabel.setVisible(items.isEmpty()); emptyLabel.setManaged(items.isEmpty());
        items.forEach(item -> notificationList.getChildren().add(card(item)));
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
                @Override public void onResponse(Call<NotificationDTO> call, Response<NotificationDTO> response) { refreshShell(); }
                @Override public void onFailure(Call<NotificationDTO> call, Throwable throwable) { }
            });
        }
        if ("TICKET".equals(item.getReferenceType()) && mainController != null) {
            mainController.openTicketFromNotification(item.getReferenceId());
        }
    }

    @FXML private void markAllRead() {
        api.markAllRead().enqueue(new Callback<>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) { Platform.runLater(() -> { load(); refreshShell(); }); }
            @Override public void onFailure(Call<Void> call, Throwable throwable) { }
        });
    }

    private void refreshShell() { if (mainController != null) Platform.runLater(mainController::refreshNotificationBadge); }
}
