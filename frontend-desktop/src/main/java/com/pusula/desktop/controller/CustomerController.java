package com.pusula.desktop.controller;



import com.pusula.desktop.api.CustomerApi;

import com.pusula.desktop.dto.CustomerDTO;

import com.pusula.desktop.network.RetrofitClient;

import com.pusula.desktop.util.AlertHelper;

import com.pusula.desktop.util.AnimationHelper;

import com.pusula.desktop.util.TableUiHelper;

import com.pusula.desktop.util.ThemeHelper;

import com.pusula.desktop.util.UTF8Control;

import com.pusula.desktop.util.WhatsAppHelper;

import javafx.application.Platform;

import javafx.collections.FXCollections;

import javafx.collections.ObservableList;

import javafx.fxml.FXML;

import javafx.fxml.FXMLLoader;

import javafx.geometry.Pos;

import javafx.scene.control.Alert;

import javafx.scene.control.Button;

import javafx.scene.control.Label;

import javafx.scene.control.ListCell;

import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import javafx.scene.layout.HBox;

import javafx.scene.layout.Priority;

import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;
import retrofit2.Call;
import retrofit2.Callback;

import retrofit2.Response;



import java.util.List;

import java.util.Locale;

import java.util.ResourceBundle;



public class CustomerController {



    @FXML private ListView<CustomerDTO> customersListView;

    @FXML private TextField searchField;

    @FXML private Label resultCountLabel;

    @FXML private VBox emptyStateBox;



    private final ObservableList<CustomerDTO> customerList = FXCollections.observableArrayList();

    private javafx.collections.transformation.FilteredList<CustomerDTO> filteredList;

    private ResourceBundle bundle;



    @FXML

    public void initialize() {

        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());



        filteredList = new javafx.collections.transformation.FilteredList<>(customerList, p -> true);

        searchField.textProperty().addListener((o, ov, nv) -> updateSearch(nv));

        filteredList.addListener((javafx.collections.ListChangeListener<CustomerDTO>) c -> updateEmptyState());



        customersListView.setItems(filteredList);

        customersListView.setCellFactory(lv -> new CustomerCardCell());

