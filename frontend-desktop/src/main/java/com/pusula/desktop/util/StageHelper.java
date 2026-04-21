package com.pusula.desktop.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Utility class to manage stage/window configuration
 */
public class StageHelper {

    private static Image appIcon;

    static {
        try {
            appIcon = new Image(StageHelper.class.getResourceAsStream("/app.png"));
        } catch (Exception e) {
            System.err.println("Could not load application icon: " + e.getMessage());
        }
    }

    /**
     * Sets the application icon on a stage.
     * Call this method after creating any new Stage.
     * 
     * @param stage The stage to set the icon on
     */
    public static void setIcon(Stage stage) {
        if (appIcon != null && stage != null) {
            stage.getIcons().clear();
            stage.getIcons().add(appIcon);
        }
    }

    /**
     * Gets the application icon image
     * 
     * @return The application icon image
     */
    public static Image getAppIcon() {
        return appIcon;
    }
}
