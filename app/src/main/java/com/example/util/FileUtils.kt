package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object FileUtils {

    suspend fun uriToBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                return@withContext renderFirstPdfPage(context, uri)
            }

            // First decode bounds to compute sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val maxDimension = 1600
            var sampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val heightRatio = Math.round(options.outHeight.toFloat() / maxDimension.toFloat())
                val widthRatio = Math.round(options.outWidth.toFloat() / maxDimension.toFloat())
                sampleSize = max(heightRatio, widthRatio)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = max(1, sampleSize)
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun renderFirstPdfPage(context: Context, uri: Uri): Bitmap? {
        var fileDescriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(fileDescriptor)
            if (renderer.pageCount > 0) {
                page = renderer.openPage(0)
                val targetWidth = min(page.width * 2, 1800).coerceAtLeast(1000)
                val targetHeight = min(page.height * 2, 2400).coerceAtLeast(1200)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        } finally {
            try {
                page?.close()
            } catch (ignored: Throwable) {}
            try {
                renderer?.close()
            } catch (ignored: Throwable) {}
            try {
                fileDescriptor?.close()
            } catch (ignored: Throwable) {}
        }
    }
}
