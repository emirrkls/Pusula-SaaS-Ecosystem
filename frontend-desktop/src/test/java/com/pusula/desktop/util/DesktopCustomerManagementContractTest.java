package com.pusula.desktop.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
