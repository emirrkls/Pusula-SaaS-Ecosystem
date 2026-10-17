package com.pusula.service

import com.pusula.service.ui.technician.ServicePhotoUploadBatch
import org.junit.Assert.*
import org.junit.Test

class ServicePhotoUploadBatchTest {
    @Test fun boundedSelectionAndCancellation() {
        assertTrue(ServicePhotoUploadBatch.create<Int, String>(emptyList(), "BEFORE", "").pendingIds.isEmpty())
        val batch = ServicePhotoUploadBatch.create<Int, String>((0..20).toList(), "AFTER", "Frozen")
        assertEquals(10, batch.items.size)
        assertEquals(10, batch.pendingIds.toSet().size)
    }
    @Test fun partialFailureOnlyReplaysUnconfirmedFrozenPayloadAndKey() {
        var batch = ServicePhotoUploadBatch.create<String, String>(listOf("a", "b", "c"), "AFTER", "Frozen")
        val ids = batch.pendingIds
        batch = batch.prepare(ids[0], "bytes-a").succeed(ids[0])
            .prepare(ids[1], "bytes-b").fail(ids[1], "Lost response")
            .prepare(ids[2], "bytes-c").succeed(ids[2])
            .prepare(ids[1], "must-not-replace-bytes")
        assertEquals(listOf(ids[1]), batch.pendingIds)
        assertEquals("bytes-b", batch.items[1].payload)
        assertEquals("AFTER", batch.type); assertEquals("Frozen", batch.note)
        assertEquals(2, batch.confirmedCount); assertNull(batch.items[0].payload)
        batch = batch.succeed(ids[1]).fail(ids[1], "Stale failure")
        assertTrue(batch.pendingIds.isEmpty()); assertNull(batch.items[1].error)
    }
    @Test fun unreadableSourceRetainedUntilPreparationAndNewBatchHasNewKeys() {
        var batch = ServicePhotoUploadBatch.create<String, String>(listOf("source"), "BEFORE", "")
        val id = batch.pendingIds.single()
        batch = batch.fail(id, "Cannot read")
        assertEquals("source", batch.items.single().source)
        batch = batch.prepare(id, "bytes")
        assertNull(batch.items.single().source)
        val other = ServicePhotoUploadBatch.create<String, String>(listOf("source"), "BEFORE", "")
        assertNotEquals(id, other.pendingIds.single())
    }
}
