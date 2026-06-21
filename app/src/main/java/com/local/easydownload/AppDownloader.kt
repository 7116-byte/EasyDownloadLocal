package com.local.easydownload

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object AppDownloader {
    private const val DOWNLOAD_FOLDER = "EasyDownloadLocal"
    private const val CACHE_FOLDER = "media-cache"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun enqueue(context: Context, item: MediaItem) {
        val appContext = context.applicationContext
        Thread {
            val result = runCatching {
                val cacheFile = when (item.type) {
                    MediaType.Playlist -> writePlaylistCache(appContext, item)
                    else -> downloadToCacheIfNeeded(appContext, item)
                }
                saveCacheToDownloads(appContext, item, cacheFile)
            }
            result.onSuccess { savedPath ->
                TaskStore.update(appContext, item.url, "已保存", 100, savedPath, "应用下载目录")
                showToast(appContext, "已保存到 Download/$DOWNLOAD_FOLDER")
            }.onFailure { error ->
                TaskStore.update(appContext, item.url, "保存失败", 0, speed = error.message.orEmpty().take(60))
                showToast(appContext, "保存失败：${error.message.orEmpty().take(40)}")
            }
        }.start()
    }

    fun prefetch(context: Context, item: MediaItem) {
        if (item.type == MediaType.Playlist || item.url.isBlank()) return
        val appContext = context.applicationContext
        Thread {
            runCatching { downloadToCacheIfNeeded(appContext, item) }
        }.start()
    }

    fun cachedFileIfPresent(context: Context, url: String, type: MediaType): File? {
        val file = cacheFileFor(context, url, type)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    fun downloadToCacheIfNeeded(context: Context, item: MediaItem): File {
        val target = cacheFileFor(context, item.url, item.type)
        if (target.exists() && target.length() > 0L) return target
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        val connection = (URL(item.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            mediaHeaders(item.url).forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                }
            }
            if (temp.length() <= 0L) error("empty download")
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            connection.disconnect()
            if (temp.exists() && !target.exists()) temp.delete()
        }
        return target
    }

    private fun writePlaylistCache(context: Context, item: MediaItem): File {
        val target = cacheFileFor(context, item.url, item.type)
        if (target.exists() && target.length() > 0L) return target
        target.parentFile?.mkdirs()
        target.writeText(item.url, Charsets.UTF_8)
        return target
    }

    private fun saveCacheToDownloads(context: Context, item: MediaItem, cacheFile: File): String {
        val outputName = item.fileName.safeFileName()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, outputName, item.type, cacheFile)
        } else {
            saveWithPublicDirectory(context, outputName, cacheFile)
        }
    }

    private fun saveWithMediaStore(context: Context, name: String, type: MediaType, cacheFile: File): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeFor(type))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("cannot create download item")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(cacheFile).use { input -> input.copyTo(output) }
            } ?: error("cannot open download item")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri.toString()
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveWithPublicDirectory(context: Context, name: String, cacheFile: File): String {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FOLDER)
        val targetDir = publicDir.takeIf { runCatching { it.mkdirs() || it.exists() }.getOrDefault(false) }
            ?: File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FOLDER).apply { mkdirs() }
        val output = uniqueFile(File(targetDir, name))
        FileInputStream(cacheFile).use { input ->
            FileOutputStream(output).use { target -> input.copyTo(target) }
        }
        return output.absolutePath
    }

    private fun uniqueFile(base: File): File {
        if (!base.exists()) return base
        val name = base.nameWithoutExtension
        val ext = base.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        for (index in 1..999) {
            val next = File(base.parentFile, "$name-$index$ext")
            if (!next.exists()) return next
        }
        return File(base.parentFile, "$name-${System.currentTimeMillis()}$ext")
    }

    private fun cacheFileFor(context: Context, url: String, type: MediaType): File {
        val cacheRoot = File(context.externalCacheDir ?: context.cacheDir, CACHE_FOLDER).apply { mkdirs() }
        val ext = fileNameFor(url, type)
            .substringAfterLast('.', fallbackExt(type).removePrefix("."))
            .lowercase(Locale.US)
            .takeIf { it.length in 2..5 }
            ?: fallbackExt(type).removePrefix(".")
        return File(cacheRoot, "${url.sha256()}.$ext")
    }

    private fun fallbackExt(type: MediaType): String = when (type) {
        MediaType.Video -> ".mp4"
        MediaType.Image -> ".jpg"
        MediaType.Gif -> ".gif"
        MediaType.Audio -> ".mp3"
        MediaType.Playlist -> ".m3u8"
        else -> ".bin"
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun showToast(context: Context, message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
