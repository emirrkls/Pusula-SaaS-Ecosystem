package com.pusula.service.guide

import com.google.gson.JsonParser
import com.pusula.service.data.model.guide.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

class GuideCatalogTest {
    private fun json() = File("src/main/assets/app-guide.json").readText()
    private fun catalog() = GuideCatalog.decode(json())
    @Test fun catalogCoversFeaturesAndFiltersRoles() {
        val c = catalog()
        assertEquals(10, c.categories.size); assertEquals(56, c.topics.size)
        assertFalse(c.visible("TECHNICIAN").any { it.id == "company-settings" })
        assertTrue(c.visible("TECHNICIAN").any { it.id == "tickets-notes" })
        assertFalse(c.visible("COMPANY_ADMIN").any { it.id == "apple-login" })
        assertTrue(c.visible("COMPANY_ADMIN").any { it.id == "discount" })
        assertTrue(c.visible("UNKNOWN").isEmpty())
    }
    @Test fun turkishSearchAndCategories() {
        assertTrue(catalog().visible("COMPANY_ADMIN", query = "ISKONTO").any { it.id == "discount" })
        assertTrue(catalog().visible("TECHNICIAN", query = "NOTLAR").any { it.id == "tickets-notes" })
        assertTrue(catalog().visible("COMPANY_ADMIN", category = "payments").all { it.category == "payments" })
        assertTrue(catalog().visible("TECHNICIAN", query = "olmayan-konu-123").isEmpty())
    }
    @Test fun continueBackAndBounds() {
        for (count in 1..6) {
            var cursor = GuideCursor(count)
            assertEquals(0, cursor.back().boundedIndex)
            for (index in 0 until count) {
                assertEquals(index, cursor.boundedIndex)
                assertEquals(index == count - 1, cursor.isLast)
                cursor = cursor.next()
            }
            assertEquals(count - 1, cursor.boundedIndex)
        }
        assertEquals(0, GuideCursor(0, 99).boundedIndex)
        assertEquals(0, GuideCursor(3, -3).boundedIndex)
        assertEquals(2, GuideCursor(3, 99).boundedIndex)
    }
    @Test fun resumeReplayAndRevision() {
        val t = catalog().topics.first()
        assertEquals(1, GuideRecord(step = 1).start(t))
        assertEquals(t.steps.lastIndex, GuideRecord(step = 999).start(t))
        assertEquals(0, GuideRecord(step = 2, completedRevision = t.revision).start(t))
        assertFalse(GuideRecord(completedRevision = t.revision - 1).complete(t))
    }
    @Test fun invalidCatalogRejected() {
        val root = JsonParser.parseString(json()).asJsonObject
        root.addProperty("schemaVersion", 99)
        assertTrue(runCatching { GuideCatalog.decode(root.toString()) }.isFailure)
        root.addProperty("schemaVersion", 1)
        root.getAsJsonArray("topics")[0].asJsonObject.add("steps", com.google.gson.JsonArray())
        assertTrue(runCatching { GuideCatalog.decode(root.toString()) }.isFailure)
    }
    private fun token(subject: String, suffix: String = "sig") =
        "header." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"$subject\"}".toByteArray()) + ".$suffix"
    @Test fun progressIdentitySurvivesTokenRefreshButSeparatesAccountsAndCompanies() {
        assertEquals(GuideIdentity.scope(token("a"), 1), GuideIdentity.scope(token("a", "new"), 1))
        assertNotEquals(GuideIdentity.scope(token("a"), 1), GuideIdentity.scope(token("b"), 1))
        assertNotEquals(GuideIdentity.scope(token("a"), 1), GuideIdentity.scope(token("a"), 2))
        assertNull(GuideIdentity.scope(null, 1)); assertNull(GuideIdentity.scope("malformed", 1))
        assertEquals(64, GuideIdentity.scope(token("a"), 1)!!.length)
    }
}
