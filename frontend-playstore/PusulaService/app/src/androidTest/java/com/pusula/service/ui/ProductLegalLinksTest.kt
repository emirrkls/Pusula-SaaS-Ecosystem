package com.pusula.service.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pusula.service.core.ProductLegal
import com.pusula.service.ui.components.ProductLegalLinks
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class ProductLegalLinksTest {
    @get:Rule val compose = createComposeRule()
    @Test fun policyAndTermsOpenTheCorrectProductPagesWithoutAnyExternalRequest() {
        val opened = mutableListOf<String>()
        compose.setContent { MaterialTheme { ProductLegalLinks(onOpenLink = { opened += it }) } }
        compose.onNodeWithText("Gizlilik Politikası").assertIsDisplayed().performClick()
        compose.onNodeWithText("Kullanım Şartları").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(ProductLegal.PRIVACY_URL, ProductLegal.TERMS_URL), opened) }
    }
}
