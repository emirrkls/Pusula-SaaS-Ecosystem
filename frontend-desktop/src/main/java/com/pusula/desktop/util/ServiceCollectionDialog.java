package com.pusula.desktop.util;

import com.pusula.desktop.api.AccountPartyApi;
import com.pusula.desktop.api.ServiceTicketApi;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.dto.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/** Keeps validation and failed submissions in the dialog; no silent changes to manually entered payments. */
public final class ServiceCollectionDialog {
    private final ServiceTicketDTO ticket;
    private final BigDecimal parts;
    private final Consumer<ServiceTicketDTO> onCompleted;
    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox form = new VBox(14);
    private final TextField labor = new TextField(), discountValue = new TextField(),
            organizationAmount = new TextField(), collected = new TextField();
    private final CheckBox discountEnabled = new CheckBox("İskonto ekle"),
            organizationEnabled = new CheckBox("Tamamını veya bir kısmını kurum carisine aktar"),
            warranty = new CheckBox("Garanti kapsamında kapat");
    private final ComboBox<String> discountType = new ComboBox<>(), method = new ComboBox<>();
    private final ComboBox<AccountPartyOptionDTO> organization = new ComboBox<>();
    private final TextArea note = new TextArea();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final Label net = new Label(), subtotal = new Label(), discount = new Label(), share = new Label(),
            debt = new Label(), error = new Label(), organizationError = new Label();
    private final VBox discountFields = new VBox(8), organizationFields = new VBox(8);
    private final VBox collectedInput = field("Tahsil edilen tutar", collected), paymentInput = field("Ödeme yöntemi", method);
    private final Button allCustomer = new Button("Müşteri payının tamamı");
    private final Label partsDisplay = new Label(), warrantyInfo = hint("Garanti kapsamında müşteriden ödeme alınmaz.");
    private final Button submit;
    private UUID requestId = UUID.randomUUID();
    private Map<String, Object> pendingBody;
    private boolean busy;

