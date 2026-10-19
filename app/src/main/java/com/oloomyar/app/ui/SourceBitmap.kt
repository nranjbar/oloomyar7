package com.oloomyar.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.oloomyar.app.BuildConfig
import com.oloomyar.app.model.PdfSource
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One compressed book instead of hundreds of duplicated raster crops. No network access. */
internal object SourceBitmap {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val copyLock = Any()

    fun load(context: Context, asset: String, trim: Boolean = false): Bitmap? {
        val key = "$asset|$trim"
        cache.get(key)?.let { return it }
        val bitmap = runCatching {
            val pdf = PdfSource.parse(asset)
            val original = if (pdf == null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open("images/$asset").use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 4096) inSampleSize *= 2
                }
                context.assets.open("images/$asset").use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Image decode failed")
            } else render(context, pdf)
            if (trim) cropWorkbookAnswer(original) else original
        }.getOrNull()
        bitmap?.let { cache.put(key, it) }
        return bitmap
    }

    private fun render(context: Context, source: PdfSource): Bitmap {
        val local = File(context.cacheDir, "source-${BuildConfig.VERSION_CODE}-${source.file}")
        synchronized(copyLock) {
            if (!local.exists() || local.length() == 0L) {
                val temp = File(local.path + ".tmp")
                context.assets.open("source/${source.file}").use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(temp.renameTo(local))
            }
        }
        val sourceWidth = source.regions.maxOf { it.width }
        val sourceHeight = source.regions.sumOf { it.height.toDouble() }.toFloat() + 5f * (source.regions.size - 1)
        // Retain sharp small print without risking a huge bitmap on long, multipage questions.
        val scale = minOf(3.5f, 2000f / sourceWidth, sqrt(9_000_000f / (sourceWidth * sourceHeight)))
        val heights = source.regions.map { ceil(it.height * scale).toInt() }
        val gap = ceil(5f * scale).toInt()
        val result = Bitmap.createBitmap(ceil(sourceWidth * scale).toInt(), heights.sum() + gap * (heights.size - 1), Bitmap.Config.ARGB_8888)
        result.eraseColor(Color.WHITE)
        val canvas = Canvas(result)
        ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                var offsetY = 0
                for ((index, region) in source.regions.withIndex()) {
                    require(region.page <= renderer.pageCount)
                    renderer.openPage(region.page - 1).use { page ->
                        require(region.right <= page.width + 1 && region.bottom <= page.height + 1)
                        val piece = Bitmap.createBitmap(ceil(region.width * scale).toInt(), heights[index], Bitmap.Config.ARGB_8888)
                        piece.eraseColor(Color.WHITE)
                        val matrix = Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(-region.left * scale, -region.top * scale)
                        }
                        page.render(piece, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        canvas.drawBitmap(piece, ((result.width - piece.width) / 2f).roundToInt().toFloat(), offsetY.toFloat(), null)
                        offsetY += piece.height + gap
                        piece.recycle()
                    }
                }
            }
        }
        return result
    }
}
