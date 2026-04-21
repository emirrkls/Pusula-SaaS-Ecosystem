package com.pusula.desktop.controller;

import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class ReassignTicketsDialogController {

    @FXML
    private Label lblMessage;
    
    @FXML
    private ComboBox<UserDTO> comboTechnicians;

    private Long selectedTechnicianId = null;
    private final ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());

    @FXML
    public void initialize() {
        comboTechnicians.setConverter(new StringConverter<UserDTO>() {
            @Override
            public String toString(UserDTO user) {
                if (user == null) return "";
                return user.getFullName() + " (" + user.getUsername() + ")";
            }

            @Override
            public UserDTO fromString(String string) {
                return null;
            }
        });
    }

    public void setup(Long ticketCount, List<UserDTO> availableTechnicians) {
        String msg = bundle.getString("reassign.message");
        lblMessage.setText(String.format(msg, ticketCount));

        if (availableTechnicians != null) {
            comboTechnicians.getItems().addAll(availableTechnicians);
        }
    }

    @FXML
    private void handleReassign() {
        UserDTO selected = comboTechnicians.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, lblMessage.getScene().getWindow(),
                    "Uyarı", bundle.getString("reassign.error.no_selection"));
            return;
        }

        this.selectedTechnicianId = selected.getId();
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        this.selectedTechnicianId = null;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) lblMessage.getScene().getWindow();
        stage.close();
    }

    public Long getSelectedTechnicianId() {
        return selectedTechnicianId;
    }
}
