package com.local.easydownload

import android.graphics.BitmapFactory
import android.widget.ImageView

fun loadThumbnailInto(imageView: ImageView, url: String) {
    if (url.isBlank()) {
        imageView.setImageDrawable(null)
        return
    }
    imageView.tag = url
    imageView.setBackgroundColor(0xFFEFF3F6.toInt())
    Thread {
        val bitmap = runCatching {
            val type = classifyMedia(url).takeIf { it == MediaType.Image || it == MediaType.Gif } ?: MediaType.Image
            val file = AppDownloader.downloadToCacheIfNeeded(imageView.context.applicationContext, MediaItem(url = url, type = type))
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
        imageView.post {
            if (imageView.tag == url && bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }.start()
}
