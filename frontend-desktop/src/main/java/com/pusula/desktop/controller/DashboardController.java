package com.pusula.desktop.controller;

import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.util.AnimationHelper;

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
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardController {

    @FXML
    private VBox alertContainer;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private Label activeTicketsLabel;

    @FXML
    private Label criticalStockLabel;

    @FXML
    private Label pendingProposalsLabel;

    @FXML
    private HBox activeTicketsCard;

    @FXML
    private HBox criticalStockCard;

    @FXML
    private HBox pendingProposalsCard;

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
        if (welcomeLabel != null) {
            String userName = com.pusula.desktop.util.SessionManager.getUsername();
            welcomeLabel.setText("Hoş Geldiniz, " + (userName != null ? userName : "Yönetici") + "!");
        }
        if (dateLabel != null) {
            dateLabel.setText(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE", new java.util.Locale("tr"))));
        }

        setupTable();
        setupActionColumn();
        loadDashboardData();
        loadPerformanceChart();

        // Apply card hover animations for premium feel
        if (activeTicketsCard != null) {
            AnimationHelper.applyCardHover(activeTicketsCard);
        }
        if (criticalStockCard != null) {
            AnimationHelper.applyCardHover(criticalStockCard);
        }
        if (pendingProposalsCard != null) {
            AnimationHelper.applyCardHover(pendingProposalsCard);
        }
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
            mainController.showInventory(true);
        }
    }

    @FXML
    private void handlePendingProposalsClick() {
        if (mainController != null) {
            mainController.showProposals();
        }
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
                    userApi.getTechnicians().enqueue(new retrofit2.Callback<java.util.List<UserDTO>>() {
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
            com.pusula.desktop.util.StageHelper.setIcon(stage);
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

        // Get the CategoryAxis and clear its categories
        javafx.scene.chart.CategoryAxis xAxis = (javafx.scene.chart.CategoryAxis) performanceChart.getXAxis();
        xAxis.setAutoRanging(false);
        xAxis.getCategories().clear();

        // Get last 7 days - use yyyy-MM-dd for API matching, dd/MM for display
        List<LocalDate> last7Days = new ArrayList<>();
        List<String> displayDates = new ArrayList<>();
        DateTimeFormatter apiFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            last7Days.add(date);
            displayDates.add(date.format(displayFormatter));
        }

        // Set explicit categories for the X-axis
        xAxis.setCategories(FXCollections.observableArrayList(displayDates));

        // Create a series for each technician
        for (Map.Entry<String, Map<String, Integer>> entry : data.entrySet()) {
            String techName = entry.getKey();
            Map<String, Integer> dailyCounts = entry.getValue();

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(techName);

            // Add data points for each day
            for (int i = 0; i < last7Days.size(); i++) {
                LocalDate date = last7Days.get(i);
                String apiDate = date.format(apiFormatter);
                String displayDate = displayDates.get(i);
                int count = dailyCounts.getOrDefault(apiDate, 0);
                series.getData().add(new XYChart.Data<>(displayDate, count));
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
                    long activeCount = response.body().stream()
                            .filter(t -> !Arrays.asList("COMPLETED", "CANCELLED").contains(t.getStatus()))
                            .count();
                            
                    // SLA Checker
                    long delayedCount = response.body().stream()
                            .filter(t -> "PENDING".equals(t.getStatus()) || "IN_PROGRESS".equals(t.getStatus()))
                            .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isBefore(LocalDateTime.now().minusHours(48)))
                            .count();

                    Platform.runLater(() -> {
                        AnimationHelper.animateCounter(activeTicketsLabel, activeCount, 1500);
                        
                        if (alertContainer != null) {
                            if (delayedCount > 0) {
                                alertContainer.getChildren().clear();
                                Label alertText = new Label("🔥 " + delayedCount + " adet servis fişi 48 saatten uzun süredir müdahale bekliyor!");
                                alertText.setStyle("-fx-text-fill: #991B1B; -fx-font-weight: bold; -fx-font-size: 14px;");
                                alertContainer.getChildren().add(alertText);
                                alertContainer.setVisible(true);
                                alertContainer.setManaged(true);
                            } else {
                                alertContainer.setVisible(false);
                                alertContainer.setManaged(false);
                            }
                        }
                    });

                    // Agenda (Today's tickets)
                    java.util.List<ServiceTicketDTO> todayTickets = response.body().stream()
                            .filter(t -> t.getScheduledDate() != null &&
                                    t.getScheduledDate().toLocalDate().isEqual(java.time.LocalDate.now()))
                            .collect(java.util.stream.Collectors.toList());

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
                    Platform.runLater(() -> AnimationHelper.animateCounter(criticalStockLabel, criticalCount, 1500));
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
                    // Count DRAFT proposals (pending to be sent)
                    long draftCount = response.body().stream()
                            .filter(p -> "DRAFT".equalsIgnoreCase(p.getStatus()))
                            .count();
                    Platform.runLater(() -> AnimationHelper.animateCounter(pendingProposalsLabel, draftCount, 1500));
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
