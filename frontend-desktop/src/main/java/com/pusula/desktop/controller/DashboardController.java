package com.pusula.desktop.controller;

import com.pusula.desktop.util.UTF8Control;

import com.pusula.desktop.api.CustomerApi;
import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.api.ReportsApi;
import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.dto.CustomerDTO;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardController {

    @FXML
    private Label activeTicketsLabel;

    @FXML
    private Label criticalStockLabel;

    @FXML
    private Label pendingProposalsLabel;

    @FXML
    private LineChart<String, Number> performanceChart;

    private MainDashboardController mainController;

    public void setMainController(MainDashboardController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private TableView<ServiceTicketDTO> agendaTable;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colCustomer;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colTime;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colStatus;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colTechnician;

    @FXML
    private TableColumn<ServiceTicketDTO, Void> colActions;

    @FXML
    public void initialize() {
        setupTable();
        setupActionColumn();
        loadDashboardData();
        loadPerformanceChart();
    }

    @FXML
    private void handleActiveTicketsClick() {
        if (mainController != null) {
            mainController.showServiceManagement();
        }
    }

    @FXML
    private void handleCriticalStockClick() {
        if (mainController != null) {
            mainController.showInventory();
        }
    }

    @FXML
    private void handlePendingProposalsClick() {
        // TODO: Navigate to Proposals view when implemented.
        System.out.println("Navigate to Proposals clicked");
    }

    private void setupTable() {
        colCustomer.setCellValueFactory(cellData -> {
            Long customerId = cellData.getValue().getCustomerId();
            if (customerId != null) {
                // Fetch customer name
                CustomerApi customerApi = RetrofitClient.getClient().create(CustomerApi.class);
                try {
                    var response = customerApi.getCustomerById(customerId).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        return new SimpleStringProperty(response.body().getName());
                    }
                } catch (Exception e) {
                    // Fallback to ID
                }
            }
            return new SimpleStringProperty("Müşteri " + customerId);
        });

        colTime.setCellValueFactory(cellData -> {
            LocalDateTime date = cellData.getValue().getScheduledDate();
            return new SimpleStringProperty(date != null ? date.format(DateTimeFormatter.ofPattern("HH:mm")) : "-");
        });

        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));

        // Load resource bundle
        java.util.ResourceBundle resourceBundle = java.util.ResourceBundle.getBundle("i18n.messages",
                java.util.Locale.of("tr", "TR"), new UTF8Control());

        // Set custom cell factory for Status column
        colStatus.setCellFactory(column -> new TableCell<ServiceTicketDTO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setStyle("");
                    return;
                }

                ServiceTicketDTO ticket = getTableView().getItems().get(getIndex());
                String status = ticket.getStatus();

                if (status == null) {
                    setText("");
                    setStyle("");
                    return;
                }

                // Translate the status
                String translatedStatus = switch (status) {
                    case "PENDING" -> resourceBundle.getString("status.pending");
                    case "IN_PROGRESS" -> resourceBundle.getString("status.in_progress");
                    case "COMPLETED" -> resourceBundle.getString("status.completed");
                    case "CANCELLED" -> resourceBundle.getString("status.cancelled");
                    case "ASSIGNED" -> resourceBundle.getString("status.assigned");
                    default -> status;
                };

                setText(translatedStatus);

                // Set color based on status
                switch (status) {
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

        // Set custom cell factory for Technician column
        colTechnician.setCellFactory(column -> new TableCell<ServiceTicketDTO, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setStyle("");
                    return;
                }

                ServiceTicketDTO ticket = getTableView().getItems().get(getIndex());
                Long technicianId = ticket.getAssignedTechnicianId();

                if (technicianId == null) {
                    setText(resourceBundle.getString("dashboard.unassigned"));
                    setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404;");
                } else {
                    UserApi userApi = RetrofitClient.getClient().create(UserApi.class);
                    userApi.getAllUsers().enqueue(new retrofit2.Callback<java.util.List<UserDTO>>() {
                        @Override
                        public void onResponse(retrofit2.Call<java.util.List<UserDTO>> call,
                                retrofit2.Response<java.util.List<UserDTO>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                UserDTO tech = response.body().stream()
                                        .filter(u -> u.getId().equals(technicianId))
                                        .findFirst()
                                        .orElse(null);

                                Platform.runLater(() -> {
                                    if (tech != null) {
                                        setText(tech.getFullName());
                                    } else {
                                        setText("Teknisyen " + technicianId);
                                    }
                                    setStyle("");
                                });
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<java.util.List<UserDTO>> call, Throwable t) {
                            Platform.runLater(() -> {
                                setText("Teknisyen " + technicianId);
                                setStyle("");
                            });
                        }
                    });
                }
            }
        });

        colTechnician.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getAssignedTechnicianId() != null
                        ? cellData.getValue().getAssignedTechnicianId().toString()
                        : ""));

        // Add double-click handler to open ticket details
        agendaTable.setRowFactory(tv -> {
            TableRow<ServiceTicketDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    ServiceTicketDTO ticket = row.getItem();
                    openTicketDetails(ticket);
                }
            });
            return row;
        });
    }

    private void setupActionColumn() {
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button startBtn = new Button("▶");
            private final Button callBtn = new Button("📞");
            private final Button completeBtn = new Button("✓");
            private final HBox actionBox = new HBox(5);

            {
                startBtn.setStyle(
                        "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 2 6;");
                callBtn.setStyle(
                        "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 2 6;");
                completeBtn.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 2 6;");

                actionBox.setAlignment(Pos.CENTER);
                actionBox.getChildren().addAll(startBtn, callBtn, completeBtn);

                startBtn.setOnAction(event -> {
                    ServiceTicketDTO ticket = getTableView().getItems().get(getIndex());
                    handleStartTicket(ticket);
                });

                callBtn.setOnAction(event -> {
                    ServiceTicketDTO ticket = getTableView().getItems().get(getIndex());
                    handleCallCustomer(ticket);
                });

                completeBtn.setOnAction(event -> {
                    ServiceTicketDTO ticket = getTableView().getItems().get(getIndex());
                    handleCompleteTicket(ticket);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    ServiceTicketDTO ticket = getTableView().getItems().get(getIndex());
                    String status = ticket.getStatus();

                    // Show start button only for PENDING/ASSIGNED tickets
                    startBtn.setVisible("PENDING".equals(status) || "ASSIGNED".equals(status));
                    startBtn.setManaged(startBtn.isVisible());

                    setGraphic(actionBox);
                }
            }
        });
    }

    private void handleStartTicket(ServiceTicketDTO ticket) {
        // TODO: Update ticket status to IN_PROGRESS
        System.out.println("Starting ticket: " + ticket.getId());
        // Reload data after action
        loadDashboardData();
    }

    private void handleCallCustomer(ServiceTicketDTO ticket) {
        if (ticket.getCustomerId() != null) {
            CustomerApi customerApi = RetrofitClient.getClient().create(CustomerApi.class);
            customerApi.getCustomerById(ticket.getCustomerId()).enqueue(new retrofit2.Callback<>() {
                @Override
                public void onResponse(retrofit2.Call<CustomerDTO> call, retrofit2.Response<CustomerDTO> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Müşteri İletişim");
                            alert.setHeaderText("Müşteri: " + response.body().getName());
                            alert.setContentText("Telefon: " + response.body().getPhone());
                            alert.showAndWait();
                        });
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<CustomerDTO> call, Throwable t) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Hata");
                        alert.setContentText("Müşteri bilgisi alınamadı.");
                        alert.showAndWait();
                    });
                }
            });
        }
    }

    private void handleCompleteTicket(ServiceTicketDTO ticket) {
        if (mainController != null) {
            // Navigate to ticket details for completion
            openTicketDetails(ticket);
        }
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

            // Refresh data after closing details
            loadDashboardData();
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText("Could not open details: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void loadPerformanceChart() {
        ReportsApi reportsApi = RetrofitClient.getClient().create(ReportsApi.class);
        reportsApi.getTechnicianPerformance().enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(retrofit2.Call<Map<String, Map<String, Integer>>> call,
                    retrofit2.Response<Map<String, Map<String, Integer>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> populateChart(response.body()));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Map<String, Map<String, Integer>>> call, Throwable t) {
                System.err.println("Failed to load performance data: " + t.getMessage());
            }
        });
    }

    private void populateChart(Map<String, Map<String, Integer>> data) {
        performanceChart.getData().clear();

        // Get last 7 days
        List<String> last7Days = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            last7Days.add(LocalDate.now().minusDays(i).format(formatter));
        }

        // Create a series for each technician
        for (Map.Entry<String, Map<String, Integer>> entry : data.entrySet()) {
            String techName = entry.getKey();
            Map<String, Integer> dailyCounts = entry.getValue();

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(techName);

            // Add data points for each day
            for (String date : last7Days) {
                int count = dailyCounts.getOrDefault(date, 0);
                series.getData().add(new XYChart.Data<>(date, count));
            }

            performanceChart.getData().add(series);
        }
    }

    private void loadDashboardData() {
        // 1. Active Tickets
        ServiceTicketApi ticketApi = RetrofitClient.getClient().create(ServiceTicketApi.class);
        ticketApi.getAllTickets().enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<ServiceTicketDTO>> call,
                    retrofit2.Response<java.util.List<ServiceTicketDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    System.out.println("=== DASHBOARD DEBUG ===");
                    System.out.println("Total tickets from API: " + response.body().size());
                    System.out.println("Today's date (client): " + java.time.LocalDate.now());

                    long activeCount = response.body().stream()
                            .filter(t -> !Arrays.asList("COMPLETED", "CANCELLED").contains(t.getStatus()))
                            .count();
                    Platform.runLater(() -> activeTicketsLabel.setText(String.valueOf(activeCount)));

                    // Agenda (Today's tickets)
                    response.body().forEach(t -> {
                        System.out.println("Ticket ID=" + t.getId() +
                                ", ScheduledDate=" + t.getScheduledDate() +
                                ", Date only="
                                + (t.getScheduledDate() != null ? t.getScheduledDate().toLocalDate() : "null") +
                                ", Status=" + t.getStatus());
                    });

                    java.util.List<ServiceTicketDTO> todayTickets = response.body().stream()
                            .filter(t -> t.getScheduledDate() != null &&
                                    t.getScheduledDate().toLocalDate().isEqual(java.time.LocalDate.now()))
                            .collect(java.util.stream.Collectors.toList());

                    System.out.println("Filtered today's tickets: " + todayTickets.size());
                    System.out.println("======================");

                    Platform.runLater(() -> agendaTable.setItems(FXCollections.observableArrayList(todayTickets)));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<ServiceTicketDTO>> call, Throwable t) {
                System.err.println("Failed to fetch tickets: " + t.getMessage());
            }
        });

        // 2. Critical Stock
        InventoryApi inventoryApi = RetrofitClient.getClient().create(InventoryApi.class);
        inventoryApi.getAllInventory().enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.pusula.desktop.dto.InventoryDTO>> call,
                    retrofit2.Response<java.util.List<com.pusula.desktop.dto.InventoryDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long criticalCount = response.body().stream()
                            .filter(i -> i.getQuantity() <= i.getCriticalLevel())
                            .count();
                    Platform.runLater(() -> criticalStockLabel.setText(String.valueOf(criticalCount)));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<com.pusula.desktop.dto.InventoryDTO>> call,
                    Throwable t) {
                System.err.println("Failed to fetch inventory: " + t.getMessage());
            }
        });

        // 3. Pending Proposals
        com.pusula.desktop.api.ProposalApi proposalApi = RetrofitClient.getClient()
                .create(com.pusula.desktop.api.ProposalApi.class);
        proposalApi.getAllProposals().enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<com.pusula.desktop.dto.ProposalDTO>> call,
                    retrofit2.Response<java.util.List<com.pusula.desktop.dto.ProposalDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long pendingCount = response.body().stream()
                            .filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()))
                            .count();
                    Platform.runLater(() -> pendingProposalsLabel.setText(String.valueOf(pendingCount)));
                } else {
                    Platform.runLater(() -> pendingProposalsLabel.setText("0"));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<com.pusula.desktop.dto.ProposalDTO>> call,
                    Throwable t) {
                System.err.println("Failed to fetch proposals: " + t.getMessage());
            }
        });
    }
}
