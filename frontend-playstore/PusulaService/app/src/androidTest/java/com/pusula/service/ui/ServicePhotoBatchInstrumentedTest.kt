package com.pusula.service.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.pusula.service.core.SessionManager
import com.pusula.service.data.model.AuthResponse
import com.pusula.service.data.model.ServicePhotoDTO
import com.pusula.service.data.remote.ApiService
import com.pusula.service.data.repository.TicketRepository
import com.pusula.service.ui.technician.*
import com.pusula.service.usecase.technician.OperationTicketUseCase
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.cancel
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.*
import org.junit.Assert.*

/** Production ViewModel + Repository + native feedback; only the API boundary
 * is synthetic. Never configures a live account or calls the production host. */
class ServicePhotoBatchInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session: SessionManager
    private lateinit var vm: TicketViewModel
    private val calls = CopyOnWriteArrayList<Attempt>()
    private val writes = linkedMapOf<String, ServicePhotoDTO>()
    private val prepared = CopyOnWriteArrayList<File>()
    private var loseSecond = false
    private var holdFirst = false
    private var held: Continuation<ServicePhotoDTO>? = null
    private var heldValue: ServicePhotoDTO? = null
    data class Attempt(val key: String, val token: String, val type: String, val note: String, val bytes: ByteArray)
    private val source = listOf("a", "b", "c").map { Uri.parse("content://offline-photo-fixture/$it") }
    private fun body(request: RequestBody) = Buffer().also { request.writeTo(it) }.readByteArray()
    @Before fun setup() {
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, args ->
            check(method.name == "uploadServicePhoto") { "Unexpected API method ${method.name}" }
            val key = args!![1] as String
            calls.add(Attempt(key, args[2] as String, String(body(args[3] as RequestBody)),
                String(body(args[4] as RequestBody)), body((args[5] as MultipartBody.Part).body)))
            val saved = writes.getOrPut(key) { ServicePhotoDTO(writes.size.toLong() + 1, 701, "file:///dev/null", "AFTER", "Frozen") }
            if (holdFirst && calls.size == 1) {
                @Suppress("UNCHECKED_CAST")
                held = args.last() as Continuation<ServicePhotoDTO>; heldValue = saved
                COROUTINE_SUSPENDED
            } else {
                if (loseSecond && calls.size == 2) throw IOException("Saved but response lost")
                saved
            }
        } as ApiService
        val repository = TicketRepository(api)
        session = SessionManager(context)
        main { session.configure(auth("a")); vm = TicketViewModel(repository, OperationTicketUseCase(repository), session) }
        compose.setContent {
            val state by vm.uiState.collectAsState()
            MaterialTheme { ServicePhotoBatchStatus(state.photoBatch, state.photoUploading) { vm.retryServicePhotos(701) } }
        }
    }
    private fun auth(name: String) = AuthResponse(token = "offline-photo-$name", role = "TECHNICIAN", companyId = 0, fullName = "Synthetic QA", planType = "CIRAK")
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun prepareFile(uri: Uri): File = File.createTempFile("batch_fixture_", ".jpg", context.cacheDir).also {
        it.writeBytes(requireNotNull(uri.lastPathSegment).toByteArray()); prepared.add(it)
    }
    private fun start(uris: List<Uri> = source, prepare: suspend (Uri) -> File? = { prepareFile(it) }) = main {
        vm.uploadServicePhotos(701, "AFTER", "Frozen", uris, prepare)
    }
    @After fun cleanup() {
        main { vm.viewModelScope.cancel(); vm.abandonServicePhotos(); session.logout() }
        prepared.forEach { it.delete() }
    }
    @Test fun partialFailureRetriesOnlyUnconfirmedWithTheSameBytesKeyAndAccount() {
        loseSecond = true; start()
        compose.waitUntil(10000) { !vm.uiState.value.photoUploading && vm.uiState.value.photoBatch.confirmed == 2 }
        compose.onNodeWithTag("photos.batch.retry").assertIsDisplayed().performClick()
        compose.waitUntil(10000) { !vm.uiState.value.photoUploading && vm.uiState.value.photoBatch.confirmed == 3 }
        assertEquals(4, calls.size); assertEquals(3, writes.size); assertEquals(3, prepared.size)
        assertEquals(calls[1].key, calls[3].key); assertArrayEquals(calls[1].bytes, calls[3].bytes)
        assertTrue(calls.all { it.token == "Bearer offline-photo-a" && it.type == "AFTER" && it.note == "Frozen" })
        assertEquals(3, vm.uiState.value.servicePhotos.map { it.id }.toSet().size)
        assertTrue(prepared.all { !it.exists() })
        compose.onNodeWithTag("photos.batch.retry").assertDoesNotExist()
    }
    @Test fun unreadablePhotoDoesNotPreventOtherPhotosAndDoubleSubmitDoesNothing() {
        var failures = 0
        start(prepare = { if (it == source[1] && failures++ == 0) null else prepareFile(it) })
        compose.waitUntil(10000) { !vm.uiState.value.photoUploading && vm.uiState.value.photoBatch.confirmed == 2 }
        start() // A new selection cannot replace an unfinished intent.
        assertEquals(2, calls.size)
        main { vm.retryServicePhotos(701) }
        compose.waitUntil(10000) { !vm.uiState.value.photoUploading && vm.uiState.value.photoBatch.confirmed == 3 }
        assertEquals(3, writes.size)
    }
    @Test fun switchingAccountCancelsRemainingPhotosAndRejectsTheOldResponse() {
        holdFirst = true; start()
        compose.waitUntil(10000) { held != null }
        main { session.configure(auth("b")) }
        compose.waitUntil(10000) { vm.uiState.value.photoBatch.total == 0 && !vm.uiState.value.photoUploading }
        main { held!!.resume(heldValue!!) }
        compose.waitForIdle()
        assertEquals(1, calls.size); assertTrue(vm.uiState.value.servicePhotos.isEmpty())
        assertTrue(prepared.all { !it.exists() })
        main { vm.retryServicePhotos(701) }
        compose.waitForIdle(); assertEquals(1, calls.size)
    }
}
