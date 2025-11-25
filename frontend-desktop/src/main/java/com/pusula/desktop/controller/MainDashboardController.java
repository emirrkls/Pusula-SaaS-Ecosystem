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

    private boolean isDark = false; // Theme state

    @FXML
    public void initialize() {
        if (SessionManager.getUsername() != null) {
            userLabel.setText("Welcome, " + SessionManager.getUsername());
        }
        showDashboard();
    }

    @FXML
    private void toggleTheme() {
        // Theme toggle implementation
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
        System.out.println("MainDashboardController: 'Dashboard' clicked.");
        try {
            System.out.println("MainDashboardController: Loading /view/dashboard.fxml");
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dashboard.fxml"), bundle);
            Parent view = loader.load();
            System.out.println("MainDashboardController: FXML loaded successfully.");

            DashboardController controller = loader.getController();
            controller.setMainController(this);

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            System.out.println("MainDashboardController: Dashboard view updated.");
        } catch (Exception e) {
            System.err.println("MainDashboardController: Error loading dashboard view!");
            e.printStackTrace();
        }
    }

    @FXML
    public void showServiceManagement() {
        System.out.println("MainDashboardController: 'Service Tickets' clicked.");
        try {
            System.out.println("MainDashboardController: Loading /view/service_tickets.fxml");
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/service_tickets.fxml"), bundle);
            Parent view = loader.load();
            System.out.println("MainDashboardController: FXML loaded successfully.");

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            System.out.println("MainDashboardController: View updated.");
        } catch (Exception e) {
            System.err.println("MainDashboardController: Error loading view!");
            e.printStackTrace();
        }
    }

    @FXML
    public void showInventory() {
        System.out.println("MainDashboardController: 'Inventory' clicked.");
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/inventory.fxml"), bundle);
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            System.err.println("MainDashboardController: Error loading inventory view!");
            e.printStackTrace();
        }
    }

    @FXML
    public void showCustomers() {
        System.out.println("MainDashboardController: 'Customers' clicked.");
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/customers.fxml"), bundle);
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            System.err.println("MainDashboardController: Error loading customers view!");
            e.printStackTrace();
        }
    }

    @FXML
    public void showFinance() {
        System.out.println("MainDashboardController: 'Finance' clicked.");
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n.messages",
                    new java.util.Locale("tr", "TR"), new UTF8Control());
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/finance_view.fxml"), bundle);
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            System.err.println("MainDashboardController: Error loading finance view!");
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
