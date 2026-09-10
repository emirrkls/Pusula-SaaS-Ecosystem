package com.pusula.desktop.controller;

import com.google.gson.JsonParser;
import com.pusula.desktop.api.ProposalApi;
import com.pusula.desktop.dto.ProposalDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class ProposalViewController {

    @FXML
    private TableView<ProposalDTO> proposalsTable;
    @FXML
    private TableColumn<ProposalDTO, String> colId;
    @FXML
    private TableColumn<ProposalDTO, String> colCustomer;
    @FXML
    private TableColumn<ProposalDTO, String> colPreparedBy;
    @FXML
    private TableColumn<ProposalDTO, String> colTotal;
    @FXML
    private TableColumn<ProposalDTO, String> colStatus;
    @FXML
    private TableColumn<ProposalDTO, String> colValidUntil;
    @FXML
    private TableColumn<ProposalDTO, Void> colActions;
    @FXML
    private ComboBox<String> statusFilter;
    @FXML
    private TextField searchField;

    private ProposalApi proposalApi;
    private ResourceBundle bundle;
    private List<ProposalDTO> allProposals;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages_tr");
        proposalApi = RetrofitClient.getClient().create(ProposalApi.class);

        setupTable();
        setupFilters();
        loadProposals();
    }

    private void setupTable() {
        colId.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getId() != null ? "T-" + data.getValue().getId() : "-"));

        colCustomer.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCustomerName() != null ? data.getValue().getCustomerName() : "-"));

        colPreparedBy.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPreparedByName() != null ? data.getValue().getPreparedByName() : "-"));

        colTotal.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getTotalPrice() != null ? String.format("%.2f ₺", data.getValue().getTotalPrice())
                        : "-"));

        colStatus.setCellValueFactory(data -> new SimpleStringProperty(
                translateStatus(data.getValue().getStatus())));

        colValidUntil.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getValidUntil() != null
                        ? data.getValue().getValidUntil().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "-"));

        colActions.setCellFactory(param -> new TableCell<>() {
            private final MenuButton actionsBtn = new MenuButton("İşlemler");
            private final MenuItem editItem = new MenuItem("Düzenle");
            private final MenuItem pdfItem = new MenuItem("PDF Görüntüle");
            private final MenuItem convertItem = new MenuItem("İşe Dönüştür");

            {
                actionsBtn.getItems().addAll(editItem, pdfItem, new SeparatorMenuItem(), convertItem);
                actionsBtn.getStyleClass().addAll("button-sm", "button-secondary", "action-menu-button");
                actionsBtn.setAccessibleText("Teklif işlemleri");
                editItem.setOnAction(e -> handleEdit(getTableRow().getItem()));
                pdfItem.setOnAction(e -> handlePdf(getTableRow().getItem()));
                convertItem.setOnAction(e -> handleConvert(getTableRow().getItem(), actionsBtn));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ProposalDTO proposal = getTableRow().getItem();
                    convertItem.setDisable("APPROVED".equals(proposal.getStatus()) ||
                            "REJECTED".equals(proposal.getStatus()));
                    setGraphic(actionsBtn);
                }
            }
        });
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Tümü", "Taslak", "Gönderildi", "Onaylandı", "Reddedildi"));
        statusFilter.setValue("Tümü");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTable());
    }

    private void loadProposals() {
        proposalApi.getAllProposals().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<ProposalDTO>> call, Response<List<ProposalDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        allProposals = response.body();
                        filterTable();
                    }
                });
            }

            @Override
            public void onFailure(Call<List<ProposalDTO>> call, Throwable t) {
                Platform.runLater(() -> showError("Teklifler yüklenemedi: " + t.getMessage()));
            }
        });
    }

    private void filterTable() {
        if (allProposals == null)
            return;

        String statusFilterValue = statusFilter.getValue();
        String searchText = searchField.getText() != null
                ? searchField.getText().trim().toLowerCase(Locale.forLanguageTag("tr-TR"))
                : "";

        List<ProposalDTO> filtered = allProposals.stream()
                .filter(p -> {
                    if (!"Tümü".equals(statusFilterValue)) {
                        String translatedStatus = translateStatus(p.getStatus());
                        if (!translatedStatus.equals(statusFilterValue))
                            return false;
                    }
                    if (!searchText.isEmpty()) {
                        String customer = searchable(p.getCustomerName());
                        String preparedBy = searchable(p.getPreparedByName());
                        String title = searchable(p.getTitle());
                        String proposalNo = p.getId() != null ? String.valueOf(p.getId()) : "";
                        return customer.contains(searchText)
                                || preparedBy.contains(searchText)
                                || title.contains(searchText)
                                || proposalNo.contains(searchText);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        proposalsTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private String searchable(String value) {
        return value == null ? "" : value.toLowerCase(Locale.forLanguageTag("tr-TR"));
    }

    @FXML
    private void handleRefresh() {
        loadProposals();
    }

    @FXML
    private void handleNewProposal() {
        openEditor(null);
    }

    @FXML
    private void handleFilterChange() {
        filterTable();
    }

    private void handleEdit(ProposalDTO proposal) {
        openEditor(proposal);
    }

    private void handlePdf(ProposalDTO proposal) {
        proposalApi.getPdf(proposal.getId()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            // Save to temp file
                            File tempFile = File.createTempFile("teklif-" + proposal.getId(), ".pdf");
                            tempFile.deleteOnExit();

                            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                                fos.write(response.body().bytes());
                            }

                            // Open with default PDF viewer
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(tempFile);
                            } else {
                                showInfo("PDF kaydedildi: " + tempFile.getAbsolutePath());
                            }
                        } catch (Exception e) {
                            showError("PDF açılamadı: " + e.getMessage());
                        }
                    } else {
                        showError("PDF oluşturulamadı.");
                    }
                });
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Platform.runLater(() -> showError("Hata: " + t.getMessage()));
            }
        });
    }

    private void handleConvert(ProposalDTO proposal, Control actionButton) {
        if (AlertHelper.showConfirmation(proposalsTable.getScene().getWindow(), "Teklifi İşe Dönüştür",
                "Bu teklif onaylanıp servis fişine dönüştürülsün mü?")) {
                actionButton.setDisable(true);
                proposalApi.convertToJob(proposal.getId()).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<ProposalDTO> call, Response<ProposalDTO> response) {
                        String errorMessage = response.isSuccessful()
                                ? null
                                : com.pusula.desktop.util.ApiErrorHelper.message(
                                        response, "Dönüştürme başarısız.");
                        Platform.runLater(() -> {
                            if (response.isSuccessful()) {
                                showInfo("Teklif başarıyla işe dönüştürüldü!");
                                loadProposals();
                            } else {
                                actionButton.setDisable(false);
                                showError(errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<ProposalDTO> call, Throwable t) {
                        Platform.runLater(() -> {
                            actionButton.setDisable(false);
                            showError("Hata: " + t.getMessage());
                        });
                    }
                });
        }
    }

    private void openEditor(ProposalDTO proposal) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/proposal_editor.fxml"), bundle);
            Parent root = loader.load();

            ProposalEditorController controller = loader.getController();
            controller.setProposal(proposal);
            controller.setOnSaveCallback(this::loadProposals);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            Window owner = proposalsTable.getScene().getWindow();
            stage.initOwner(owner);
            stage.setTitle(proposal == null ? "Yeni Teklif" : "Teklif Düzenle");
            stage.setScene(com.pusula.desktop.util.ThemeHelper.createDialogScene(root));
            com.pusula.desktop.util.ThemeHelper.configureDialogStage(stage, owner,
                    com.pusula.desktop.util.ThemeHelper.DialogProfile.DETAIL);
            stage.showAndWait();
        } catch (IOException e) {
            showError("Teklif formu açılamadı: " + e.getMessage());
        }
    }

    private String translateStatus(String status) {
        if (status == null)
            return "-";
        switch (status) {
            case "DRAFT":
                return "Taslak";
            case "SENT":
                return "Gönderildi";
            case "APPROVED":
                return "Onaylandı";
            case "REJECTED":
                return "Reddedildi";
            default:
                return status;
        }
    }

    private void showError(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, proposalsTable.getScene().getWindow(), "Hata", message);
    }

    private void showInfo(String message) {
        AlertHelper.showSuccess(proposalsTable.getScene().getWindow(), "Başarılı", message);
    }
}
