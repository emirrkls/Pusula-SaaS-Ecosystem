package com.pusula.desktop.controller;

import com.pusula.desktop.dto.BusinessAssetDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAssetControllerSearchTest {
    @Test
    void searchesAcrossNameSerialLocationAssignmentAndNotesWithTurkishCaseRules() {
        BusinessAssetDTO asset = new BusinessAssetDTO();
        asset.setAssetName("Şarjlı Matkap");
        asset.setCategory("Elektrikli El Aleti");
        asset.setSerialNumber("SN-12345");
        asset.setLocation("İzmir Depo");
        asset.setAssignedTo("Uğur Yılmaz");
        asset.setNotes("Yedek bataryalı");
        asset.setPurchaseDate(LocalDate.of(2026, 8, 1));
        asset.setQuantity(2);
        asset.setCondition("ACTIVE");

        assertTrue(BusinessAssetController.matchesSearch(asset, "şarjlı matkap"));
        assertTrue(BusinessAssetController.matchesSearch(asset, "sn-12345 uğur"));
        assertTrue(BusinessAssetController.matchesSearch(asset, "İZMİR"));
        assertTrue(BusinessAssetController.matchesSearch(asset, "2026-08-01"));
        assertFalse(BusinessAssetController.matchesSearch(asset, "vakum"));
    }
}
