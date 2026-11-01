package com.offline.due.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GallerySaver {

    private const val FOLDER = "如期"

    suspend fun save(context: Context, bitmap: Bitmap, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveModern(context, bitmap, name)
                } else {
                    saveLegacy(context, bitmap, name)
                }
            }.getOrDefault(false)
        }

    private fun saveModern(context: Context, bitmap: Bitmap, name: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + FOLDER,
            )
// 写之前先标记为"正在写"，相册会跳过它。
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        val wrote = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        if (!wrote) {
// 没写成要把这条空记录删掉，否则相册里会留一个 0 字节的坏图。
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap, name: String): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            FOLDER,
        )
        if (!dir.exists() && !dir.mkdirs()) return false
        val file = File(dir, name)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
// API 28 及以下：写进公共目录后必须主动通知相册，否则要等下次媒体扫描。
        MediaStore.Images.Media.insertImage(
            context.contentResolver,
            file.absolutePath,
            name,
            null,
        )
        return true
    }
}

