package com.pusula.service.ui.support

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.AuthResponse
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

/** Real Compose screen, ViewModel, protected storage and session guards; no real API/login. */
class SupportFlowInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session:SessionManager
    private lateinit var repository:SupportRepository
    private lateinit var vm:SupportViewModel
    private lateinit var api:OfflineApi
    private val tokenA="offline.eyJzdWIiOiJzdXBwb3J0LXFhLWEifQ.fixture"
    private val tokenB="offline.eyJzdWIiOiJzdXBwb3J0LXFhLWIifQ.fixture"
    @Before fun setup() {
        api=OfflineApi();session=SessionManager(context);repository=SupportRepository(context,api)
        runBlocking {repository.clear(repository.accountKey(tokenA,0));repository.clear(repository.accountKey(tokenB,0))}
        InstrumentationRegistry.getInstrumentation().runOnMainSync {session.configure(auth(tokenA));vm=SupportViewModel(repository,session)}
        compose.setContent {MaterialTheme {SupportCenterScreen({},vm)}}
        compose.waitUntil(10000){vm.state.value.contact.enabled&&!vm.state.value.busy}
    }
    @After fun cleanup() {InstrumentationRegistry.getInstrumentation().runOnMainSync {vm.viewModelScope.cancel();session.logout()}}
    private fun auth(token:String)=AuthResponse(token=token,role="TECHNICIAN",companyId=0,fullName="Synthetic QA",planType="CIRAK")
    private fun composeRequest() {
        compose.onNodeWithText("Yeni talep").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Konu")).performScrollTo().performTextInput("Synthetic QA")
        compose.onNode(hasSetTextAction() and hasText("Açıklama")).performScrollTo().performTextInput("Private synthetic support body")
    }
    @Test fun lostResponseReusesTheFrozenKeyAndDoesNotDuplicateTheWrite() {
        api.loseFirstResponse=true;composeRequest();compose.onNodeWithText("Gönder").performScrollTo().performClick()
        compose.waitUntil(10000){vm.state.value.error!=null&&!vm.state.value.busy}
        assertTrue(vm.state.value.draft.frozen)
        compose.onNodeWithText("Aynı gönderimi tekrar dene").performScrollTo().performClick()
        compose.waitUntil(10000){vm.state.value.success!=null}
        assertEquals(2,api.keys.size);assertEquals(api.keys[0],api.keys[1]);assertEquals(1,api.writes.size)
    }
    @Test fun subjectOnlyDraftIsEncryptedAndRestoredWithTheSameIdentity() {
        compose.onNodeWithText("Yeni talep").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Konu")).performScrollTo().performTextInput("Private subject-only QA")
        var restored:SupportDraft?=null
        compose.waitUntil(10000){restored=runBlocking {repository.load(repository.accountKey(tokenA,0))};restored?.subject=="Private subject-only QA"}
        assertEquals(vm.state.value.draft.clientKey,restored!!.clientKey);assertTrue(restored!!.hasContent)
        val file=java.io.File(context.applicationInfo.dataDir,"shared_prefs/pusula_support_drafts.xml")
        assertFalse(file.readText().contains("Private subject-only QA"))
    }
    @Test fun switchingAccountsDiscardsTheOldResponseWithoutSendingWithTheNewToken() {
        api.gate=CompletableDeferred();composeRequest();compose.onNodeWithText("Gönder").performScrollTo().performClick()
        compose.waitUntil(10000){api.keys.isNotEmpty()}
        InstrumentationRegistry.getInstrumentation().runOnMainSync {session.configure(auth(tokenB))}
        compose.waitUntil(10000){vm.state.value.accountPartition==repository.accountKey(tokenB,0)}
        api.gate!!.complete(Unit)
        compose.waitForIdle();assertNull(vm.state.value.detail);assertNull(vm.state.value.success);assertEquals("",vm.state.value.draft.body)
        assertEquals("Bearer $tokenA",api.lastAuthorization)
        assertTrue(runBlocking {repository.load(repository.accountKey(tokenA,0))}!!.frozen)
    }
    private class OfflineApi:SupportApi {
        var loseFirstResponse=false;var gate:CompletableDeferred<Unit>?=null
        val keys=java.util.concurrent.CopyOnWriteArrayList<String>();val writes=java.util.concurrent.ConcurrentHashMap<String,Boolean>();@Volatile var lastAuthorization=""
        override suspend fun contact()=SupportContact(enabled=true)
        override suspend fun unread(token:String)=mapOf("count" to 0L)
        override suspend fun list(token:String,page:Int)=SupportPage(emptyList(),page,false,0)
        override suspend fun detail(token:String,id:Long,before:Long?)=detailValue()
        override suspend fun read(token:String,id:Long) {}
        override suspend fun attachment(token:String,id:Long):okhttp3.ResponseBody=error("No fixture attachments")
        override suspend fun create(token:String,body:Map<String,Any>):SupportDetail {lastAuthorization=token;val key=body.getValue("clientKey").toString();keys+=key;writes[key]=true;gate?.await();if(loseFirstResponse&&keys.size==1)throw IOException("Synthetic lost response");return detailValue()}
        override suspend fun reply(token:String,id:Long,body:Map<String,Any>)=create(token,body)
        private fun detailValue()=SupportDetail(SupportTicket(1,"DST-000001","Synthetic QA","BUG","IN_PROGRESS",false,"2026-10-17T12:00:00Z"),emptyList(),null)
    }
}
