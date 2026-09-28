package com.pusula.desktop.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DesktopCustomerManagementContractTest {

    @Test
    void desktopCustomerCardsExposeEditAndDeleteBackedByTheCustomerApi() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java", "com", "pusula", "desktop");
        String api = Files.readString(sourceRoot.resolve(Path.of("api", "CustomerApi.java")),
                StandardCharsets.UTF_8);
        String controller = Files.readString(sourceRoot.resolve(Path.of("controller", "CustomerController.java")),
                StandardCharsets.UTF_8);

        assertTrue(api.contains("@DELETE(\"api/customers/{id}\")"));
        assertTrue(api.contains("Call<Void> deleteCustomer"));
        assertTrue(controller.contains("customer.action.edit"));
        assertTrue(controller.contains("customer.action.delete"));
        assertTrue(controller.contains("handleDeleteCustomer"));
        assertTrue(controller.contains("ApiErrorHelper.message"),
                "Backend history-protection messages must be shown to the operator");
    }

    @Test
    void customerDetailManagesWhatsAppConsentOnlyThroughDedicatedAdminEndpoints() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java", "com", "pusula", "desktop");
        String api = Files.readString(sourceRoot.resolve(Path.of("api", "CustomerApi.java")),
                StandardCharsets.UTF_8);
        String customerDto = Files.readString(sourceRoot.resolve(Path.of("dto", "CustomerDTO.java")),
                StandardCharsets.UTF_8);
        String controller = Files.readString(sourceRoot.resolve(Path.of("controller", "CustomerDetailController.java")),
                StandardCharsets.UTF_8);
        String fxml = Files.readString(Path.of("src", "main", "resources", "view", "customer_detail.fxml"),
                StandardCharsets.UTF_8);
        String messages = Files.readString(Path.of("src", "main", "resources", "i18n", "messages_tr.properties"),
                StandardCharsets.UTF_8);

        assertTrue(api.contains("@GET(\"api/customers/{id}/whatsapp-consent\")"));
        assertTrue(api.contains("@PUT(\"api/customers/{id}/whatsapp-consent\")"));
        assertFalse(customerDto.contains("whatsappOptIn"),
                "Normal customer save payload must not carry legal consent state");
        assertTrue(controller.contains("SessionManager.isAdmin()"));
        assertTrue(controller.contains("whatsappConsentSourceCombo.getValue() == null"));
        assertTrue(controller.contains("AlertHelper.showConfirmation"));
        assertTrue(fxml.contains("fx:id=\"whatsappConsentPane\""));
        assertTrue(fxml.contains("<FlowPane"),
                "Consent actions must wrap cleanly in compact customer dialogs");
        assertTrue(fxml.contains("%customer.whatsapp_consent.title"));
        assertTrue(messages.contains("customer.whatsapp_consent.revoke_confirm_message="));
    }
}
