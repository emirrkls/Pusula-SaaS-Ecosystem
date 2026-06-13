package com.pusula.desktop.controller;

import com.pusula.desktop.util.UTF8Control;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.SearchSuggestion;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.AnimationHelper;
import com.pusula.desktop.util.NotificationHelper;
import com.pusula.desktop.util.ThemeHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.TextFields;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;

public class ServiceTicketController {

    enum TicketFilter {
        PENDING_ASSIGNMENT,
        OPENED_TODAY,
        ASSIGNED,
        IN_PROGRESS,
        CLOSED,
        ALL
    }

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @FXML
    private TextField txtSearch;
    @FXML
    private HBox filterChipContainer;
    @FXML
    private ListView<ServiceTicketDTO> ticketsListView;
    @FXML
    private Label resultCountLabel;
    @FXML
    private VBox emptyStateBox;
    @FXML
    private StackPane loadingOverlay;

    private final ObservableList<ServiceTicketDTO> allTicketsList = FXCollections.observableArrayList();
    private FilteredList<ServiceTicketDTO> filteredList;
    private SortedList<ServiceTicketDTO> sortedList;
    private ResourceBundle resourceBundle;

    private SearchSuggestion selectedSuggestion = null;
    private org.controlsfx.control.textfield.AutoCompletionBinding<SearchSuggestion> autoCompletionBinding;
    private TicketFilter currentFilter = TicketFilter.PENDING_ASSIGNMENT;
    private final ToggleGroup filterToggleGroup = new ToggleGroup();

    @FXML
    public void initialize() {
        resourceBundle = ResourceBundle.getBundle("i18n.messages",
                java.util.Locale.of("tr", "TR"), new UTF8Control());

        setupTicketList();
        setupFilterChips();
        setupSmartSearch();
        loadTickets();
    }

