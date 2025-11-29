package com.pusula.desktop;

import atlantafx.base.theme.PrimerLight;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

import java.util.Locale;
import java.util.ResourceBundle;

public class PusulaDesktopApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Set Turkish as default locale for all FXML screens
        Locale.setDefault(new Locale("tr", "TR"));
        // Use UTF8Control to properly load Turkish characters
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", new Locale("tr", "TR"), new UTF8Control());
        FXMLLoader fxmlLoader = new FXMLLoader(PusulaDesktopApp.class.getResource("/view/login.fxml"), bundle);

        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        // Set application icon
        try {
            stage.getIcons().add(new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/app.png")));
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }

        stage.setTitle(bundle.getString("app.title"));
        stage.setScene(scene);

        // Restore window state from preferences
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(PusulaDesktopApp.class);
        double x = prefs.getDouble("windowX", 100);
        double y = prefs.getDouble("windowY", 100);
        double width = prefs.getDouble("windowWidth", 800);
        double height = prefs.getDouble("windowHeight", 600);

        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);

        // Save window state on close
        stage.setOnCloseRequest(event -> {
            prefs.putDouble("windowX", stage.getX());
            prefs.putDouble("windowY", stage.getY());
            prefs.putDouble("windowWidth", stage.getWidth());
            prefs.putDouble("windowHeight", stage.getHeight());
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
