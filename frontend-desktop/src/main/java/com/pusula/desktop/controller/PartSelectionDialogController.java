package com.pusula.desktop.controller;

import com.pusula.desktop.api.InventoryApi;
import com.pusula.desktop.dto.InventoryDTO;
import com.pusula.desktop.network.RetrofitClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.List;

public class PartSelectionDialogController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<InventoryDTO> partsTable;

    @FXML
    private TableColumn<InventoryDTO, String> colPartName;

    @FXML
    private TableColumn<InventoryDTO, String> colStock;

    @FXML
    private TableColumn<InventoryDTO, String> colPrice;

    @FXML
    private Spinner<Integer> quantitySpinner;

    private final ObservableList<InventoryDTO> partsList = FXCollections.observableArrayList();
    private FilteredList<InventoryDTO> filteredParts;
    private InventoryDTO selectedPart;
    private Integer selectedQuantity;
    private Runnable onPartSelected;

    @FXML
    public void initialize() {
        setupTable();
        setupSpinner();
        setupSearch();
        loadParts();
    }

    private void setupTable() {
        colPartName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPartName()));
        colStock.setCellValueFactory(
                cellData -> new SimpleStringProperty(String.valueOf(cellData.getValue().getQuantity())));
        colPrice.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getSellPrice().toString()));

        filteredParts = new FilteredList<>(partsList, p -> true);
        partsTable.setItems(filteredParts);
    }

    private void setupSpinner() {
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1);
        quantitySpinner.setValueFactory(valueFactory);
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredParts.setPredicate(part -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                return part.getPartName().toLowerCase().contains(lowerCaseFilter);
            });
        });
    }

    private void loadParts() {
        InventoryApi api = RetrofitClient.getClient().create(InventoryApi.class);
        api.getAllInventory().enqueue(new Callback<List<InventoryDTO>>() {
            @Override
            public void onResponse(Call<List<InventoryDTO>> call, Response<List<InventoryDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        partsList.clear();
                        partsList.addAll(response.body());
                    });
                }
            }

            @Override
            public void onFailure(Call<List<InventoryDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Failed to load inventory");
                    alert.setContentText(t.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    @FXML
    private void handleSelect() {
        selectedPart = partsTable.getSelectionModel().getSelectedItem();
        if (selectedPart == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("No part selected");
            alert.setContentText("Please select a part from the list.");
            alert.showAndWait();
            return;
        }

        selectedQuantity = quantitySpinner.getValue();

        // Check if enough stock is available
        if (selectedQuantity > selectedPart.getQuantity()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Insufficient Stock");
            alert.setHeaderText("Not enough stock available");
            alert.setContentText("Available: " + selectedPart.getQuantity() + ", Requested: " + selectedQuantity);
            alert.showAndWait();
            return;
        }

        if (onPartSelected != null) {
            onPartSelected.run();
        }

        closeDialog();
    }

    @FXML
    private void handleCancel() {
        selectedPart = null;
        selectedQuantity = null;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) searchField.getScene().getWindow();
        stage.close();
    }

    public InventoryDTO getSelectedPart() {
        return selectedPart;
    }

    public Integer getSelectedQuantity() {
        return selectedQuantity;
    }

    public void setOnPartSelected(Runnable onPartSelected) {
        this.onPartSelected = onPartSelected;
    }
}
