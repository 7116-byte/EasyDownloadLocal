package com.local.easydownload

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWindowService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var subtitleView: TextView? = null
    private var lastClipboardText = ""
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var lastTapTime = 0L
    private var pendingSingleTap: Runnable? = null
    private var pendingLongPress: Runnable? = null
    private var longPressTriggered = false
    private var lastParsedText = ""
    private var lastParsedItems: List<MediaItem> = emptyList()

    private val clipboardPoller = object : Runnable {
        override fun run() {
            refreshClipboardState()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        addFloatingView()
        refreshClipboardState()
        handler.postDelayed(clipboardPoller, 500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        floatingView?.let { view -> runCatching { windowManager?.removeView(view) } }
        floatingView = null
        super.onDestroy()
    }

    private fun addFloatingView() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xEE00796B.toInt())
        }
        val main = TextView(this).apply {
            text = "粘"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 26f
            gravity = Gravity.CENTER
        }
        subtitleView = TextView(this).apply {
            text = ""
            setTextColor(0xFFE0F2F1.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
        }
        root.addView(main)
        root.addView(subtitleView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    longPressTriggered = false
                    pendingLongPress = Runnable {
                        longPressTriggered = true
                        pendingSingleTap?.let { handler.removeCallbacks(it) }
                        pendingSingleTap = null
                        openMainWithClipboard()
                    }.also { handler.postDelayed(it, 650) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                        pendingLongPress?.let { handler.removeCallbacks(it) }
                        pendingLongPress = null
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager?.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    pendingLongPress?.let { handler.removeCallbacks(it) }
                    pendingLongPress = null
                    val moved = kotlin.math.abs(event.rawX - downX) > 10 || kotlin.math.abs(event.rawY - downY) > 10
                    if (!moved && !longPressTriggered) handleTap()
                    true
                }
                else -> true
            }
        }

        floatingView = root
        windowManager?.addView(root, params)
    }

    private fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 360) {
            pendingSingleTap?.let { handler.removeCallbacks(it) }
            pendingSingleTap = null
            lastTapTime = 0L
            downloadParsedClipboard()
            return
        }
        lastTapTime = now
        val runnable = Runnable { parseClipboardOnly() }
        pendingSingleTap = runnable
        handler.postDelayed(runnable, 380)
    }

    private fun refreshClipboardState() {
        lastClipboardText = parseIntentForText(this)
        subtitleView?.text = if (extractFirstUrl(lastClipboardText) == null) "" else "待解析链接"
    }

    private fun parseClipboardOnly() {
        refreshClipboardState()
        val text = lastClipboardText
        if (extractFirstUrl(text) == null) {
            Toast.makeText(this, "剪贴板没有链接", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在解析链接", Toast.LENGTH_SHORT).show()
        scope.launch {
            val items = withContext(Dispatchers.IO) { resolveDownloadItems(this@FloatingWindowService, text) }
            lastParsedText = text
            lastParsedItems = items
            if (items.isEmpty()) {
                subtitleView?.text = "未解析到"
                Toast.makeText(this@FloatingWindowService, "未解析到媒体，长按打开软件查看", Toast.LENGTH_LONG).show()
            } else {
                val summary = summarizeItems(items)
                subtitleView?.text = summary
                Toast.makeText(this@FloatingWindowService, "解析到 $summary，双击下载", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadParsedClipboard() {
        refreshClipboardState()
        val text = lastClipboardText
        if (extractFirstUrl(text) == null) {
            Toast.makeText(this, "剪贴板没有链接", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val items = if (text == lastParsedText && lastParsedItems.isNotEmpty()) {
                lastParsedItems
            } else {
                Toast.makeText(this@FloatingWindowService, "正在解析并下载", Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.IO) { resolveDownloadItems(this@FloatingWindowService, text) }.also {
                    lastParsedText = text
                    lastParsedItems = it
                }
            }
            if (items.isEmpty()) {
                Toast.makeText(this@FloatingWindowService, "未解析到媒体，长按打开软件查看", Toast.LENGTH_LONG).show()
            } else {
                items.forEach { enqueueMediaDownload(this@FloatingWindowService, it) }
                Toast.makeText(this@FloatingWindowService, "已下载 ${summarizeItems(items)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun summarizeItems(items: List<MediaItem>): String {
        return items.groupBy { item ->
            when (item.type) {
                MediaType.Video -> "MP4"
                MediaType.Image -> "JPG"
                MediaType.Gif -> "GIF"
                MediaType.Audio -> "MP3"
                MediaType.Playlist -> "M3U8"
                else -> item.type.label
            }
        }.entries.joinToString(" / ") { "${it.value.size} 个${it.key}" }
    }

    private fun openMainWithClipboard() {
        refreshClipboardState()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_PARSE_TEXT, lastClipboardText)
        startActivity(intent)
    }
}
