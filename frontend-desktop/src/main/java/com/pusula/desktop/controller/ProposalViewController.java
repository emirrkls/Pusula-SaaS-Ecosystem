package com.pusula.desktop.controller;

import com.pusula.desktop.api.ProposalApi;
import com.pusula.desktop.dto.ProposalDTO;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
            private final Button editBtn = new Button("Düzenle");
            private final Button pdfBtn = new Button("PDF");
            private final Button convertBtn = new Button("İşe Dönüştür");
            private final HBox box = new HBox(5, editBtn, pdfBtn, convertBtn);

            {
                editBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10px;");
                pdfBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-size: 10px;");
                convertBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 10px;");

                editBtn.setOnAction(e -> handleEdit(getTableRow().getItem()));
                pdfBtn.setOnAction(e -> handlePdf(getTableRow().getItem()));
                convertBtn.setOnAction(e -> handleConvert(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ProposalDTO proposal = getTableRow().getItem();
                    convertBtn.setDisable("APPROVED".equals(proposal.getStatus()) ||
                            "REJECTED".equals(proposal.getStatus()));
                    setGraphic(box);
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
        String searchText = searchField.getText() != null ? searchField.getText().toLowerCase() : "";

        List<ProposalDTO> filtered = allProposals.stream()
                .filter(p -> {
                    if (!"Tümü".equals(statusFilterValue)) {
                        String translatedStatus = translateStatus(p.getStatus());
                        if (!translatedStatus.equals(statusFilterValue))
                            return false;
                    }
                    if (!searchText.isEmpty()) {
                        String customer = p.getCustomerName() != null ? p.getCustomerName().toLowerCase() : "";
                        String preparedBy = p.getPreparedByName() != null ? p.getPreparedByName().toLowerCase() : "";
                        return customer.contains(searchText) || preparedBy.contains(searchText);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        proposalsTable.setItems(FXCollections.observableArrayList(filtered));
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

    private void handleConvert(ProposalDTO proposal) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Onay");
        confirm.setHeaderText("Teklifi işe dönüştür");
        confirm.setContentText("Bu teklif onaylanıp servis fişine dönüştürülsün mü?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                proposalApi.convertToJob(proposal.getId()).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<ProposalDTO> call, Response<ProposalDTO> response) {
                        Platform.runLater(() -> {
                            if (response.isSuccessful()) {
                                showInfo("Teklif başarıyla işe dönüştürüldü!");
                                loadProposals();
                            } else {
                                showError("Dönüştürme başarısız.");
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<ProposalDTO> call, Throwable t) {
                        Platform.runLater(() -> showError("Hata: " + t.getMessage()));
                    }
                });
            }
        });
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
            stage.setTitle(proposal == null ? "Yeni Teklif" : "Teklif Düzenle");
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Editor açılamadı: " + e.getMessage());
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
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Hata");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Bilgi");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