    public ServiceCollectionDialog(Window owner, ServiceTicketDTO ticket, BigDecimal parts,
                                   Consumer<ServiceTicketDTO> onCompleted) {
        this.ticket = ticket; this.parts = parts; this.onCompleted = onCompleted;
        ThemeHelper.applyToDialog(dialog, owner, ThemeHelper.DialogProfile.WORKFLOW);
        dialog.setTitle("Tahsilat · Servis fişi #" + ticket.getId());
        ButtonType save = new ButtonType("Tahsilatı Kaydet ve İşi Tamamla", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        submit = (Button) dialog.getDialogPane().lookupButton(save);
        submit.addEventFilter(javafx.event.ActionEvent.ACTION, event -> { event.consume(); submit(); });
        dialog.setOnCloseRequest(event -> { if (busy) event.consume(); });
        discountType.getItems().setAll("TL", "%"); discountType.setValue("TL");
        method.getItems().setAll("Nakit", "Kredi Kartı", "Şimdi ödeme alınmadı"); method.setValue("Nakit");
        labor.setText(ticket.getLaborFee() == null ? "" : ticket.getLaborFee().toPlainString());
        if (ticket.getInvoiceTotal() != null) {
            discountEnabled.setSelected(ticket.getDiscountAmount() != null && ticket.getDiscountAmount().signum() > 0);
            discountType.setValue("PERCENT".equals(ticket.getDiscountType()) ? "%" : "TL");
            discountValue.setText(ticket.getDiscountValue() == null ? "" : ticket.getDiscountValue().toPlainString());
            organizationEnabled.setSelected(ticket.getBillingPartyAmount() != null && ticket.getBillingPartyAmount().signum() > 0);
            organizationAmount.setText(ticket.getBillingPartyAmount() == null ? "" : ticket.getBillingPartyAmount().toPlainString());
            collected.setText(ticket.getCollectedAmount() == null ? "" : ticket.getCollectedAmount().toPlainString());
            warranty.setSelected("WARRANTY".equals(ticket.getPaymentMethod()));
            method.setValue("CREDIT_CARD".equals(ticket.getPaymentMethod()) ? "Kredi Kartı"
                    : "CURRENT_ACCOUNT".equals(ticket.getPaymentMethod()) ? "Şimdi ödeme alınmadı" : "Nakit");
        }
        organization.setPromptText("Kurum seçin"); organization.setMaxWidth(Double.MAX_VALUE);
        organization.setConverter(new javafx.util.StringConverter<>() {
            public String toString(AccountPartyOptionDTO value) { return value == null ? "Kurum seçin" : value.getDisplayName(); }
            public AccountPartyOptionDTO fromString(String value) { return null; }
        });
        error.setWrapText(true); error.getStyleClass().add("text-danger"); organizationError.setWrapText(true);
        note.setPromptText("Yapılan işlem / kapanış notu"); note.setPrefRowCount(2); note.setWrapText(true);
        discountFields.getChildren().setAll(discountType, field("İskonto tutarı / yüzdesi", discountValue),
                line("Ara toplam", subtotal), line("İskonto", discount));
        Button allOrganization = new Button("Fişin tamamını aktar");
        allOrganization.setOnAction(event -> previewNet().ifPresent(value -> organizationAmount.setText(value.toPlainString())));
        Button reloadOrganizations = new Button("Kurumları yenile"); reloadOrganizations.setOnAction(event -> loadOrganizations());
        VBox institutionActions = new VBox(8, organization, organizationError, reloadOrganizations);
        if (SessionManager.isAdmin()) {
            Button create = new Button("Yeni kurum"); create.setOnAction(event -> createOrganization(owner));
            institutionActions.getChildren().add(create);
        }
        organizationFields.getChildren().setAll(institutionActions, field("Kuruma aktarılacak tutar", organizationAmount),
                allOrganization, hint("Kurum payı alınmış ödeme değil, kurumun cari borcudur."));
        allCustomer.setOnAction(event -> previewNet().ifPresent(value -> {
            try { collected.setText(value.subtract(organizationEnabled.isSelected() ? CollectionPricing.parse(organizationAmount.getText()) : BigDecimal.ZERO)
                    .max(BigDecimal.ZERO).toPlainString()); } catch (IllegalArgumentException exception) { error.setText(exception.getMessage()); }
        }));
        VBox extras = new VBox(10, note, warranty,
                hint("İşletme garantisinde satış oluşmaz. Kurum garantisinde fişin tamamı kuruma aktarılmalıdır."));
        if (SessionManager.isAdmin()) extras.getChildren().add(field("Kapanış tarihi", date));
        TitledPane extraPane = new TitledPane("Kapanış notu, tarih ve garanti", extras); extraPane.setExpanded(false);
        form.getChildren().setAll(section("1. Ücret Bilgileri"), line("Parça toplamı", partsDisplay),
                field("Servis / işçilik bedeli", labor), discountEnabled, discountFields, line("Fiş toplamı", net),
                new Separator(), section("2. Ödemeyi Üstlenen"), organizationEnabled, organizationFields, line("Müşteriye düşen", share),
                new Separator(), section("3. Tahsilat"), paymentInput, collectedInput, warrantyInfo,
                allCustomer, line("Müşteri carisine kalan", debt), extraPane);
        form.setPadding(new javafx.geometry.Insets(12));
        ScrollPane scroll = new ScrollPane(new VBox(10, form, error)); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(570);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setMinHeight(0);
        dialog.getDialogPane().setContent(scroll);
        for (TextField input : List.of(labor, discountValue, organizationAmount, collected)) {
            input.setPromptText("0,00"); input.textProperty().addListener((observable, before, after) -> refresh());
        }
        discountType.valueProperty().addListener((observable, before, after) -> refresh());
        method.valueProperty().addListener((observable, before, after) -> refresh());
        discountEnabled.selectedProperty().addListener((observable, before, after) -> refresh());
        organizationEnabled.selectedProperty().addListener((observable, before, after) -> refresh());
        organization.valueProperty().addListener((observable, before, after) -> refresh());
        warranty.selectedProperty().addListener((observable, before, after) -> refresh());
        date.valueProperty().addListener((observable, before, after) -> refresh());
        refresh(); loadOrganizations();
    }
    public void show() { dialog.show(); }
    private boolean internalWarranty() { return warranty.isSelected() && !organizationEnabled.isSelected(); }
    private boolean noCollection() { return warranty.isSelected() || "Şimdi ödeme alınmadı".equals(method.getValue()); }
    private String type() { return "%".equals(discountType.getValue()) ? "PERCENT" : "AMOUNT"; }
    private BigDecimal laborValue() { return internalWarranty() ? BigDecimal.ZERO : CollectionPricing.parse(labor.getText()); }
    private BigDecimal discountValue() { return discountEnabled.isSelected() && !warranty.isSelected() ? CollectionPricing.parse(discountValue.getText()) : BigDecimal.ZERO; }
    private BigDecimal organizationValue() { return organizationEnabled.isSelected() ? CollectionPricing.parse(organizationAmount.getText()) : BigDecimal.ZERO; }
    private BigDecimal collectedValue() { return noCollection() ? BigDecimal.ZERO : CollectionPricing.parse(collected.getText()); }
    private Optional<BigDecimal> previewNet() {
        try { return Optional.of(CollectionPricing.calculate(internalWarranty() ? BigDecimal.ZERO : parts,
                laborValue(), type(), discountValue(), BigDecimal.ZERO, BigDecimal.ZERO).net()); }
        catch (IllegalArgumentException exception) { return Optional.empty(); }
    }
    private CollectionPricing.Totals validate() {
        var totals = CollectionPricing.calculate(internalWarranty() ? BigDecimal.ZERO : parts, laborValue(),
                type(), discountValue(), organizationValue(), collectedValue());
        if (organizationEnabled.isSelected() && (organization.getValue() == null || organizationValue().signum() <= 0)) {
            throw new IllegalArgumentException("Kurum seçin ve aktarılacak tutarı girin.");
        }
        if (warranty.isSelected() && organizationEnabled.isSelected() && organizationValue().compareTo(totals.net()) != 0) {
            throw new IllegalArgumentException("Kurum garantisinde fişin tamamı kurum carisine aktarılmalıdır.");
        }
        if (SessionManager.isAdmin() && (date.getValue() == null || date.getValue().isAfter(LocalDate.now()))) {
            throw new IllegalArgumentException("Geçerli bir kapanış tarihi seçin; gelecekte olamaz.");
        }
        return totals;
    }
    private void refresh() {
        discountFields.setVisible(discountEnabled.isSelected() && !warranty.isSelected()); discountFields.setManaged(discountFields.isVisible());
        discountEnabled.setDisable(warranty.isSelected()); labor.setDisable(internalWarranty());
        organizationFields.setVisible(organizationEnabled.isSelected()); organizationFields.setManaged(organizationFields.isVisible());
        method.setDisable(warranty.isSelected()); collected.setDisable(noCollection());
        partsDisplay.setText(money(internalWarranty() ? BigDecimal.ZERO : parts));
        collectedInput.setVisible(!noCollection()); collectedInput.setManaged(collectedInput.isVisible());
        allCustomer.setVisible(!noCollection()); allCustomer.setManaged(allCustomer.isVisible());
        paymentInput.setVisible(!warranty.isSelected()); paymentInput.setManaged(paymentInput.isVisible());
        warrantyInfo.setVisible(warranty.isSelected()); warrantyInfo.setManaged(warrantyInfo.isVisible());
        warrantyInfo.setText(internalWarranty() ? "İşletme garantisi: satış, tahsilat ve cari borç oluşmaz. Parça maliyeti raporda izlenir."
                : "Kurum garantisi: müşteriden ödeme alınmaz; fişin tamamı kurum carisine aktarılır.");
        try {
            var price = CollectionPricing.calculate(internalWarranty() ? BigDecimal.ZERO : parts, laborValue(), type(), discountValue(), BigDecimal.ZERO, BigDecimal.ZERO);
            subtotal.setText(money(price.subtotal())); discount.setText("−" + money(price.discount())); net.setText(money(price.net()));
            share.setText(money(price.net().subtract(organizationValue()).max(BigDecimal.ZERO)));
            var total = validate(); debt.setText(money(total.customerDebt())); error.setText(""); submit.setDisable(busy);
        } catch (IllegalArgumentException exception) { debt.setText("—"); error.setText(exception.getMessage()); submit.setDisable(true); }
    }
    private void submit() {
        if (busy) return;
        if (pendingBody == null) {
            try {
                var total = validate();
                if (total.customerDebt().signum() > 0 || organizationValue().signum() > 0) {
                    if (!AlertHelper.showConfirmation(dialog.getDialogPane().getScene().getWindow(),
                            "Cari aktarımı onayla", "Kurum carisine: " + money(organizationValue())
                                    + "\nMüşteri carisine: " + money(total.customerDebt()))) return;
                }
                Map<String, Object> body = new HashMap<>();
                body.put("laborFee", laborValue()); body.put("collectedAmount", collectedValue());
                body.put("paymentMethod", warranty.isSelected() ? "WARRANTY" : "Kredi Kartı".equals(method.getValue()) ? "CREDIT_CARD"
                        : noCollection() ? "CURRENT_ACCOUNT" : "CASH");
                body.put("discountType", warranty.isSelected() ? "AMOUNT" : type()); body.put("discountValue", discountValue());
                body.put("expectedPartsTotal", CollectionPricing.money(parts));
                body.put("technicianNote", note.getText().trim());
                if (organizationEnabled.isSelected()) {
                    body.put("billingPartyId", organization.getValue().getId()); body.put("billingPartyAmount", organizationValue());
                }
                if (SessionManager.isAdmin()) body.put("completionDate", date.getValue().toString());
                pendingBody = Map.copyOf(body);
            } catch (IllegalArgumentException exception) { error.setText(exception.getMessage()); return; }
        }
        busy = true; form.setDisable(true); submit.setDisable(true);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
        RetrofitClient.getClient().create(ServiceTicketApi.class).completeServiceReliable(ticket.getId(), requestId.toString(), pendingBody)
                .enqueue(new Callback<>() {
                    public void onResponse(Call<ServiceTicketDTO> call, Response<ServiceTicketDTO> response) {
                        String failureMessage = response.isSuccessful() ? "Sunucudan geçerli işlem sonucu alınamadı. Aynı işlemi tekrar deneyin." : ApiErrorHelper.message(response,
                                response.code() == 404 ? "Sunucunun yeni tahsilat sürümüne güncellenmesi gerekiyor."
                                        : "İşlem teyit edilemedi (" + response.code() + "). Tutarları ve fişin durumunu kontrol edin.");
                        Platform.runLater(() -> {
                            busy = false;
                            if (response.isSuccessful() && response.body() != null) { dialog.close(); onCompleted.accept(response.body()); }
                            else {
                                if (response.code() >= 400 && response.code() < 500 && response.code() != 409) {
                                    pendingBody = null; requestId = UUID.randomUUID(); form.setDisable(false);
                                }
                                failed(failureMessage);
                            }
                        });
                    }
                    public void onFailure(Call<ServiceTicketDTO> call, Throwable failure) {
                        Platform.runLater(() -> { busy = false; failed("Bağlantı kesildi. Aynı bilgilerle tekrar deneyin; ikinci tahsilat oluşmaz."); });
                    }
                });
    }
    private void failed(String message) {
        error.setText(message); submit.setText(pendingBody == null ? "Tahsilatı Kaydet ve İşi Tamamla" : "Aynı İşlemi Tekrar Dene");
        submit.setDisable(false); dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
    }
    private void loadOrganizations() {
        RetrofitClient.getClient().create(AccountPartyApi.class).getBillingOptions().enqueue(new Callback<>() {
            public void onResponse(Call<List<AccountPartyOptionDTO>> call, Response<List<AccountPartyOptionDTO>> response) {
                Platform.runLater(() -> { if (response.isSuccessful() && response.body() != null) {
                    organization.getItems().setAll(response.body()); organizationError.setText("");
                    if (organization.getValue() == null && ticket.getBillingPartyId() != null) {
                        response.body().stream().filter(value -> ticket.getBillingPartyId().equals(value.getId())).findFirst().ifPresent(organization::setValue);
                    }
                } else organizationError.setText("Kurumlar alınamadı. Yenilemeyi deneyin."); });
            }
            public void onFailure(Call<List<AccountPartyOptionDTO>> call, Throwable failure) {
                Platform.runLater(() -> organizationError.setText("Kurumlar alınamadı. Yenilemeyi deneyin."));
            }
        });
    }
    private void createOrganization(Window owner) {
        TextInputDialog input = new TextInputDialog();
        ThemeHelper.applyToDialog(input, owner, ThemeHelper.DialogProfile.FORM);
        input.setTitle("Yeni kurum"); input.setContentText("Kurum / firma adı:");
        input.showAndWait().map(String::trim).filter(value -> !value.isEmpty()).ifPresent(name -> {
            var body = AccountPartyDTO.builder().partyType("ORGANIZATION").displayName(name).paymentTermDays(0).active(true).build();
            RetrofitClient.getClient().create(AccountPartyApi.class).create(body).enqueue(new Callback<>() {
                public void onResponse(Call<AccountPartyDTO> call, Response<AccountPartyDTO> response) {
                    Platform.runLater(() -> { if (response.isSuccessful() && response.body() != null) {
                        var saved = response.body(); var option = new AccountPartyOptionDTO(saved.getId(), saved.getDisplayName(), saved.getPartyType());
                        organization.getItems().add(option); organization.setValue(option);
                    } else organizationError.setText("Kurum oluşturulamadı."); });
                }
                public void onFailure(Call<AccountPartyDTO> call, Throwable failure) { Platform.runLater(() -> organizationError.setText("Kurum oluşturulamadı.")); }
            });
        });
    }
    private static VBox field(String label, javafx.scene.Node input) { return new VBox(5, new Label(label), input); }
    private static Label hint(String text) { Label label = new Label(text); label.setWrapText(true); return label; }
    private static HBox line(String title, Label value) {
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); return new HBox(12, new Label(title), spacer, value);
    }
    private static Label section(String title) { Label label = new Label(title); label.getStyleClass().add("dialog-section-title"); return label; }
    private static String money(BigDecimal value) { return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("tr-TR")).format(value); }
}
