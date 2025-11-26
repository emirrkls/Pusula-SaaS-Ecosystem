package com.pusula.desktop.controller;

import com.pusula.desktop.api.CompanyApi;
import com.pusula.desktop.api.FinanceApi;
import com.pusula.desktop.api.UserApi;
import com.pusula.desktop.dto.FixedExpenseDefinitionDTO;
import com.pusula.desktop.dto.UserDTO;
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

    private FinanceApi financeApi;
    private UserApi userApi;
    private CompanyApi companyApi;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("tr-TR"), new UTF8Control());
        financeApi = RetrofitClient.getClient().create(FinanceApi.class);
        userApi = RetrofitClient.getClient().create(UserApi.class);
        companyApi = RetrofitClient.getClient().create(CompanyApi.class);

        setupFixedExpensesTable();
        setupUsersTable();

        loadFixedExpenses();
        loadUsers();
        loadCompanyProfile();
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
            dialogController.setOnSave(() -> {
                FixedExpenseDefinitionDTO result = dialogController.getResult();
                if (result != null) {
                    createFixedExpense(result);
                }
            });

            Stage stage = new Stage();
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
            dialogController.setFixedExpense(selected);
            dialogController.setOnSave(() -> {
                FixedExpenseDefinitionDTO result = dialogController.getResult();
                if (result != null) {
                    updateFixedExpense(result);
                }
            });

            Stage stage = new Stage();
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
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("settings.edit_user"));
            stage.setScene(new Scene(root));
            stage.showAndWait();

            UserDTO result = dialogController.getResult();
            if (result != null) {
                updateUser(result);
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

    private void updateUser(UserDTO user) {
        userApi.updateUser(user.getId(), user).enqueue(new Callback<UserDTO>() {
            @Override
            public void onResponse(Call<UserDTO> call, Response<UserDTO> response) {
                if (response.isSuccessful()) {
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

    private void deleteUser(Long userId) {
        userApi.deleteUser(userId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Platform.runLater(() -> {
                        AlertHelper.showAlert(Alert.AlertType.INFORMATION, null, "Başarılı",
                                "Kullanıcı başarıyla silindi!");
                        loadUsers();
                    });
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Platform.runLater(() -> {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, null, "Hata",
                            "Kullanıcı silinemedi: " + t.getMessage());
                });
            }
        });
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

    // ============ COMPANY PROFILE TAB ============

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
}
