package com.pusula.desktop.util;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Shared table/list UI helpers — badges, formatting, pills.
 */
public final class TableUiHelper {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(TR);

    static {
        CURRENCY.setMaximumFractionDigits(2);
        CURRENCY.setMinimumFractionDigits(2);
    }

    private TableUiHelper() {
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "₺0,00";
        }
        return CURRENCY.format(amount);
    }

    public static String toTitleCase(String text) {
        if (text == null || text.isBlank()) {
            return "—";
        }
        String[] parts = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String word = parts[i];
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1).toLowerCase(TR));
                }
            }
        }
        return sb.toString();
    }

    public static String avatarLetter(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase(TR);
    }

    public static void applyStatusBadge(Label badge, String status, ResourceBundle bundle) {
        badge.getStyleClass().removeIf(c -> c.startsWith("status-badge"));
        badge.getStyleClass().add("status-badge");
        String normalized = status != null ? status.trim().toUpperCase() : "";
        switch (normalized) {
            case "PENDING" -> badge.getStyleClass().add("status-badge-pending");
            case "ASSIGNED" -> badge.getStyleClass().add("status-badge-assigned");
            case "IN_PROGRESS" -> badge.getStyleClass().add("status-badge-in-progress");
            case "COMPLETED" -> badge.getStyleClass().add("status-badge-completed");
            case "CANCELLED" -> badge.getStyleClass().add("status-badge-cancelled");
            default -> badge.getStyleClass().add("status-badge-pending");
        }
        badge.setText(translateStatus(normalized, bundle));
    }

    public static void applyUnassignedBadge(Label badge, ResourceBundle bundle) {
        badge.getStyleClass().removeIf(c -> c.startsWith("status-badge"));
        badge.getStyleClass().addAll("status-badge", "badge-unassigned");
        badge.setText(bundle != null ? bundle.getString("dashboard.unassigned") : "Atanmadı");
    }

    public static String translateStatus(String status, ResourceBundle bundle) {
        if (bundle == null || status == null || status.isBlank()) {
            return status != null ? status : "—";
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING" -> bundle.getString("status.pending");
            case "IN_PROGRESS" -> bundle.getString("status.in_progress");
            case "COMPLETED" -> bundle.getString("status.completed");
            case "CANCELLED" -> bundle.getString("status.cancelled");
            case "ASSIGNED" -> bundle.getString("status.assigned");
            default -> status;
        };
    }

    public static HBox createDistributionPills(int warehouse, int vehicle) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        Label wh = new Label("Depo " + warehouse);
        wh.getStyleClass().addAll("mini-pill", "mini-pill-blue");
        Label veh = new Label("Araç " + vehicle);
        veh.getStyleClass().addAll("mini-pill", "mini-pill-green");
        box.getChildren().addAll(wh, veh);
        return box;
    }

    public static Label createCriticalBadge(int quantity, int criticalLevel) {
        Label badge = new Label(String.valueOf(quantity));
        if (criticalLevel > 0 && quantity <= criticalLevel) {
            badge.getStyleClass().addAll("mini-pill", "mini-pill-danger");
        }
        return badge;
    }

    public static Label createAvatar(String name) {
        Label avatar = new Label(avatarLetter(name));
        avatar.getStyleClass().add("customer-avatar");
        avatar.setAlignment(Pos.CENTER);
        return avatar;
    }

    public static String truncate(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "—";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
    }

    public static Region horizontalSpacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
