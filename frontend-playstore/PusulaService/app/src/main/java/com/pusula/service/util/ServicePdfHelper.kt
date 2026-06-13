package com.pusula.service.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ServicePdfHelper {
    fun saveAndShare(context: Context, ticketId: Long, data: ByteArray) {
        val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(outDir, "servis_raporu_$ticketId.pdf")
        FileOutputStream(outFile).use { it.write(data) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Servis raporu paylaş"))
    }
}
