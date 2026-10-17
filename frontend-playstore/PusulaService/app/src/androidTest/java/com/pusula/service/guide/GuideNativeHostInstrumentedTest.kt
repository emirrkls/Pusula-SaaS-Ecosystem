package com.pusula.service.guide

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.pusula.service.MainActivity
import com.pusula.service.core.GuideReadOnlyGate
import com.pusula.service.data.model.guide.GuideCatalog
import com.pusula.service.ui.guide.GuideTourHost
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Actual Hilt/native screen integration. All application HTTP is denied before
 * launching the activity; no session, fake tenant or successful GET is fabricated. */
class GuideNativeHostInstrumentedTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var scenario:ActivityScenario<MainActivity>
    private lateinit var lease:UUID
    @Before fun prepare() {
        lease=GuideReadOnlyGate.begin(allowReads=false)
        scenario=ActivityScenario.launch(MainActivity::class.java)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val topic=context.assets.open("app-guide.json").bufferedReader().use{GuideCatalog.decode(it.readText()).topics.first{it.id=="quick-start-admin"}}
        scenario.onActivity { activity -> activity.setContent {
            var index by remember{mutableIntStateOf(0)}
            var open by remember{mutableStateOf(true)}
            MaterialTheme {
                if(open)GuideTourHost(topic,index,mapOf("FINANCE_MODULE" to true),"COMPANY_ADMIN",{index++},{index--},{open=false})
                else androidx.compose.material3.Text("Tur kapandı")
            }
        }}
    }
    @After fun cleanup(){if(::scenario.isInitialized)scenario.close();if(::lease.isInitialized)GuideReadOnlyGate.end(lease)}
    @Test fun actualScreensMountWithIsolatedHiltStateAndNeverAutoSelectARecord() {
        compose.waitUntil(15000){compose.onAllNodesWithTag("guide.real.screen.home").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("guide.real.screen.home").assertIsDisplayed()
        compose.onNodeWithTag("guide.next").performClick()
        compose.onNodeWithTag("guide.real.screen.customers").assertIsDisplayed()
        compose.onNodeWithTag("guide.next").performClick()
        compose.onNodeWithTag("guide.real.screen.team").assertIsDisplayed()
        compose.onNodeWithTag("guide.next").performClick()
        compose.onNodeWithTag("guide.next").assertIsNotEnabled()
        compose.onNodeWithText("Kayıt seçin · yalnızca görüntülenecek").assertExists()
        assertTrue(compose.onAllNodesWithTag("guide.real.screen.ticketDetail").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("guide.close").performClick()
        compose.onNodeWithText("Tur kapandı").assertExists()
        assertFalse(GuideReadOnlyGate.allows("POST")) // Outer offline lease still owns the barrier.
    }
}
