package com.pusula.desktop.util;

import javafx.scene.control.Alert;
import java.awt.Desktop;
import java.net.URI;

public class WhatsAppHelper {

    /**
     * Formats a phone number and opens WhatsApp Web/Desktop.
     * @param phone The raw phone number string
     */
    public static void openWhatsApp(String phone) {
        if (phone == null || phone.trim().isEmpty() || phone.equals("-")) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı", "Geçerli bir telefon numarası bulunamadı.");
            return;
        }

        // Clean up phone number (remove spaces, parens, dashes, etc.)
        String cleanPhone = phone.replaceAll("[^0-9+]", "");

        // If it starts with 0 (e.g., 05551234567), change to 90555...
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "90" + cleanPhone.substring(1);
        }
        // If it's a 10 digit number without country code (e.g., 5551234567), add 90
        else if (cleanPhone.length() == 10 && !cleanPhone.startsWith("+")) {
            cleanPhone = "90" + cleanPhone;
        }
        // If it starts with +, remove it for the wa.me URL
        else if (cleanPhone.startsWith("+")) {
            cleanPhone = cleanPhone.substring(1);
        }

        try {
            // Use Desktop class to open default browser or registered app handlers
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://wa.me/" + cleanPhone));
            } else {
                AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", "Sisteminiz tarayıcı açmayı desteklemiyor.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata", "WhatsApp açılamadı: " + e.getMessage());
        }
    }
}
