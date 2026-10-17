package com.pusula.service.guide

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val screen = compose.onNodeWithTag("guide.screen").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("guide.next").fetchSemanticsNode().boundsInRoot
        assertTrue("Continue must remain inside the visible guide", next.top >= screen.top && next.bottom <= screen.bottom && next.left >= screen.left && next.right <= screen.right)
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun open(role: String = "COMPANY_ADMIN", large: Boolean = false) {
        val token = "header." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"fixture-guide\"}".toByteArray()) + ".sig"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else density.fontScale)) {
                com.pusula.service.ui.theme.PusulaTheme {
                    // Shared production chrome/progress/gate; offline backdrop is
                    // explicitly injected. This test never creates a live session.
                    ApplicationGuideEntry(SessionState(isAuthenticated = true, token = token, role = role, companyId = 1),tourRenderer={t,i,_,_,n,b,c->
                        GuideTourChrome(t,i,false,n,b,c){padding->androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {androidx.compose.material3.Text(t.steps[i].tour.screen)}}
                    })
                }
            }
        }
        compose.onNodeWithTag("account.guide.open").performClick()
        ready()
    }
    @Test fun actualEntryNextBackResumeAndFinish() {
        open()
        compose.onNodeWithTag("pusula.brand").assertExists()
        compose.onNodeWithTag("guide.quick-start").performClick()
        compose.runOnIdle {assertTrue(com.pusula.service.core.GuideReadOnlyGate.active.value)}
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("1 / 6")
        compose.onNodeWithTag("guide.next").performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("2 / 6")
        compose.onNodeWithTag("guide.back").performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("1 / 6")
        compose.onNodeWithTag("guide.next").performClick()
        compose.onNodeWithTag("guide.close").performClick()
        compose.runOnIdle {assertFalse(com.pusula.service.core.GuideReadOnlyGate.active.value)}
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
        compose.onNodeWithTag("guide.list").performScrollToNode(hasTestTag("guide.topic.discount"))
        compose.onNodeWithTag("guide.topic.discount").performClick()
        screenshot("guide-search-lesson.png")
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
    @Test fun largeTextKeepsContinueVisibleAndUsesCanonicalBrand() {
        open(large = true)
        compose.onNodeWithTag("pusula.brand").assertExists()
        compose.onNodeWithTag("guide.list").performScrollToNode(hasTestTag("guide.quick-start"))
        compose.onNodeWithTag("guide.quick-start").performClick()
        screenshot("guide-large-text.png")
        compose.onNodeWithTag("guide.next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("guide.step.progress").assertTextEquals("2 / 6")
        compose.onNodeWithTag("guide.back").assertIsDisplayed()
        screenshot("guide-large-text-next.png")
    }
}
