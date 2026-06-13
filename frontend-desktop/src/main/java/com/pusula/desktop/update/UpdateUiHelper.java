package com.pusula.desktop.update;

import com.pusula.desktop.dto.UpdateInfoDTO;
import com.pusula.desktop.util.ThemeHelper;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

public final class UpdateUiHelper {

    private static final double DIALOG_TEXT_WIDTH = 400;

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
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Güncelleme");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType confirm = new ButtonType("Devam et", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirm, cancel);

        VBox content = new VBox(14);
        content.setPadding(new Insets(4, 0, 0, 0));
        content.setPrefWidth(DIALOG_TEXT_WIDTH + 40);
        content.getStyleClass().add("update-dialog-content");

        Label lead = new Label("Sürüm " + version + " indirilecek ve kurulacak.");
        lead.getStyleClass().add("update-dialog-lead");
        lead.setWrapText(true);
        lead.setMaxWidth(DIALOG_TEXT_WIDTH);

        VBox bullets = new VBox(8);
        bullets.getChildren().addAll(
                bullet("Uygulama kapanacak."),
                bullet("Windows yönetici ve güvenlik onayı isteyebilir."),
                bullet("Gerekirse Evet veya Yine de çalıştır seçin."),
                bullet("Kurulum bitince uygulama yeniden açılacak.")
        );

        Label question = new Label("Devam edilsin mi?");
        question.getStyleClass().add("update-dialog-question");

        content.getChildren().addAll(lead, bullets, question);
        dialog.getDialogPane().setContent(content);
        applyDialogTheme(dialog);

        dialog.setOnShown(e -> {
            Button confirmBtn = (Button) dialog.getDialogPane().lookupButton(confirm);
            Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(cancel);
            if (confirmBtn != null) {
                confirmBtn.setMinWidth(Region.USE_PREF_SIZE);
                confirmBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);
                confirmBtn.setTextOverrun(OverrunStyle.CLIP);
            }
            if (cancelBtn != null) {
                cancelBtn.setMinWidth(Region.USE_PREF_SIZE);
                cancelBtn.setPrefWidth(Region.USE_COMPUTED_SIZE);
            }
        });

        return dialog.showAndWait().orElse(cancel) == confirm;
    }

    private static Label bullet(String text) {
        Label label = new Label("•  " + text);
        label.getStyleClass().add("update-dialog-bullet");
        label.setWrapText(true);
        label.setMaxWidth(DIALOG_TEXT_WIDTH);
        return label;
    }

    private static void applyDialogTheme(Dialog<?> dialog) {
        var pane = dialog.getDialogPane();
        String stylesUrl = ThemeHelper.class.getResource("/css/styles.css").toExternalForm();
        if (!pane.getStylesheets().contains(stylesUrl)) {
            pane.getStylesheets().add(stylesUrl);
        }
        pane.getStyleClass().add("update-dialog-pane");
    }
}
