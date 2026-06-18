package com.local.easydownload

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.MediaController
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
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(buildTopBar())
            addView(buildPreview(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buildBottomMenu())
        }
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

    private fun buildPreview(): View = when (mediaType) {
        MediaType.Video, MediaType.Audio, MediaType.Playlist -> buildVideoPreview()
        MediaType.Image, MediaType.Gif -> buildImagePreview()
        MediaType.Web -> buildWebPreview()
        else -> buildFallbackPreview()
    }

    private fun buildVideoPreview(): View {
        val container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val fallback = buildFallbackPreview().apply { visibility = View.GONE }
        val videoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            setMediaController(MediaController(this@PreviewActivity).also { it.setAnchorView(this) })
            setOnPreparedListener { player ->
                fallback.visibility = View.GONE
                player.isLooping = false
            }
            setOnErrorListener { _, _, _ ->
                fallback.visibility = View.VISIBLE
                true
            }
            runCatching {
                setVideoURI(Uri.parse(url), mediaHeaders(url))
                start()
            }.onFailure {
                fallback.visibility = View.VISIBLE
            }
        }
        container.addView(videoView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(fallback, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return container
    }

    private fun buildImagePreview(): View {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
            }
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.javaScriptEnabled = true
            loadUrl(this@PreviewActivity.url, mediaHeaders(this@PreviewActivity.url))
        }
    }

    private fun buildWebPreview(): View {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            loadUrl(this@PreviewActivity.url, mediaHeaders(this@PreviewActivity.url))
        }
    }

    private fun buildFallbackPreview(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.BLACK)
            addView(TextView(this@PreviewActivity).apply {
                text = "此资源无法直接预览"
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
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 10, 10, 14)
        }
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
        return HorizontalScrollView(this).apply {
            setBackgroundColor(0xEE101010.toInt())
            addView(row)
        }
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
                URL(url).openConnection().apply {
                    setRequestProperty("User-Agent", PREVIEW_UA)
                    setRequestProperty("Referer", refererFor(this@PreviewActivity.url))
                }.getInputStream().use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    WallpaperManager.getInstance(this).setBitmap(bitmap)
                }
            }.isSuccess
            runOnUiThread { toast(this, if (ok) "壁纸已设置" else "设置失败") }
        }.start()
    }
}

fun mediaHeaders(url: String): Map<String, String> = mapOf(
    "User-Agent" to PREVIEW_UA,
    "Referer" to refererFor(url),
    "Accept" to "*/*"
)

fun refererFor(url: String): String = when {
    url.contains("douyin", ignoreCase = true) || url.contains("douyinvod", ignoreCase = true) || url.contains("byteimg", ignoreCase = true) -> "https://www.douyin.com/"
    else -> Uri.parse(url).let { "${it.scheme}://${it.host}/" }
}

private const val PREVIEW_UA =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
