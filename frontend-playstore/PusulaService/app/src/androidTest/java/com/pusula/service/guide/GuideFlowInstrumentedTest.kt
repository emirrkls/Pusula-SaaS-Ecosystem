package com.pusula.service.guide

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pusula.service.core.SessionState
import com.pusula.service.data.model.guide.GuideCatalog
import com.pusula.service.ui.guide.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Base64

class GuideFlowInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset() { context.getSharedPreferences("pusula_guide_progress", 0).edit().clear().commit() }
    private fun ready() { compose.waitUntil(15000) { compose.onAllNodesWithTag("guide.quick-start").fetchSemanticsNodes().isNotEmpty() } }
    private fun open(role: String = "COMPANY_ADMIN") {
        val token = "header." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"fixture-guide\"}".toByteArray()) + ".sig"
        compose.setContent { MaterialTheme { ApplicationGuideEntry(SessionState(isAuthenticated = true, token = token, role = role, companyId = 1)) } }
        compose.onNodeWithTag("account.guide.open").performClick()
        ready()
    }
    @Test fun actualEntryNextBackResumeAndFinish() {
        open()
        compose.onNodeWithTag("pusula.brand").assertExists()
        compose.onNodeWithTag("guide.quick-start").performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("1 / 6")
        compose.onNodeWithTag("guide.next").performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("2 / 6")
        compose.onNodeWithTag("guide.back").performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("1 / 6")
        compose.onNodeWithTag("guide.next").performClick()
        compose.onNodeWithTag("guide.close").performClick()
        compose.onNodeWithTag("guide.quick-start").performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("2 / 6")
        repeat(5) { compose.onNodeWithTag("guide.next").performClick() }
        compose.onNodeWithTag("guide.progress").assertTextContains("1 /", substring = true)
    }
    @Test fun technicianSearchFiltersAdminAndAppleOnlyTopics() {
        open("TECHNICIAN")
        compose.onNodeWithTag("guide.search").performTextInput("firma ayar")
        compose.onNodeWithTag("guide.topic.company-settings").assertDoesNotExist()
        compose.onNodeWithTag("guide.search").performTextReplacement("iskonto")
        compose.onNodeWithTag("guide.topic.discount").performScrollTo().performClick()
        compose.onNodeWithTag("guide.next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextContains("2 /", substring = true)
    }
    @Test fun persistenceIsScopedAndDeletionRemovesOnlySelectedAccount() {
        val repository = GuideProgressRepository(context)
        val topic = context.assets.open("app-guide.json").bufferedReader().use { GuideCatalog.decode(it.readText()).topics.first() }
        repository.save("a", "COMPANY_ADMIN", topic, 2, completed = true)
        repository.save("b", "COMPANY_ADMIN", topic, 1)
        assertTrue(repository.read("a", "COMPANY_ADMIN", topic).complete(topic))
        assertEquals(0, repository.read("a", "TECHNICIAN", topic).step)
        assertEquals(1, repository.read("b", "COMPANY_ADMIN", topic).step)
        repository.clear("a")
        assertFalse(repository.read("a", "COMPANY_ADMIN", topic).complete(topic))
        assertEquals(1, repository.read("b", "COMPANY_ADMIN", topic).step)
    }
}
