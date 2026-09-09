package com.pusula.desktop.controller;

import com.pusula.desktop.api.CompanyDebtApi;
import com.pusula.desktop.dto.CompanyDebtDTO;
import com.pusula.desktop.dto.CompanyDebtPaymentDTO;
import com.pusula.desktop.dto.DebtAdditionRequestDTO;
import com.pusula.desktop.dto.DebtPaymentRequestDTO;
import com.pusula.desktop.dto.PayablePartySummaryDTO;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
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
import javafx.stage.FileChooser;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.io.File;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
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
    private TableColumn<CompanyDebtDTO, String> colCategory;
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
    @FXML private TableView<PayablePartySummaryDTO> partyTable;
    @FXML private TableColumn<PayablePartySummaryDTO, String> colPartyName;
    @FXML private TableColumn<PayablePartySummaryDTO, String> colPartyPurchases;
    @FXML private TableColumn<PayablePartySummaryDTO, String> colPartyPaid;
    @FXML private TableColumn<PayablePartySummaryDTO, String> colPartyBalance;
    @FXML private TableColumn<PayablePartySummaryDTO, String> colPartyFirstDate;
    @FXML private TableColumn<PayablePartySummaryDTO, String> colPartyLastDate;
    @FXML private TableColumn<PayablePartySummaryDTO, Void> colPartyActions;
    @FXML private Label debtDetailLabel;

    private CompanyDebtApi api;
    private ObservableList<CompanyDebtDTO> debts = FXCollections.observableArrayList();
    private PayablePartySummaryDTO selectedPayableParty;
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("tr", "TR"));

    @FXML
    public void initialize() {
        api = RetrofitClient.getClient().create(CompanyDebtApi.class);

        setupTable();
        setupPartyTable();
        setupFilters();
        loadDebts();
        loadParties();
        loadTotalDebt();
    }

    private void setupTable() {
        colCreditor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCreditorName()));
        colDescription.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDescription() != null ? data.getValue().getDescription() : ""));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(
                getCategoryText(data.getValue().getExpenseCategory())));

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
                    getStyleClass().removeAll("amount-positive", "amount-negative");
                } else {
                    setText(item);
                    getStyleClass().removeAll("amount-positive", "amount-negative");
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null && debt.getRemainingAmount() != null) {
                        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                            getStyleClass().add("amount-negative");
                        } else {
                            getStyleClass().add("amount-positive");
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
            private final MenuButton moreBtn = new MenuButton("Diğer");
            private final MenuItem historyItem = new MenuItem("Ödeme Geçmişi");
            private final MenuItem addItem = new MenuItem("Borca İlave Yap");
            private final MenuItem deleteItem = new MenuItem("Borcu Sil");
            private final HBox box = new HBox(6, payBtn, moreBtn);

            {
                box.getStyleClass().add("table-actions");
                payBtn.getStyleClass().addAll("btn-success", "btn-sm");
                moreBtn.getStyleClass().addAll("btn-secondary", "btn-sm", "action-menu-button");
                moreBtn.getItems().addAll(historyItem, addItem, new SeparatorMenuItem(), deleteItem);
                moreBtn.setAccessibleText("Diğer borç işlemleri");

                payBtn.setOnAction(e -> {
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null) {
                        handlePayDebt(debt);
                    }
                });

                historyItem.setOnAction(e -> {
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null) {
                        handlePaymentHistory(debt);
                    }
                });

                addItem.setOnAction(e -> {
                    CompanyDebtDTO debt = getTableRow().getItem();
                    if (debt != null) {
                        handleAddAmountToDebt(debt);
                    }
                });

                deleteItem.setOnAction(e -> {
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
        selectedPayableParty = null;
        if (debtDetailLabel != null) debtDetailLabel.setText("Tüm Borç Hareketleri");
        api.getAllDebts().enqueue(new Callback<>() {
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
        api.getTotalUnpaidDebt().enqueue(new Callback<>() {
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
            api.getAllDebts().enqueue(new Callback<>() {
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
        openDebtDialog(null);
    }

    private void openDebtDialog(PayablePartySummaryDTO supplier) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/company_debt_dialog.fxml"));
            Parent root = loader.load();

            CompanyDebtDialogController controller = loader.getController();
            if (supplier != null) {
                controller.setSupplierCard(supplier.getPartyId(), supplier.getName(), supplier.getPhone());
            }
            controller.setOnSave(() -> {
                refreshDebtRows();
                loadTotalDebt();
                loadParties();
            });

            Stage dialog = new Stage();
            dialog.setTitle("Borç Ekle");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(com.pusula.desktop.util.ThemeHelper.createDialogScene(root));
            dialog.showAndWait();
        } catch (Exception e) {
            showError("Dialog açılamadı: " + e.getMessage());
        }
    }

    private void handlePayDebt(CompanyDebtDTO debt) {
        Dialog<DebtPaymentRequestDTO> dialog = new Dialog<>();
        com.pusula.desktop.util.ThemeHelper.applyToDialog(dialog, debtTable.getScene().getWindow());
        dialog.setTitle("Borç Öde");
        dialog.setHeaderText(debt.getCreditorName() + " - Kalan: " + formatCurrency(debt.getRemainingAmount()));
        ButtonType payButtonType = new ButtonType("Ödemeyi Kaydet", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(payButtonType, ButtonType.CANCEL);

        CurrencyTextField amountField = new CurrencyTextField();
        amountField.setRawValue(debt.getRemainingAmount());
        DatePicker paymentDatePicker = new DatePicker(LocalDate.now());
        paymentDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(debt.getDebtDate()) || date.isAfter(LocalDate.now()));
            }
        });
        TextArea notesField = new TextArea();
        notesField.setPromptText("Ödeme notu (isteğe bağlı)");
        notesField.setPrefRowCount(2);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.add(new Label("Gider Kategorisi:"), 0, 0);
        grid.add(new Label(getCategoryText(debt.getExpenseCategory())), 1, 0);
        grid.add(new Label("Ödeme Tutarı (₺):"), 0, 1);
        grid.add(amountField, 1, 1);
        grid.add(new Label("Ödeme Tarihi:"), 0, 2);
        grid.add(paymentDatePicker, 1, 2);
        grid.add(new Label("Not:"), 0, 3);
        grid.add(notesField, 1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == payButtonType
                ? DebtPaymentRequestDTO.builder()
                        .amount(amountField.getRawValue())
                        .paymentDate(paymentDatePicker.getValue())
                        .notes(notesField.getText())
                        .build()
                : null);

        dialog.showAndWait().ifPresent(request -> {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                showError("Tutar sıfırdan büyük olmalıdır!");
                return;
            }
            if (request.getAmount().compareTo(debt.getRemainingAmount()) > 0) {
                showError("Ödeme tutarı kalan borçtan fazla olamaz!");
                return;
            }
            if (request.getPaymentDate() == null) {
                showError("Ödeme tarihi zorunludur!");
                return;
            }

            api.payDebt(debt.getId(), request).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<CompanyDebtDTO> call, Response<CompanyDebtDTO> response) {
                    Platform.runLater(() -> {
                        if (response.isSuccessful()) {
                            showInfo("Ödeme seçilen tarihe kaydedildi!");
                            refreshDebtRows();
                            loadTotalDebt();
                            loadParties();
                        } else {
                            showError("Ödeme kaydedilemedi: " + response.code());
                        }
                    });
                }

                @Override
                public void onFailure(Call<CompanyDebtDTO> call, Throwable t) {
                    Platform.runLater(() -> showError("Hata: " + t.getMessage()));
                }
            });
        });
    }

    private void setupPartyTable() {
        colPartyName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colPartyPurchases.setCellValueFactory(data -> new SimpleStringProperty(formatCurrency(data.getValue().getTotalPurchases())));
        colPartyPaid.setCellValueFactory(data -> new SimpleStringProperty(formatCurrency(data.getValue().getTotalPaid())));
        colPartyBalance.setCellValueFactory(data -> new SimpleStringProperty(formatCurrency(data.getValue().getBalance())));
        colPartyFirstDate.setCellValueFactory(data -> new SimpleStringProperty(formatDate(data.getValue().getFirstDebtDate())));
        colPartyLastDate.setCellValueFactory(data -> new SimpleStringProperty(formatDate(data.getValue().getLastMovementDate())));
        colPartyActions.setCellFactory(column -> new TableCell<>() {
            private final Button details = new Button("Hareketler");
            private final Button add = new Button("Alım Ekle");
            private final Button pay = new Button("Öde");
            private final HBox box = new HBox(6, details, add, pay);
            {
                details.getStyleClass().addAll("btn-secondary", "btn-sm");
                add.getStyleClass().addAll("btn-primary", "btn-sm");
                pay.getStyleClass().addAll("btn-success", "btn-sm");
                details.setOnAction(event -> showPartyDebts(getTableRow().getItem()));
                add.setOnAction(event -> openDebtDialog(getTableRow().getItem()));
                pay.setOnAction(event -> handlePayParty(getTableRow().getItem()));
            }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                PayablePartySummaryDTO party = empty ? null : getTableRow().getItem();
                pay.setDisable(party == null || party.getBalance() == null || party.getBalance().signum() <= 0);
                setGraphic(party == null ? null : box);
            }
        });
        partyTable.setRowFactory(table -> {
            TableRow<PayablePartySummaryDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (!row.isEmpty() && event.getClickCount() == 2) showPartyDebts(row.getItem()); });
            return row;
        });
    }

    private String formatDate(LocalDate value) { return value == null ? "-" : value.format(dateFormatter); }

    private void loadParties() {
        api.getPayableParties().enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<PayablePartySummaryDTO>> call,
                    Response<List<PayablePartySummaryDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        partyTable.setItems(FXCollections.observableArrayList(response.body()));
                    }
                });
            }
            @Override public void onFailure(Call<List<PayablePartySummaryDTO>> call, Throwable throwable) {
                Platform.runLater(() -> showError("Tedarikçi kartları yüklenemedi: " + throwable.getMessage()));
            }
        });
    }

    private void showPartyDebts(PayablePartySummaryDTO party) {
        if (party == null) return;
        selectedPayableParty = party;
        api.getPartyDebts(party.getPartyId()).enqueue(new Callback<>() {
            @Override public void onResponse(Call<List<CompanyDebtDTO>> call, Response<List<CompanyDebtDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        debts.setAll(response.body());
                        debtDetailLabel.setText(party.getName() + " · Alım ve Hareketler");
                    }
                });
            }
            @Override public void onFailure(Call<List<CompanyDebtDTO>> call, Throwable throwable) {
                Platform.runLater(() -> showError("Kart hareketleri yüklenemedi: " + throwable.getMessage()));
            }
        });
    }

    private void refreshDebtRows() {
        if (selectedPayableParty != null) showPartyDebts(selectedPayableParty);
        else loadDebts();
    }

    private void handlePayParty(PayablePartySummaryDTO party) {
        if (party == null || party.getBalance() == null || party.getBalance().signum() <= 0) return;
        Dialog<DebtPaymentRequestDTO> dialog = new Dialog<>();
        com.pusula.desktop.util.ThemeHelper.applyToDialog(dialog, partyTable.getScene().getWindow());
        dialog.setTitle("Tedarikçi Kartına Ödeme");
        dialog.setHeaderText(party.getName() + " · Kalan " + formatCurrency(party.getBalance()));
        ButtonType save = new ButtonType("Ödemeyi Kaydet", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        CurrencyTextField amount = new CurrencyTextField(); amount.setRawValue(party.getBalance());
        DatePicker date = new DatePicker(LocalDate.now());
        TextArea notes = new TextArea(); notes.setPromptText("Ödeme notu (isteğe bağlı)"); notes.setPrefRowCount(2);
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Tutar (₺):"), amount);
        grid.addRow(1, new Label("Ödeme tarihi:"), date);
        grid.addRow(2, new Label("Not:"), notes);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button == save ? DebtPaymentRequestDTO.builder()
                .amount(amount.getRawValue()).paymentDate(date.getValue()).notes(notes.getText()).build() : null);
        dialog.showAndWait().ifPresent(request -> api.payParty(party.getPartyId(), request).enqueue(new Callback<>() {
            @Override public void onResponse(Call<PayablePartySummaryDTO> call,
                    Response<PayablePartySummaryDTO> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) {
                        showInfo("Ödeme en eski açık alımdan başlayarak tedarikçi kartına işlendi.");
                        loadParties(); refreshDebtRows(); loadTotalDebt();
                    } else showError("Ödeme kaydedilemedi: " + response.code());
                });
            }
            @Override public void onFailure(Call<PayablePartySummaryDTO> call, Throwable throwable) {
                Platform.runLater(() -> showError("Ödeme kaydedilemedi: " + throwable.getMessage()));
            }
        }));
    }

    private void handlePaymentHistory(CompanyDebtDTO debt) {
        api.getPayments(debt.getId()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<CompanyDebtPaymentDTO>> call,
                    Response<List<CompanyDebtPaymentDTO>> response) {
                Platform.runLater(() -> {
                    if (!response.isSuccessful() || response.body() == null) {
                        showError("Ödeme geçmişi yüklenemedi: " + response.code());
                        return;
                    }
                    showPaymentHistoryDialog(debt, response.body());
                });
            }

            @Override
            public void onFailure(Call<List<CompanyDebtPaymentDTO>> call, Throwable t) {
                Platform.runLater(() -> showError("Ödeme geçmişi yüklenemedi: " + t.getMessage()));
            }
        });
    }

    private void showPaymentHistoryDialog(CompanyDebtDTO debt, List<CompanyDebtPaymentDTO> payments) {
        Dialog<Void> dialog = new Dialog<>();
        com.pusula.desktop.util.ThemeHelper.applyToDialog(dialog, debtTable.getScene().getWindow());
        dialog.setTitle("Ödeme Geçmişi");
        dialog.setHeaderText(debt.getCreditorName() + " - " + getCategoryText(debt.getExpenseCategory()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(720);

        TableView<CompanyDebtPaymentDTO> table = new TableView<>();
        TableColumn<CompanyDebtPaymentDTO, String> dateColumn = new TableColumn<>("Ödeme Tarihi");
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPaymentDate().format(dateFormatter)));
        TableColumn<CompanyDebtPaymentDTO, String> amountColumn = new TableColumn<>("Tutar");
        amountColumn.setCellValueFactory(data -> new SimpleStringProperty(formatCurrency(data.getValue().getAmount())));
        TableColumn<CompanyDebtPaymentDTO, String> categoryColumn = new TableColumn<>("Kategori");
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(
                getCategoryText(data.getValue().getExpenseCategory())));
        TableColumn<CompanyDebtPaymentDTO, String> notesColumn = new TableColumn<>("Not");
        notesColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getNotes() != null ? data.getValue().getNotes() : ""));
        TableColumn<CompanyDebtPaymentDTO, Void> actionColumn = new TableColumn<>("İşlem");
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button undoButton = new Button("Geri Al");
            {
                undoButton.getStyleClass().add("btn-danger");
                undoButton.setOnAction(event -> {
                    CompanyDebtPaymentDTO payment = getTableView().getItems().get(getIndex());
                    undoPayment(debt, payment, dialog);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : undoButton);
            }
        });

        table.getColumns().addAll(dateColumn, amountColumn, categoryColumn, notesColumn, actionColumn);
        table.setItems(FXCollections.observableArrayList(payments));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320);
        table.setPlaceholder(new Label("Bu borç için kayıtlı ödeme bulunmuyor."));
        dialog.getDialogPane().setContent(table);
        dialog.showAndWait();
    }

    private void undoPayment(CompanyDebtDTO debt, CompanyDebtPaymentDTO payment, Dialog<Void> historyDialog) {
        if (!AlertHelper.showConfirmation(debtTable.getScene().getWindow(), "Ödemeyi Geri Al",
                payment.getPaymentDate().format(dateFormatter) + " tarihli "
                        + formatCurrency(payment.getAmount())
                        + " ödeme ve bağlı finans gideri geri alınsın mı?")) {
            return;
        }

        api.deletePayment(debt.getId(), payment.getId()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<CompanyDebtDTO> call, Response<CompanyDebtDTO> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) {
                        historyDialog.close();
                        refreshDebtRows();
                        loadTotalDebt();
                        loadParties();
                        showInfo("Ödeme ve bağlı finans gideri geri alındı.");
                    } else {
                        showError("Ödeme geri alınamadı: " + response.code());
                    }
                });
            }

            @Override
            public void onFailure(Call<CompanyDebtDTO> call, Throwable t) {
                Platform.runLater(() -> showError("Ödeme geri alınamadı: " + t.getMessage()));
            }
        });
    }

    private void handleAddAmountToDebt(CompanyDebtDTO debt) {
        Dialog<DebtAdditionRequestDTO> dialog = new Dialog<>();
        com.pusula.desktop.util.ThemeHelper.applyToDialog(dialog, debtTable.getScene().getWindow());
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
        DatePicker additionDatePicker = new DatePicker(LocalDate.now());
        additionDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(debt.getDebtDate()) || date.isAfter(LocalDate.now()));
            }
        });

        grid.add(new Label("Tutar (₺):"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("İlave Tarihi:"), 0, 1);
        grid.add(additionDatePicker, 1, 1);
        grid.add(new Label("Açıklama:"), 0, 2);
        grid.add(notesField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return DebtAdditionRequestDTO.builder()
                        .amount(amountField.getRawValue())
                        .additionDate(additionDatePicker.getValue())
                        .notes(notesField.getText())
                        .build();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(request -> {
            try {
                BigDecimal amount = request.getAmount();
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    showError("Tutar sıfırdan büyük olmalıdır!");
                    return;
                }

                if (request.getAdditionDate() == null) {
                    showError("İlave tarihi zorunludur!");
                    return;
                }
                api.addDebtAmount(debt.getId(), request).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<CompanyDebtDTO> call, Response<CompanyDebtDTO> response) {
                        Platform.runLater(() -> {
                            if (response.isSuccessful()) {
                                showInfo("Borca ilave başarıyla eklendi!");
                                refreshDebtRows();
                                loadTotalDebt();
                                loadParties();
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

    @FXML
    public void handleExportPdf() {
        api.downloadOpenDebtsPdf().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Platform.runLater(() -> showError("PDF oluşturulamadı: " + response.code()));
                    return;
                }
                try {
                    byte[] pdf = response.body().bytes();
                    Platform.runLater(() -> savePdf(pdf, "Acik_Isletme_Borclari.pdf"));
                } catch (Exception exception) {
                    Platform.runLater(() -> showError("PDF okunamadı: " + exception.getMessage()));
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable throwable) {
                Platform.runLater(() -> showError("PDF indirilemedi: " + throwable.getMessage()));
            }
        });
    }

    private void savePdf(byte[] pdf, String initialFileName) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Açık Borçlar PDF Raporunu Kaydet");
            chooser.setInitialFileName(initialFileName);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Dosyaları", "*.pdf"));
            File file = chooser.showSaveDialog((Stage) debtTable.getScene().getWindow());
            if (file != null) {
                Files.write(file.toPath(), pdf);
                showInfo("PDF başarıyla kaydedildi.");
            }
        } catch (Exception exception) {
            showError("PDF kaydedilemedi: " + exception.getMessage());
        }
    }

    private void handleDeleteDebt(CompanyDebtDTO debt) {
        if (AlertHelper.showConfirmation(debtTable.getScene().getWindow(), "Borcu Sil",
                debt.getCreditorName() + " · " + formatCurrency(debt.getOriginalAmount()))) {
                api.deleteDebt(debt.getId()).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        Platform.runLater(() -> {
                            if (response.isSuccessful()) {
                                refreshDebtRows();
                                loadTotalDebt();
                                loadParties();
                            } else {
                                showError("Ödeme geçmişi bulunan borç silinemez. Önce ödemeleri geri alın.");
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Platform.runLater(() -> showError("Silme hatası: " + t.getMessage()));
                    }
                });
        }
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

    private String getCategoryText(String category) {
        if (category == null) {
            return "Diğer";
        }
        return switch (category) {
            case "RENT" -> "Kira";
            case "SALARY" -> "Maaş";
            case "BILLS" -> "Faturalar";
            case "FUEL" -> "Yakıt";
            case "FOOD" -> "Yemek";
            case "TAX" -> "Vergi";
            case "MATERIAL" -> "Malzeme";
            case "OTHER" -> "Diğer";
            default -> category;
        };
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "0 ₺";
        return String.format("%,.2f ₺", amount);
    }

    private void showError(String message) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, debtTable.getScene().getWindow(), "Hata", message);
    }

    private void showInfo(String message) {
        AlertHelper.showSuccess(debtTable.getScene().getWindow(), "Başarılı", message);
    }
}
