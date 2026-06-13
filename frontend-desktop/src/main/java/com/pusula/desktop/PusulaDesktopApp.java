package com.pusula.desktop;

import com.pusula.desktop.util.ThemeHelper;
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
        boolean dark = ThemeHelper.isDarkMode();
        ThemeHelper.applyGlobalTheme(dark);

        Locale.setDefault(new Locale("tr", "TR"));
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", new Locale("tr", "TR"), new UTF8Control());
        FXMLLoader fxmlLoader = new FXMLLoader(PusulaDesktopApp.class.getResource("/view/login.fxml"), bundle);

        Scene scene = new Scene(fxmlLoader.load(), 960, 640);
        ThemeHelper.applyToScene(scene, dark);

        com.pusula.desktop.util.StageHelper.setIcon(stage);

        stage.setTitle(bundle.getString("app.title"));
        stage.setScene(scene);

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(PusulaDesktopApp.class);
        double x = prefs.getDouble("windowX", 100);
        double y = prefs.getDouble("windowY", 100);
        double width = prefs.getDouble("windowWidth", 960);
        double height = prefs.getDouble("windowHeight", 640);

        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);

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
