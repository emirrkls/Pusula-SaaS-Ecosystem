package com.pusula.desktop.controller;

import com.pusula.desktop.api.CompanyApi;
import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.api.VehicleApi;
import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import com.pusula.desktop.dto.UserDTO;
import com.pusula.desktop.dto.VehicleDTO;
import com.pusula.desktop.entity.Company;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.AlertHelper;
import com.pusula.desktop.util.UTF8Control;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class SettingsController {

    // Fixed Expenses Tab (existing)
    @FXML
    private TableView<FixedExpenseDefinitionDTO> fixedExpensesTable;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colName;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colAmount;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colCategory;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colDayOfMonth;
    @FXML
    private TableColumn<FixedExpenseDefinitionDTO, String> colDescription;

    // User Management Tab
    @FXML
    private TableView<UserDTO> usersTable;
    @FXML
    private TableColumn<UserDTO, String> colUsername;
    @FXML
    private TableColumn<UserDTO, String> colFullName;
    @FXML
    private TableColumn<UserDTO, String> colRole;

    // Company Profile Tab
    @FXML
    private TextField txtCompanyName;
    @FXML
    private TextField txtCompanyPhone;
    @FXML
    private TextField txtCompanyEmail;
    @FXML
    private TextArea txtCompanyAddress;

    // Vehicles Tab
    @FXML
    private TableView<VehicleDTO> vehiclesTable;
    @FXML
    private TableColumn<VehicleDTO, Long> colVehicleId;
    @FXML
    private TableColumn<VehicleDTO, String> colLicensePlate;
    @FXML
    private TableColumn<VehicleDTO, String> colDriverName;
    @FXML
    private TableColumn<VehicleDTO, String> colVehicleStatus;

    private FinanceApi financeApi;
    private UserApi userApi;
    private CompanyApi companyApi;
    private VehicleApi vehicleApi;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
        financeApi = RetrofitClient.getClient().create(FinanceApi.class);
        userApi = RetrofitClient.getClient().create(UserApi.class);
        companyApi = RetrofitClient.getClient().create(CompanyApi.class);
        vehicleApi = RetrofitClient.getClient().create(VehicleApi.class);

        setupFixedExpensesTable();
        setupUsersTable();
        setupVehiclesTable();

        loadFixedExpenses();
        loadUsers();
        loadCompanyProfile();
        loadVehicles();
    }

    // ============ FIXED EXPENSES TAB (existing) ============

    private void setupFixedExpensesTable() {
        colName.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));

        colAmount.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f ₺", cellData.getValue().getDefaultAmount())));

        colCategory.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getCategory();
            String key = "category." + category;
            String localized = bundle.containsKey(key) ? bundle.getString(key) : category;
            return new javafx.beans.property.SimpleStringProperty(localized);
        });

        colDayOfMonth.setCellValueFactory(cellData -> {
            Integer day = cellData.getValue().getDayOfMonth();
            return new javafx.beans.property.SimpleStringProperty(day != null ? day.toString() : "-");
        });

        colDescription.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getDescription() != null ? cellData.getValue().getDescription() : ""));

        // Row factory to highlight overdue unpaid expenses in red
        fixedExpensesTable.setRowFactory(tv -> new javafx.scene.control.TableRow<FixedExpenseDefinitionDTO>() {
            {
                // Add listener for selection changes
                selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                    updateRowStyle(getItem(), isNowSelected);
                });
            }

            @Override
            protected void updateItem(FixedExpenseDefinitionDTO item, boolean empty) {
                super.updateItem(item, empty);
                updateRowStyle(item, isSelected());
            }

            private void updateRowStyle(FixedExpenseDefinitionDTO item, boolean selected) {
                if (item == null) {
                    setStyle("");
                    return;
                }

                if (selected) {
                    // Force dark blue background with white text when selected
                    setStyle("-fx-background-color: #334155; -fx-text-fill: white;");
                } else {
                    int currentDay = java.time.LocalDate.now().getDayOfMonth();
                    Integer dueDay = item.getDayOfMonth();
                    boolean isOverdue = !item.isPaidThisMonth() && dueDay != null && dueDay < currentDay;

                    if (isOverdue) {
                        // Red background for overdue unpaid expenses
                        setStyle("-fx-background-color: #ffcccc;");
                    } else if (item.isPaidThisMonth()) {
                        // Light green for paid expenses
                        setStyle("-fx-background-color: #ccffcc;");
                    } else {
                        // Default style
                        setStyle("");
                    }
                }
            }
        });
    }

    private void loadFixedExpenses() {
        financeApi.getFixedExpenses(1L).enqueue(new Callback<List<FixedExpenseDefinitionDTO>>() {
            @Override
            public void onResponse(Call<List<FixedExpenseDefinitionDTO>> call,
                    Response<List<FixedExpenseDefinitionDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        fixedExpensesTable.setItems(FXCollections.observableArrayList(response.body()));
                    });
                }
            }

            @Override
            public void onFailure(Call<List<FixedExpenseDefinitionDTO>> call, Throwable t) {
                System.err.println("Failed to load fixed expenses: " + t.getMessage());
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Sabit giderler yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleAddFixedExpense() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/fixed_expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            FixedExpenseDialogController dialogController = loader.getController();
            // Pass available expenses for linking
            dialogController.setAvailableExpenses(fixedExpensesTable.getItems());
            dialogController.setOnSave(() -> {
                FixedExpenseDefinitionDTO result = dialogController.getResult();
                if (result != null) {
                    createFixedExpense(result);
                }
            });

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("settings.add_fixed_expense"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditFixedExpense() {
        FixedExpenseDefinitionDTO selected = fixedExpensesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen düzenlemek için bir sabit gider seçin.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/fixed_expense_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            FixedExpenseDialogController dialogController = loader.getController();
            // Pass available expenses for linking
            dialogController.setAvailableExpenses(fixedExpensesTable.getItems());
            dialogController.setFixedExpense(selected);
            dialogController.setOnSave(() -> {
                FixedExpenseDefinitionDTO result = dialogController.getResult();
                if (result != null) {
                    updateFixedExpense(result);
                }
            });

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("settings.edit_fixed_expense"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteFixedExpense() {
        FixedExpenseDefinitionDTO selected = fixedExpensesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen silmek için bir sabit gider seçin.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Silme Onayı");
        confirmation.setHeaderText(selected.getName() + " sabit giderini silmek istediğinize emin misiniz?");
        confirmation.setContentText("Bu işlem geri alınamaz.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteFixedExpense(selected.getId());
            }
        });
    }

    private void createFixedExpense(FixedExpenseDefinitionDTO expense) {
        financeApi.createFixedExpense(expense).enqueue(new Callback<FixedExpenseDefinitionDTO>() {
            @Override
            public void onResponse(Call<FixedExpenseDefinitionDTO> call, Response<FixedExpenseDefinitionDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Sabit gider başarıyla eklendi!");
                        loadFixedExpenses();
                    });
                }
            }

            @Override
            public void onFailure(Call<FixedExpenseDefinitionDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Ekleme işlemi başarısız: " + t.getMessage());
                });
            }
        });
    }

    private void updateFixedExpense(FixedExpenseDefinitionDTO expense) {
        financeApi.updateFixedExpense(expense.getId(), expense).enqueue(new Callback<FixedExpenseDefinitionDTO>() {
            @Override
            public void onResponse(Call<FixedExpenseDefinitionDTO> call, Response<FixedExpenseDefinitionDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Sabit gider başarıyla güncellendi!");
                        loadFixedExpenses();
                    });
                }
            }

            @Override
            public void onFailure(Call<FixedExpenseDefinitionDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Güncelleme işlemi başarısız: " + t.getMessage());
                });
            }
        });
    }

    private void deleteFixedExpense(Long id) {
        financeApi.deleteFixedExpense(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Sabit gider başarıyla silindi!");
                        loadFixedExpenses();
                    });
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Silme işlemi başarısız: " + t.getMessage());
                });
            }
        });
    }

    // ============ USER MANAGEMENT TAB ============

    private void setupUsersTable() {
        colUsername.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getUsername()));
        colFullName.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getFullName() != null ? cellData.getValue().getFullName() : ""));
        colRole.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getRole()));
    }

    private void loadUsers() {
        userApi.getAllUsers().enqueue(new Callback<List<UserDTO>>() {
            @Override
            public void onResponse(Call<List<UserDTO>> call, Response<List<UserDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        usersTable.setItems(FXCollections.observableArrayList(response.body()));
                    });
                }
            }

            @Override
            public void onFailure(Call<List<UserDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Kullanıcılar yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleAddUser() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            UserDialogController dialogController = loader.getController();

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("settings.add_user"));
            stage.setScene(new Scene(root));
            stage.showAndWait();

            UserDTO result = dialogController.getResult();
            if (result != null) {
                createUser(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditUser() {
        UserDTO selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen düzenlemek için bir kullanıcı seçin.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            UserDialogController dialogController = loader.getController();
            dialogController.setUser(selected);

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("settings.edit_user"));
            stage.setScene(new Scene(root));
            stage.showAndWait();

            UserDTO result = dialogController.getResult();
            if (result != null) {
                java.io.File signatureFile = dialogController.getSelectedSignatureFile();
                updateUser(result, signatureFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleResetPassword() {
        UserDTO selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen bir kullanıcı seçin.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Şifre Sıfırla");
        dialog.setHeaderText(selected.getUsername() + " için yeni şifre girin:");
        dialog.setContentText("Yeni Şifre:");

        dialog.showAndWait().ifPresent(password -> {
            if (!password.isEmpty()) {
                resetPassword(selected.getId(), password);
            }
        });
    }

    @FXML
    private void handleDeleteUser() {
        UserDTO selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen silmek için bir kullanıcı seçin.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Silme Onayı");
        confirmation.setHeaderText(selected.getUsername() + " kullanıcısını silmek istediğinize emin misiniz?");
        confirmation.setContentText("Bu işlem geri alınamaz.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteUser(selected.getId());
            }
        });
    }

    private void createUser(UserDTO user) {
        userApi.createUser(user).enqueue(new Callback<UserDTO>() {
            @Override
            public void onResponse(Call<UserDTO> call, Response<UserDTO> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Kullanıcı başarıyla eklendi!");
                        loadUsers();
                    });
                }
            }

            @Override
            public void onFailure(Call<UserDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Kullanıcı eklenemedi: " + t.getMessage());
                });
            }
        });
    }

    private void updateUser(UserDTO user, java.io.File signatureFile) {
        userApi.updateUser(user.getId(), user).enqueue(new Callback<UserDTO>() {
            @Override
            public void onResponse(Call<UserDTO> call, Response<UserDTO> response) {
                if (response.isSuccessful()) {
                    // Upload signature if file was selected
                    if (signatureFile != null) {
                        uploadSignature(user.getId(), signatureFile);
                    }
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Kullanıcı başarıyla güncellendi!");
                        loadUsers();
                    });
                }
            }

            @Override
            public void onFailure(Call<UserDTO> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Kullanıcı güncellenemedi: " + t.getMessage());
                });
            }
        });
    }

    private void uploadSignature(Long userId, java.io.File signatureFile) {
        okhttp3.RequestBody requestFile = okhttp3.RequestBody.create(
                signatureFile, okhttp3.MediaType.parse("image/*"));
        okhttp3.MultipartBody.Part body = okhttp3.MultipartBody.Part.createFormData(
                "file", signatureFile.getName(), requestFile);

        userApi.uploadSignature(userId, body).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    System.out.println("Signature uploaded successfully for user " + userId);
                } else {
                    System.err.println("Signature upload failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                System.err.println("Signature upload error: " + t.getMessage());
            }
        });
    }

    private void deleteUser(Long userId) {
        deleteUserWithReassign(userId, null);
    }

    private void deleteUserWithReassign(Long userId, Long reassignTo) {
        userApi.deleteUser(userId, reassignTo).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Kullanıcı başarıyla silindi!");
                        loadUsers();
                    });
                } else if (response.code() == 409) {
                    // Active tickets exist, parse response and ask for reassignment
                    try {
                        String errorBody = response.errorBody().string();
                        // Manual extremely simple JSON parsing
                        Long ticketCount = 0L;
                        if (errorBody.contains("\"count\":")) {
                            String countStr = errorBody.split("\"count\":")[1].split("}")[0].trim();
                            ticketCount = Long.parseLong(countStr);
                        }
                        
                        Long currentTicketCount = ticketCount;
                        Platform.runLater(() -> showReassignDialog(userId, currentTicketCount));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                                "Kullanıcı silinemedi. Sunucu Hatası: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Kullanıcı silinemedi: " + t.getMessage());
                });
            }
        });
    }

    private void showReassignDialog(Long userToDeleteId, Long ticketCount) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/reassign_tickets_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            ReassignTicketsDialogController dialogController = loader.getController();

            // Filter out the user being deleted
            List<UserDTO> availableTechs = usersTable.getItems().stream()
                    .filter(u -> !u.getId().equals(userToDeleteId) && ("TECHNICIAN".equals(u.getRole()) || "COMPANY_ADMIN".equals(u.getRole())))
                    .toList();

            dialogController.setup(ticketCount, availableTechs);

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("reassign.title"));
            stage.setScene(new Scene(root));
            stage.showAndWait();

            Long newTechId = dialogController.getSelectedTechnicianId();
            if (newTechId != null) {
                // User chose someone, proceed with delete + reassign
                deleteUserWithReassign(userToDeleteId, newTechId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Devir ekranı açılamadı: " + e.getMessage());
        }
    }

    private void resetPassword(Long userId, String newPassword) {
        java.util.Map<String, String> payload = new java.util.HashMap<>();
        payload.put("password", newPassword);

        userApi.resetPassword(userId, payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Şifre başarıyla sıfırlandı!");
                    });
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Şifre sıfırlanamadı: " + t.getMessage());
                });
            }
        });
    }

    // ============ VEHICLES TAB ============

    private void setupVehiclesTable() {
        colVehicleId.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleLongProperty(cellData.getValue().getId()).asObject());
        colLicensePlate.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLicensePlate()));
        colDriverName.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getDriverName() != null ? cellData.getValue().getDriverName() : ""));
        colVehicleStatus.setCellValueFactory(cellData -> {
            String status = (cellData.getValue().getIsActive() != null && cellData.getValue().getIsActive())
                    ? bundle.getString("vehicle.active")
                    : bundle.getString("status.inactive");
            return new javafx.beans.property.SimpleStringProperty(status);
        });
    }

    private void loadVehicles() {
        vehicleApi.getAll(1L).enqueue(new Callback<List<VehicleDTO>>() {
            @Override
            public void onResponse(Call<List<VehicleDTO>> call, Response<List<VehicleDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        vehiclesTable.setItems(FXCollections.observableArrayList(response.body()));
                    });
                }
            }

            @Override
            public void onFailure(Call<List<VehicleDTO>> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Araçlar yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleAddVehicle() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/vehicle_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            VehicleDialogController dialogController = loader.getController();
            dialogController.setOnSaveSuccess(this::loadVehicles);

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("vehicle.add"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditVehicle() {
        VehicleDTO selected = vehiclesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen düzenlemek için bir araç seçin.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/vehicle_dialog.fxml"), bundle);
            javafx.scene.Parent root = loader.load();

            VehicleDialogController dialogController = loader.getController();
            dialogController.setVehicle(selected);
            dialogController.setOnSaveSuccess(this::loadVehicles);

            Stage stage = new Stage();
            com.pusula.desktop.util.StageHelper.setIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("vehicle.edit"));
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                    "Dialog açılamadı: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteVehicle() {
        VehicleDTO selected = vehiclesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, null, "Uyarı",
                    "Lütfen silmek için bir araç seçin.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Silme Onayı");
        confirmation.setHeaderText(selected.getLicensePlate() + " plakalı aracı silmek istediğinize emin misiniz?");
        confirmation.setContentText("Bu işlem geri alınamaz.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteVehicle(selected.getId());
            }
        });
    }

    private void deleteVehicle(Long id) {
        vehicleApi.delete(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Araç başarıyla silindi!");
                        loadVehicles();
                    });
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Araç silinemedi: " + t.getMessage());
                });
            }
        });
    }

    // ============ COMPANY PROFILE TAB ============

    @FXML
    private javafx.scene.image.ImageView imgCompanyLogo;

    private void loadCompanyProfile() {
        companyApi.getMyCompany().enqueue(new Callback<com.pusula.desktop.entity.Company>() {
            @Override
            public void onResponse(Call<com.pusula.desktop.entity.Company> call,
                    Response<com.pusula.desktop.entity.Company> response) {
                if (response.isSuccessful() && response.body() != null) {
                    com.pusula.desktop.entity.Company company = response.body();
                    Platform.runLater(() -> {
                        txtCompanyName.setText(company.getName());
                        txtCompanyPhone.setText(company.getPhone() != null ? company.getPhone() : "");
                        txtCompanyEmail.setText(company.getEmail() != null ? company.getEmail() : "");
                        txtCompanyAddress.setText(company.getAddress() != null ? company.getAddress() : "");

                        if (company.getLogoUrl() != null && !company.getLogoUrl().isEmpty()) {
                            // Assuming logoUrl is a relative path or full URL.
                            // If relative, prepend BASE_URL.
                            // For now, let's try loading it directly if it's a full URL,
                            // or construct it if we know the pattern.
                            // Usually: BASE_URL + logoUrl
                            String imageUrl = RetrofitClient.BASE_URL.replace("/api", "") + company.getLogoUrl();
                            if (company.getLogoUrl().startsWith("http")) {
                                imageUrl = company.getLogoUrl();
                            }
                            // Fix double slashes if any (simple check)
                            // imageUrl = imageUrl.replace("com//", "com/");

                            imgCompanyLogo.setImage(new javafx.scene.image.Image(imageUrl, true)); // background loading
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<com.pusula.desktop.entity.Company> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Şirket bilgileri yüklenemedi: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleSaveCompany() {
        com.pusula.desktop.entity.Company company = com.pusula.desktop.entity.Company.builder()
                .name(txtCompanyName.getText())
                .phone(txtCompanyPhone.getText())
                .email(txtCompanyEmail.getText())
                .address(txtCompanyAddress.getText())
                .build();

        companyApi.updateMyCompany(company).enqueue(new Callback<com.pusula.desktop.entity.Company>() {
            @Override
            public void onResponse(Call<com.pusula.desktop.entity.Company> call,
                    Response<com.pusula.desktop.entity.Company> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Şirket bilgileri başarıyla güncellendi!");
                        loadCompanyProfile(); // Refresh
                    });
                }
            }

            @Override
            public void onFailure(Call<com.pusula.desktop.entity.Company> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Şirket bilgileri güncellenemedi: " + t.getMessage());
                });
            }
        });
    }

    @FXML
    private void handleUploadLogo() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Logo Seç");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Resim Dosyaları", "*.png", "*.jpg", "*.jpeg"));

        java.io.File selectedFile = fileChooser.showOpenDialog(txtCompanyName.getScene().getWindow());
        if (selectedFile != null) {
            uploadLogoFile(selectedFile);
        }
    }

    private void uploadLogoFile(java.io.File file) {
        // Prepare file part
        okhttp3.RequestBody requestFile = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("image/*"), file);
        okhttp3.MultipartBody.Part body = okhttp3.MultipartBody.Part.createFormData("file", file.getName(),
                requestFile);

        companyApi.uploadLogo(body).enqueue(new Callback<com.pusula.desktop.entity.Company>() {
            @Override
            public void onResponse(Call<com.pusula.desktop.entity.Company> call,
                    Response<com.pusula.desktop.entity.Company> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Logo başarıyla yüklendi!");
                        loadCompanyProfile(); // Refresh to show new logo
                    });
                } else {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                                "Logo yüklenemedi: " + response.code());
                    });
                }
            }

            @Override
            public void onFailure(Call<com.pusula.desktop.entity.Company> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Logo yüklenirken hata oluştu: " + t.getMessage());
                });
            }
        });
    }
}
