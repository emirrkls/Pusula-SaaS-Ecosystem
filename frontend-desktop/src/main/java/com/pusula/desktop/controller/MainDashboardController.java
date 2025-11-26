package com.pusula.desktop.controller;

import com.pusula.desktop.util.UTF8Control;
import com.pusula.desktop.util.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.io.IOException;

public class MainDashboardController {
    @FXML
    private StackPane contentArea;
    @FXML
    private Label userLabel;
    private boolean isDark = false;

    @FXML
    public void initialize() {
        if (SessionManager.getUsername() != null) {
            userLabel.setText("Welcome, " + SessionManager.getUsername());
        }
        showDashboard();
    }

    @FXML
    private void toggleTheme() {
        if (isDark) {
            javafx.application.Application.setUserAgentStylesheet(
                    new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
            isDark = false;
        } else {
            javafx.application.Application.setUserAgentStylesheet(
                    new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
            isDark = true;
        }
    }

    @FXML
    public void showDashboard() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dashboard.fxml"), bundle);
            Parent view = loader.load();
            DashboardController controller = loader.getController();
            controller.setMainController(this);
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showServiceManagement() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/service_tickets.fxml"), bundle);
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showInventory() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/inventory.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showCustomers() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/customers.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void showFinance() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/finance_view.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showSettings() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/settings_view.fxml"), bundle);
            Parent view = loader.load();

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.clearSession();
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"), bundle);
            Parent root = loader.load();
            Stage stage = (Stage) userLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Pusula - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}