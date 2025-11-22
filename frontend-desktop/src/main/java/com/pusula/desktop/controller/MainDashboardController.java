package com.pusula.desktop.controller;

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

    @FXML
    public void initialize() {
        if (SessionManager.getUsername() != null) {
            userLabel.setText("Welcome, " + SessionManager.getUsername());
        }
    }

    @FXML
    private void showDashboard() {
        contentArea.getChildren().clear();
        Label label = new Label("Dashboard Overview");
        label.setStyle("-fx-font-size: 24px;");
        contentArea.getChildren().add(label);
    }

    @FXML
    private void showServiceManagement() {
        System.out.println("MainDashboardController: 'Service Tickets' clicked.");
        try {
            System.out.println("MainDashboardController: Loading /view/service_tickets.fxml");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/service_tickets.fxml"));
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
    private void showInventory() {
        System.out.println("MainDashboardController: 'Inventory' clicked.");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/inventory.fxml"));
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            System.err.println("MainDashboardController: Error loading inventory view!");
            e.printStackTrace();
        }
    }

    @FXML
    private void showCustomers() {
        System.out.println("MainDashboardController: 'Customers' clicked.");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/customers.fxml"));
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            System.err.println("MainDashboardController: Error loading customers view!");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        SessionManager.clearSession();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) userLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Pusula - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
