package com.pusula.desktop.controller;

import com.pusula.desktop.dto.ServiceTicketDTO;
import com.pusula.desktop.dto.UserDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BulkAssignTicketsDialogController {
    @FXML private ComboBox<UserDTO> technicianCombo;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private ListView<ServiceTicketDTO> ticketList;
    @FXML private Label selectionLabel;
    @FXML private Button assignButton;

    private final Set<Long> selectedTicketIds = new LinkedHashSet<>();
    private boolean confirmed;

    @FXML
    public void initialize() {
        technicianCombo.setConverter(new StringConverter<>() {
            @Override public String toString(UserDTO user) {
                if (user == null) return "";
                String name = user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername();
                return name != null ? name : "Teknisyen";
            }
            @Override public UserDTO fromString(String value) { return null; }
        });
        ticketList.setCellFactory(ignored -> new TicketSelectionCell());
        technicianCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateState());
    }

    public void setup(List<ServiceTicketDTO> tickets, List<UserDTO> technicians) {
        ticketList.getItems().setAll(tickets);
        technicianCombo.getItems().setAll(technicians);
        selectedTicketIds.clear();
        selectAllCheckBox.setSelected(false);
        confirmed = false;
        updateState();
    }

    @FXML
    private void handleSelectAll() {
        selectedTicketIds.clear();
        if (selectAllCheckBox.isSelected()) {
            ticketList.getItems().stream().map(ServiceTicketDTO::getId).forEach(selectedTicketIds::add);
        }
        ticketList.refresh();
        updateState();
    }

    @FXML
    private void handleAssign() {
        if (selectedTicketIds.isEmpty() || technicianCombo.getValue() == null) return;
        confirmed = true;
        close();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        close();
    }

    private void toggle(Long ticketId, boolean selected) {
        if (selected) selectedTicketIds.add(ticketId);
        else selectedTicketIds.remove(ticketId);
        selectAllCheckBox.setSelected(!ticketList.getItems().isEmpty()
                && selectedTicketIds.size() == ticketList.getItems().size());
        updateState();
    }

    private void updateState() {
        int count = selectedTicketIds.size();
        selectionLabel.setText(count == 0 ? "Henüz fiş seçilmedi" : count + " fiş seçildi");
        assignButton.setText(count > 0 ? count + " Fişi Ata" : "Fişleri Ata");
        assignButton.setDisable(count == 0 || technicianCombo.getValue() == null);
    }

    private void close() {
        ((Stage) ticketList.getScene().getWindow()).close();
    }

    public boolean isConfirmed() { return confirmed; }
    public List<Long> getSelectedTicketIds() { return List.copyOf(selectedTicketIds); }
    public Long getSelectedTechnicianId() {
        return technicianCombo.getValue() != null ? technicianCombo.getValue().getId() : null;
    }

    private final class TicketSelectionCell extends ListCell<ServiceTicketDTO> {
        private final CheckBox checkBox = new CheckBox();
        private final Label customer = new Label();
        private final Label description = new Label();
        private final VBox text = new VBox(3, customer, description);
        private final HBox row = new HBox(12, checkBox, text, new Region());

        TicketSelectionCell() {
            customer.getStyleClass().add("bulk-ticket-customer");
            description.getStyleClass().add("bulk-ticket-description");
            description.setMaxWidth(520);
            description.setEllipsisString("…");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getStyleClass().add("bulk-ticket-row");
            row.setOnMouseClicked(event -> {
                if (!checkBox.isHover()) checkBox.setSelected(!checkBox.isSelected());
            });
            checkBox.selectedProperty().addListener((obs, oldValue, selected) -> {
                ServiceTicketDTO ticket = getItem();
                if (ticket != null) toggle(ticket.getId(), selected);
            });
        }

        @Override
        protected void updateItem(ServiceTicketDTO ticket, boolean empty) {
            super.updateItem(ticket, empty);
            if (empty || ticket == null) {
                setGraphic(null);
                return;
            }
            customer.setText((ticket.getCustomerName() != null ? ticket.getCustomerName() : "Müşteri")
                    + "  ·  #" + ticket.getId());
            description.setText(ticket.getDescription() != null && !ticket.getDescription().isBlank()
                    ? ticket.getDescription() : "Açıklama yok");
            checkBox.setSelected(selectedTicketIds.contains(ticket.getId()));
            setGraphic(row);
        }
    }
}
