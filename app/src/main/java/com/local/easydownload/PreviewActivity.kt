package com.local.easydownload

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import java.net.URL

class PreviewActivity : ComponentActivity() {
    private val url: String by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }
    private val titleText: String by lazy { intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "媒体预览" } }
    private val mediaType: MediaType by lazy {
        runCatching { MediaType.valueOf(intent.getStringExtra(EXTRA_MEDIA_TYPE).orEmpty()) }.getOrDefault(MediaType.Other)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root.addView(buildTopBar())
        root.addView(buildPreview(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomMenu())
        return root
    }

    private fun buildTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 12, 10, 10)
            setBackgroundColor(0xDD000000.toInt())
            addView(Button(this@PreviewActivity).apply {
                text = "返回"
                setOnClickListener { finish() }
            })
            addView(TextView(this@PreviewActivity).apply {
                text = titleText
                setTextColor(Color.WHITE)
                textSize = 16f
                maxLines = 2
                setPadding(12, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@PreviewActivity).apply {
                text = "浏览器"
                setOnClickListener { openUrl(this@PreviewActivity, url) }
            })
        }
    }

    private fun buildPreview(): View {
        return when (mediaType) {
            MediaType.Video, MediaType.Audio, MediaType.Playlist -> buildVideoPreview()
            MediaType.Image, MediaType.Gif -> buildImagePreview()
            else -> buildFallbackPreview()
        }
    }

    private fun buildVideoPreview(): View {
        return VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            setMediaController(MediaController(this@PreviewActivity).also { it.setAnchorView(this) })
            setOnErrorListener { _, _, _ ->
                toast(this@PreviewActivity, "无法直接预览，请用浏览器打开")
                true
            }
            runCatching {
                setVideoURI(Uri.parse(url))
                start()
            }.onFailure {
                toast(this@PreviewActivity, "无法直接预览，请用浏览器打开")
            }
        }
    }

    private fun buildImagePreview(): View {
        val escaped = url.replace("&", "&amp;").replace("\"", "&quot;")
        val html = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=8.0, user-scalable=yes">
              <style>
                html,body{margin:0;height:100%;background:#000;display:flex;align-items:center;justify-content:center;}
                img{max-width:100%;max-height:100%;object-fit:contain;}
              </style>
            </head>
            <body><img src="$escaped"></body>
            </html>
        """.trimIndent()
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
        }
    }

    private fun buildFallbackPreview(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            addView(TextView(this@PreviewActivity).apply {
                text = "此资源无法在应用内直接预览"
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@PreviewActivity).apply {
                text = url
                setTextColor(0xFFB0BEC5.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 14, 0, 14)
            })
            addView(Button(this@PreviewActivity).apply {
                text = "浏览器打开"
                setOnClickListener { openUrl(this@PreviewActivity, url) }
            })
        }
    }

    private fun buildBottomMenu(): View {
        val scrollView = HorizontalScrollView(this).apply {
            setBackgroundColor(0xEE101010.toInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 10, 10, 14)
        }
        scrollView.addView(row)
        row.addAction("下载") {
            enqueueMediaDownload(this, MediaItem(url = url, type = mediaType, title = titleText, source = "预览页"))
            toast(this, "已加入下载任务")
        }
        row.addAction("复制链接") { copyText(this, url) }
        row.addAction("浏览器打开") { openUrl(this, url) }
        row.addAction("重命名") { toast(this, "本地重命名工具开发中") }
        if (mediaType == MediaType.Image || mediaType == MediaType.Gif) {
            row.addAction("设为壁纸") { setWallpaperFromUrl() }
            row.addAction("图片编辑") { toast(this, "本地图片编辑开发中") }
        }
        if (mediaType == MediaType.Video || mediaType == MediaType.Playlist || mediaType == MediaType.Audio) {
            row.addAction("提取音频") { toast(this, "本地工具开发中") }
            row.addAction("转 MP4") { toast(this, "本地工具开发中") }
            row.addAction("压缩") { toast(this, "本地工具开发中") }
            row.addAction("取帧") { toast(this, "本地工具开发中") }
        }
        row.addAction("更多") { toast(this, "更多本地工具开发中") }
        return scrollView
    }

    private fun LinearLayout.addAction(label: String, action: () -> Unit) {
        addView(Button(this@PreviewActivity).apply {
            text = label
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(4, 0, 4, 0)
        })
    }

    private fun setWallpaperFromUrl() {
        toast(this, "正在设置壁纸")
        Thread {
            val ok = runCatching {
                URL(url).openStream().use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    WallpaperManager.getInstance(this).setBitmap(bitmap)
                }
            }.isSuccess
            runOnUiThread { toast(this, if (ok) "壁纸已设置" else "设置失败") }
        }.start()
    }
}
