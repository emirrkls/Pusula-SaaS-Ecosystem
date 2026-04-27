package com.pusula.service.util

import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FinancePdfHelper {
    fun saveAndShare(context: Context, month: String, data: ByteArray) {
        val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(outDir, "finans_raporu_$month.pdf")
        FileOutputStream(outFile).use { it.write(data) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Finans PDF Paylaş"))
    }

    fun saveToDownloads(context: Context, month: String, data: ByteArray): Result<String> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloadsMediaStore(context, month, data)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val outFile = File(downloadsDir, "finans_raporu_$month.pdf")
                FileOutputStream(outFile).use { it.write(data) }
                outFile.absolutePath
            }
        }
    }

    private fun saveToDownloadsMediaStore(context: Context, month: String, data: ByteArray): String {
        val fileName = "finans_raporu_$month.pdf"
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri: Uri = resolver.insert(collection, values)
            ?: error("Downloads kaydı oluşturulamadı")
        resolver.openOutputStream(itemUri)?.use { output ->
            output.write(data)
        } ?: error("Downloads dosyası açılamadı")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        return "Downloads/$fileName"
    }
}