        loadCustomers();

    }



    private void updateSearch(String query) {

        filteredList.setPredicate(customer -> {

            if (query == null || query.isBlank()) {

                return true;

            }

            String q = query.toLowerCase(Locale.forLanguageTag("tr-TR"));

            return (customer.getName() != null && customer.getName().toLowerCase(Locale.forLanguageTag("tr-TR")).contains(q))

                    || (customer.getPhone() != null && customer.getPhone().toLowerCase().contains(q));

        });

    }



    private void updateEmptyState() {

        boolean empty = filteredList.isEmpty();

        emptyStateBox.setVisible(empty);

        emptyStateBox.setManaged(empty);

        customersListView.setVisible(!empty);

        resultCountLabel.setText(filteredList.size() + " müşteri");

    }



    private class CustomerCardCell extends ListCell<CustomerDTO> {

        private final HBox card = new HBox(12);

        private final Label avatar = new Label();

        private final VBox content = new VBox(4);

        private final Label nameLabel = new Label();

        private final Label metaLabel = new Label();

        private final Button whatsAppBtn = new Button();

        CustomerCardCell() {
            avatar.getStyleClass().add("customer-avatar");
            avatar.setAlignment(Pos.CENTER);
            nameLabel.getStyleClass().add("customer-card-name");
            metaLabel.getStyleClass().add("customer-card-meta");
            metaLabel.setWrapText(true);

            FontIcon whatsAppIcon = FontIcon.of(MaterialDesignW.WHATSAPP, 16);
            whatsAppIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            whatsAppBtn.setGraphic(whatsAppIcon);
            whatsAppBtn.getStyleClass().addAll("btn-whatsapp", "btn-whatsapp-icon");
            whatsAppBtn.setTooltip(new Tooltip("WhatsApp"));

            whatsAppBtn.setOnAction(e -> {

                CustomerDTO c = getItem();

                if (c != null && c.getPhone() != null) {

                    WhatsAppHelper.openWhatsApp(c.getPhone());

                    e.consume();

                }

            });



            HBox phoneRow = new HBox(8, metaLabel, whatsAppBtn);

            phoneRow.setAlignment(Pos.CENTER_LEFT);

            content.getChildren().addAll(nameLabel, phoneRow);

            card.getStyleClass().add("customer-card");

            card.setAlignment(Pos.CENTER_LEFT);

            card.getChildren().addAll(avatar, content);

            HBox.setHgrow(content, Priority.ALWAYS);



            card.setOnMouseClicked(e -> {
                if (isEmpty() || getItem() == null || e.getTarget() instanceof Button) {
                    return;
                }

                customersListView.getSelectionModel().select(getItem());

                handleEditCustomer(getItem());

            });

            AnimationHelper.applyCardHover(card);

        }



        @Override

        protected void updateItem(CustomerDTO customer, boolean empty) {

            super.updateItem(customer, empty);

            if (empty || customer == null) {

                setGraphic(null);

                return;

            }

            String displayName = TableUiHelper.toTitleCase(customer.getName());

            avatar.setText(TableUiHelper.avatarLetter(displayName));

            nameLabel.setText(displayName);

            String phone = customer.getPhone() != null ? customer.getPhone() : "—";

            String address = TableUiHelper.truncate(customer.getAddress(), 60);

            metaLabel.setText(phone + "  ·  " + address);

            whatsAppBtn.setVisible(customer.getPhone() != null && !customer.getPhone().isBlank());

            whatsAppBtn.setManaged(whatsAppBtn.isVisible());

            setGraphic(card);

        }

    }



    private void handleEditCustomer(CustomerDTO customer) {

        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/customer_detail.fxml"), bundle);

            javafx.scene.Parent root = loader.load();

            CustomerDetailController controller = loader.getController();

            controller.setCustomer(customer);

            controller.setOnSaveSuccess(this::loadCustomers);

            javafx.stage.Stage stage = new javafx.stage.Stage();

            stage.setTitle(bundle.getString("customer.details.title"));

            stage.setScene(ThemeHelper.createDialogScene(root));

            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            stage.showAndWait();

        } catch (Exception e) {

            e.printStackTrace();

            AlertHelper.showAlert(Alert.AlertType.ERROR, customersListView.getScene().getWindow(),

                    "Error", "Could not open details: " + e.getMessage());

        }

    }



    @FXML

    private void handleRefresh() {

        loadCustomers();

    }



    @FXML

    private void handleAddCustomer() {

        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/customer_dialog.fxml"), bundle);

            javafx.scene.Parent root = loader.load();

            CustomerDialogController controller = loader.getController();

            controller.setOnSaveSuccess(saved -> loadCustomers());

            javafx.stage.Stage stage = new javafx.stage.Stage();

            stage.setTitle("Add New Customer");

            stage.setScene(ThemeHelper.createDialogScene(root));

            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            stage.showAndWait();

        } catch (Exception e) {

            e.printStackTrace();

            AlertHelper.showAlert(Alert.AlertType.ERROR, customersListView.getScene().getWindow(),

                    "Error", "Could not open dialog: " + e.getMessage());

        }

    }



    private void loadCustomers() {

        CustomerApi api = RetrofitClient.getClient().create(CustomerApi.class);

        api.getAllCustomers().enqueue(new Callback<>() {

            @Override

            public void onResponse(Call<List<CustomerDTO>> call, Response<List<CustomerDTO>> response) {

                if (response.isSuccessful() && response.body() != null) {

                    Platform.runLater(() -> {

                        customerList.clear();

                        customerList.addAll(response.body());

                        updateEmptyState();

                    });

                } else {

                    Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,

                            customersListView.getScene().getWindow(), "Error",

                            "Failed to load customers: " + response.code()));

                }

            }



            @Override

            public void onFailure(Call<List<CustomerDTO>> call, Throwable t) {

                Platform.runLater(() -> AlertHelper.showAlert(Alert.AlertType.ERROR,

                        customersListView.getScene().getWindow(), "Network Error",

                        "Could not connect to server: " + t.getMessage()));

            }

        });

    }

}


