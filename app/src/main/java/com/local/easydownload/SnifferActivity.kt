package com.local.easydownload

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.Collections

class SnifferActivity : ComponentActivity() {
    private val initialUrl: String by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }
    private val sniffed = Collections.synchronizedList(mutableListOf<MediaItem>())
    private lateinit var webView: WebView
    private lateinit var countButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        webView.loadUrl(initialUrl.ifBlank { "https://www.baidu.com" })
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildLayout(): View {
        val root = FrameLayout(this)
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F8FA.toInt())
        }
        main.addView(buildTopBar())
        main.addView(TextView(this).apply {
            text = "顶部提示：网页加载后点击“获取素材”，可查看嗅探到的视频、图片、音频和播放列表。"
            setTextColor(0xFFC62828.toInt())
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xFFFFF5F5.toInt())
        })
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = MOBILE_UA_FOR_SNIFFER
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    recordCandidate(request.url.toString(), request.requestHeaders["Accept"].orEmpty(), "网页请求")
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    collectDomLinks()
                }
            }
        }
        main.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(main)
        countButton = Button(this).apply {
            text = "获取素材 0"
            setOnClickListener {
                collectDomLinks()
                showMediaDialog()
            }
        }
        root.addView(countButton, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 18, 22)
        })
        return root
    }

    private fun buildTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 10, 10, 8)
            setBackgroundColor(Color.WHITE)
            addView(Button(this@SnifferActivity).apply {
                text = "返回"
                setOnClickListener { finish() }
            })
            addView(TextView(this@SnifferActivity).apply {
                text = "任意门"
                textSize = 18f
                setTextColor(0xFF111827.toInt())
                setPadding(14, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@SnifferActivity).apply {
                text = "清空"
                setOnClickListener {
                    sniffed.clear()
                    updateCount()
                    toast(this@SnifferActivity, "已清空")
                }
            })
        }
    }

    private fun collectDomLinks() {
        val script = """
            (function(){
              var out = [];
              document.querySelectorAll('img,video,source,audio,source,a').forEach(function(e){
                ['src','href','poster','data-src'].forEach(function(k){
                  var v = e.getAttribute && e.getAttribute(k);
                  if(v){
                    try { out.push(new URL(v, location.href).href); } catch(err) {}
                  }
                });
              });
              return out.join('\n');
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            val text = raw.trim('"').replace("\\n", "\n").replace("\\/", "/")
            Regex("""https?://[^\s"']+""").findAll(text).forEach {
                recordCandidate(it.value, "", "页面元素")
            }
        }
    }

    private fun recordCandidate(url: String, accept: String, source: String) {
        if (url.startsWith("data:", ignoreCase = true) || url.length < 12) return
        val type = classifyMedia(url, accept)
        val keep = type == MediaType.Video ||
            type == MediaType.Image ||
            type == MediaType.Gif ||
            type == MediaType.Audio ||
            type == MediaType.Playlist
        if (!keep) return
        synchronized(sniffed) {
            if (sniffed.none { it.url == url }) {
                val cover = when {
                    type == MediaType.Image || type == MediaType.Gif -> url
                    else -> sniffed.firstOrNull { it.type == MediaType.Image || it.type == MediaType.Gif }?.url.orEmpty()
                }
                sniffed.add(MediaItem(url = url, type = type, source = source, selected = true, coverUrl = cover))
            }
        }
        runOnUiThread { updateCount() }
    }

    private fun updateCount() {
        if (::countButton.isInitialized) countButton.text = "获取素材 ${sniffed.size}"
    }

    private fun showMediaDialog() {
        val dialog = Dialog(this)
        val dialogItems = mutableListOf<MediaItem>()
        var filterSmallImage = true
        fun reloadItems() {
            dialogItems.clear()
            val sourceItems = synchronized(sniffed) { sniffed.toList() }
            dialogItems.addAll(sourceItems.filter { item ->
                !filterSmallImage || item.type !in listOf(MediaType.Image, MediaType.Gif) || !looksLikeSmallImage(item.url)
            })
        }
        reloadItems()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            setBackgroundColor(Color.WHITE)
        }
        val header = TextView(this).apply {
            text = "媒体嗅探"
            textSize = 20f
            setTextColor(0xFF111827.toInt())
        }
        val tip = TextView(this).apply {
            text = "加载出的视频和图片可先查看，再决定下载或复制链接。"
            setTextColor(0xFFC62828.toInt())
            setPadding(0, 6, 0, 8)
        }
        val filter = CheckBox(this).apply {
            text = "图片尺寸过滤：隐藏图标/头像/小缩略图"
            isChecked = filterSmallImage
        }
        val selectedInfo = TextView(this).apply {
            setTextColor(0xFF667085.toInt())
        }
        lateinit var adapter: MediaGridAdapter
        fun refreshSelectedText() {
            selectedInfo.text = "已选 ${dialogItems.count { it.selected }} / ${dialogItems.size}"
        }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val clearButton = Button(this).apply {
            text = "清空"
            setOnClickListener {
                sniffed.clear()
                dialogItems.clear()
                adapter.notifyDataSetChanged()
                updateCount()
                refreshSelectedText()
            }
        }
        val allButton = Button(this).apply {
            text = "全选"
            setOnClickListener {
                for (index in dialogItems.indices) dialogItems[index] = dialogItems[index].copy(selected = true)
                adapter.notifyDataSetChanged()
                refreshSelectedText()
            }
        }
        actionRow.addView(clearButton)
        actionRow.addView(allButton)
        actionRow.addView(selectedInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val grid = GridView(this).apply {
            numColumns = 3
            verticalSpacing = 8
            horizontalSpacing = 8
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        }
        adapter = MediaGridAdapter(dialogItems, onToggle = { index ->
            dialogItems[index] = dialogItems[index].copy(selected = !dialogItems[index].selected)
            adapter.notifyDataSetChanged()
            refreshSelectedText()
        }, onPreview = { item ->
            openPreview(this, item)
        })
        grid.adapter = adapter

        filter.setOnCheckedChangeListener { _, checked ->
            filterSmallImage = checked
            reloadItems()
            adapter.notifyDataSetChanged()
            refreshSelectedText()
        }
        val download = Button(this).apply {
            text = "下载选中内容"
            setOnClickListener {
                val chosen = dialogItems.filter { it.selected }
                if (chosen.isEmpty()) {
                    toast(this@SnifferActivity, "请先选择内容")
                } else {
                    chosen.forEach { enqueueMediaDownload(this@SnifferActivity, it) }
                    toast(this@SnifferActivity, "已加入下载任务")
                    dialog.dismiss()
                }
            }
        }

        root.addView(header)
        root.addView(tip)
        root.addView(filter)
        root.addView(actionRow)
        root.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(download, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        refreshSelectedText()
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun looksLikeSmallImage(url: String): Boolean {
        val lower = url.lowercase()
        return listOf("avatar", "icon", "logo", "emoji", "thumb", "small", "1x1", "sprite").any { lower.contains(it) }
    }

    private inner class MediaGridAdapter(
        private val items: List<MediaItem>,
        private val onToggle: (Int) -> Unit,
        private val onPreview: (MediaItem) -> Unit
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val thumbUrl = item.coverUrl.ifBlank {
                if (item.type == MediaType.Image || item.type == MediaType.Gif) item.url else ""
            }
            val root = (convertView as? LinearLayout) ?: LinearLayout(this@SnifferActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
                setBackgroundColor(0xFFEFF3F6.toInt())
            }
            root.removeAllViews()
            if (thumbUrl.isNotBlank()) {
                root.addView(ImageView(this@SnifferActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    loadThumbnailInto(this, thumbUrl)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 118))
            } else {
                root.addView(TextView(this@SnifferActivity).apply {
                    text = typeIcon(item.type)
                    textSize = 22f
                    gravity = Gravity.CENTER
                })
            }
            root.addView(TextView(this@SnifferActivity).apply {
                text = "${typeIcon(item.type)} ${item.type.label}"
                setTextColor(0xFF111827.toInt())
                gravity = Gravity.CENTER
            })
            root.addView(TextView(this@SnifferActivity).apply {
                text = if (item.selected) "已选" else "未选"
                setTextColor(if (item.selected) 0xFF00796B.toInt() else 0xFF667085.toInt())
                gravity = Gravity.CENTER
            })
            root.addView(TextView(this@SnifferActivity).apply {
                text = item.fileName
                maxLines = 1
                textSize = 11f
                setTextColor(0xFF667085.toInt())
                gravity = Gravity.CENTER
            })
            root.setOnClickListener { onPreview(item) }
            root.setOnLongClickListener {
                onToggle(position)
                true
            }
            return root
        }
    }
}

private const val MOBILE_UA_FOR_SNIFFER =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
