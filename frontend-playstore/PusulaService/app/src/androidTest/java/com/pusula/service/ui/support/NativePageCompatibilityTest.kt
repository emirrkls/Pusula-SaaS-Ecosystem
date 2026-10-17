package com.pusula.service.ui.support

import android.graphics.Bitmap
import android.graphics.Color
import android.system.Os
import android.system.OsConstants
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual packaged JNI libraries and synthetic pixels only; no API, camera or user data. */
class NativePageCompatibilityTest {
    @Test fun packagedNativeLibrariesLoadOnTheCurrentPageSize() {
        assertTrue(Os.sysconf(OsConstants._SC_PAGESIZE) in setOf(4096L,16384L))
        for (library in listOf("androidx.graphics.path","barhopper_v3","image_processing_util_jni","surface_util_jni")) {
            System.loadLibrary(library)
        }
    }
    @Test fun barcodeNativeEngineProcessesSyntheticPixels() {
        val bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val scanner=BarcodeScanning.getClient()
        val done=CountDownLatch(1)
        val failure=AtomicReference<Exception?>()
        scanner.process(InputImage.fromBitmap(bitmap,0))
            .addOnSuccessListener { done.countDown() }
            .addOnFailureListener { failure.set(it);done.countDown() }
        try { assertTrue("Native barcode operation timed out",done.await(30,TimeUnit.SECONDS));assertNull(failure.get()) }
        finally {scanner.close();bitmap.recycle()}
    }
}
