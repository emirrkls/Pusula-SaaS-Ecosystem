package com.pusula.desktop.controller;

import com.pusula.desktop.dto.InventoryDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryControllerSearchTest {

    @Test
    void searchesBarcodeAndMultipleFieldsWithTurkishCaseRules() {
        InventoryDTO item = inventoryItem();

        assertTrue(InventoryController.matchesSearch(item, "8691234567890"));
        assertTrue(InventoryController.matchesSearch(item, "mitsubishi inverter"));
        assertTrue(InventoryController.matchesSearch(item, "İNVERTER"));
        assertFalse(InventoryController.matchesSearch(item, "mitsubishi kombi"));
    }

    @Test
    void searchesNumericStockAndPriceValues() {
        InventoryDTO item = inventoryItem();

        assertTrue(InventoryController.matchesSearch(item, "12"));
        assertTrue(InventoryController.matchesSearch(item, "750.00"));
        assertFalse(InventoryController.matchesSearch(item, "9999"));
    }

    private InventoryDTO inventoryItem() {
        InventoryDTO item = new InventoryDTO();
        item.setPartName("İnverter Klima Kartı");
        item.setBrand("Mitsubishi");
        item.setCategory("Elektronik");
        item.setBarcode("8691234567890");
        item.setQuantity(12);
        item.setBuyPrice(new BigDecimal("500.00"));
        item.setSellPrice(new BigDecimal("750.00"));
        return item;
    }
}
