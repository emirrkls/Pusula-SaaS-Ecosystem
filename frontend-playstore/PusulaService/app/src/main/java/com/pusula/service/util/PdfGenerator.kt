package com.pusula.service.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.pusula.service.data.model.FieldTicketDTO
import com.pusula.service.data.model.UsedPartDTO
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {
    fun generateAndShare(
        context: Context,
        ticket: FieldTicketDTO,
        parts: List<UsedPartDTO>,
        technicianName: String,
        signatureBitmap: Bitmap? = null
    ) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val textPaint = Paint().apply { color = Color.BLACK; textSize = 12f }
        val headerPaint = Paint().apply { color = Color.parseColor("#1565C0"); textSize = 18f; isFakeBoldText = true }
        val sectionPaint = Paint().apply { color = Color.parseColor("#1565C0"); textSize = 14f; isFakeBoldText = true }

        var y = 40f
        canvas.drawText("Pusula Servis", 40f, y, headerPaint)
        y += 24f
        canvas.drawText("SERVIS FORMU", 40f, y, sectionPaint)
        canvas.drawText("Fis No: #${ticket.id}", 420f, y, textPaint)
        y += 28f

        canvas.drawText("Musteri Bilgileri", 40f, y, sectionPaint); y += 20f
        canvas.drawText("Ad: ${ticket.customerName.orEmpty()}", 40f, y, textPaint); y += 16f
        canvas.drawText("Telefon: ${ticket.customerPhone.orEmpty()}", 40f, y, textPaint); y += 16f
        canvas.drawText("Adres: ${ticket.customerAddress.orEmpty()}", 40f, y, textPaint); y += 24f

        canvas.drawText("Servis Aciklamasi", 40f, y, sectionPaint); y += 20f
        canvas.drawText(ticket.description.orEmpty(), 40f, y, textPaint); y += 28f

        val tableHeaderPaint = Paint().apply { color = Color.parseColor("#1976D2") }
        canvas.drawRect(40f, y, 555f, y + 20f, tableHeaderPaint)
        textPaint.color = Color.WHITE
        canvas.drawText("Parca", 46f, y + 14f, textPaint)
        canvas.drawText("Adet", 320f, y + 14f, textPaint)
        canvas.drawText("Fiyat", 420f, y + 14f, textPaint)
        textPaint.color = Color.BLACK
        y += 24f

        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                val zebra = Paint().apply { color = Color.parseColor("#F1F5FB") }
                canvas.drawRect(40f, y - 14f, 555f, y + 6f, zebra)
            }
            canvas.drawText(part.partName, 46f, y, textPaint)
            canvas.drawText(part.quantityUsed.toString(), 320f, y, textPaint)
            canvas.drawText("₺${part.sellingPriceSnapshot}", 420f, y, textPaint)
            y += 20f
        }
        y += 20f

        val total = parts.sumOf { it.sellingPriceSnapshot * it.quantityUsed }
        val paid = ticket.collectedAmount ?: 0.0
        val remain = (total - paid).coerceAtLeast(0.0)
        canvas.drawText("Odeme Bilgileri", 40f, y, sectionPaint); y += 18f
        canvas.drawText("Toplam: ₺$total", 40f, y, textPaint); y += 16f
        canvas.drawText("Tahsil Edilen: ₺$paid", 40f, y, textPaint); y += 16f
        canvas.drawText("Yontem: ${ticket.paymentMethod.orEmpty()}", 40f, y, textPaint); y += 16f
        val debtPaint = Paint().apply { color = Color.RED; textSize = 12f; isFakeBoldText = true }
        canvas.drawText("Kalan Borc: ₺$remain", 40f, y, debtPaint); y += 24f

        canvas.drawText("Teknisyen: $technicianName", 40f, y, textPaint); y += 20f
        if (signatureBitmap != null) {
            canvas.drawText("Musteri Imzasi:", 40f, y, textPaint)
            canvas.drawBitmap(signatureBitmap, null, android.graphics.Rect(140, y.toInt() - 30, 340, y.toInt() + 40), null)
            y += 60f
        }

        canvas.drawText("Pusula Servis Yonetim Sistemi", 180f, 820f, textPaint)
        document.finishPage(page)

        val outDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(outDir, "servis_formu_${ticket.id}.pdf")
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "PDF Paylaş"))
    }
}
