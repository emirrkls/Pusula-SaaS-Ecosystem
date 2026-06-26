package com.pusula.service.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import kotlin.math.max

object ImageUploadHelper {
    private const val TAG = "ImageUpload"
    private const val MAX_DIMENSION = 1920
    private const val JPEG_QUALITY = 82
    private const val MAX_OUTPUT_BYTES = 900_000L

    fun prepareForUpload(context: Context, uri: Uri, sourceFile: File? = null): File? {
        val cacheDir = context.cacheDir
        if (sourceFile != null && sourceFile.exists() && sourceFile.length() > 0L) {
            encodeBitmapToUploadFile(decodeBitmap(context, Uri.fromFile(sourceFile), sourceFile), cacheDir)
                ?.let { return it }
        }
        encodeBitmapToUploadFile(decodeBitmap(context, uri, null), cacheDir)?.let { return it }
        return copyUriBytes(context, uri, cacheDir)
    }

    private fun decodeBitmap(context: Context, uri: Uri, sourceFile: File?): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context, uri)?.let { return it }
        }
        return decodeWithBitmapFactory(context, uri, sourceFile)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            val source = if (uri.scheme == "file") {
                ImageDecoder.createSource(File(requireNotNull(uri.path)))
            } else {
                ImageDecoder.createSource(context.contentResolver, uri)
            }
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = max(info.size.width, info.size.height).toFloat()
                if (largest > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION / largest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        }.onFailure { Log.w(TAG, "ImageDecoder failed for $uri", it) }.getOrNull()
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri, sourceFile: File?): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (sourceFile != null) {
                BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            } else {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "Invalid image bounds for $uri")
                return null
            }

            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = if (sourceFile != null) {
                BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            } else {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            } ?: return null

            scaleDownIfNeeded(decoded, MAX_DIMENSION)
        }.onFailure { Log.w(TAG, "BitmapFactory failed for $uri", it) }.getOrNull()
    }

    private fun encodeBitmapToUploadFile(bitmap: Bitmap?, cacheDir: File): File? {
        if (bitmap == null) return null
        return runCatching {
            var quality = JPEG_QUALITY
            var output = writeJpeg(bitmap, cacheDir, quality)
            while (output.length() > MAX_OUTPUT_BYTES && quality > 40) {
                quality -= 10
                output.delete()
                output = writeJpeg(bitmap, cacheDir, quality)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            if (output.length() == 0L) {
                output.delete()
                null
            } else {
                output
            }
        }.onFailure {
            if (!bitmap.isRecycled) bitmap.recycle()
            Log.w(TAG, "JPEG encode failed", it)
        }.getOrNull()
    }

    private fun copyUriBytes(context: Context, uri: Uri, cacheDir: File): File? {
        return runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val temp = File.createTempFile("ticket_photo_raw_", ".jpg", cacheDir)
            input.use { inStream ->
                temp.outputStream().use { out -> inStream.copyTo(out) }
            }
            if (temp.length() == 0L) {
                temp.delete()
                return null
            }
            if (temp.length() <= MAX_OUTPUT_BYTES) {
                temp
            } else {
                val retry = encodeBitmapToUploadFile(
                    decodeBitmap(context, Uri.fromFile(temp), temp),
                    cacheDir
                )
                temp.delete()
                retry
            }
        }.onFailure { Log.w(TAG, "Raw copy failed for $uri", it) }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val largest = max(width, height)
        while (largest / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val largest = max(width, height)
        if (largest <= maxDimension) return source

        val scale = maxDimension.toFloat() / largest
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        if (scaled != source && !source.isRecycled) {
            source.recycle()
        }
        return scaled
    }

    private fun writeJpeg(bitmap: Bitmap, cacheDir: File, quality: Int): File {
        val file = File.createTempFile("ticket_photo_", ".jpg", cacheDir)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return file
    }
}
