package com.pusula.service.guide

import com.pusula.service.core.GuideReadOnlyGate
import com.pusula.service.core.GuideReadOnlyInterceptor
import com.pusula.service.data.model.guide.GuideCatalog
import com.pusula.service.ui.support.supportDisplayDate
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class GuideSafetyTest {
    @Test fun mutationsAreBlockedAndIndependentLeasesRestoreNormalUse() {
        val first=GuideReadOnlyGate.begin();val second=GuideReadOnlyGate.begin()
        try {
            for(method in listOf("POST","PUT","PATCH","DELETE","post","UNKNOWN"))assertFalse(method,GuideReadOnlyGate.allows(method))
            assertTrue(GuideReadOnlyGate.allows("GET"));assertTrue(GuideReadOnlyGate.allows("HEAD"))
            GuideReadOnlyGate.end(first);GuideReadOnlyGate.end(first)
            assertFalse(GuideReadOnlyGate.allows("POST"))
        } finally {GuideReadOnlyGate.end(first);GuideReadOnlyGate.end(second)}
        assertTrue(GuideReadOnlyGate.allows("POST"))
    }
    @Test fun realTransportDoesNotSendWritesDuringTourButWorksAfterExit() {
        val server=ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));server.soTimeout=3000
        val received=AtomicInteger()
        val worker=thread {
            repeat(2) { server.accept().use { socket ->
                val input=socket.getInputStream().bufferedReader()
                while(!input.readLine().isNullOrEmpty()) { /* Headers only, no real data. */ }
                received.incrementAndGet()
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
            }}
        }
        val client=OkHttpClient.Builder().addInterceptor(GuideReadOnlyInterceptor()).addNetworkInterceptor(GuideReadOnlyInterceptor()).build()
        val url="http://127.0.0.1:${server.localPort}/offline-test"
        val lease=GuideReadOnlyGate.begin()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use {assertEquals(200,it.code)}
            try {client.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()).execute().close();fail("Write escaped tour gate")} catch(expected:java.io.IOException) {assertTrue(expected.message.orEmpty().contains("Salt okunur"))}
            assertEquals(1,received.get())
        } finally {GuideReadOnlyGate.end(lease)}
        try {client.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()).execute().use{assertEquals(200,it.code)};worker.join(3000);assertEquals(2,received.get())}
        finally {server.close();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
    }
    @Test fun everyStepHasTypedRealScreenAndContextPolicy() {
        val source=java.io.File("src/main/assets/app-guide.json").readText()
        val catalog=GuideCatalog.decode(source)
        assertEquals(156,catalog.topics.sumOf{it.steps.size})
        catalog.topics.flatMap{it.steps}.forEach{assertTrue(it.tour.screen in com.pusula.service.data.model.guide.GuideTourRoute.screens);assertTrue(it.tour.target.isNotBlank())}
        val route=com.pusula.service.data.model.guide.GuideTourRoute("customerContact","customer-contact")
        assertTrue(route.needsTicket("TECHNICIAN"));assertFalse(route.needsCustomer("TECHNICIAN"));assertTrue(route.needsCustomer("COMPANY_ADMIN"))
        try {GuideCatalog.decode(source.replaceFirst("\"screen\": \"home\"","\"screen\": \"deleteAll\""));fail("Invalid screen accepted")}catch(expected:IllegalArgumentException) { }
    }
    @Test fun datesAreReadableAcrossOffsetsAndInvalidDataIsPreserved() {
        val expected=supportDisplayDate("2026-10-17T16:20:00Z")
        assertEquals(expected,supportDisplayDate("2026-10-17T19:20:00+03:00"))
        assertEquals(expected,supportDisplayDate("2026-10-17T19:20:00.000000"))
        assertNotEquals("2026-10-17T16:20:00Z",expected)
        assertEquals("invalid",supportDisplayDate("invalid"))
    }
}
