package com.local.easydownload

import android.graphics.BitmapFactory
import android.widget.ImageView
import java.net.URL

fun loadThumbnailInto(imageView: ImageView, url: String) {
    if (url.isBlank()) {
        imageView.setImageDrawable(null)
        return
    }
    imageView.tag = url
    imageView.setBackgroundColor(0xFFEFF3F6.toInt())
    Thread {
        val bitmap = runCatching {
            URL(url).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 12000
                mediaHeaders(url).forEach { (key, value) -> setRequestProperty(key, value) }
            }.getInputStream().use { stream -> BitmapFactory.decodeStream(stream) }
        }.getOrNull()
        imageView.post {
            if (imageView.tag == url && bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }.start()
}
