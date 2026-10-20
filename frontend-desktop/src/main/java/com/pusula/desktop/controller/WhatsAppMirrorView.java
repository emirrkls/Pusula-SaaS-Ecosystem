package com.pusula.desktop.controller;

import com.pusula.desktop.api.WhatsAppIntegrationApi;
import com.pusula.desktop.dto.WhatsAppMirrorItemDTO;
import com.pusula.desktop.util.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import retrofit2.*;
import java.util.*;

/** Read-only projection: no customer creation, edits, sending, or consent mutations. */
public class WhatsAppMirrorView extends VBox {
    private final WhatsAppIntegrationApi api;
    private final String sessionToken = SessionManager.getToken();
    private final ListView<WhatsAppMirrorItemDTO> contacts = new ListView<>();
    private final ListView<WhatsAppMirrorItemDTO> messages = new ListView<>();
    private final Label status = new Label();
    private final Button previous = new Button("Önceki 100");
    private final Button next = new Button("Sonraki 100");
    private final Button previousContacts = new Button("Önceki kişiler");
    private final Button nextContacts = new Button("Sonraki kişiler");
    private Call<List<WhatsAppMirrorItemDTO>> contactCall;
    private Call<List<WhatsAppMirrorItemDTO>> messageCall;
    private int version;
    private int page;
    private int contactPage;
    private boolean closed;

    public WhatsAppMirrorView(WhatsAppIntegrationApi api) {
        this.api = api;
        setSpacing(12); setStyle("-fx-padding: 18;");
        Label note = new Label("Salt okunur WhatsApp aynası. Müşteri kaydı veya mesaj izni oluşturmaz. Kayıtlar sunucunun saklama süresi sonunda kaldırılır; medya indirilmez.");
        note.setWrapText(true);
        contacts.setPrefWidth(280);
        contacts.setPlaceholder(new Label("Kişi kaydı yok. Tüm mesajları görüntüleyebilirsiniz."));
        messages.setPlaceholder(new Label("Henüz eşitlenen mesaj yok."));
        contacts.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(WhatsAppMirrorItemDTO value, boolean empty) {
                super.updateItem(value, empty); setText(empty || value == null ? null : value.contactLabel());
            }
        });
        messages.setCellFactory(list -> new ListCell<>() {
            private final Label label = new Label();
            { label.setWrapText(true); label.maxWidthProperty().bind(messages.widthProperty().subtract(40)); }
            @Override protected void updateItem(WhatsAppMirrorItemDTO value, boolean empty) {
                super.updateItem(value, empty);
                label.setText(empty || value == null ? "" : value.messageLabel());
                setGraphic(empty || value == null ? null : label);
            }
        });
        VBox contactPane = new VBox(8, new Label("Kişiler"), contacts, new HBox(8, previousContacts, nextContacts));
        VBox.setVgrow(contacts, Priority.ALWAYS);
        previousContacts.setOnAction(e -> { if (contactPage > 0) { contactPage--; loadContacts(); } });
        nextContacts.setOnAction(e -> { contactPage++; loadContacts(); });
        SplitPane pane = new SplitPane(contactPane, messages); pane.setDividerPositions(.30);
        VBox.setVgrow(pane, Priority.ALWAYS);
        Button all = new Button("Tüm mesajlar");
        all.setOnAction(e -> { contacts.getSelectionModel().clearSelection(); page = 0; loadMessages(); });
        Button refresh = new Button("Yenile");
        refresh.setOnAction(e -> { page = 0; contactPage = 0; loadContacts(); loadMessages(); });
        previous.setOnAction(e -> { if (page > 0) { page--; loadMessages(); } });
        next.setOnAction(e -> { page++; loadMessages(); });
        contacts.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { page = 0; loadMessages(); });
        getChildren().addAll(note, new HBox(8, all, refresh, previous, next), status, pane);
        loadContacts(); loadMessages();
    }

    private boolean validSession() {
        if (closed) return false;
        if (!Objects.equals(sessionToken, SessionManager.getToken())) {
            contacts.getItems().clear(); messages.getItems().clear(); close();
            status.setText("Oturum değişti. Bu pencereyi kapatıp yeniden açın."); return false;
        }
        return true;
    }
    private void loadContacts() {
        if (!validSession()) return;
        if (contactCall != null) contactCall.cancel();
        previousContacts.setDisable(true); nextContacts.setDisable(true);
        contactCall = api.contacts(contactPage);
        contactCall.enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<WhatsAppMirrorItemDTO>> call, Response<List<WhatsAppMirrorItemDTO>> response) {
                Platform.runLater(() -> {
                    if (call != contactCall || !validSession()) return;
                    previousContacts.setDisable(contactPage == 0);
                    if (response.isSuccessful() && response.body() != null) {
                        contacts.getItems().setAll(response.body());
                        nextContacts.setDisable(response.body().size() < 100 || contactPage >= 1000);
                    }
                    else status.setText("Kişiler alınamadı. Yenile ile tekrar deneyebilirsiniz.");
                });
            }
            @Override public void onFailure(Call<List<WhatsAppMirrorItemDTO>> call, Throwable error) {
                Platform.runLater(() -> { if (!call.isCanceled() && validSession()) status.setText("Kişiler alınamadı."); });
            }
        });
    }
    private void loadMessages() {
        if (!validSession()) return;
        if (messageCall != null) messageCall.cancel();
        int requestVersion = ++version;
        var selected = contacts.getSelectionModel().getSelectedItem();
        messageCall = api.messages(selected == null ? null : selected.getPeerKey(), page);
        status.setText("Mesajlar yükleniyor…"); previous.setDisable(true); next.setDisable(true);
        messageCall.enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<WhatsAppMirrorItemDTO>> call, Response<List<WhatsAppMirrorItemDTO>> response) {
                Platform.runLater(() -> {
                    if (!validSession() || requestVersion != version) return;
                    previous.setDisable(page == 0);
                    if (response.isSuccessful() && response.body() != null) {
                        messages.getItems().setAll(response.body());
                        next.setDisable(response.body().size() < 100 || page >= 1000);
                        status.setText("Sayfa " + (page+1) + " — salt okunur");
                    } else status.setText("Mesajlar alınamadı. Yenile ile tekrar deneyebilirsiniz.");
                });
            }
            @Override public void onFailure(Call<List<WhatsAppMirrorItemDTO>> call, Throwable error) {
                Platform.runLater(() -> {
                    if (!call.isCanceled() && validSession() && requestVersion == version) {
                        status.setText("Mesajlar alınamadı. Yenile ile tekrar deneyebilirsiniz.");
                        previous.setDisable(page == 0);
                    }
                });
            }
        });
    }
    public void close() {
        closed = true; version++;
        if (contactCall != null) contactCall.cancel();
        if (messageCall != null) messageCall.cancel();
    }
}
