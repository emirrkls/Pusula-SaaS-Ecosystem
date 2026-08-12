package com.pusula.desktop.util;

import javafx.scene.control.Alert;
import javafx.stage.Window;

public class AlertHelper {

    public static void showAlert(Alert.AlertType alertType, Window owner, String title, String message) {
        NotificationService.Kind kind = switch (alertType) {
            case ERROR -> NotificationService.Kind.ERROR;
            case WARNING -> NotificationService.Kind.WARNING;
            case INFORMATION -> title != null && (title.toLowerCase(java.util.Locale.ROOT).contains("başar")
                    || title.toLowerCase(java.util.Locale.ROOT).contains("success"))
                    ? NotificationService.Kind.SUCCESS : NotificationService.Kind.INFO;
            default -> NotificationService.Kind.INFO;
        };
        if (kind == NotificationService.Kind.ERROR) {
            NotificationService.modal(owner, kind, title, message);
        } else {
            NotificationService.toast(owner, kind, title, message);
        }
    }

    public static boolean showConfirmation(String title, String message) {
        return showConfirmation(null, title, message);
    }

    public static boolean showConfirmation(Window owner, String title, String message) {
        return NotificationService.confirm(owner, title, message);
    }

    public static void showSuccess(Window owner, String title, String message) {
        NotificationService.toast(owner, NotificationService.Kind.SUCCESS, title, message);
    }
}
