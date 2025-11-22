package com.pusula.desktop.controller;

import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ServiceTicketController {

    @FXML
    private TableView<ServiceTicketDTO> ticketsTable;

    @FXML
    private TableColumn<ServiceTicketDTO, UUID> colId;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colDescription;

    @FXML
    private TableColumn<ServiceTicketDTO, String> colStatus;

    @FXML
    private TableColumn<ServiceTicketDTO, LocalDateTime> colDate;

    private final ObservableList<ServiceTicketDTO> ticketList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("scheduledDate"));

        ticketsTable.setItems(ticketList);

        // Add double click listener
        ticketsTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<ServiceTicketDTO> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    ServiceTicketDTO rowData = row.getItem();
                    openTicketDetails(rowData);
                }
            });
            return row;
        });

        loadTickets();
    }

    private void openTicketDetails(ServiceTicketDTO ticket) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/ticket_details.fxml"));
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
            AlertHelper.showAlert(Alert.AlertType.ERROR, ticketsTable.getScene().getWindow(),
                    "Error", "Could not open details: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadTickets();
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
            AlertHelper.showAlert(Alert.AlertType.ERROR, ticketsTable.getScene().getWindow(),
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
                        ticketList.clear();
                        ticketList.addAll(response.body());
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, ticketsTable.getScene().getWindow(),
                                "Error", "Failed to load tickets: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<ServiceTicketDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, ticketsTable.getScene().getWindow(),
                            "Network Error", "Could not connect to server: " + t.getMessage());
                });
            }
        });
    }
}
