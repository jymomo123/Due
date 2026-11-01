package com.offline.due.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object CardImage {

    private const val PREFIX = "card_"

    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.asAndroidBitmap().byteCount
    }

    private fun cacheKey(name: String, maxWidthPx: Int) = "$name@$maxWidthPx"

    private fun dropCache(name: String) {
        cache.snapshot().keys
            .filter { it.startsWith("$name@") }
            .forEach { cache.remove(it) }
    }

    private const val MAX_WIDTH = 1200

    private const val QUALITY = 86

    private fun prefixOf(eventId: String) = "$PREFIX${eventId}_"

    fun fileOf(context: Context, name: String): File = File(context.filesDir, name)

    fun import(context: Context, eventId: String, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = Backdrop.sampleSize(bounds.outWidth, bounds.outHeight, MAX_WIDTH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val src = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull() ?: return null

        val scaled = if (src.width > MAX_WIDTH) {
            val h = (src.height.toFloat() * MAX_WIDTH / src.width).roundToInt().coerceAtLeast(1)
            runCatching { Bitmap.createScaledBitmap(src, MAX_WIDTH, h, true) }.getOrDefault(src)
        } else {
            src
        }

        val name = "${prefixOf(eventId)}${System.currentTimeMillis()}.jpg"
        val ok = runCatching {
            FileOutputStream(fileOf(context, name)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
        }.isSuccess

        if (!ok) {
            fileOf(context, name).delete()
            return null
        }
        return name
    }

    fun load(context: Context, name: String, maxWidthPx: Int): ImageBitmap? {
        val key = cacheKey(name, maxWidthPx)
        cache.get(key)?.let { return it }
        val f = fileOf(context, name)
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = Backdrop.sampleSize(bounds.outWidth, bounds.outHeight, maxWidthPx)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
                ?.also { cache.put(key, it) }
        }.getOrNull()
    }

    fun remove(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        dropCache(name)
        fileOf(context, name).delete()
    }

    fun removeAllOf(context: Context, eventId: String) {
        val prefix = prefixOf(eventId)
        runCatching {
            context.filesDir.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefix)) {
                    dropCache(f.name)
                    f.delete()
                }
            }
        }
    }
}

