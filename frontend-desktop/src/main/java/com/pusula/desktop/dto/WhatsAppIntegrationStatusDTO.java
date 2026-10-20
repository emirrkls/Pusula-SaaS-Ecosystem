package com.pusula.desktop.dto;

import java.time.LocalDateTime;

public class WhatsAppIntegrationStatusDTO {
    private boolean connected;
    private String displayPhoneNumber;
    private String verifiedName;
    private String status;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime updatedAt;
    private SyncStatus sync;

    public boolean isConnected() { return connected; }
    public String getDisplayPhoneNumber() { return displayPhoneNumber; }
    public String getVerifiedName() { return verifiedName; }
    public String getStatus() { return status; }
    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public SyncStatus getSync() { return sync; }
    public static class SyncStatus {
        private String status;
        private String contactsStatus;
        private String historyStatus;
        private int historyProgress;
        public String getStatus() { return status; }
        public String getContactsStatus() { return contactsStatus; }
        public String getHistoryStatus() { return historyStatus; }
        public int getHistoryProgress() { return historyProgress; }
        public String description() {
            if ("ACTION_REQUIRED".equals(status)) return "Eşitleme kontrol gerektiriyor. Bağlantıyı silmeden destekle iletişime geçin.";
            if ("READY".equals(status)) return "DECLINED".equals(historyStatus)
                ? "Bağlantı hazır. Sohbet geçmişini paylaşmama tercihiniz korundu." : "Bağlantı hazır; WhatsApp güncellemeleri eşitleniyor.";
            if ("WAITING_WEBHOOKS".equals(status)) return "Bildirim bağlantısı hazırlanıyor.";
            return "Sohbet eşitlemesi sürüyor (%" + Math.max(0, Math.min(100, historyProgress)) + "). WhatsApp Business uygulamasını açık tutun.";
        }
    }
}
