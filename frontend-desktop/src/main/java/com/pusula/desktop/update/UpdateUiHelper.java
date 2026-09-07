package com.pusula.desktop.update;

import com.pusula.desktop.dto.UpdateInfoDTO;
import com.pusula.desktop.util.NotificationService;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class UpdateUiHelper {

    private UpdateUiHelper() {
    }

    public static VBox createUpdateBanner(
            UpdateInfoDTO dto,
            Runnable onInAppUpdate,
            Runnable onBrowserDownload) {
        VBox banner = new VBox(14);
        banner.getStyleClass().add("update-banner-modern");
        banner.setFillWidth(true);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setMinWidth(320);

        Label titleLabel = new Label("Yeni sürüm v" + dto.getLatestVersion() + " mevcut");
        titleLabel.getStyleClass().add("update-banner-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.prefWidthProperty().bind(
                Bindings.max(280, banner.widthProperty().subtract(32)));

        Button updateBtn = new Button("Güncelle");
        updateBtn.getStyleClass().addAll("update-btn", "update-btn-primary");
        updateBtn.setMaxWidth(Double.MAX_VALUE);
        updateBtn.setMinWidth(120);
        updateBtn.setOnAction(e -> onInAppUpdate.run());

        Button browserBtn = new Button("Tarayıcıdan indir");
        browserBtn.getStyleClass().addAll("update-btn-secondary");
        browserBtn.setMaxWidth(Double.MAX_VALUE);
        browserBtn.setMinWidth(120);
        browserBtn.setOnAction(e -> onBrowserDownload.run());

        VBox actions = new VBox(8, updateBtn, browserBtn);
        actions.getStyleClass().add("update-banner-actions");
        actions.setFillWidth(true);

        banner.getChildren().addAll(titleLabel, actions);
        return banner;
    }

    public static boolean showUpdateConfirmation(Window owner, String version) {
        String message = "Sürüm " + version + " indirilecek ve kurulacak.\n\n"
                + "• Uygulama kapanacak.\n"
                + "• Windows yönetici ve güvenlik onayı isteyebilir.\n"
                + "• Gerekirse ‘Evet’ veya ‘Yine de çalıştır’ seçeneğini kullanın.\n"
                + "• Kurulum tamamlandığında uygulama yeniden açılacak.";
        return NotificationService.confirm(owner, "Uygulamayı Güncelle", message);
    }
}
