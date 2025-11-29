package com.pusula.desktop.controller;

import com.pusula.desktop.entity.AuditLog;
import com.pusula.desktop.api.AuditLogApi;
import com.pusula.desktop.api.PageResponse;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private TableColumn<AuditLog, String> oldValueColumn;
    @FXML
    private TableColumn<AuditLog, String> newValueColumn;
    @FXML
    private TableColumn<AuditLog, String> descriptionColumn;

    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private ComboBox<String> actionTypeCombo;

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

    private int currentPage = 0;
    private int pageSize = 50;
    private int totalPages = 1;
    private long totalElements = 0;

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();
        loadAuditLogs();
    }

    private void setupTable() {
        // Configure columns
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        userColumn.setCellValueFactory(new PropertyValueFactory<>("userName"));
        actionColumn.setCellValueFactory(new PropertyValueFactory<>("actionType"));
        entityColumn.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        oldValueColumn.setCellValueFactory(new PropertyValueFactory<>("oldValue"));
        newValueColumn.setCellValueFactory(new PropertyValueFactory<>("newValue"));
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

        // User column with wrapping and tooltip
        userColumn.setCellFactory(column -> createWrappingCell());

        // Translate action types to Turkish
        actionColumn.setCellFactory(column -> new TableCell<AuditLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String translated = translateAction(item);
                    setText(translated);
                    // Only show tooltip for longer text
                    if (translated.length() > 30) {
                        Tooltip tooltip = new Tooltip(translated);
                        tooltip.setWrapText(true);
                        tooltip.setMaxWidth(300);
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
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
                    setTooltip(null);
                } else {
                    String translated = translateEntity(item);
                    setText(translated);
                    // Only show tooltip for longer text
                    if (translated.length() > 30) {
                        Tooltip tooltip = new Tooltip(translated);
                        tooltip.setWrapText(true);
                        tooltip.setMaxWidth(300);
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });

        // Old value, new value, and description with wrapping and tooltip
        oldValueColumn.setCellFactory(column -> createWrappingCell());
        newValueColumn.setCellFactory(column -> createWrappingCell());
        descriptionColumn.setCellFactory(column -> createWrappingCell());

        auditLogTable.setItems(auditLogs);
    }

    /**
     * Creates a table cell with text wrapping and tooltip support
     */
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
                    // Show tooltip for text longer than 30 characters
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

        // Setup action type combo
        ObservableList<String> actionTypes = FXCollections.observableArrayList(
                "Tümü", "CREATE", "UPDATE", "DELETE");
        actionTypeCombo.setItems(actionTypes);
        actionTypeCombo.setValue("Tümü");
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
        actionTypeCombo.setValue("Tümü");
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
        String actionType = actionTypeCombo.getValue();
        if ("Tümü".equals(actionType)) {
            actionType = null;
        }

        String startDate = startDatePicker.getValue() != null
                ? startDatePicker.getValue().atStartOfDay().toString()
                : null;
        String endDate = endDatePicker.getValue() != null
                ? endDatePicker.getValue().atTime(23, 59, 59).toString()
                : null;

        api.getAuditLogs(currentPage, pageSize, null, actionType, startDate, endDate)
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
        switch (action) {
            case "CREATE":
                return "Oluşturuldu";
            case "UPDATE":
                return "Güncellendi";
            case "DELETE":
                return "Silindi";
            default:
                return action;
        }
    }

    private String translateEntity(String entity) {
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
            case "DAILY_CLOSING":
                return "Günlük Kapanış";
            default:
                return entity;
        }
    }
}
