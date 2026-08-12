package com.pusula.desktop.controller;

import com.pusula.desktop.api.*;
import com.pusula.desktop.dto.*;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.CustomerSearchSupport;
import com.pusula.desktop.util.CurrencyTextField;
import com.pusula.desktop.util.SessionManager;
import com.pusula.desktop.util.AlertHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class ProposalEditorController {

    @FXML
    private Label titleLabel;
    @FXML
    private TextField titleField;
    @FXML
    private ComboBox<CustomerDTO> customerComboBox;
    @FXML
    private ComboBox<UserDTO> preparedByComboBox;
    @FXML
    private DatePicker validUntilPicker;
    @FXML
    private ComboBox<String> statusComboBox;

    @FXML
    private TableView<ProposalItemDTO> itemsTable;
    @FXML
    private TableColumn<ProposalItemDTO, String> colItemDesc;
    @FXML
    private TableColumn<ProposalItemDTO, String> colItemQty;
    @FXML
    private TableColumn<ProposalItemDTO, String> colItemCost;
    @FXML
    private TableColumn<ProposalItemDTO, String> colItemPrice;
    @FXML
    private TableColumn<ProposalItemDTO, String> colItemTotal;
    @FXML
    private TableColumn<ProposalItemDTO, Void> colItemActions;

    @FXML
    private ComboBox<String> sourceTypeComboBox;
    @FXML
    private ComboBox<Object> sourceItemComboBox;
    @FXML
    private TextField itemQtyField;
    @FXML
    private CurrencyTextField itemCostField;
    @FXML
    private CurrencyTextField itemPriceField;
    @FXML
    private Button addItemButton;

    @FXML
    private Label subtotalLabel;
    @FXML
    private CurrencyTextField taxRateField;
    @FXML
    private Label taxAmountLabel;
    @FXML
    private CurrencyTextField discountField;
    @FXML
    private Label totalLabel;

    @FXML
    private VBox profitBox;
    @FXML
    private Label totalCostLabel;
    @FXML
    private Label profitLabel;

    @FXML
    private TextArea noteArea;
    @FXML
    private Button saveButton;

    private ProposalApi proposalApi;
    private CustomerApi customerApi;
    private UserApi userApi;
    private CommercialDeviceApi deviceApi;
    private InventoryApi inventoryApi;

    private ProposalDTO currentProposal;
    private ObservableList<ProposalItemDTO> items = FXCollections.observableArrayList();
    private Runnable onSaveCallback;
    private boolean isAdmin;
    private boolean saveInProgress;
    private ProposalItemDTO editingItem;
    private Long pendingInventorySelectionId;

    private List<CommercialDeviceDTO> devices = new ArrayList<>();
    private List<InventoryDTO> inventoryItems = new ArrayList<>();
    private final ObservableList<CustomerDTO> allCustomers = FXCollections.observableArrayList();
    private final ObservableList<CustomerDTO> filteredCustomers = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        proposalApi = RetrofitClient.getClient().create(ProposalApi.class);
        customerApi = RetrofitClient.getClient().create(CustomerApi.class);
        userApi = RetrofitClient.getClient().create(UserApi.class);
        deviceApi = RetrofitClient.getClient().create(CommercialDeviceApi.class);
        inventoryApi = RetrofitClient.getClient().create(InventoryApi.class);

        // Check if admin (static method)
        isAdmin = SessionManager.isAdmin();
        profitBox.setVisible(isAdmin);
        profitBox.setManaged(isAdmin);
        colItemCost.setVisible(isAdmin);

        setupTable();
        setupComboBoxes();
        setupCalculationListeners();
        loadData();
    }

    private void setupTable() {
        colItemDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colItemQty.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantity())));
        colItemCost.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getUnitCost() != null ? String.format("%.2f ₺", d.getValue().getUnitCost()) : "-"));
        colItemPrice.setCellValueFactory(d -> new SimpleStringProperty(
                String.format("%.2f ₺", d.getValue().getUnitPrice())));
        colItemTotal.setCellValueFactory(d -> new SimpleStringProperty(
                String.format("%.2f ₺", d.getValue().getTotalPrice())));

        colItemActions.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Düzenle");
            private final Button deleteBtn = new Button("Sil");
            private final javafx.scene.layout.HBox actions = new javafx.scene.layout.HBox(5, editBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                deleteBtn.getStyleClass().add("btn-danger");
                editBtn.getStyleClass().add("btn-sm");
                deleteBtn.getStyleClass().add("btn-sm");
                editBtn.setOnAction(e -> startEditingItem(getTableRow().getItem()));
                deleteBtn.setOnAction(e -> {
                    ProposalItemDTO rowItem = getTableRow().getItem();
                    if (rowItem == editingItem) {
                        cancelItemEditing();
                    }
                    items.remove(rowItem);
                    recalculateTotals();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actions);
            }
        });

        itemsTable.setItems(items);
        itemsTable.setPlaceholder(new Label("Henüz teklif kalemi eklenmedi."));
        itemsTable.setRowFactory(table -> {
            TableRow<ProposalItemDTO> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    startEditingItem(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupComboBoxes() {
        // Status with Turkish labels
        configureAllowedStatuses("DRAFT", true);
        statusComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String s) {
                if (s == null)
                    return "";
                switch (s) {
                    case "DRAFT":
                        return "Taslak";
                    case "SENT":
                        return "Gönderildi";
                    case "APPROVED":
                        return "Onaylandı";
                    case "REJECTED":
                        return "Reddedildi";
                    default:
                        return s;
                }
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });

        validUntilPicker.setValue(LocalDate.now().plusDays(30));

        sourceTypeComboBox.setItems(FXCollections.observableArrayList("Yedek Parça", "Cihaz", "Hizmet"));
        sourceTypeComboBox.setOnAction(e -> loadSourceItems());
        sourceTypeComboBox.setValue("Hizmet");
        sourceItemComboBox.valueProperty().addListener((obs, oldValue, newValue) -> populateSourcePricing(newValue));

        customerComboBox.setEditable(true);
        customerComboBox.setItems(filteredCustomers);
        customerComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CustomerDTO c) {
                return CustomerSearchSupport.displayText(c);
            }

            @Override
            public CustomerDTO fromString(String s) {
                if (s == null || s.isBlank()) {
                    return null;
                }
                return allCustomers.stream()
                        .filter(c -> CustomerSearchSupport.displayText(c).equalsIgnoreCase(s.trim()))
                        .findFirst()
                        .orElse(null);
            }
        });
        customerComboBox.getEditor().setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN
                    || event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB
                    || event.getCode() == KeyCode.ESCAPE) {
                return;
            }
            String query = customerComboBox.getEditor().getText();
            CustomerDTO selected = customerComboBox.getSelectionModel().getSelectedItem();
            if (selected != null && !CustomerSearchSupport.displayText(selected).equals(query)) {
                customerComboBox.getSelectionModel().clearSelection();
                customerComboBox.setValue(null);
                customerComboBox.getEditor().setText(query);
                customerComboBox.getEditor().positionCaret(query.length());
            }
            applyCustomerFilter(query);
        });

        preparedByComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(UserDTO u) {
                return u == null ? "" : u.getFullName();
            }

            @Override
            public UserDTO fromString(String s) {
                return null;
            }
        });

        loadSourceItems();
    }

    private void configureAllowedStatuses(String currentStatus, boolean isNewProposal) {
        List<String> allowed;
        if (isNewProposal) {
            allowed = List.of("DRAFT");
        } else if ("DRAFT".equals(currentStatus)) {
            allowed = List.of("DRAFT", "SENT", "REJECTED");
        } else if ("SENT".equals(currentStatus)) {
            allowed = List.of("SENT", "APPROVED", "REJECTED");
        } else if ("APPROVED".equals(currentStatus)) {
            allowed = List.of("APPROVED");
        } else {
            allowed = List.of("REJECTED");
        }
        statusComboBox.setItems(FXCollections.observableArrayList(allowed));
        statusComboBox.setValue(currentStatus);
        statusComboBox.setDisable(allowed.size() == 1);
    }

    private void applyCustomerFilter(String query) {
        filteredCustomers.setAll(allCustomers.stream()
                .filter(customer -> CustomerSearchSupport.matches(customer, query))
                .toList());
        if (filteredCustomers.isEmpty()) {
            customerComboBox.hide();
        } else if (!customerComboBox.isShowing() && customerComboBox.isFocused()) {
            customerComboBox.show();
        }
    }

    private void setupCalculationListeners() {
        taxRateField.textProperty().addListener((obs, o, n) -> recalculateTotals());
        discountField.textProperty().addListener((obs, o, n) -> recalculateTotals());
    }

    private void loadData() {
        customerApi.getAllCustomers().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        allCustomers.setAll(response.body());
                        filteredCustomers.setAll(allCustomers);
                        // Pre-select customer if editing an existing proposal
                        if (currentProposal != null && currentProposal.getCustomerId() != null) {
                            customerComboBox.getItems().stream()
                                    .filter(c -> c.getId().equals(currentProposal.getCustomerId()))
                                    .findFirst()
                                    .ifPresent(c -> customerComboBox.setValue(c));
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {
                Platform.runLater(() -> showError("Müşteriler yüklenemedi: " + t.getMessage()));
            }
        });

        userApi.getAllUsers().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        List<UserDTO> users = response.body();
                        preparedByComboBox.setItems(FXCollections.observableArrayList(users));

                        // Pre-select prepared by user if editing an existing proposal
                        if (currentProposal != null && currentProposal.getPreparedById() != null) {
                            users.stream()
                                    .filter(u -> u.getId().equals(currentProposal.getPreparedById()))
                                    .findFirst()
                                    .ifPresent(u -> preparedByComboBox.setValue(u));
                        } else {
                            // For new proposals, select current user
                            String currentUsername = SessionManager.getUsername();
                            users.stream()
                                    .filter(u -> u.getUsername() != null && u.getUsername().equals(currentUsername))
                                    .findFirst()
                                    .ifPresent(u -> preparedByComboBox.setValue(u));
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
                Platform.runLater(() -> showError("Kullanıcılar yüklenemedi: " + t.getMessage()));
            }
        });
    }

    private void loadSourceItems() {
        String sourceType = sourceTypeComboBox.getValue();
        if (sourceType == null)
            return;

        sourceItemComboBox.getEditor().setOnKeyReleased(null);
        sourceItemComboBox.getItems().clear();
        sourceItemComboBox.setValue(null);
        sourceItemComboBox.getEditor().clear();

        if ("Yedek Parça".equals(sourceType)) {
            inventoryApi.getAllInventory().enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<List<InventoryDTO>> call, Response<List<InventoryDTO>> response) {
                    Platform.runLater(() -> {
                        if (response.isSuccessful() && response.body() != null) {
                            inventoryItems = response.body();
                            configureSelectableSourceItems(new ArrayList<>(inventoryItems), value -> {
                                InventoryDTO item = (InventoryDTO) value;
                                return item.getPartName();
                            });
                            selectPendingInventoryItem();
                        } else {
                            showError("Yedek parçalar yüklenemedi.");
                        }
                    });
                }

                @Override
                public void onFailure(Call<List<InventoryDTO>> call, Throwable t) {
                    Platform.runLater(() -> showError("Yedek parçalar yüklenemedi: " + t.getMessage()));
                }
            });
        } else if ("Cihaz".equals(sourceType)) {
            deviceApi.getAll().enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<List<CommercialDeviceDTO>> call,
                        Response<List<CommercialDeviceDTO>> response) {
                    Platform.runLater(() -> {
                        if (response.isSuccessful() && response.body() != null) {
                            devices = response.body();
                            configureSelectableSourceItems(new ArrayList<>(devices), value -> {
                                CommercialDeviceDTO device = (CommercialDeviceDTO) value;
                                return device.getBrand() + " " + device.getModel();
                            });
                        } else {
                            showError("Cihazlar yüklenemedi.");
                        }
                    });
                }

                @Override
                public void onFailure(Call<List<CommercialDeviceDTO>> call, Throwable t) {
                    Platform.runLater(() -> showError("Cihazlar yüklenemedi: " + t.getMessage()));
                }
            });
        } else {
            sourceItemComboBox.setItems(FXCollections.observableArrayList());
            sourceItemComboBox.setConverter(new StringConverter<>() {
                @Override
                public String toString(Object o) {
                    return o != null ? o.toString() : "";
                }

                @Override
                public Object fromString(String s) {
                    return s;
                }
            });
            itemCostField.setText("0");
            itemPriceField.setText("");
        }
    }

    private void configureSelectableSourceItems(List<?> sourceItems, Function<Object, String> displayText) {
        ObservableList<Object> allItems = FXCollections.observableArrayList(sourceItems);
        sourceItemComboBox.setItems(allItems);
        sourceItemComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Object value) {
                return value == null ? "" : displayText.apply(value);
            }

            @Override
            public Object fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                return allItems.stream()
                        .filter(value -> displayText.apply(value).equalsIgnoreCase(text.trim()))
                        .findFirst()
                        .orElse(null);
            }
        });
        sourceItemComboBox.getEditor().setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN
                    || event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB
                    || event.getCode() == KeyCode.ESCAPE) {
                return;
            }
            String query = sourceItemComboBox.getEditor().getText();
            Object selected = sourceItemComboBox.getSelectionModel().getSelectedItem();
            if (selected != null && !displayText.apply(selected).equals(query)) {
                sourceItemComboBox.getSelectionModel().clearSelection();
                sourceItemComboBox.setValue(null);
                sourceItemComboBox.getEditor().setText(query);
                sourceItemComboBox.getEditor().positionCaret(query.length());
            }
            String normalized = query == null ? "" : query.trim().toLowerCase(Locale.forLanguageTag("tr-TR"));
            sourceItemComboBox.setItems(allItems.filtered(value ->
                    displayText.apply(value).toLowerCase(Locale.forLanguageTag("tr-TR")).contains(normalized)));
            if (!sourceItemComboBox.getItems().isEmpty() && !sourceItemComboBox.isShowing()) {
                sourceItemComboBox.show();
            }
        });
    }

    private void populateSourcePricing(Object selected) {
        if (selected instanceof InventoryDTO inventory) {
            itemCostField.setRawValue(inventory.getBuyPrice() != null ? inventory.getBuyPrice() : BigDecimal.ZERO);
            itemPriceField.setRawValue(inventory.getSellPrice() != null ? inventory.getSellPrice() : BigDecimal.ZERO);
        } else if (selected instanceof CommercialDeviceDTO device) {
            itemCostField.setRawValue(device.getBuyingPrice() != null ? device.getBuyingPrice() : BigDecimal.ZERO);
            itemPriceField.setRawValue(device.getSellingPrice() != null ? device.getSellingPrice() : BigDecimal.ZERO);
        }
    }

    @FXML
    private void handleAddItem() {
        try {
            String description;
            BigDecimal unitCost = BigDecimal.ZERO;
            BigDecimal unitPrice;
            int quantity = Integer.parseInt(itemQtyField.getText().trim());
            if (quantity <= 0) {
                showError("Adet sıfırdan büyük olmalıdır.");
                return;
            }

            Object selected = sourceItemComboBox.getValue();
            String sourceType = sourceTypeComboBox.getValue();

            if ("Yedek Parça".equals(sourceType) && selected instanceof InventoryDTO inventory) {
                description = inventory.getPartName();
                unitCost = inventory.getBuyPrice() != null ? inventory.getBuyPrice() : BigDecimal.ZERO;
                unitPrice = !itemPriceField.isEmpty()
                        ? itemPriceField.getRawValue()
                        : (inventory.getSellPrice() != null ? inventory.getSellPrice() : BigDecimal.ZERO);
            } else if ("Cihaz".equals(sourceType) && selected instanceof CommercialDeviceDTO) {
                CommercialDeviceDTO device = (CommercialDeviceDTO) selected;
                description = device.getBrand() + " " + device.getModel();
                unitCost = device.getBuyingPrice() != null ? device.getBuyingPrice() : BigDecimal.ZERO;
                unitPrice = !itemPriceField.isEmpty()
                        ? itemPriceField.getRawValue()
                        : (device.getSellingPrice() != null ? device.getSellingPrice() : BigDecimal.ZERO);
            } else {
                // Manual service entry
                description = sourceItemComboBox.getEditor().getText();
                if (description == null || description.trim().isEmpty()) {
                    showError("Lütfen açıklama girin.");
                    return;
                }
                if (!itemCostField.isEmpty()) {
                    unitCost = itemCostField.getRawValue();
                }
                unitPrice = itemPriceField.getRawValue();
            }

            if (unitCost.compareTo(BigDecimal.ZERO) < 0 || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                showError("Maliyet ve fiyat negatif olamaz.");
                return;
            }

            ProposalItemDTO item = editingItem != null ? editingItem : new ProposalItemDTO();
            item.setInventoryId(selected instanceof InventoryDTO inventory ? inventory.getId() : null);
            item.setDescription(description);
            item.setQuantity(quantity);
            item.setUnitCost(unitCost);
            item.setUnitPrice(unitPrice);
            item.setTotalPrice(unitPrice.multiply(new BigDecimal(quantity)));

            if (editingItem == null) {
                items.add(item);
            } else {
                itemsTable.refresh();
            }
            recalculateTotals();
            clearItemFields();
            Platform.runLater(() -> {
                itemsTable.getSelectionModel().select(item);
                itemsTable.scrollTo(item);
            });

        } catch (Exception e) {
            showError("Kalem eklenemedi: " + e.getMessage());
        }
    }

    private void clearItemFields() {
        editingItem = null;
        pendingInventorySelectionId = null;
        addItemButton.setText("+ Ekle");
        sourceItemComboBox.setValue(null);
        sourceItemComboBox.getEditor().clear();
        itemQtyField.clear();
        itemCostField.clear();
        itemPriceField.clear();
    }

    private void startEditingItem(ProposalItemDTO item) {
        if (item == null) {
            return;
        }
        editingItem = item;
        if (item.getInventoryId() != null) {
            pendingInventorySelectionId = item.getInventoryId();
            if ("Yedek Parça".equals(sourceTypeComboBox.getValue())) {
                loadSourceItems();
            } else {
                sourceTypeComboBox.setValue("Yedek Parça");
            }
        } else {
            sourceTypeComboBox.setValue("Hizmet");
            sourceItemComboBox.getEditor().setText(item.getDescription());
        }
        itemQtyField.setText(String.valueOf(item.getQuantity()));
        itemCostField.setRawValue(item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO);
        itemPriceField.setRawValue(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
        addItemButton.setText("Güncelle");
        sourceItemComboBox.requestFocus();
        sourceItemComboBox.getEditor().positionCaret(sourceItemComboBox.getEditor().getText().length());
    }

    private void selectPendingInventoryItem() {
        if (pendingInventorySelectionId == null || editingItem == null) {
            return;
        }
        inventoryItems.stream()
                .filter(inventory -> pendingInventorySelectionId.equals(inventory.getId()))
                .findFirst()
                .ifPresent(inventory -> {
                    sourceItemComboBox.setValue(inventory);
                    itemCostField.setRawValue(editingItem.getUnitCost() != null
                            ? editingItem.getUnitCost() : BigDecimal.ZERO);
                    itemPriceField.setRawValue(editingItem.getUnitPrice() != null
                            ? editingItem.getUnitPrice() : BigDecimal.ZERO);
                });
        pendingInventorySelectionId = null;
    }

    private void cancelItemEditing() {
        clearItemFields();
    }

    private void recalculateTotals() {
        BigDecimal subtotal = items.stream()
                .map(ProposalItemDTO::getTotalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxRate = new BigDecimal("20");
        try {
            taxRate = taxRateField.getRawValue();
        } catch (Exception ignored) {
        }

        BigDecimal discount = BigDecimal.ZERO;
        try {
            discount = discountField.getRawValue();
        } catch (Exception ignored) {
        }

        BigDecimal taxAmount = subtotal.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxAmount).subtract(discount);

        subtotalLabel.setText(String.format("%.2f ₺", subtotal));
        taxAmountLabel.setText(String.format("%.2f ₺", taxAmount));
        totalLabel.setText(String.format("%.2f ₺", total));

        // Admin-only: calculate profit
        if (isAdmin) {
            BigDecimal totalCost = items.stream()
                    .map(i -> i.getUnitCost() != null ? i.getUnitCost().multiply(new BigDecimal(i.getQuantity()))
                            : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal profit = subtotal.subtract(discount).subtract(totalCost);

            totalCostLabel.setText(String.format("%.2f ₺", totalCost));
            profitLabel.setText(String.format("%.2f ₺", profit));
            profitLabel.getStyleClass().removeAll("amount-positive", "amount-negative");
            profitLabel.getStyleClass().add(profit.compareTo(BigDecimal.ZERO) >= 0
                    ? "amount-positive" : "amount-negative");
        }
    }

    public void setProposal(ProposalDTO proposal) {
        this.currentProposal = proposal;
        if (proposal != null) {
            titleLabel.setText("Teklif Düzenle #" + proposal.getId());
            titleField.setText(proposal.getTitle());
            configureAllowedStatuses(proposal.getStatus(), false);
            validUntilPicker.setValue(proposal.getValidUntil());
            noteArea.setText(proposal.getNote());
            taxRateField.setRawValue(proposal.getTaxRate() != null ? proposal.getTaxRate() : new BigDecimal("20"));
            discountField.setRawValue(proposal.getDiscount() != null ? proposal.getDiscount() : BigDecimal.ZERO);

            if (proposal.getItems() != null) {
                items.addAll(proposal.getItems());
            }
            recalculateTotals();

            Platform.runLater(() -> {
                customerComboBox.getItems().stream()
                        .filter(c -> c.getId().equals(proposal.getCustomerId()))
                        .findFirst()
                        .ifPresent(c -> customerComboBox.setValue(c));
                preparedByComboBox.getItems().stream()
                        .filter(u -> u.getId().equals(proposal.getPreparedById()))
                        .findFirst()
                        .ifPresent(u -> preparedByComboBox.setValue(u));
            });
        }
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }

    @FXML
    private void handleAddCustomer() {
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle(
                    "i18n.messages", java.util.Locale.forLanguageTag("tr-TR"),
                    new com.pusula.desktop.util.UTF8Control());
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/view/customer_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();
            CustomerDialogController dialogController = loader.getController();
            dialogController.setOnSaveSuccess((savedCustomer) -> {
                // Refresh customers and auto-select the new one
                loadData();
                Platform.runLater(() -> {
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            Platform.runLater(() -> {
                                customerComboBox.getItems().stream()
                                        .filter(c -> c.getId().equals(savedCustomer.getId()))
                                        .findFirst()
                                        .ifPresent(c -> customerComboBox.setValue(c));
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                });
            });
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle(bundle.getString("customer.dialog.title"));
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(customerComboBox.getScene().getWindow());
            dialogStage.setScene(com.pusula.desktop.util.ThemeHelper.createDialogScene(root));
            dialogStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Müşteri ekleme ekranı açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        if (saveInProgress) {
            return;
        }
        if (customerComboBox.getValue() == null && customerComboBox.getEditor().getText() != null) {
            CustomerDTO typedCustomer = customerComboBox.getConverter()
                    .fromString(customerComboBox.getEditor().getText());
            customerComboBox.setValue(typedCustomer);
        }
        if (customerComboBox.getValue() == null) {
            showError("Lütfen listeden geçerli bir müşteri seçin.");
            return;
        }
        if (items.isEmpty()) {
            showError("Lütfen en az bir kalem ekleyin.");
            return;
        }
        if (validUntilPicker.getValue() == null) {
            showError("Lütfen teklif geçerlilik tarihini seçin.");
            return;
        }

        ProposalDTO dto = new ProposalDTO();
        dto.setCustomerId(customerComboBox.getValue().getId());
        dto.setPreparedById(preparedByComboBox.getValue() != null ? preparedByComboBox.getValue().getId() : null);
        dto.setStatus(statusComboBox.getValue());
        dto.setValidUntil(validUntilPicker.getValue());
        dto.setNote(noteArea.getText());
        dto.setTitle(titleField.getText());

        try {
            BigDecimal taxRate = taxRateField.getRawValue();
            BigDecimal discount = discountField.getRawValue();
            if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
                showError("KDV oranı 0 ile 100 arasında olmalıdır.");
                return;
            }
            if (discount.compareTo(BigDecimal.ZERO) < 0) {
                showError("İndirim negatif olamaz.");
                return;
            }
            dto.setTaxRate(taxRate);
            dto.setDiscount(discount);
        } catch (Exception e) {
            showError("KDV ve indirim alanlarını kontrol edin.");
            return;
        }

        dto.setItems(new ArrayList<>(items));
        setSaving(true);

        Callback<ProposalDTO> callback = new Callback<>() {
            @Override
            public void onResponse(Call<ProposalDTO> call, Response<ProposalDTO> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) {
                        if (onSaveCallback != null)
                            onSaveCallback.run();
                        closeWindow();
                    } else {
                        setSaving(false);
                        showError("Kaydetme başarısız (HTTP " + response.code() + ").");
                    }
                });
            }

            @Override
            public void onFailure(Call<ProposalDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    setSaving(false);
                    showError("Hata: " + t.getMessage());
                });
            }
        };

        if (currentProposal != null) {
            proposalApi.update(currentProposal.getId(), dto).enqueue(callback);
        } else {
            proposalApi.create(dto).enqueue(callback);
        }
    }

    private void setSaving(boolean saving) {
        saveInProgress = saving;
        saveButton.setDisable(saving);
        saveButton.setText(saving ? "Kaydediliyor..." : "Kaydet");
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) titleLabel.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        AlertHelper.showAlert(Alert.AlertType.ERROR, titleLabel.getScene().getWindow(), "Hata", msg);
    }

    private void showInfo(String msg) {
        AlertHelper.showSuccess(titleLabel.getScene().getWindow(), "Başarılı", msg);
    }
}