    private void setupFilterChips() {
        filterChipContainer.getChildren().clear();
        for (TicketFilter filter : TicketFilter.values()) {
            ToggleButton chip = new ToggleButton(filterLabel(filter));
            chip.getStyleClass().add("filter-chip");
            chip.setUserData(filter);
            chip.setToggleGroup(filterToggleGroup);
            chip.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    currentFilter = filter;
                    applySortOrder();
                    updateFilters();
                }
            });
            filterChipContainer.getChildren().add(chip);
        }

        if (filterChipContainer.getChildren().getFirst() instanceof ToggleButton firstChip) {
            firstChip.setSelected(true);
        }
    }

    private void setupTicketList() {
        filteredList = new FilteredList<>(allTicketsList, this::matchesCurrentFilter);
        sortedList = new SortedList<>(filteredList);
        applySortOrder();

        ticketsListView.setItems(sortedList);
        ticketsListView.setCellFactory(listView -> new TicketCardCell());

        sortedList.addListener((javafx.collections.ListChangeListener<ServiceTicketDTO>) change -> updateEmptyState());
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = sortedList.isEmpty();
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
        ticketsListView.setVisible(!empty);
        resultCountLabel.setText(sortedList.size() + " sonuç");
    }

    private class TicketCardCell extends ListCell<ServiceTicketDTO> {
        private final HBox card = new HBox(14);
        private final Region accent = new Region();
        private final VBox content = new VBox(6);
        private final HBox headerRow = new HBox(10);
        private final Label customerLabel = new Label();
        private final Label statusBadge = new Label();
        private final Label metaLabel = new Label();
        private final Label descriptionLabel = new Label();

        TicketCardCell() {
            accent.getStyleClass().add("ticket-card-accent");
            customerLabel.getStyleClass().add("ticket-card-customer");
            metaLabel.getStyleClass().add("ticket-card-meta");
            descriptionLabel.getStyleClass().add("ticket-card-description");
            descriptionLabel.setWrapText(true);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            headerRow.getChildren().addAll(customerLabel, spacer, statusBadge);

            content.getChildren().addAll(headerRow, descriptionLabel, metaLabel);
            card.getStyleClass().add("ticket-card");
            card.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(accent, content);
            HBox.setHgrow(content, Priority.ALWAYS);

            card.setOnMouseClicked(e -> {
                if (isEmpty() || getItem() == null) {
                    return;
                }
                ticketsListView.getSelectionModel().select(getItem());
                openTicketDetails(getItem());
                e.consume();
            });

            AnimationHelper.applyCardHover(card);
        }

        @Override
        protected void updateItem(ServiceTicketDTO ticket, boolean empty) {
            super.updateItem(ticket, empty);
            if (empty || ticket == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            customerLabel.setText(ticket.getCustomerName() != null ? ticket.getCustomerName() : "Müşteri #" + ticket.getId());
            descriptionLabel.setText(ticket.getDescription() != null && !ticket.getDescription().isBlank()
                    ? ticket.getDescription()
                    : "—");

            String tech = ticket.getAssignedTechnicianName();
            String techLine = tech != null && !tech.isBlank() ? tech : "Atanmadı";
            String dateLine = ticket.getScheduledDate() != null
                    ? ticket.getScheduledDate().format(DATE_FMT)
                    : "Tarih yok";
            metaLabel.setText("#" + ticket.getId() + "  ·  " + dateLine + "  ·  " + techLine);

            applyStatusBadge(statusBadge, ticket.getStatus());
            applyAccentColor(accent, ticket.getStatus());
            setGraphic(card);
        }

        private void applyStatusBadge(Label badge, String status) {
            badge.getStyleClass().removeIf(c -> c.startsWith("status-badge"));
            badge.getStyleClass().add("status-badge");
            String normalized = status != null ? status.trim().toUpperCase() : "";
            switch (normalized) {
                case "PENDING" -> badge.getStyleClass().add("status-badge-pending");
                case "ASSIGNED" -> badge.getStyleClass().add("status-badge-assigned");
                case "IN_PROGRESS" -> badge.getStyleClass().add("status-badge-in-progress");
                case "COMPLETED" -> badge.getStyleClass().add("status-badge-completed");
                case "CANCELLED" -> badge.getStyleClass().add("status-badge-cancelled");
                default -> badge.getStyleClass().add("status-badge-pending");
            }
            badge.setText(translateStatus(normalized));
        }

        private void applyAccentColor(Region region, String status) {
            String color = switch (status != null ? status.trim().toUpperCase() : "") {
                case "COMPLETED" -> "#0F766E";
                case "IN_PROGRESS" -> "#00B6EB";
                case "CANCELLED" -> "#B91C1C";
                case "ASSIGNED" -> "#6D28D9";
                default -> "#B45309";
            };
            region.setStyle("-fx-background-color: " + color + ";");
        }
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "PENDING" -> resourceBundle.getString("status.pending");
            case "IN_PROGRESS" -> resourceBundle.getString("status.in_progress");
            case "COMPLETED" -> resourceBundle.getString("status.completed");
            case "CANCELLED" -> resourceBundle.getString("status.cancelled");
            case "ASSIGNED" -> resourceBundle.getString("status.assigned");
            default -> status != null && !status.isBlank() ? status : "—";
        };
    }

    private String filterLabel(TicketFilter filter) {
        return switch (filter) {
            case PENDING_ASSIGNMENT -> resourceBundle.getString("filter.pending_assignment");
            case OPENED_TODAY -> resourceBundle.getString("filter.opened_today");
            case ASSIGNED -> resourceBundle.getString("filter.assigned");
            case IN_PROGRESS -> resourceBundle.getString("filter.in_progress");
            case CLOSED -> resourceBundle.getString("filter.closed");
            case ALL -> resourceBundle.getString("filter.all");
        };
    }

    private boolean matchesCurrentFilter(ServiceTicketDTO ticket) {
        if (!matchesStatusFilter(ticket, currentFilter)) {
            return false;
        }
        if (selectedSuggestion == null) {
            return true;
        }
        Long searchId = selectedSuggestion.getId();
        if (selectedSuggestion.getType() == SearchSuggestion.Type.CUSTOMER) {
            return searchId.equals(ticket.getCustomerId());
        }
        return searchId.equals(ticket.getAssignedTechnicianId());
    }

    private boolean matchesStatusFilter(ServiceTicketDTO ticket, TicketFilter filter) {
        String status = ticket.getStatus() != null ? ticket.getStatus().trim().toUpperCase() : "";
        return switch (filter) {
            case PENDING_ASSIGNMENT -> ticket.getAssignedTechnicianId() == null && "PENDING".equals(status);
            case OPENED_TODAY -> isTodayInBusinessZone(ticket.getCreatedAt());
            case ASSIGNED -> "ASSIGNED".equals(status);
            case IN_PROGRESS -> "IN_PROGRESS".equals(status);
            case CLOSED -> "COMPLETED".equals(status) || "CANCELLED".equals(status);
            case ALL -> true;
        };
    }

    private boolean isTodayInBusinessZone(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalDate businessToday = LocalDate.now(BUSINESS_ZONE);
        LocalDate ticketDate = dateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(BUSINESS_ZONE)
                .toLocalDate();
        return ticketDate.equals(businessToday);
    }

    private void applySortOrder() {
        if (currentFilter == TicketFilter.CLOSED) {
            sortedList.setComparator(
                    Comparator.comparing(ServiceTicketDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(ServiceTicketDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        } else {
            sortedList.setComparator(
                    Comparator.comparing(ServiceTicketDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(ServiceTicketDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        }
    }

    private void setupSmartSearch() {
        refreshSearchData();
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                selectedSuggestion = null;
                updateFilters();
            }
        });
    }

    public void refreshSearchData() {
        List<SearchSuggestion> suggestions = new ArrayList<>();
        CustomerApi customerApi = RetrofitClient.getClient().create(CustomerApi.class);
        customerApi.getAllCustomers().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (CustomerDTO customer : response.body()) {
                        suggestions.add(new SearchSuggestion(
                                customer.getId(),
                                customer.getName(),
                                customer.getPhone(),
                                SearchSuggestion.Type.CUSTOMER));
                    }
                    loadTechniciansAndBind(suggestions);
                }
            }

            @Override
            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {
                loadTechniciansAndBind(suggestions);
            }
        });
    }

    private void loadTechniciansAndBind(List<SearchSuggestion> suggestions) {
        UserApi userApi = RetrofitClient.getClient().create(UserApi.class);
        userApi.getAllUsers().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (UserDTO user : response.body()) {
                        if ("TECHNICIAN".equals(user.getRole())) {
                            suggestions.add(new SearchSuggestion(
                                    user.getId(),
                                    user.getFullName(),
                                    null,
                                    SearchSuggestion.Type.TECHNICIAN));
                        }
                    }
                }
                Platform.runLater(() -> bindAutoComplete(suggestions));
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
                Platform.runLater(() -> bindAutoComplete(suggestions));
            }
        });
    }

    private void bindAutoComplete(List<SearchSuggestion> suggestions) {
        if (autoCompletionBinding != null) {
            autoCompletionBinding.dispose();
        }

        javafx.util.Callback<org.controlsfx.control.textfield.AutoCompletionBinding.ISuggestionRequest, java.util.Collection<SearchSuggestion>> suggestionProvider = param -> {
            String userText = param.getUserText().toLowerCase();
            if (userText.isEmpty()) {
                return suggestions;
            }
            return suggestions.stream()
                    .filter(s -> s.getName().toLowerCase().contains(userText) ||
                            (s.getPhone() != null && s.getPhone().toLowerCase().contains(userText)))
                    .toList();
        };

        autoCompletionBinding = TextFields.bindAutoCompletion(txtSearch, suggestionProvider);
        autoCompletionBinding.setOnAutoCompleted(event -> {
            selectedSuggestion = event.getCompletion();
            updateFilters();
        });
    }

    private void updateFilters() {
        filteredList.setPredicate(this::matchesCurrentFilter);
        updateEmptyState();
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        loadingOverlay.setManaged(loading);
    }

    private void openTicketDetails(ServiceTicketDTO ticket) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/ticket_details.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            TicketDetailsController controller = loader.getController();
            controller.setTicket(ticket);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Servis Fişi - " + ticket.getId());
            javafx.scene.Scene scene = ThemeHelper.createDialogScene(root, 900, 700);
            stage.setScene(scene);
            if (ticketsListView.getScene() != null) {
                stage.initOwner(ticketsListView.getScene().getWindow());
            }
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();

            loadTickets();
        } catch (Exception e) {
            e.printStackTrace();
            NotificationHelper.showError("Detay açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadTickets();
        refreshSearchData();
        NotificationHelper.showInfo("Liste yenilendi");
    }

    @FXML
    private void handleCreateTicket() {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/ticket_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            TicketDialogController controller = loader.getController();
            controller.setOnSaveSuccess(this::loadTickets);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Yeni Servis Fişi");
            stage.setScene(ThemeHelper.createDialogScene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            NotificationHelper.showError("Dialog açılamadı: " + e.getMessage());
        }
    }

    private void loadTickets() {
        setLoading(true);
        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.getAllTickets().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<ServiceTicketDTO>> call, Response<List<ServiceTicketDTO>> response) {
                Platform.runLater(() -> {
                    setLoading(false);
                    if (response.isSuccessful() && response.body() != null) {
                        allTicketsList.setAll(response.body());
                        AnimationHelper.fadeInUp(ticketsListView, 0);
                    } else {
                        NotificationHelper.showError("Fişler yüklenemedi: " + response.code());
                    }
                });
            }

            @Override
            public void onFailure(Call<List<ServiceTicketDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    setLoading(false);
                    NotificationHelper.showError("Sunucuya bağlanılamadı: " + t.getMessage());
                });
            }
        });
    }
}
