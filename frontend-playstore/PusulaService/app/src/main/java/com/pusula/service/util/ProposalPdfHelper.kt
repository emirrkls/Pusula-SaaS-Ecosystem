package com.pusula.service.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ProposalPdfHelper {
    fun saveAndShare(context: Context, proposalId: Long, data: ByteArray) {
        val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(outDir, "teklif_$proposalId.pdf")
        FileOutputStream(outFile).use { it.write(data) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Teklif PDF Paylaş"))
    }
}
