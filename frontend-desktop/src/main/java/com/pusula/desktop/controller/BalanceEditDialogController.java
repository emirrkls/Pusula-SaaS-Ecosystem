package com.pusula.desktop.controller;

import com.pusula.desktop.dto.CurrentAccountDTO;
import com.pusula.desktop.util.UTF8Control;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Controller for Balance Edit Dialog (Current Accounts).
 * Supports three operations: ADD (+), SUBTRACT (-), SET (=)
 */
public class BalanceEditDialogController {

    @FXML
    private Label currentBalanceLabel;
    @FXML
    private ComboBox<String> operationCombo;
    @FXML
    private TextField amountField;
    @FXML
    private TextArea noteField;
    @FXML
    private Label newBalanceLabel;

    private CurrentAccountDTO currentAccount;
    private Runnable onSaveCallback;
    private ResourceBundle bundle;
    private BigDecimal originalBalance;

    @FXML
    public void initialize() {
        // Load resource bundle
        bundle = ResourceBundle.getBundle("i18n.messages",
                Locale.forLanguageTag("tr-TR"),
                new UTF8Control());

        // Setup operation combobox
        operationCombo.getItems().addAll(
                bundle.getString("balance.operation.add"), // "Ekle (+)"
                bundle.getString("balance.operation.subtract"), // "Çıkar (-)"
                bundle.getString("balance.operation.set") // "Ayarla (=)"
        );
        operationCombo.getSelectionModel().selectFirst();

        // Add listeners for live balance preview
        amountField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        operationCombo.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
    }

    public void setCurrentAccount(CurrentAccountDTO account) {
        this.currentAccount = account;
        this.originalBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;

        currentBalanceLabel.setText("₺" + originalBalance.toString());
        updatePreview();
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }

    private void updatePreview() {
        try {
            String amountText = amountField.getText();
            if (amountText == null || amountText.trim().isEmpty()) {
                newBalanceLabel.setText("₺" + originalBalance.toString());
                return;
            }

            BigDecimal amount = new BigDecimal(amountText);
            BigDecimal newBalance;
            String operation = operationCombo.getValue();

            if (operation != null) {
                if (operation.equals(bundle.getString("balance.operation.add"))) {
                    // ADD (+)
                    newBalance = originalBalance.add(amount);
                } else if (operation.equals(bundle.getString("balance.operation.subtract"))) {
                    // SUBTRACT (-)
                    newBalance = originalBalance.subtract(amount);
                } else {
                    // SET (=)
                    newBalance = amount;
                }

                // Color-code the preview
                if (newBalance.compareTo(originalBalance) > 0) {
                    newBalanceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #4CAF50;"); // Green
                } else if (newBalance.compareTo(originalBalance) < 0) {
                    newBalanceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #f44336;"); // Red
                } else {
                    newBalanceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2196F3;"); // Blue
                }

                newBalanceLabel.setText("₺" + newBalance.toString());
            }
        } catch (NumberFormatException e) {
            newBalanceLabel.setText("₺" + originalBalance.toString());
        }
    }

    @FXML
    private void handleSave() {
        try {
            String amountText = amountField.getText();
            if (amountText == null || amountText.trim().isEmpty()) {
                showError(bundle.getString("error.amount_required"));
                return;
            }

            BigDecimal amount = new BigDecimal(amountText);
            String operation = operationCombo.getValue();
            BigDecimal newBalance;

            if (operation.equals(bundle.getString("balance.operation.add"))) {
                newBalance = originalBalance.add(amount);
            } else if (operation.equals(bundle.getString("balance.operation.subtract"))) {
                newBalance = originalBalance.subtract(amount);
            } else {
                newBalance = amount;
            }

            // Update the DTO
            currentAccount.setBalance(newBalance);

            // Execute callback (parent will handle API call)
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }

            closeDialog();
        } catch (NumberFormatException e) {
            showError(bundle.getString("error.invalid_amount"));
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) amountField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(bundle.getString("error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
