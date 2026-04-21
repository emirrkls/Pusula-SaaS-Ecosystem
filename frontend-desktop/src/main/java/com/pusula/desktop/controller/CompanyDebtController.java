package com.pusula.desktop.controller;

import com.pusula.desktop.api.CompanyDebtApi;
import com.pusula.desktop.dto.CompanyDebtDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.CurrencyTextField;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.stage.Modality;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompanyDebtController {

    @FXML
    private TableView<CompanyDebtDTO> debtTable;
    @FXML
    private TableColumn<CompanyDebtDTO, String> colCreditor;
    @FXML
    private TableColumn<CompanyDebtDTO, String> colDescription;
    @FXML
    private TableColumn<CompanyDebtDTO, String> colOriginal;
    @FXML
    private TableColumn<CompanyDebtDTO, String> colRemaining;
    @FXML
    private TableColumn<CompanyDebtDTO, String> colDebtDate;
    @FXML
    private TableColumn<CompanyDebtDTO, String> colDueDate;
    @FXML
    private TableColumn<CompanyDebtDTO, String> colStatus;
    @FXML
    private TableColumn<CompanyDebtDTO, Void> colActions;
    @FXML
    private ComboBox<String> statusFilter;
    @FXML
    private Label totalDebtLabel;

    private CompanyDebtApi api;
    private ObservableList<CompanyDebtDTO> debts = FXCollections.observableArrayList();
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("tr", "TR"));

    @FXML
    public void initialize() {
        api = RetrofitClient.getClient().create(CompanyDebtApi.class);

        setupTable();
        setupFilters();
        loadDebts();
        loadTotalDebt();
    }

    private void setupTable() {
        colCreditor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCreditorName()));
        colDescription.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDescription() != null ? data.getValue().getDescription() : ""));

        colOriginal.setCellValueFactory(data -> new SimpleStringProperty(
                formatCurrency(data.getValue().getOriginalAmount())));
        colRemaining.setCellValueFactory(data -> new SimpleStringProperty(
                formatCurrency(data.getValue().getRemainingAmount())));

        colDebtDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDebtDate() != null ? data.getValue().getDebtDate().format(dateFormatter) : ""));
        colDueDate.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDueDate() != null ? data.getValue().getDueDate().format(dateFormatter) : "-"));

        colStatus.setCellValueFactory(data -> new SimpleStringProperty(getStatusText(data.getValue().getStatus())));

        // Style remaining column based on value
        colRemaining.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null && debt.getRemainingAmount() != null) {
                        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                            setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #27ae60;");
                        }
                    }
                }
            }
        });

        setupActionsColumn();
        debtTable.setItems(debts);
    }

    private void setupActionsColumn() {
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button payBtn = new Button("Öde");
            private final Button addBtn = new Button("İlave");
            private final Button deleteBtn = new Button("Sil");
            private final HBox box = new HBox(8, payBtn, addBtn, deleteBtn);

            {
                box.setStyle("-fx-alignment: center;");
                payBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11; -fx-cursor: hand; -fx-padding: 5 10; -fx-min-width: 50;");
                addBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 11; -fx-cursor: hand; -fx-padding: 5 10; -fx-min-width: 50;");
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11; -fx-cursor: hand; -fx-padding: 5 10; -fx-min-width: 50;");

                payBtn.setOnAction(e -> {
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null) {
                        handlePayDebt(debt);
                    }
                });

                addBtn.setOnAction(e -> {
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null) {
                        handleAddAmountToDebt(debt);
                    }
                });

                deleteBtn.setOnAction(e -> {
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null) {
                        handleDeleteDebt(debt);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null && "PAID".equals(debt.getStatus())) {
                        payBtn.setDisable(true);
                    } else {
                        payBtn.setDisable(false);
                    }
                    setGraphic(box);
                }
            }
        });
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Tümü", "Ödenmedi", "Kısmi Ödeme", "Ödendi"));
        statusFilter.setValue("Tümü");
    }

    @FXML
    public void loadDebts() {
        api.getAllDebts(1L).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<CompanyDebtDTO>> call, Response<List<CompanyDebtDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        debts.setAll(response.body());
                    }
                });
            }

            @Override
            public void onFailure(Call<List<CompanyDebtDTO>> call, Throwable t) {
                Platform.runLater(() -> showError("Borçlar yüklenemedi: " + t.getMessage()));
            }
        });
    }

    private void loadTotalDebt() {
        api.getTotalUnpaidDebt(1L).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<Map<String, BigDecimal>> call, Response<Map<String, BigDecimal>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        BigDecimal total = response.body().get("totalUnpaid");
                        totalDebtLabel.setText("Toplam Borç: " + formatCurrency(total));
                    }
                });
            }

            @Override
            public void onFailure(Call<Map<String, BigDecimal>> call, Throwable t) {
            }
        });
    }

    @FXML
    public void applyFilter() {
        String selected = statusFilter.getValue();
        if (selected == null || "Tümü".equals(selected)) {
            loadDebts();
        } else {
            // Filter locally
            api.getAllDebts(1L).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<List<CompanyDebtDTO>> call, Response<List<CompanyDebtDTO>> response) {
                    Platform.runLater(() -> {
                        if (response.isSuccessful() && response.body() != null) {
                            String statusToFilter = switch (selected) {
                                case "Ödenmedi" -> "UNPAID";
                                case "Kısmi Ödeme" -> "PARTIAL";
                                case "Ödendi" -> "PAID";
                                default -> null;
                            };
                            if (statusToFilter != null) {
                                debts.setAll(response.body().stream()
                                        .filter(d -> statusToFilter.equals(d.getStatus()))
                                        .toList());
                            } else {
                                debts.setAll(response.body());
                            }
                        }
                    });
                }

                @Override
                public void onFailure(Call<List<CompanyDebtDTO>> call, Throwable t) {
                }
            });
        }
    }

    @FXML
    public void handleAddDebt() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/company_debt_dialog.fxml"));
            Parent root = loader.load();

            CompanyDebtDialogController controller = loader.getController();
            controller.setOnSave(() -> {
                loadDebts();
                loadTotalDebt();
            });

            Stage dialog = new Stage();
            dialog.setTitle("Borç Ekle");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (Exception e) {
            showError("Dialog açılamadı: " + e.getMessage());
        }
    }

    private void handlePayDebt(CompanyDebtDTO debt) {
        TextInputDialog dialog = new TextInputDialog(debt.getRemainingAmount().toString());
        dialog.setTitle("Borç Öde");
        dialog.setHeaderText(debt.getCreditorName() + " - Kalan: " + formatCurrency(debt.getRemainingAmount()));
        dialog.setContentText("Ödeme Tutarı (₺):");

        dialog.showAndWait().ifPresent(input -> {
            try {
                BigDecimal amount = CurrencyTextField.parseTurkishCurrency(input);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    showError("Tutar sıfırdan büyük olmalıdır!");
                    return;
                }

                api.payDebt(debt.getId(), amount).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<CompanyDebtDTO> call, Response<CompanyDebtDTO> response) {
                        Platform.runLater(() -> {
                            if (response.isSuccessful()) {
                                showInfo("Ödeme başarılı!");
                                loadDebts();
                                loadTotalDebt();
                            } else {
                                showError("Ödeme başarısız!");
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<CompanyDebtDTO> call, Throwable t) {
                        Platform.runLater(() -> showError("Hata: " + t.getMessage()));
                    }
                });
            } catch (NumberFormatException e) {
                showError("Geçersiz tutar formatı!");
            }
        });
    }

    private void handleAddAmountToDebt(CompanyDebtDTO debt) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Borca İlave Yap");
        dialog.setHeaderText(debt.getCreditorName() + " borcuna ilave tutar ekleniyor.");

        ButtonType saveButtonType = new ButtonType("Ekle", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 50, 10, 10));

        CurrencyTextField amountField = new CurrencyTextField();
        amountField.setPromptText("0,00");
        TextField notesField = new TextField();
        notesField.setPromptText("Örn: X malzemesi alındı");

        grid.add(new Label("Tutar (₺):"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Açıklama:"), 0, 1);
        grid.add(notesField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return Map.of("amount", amountField.getText(), "notes", notesField.getText());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                BigDecimal amount = amountField.getRawValue();
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    showError("Tutar sıfırdan büyük olmalıdır!");
                    return;
                }

                String notes = result.get("notes");
                api.addDebtAmount(debt.getId(), amount, notes).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<CompanyDebtDTO> call, Response<CompanyDebtDTO> response) {
                        Platform.runLater(() -> {
                            if (response.isSuccessful()) {
                                showInfo("Borca ilave başarıyla eklendi!");
                                loadDebts();
                                loadTotalDebt();
                            } else {
                                showError("İşlem başarısız oldu!");
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<CompanyDebtDTO> call, Throwable t) {
                        Platform.runLater(() -> showError("Hata: " + t.getMessage()));
                    }
                });
            } catch (NumberFormatException e) {
                showError("Geçersiz tutar formatı!");
            }
        });
    }

    private void handleDeleteDebt(CompanyDebtDTO debt) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Borç Sil");
        confirm.setHeaderText("Bu borcu silmek istediğinize emin misiniz?");
        confirm.setContentText(debt.getCreditorName() + " - " + formatCurrency(debt.getOriginalAmount()));

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                api.deleteDebt(debt.getId()).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        Platform.runLater(() -> {
                            loadDebts();
                            loadTotalDebt();
                        });
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Platform.runLater(() -> showError("Silme hatası: " + t.getMessage()));
                    }
                });
            }
        });
    }

    private String getStatusText(String status) {
        if (status == null)
            return "";
        return switch (status) {
            case "UNPAID" -> "Ödenmedi";
            case "PARTIAL" -> "Kısmi Ödeme";
            case "PAID" -> "Ödendi";
            default -> status;
        };
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "0 ₺";
        return String.format("%,.2f ₺", amount);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Hata");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Bilgi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
