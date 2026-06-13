package com.pusula.desktop.controller;

import com.pusula.desktop.util.TableUiHelper;
import com.pusula.desktop.util.ThemeHelper;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    private ListView<ServiceTicketDTO> agendaListView;

    @FXML
    private Label agendaCountLabel;

    @FXML
    private VBox agendaEmptyBox;

    private ResourceBundle resourceBundle;
    private final ObservableList<ServiceTicketDTO> agendaItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (welcomeLabel != null) {
            String userName = com.pusula.desktop.util.SessionManager.getUsername();
            welcomeLabel.setText("Hoş Geldiniz, " + (userName != null ? userName : "Yönetici") + "!");
        }
        if (dateLabel != null) {
            dateLabel.setText(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE", new java.util.Locale("tr"))));
        }

        resourceBundle = ResourceBundle.getBundle("i18n.messages",
                Locale.of("tr", "TR"), new UTF8Control());

        setupAgendaList();
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

    private void setupAgendaList() {
        agendaListView.setItems(agendaItems);
        agendaListView.setCellFactory(lv -> new AgendaCardCell());
        agendaItems.addListener((javafx.collections.ListChangeListener<ServiceTicketDTO>) c -> updateAgendaEmptyState());
    }

    private void updateAgendaEmptyState() {
        boolean empty = agendaItems.isEmpty();
        agendaEmptyBox.setVisible(empty);
        agendaEmptyBox.setManaged(empty);
        agendaListView.setVisible(!empty);
        agendaCountLabel.setText(agendaItems.size() + " randevu");
    }

    private void setAgendaItems(List<ServiceTicketDTO> tickets) {
        agendaItems.setAll(tickets);
        updateAgendaEmptyState();
    }

    private class AgendaCardCell extends ListCell<ServiceTicketDTO> {
        private final HBox card = new HBox(10);
        private final Label timeLabel = new Label();
        private final VBox content = new VBox(4);
        private final HBox headerRow = new HBox(8);
        private final Label customerLabel = new Label();
        private final Label statusBadge = new Label();
        private final Label techLabel = new Label();
        private final HBox actions = new HBox(4);
        private final Button startBtn = new Button("▶");
        private final Button callBtn = new Button("📞");
        private final Button openBtn = new Button("✓");

        AgendaCardCell() {
            timeLabel.getStyleClass().add("agenda-card-time");
            customerLabel.getStyleClass().add("agenda-card-customer");
            techLabel.getStyleClass().add("customer-card-meta");
            card.getStyleClass().add("agenda-card");
            card.setAlignment(Pos.CENTER_LEFT);

            startBtn.getStyleClass().addAll("btn-icon-sm", "btn-icon-success");
            callBtn.getStyleClass().addAll("btn-icon-sm", "btn-icon-primary");
            openBtn.getStyleClass().addAll("btn-icon-sm", "btn-icon-danger");
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.getChildren().addAll(startBtn, callBtn, openBtn);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            headerRow.getChildren().addAll(customerLabel, spacer, statusBadge);

            content.getChildren().addAll(headerRow, techLabel);
            HBox.setHgrow(content, Priority.ALWAYS);
            card.getChildren().addAll(timeLabel, content, actions);

            card.setOnMouseClicked(e -> {
                if (isEmpty() || getItem() == null || e.getTarget() instanceof Button) {
                    return;
                }
                openTicketDetails(getItem());
            });

            startBtn.setOnAction(e -> {
                if (getItem() != null) handleStartTicket(getItem());
                e.consume();
            });
            callBtn.setOnAction(e -> {
                if (getItem() != null) handleCallCustomer(getItem());
                e.consume();
            });
            openBtn.setOnAction(e -> {
                if (getItem() != null) handleCompleteTicket(getItem());
                e.consume();
            });

            AnimationHelper.applyCardHover(card);
        }

        @Override
        protected void updateItem(ServiceTicketDTO ticket, boolean empty) {
            super.updateItem(ticket, empty);
            if (empty || ticket == null) {
                setGraphic(null);
                return;
            }

            LocalDateTime scheduled = ticket.getScheduledDate();
            timeLabel.setText(scheduled != null ? scheduled.format(DateTimeFormatter.ofPattern("HH:mm")) : "—");

            String customer = ticket.getCustomerName();
            customerLabel.setText(customer != null && !customer.isBlank()
                    ? TableUiHelper.toTitleCase(customer)
                    : "Müşteri #" + ticket.getCustomerId());

            TableUiHelper.applyStatusBadge(statusBadge, ticket.getStatus(), resourceBundle);

            if (ticket.getAssignedTechnicianId() == null) {
                techLabel.setText(resourceBundle.getString("dashboard.unassigned"));
            } else {
                String tech = ticket.getAssignedTechnicianName();
                techLabel.setText(tech != null && !tech.isBlank() ? tech : "Teknisyen #" + ticket.getAssignedTechnicianId());
            }

            String status = ticket.getStatus() != null ? ticket.getStatus() : "";
            startBtn.setVisible("PENDING".equals(status) || "ASSIGNED".equals(status));
            startBtn.setManaged(startBtn.isVisible());

            setGraphic(card);
        }
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
            stage.setScene(ThemeHelper.createDialogScene(root));
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
        if (!com.pusula.desktop.util.SessionManager.isAdmin()) {
            return;
        }
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

                    Platform.runLater(() -> setAgendaItems(todayTickets));
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
