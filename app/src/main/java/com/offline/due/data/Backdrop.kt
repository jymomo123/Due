package com.offline.due.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object Backdrop {

    private const val PREFIX = "backdrop_"

    private const val MAX_WIDTH = 1440

    private const val QUALITY = 88

    fun fileOf(context: Context, name: String): File = File(context.filesDir, name)

    fun import(context: Context, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_WIDTH)
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

        val name = "$PREFIX${System.currentTimeMillis()}.jpg"
        val ok = runCatching {
            FileOutputStream(fileOf(context, name)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
        }.isSuccess

        if (!ok) {
            fileOf(context, name).delete()
            return null
        }

// 新图已经落盘，这时才动旧图
        SettingsStore.state.value.bgImage?.let { old ->
            if (old != name) fileOf(context, old).delete()
        }
        return name
    }

    fun load(context: Context, name: String, maxWidthPx: Int): ImageBitmap? {
        val f = fileOf(context, name)
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidthPx)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
        }.getOrNull()
    }

    fun remove(context: Context) {
        SettingsStore.state.value.bgImage?.let { fileOf(context, it).delete() }
        SettingsStore.setBgImage(null)
// 图没了，样式还停在"图片"上会只剩一层遮罩。退回冰川。
        if (SettingsStore.state.value.bgStyle == BgStyle.IMAGE) {
            SettingsStore.setBgStyle(BgStyle.ICE)
        }
    }

    internal fun sampleSize(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
// 用长边判断：横图缩到 target 宽，竖图缩到 target 高，观感一致
        val long = maxOf(width, height)
        while (long / (sample * 2) >= target) sample *= 2
        return sample
    }
}

