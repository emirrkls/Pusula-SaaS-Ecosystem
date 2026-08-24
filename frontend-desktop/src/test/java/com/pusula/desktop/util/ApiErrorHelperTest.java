package com.pusula.desktop.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiErrorHelperTest {
    @Test
    void translatesInventoryBarcodeConstraintWithoutLeakingDatabaseDetails() {
        String result = ApiErrorHelper.userFacing(
                "ERROR: duplicate key value violates unique constraint "
                        + "\"uq_inventory_company_barcode_normalized_active\"");

        assertEquals("Bu barkod işletmenizde aktif başka bir ürün tarafından kullanılıyor. "
                + "Ürünün barkodunu kontrol edip tekrar deneyin.", result);
        assertFalse(result.contains("constraint"));
    }

    @Test
    void preservesUsefulUserFacingMessages() {
        assertEquals("Tutar sıfırdan büyük olmalıdır.",
                ApiErrorHelper.userFacing("Tutar sıfırdan büyük olmalıdır."));
    }
}
