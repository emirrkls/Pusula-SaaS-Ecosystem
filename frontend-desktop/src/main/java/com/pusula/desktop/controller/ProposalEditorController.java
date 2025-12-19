package com.pusula.desktop.controller;

import com.pusula.desktop.api.*;
import com.pusula.desktop.dto.*;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
    private TextField itemCostField;
    @FXML
    private TextField itemPriceField;

    @FXML
    private Label subtotalLabel;
    @FXML
    private TextField taxRateField;
    @FXML
    private Label taxAmountLabel;
    @FXML
    private TextField discountField;
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

    private ProposalApi proposalApi;
    private CustomerApi customerApi;
    private UserApi userApi;
    private CommercialDeviceApi deviceApi;

    private ProposalDTO currentProposal;
    private ObservableList<ProposalItemDTO> items = FXCollections.observableArrayList();
    private Runnable onSaveCallback;
    private boolean isAdmin;

    private List<CommercialDeviceDTO> devices = new ArrayList<>();

    @FXML
    public void initialize() {
        proposalApi = RetrofitClient.getClient().create(ProposalApi.class);
        customerApi = RetrofitClient.getClient().create(CustomerApi.class);
        userApi = RetrofitClient.getClient().create(UserApi.class);
        deviceApi = RetrofitClient.getClient().create(CommercialDeviceApi.class);

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
            private final Button deleteBtn = new Button("❌");
            {
                deleteBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                deleteBtn.setOnAction(e -> {
                    items.remove(getIndex());
                    recalculateTotals();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });

        itemsTable.setItems(items);
    }

    private void setupComboBoxes() {
        // Status with Turkish labels
        statusComboBox.setItems(FXCollections.observableArrayList("DRAFT", "SENT", "APPROVED", "REJECTED"));
        statusComboBox.setValue("DRAFT");
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

        // Source types: Device or manual Service
        sourceTypeComboBox.setItems(FXCollections.observableArrayList("Cihaz", "Hizmet"));
        sourceTypeComboBox.setOnAction(e -> loadSourceItems());

        customerComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CustomerDTO c) {
                return c == null ? "" : c.getName();
            }

            @Override
            public CustomerDTO fromString(String s) {
                return null;
            }
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
                        customerComboBox.setItems(FXCollections.observableArrayList(response.body()));
                    }
                });
            }

            @Override
            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {
            }
        });

        userApi.getAllUsers().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        List<UserDTO> users = response.body();
                        preparedByComboBox.setItems(FXCollections.observableArrayList(users));
                        // Select current user by username
                        String currentUsername = SessionManager.getUsername();
                        users.stream()
                                .filter(u -> u.getUsername() != null && u.getUsername().equals(currentUsername))
                                .findFirst()
                                .ifPresent(u -> preparedByComboBox.setValue(u));
                    }
                });
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
            }
        });
    }

    private void loadSourceItems() {
        String sourceType = sourceTypeComboBox.getValue();
        if (sourceType == null)
            return;

        sourceItemComboBox.getItems().clear();

        if ("Cihaz".equals(sourceType)) {
            deviceApi.getAll().enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<List<CommercialDeviceDTO>> call,
                        Response<List<CommercialDeviceDTO>> response) {
                    Platform.runLater(() -> {
                        if (response.isSuccessful() && response.body() != null) {
                            devices = response.body();
                            sourceItemComboBox.setItems(FXCollections.observableArrayList(devices));
                            sourceItemComboBox.setConverter(new StringConverter<>() {
                                @Override
                                public String toString(Object o) {
                                    if (o instanceof CommercialDeviceDTO) {
                                        CommercialDeviceDTO d = (CommercialDeviceDTO) o;
                                        return d.getBrand() + " " + d.getModel();
                                    }
                                    return "";
                                }

                                @Override
                                public Object fromString(String s) {
                                    return null;
                                }
                            });
                        }
                    });
                }

                @Override
                public void onFailure(Call<List<CommercialDeviceDTO>> call, Throwable t) {
                }
            });
        } else {
            // Manual service entry - clear converter
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

    @FXML
    private void handleAddItem() {
        try {
            String description;
            BigDecimal unitCost = BigDecimal.ZERO;
            BigDecimal unitPrice;
            int quantity = Integer.parseInt(itemQtyField.getText().trim());

            Object selected = sourceItemComboBox.getValue();
            String sourceType = sourceTypeComboBox.getValue();

            if ("Cihaz".equals(sourceType) && selected instanceof CommercialDeviceDTO) {
                CommercialDeviceDTO device = (CommercialDeviceDTO) selected;
                description = device.getBrand() + " " + device.getModel();
                unitCost = device.getBuyingPrice() != null ? device.getBuyingPrice() : BigDecimal.ZERO;
                unitPrice = !itemPriceField.getText().isEmpty()
                        ? new BigDecimal(itemPriceField.getText().replace(",", "."))
                        : (device.getSellingPrice() != null ? device.getSellingPrice() : BigDecimal.ZERO);
            } else {
                // Manual service entry
                description = sourceItemComboBox.getEditor().getText();
                if (description == null || description.trim().isEmpty()) {
                    showError("Lütfen açıklama girin.");
                    return;
                }
                if (!itemCostField.getText().isEmpty()) {
                    unitCost = new BigDecimal(itemCostField.getText().replace(",", "."));
                }
                unitPrice = new BigDecimal(itemPriceField.getText().replace(",", "."));
            }

            ProposalItemDTO item = new ProposalItemDTO();
            item.setDescription(description);
            item.setQuantity(quantity);
            item.setUnitCost(unitCost);
            item.setUnitPrice(unitPrice);
            item.setTotalPrice(unitPrice.multiply(new BigDecimal(quantity)));

            items.add(item);
            recalculateTotals();
            clearItemFields();

        } catch (Exception e) {
            showError("Kalem eklenemedi: " + e.getMessage());
        }
    }

    private void clearItemFields() {
        sourceItemComboBox.setValue(null);
        sourceItemComboBox.getEditor().clear();
        itemQtyField.clear();
        itemCostField.clear();
        itemPriceField.clear();
    }

    private void recalculateTotals() {
        BigDecimal subtotal = items.stream()
                .map(ProposalItemDTO::getTotalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxRate = new BigDecimal("20");
        try {
            taxRate = new BigDecimal(taxRateField.getText().replace(",", "."));
        } catch (Exception ignored) {
        }

        BigDecimal discount = BigDecimal.ZERO;
        try {
            discount = new BigDecimal(discountField.getText().replace(",", "."));
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
            BigDecimal profit = subtotal.subtract(totalCost);

            totalCostLabel.setText(String.format("%.2f ₺", totalCost));
            profitLabel.setText(String.format("%.2f ₺", profit));
            profitLabel.setStyle(profit.compareTo(BigDecimal.ZERO) >= 0
                    ? "-fx-font-weight: bold; -fx-text-fill: #27ae60;"
                    : "-fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        }
    }

    public void setProposal(ProposalDTO proposal) {
        this.currentProposal = proposal;
        if (proposal != null) {
            titleLabel.setText("Teklif Düzenle #" + proposal.getId());
            titleField.setText(proposal.getTitle());
            statusComboBox.setValue(proposal.getStatus());
            validUntilPicker.setValue(proposal.getValidUntil());
            noteArea.setText(proposal.getNote());
            taxRateField.setText(proposal.getTaxRate() != null ? proposal.getTaxRate().toString() : "20");
            discountField.setText(proposal.getDiscount() != null ? proposal.getDiscount().toString() : "0");

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
        showInfo("Müşteri ekleme özelliği yakında eklenecek.");
    }

    @FXML
    private void handleSave() {
        if (customerComboBox.getValue() == null) {
            showError("Lütfen müşteri seçin.");
            return;
        }
        if (items.isEmpty()) {
            showError("Lütfen en az bir kalem ekleyin.");
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
            dto.setTaxRate(new BigDecimal(taxRateField.getText().replace(",", ".")));
            dto.setDiscount(new BigDecimal(discountField.getText().replace(",", ".")));
        } catch (Exception e) {
            dto.setTaxRate(new BigDecimal("20"));
            dto.setDiscount(BigDecimal.ZERO);
        }

        dto.setItems(new ArrayList<>(items));

        Callback<ProposalDTO> callback = new Callback<>() {
            @Override
            public void onResponse(Call<ProposalDTO> call, Response<ProposalDTO> response) {
                Platform.runLater(() -> {
                    if (response.isSuccessful()) {
                        if (onSaveCallback != null)
                            onSaveCallback.run();
                        closeWindow();
                    } else {
                        showError("Kaydetme başarısız.");
                    }
                });
            }

            @Override
            public void onFailure(Call<ProposalDTO> call, Throwable t) {
                Platform.runLater(() -> showError("Hata: " + t.getMessage()));
            }
        };

        if (currentProposal != null) {
            proposalApi.update(currentProposal.getId(), dto).enqueue(callback);
        } else {
            proposalApi.create(dto).enqueue(callback);
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) titleLabel.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setContentText(msg);
        a.showAndWait();
    }
}
