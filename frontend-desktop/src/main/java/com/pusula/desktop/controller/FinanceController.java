package com.pusula.desktop.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

public class FinanceController {

    @FXML
    private VBox contentBox;

    @FXML
    private VBox lockScreen;

    @FXML
    private PasswordField pinField;

    @FXML
    private Label errorLabel;

    // Hardcoded PIN for demonstration. In production, fetch from backend/user
    // profile.
    private static final String CORRECT_PIN = "1234";

    @FXML
    public void initialize() {
        // Ensure lock screen is visible and content is hidden
        lockScreen.setVisible(true);
        contentBox.setVisible(false);
    }

    @FXML
    private void handleUnlock() {
        String enteredPin = pinField.getText();
        if (CORRECT_PIN.equals(enteredPin)) {
            lockScreen.setVisible(false);
            contentBox.setVisible(true);
            errorLabel.setText("");
        } else {
            errorLabel.setText("Invalid PIN");
            pinField.clear();
        }
    }
}
