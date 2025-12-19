package com.pusula.desktop.controller;

import com.pusula.desktop.entity.AuditLog;
import com.pusula.desktop.api.AuditLogApi;
import com.pusula.desktop.api.PageResponse;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ActivityLogController {

    @FXML
    private TableView<AuditLog> auditLogTable;
    @FXML
    private TableColumn<AuditLog, LocalDateTime> timestampColumn;
    @FXML
    private TableColumn<AuditLog, String> userColumn;
    @FXML
    private TableColumn<AuditLog, String> actionColumn;
    @FXML
    private TableColumn<AuditLog, String> entityColumn;
    @FXML
    private TableColumn<AuditLog, String> descriptionColumn;
    @FXML
    private TableColumn<AuditLog, Void> actionsColumn;

    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private ComboBox<ActionType> actionTypeCombo;
    @FXML
    private ComboBox<UserDTO> userCombo;

    @FXML
    private Label totalCountLabel;
    @FXML
    private Label pageLabel;
    @FXML
    private Button btnPrevious;
    @FXML
    private Button btnNext;

    private final ObservableList<AuditLog> auditLogs = FXCollections.observableArrayList();
    private final AuditLogApi api = RetrofitClient.getClient().create(AuditLogApi.class);
    private final UserApi userApi = RetrofitClient.getClient().create(UserApi.class);

    private int currentPage = 0;
    private int pageSize = 50;
    private int totalPages = 1;
    private long totalElements = 0;

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();
        loadUsers();
        loadAuditLogs();
    }

    private void setupTable() {
        // Configure columns
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        userColumn.setCellValueFactory(new PropertyValueFactory<>("userName"));
        actionColumn.setCellValueFactory(new PropertyValueFactory<>("actionType"));
        entityColumn.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Format timestamp column
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        timestampColumn.setCellFactory(column -> new TableCell<AuditLog, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatter.format(item));
                }
            }
        });

        // Translate action types to Turkish
        actionColumn.setCellFactory(column -> new TableCell<AuditLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(translateAction(item));
                }
            }
        });

        // Translate entity types to Turkish
        entityColumn.setCellFactory(column -> new TableCell<AuditLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(translateEntity(item));
                }
            }
        });

        // Description with wrapping
        descriptionColumn.setCellFactory(column -> createWrappingCell());

        // Actions column with Details button
        actionsColumn.setCellFactory(column -> new TableCell<AuditLog, Void>() {
            private final Button detailBtn = new Button("Detay");

            {
                detailBtn.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px;");
                detailBtn.setOnAction(event -> {
                    AuditLog log = getTableView().getItems().get(getIndex());
                    showDetailDialog(log);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(detailBtn);
                }
            }
        });

        auditLogTable.setItems(auditLogs);
    }

    private void showDetailDialog(AuditLog log) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Aktivite Detayı");
        dialog.setHeaderText(translateAction(log.getActionType()) + " - " + translateEntity(log.getEntityType()));

        // Create content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        int row = 0;
        grid.add(new Label("Tarih:"), 0, row);
        grid.add(new Label(log.getTimestamp() != null ? formatter.format(log.getTimestamp()) : "-"), 1, row++);

        grid.add(new Label("Kullanıcı:"), 0, row);
        grid.add(new Label(log.getUserName()), 1, row++);

        grid.add(new Label("İşlem:"), 0, row);
        grid.add(new Label(translateAction(log.getActionType())), 1, row++);

        grid.add(new Label("Varlık:"), 0, row);
        grid.add(new Label(translateEntity(log.getEntityType()) + " (ID: " + log.getEntityId() + ")"), 1, row++);

        grid.add(new Label("Açıklama:"), 0, row);
        Label descLabel = new Label(log.getDescription());
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(400);
        grid.add(descLabel, 1, row++);

        grid.add(new Label("IP Adresi:"), 0, row);
        grid.add(new Label(log.getIpAddress() != null ? log.getIpAddress() : "-"), 1, row++);

        // Old/New value comparison
        if ((log.getOldValue() != null && !log.getOldValue().isEmpty()) ||
                (log.getNewValue() != null && !log.getNewValue().isEmpty())) {

            grid.add(new Separator(), 0, row++, 2, 1);
            grid.add(new Label("Değişiklik Karşılaştırması:"), 0, row++, 2, 1);

            // Side by side TextAreas
            VBox oldBox = new VBox(5);
            Label oldLabel = new Label("Eski Değer:");
            oldLabel.setStyle("-fx-font-weight: bold;");
            TextArea oldArea = new TextArea(formatJson(log.getOldValue()));
            oldArea.setEditable(false);
            oldArea.setPrefRowCount(8);
            oldArea.setPrefWidth(300);
            oldArea.setWrapText(true);
            oldBox.getChildren().addAll(oldLabel, oldArea);
            VBox.setVgrow(oldArea, Priority.ALWAYS);

            VBox newBox = new VBox(5);
            Label newLabel = new Label("Yeni Değer:");
            newLabel.setStyle("-fx-font-weight: bold;");
            TextArea newArea = new TextArea(formatJson(log.getNewValue()));
            newArea.setEditable(false);
            newArea.setPrefRowCount(8);
            newArea.setPrefWidth(300);
            newArea.setWrapText(true);
            newBox.getChildren().addAll(newLabel, newArea);
            VBox.setVgrow(newArea, Priority.ALWAYS);

            grid.add(oldBox, 0, row);
            grid.add(newBox, 1, row++);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(700);

        dialog.showAndWait();
    }

    private String formatJson(String json) {
        if (json == null || json.isEmpty()) {
            return "(boş)";
        }

        // Translate common JSON property names to Turkish
        String translated = json
                // Inventory fields
                .replace("\"buyPrice\"", "\"Alış Fiyatı\"")
                .replace("\"sellPrice\"", "\"Satış Fiyatı\"")
                .replace("\"quantity\"", "\"Miktar\"")
                .replace("\"criticalLevel\"", "\"Kritik Seviye\"")
                .replace("\"partName\"", "\"Parça Adı\"")
                .replace("\"brand\"", "\"Marka\"")
                .replace("\"category\"", "\"Kategori\"")
                // Expense fields
                .replace("\"amount\"", "\"Tutar\"")
                .replace("\"description\"", "\"Açıklama\"")
                .replace("\"date\"", "\"Tarih\"")
                // Customer fields
                .replace("\"name\"", "\"Ad\"")
                .replace("\"phone\"", "\"Telefon\"")
                .replace("\"address\"", "\"Adres\"")
                .replace("\"email\"", "\"E-posta\"")
                // Ticket fields
                .replace("\"status\"", "\"Durum\"")
                .replace("\"notes\"", "\"Notlar\"")
                .replace("\"scheduledDate\"", "\"Planlanan Tarih\"")
                .replace("\"collectedAmount\"", "\"Tahsil Edilen\"")
                // Common fields
                .replace("\"id\"", "\"ID\"")
                .replace("\"companyId\"", "\"Şirket ID\"")
                .replace("\"createdAt\"", "\"Oluşturulma\"")
                .replace("\"updatedAt\"", "\"Güncellenme\"");

        // Simple pretty print (replace commas with newlines for readability)
        return translated.replace(",", ",\n").replace("{", "{\n").replace("}", "\n}");
    }

    private TableCell<AuditLog, String> createWrappingCell() {
        return new TableCell<AuditLog, String>() {
            private final Label label = new Label();

            {
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
                setGraphic(label);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    label.setText(null);
                    setTooltip(null);
                } else {
                    label.setText(item);
                    if (item.length() > 30) {
                        Tooltip tooltip = new Tooltip(item);
                        tooltip.setWrapText(true);
                        tooltip.setMaxWidth(400);
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        };
    }

    private void setupFilters() {
        // Set default date range (last 30 days)
        endDatePicker.setValue(LocalDate.now());
        startDatePicker.setValue(LocalDate.now().minusDays(30));

        // Setup action type combo with Turkish labels
        ObservableList<ActionType> actionTypes = FXCollections.observableArrayList(
                new ActionType(null, "Tümü"),
                new ActionType("CREATE", "Oluşturma"),
                new ActionType("UPDATE", "Güncelleme"),
                new ActionType("DELETE", "Silme"),
                new ActionType("LOGIN_SUCCESS", "Giriş Başarılı"),
                new ActionType("LOGIN_FAILED", "Giriş Başarısız"),
                new ActionType("SALE", "Satış"),
                new ActionType("CANCEL", "İptal"));
        actionTypeCombo.setItems(actionTypes);
        actionTypeCombo.setValue(actionTypes.get(0)); // Default to "Tümü"

        actionTypeCombo.setConverter(new javafx.util.StringConverter<ActionType>() {
            @Override
            public String toString(ActionType type) {
                return type != null ? type.displayName : "Tümü";
            }

            @Override
            public ActionType fromString(String string) {
                return null;
            }
        });

        // Setup user combo converter
        userCombo.setConverter(new javafx.util.StringConverter<UserDTO>() {
            @Override
            public String toString(UserDTO user) {
                if (user == null)
                    return "Tümü";
                return user.getFullName() != null ? user.getFullName() : user.getUsername();
            }

            @Override
            public UserDTO fromString(String string) {
                return null;
            }
        });
    }

    // Inner class to hold action type with display name
    private static class ActionType {
        final String apiValue;
        final String displayName;

        ActionType(String apiValue, String displayName) {
            this.apiValue = apiValue;
            this.displayName = displayName;
        }
    }

    private void loadUsers() {
        userApi.getAllUsers().enqueue(new Callback<List<UserDTO>>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        ObservableList<UserDTO> users = FXCollections.observableArrayList();
                        users.add(null); // "Tümü" option
                        users.addAll(response.body());
                        userCombo.setItems(users);
                        userCombo.setValue(null); // Default to "Tümü"
                    }
                });
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
                System.err.println("Failed to load users: " + t.getMessage());
            }
        });
    }

    @FXML
    private void loadAuditLogs() {
        currentPage = 0;
        fetchAuditLogs();
    }

    @FXML
    private void applyFilters() {
        currentPage = 0;
        fetchAuditLogs();
    }

    @FXML
    private void resetFilters() {
        startDatePicker.setValue(LocalDate.now().minusDays(30));
        endDatePicker.setValue(LocalDate.now());
        actionTypeCombo.setValue(actionTypeCombo.getItems().get(0)); // Reset to "Tümü"
        userCombo.setValue(null);
        loadAuditLogs();
    }

    @FXML
    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            fetchAuditLogs();
        }
    }

    @FXML
    private void nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            fetchAuditLogs();
        }
    }

    private void fetchAuditLogs() {
        ActionType selectedAction = actionTypeCombo.getValue();
        String actionType = (selectedAction != null) ? selectedAction.apiValue : null;

        Long userId = null;
        UserDTO selectedUser = userCombo.getValue();
        if (selectedUser != null) {
            userId = selectedUser.getId();
        }

        String startDate = startDatePicker.getValue() != null
                ? startDatePicker.getValue().atStartOfDay().toString()
                : null;
        String endDate = endDatePicker.getValue() != null
                ? endDatePicker.getValue().atTime(23, 59, 59).toString()
                : null;

        api.getAuditLogs(currentPage, pageSize, userId, actionType, startDate, endDate)
                .enqueue(new Callback<PageResponse<AuditLog>>() {
                    @Override
                    public void onResponse(Call<PageResponse<AuditLog>> call,
                            Response<PageResponse<AuditLog>> response) {
                        Platform.runLater(() -> {
                            if (response.isSuccessful() && response.body() != null) {
                                PageResponse<AuditLog> page = response.body();
                                auditLogs.clear();
                                auditLogs.addAll(page.getContent());

                                totalPages = page.getTotalPages();
                                totalElements = page.getTotalElements();

                                updatePaginationControls();
                                totalCountLabel.setText("Toplam: " + totalElements);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<PageResponse<AuditLog>> call, Throwable t) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Hata");
                            alert.setHeaderText("Veri yüklenemedi");
                            alert.setContentText(t.getMessage());
                            alert.show();
                        });
                    }
                });
    }

    private void updatePaginationControls() {
        pageLabel.setText(String.format("Sayfa %d / %d", currentPage + 1, Math.max(1, totalPages)));
        btnPrevious.setDisable(currentPage == 0);
        btnNext.setDisable(currentPage >= totalPages - 1);
    }

    private String translateAction(String action) {
        if (action == null)
            return "-";
        switch (action) {
            case "CREATE":
                return "Oluşturuldu";
            case "UPDATE":
                return "Güncellendi";
            case "DELETE":
                return "Silindi";
            case "LOGIN_SUCCESS":
                return "Giriş Başarılı";
            case "LOGIN_FAILED":
                return "Giriş Başarısız";
            case "USER_REGISTERED":
                return "Kayıt Oldu";
            case "SALE":
                return "Satış";
            case "CANCEL":
                return "İptal Edildi";
            default:
                return action;
        }
    }

    private String translateEntity(String entity) {
        if (entity == null)
            return "-";
        switch (entity) {
            case "EXPENSE":
                return "Gider";
            case "CUSTOMER":
                return "Müşteri";
            case "TICKET":
                return "Servis İşi";
            case "INVENTORY":
                return "Envanter";
            case "USER":
                return "Kullanıcı";
            case "COMPANY":
                return "Şirket";
            case "FIXED_EXPENSE":
                return "Sabit Gider";
            case "DAY_CLOSING":
                return "Gün Kapanış";
            case "DEVICE":
                return "Cihaz";
            case "AUTH":
                return "Kimlik Doğrulama";
            default:
                return entity;
        }
    }
}
