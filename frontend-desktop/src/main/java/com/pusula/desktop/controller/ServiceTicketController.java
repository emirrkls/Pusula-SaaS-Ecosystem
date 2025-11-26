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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.controlsfx.control.textfield.TextFields;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServiceTicketController {

    @FXML
    private TextField txtSearch;
    @FXML
    private TableView<ServiceTicketDTO> activeTicketsTable;
    @FXML
    private TableColumn<ServiceTicketDTO, Long> colActiveId;
    @FXML
    private TableColumn<ServiceTicketDTO, String> colActiveDescription;
    @FXML
    private TableColumn<ServiceTicketDTO, String> colActiveStatus;
    @FXML
    private TableColumn<ServiceTicketDTO, LocalDateTime> colActiveDate;

    @FXML
    private TableView<ServiceTicketDTO> historyTicketsTable;
    @FXML
    private TableColumn<ServiceTicketDTO, Long> colHistoryId;
    @FXML
    private TableColumn<ServiceTicketDTO, String> colHistoryDescription;
    @FXML
    private TableColumn<ServiceTicketDTO, String> colHistoryStatus;
    @FXML
    private TableColumn<ServiceTicketDTO, LocalDateTime> colHistoryDate;

    private final ObservableList<ServiceTicketDTO> allTicketsList = FXCollections.observableArrayList();
    private FilteredList<ServiceTicketDTO> activeFilteredList;
    private FilteredList<ServiceTicketDTO> historyFilteredList;

    private SearchSuggestion selectedSuggestion = null;
    private org.controlsfx.control.textfield.AutoCompletionBinding<SearchSuggestion> autoCompletionBinding;
    private boolean isAutoCompleting = false;

    @FXML
    public void initialize() {
        // Setup Active Table
        colActiveId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colActiveDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colActiveStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colActiveDate.setCellValueFactory(new PropertyValueFactory<>("scheduledDate"));

        // Setup History Table
        colHistoryId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colHistoryDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colHistoryStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colHistoryDate.setCellValueFactory(new PropertyValueFactory<>("scheduledDate"));

        // Load resource bundle for translations
        java.util.ResourceBundle resourceBundle = java.util.ResourceBundle.getBundle("i18n.messages",
                java.util.Locale.of("tr", "TR"), new UTF8Control());

        // Set custom cell factory for Active Status column
        colActiveStatus.setCellFactory(column -> new TableCell<ServiceTicketDTO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                // Translate the status
                String translatedStatus = switch (item) {
                    case "PENDING" -> resourceBundle.getString("status.pending");
                    case "IN_PROGRESS" -> resourceBundle.getString("status.in_progress");
                    case "COMPLETED" -> resourceBundle.getString("status.completed");
                    case "CANCELLED" -> resourceBundle.getString("status.cancelled");
                    case "ASSIGNED" -> resourceBundle.getString("status.assigned");
                    default -> item;
                };

                setText(translatedStatus);

                // Set color based on status
                switch (item) {
                    case "COMPLETED":
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        break;
                    case "IN_PROGRESS":
                        setStyle("-fx-text-fill: #2980b9; -fx-font-weight: bold;");
                        break;
                    case "CANCELLED":
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        break;
                    case "PENDING":
                        setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        break;
                    case "ASSIGNED":
                        setStyle("-fx-text-fill: #8e44ad; -fx-font-weight: bold;");
                        break;
                    default:
                        setStyle("");
                }
            }
        });

        // Set custom cell factory for History Status column
        colHistoryStatus.setCellFactory(column -> new TableCell<ServiceTicketDTO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                // Translate the status
                String translatedStatus = switch (item) {
                    case "PENDING" -> resourceBundle.getString("status.pending");
                    case "IN_PROGRESS" -> resourceBundle.getString("status.in_progress");
                    case "COMPLETED" -> resourceBundle.getString("status.completed");
                    case "CANCELLED" -> resourceBundle.getString("status.cancelled");
                    case "ASSIGNED" -> resourceBundle.getString("status.assigned");
                    default -> item;
                };

                setText(translatedStatus);

                // Set color based on status
                switch (item) {
                    case "COMPLETED":
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        break;
                    case "IN_PROGRESS":
                        setStyle("-fx-text-fill: #2980b9; -fx-font-weight: bold;");
                        break;
                    case "CANCELLED":
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        break;
                    case "PENDING":
                        setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        break;
                    case "ASSIGNED":
                        setStyle("-fx-text-fill: #8e44ad; -fx-font-weight: bold;");
                        break;
                    default:
                        setStyle("");
                }
            }
        });

        // Create filtered lists
        activeFilteredList = new FilteredList<>(allTicketsList, ticket -> "PENDING".equals(ticket.getStatus())
                || "IN_PROGRESS".equals(ticket.getStatus()) || "ASSIGNED".equals(ticket.getStatus()));

        historyFilteredList = new FilteredList<>(allTicketsList,
                ticket -> "COMPLETED".equals(ticket.getStatus()) || "CANCELLED".equals(ticket.getStatus()));

        activeTicketsTable.setItems(activeFilteredList);
        historyTicketsTable.setItems(historyFilteredList);

        // Setup auto-complete search
        setupSmartSearch();

        // Setup double click listeners
        setupDoubleClickListener(activeTicketsTable);
        setupDoubleClickListener(historyTicketsTable);

        // Setup row factories for conditional styling
        activeTicketsTable.setRowFactory(tv -> createStyledRow());
        historyTicketsTable.setRowFactory(tv -> createStyledRow());

        loadTickets();
    }

    private void setupSmartSearch() {
        // Load search suggestions
        refreshSearchData();

        // Listen for manual text changes to clear filters
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                selectedSuggestion = null;
                isAutoCompleting = false;
                updateFilters();
            }
            // Don't filter tickets while typing - only when selecting from dropdown
        });
    }

    private void applyTextSearch(String searchText) {
        activeFilteredList.setPredicate(ticket -> {
            boolean matchesStatus = "PENDING".equals(ticket.getStatus()) ||
                    "IN_PROGRESS".equals(ticket.getStatus()) ||
                    "ASSIGNED".equals(ticket.getStatus());
            if (!matchesStatus)
                return false;

            // Search in description or status
            return ticket.getDescription().toLowerCase().contains(searchText) ||
                    ticket.getStatus().toLowerCase().contains(searchText);
        });

        historyFilteredList.setPredicate(ticket -> {
            boolean matchesStatus = "COMPLETED".equals(ticket.getStatus()) || "CANCELLED".equals(ticket.getStatus());
            if (!matchesStatus)
                return false;

            return ticket.getDescription().toLowerCase().contains(searchText) ||
                    ticket.getStatus().toLowerCase().contains(searchText);
        });
    }

    public void refreshSearchData() {
        List<SearchSuggestion> suggestions = new ArrayList<>();

        // Load customers
        CustomerApi customerApi = RetrofitClient.getClient().create(CustomerApi.class);
        customerApi.getAllCustomers().enqueue(new Callback<List<CustomerDTO>>() {
            @Override
            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (CustomerDTO customer : response.body()) {
                        suggestions.add(new SearchSuggestion(
                                customer.getId(),
                                customer.getName(),
                                customer.getPhone(), // Include phone number
                                SearchSuggestion.Type.CUSTOMER));
                    }
                    loadTechniciansAndBind(suggestions);
                }
            }

            @Override
            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {
                // Continue with what we have
                loadTechniciansAndBind(suggestions);
            }
        });
    }

    private void loadTechniciansAndBind(List<SearchSuggestion> suggestions) {
        UserApi userApi = RetrofitClient.getClient().create(UserApi.class);
        userApi.getAllUsers().enqueue(new Callback<List<UserDTO>>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (UserDTO user : response.body()) {
                        if ("TECHNICIAN".equals(user.getRole())) {
                            suggestions.add(new SearchSuggestion(
                                    user.getId(),
                                    user.getFullName(),
                                    null, // Technicians don't have phone in suggestions
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

        // Custom callback to filter suggestions by name OR phone
        javafx.util.Callback<org.controlsfx.control.textfield.AutoCompletionBinding.ISuggestionRequest, java.util.Collection<SearchSuggestion>> suggestionProvider = param -> {
            String userText = param.getUserText().toLowerCase();
            if (userText.isEmpty()) {
                return suggestions;
            }

            return suggestions.stream()
                    .filter(s -> s.getName().toLowerCase().contains(userText) ||
                            (s.getPhone() != null && s.getPhone().toLowerCase().contains(userText)))
                    .collect(java.util.stream.Collectors.toList());
        };

        autoCompletionBinding = TextFields.bindAutoCompletion(txtSearch, suggestionProvider);
        autoCompletionBinding.setOnAutoCompleted(event -> {
            isAutoCompleting = true;
            selectedSuggestion = event.getCompletion();
            updateFilters();
            // Reset flag after a short delay
            Platform.runLater(() -> isAutoCompleting = false);
        });
    }

    private void updateFilters() {
        if (selectedSuggestion == null) {
            // Show all tickets
            activeFilteredList.setPredicate(ticket -> "PENDING".equals(ticket.getStatus())
                    || "IN_PROGRESS".equals(ticket.getStatus()) || "ASSIGNED".equals(ticket.getStatus()));

            historyFilteredList.setPredicate(
                    ticket -> "COMPLETED".equals(ticket.getStatus()) || "CANCELLED".equals(ticket.getStatus()));
        } else {
            // Filter by selected customer or technician
            Long searchId = selectedSuggestion.getId();
            SearchSuggestion.Type searchType = selectedSuggestion.getType();

            activeFilteredList.setPredicate(ticket -> {
                boolean matchesStatus = "PENDING".equals(ticket.getStatus()) ||
                        "IN_PROGRESS".equals(ticket.getStatus()) ||
                        "ASSIGNED".equals(ticket.getStatus());

                if (!matchesStatus)
                    return false;

                if (searchType == SearchSuggestion.Type.CUSTOMER) {
                    return searchId.equals(ticket.getCustomerId());
                } else {
                    return searchId.equals(ticket.getAssignedTechnicianId());
                }
            });

            historyFilteredList.setPredicate(ticket -> {
                boolean matchesStatus = "COMPLETED".equals(ticket.getStatus())
                        || "CANCELLED".equals(ticket.getStatus());

                if (!matchesStatus)
                    return false;

                if (searchType == SearchSuggestion.Type.CUSTOMER) {
                    return searchId.equals(ticket.getCustomerId());
                } else {
                    return searchId.equals(ticket.getAssignedTechnicianId());
                }
            });
        }
    }

    private TableRow<ServiceTicketDTO> createStyledRow() {
        TableRow<ServiceTicketDTO> row = new TableRow<ServiceTicketDTO>() {
            @Override
            protected void updateItem(ServiceTicketDTO ticket, boolean empty) {
                super.updateItem(ticket, empty);

                if (empty || ticket == null) {
                    setStyle("");
                } else {
                    String status = ticket.getStatus();
                    switch (status) {
                        case "COMPLETED":
                            setStyle("-fx-background-color: #d5f4e6; -fx-text-fill: #27ae60;");
                            break;
                        case "IN_PROGRESS":
                            setStyle("-fx-background-color: #d6eaf8; -fx-text-fill: #2980b9;");
                            break;
                        case "PENDING":
                            setStyle("-fx-background-color: #fef5e7; -fx-text-fill: #f39c12;");
                            break;
                        case "ASSIGNED":
                            setStyle("-fx-background-color: #e8daef; -fx-text-fill: #8e44ad;");
                            break;
                        case "CANCELLED":
                            setStyle("-fx-background-color: #fadbd8; -fx-text-fill: #e74c3c;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        };
        return row;
    }

    private void setupDoubleClickListener(TableView<ServiceTicketDTO> table) {
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                openTicketDetails(table.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void openTicketDetails(ServiceTicketDTO ticket) {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/ticket_details.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            TicketDetailsController controller = loader.getController();
            controller.setTicket(ticket);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Ticket Details - " + ticket.getId());
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();

            // Refresh list after closing details
            loadTickets();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, activeTicketsTable.getScene().getWindow(),
                    "Error", "Could not open details: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadTickets();
        refreshSearchData();
    }

    @FXML
    private void handleCreateTicket() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/ticket_dialog.fxml"));
            javafx.scene.Parent root = loader.load();

            TicketDialogController controller = loader.getController();
            controller.setOnSaveSuccess(this::loadTickets);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Create New Ticket");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, activeTicketsTable.getScene().getWindow(),
                    "Error", "Could not open dialog: " + e.getMessage());
        }
    }

    private void loadTickets() {
        ServiceTicketApi api = RetrofitClient.getClient().create(ServiceTicketApi.class);
        api.getAllTickets().enqueue(new Callback<List<ServiceTicketDTO>>() {
            @Override
            public void onResponse(Call<List<ServiceTicketDTO>> call, Response<List<ServiceTicketDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        allTicketsList.clear();
                        allTicketsList.addAll(response.body());
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, activeTicketsTable.getScene().getWindow(),
                                "Error", "Failed to load tickets: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<ServiceTicketDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, activeTicketsTable.getScene().getWindow(),
                            "Network Error", "Could not connect to server: " + t.getMessage());
                });
            }
        });
    }
}
