package com.kitaplik.app.domain.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Android'in yerleşik PdfRenderer API'sini kullanarak PDF sayfalarını
 * yüksek kalitede ve LruCache önbelleklemesiyle görüntüler.
 */
class PdfPageLoader(private val file: File) : Closeable {

    private val fileDescriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val pdfRenderer: PdfRenderer = PdfRenderer(fileDescriptor)
    private val renderMutex = Mutex()

    val pageCount: Int
        get() = pdfRenderer.pageCount

    private val pageCache = object : LruCache<Int, Bitmap>(6) {
        override fun entryRemoved(
            evicted: Boolean,
            key: Int?,
            oldValue: Bitmap?,
            newValue: Bitmap?
        ) {
            // Compose Image tarafından hala çiziliyor olabileceği için GC'ye bırakılır
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int = 1440): Bitmap? =
        withContext(Dispatchers.IO) {
            if (pageIndex !in 0 until pageCount) return@withContext null

            pageCache.get(pageIndex)?.let { cached ->
                if (!cached.isRecycled) return@withContext cached
            }

            renderMutex.withLock {
                pageCache.get(pageIndex)?.let { cached ->
                    if (!cached.isRecycled) return@withLock cached
                }

                runCatching {
                    pdfRenderer.openPage(pageIndex).use { page ->
                        val scale = (targetWidthPx.toFloat() / page.width.coerceAtLeast(1))
                            .coerceIn(1.0f, 2.5f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)

                        val matrix = Matrix().apply {
                            postScale(scale, scale)
                        }
                        page.render(
                            bitmap,
                            null,
                            matrix,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        pageCache.put(pageIndex, bitmap)
                        bitmap
                    }
                }.getOrNull()
            }
        }

    override fun close() {
        runCatching {
            pageCache.evictAll()
            pdfRenderer.close()
            fileDescriptor.close()
        }
    }
}
