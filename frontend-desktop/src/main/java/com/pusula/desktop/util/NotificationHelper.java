package com.pusula.desktop.util;

/**
 * Backward-compatible facade for workflows that previously used the legacy
 * standalone toast stage. All feedback now shares NotificationService.
 */
public final class NotificationHelper {
    private NotificationHelper() {}

    public static void showSuccess(String message) {
        NotificationService.toast(null, NotificationService.Kind.SUCCESS, "Başarılı", message);
    }

    public static void showError(String message) {
        NotificationService.modal(null, NotificationService.Kind.ERROR, "İşlem Tamamlanamadı", message);
    }

    public static void showWarning(String message) {
        NotificationService.toast(null, NotificationService.Kind.WARNING, "Dikkat", message);
    }

    public static void showInfo(String message) {
        NotificationService.toast(null, NotificationService.Kind.INFO, "Bilgi", message);
    }
}
