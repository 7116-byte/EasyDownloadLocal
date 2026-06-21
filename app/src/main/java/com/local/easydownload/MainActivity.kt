package com.local.easydownload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
            else -> intent?.getStringExtra(EXTRA_PARSE_TEXT).orEmpty()
        }
        setContent { EasyDownloadApp(initialText) }
    }
}

enum class MediaType(val label: String) {
    Video("视频"),
    Image("图片"),
    Gif("GIF"),
    Audio("音频"),
    Playlist("列表"),
    Web("网页"),
    File("文件"),
    Other("其他")
}

data class MediaItem(
    val url: String,
    val type: MediaType,
    val title: String = "",
    val coverUrl: String = "",
    val size: String = "",
    val source: String = "",
    val selected: Boolean = true,
    val localPath: String = "",
    val status: String = "待下载",
    val progress: Int = 0,
    val speed: String = ""
) {
    val fileName: String
        get() = fileNameFor(url, type)
}

private data class ParsedResult(
    val inputUrl: String,
    val finalUrl: String,
    val title: String,
    val description: String,
    val items: List<MediaItem>,
    val error: String? = null
)

private data class UpdateInfo(
    val latestVersion: String,
    val pageUrl: String,
    val apkUrl: String,
    val hasUpdate: Boolean,
    val error: String? = null
)

private data class ExtractedMediaUrl(
    val url: String,
    val type: MediaType,
    val source: String,
    val coverUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val byteSize: Long = 0L
)

private val AppColors = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFF1565C0),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    onPrimary = Color.White
)

@Composable
private fun EasyDownloadApp(initialText: String) {
    MaterialTheme(colorScheme = AppColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var tab by remember { mutableStateOf(0) }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> DownloadStartScreen(initialText)
                        1 -> DownloadTaskScreen()
                        2 -> ToolScreen()
                        else -> MineScreen()
                    }
                }
                NavigationBar(containerColor = Color.White) {
                    val labels = listOf("首页", "任务", "工具", "我的")
                    val icons = listOf("↓", "≡", "□", "我")
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(icons[index]) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStartScreen(initialText: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedSettings = remember { AppSettings.load(context) }
    var input by remember { mutableStateOf(initialText) }
    var includeImage by remember { mutableStateOf(savedSettings.includeImage) }
    var includeVideo by remember { mutableStateOf(savedSettings.includeVideo) }
    var includeAudio by remember { mutableStateOf(savedSettings.includeAudio) }
    var includePlaylist by remember { mutableStateOf(savedSettings.includePlaylist) }
    var parsing by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<ParsedResult?>(null) }
    val selectedItems = remember { mutableStateListOf<MediaItem>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Text("便捷下载", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("本地版", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("粘贴要下载的平台链接", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { openSupportList(context) }) { Text("支持列表") }
                }
                Text("先做平台解析，资源不完整时可用任意门嗅探网页请求。", color = Color(0xFF667085))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().height(128.dp),
                    label = { Text("分享文案或链接") },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledCheckBox("图片", includeImage) {
                        includeImage = it
                        AppSettings.save(context, DownloadSettings(includeVideo, includeImage, includeAudio, includePlaylist))
                    }
                    LabeledCheckBox("视频", includeVideo) {
                        includeVideo = it
                        AppSettings.save(context, DownloadSettings(includeVideo, includeImage, includeAudio, includePlaylist))
                    }
                    LabeledCheckBox("音频", includeAudio) {
                        includeAudio = it
                        AppSettings.save(context, DownloadSettings(includeVideo, includeImage, includeAudio, includePlaylist))
                    }
                    LabeledCheckBox("m3u8", includePlaylist) {
                        includePlaylist = it
                        AppSettings.save(context, DownloadSettings(includeVideo, includeImage, includeAudio, includePlaylist))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { input = "" }) { Text("清空") }
                    OutlinedButton(onClick = { input = clipboardText(context) }) { Text("粘贴") }
                    Button(
                        enabled = !parsing,
                        onClick = {
                            parsing = true
                            scope.launch {
                                val result = parsePlatform(input)
                                val filtered = result.items.filter {
                                    (includeImage && (it.type == MediaType.Image || it.type == MediaType.Gif)) ||
                                        (includeVideo && it.type == MediaType.Video) ||
                                        (includePlaylist && it.type == MediaType.Playlist) ||
                                        (includeAudio && it.type == MediaType.Audio)
                                }
                                parsed = result.copy(items = filtered)
                                selectedItems.clear()
                                selectedItems.addAll(filtered)
                                parsing = false
                            }
                        }
                    ) { Text(if (parsing) "解析中" else "开始") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val url = extractFirstUrl(input)
                        if (url == null) {
                            toast(context, "请先输入链接")
                        } else {
                            context.startActivity(Intent(context, SnifferActivity::class.java).putExtra(EXTRA_URL, url))
                        }
                    }
                ) { Text("链接不支持？用任意门嗅探") }
            }
        }
        parsed?.let { result ->
            item {
                SectionCard {
                    if (result.error != null) {
                        Text("解析失败", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                        Text(result.error)
                    } else {
                        ResultLine("跳转后", result.finalUrl)
                        ResultLine("标题", result.title.ifBlank { "未识别" })
                        ResultLine("描述", result.description.ifBlank { "未识别" })
                    }
                }
            }
            if (result.items.isNotEmpty()) {
                item {
                    SnifferPanel(
                        items = selectedItems,
                        onClear = { selectedItems.clear() },
                        onToggleAll = { checked ->
                            for (index in selectedItems.indices) {
                                selectedItems[index] = selectedItems[index].copy(selected = checked)
                            }
                        },
                        onToggle = { item ->
                            val index = selectedItems.indexOfFirst { it.url == item.url }
                            if (index >= 0) selectedItems[index] = selectedItems[index].copy(selected = !selectedItems[index].selected)
                        },
                        onPreview = { openPreview(context, it) },
                        onDownloadSelected = {
                            val chosen = selectedItems.filter { it.selected }
                            if (chosen.isEmpty()) {
                                toast(context, "请先选择内容")
                            } else {
                                chosen.forEach { enqueueMediaDownload(context, it) }
                                toast(context, "已加入下载任务")
                            }
                        }
                    )
                }
            } else if (result.error == null) {
                item {
                    SectionCard {
                        Text("未直接解析到可下载媒体", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                        Text("平台可能只返回了风控页或脚本占位页。可以进入任意门，等待网页播放后再获取素材。", color = Color(0xFF667085))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            context.startActivity(Intent(context, SnifferActivity::class.java).putExtra(EXTRA_URL, result.finalUrl.ifBlank { result.inputUrl }))
                        }) { Text("打开任意门嗅探") }
                    }
                }
            }
        }
        item {
            Text("下载教程", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("1. 打开要下载的平台，点击分享。", color = Color(0xFF667085))
            Text("2. 复制链接或分享文案。", color = Color(0xFF667085))
            Text("3. 粘贴到本页，选择图片/视频/音频后开始解析。", color = Color(0xFF667085))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LabeledCheckBox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun SnifferPanel(
    items: List<MediaItem>,
    onClear: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onToggle: (MediaItem) -> Unit,
    onPreview: (MediaItem) -> Unit,
    onDownloadSelected: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("加载出的素材可先预览，再决定处理方式", color = Color(0xFFC62828), modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("清空") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledCheckBox("全选", items.all { it.selected } && items.isNotEmpty(), onToggleAll)
            Text("已选 ${items.count { it.selected }} / ${items.size}", color = Color(0xFF667085))
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(340.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items) { item ->
                MediaTile(item = item, onToggle = { onToggle(item) }, onPreview = { onPreview(item) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = onDownloadSelected) {
            Text("下载选中内容")
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, onToggle: () -> Unit, onPreview: () -> Unit) {
    val thumbUrl = item.coverUrl.ifBlank {
        if (item.type == MediaType.Image || item.type == MediaType.Gif) item.url else ""
    }
    Box(
        modifier = Modifier
            .height(112.dp)
            .background(Color(0xFFEFF3F6), RoundedCornerShape(8.dp))
            .clickable(onClick = onPreview)
            .padding(6.dp)
    ) {
        if (thumbUrl.isNotBlank()) {
            NetworkThumbnail(thumbUrl, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopStart).background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("${typeIcon(item.type)} ${item.type.label}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (item.size.isNotBlank()) {
                Box(Modifier.align(Alignment.BottomStart).background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(item.size, color = Color.White)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(typeIcon(item.type), style = MaterialTheme.typography.headlineSmall)
                Text(item.type.label, fontWeight = FontWeight.Bold)
                Text(item.size.ifBlank { item.source }, color = Color(0xFF667085), maxLines = 1)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clickable(onClick = onToggle)
                .background(if (item.selected) MaterialTheme.colorScheme.primary else Color(0x99000000), RoundedCornerShape(12.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(if (item.selected) "✓" else "选", color = Color.White)
        }
    }
}

@Composable
private fun NetworkThumbnail(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFEFF3F6.toInt())
            }
        },
        update = { imageView -> loadThumbnailInto(imageView, url) }
    )
}

@Composable
private fun DownloadTaskScreen() {
    val context = LocalContext.current
    val tasks = remember { mutableStateListOf<MediaItem>() }
    val selectedUrls = remember { mutableStateListOf<String>() }
    fun reloadTasks() {
        tasks.clear()
        tasks.addAll(TaskStore.load(context))
        selectedUrls.removeAll { selected -> tasks.none { it.url == selected } }
    }
    LaunchedEffect(Unit) {
        reloadTasks()
    }
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("下载任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("显示本地任务状态、类型、进度和系统下载器提交结果。", color = Color(0xFF667085))
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledCheckBox("全选", tasks.isNotEmpty() && selectedUrls.size == tasks.size) { checked ->
                        selectedUrls.clear()
                        if (checked) selectedUrls.addAll(tasks.map { it.url })
                    }
                    Text("已选 ${selectedUrls.size} / ${tasks.size}", color = Color(0xFF667085), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = selectedUrls.isNotEmpty(),
                        onClick = {
                            TaskStore.removeUrls(context, selectedUrls.toSet())
                            selectedUrls.clear()
                            reloadTasks()
                            toast(context, "已删除选中任务")
                        }
                    ) { Text("删除选中") }
                    OutlinedButton(
                        enabled = tasks.isNotEmpty(),
                        onClick = {
                            TaskStore.clear(context)
                            selectedUrls.clear()
                            reloadTasks()
                            toast(context, "已清空任务")
                        }
                    ) { Text("清空任务") }
                    OutlinedButton(onClick = { reloadTasks() }) { Text("刷新") }
                }
            }
        }
        if (tasks.isEmpty()) {
            item { SectionCard { Text("暂无任务，从首页解析并下载选中内容后会显示在这里。") } }
        }
        items(tasks) { task ->
            TaskCard(
                task = task,
                selected = task.url in selectedUrls,
                onSelectedChange = { checked ->
                    if (checked) {
                        if (task.url !in selectedUrls) selectedUrls.add(task.url)
                    } else {
                        selectedUrls.remove(task.url)
                    }
                }
            )
        }
    }
}

@Composable
private fun TaskCard(task: MediaItem, selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    val thumbUrl = task.coverUrl.ifBlank {
        if (task.type == MediaType.Image || task.type == MediaType.Gif) task.url else ""
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            Box(
                modifier = Modifier.size(58.dp).background(Color(0xFFEFF3F6), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbUrl.isNotBlank()) {
                    NetworkThumbnail(thumbUrl, Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomEnd).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)) {
                        Text(typeIcon(task.type), color = Color.White)
                    }
                } else {
                    Text(typeIcon(task.type))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(task.fileName, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFE5E7EB), RoundedCornerShape(3.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth(task.progress.coerceIn(0, 100) / 100f)
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                    )
                }
                Text(task.size.ifBlank { task.url }, color = Color(0xFF667085), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                TypeBadge(task.type)
                Text(task.status, color = MaterialTheme.colorScheme.primary)
                Text(task.speed, color = Color(0xFF667085))
            }
        }
    }
}

@Composable
private fun ToolScreen() {
    val context = LocalContext.current
    val initialSettings = remember { AppSettings.load(context) }
    var includeVideo by remember { mutableStateOf(initialSettings.includeVideo) }
    var includeImage by remember { mutableStateOf(initialSettings.includeImage) }
    var includeAudio by remember { mutableStateOf(initialSettings.includeAudio) }
    var includePlaylist by remember { mutableStateOf(initialSettings.includePlaylist) }
    fun saveSettings() {
        AppSettings.save(context, DownloadSettings(includeVideo, includeImage, includeAudio, includePlaylist))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("本地工具", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard {
            Text("悬浮窗", fontWeight = FontWeight.Bold)
            Text("正常显示“粘”；检测到剪切板链接后显示“待解析链接”。单击解析并提示结果，双击下载，长按打开软件。", color = Color(0xFF667085))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { startFloatingWindow(context) }) { Text("开启") }
                OutlinedButton(onClick = { context.stopService(Intent(context, FloatingWindowService::class.java)) }) { Text("关闭") }
            }
        }
        SectionCard {
            Text("下载范围", fontWeight = FontWeight.Bold)
            Text("主页解析和悬浮球单击下载都会使用这里的范围。", color = Color(0xFF667085))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledCheckBox("视频", includeVideo) { includeVideo = it; saveSettings() }
                LabeledCheckBox("图片", includeImage) { includeImage = it; saveSettings() }
                LabeledCheckBox("音频", includeAudio) { includeAudio = it; saveSettings() }
                LabeledCheckBox("m3u8", includePlaylist) { includePlaylist = it; saveSettings() }
            }
        }
        ToolGrid(
            listOf(
                "任意门嗅探" to "WebView 拦截视频、图片、音频、m3u8 请求",
                "视频工具" to "提取音频、转 MP4、压缩、取帧入口",
                "图片工具" to "预览、设壁纸、编辑入口",
                "检查更新" to "我的页检查 GitHub 最新 Release"
            )
        )
    }
}

@Composable
private fun MineScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard {
            Text("便捷下载本地版", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("版本 1.32")
            Text("原版解析/预览工作流复刻：解析、嗅探、预览、选择下载、任务列表。")
        }
        SectionCard {
            Text("检查更新", fontWeight = FontWeight.Bold)
            Text("当前版本：1.32", color = Color(0xFF667085))
            updateInfo?.let {
                Spacer(Modifier.height(6.dp))
                when {
                    it.error != null -> Text("检查失败：${it.error}", color = Color(0xFFC62828))
                    it.hasUpdate -> Text("发现新版本：${it.latestVersion}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    else -> Text("已是最新版本：${it.latestVersion}", color = Color(0xFF667085))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !checking, onClick = {
                    checking = true
                    scope.launch {
                        updateInfo = checkForUpdates()
                        checking = false
                    }
                }) { Text(if (checking) "检查中" else "检查更新") }
                val info = updateInfo
                if (info?.hasUpdate == true) {
                    OutlinedButton(onClick = { openUrl(context, info.apkUrl.ifBlank { info.pageUrl }) }) { Text("下载更新") }
                }
            }
        }
        SectionCard {
            Text("隐私", fontWeight = FontWeight.Bold)
            Text("无广告、无账号、无支付。网络请求仅由你输入的链接或打开的嗅探网页触发。")
        }
    }
}

@Composable
private fun ToolGrid(items: List<Pair<String, String>>) {
    items.forEach { item ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.first, fontWeight = FontWeight.Bold)
                    Text(item.second, color = Color(0xFF667085))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Text(label, fontWeight = FontWeight.Bold)
    Text(value)
    Spacer(Modifier.height(6.dp))
    Divider(color = Color(0xFFE5E7EB))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TypeBadge(type: MediaType) {
    val color = when (type) {
        MediaType.Video -> Color(0xFF1565C0)
        MediaType.Image, MediaType.Gif -> Color(0xFF2E7D32)
        MediaType.Audio -> Color(0xFF6A1B9A)
        MediaType.Playlist -> Color(0xFFEF6C00)
        MediaType.Web -> Color(0xFF455A64)
        MediaType.File -> Color(0xFF5D4037)
        MediaType.Other -> Color(0xFF757575)
    }
    Box(Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(type.label, color = color, fontWeight = FontWeight.Bold)
    }
}

private suspend fun parsePlatform(input: String): ParsedResult = withContext(Dispatchers.IO) {
    val url = extractFirstUrl(input) ?: return@withContext ParsedResult("", "", "", "", emptyList(), "没有找到 http/https 链接")
    try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
            setRequestProperty("Referer", "https://www.douyin.com/")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
        }
        val stream = runCatching { connection.inputStream }.getOrElse { error ->
            connection.errorStream ?: throw error
        }
        val html = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val finalUrl = connection.url.toString()
        val title = htmlTitle(html)
        val description = metaContent(html, "description").ifBlank { metaContent(html, "og:description") }
        val douyin = buildDouyinItems(html, finalUrl, title)
        val items = if (isDouyinContent(finalUrl, html)) {
            douyin
        } else {
            buildMediaItems(html, finalUrl, connection.contentType.orEmpty(), title)
        }.distinctBy { it.url }.sortedBy { mediaRank(it.type) }
        ParsedResult(url, finalUrl, title, description, items)
    } catch (e: Exception) {
        ParsedResult(url, url, "", "", emptyList(), e.message ?: "网络请求失败")
    }
}

fun extractFirstUrl(text: String): String? =
    Regex("""https?://[^\s"'<>]+""").find(text)?.value?.trimEnd('.', ',', ';', ')', ']', '}')

private fun buildMediaItems(html: String, finalUrl: String, mime: String, title: String): List<MediaItem> {
    val values = linkedMapOf<String, String>()
    val decodedHtml = html.deepUnescape()
    val metaNames = listOf(
        "og:video",
        "og:video:url",
        "og:video:secure_url",
        "twitter:player:stream",
        "og:image",
        "og:image:url",
        "twitter:image",
        "og:audio"
    )
    metaNames.forEach { name ->
        metaContent(html, name).takeIf { it.startsWith("http") }?.let { values[it.unescapeUrl()] = "页面元信息" }
    }
    Regex("""(?:src|href|url|play_addr|download_addr|cover|origin_cover|dynamic_cover)["'\s:=]+(?:\[)?["']?(https?:\\?/\\?/[^"'\]\s<>]+|https?://[^"'\]\s<>]+)""", RegexOption.IGNORE_CASE)
        .findAll(html)
        .forEach { values[it.groupValues[1].unescapeUrl()] = "页面资源" }
    Regex("""https?:\\?/\\?/[^"' <>\n\r]+|https?://[^"' <>\n\r]+""", RegexOption.IGNORE_CASE)
        .findAll(html)
        .map { it.value.unescapeUrl() }
        .filter { shouldKeepCandidate(it) }
        .forEach { values.putIfAbsent(it, "页面链接") }
    Regex("""https?://[^"' <>\n\r\\]+""", RegexOption.IGNORE_CASE)
        .findAll(decodedHtml)
        .map { it.value.unescapeUrl() }
        .filter { shouldKeepCandidate(it) }
        .forEach { values.putIfAbsent(it.normalizeMediaUrl(), "JSON 资源") }
    values.putIfAbsent(finalUrl, "跳转目标")
    val coverUrl = values.keys.firstOrNull {
        classifyMedia(it) == MediaType.Image && looksLikeCoverUrl(it)
    }.orEmpty()
    return values.map { (url, source) ->
        val type = classifyMedia(url, if (url == finalUrl) mime else "")
        MediaItem(
            url = url,
            type = type,
            title = title,
            source = source,
            coverUrl = when {
                type == MediaType.Image || type == MediaType.Gif -> url
                coverUrl.isNotBlank() -> coverUrl
                else -> ""
            }
        )
    }.distinctBy { it.url }.sortedBy { mediaRank(it.type) }.take(120)
}

private fun buildDouyinItems(html: String, finalUrl: String, title: String): List<MediaItem> {
    if (!isDouyinContent(finalUrl, html)) return emptyList()
    val id = extractDouyinId(finalUrl, html)
    val sources = linkedMapOf<String, ExtractedMediaUrl>()
    val decodedHtml = html.deepUnescape()
    extractDouyinStructuredMedia(decodedHtml, "抖音页面数据").forEach { sources[it.url] = it }
    if (!id.isNullOrBlank()) {
        fetchDouyinDetailBodies(id).forEach { body ->
            extractDouyinStructuredMedia(body.deepUnescape(), "抖音详情接口").forEach { sources[it.url] = it }
        }
    }
    val fallbackCover = sources.values.firstOrNull { it.type == MediaType.Image && looksLikeCoverUrl(it.url) }?.url
        ?: sources.values.firstOrNull { it.type == MediaType.Image }?.url.orEmpty()
    val hasVideo = sources.values.any { it.type == MediaType.Video && isHighConfidenceVideoUrl(it.url) }
    val selectedSources = selectBestDouyinVideos(sources.values
        .filter { it.type != MediaType.Web && it.type != MediaType.Other }
        .filterNot { it.type == MediaType.Video && looksLikeAudioUrl(it.url) }
        .filterNot { it.type == MediaType.Video && !isHighConfidenceVideoUrl(it.url) }
        .filterNot { hasVideo && (it.type == MediaType.Image || it.type == MediaType.Gif) }
        .filterNot { !hasVideo && (it.type == MediaType.Image || it.type == MediaType.Gif) && !it.source.contains("图文") })
    return selectedSources.map { extracted ->
            MediaItem(
                url = extracted.url,
                type = extracted.type,
                title = title,
                source = extracted.source,
                size = extracted.mediaInfoText(),
                coverUrl = when {
                    extracted.type == MediaType.Image || extracted.type == MediaType.Gif -> extracted.url
                    extracted.coverUrl.isNotBlank() -> extracted.coverUrl
                    fallbackCover.isNotBlank() -> fallbackCover
                    else -> ""
                }
            )
        }
        .distinctBy { it.url }
        .take(30)
}

private fun extractDouyinId(finalUrl: String, html: String): String? {
    val haystack = "$finalUrl\n${html.deepUnescape()}"
    val patterns = listOf(
        Regex("""/video/(\d{8,})"""),
        Regex("""/note/(\d{8,})"""),
        Regex("""(?:aweme_id|item_id|itemId|modal_id|group_id)["'=:\s]+(\d{8,})""", RegexOption.IGNORE_CASE),
        Regex("""(?:aweme_id|item_id|itemId|modal_id|group_id)=([0-9]{8,})""", RegexOption.IGNORE_CASE)
    )
    return patterns.firstNotNullOfOrNull { pattern -> pattern.find(haystack)?.groupValues?.getOrNull(1) }
}

private fun fetchDouyinDetailBodies(awemeId: String): List<String> {
    val urls = listOf(
        "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$awemeId",
        "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=$awemeId&aid=1128&device_platform=webapp&version_name=23.5.0"
    )
    return urls.mapNotNull { url ->
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", MOBILE_UA)
                setRequestProperty("Referer", "https://www.douyin.com/video/$awemeId")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }
}

private fun isDouyinContent(url: String, html: String): Boolean =
    url.contains("douyin", ignoreCase = true) || html.contains("douyin", ignoreCase = true) || html.contains("aweme", ignoreCase = true)

private fun extractDouyinStructuredMedia(text: String, source: String): List<ExtractedMediaUrl> {
    val result = linkedMapOf<String, ExtractedMediaUrl>()
    extractJsonDocuments(text).forEach { json ->
        runCatching {
            when {
                json.trim().startsWith("{") -> collectDouyinFromJson(JSONObject(json), source, result)
                json.trim().startsWith("[") -> collectDouyinFromJson(JSONArray(json), source, result)
            }
        }
    }
    extractDouyinKeyedMedia(text, source).forEach { result.putIfAbsent(it.url, it) }
    if (result.none { it.value.type == MediaType.Video || it.value.type == MediaType.Image || it.value.type == MediaType.Gif }) {
        extractDouyinMediaCandidates(text, source).forEach {
            if (it.type == MediaType.Image || it.type == MediaType.Gif || (it.type == MediaType.Video && isHighConfidenceVideoUrl(it.url))) result.putIfAbsent(it.url, it)
        }
    }
    return result.values.toList()
}

private fun extractDouyinKeyedMedia(text: String, source: String): List<ExtractedMediaUrl> {
    val result = linkedMapOf<String, ExtractedMediaUrl>()
    val decoded = text.deepUnescape()
    extractUrlsNearKeys(decoded, listOf("play_addr", "download_addr", "play_addr_h264", "play_addr_265"), 2600).forEach { raw ->
        val url = raw.normalizeMediaUrl()
        if (isHighConfidenceVideoUrl(url)) result[url] = ExtractedMediaUrl(url, MediaType.Video, source)
    }
    extractUrlsNearKeys(decoded, listOf("cover", "origin_cover", "dynamic_cover", "animated_cover", "display_image", "images"), 1800).forEach { raw ->
        val url = raw.normalizeMediaUrl()
        if (looksLikeCoverUrl(url)) result[url] = ExtractedMediaUrl(url, classifyImageOrGif(url), source)
    }
    extractUrlsNearKeys(decoded, listOf("music", "play_url"), 1400).forEach { raw ->
        val url = raw.normalizeMediaUrl()
        if (looksLikeAudioUrl(url)) result[url] = ExtractedMediaUrl(url, MediaType.Audio, source)
    }
    val cover = result.values.firstOrNull { it.type == MediaType.Image || it.type == MediaType.Gif }?.url.orEmpty()
    return result.values.map {
        if (it.type == MediaType.Video && it.coverUrl.isBlank() && cover.isNotBlank()) it.copy(coverUrl = cover) else it
    }
}

private fun extractUrlsNearKeys(text: String, keys: List<String>, window: Int): List<String> {
    val urls = linkedSetOf<String>()
    keys.forEach { key ->
        Regex(""""${Regex.escape(key)}"\s*[:=]""", RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
            val chunk = text.substring(match.range.first, (match.range.last + window).coerceAtMost(text.length))
            Regex("""https?://[^"' <>\n\r\\]+""", RegexOption.IGNORE_CASE)
                .findAll(chunk)
                .forEach { urls += it.value.trimEnd('\\', ',', '}', ']', ')') }
        }
    }
    return urls.toList()
}

private fun extractJsonDocuments(text: String): List<String> {
    val docs = mutableListOf<String>()
    Regex("""<script[^>]*id=["']RENDER_DATA["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(text)
        .forEach { match ->
            val encoded = match.groupValues[1].trim()
            runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()?.let { docs += it.deepUnescape() }
        }
    Regex("""<script[^>]*id=["']__UNIVERSAL_DATA_FOR_REHYDRATION__["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(text)
        .forEach { docs += it.groupValues[1].trim().deepUnescape() }
    Regex("""window\.__INITIAL_STATE__\s*=\s*([\s\S]*?)\s*</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(text)
        .forEach { docs += it.groupValues[1].trim().trimEnd(';').deepUnescape() }
    val trimmed = text.trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) docs += trimmed.deepUnescape()
    return docs.distinct().take(10)
}

private fun collectDouyinFromJson(value: Any?, source: String, result: MutableMap<String, ExtractedMediaUrl>) {
    when (value) {
        is JSONObject -> {
            val names = value.names()
            if (names != null) {
                for (index in 0 until names.length()) {
                    val key = names.optString(index)
                    val child = value.opt(key)
                    when {
                        key.equals("video", ignoreCase = true) && child is JSONObject -> collectDouyinVideoObject(child, source, result)
                        key.equals("music", ignoreCase = true) && child is JSONObject -> collectDouyinAudioObject(child, source, result)
                        key.equals("images", ignoreCase = true) && child is JSONArray -> collectDouyinImages(child, source, result)
                        key.contains("cover", ignoreCase = true) -> extractJsonUrls(child).forEach { url ->
                            val normalized = url.normalizeMediaUrl()
                            if (looksLikeCoverUrl(normalized)) result[normalized] = ExtractedMediaUrl(normalized, classifyImageOrGif(normalized), source)
                        }
                    }
                    collectDouyinFromJson(child, source, result)
                }
            }
        }
        is JSONArray -> {
            for (index in 0 until value.length()) collectDouyinFromJson(value.opt(index), source, result)
        }
    }
}

private fun collectDouyinVideoObject(video: JSONObject, source: String, result: MutableMap<String, ExtractedMediaUrl>) {
    val cover = firstJsonUrl(video.opt("cover")).ifBlank {
        firstJsonUrl(video.opt("origin_cover")).ifBlank {
            firstJsonUrl(video.opt("dynamic_cover")).ifBlank { firstJsonUrl(video.opt("animated_cover")) }
        }
    }.normalizeMediaUrl()
    val width = video.optInt("width", 0)
    val height = video.optInt("height", 0)
    listOf("download_addr", "play_addr", "play_addr_h264", "play_addr_265", "bit_rate").forEach { key ->
        val child = video.opt(key)
        if (key == "bit_rate" && child is JSONArray) {
            for (index in 0 until child.length()) {
                val bitrateObject = child.optJSONObject(index)
                listOf("play_addr", "play_addr_265", "play_addr_h264").forEach { addressKey ->
                    val address = bitrateObject?.opt(addressKey)
                    val dataSize = extractDataSize(address).takeIf { it > 0 } ?: bitrateObject?.optLong("data_size", 0L).orZero()
                    extractJsonUrls(address).forEach { addVideoUrl(it, source, cover, result, width, height, dataSize) }
                }
            }
        } else {
            val dataSize = extractDataSize(child)
            extractJsonUrls(child).forEach { addVideoUrl(it, source, cover, result, width, height, dataSize) }
        }
    }
}

private fun collectDouyinAudioObject(music: JSONObject, source: String, result: MutableMap<String, ExtractedMediaUrl>) {
    listOf("play_url", "download_url").forEach { key ->
        extractJsonUrls(music.opt(key)).forEach { raw ->
            val url = raw.normalizeMediaUrl()
            if (url.startsWith("http")) result[url] = ExtractedMediaUrl(url, MediaType.Audio, source)
        }
    }
}

private fun collectDouyinImages(images: JSONArray, source: String, result: MutableMap<String, ExtractedMediaUrl>) {
    for (index in 0 until images.length()) {
        val image = images.optJSONObject(index) ?: continue
        val width = image.optInt("width", 0)
        val height = image.optInt("height", 0)
        listOf("display_image", "download_url", "url", "image", "origin_image").forEach { key ->
            extractJsonUrls(image.opt(key)).forEach { raw ->
                val url = raw.normalizeMediaUrl()
                if (url.startsWith("http") && isRelevantDouyinImageUrl(url)) {
                    result[url] = ExtractedMediaUrl(url, classifyImageOrGif(url), "$source 图文", width = width, height = height)
                }
            }
        }
    }
}

private fun addVideoUrl(raw: String, source: String, cover: String, result: MutableMap<String, ExtractedMediaUrl>, width: Int = 0, height: Int = 0, byteSize: Long = 0L) {
    val url = raw.normalizeMediaUrl()
    if (url.startsWith("http") && !looksLikeAudioUrl(url) && isHighConfidenceVideoUrl(url)) {
        result[url] = ExtractedMediaUrl(url, MediaType.Video, source, coverUrl = cover, width = width, height = height, byteSize = byteSize)
    }
}

private fun selectBestDouyinVideos(items: List<ExtractedMediaUrl>): List<ExtractedMediaUrl> {
    val videos = items.filter { it.type == MediaType.Video }
    if (videos.size <= 1) return items.sortedBy { mediaRank(it.type) }
    val bestVideo = videos.maxByOrNull { videoQualityScore(it) } ?: videos.first()
    return (listOf(bestVideo) + items.filterNot { it.type == MediaType.Video })
        .distinctBy { it.url }
        .sortedBy { mediaRank(it.type) }
}

private fun videoQualityScore(item: ExtractedMediaUrl): Long {
    val clean = item.url.lowercase(Locale.US)
    var score = 0L
    if (item.width > 0 && item.height > 0) score += item.width.toLong() * item.height.toLong()
    if (item.byteSize > 0L) score += (item.byteSize / 1024L).coerceAtMost(300_000L)
    if (item.source.contains("详情接口")) score += 80_000L
    if (clean.contains("download")) score += 60_000L
    if (clean.contains("play_addr")) score += 40_000L
    if (clean.contains("mime_type=video")) score += 30_000L
    if (clean.contains("ratio=1080p") || clean.contains("1080")) score += 20_000L
    if (clean.contains("720")) score += 10_000L
    if (clean.contains("playwm")) score -= 60_000L
    if (clean.contains("watermark")) score -= 60_000L
    if (looksLikeAudioUrl(clean)) score -= 1_000_000L
    return score
}

private fun List<MediaItem>.collapseDuplicateDouyinVideos(): List<MediaItem> {
    val videos = filter { it.type == MediaType.Video }
    if (videos.size <= 1) return this
    val nonVideos = filterNot { it.type == MediaType.Video }
    val bestVideo = videos.maxByOrNull { videoQualityScore(it.url, it.source) } ?: videos.first()
    return (listOf(bestVideo) + nonVideos).sortedBy { mediaRank(it.type) }
}

private fun videoQualityScore(url: String, source: String): Int {
    val clean = url.lowercase(Locale.US)
    var score = 0
    if (source.contains("详情接口")) score += 20
    if (clean.contains("download")) score += 80
    if (clean.contains("play_addr")) score += 60
    if (clean.contains("mime_type=video")) score += 30
    if (clean.contains("ratio=1080p") || clean.contains("1080")) score += 15
    if (clean.contains("720")) score += 10
    if (clean.contains("playwm")) score -= 20
    if (clean.contains("watermark")) score -= 20
    if (looksLikeAudioUrl(clean)) score -= 1000
    return score
}

private fun ExtractedMediaUrl.mediaInfoText(): String {
    val parts = mutableListOf<String>()
    if (width > 0 && height > 0) parts += "${width}x${height}"
    if (byteSize > 0) parts += formatBytes(byteSize)
    return parts.joinToString(" · ")
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

private fun extractDataSize(value: Any?): Long {
    return when (value) {
        is JSONObject -> value.optLong("data_size", 0L).takeIf { it > 0 }
            ?: value.optLong("size", 0L).takeIf { it > 0 }
            ?: value.optLong("file_size", 0L).takeIf { it > 0 }
            ?: 0L
        else -> 0L
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun isRelevantDouyinImageUrl(url: String): Boolean {
    val clean = url.lowercase(Locale.US)
    if (!looksLikeCoverUrl(clean)) return false
    val noisy = listOf("avatar", "head", "profile", "user", "emoji", "sticker", "icon", "logo", "comment", "follow", "share")
    if (noisy.any { clean.contains(it) }) return false
    return clean.contains("byteimg") ||
        clean.contains("imagex") ||
        clean.contains("tos-cn-i") ||
        clean.endsWith(".jpg") ||
        clean.endsWith(".jpeg") ||
        clean.endsWith(".png") ||
        clean.endsWith(".webp")
}

private fun extractJsonUrls(value: Any?): List<String> {
    val urls = mutableListOf<String>()
    when (value) {
        is JSONObject -> {
            value.optJSONArray("url_list")?.let { array ->
                for (index in 0 until array.length()) array.optString(index).takeIf { it.startsWith("http") }?.let { urls += it }
            }
            value.optString("url").takeIf { it.startsWith("http") }?.let { urls += it }
            value.optString("uri").takeIf { it.startsWith("http") }?.let { urls += it }
            val names = value.names()
            if (names != null) {
                for (index in 0 until names.length()) {
                    val key = names.optString(index)
                    if (key.contains("url", ignoreCase = true) || key.contains("addr", ignoreCase = true) || key.contains("cover", ignoreCase = true)) {
                        urls += extractJsonUrls(value.opt(key))
                    }
                }
            }
        }
        is JSONArray -> {
            for (index in 0 until value.length()) urls += extractJsonUrls(value.opt(index))
        }
        is String -> if (value.startsWith("http")) urls += value
    }
    return urls.map { it.normalizeMediaUrl() }.distinct()
}

private fun firstJsonUrl(value: Any?): String = extractJsonUrls(value).firstOrNull().orEmpty()

private fun extractDouyinMediaCandidates(text: String, source: String): List<ExtractedMediaUrl> {
    val result = linkedMapOf<String, ExtractedMediaUrl>()
    Regex("""https?://[^"' <>\n\r\\]+""", RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
        val rawUrl = match.value.trimEnd('\\', ',', '}', ']', ')').normalizeMediaUrl()
        val before = text.substring((match.range.first - 260).coerceAtLeast(0), match.range.first).lowercase(Locale.US)
        val after = text.substring(match.range.last.coerceAtMost(text.lastIndex), (match.range.last + 120).coerceAtMost(text.length)).lowercase(Locale.US)
        val context = before + after
        val type = when {
            looksLikeCoverUrl(rawUrl) || context.contains("cover") || context.contains("origin_cover") || context.contains("dynamic_cover") -> classifyImageOrGif(rawUrl)
            context.contains("music") || context.contains("audio") || context.contains("play_url") || looksLikeAudioUrl(rawUrl) -> MediaType.Audio
            context.contains("play_addr") || context.contains("download_addr") || context.contains("video") || looksLikeDouyinVideoUrl(rawUrl) -> MediaType.Video
            classifyMedia(rawUrl) == MediaType.Playlist -> MediaType.Playlist
            classifyMedia(rawUrl) == MediaType.Image || classifyMedia(rawUrl) == MediaType.Gif -> classifyMedia(rawUrl)
            classifyMedia(rawUrl) == MediaType.Audio -> MediaType.Audio
            else -> MediaType.Other
        }
        if (type != MediaType.Other && type != MediaType.Web) {
            result[rawUrl] = ExtractedMediaUrl(rawUrl, type, source)
        }
    }
    return result.values.toList()
}

private fun classifyImageOrGif(url: String): MediaType =
    if (url.substringBefore("?").endsWith(".gif", ignoreCase = true)) MediaType.Gif else MediaType.Image

private fun looksLikeCoverUrl(url: String): Boolean {
    val clean = url.lowercase(Locale.US)
    return clean.contains("cover") ||
        clean.contains("poster") ||
        clean.contains("tos-cn-i") ||
        clean.contains("byteimg") ||
        clean.contains("imagex") ||
        clean.endsWith(".jpg") ||
        clean.endsWith(".jpeg") ||
        clean.endsWith(".png") ||
        clean.endsWith(".webp")
}

private fun looksLikeAudioUrl(url: String): Boolean {
    val clean = url.lowercase(Locale.US)
    return clean.contains("/music/") ||
        clean.contains("music") ||
        clean.contains("audio") ||
        clean.contains("mime_type=audio") ||
        clean.endsWith(".mp3") ||
        clean.endsWith(".m4a") ||
        clean.endsWith(".aac")
}

private fun looksLikeDouyinVideoUrl(url: String): Boolean {
    val clean = url.lowercase(Locale.US)
    return clean.contains("douyinvod") ||
        clean.contains("/video/tos/") ||
        clean.contains("playwm") ||
        clean.contains("/play/") ||
        clean.endsWith(".mp4")
}

private fun isHighConfidenceVideoUrl(url: String): Boolean {
    val clean = url.lowercase(Locale.US)
    return !looksLikeAudioUrl(clean) && (
        clean.contains("mime_type=video") ||
            clean.contains("douyinvod") ||
            clean.contains("/video/tos/") ||
            clean.contains("video_id=") ||
            clean.contains("playwm") ||
            clean.contains("/play/") ||
            clean.endsWith(".mp4") ||
            clean.endsWith(".mov") ||
            clean.endsWith(".webm")
        )
}

private fun htmlTitle(html: String): String = metaContent(html, "og:title").ifBlank {
    Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()
}

private fun metaContent(html: String, name: String): String {
    val escaped = Regex.escape(name)
    return listOf(
        Regex("""<meta\s+(?:property|name)=["']$escaped["']\s+content=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE),
        Regex("""<meta\s+content=["']([^"']+)["']\s+(?:property|name)=["']$escaped["'][^>]*>""", RegexOption.IGNORE_CASE)
    ).firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }?.htmlDecode().orEmpty()
}

fun classifyMedia(url: String, mime: String = ""): MediaType {
    val clean = Uri.decode(url).lowercase(Locale.US).substringBefore("?")
    val type = mime.lowercase(Locale.US)
    return when {
        clean.contains("v.douyin.com") || clean.contains("www.douyin.com") && (clean.contains("/video/") || clean.contains("/note/")) -> MediaType.Web
        looksLikeAudioUrl(clean) || type.startsWith("audio/") || type.contains("audio/") || clean.endsWith(".mp3") || clean.endsWith(".aac") || clean.endsWith(".m4a") || clean.endsWith(".wav") || clean.endsWith(".flac") -> MediaType.Audio
        type.startsWith("video/") ||
            type.contains("video/") ||
            clean.endsWith(".mp4") ||
            clean.endsWith(".mov") ||
            clean.endsWith(".webm") ||
            clean.endsWith(".mkv") ||
            looksLikeDouyinVideoUrl(clean) -> MediaType.Video
        clean.endsWith(".m3u8") || clean.endsWith(".mpd") -> MediaType.Playlist
        clean.endsWith(".gif") -> MediaType.Gif
        type.startsWith("image/") ||
            type.contains("image/") ||
            clean.endsWith(".jpg") ||
            clean.endsWith(".jpeg") ||
            clean.endsWith(".png") ||
            clean.endsWith(".webp") ||
            clean.contains("byteimg") ||
            clean.contains("imagex") ||
            clean.contains("/obj/") && clean.contains("tos") -> MediaType.Image
        type.startsWith("text/html") || clean.contains("v.douyin.com") || clean.contains("/share/") -> MediaType.Web
        clean.substringAfterLast('.', "").length in 2..5 -> MediaType.File
        else -> MediaType.Other
    }
}

fun shouldKeepCandidate(url: String): Boolean {
    val terms = listOf(
        "douyin",
        "snssdk",
        "byteimg",
        "douyinvod",
        "ixigua",
        "pstatp",
        "amemv",
        "bilibili",
        "kuaishou",
        "m3u8",
        ".mp4",
        ".jpg",
        ".jpeg",
        ".png",
        ".webp",
        ".gif",
        ".mp3",
        ".aac",
        ".m4a"
    )
    return terms.any { url.contains(it, ignoreCase = true) }
}

private fun mediaRank(type: MediaType): Int = when (type) {
    MediaType.Video -> 0
    MediaType.Playlist -> 1
    MediaType.Image -> 2
    MediaType.Gif -> 3
    MediaType.Audio -> 4
    MediaType.File -> 5
    MediaType.Web -> 6
    MediaType.Other -> 7
}

fun enqueueMediaDownload(context: Context, item: MediaItem) {
    val task = item.copy(status = "保存中", progress = 3, speed = "应用下载器")
    TaskStore.add(context, task)
    AppDownloader.enqueue(context, item)
}

suspend fun resolveDownloadItems(context: Context, text: String): List<MediaItem> {
    val settings = AppSettings.load(context)
    val parsed = parsePlatform(text)
    return parsed.items
        .filter { settings.allows(it.type) }
        .distinctBy { it.url }
        .take(30)
}

fun openPreview(context: Context, item: MediaItem) {
    context.startActivity(Intent(context, PreviewActivity::class.java).apply {
        putExtra(EXTRA_URL, item.url)
        putExtra(EXTRA_MEDIA_TYPE, item.type.name)
        putExtra(EXTRA_TITLE, item.title.ifBlank { item.fileName })
        putExtra(EXTRA_SIZE, item.size)
    })
}

private fun openSupportList(context: Context) {
    toast(context, "支持：抖音、小红书、微博、B 站等公开网页；失败时可用任意门嗅探。")
}

private suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
    try {
        val connection = (URL("https://api.github.com/repos/7116-byte/EasyDownloadLocal/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "EasyDownloadLocal/$CURRENT_VERSION_NAME")
        }
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val tag = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1).orEmpty()
        val pageUrl = Regex(""""html_url"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1).orEmpty()
        val apkUrl = Regex(""""browser_download_url"\s*:\s*"([^"]*\.apk)"""").find(body)?.groupValues?.getOrNull(1).orEmpty()
        UpdateInfo(tag.ifBlank { "未知" }, pageUrl, apkUrl, versionCodeFromTag(tag) > CURRENT_VERSION_CODE)
    } catch (e: Exception) {
        UpdateInfo("", "", "", false, e.message ?: "无法连接 GitHub")
    }
}

private fun versionCodeFromTag(tag: String): Int {
    val parts = tag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    return parts.getOrElse(0) { 0 } * 10000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
}

fun fileNameFor(url: String, type: MediaType): String {
    val decoded = runCatching { Uri.decode(url) }.getOrElse { url }
    val last = decoded.substringBefore("?").trimEnd('/').substringAfterLast('/').substringBefore("#")
    val fallbackExt = when (type) {
        MediaType.Video -> ".mp4"
        MediaType.Image -> ".jpg"
        MediaType.Gif -> ".gif"
        MediaType.Audio -> ".mp3"
        MediaType.Playlist -> ".m3u8"
        else -> ""
    }
    val base = last
        .takeIf { it.isNotBlank() && it.length < 90 && !it.contains("=") && !it.contains("&") }
        ?: "resource-${System.currentTimeMillis()}"
    val cleanBase = base.substringBefore("?").trimEnd('.', '_', '-')
    val ext = cleanBase.substringAfterLast('.', "").lowercase(Locale.US)
    val knownExts = setOf("mp4", "mov", "webm", "mkv", "jpg", "jpeg", "png", "webp", "gif", "mp3", "aac", "m4a", "wav", "flac", "m3u8")
    return if (fallbackExt.isNotBlank() && ext !in knownExts) "$cleanBase$fallbackExt" else cleanBase
}

fun typeIcon(type: MediaType): String = when (type) {
    MediaType.Video -> "▶"
    MediaType.Image -> "图"
    MediaType.Gif -> "GIF"
    MediaType.Audio -> "♪"
    MediaType.Playlist -> "M3U8"
    MediaType.Web -> "页"
    MediaType.File -> "文"
    MediaType.Other -> "?"
}

private fun String.unescapeUrl(): String = deepUnescape().replace("&amp;", "&")

private fun String.deepUnescape(): String {
    var value = this
    repeat(3) {
        value = value
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("\\/", "/")
            .replace("%5Cu002F", "/", ignoreCase = true)
            .replace("%5C/", "/", ignoreCase = true)
            .replace("&amp;", "&")
    }
    return value
}

private fun String.normalizeMediaUrl(): String {
    val decoded = unescapeUrl()
    return if (decoded.contains("douyin", ignoreCase = true) || decoded.contains("douyinvod", ignoreCase = true)) {
        decoded.replace("playwm", "play")
    } else {
        decoded
    }
}

private fun String.htmlDecode(): String = replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")

fun String.safeFileName(): String {
    val sanitized = replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank {
        SimpleDateFormat("'download-'yyyyMMdd-HHmmss", Locale.US).format(Date())
    }
    if (sanitized.length <= 80) return sanitized
    val ext = sanitized.substringAfterLast('.', "").takeIf { it.length in 2..5 }
    return if (ext == null) {
        sanitized.take(80)
    } else {
        sanitized.substringBeforeLast('.').take(80 - ext.length - 1).trimEnd('.', '_', '-') + ".$ext"
    }
}

fun mimeTypeFor(type: MediaType): String = when (type) {
    MediaType.Video -> "video/mp4"
    MediaType.Image -> "image/jpeg"
    MediaType.Gif -> "image/gif"
    MediaType.Audio -> "audio/mpeg"
    MediaType.Playlist -> "application/vnd.apple.mpegurl"
    MediaType.Web -> "text/html"
    else -> "application/octet-stream"
}

private fun clipboardText(context: Context): String =
    context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

fun parseIntentForText(context: Context): String = clipboardText(context)

private fun startFloatingWindow(context: Context) {
    if (!Settings.canDrawOverlays(context)) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        toast(context, "请先允许悬浮窗权限")
        return
    }
    context.startService(Intent(context, FloatingWindowService::class.java))
    toast(context, "悬浮窗已开启")
}

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", text))
    toast(context, "已复制链接")
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { toast(context, "无法打开链接") }
}

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

const val EXTRA_PARSE_TEXT = "com.local.easydownload.PARSE_TEXT"
const val EXTRA_URL = "com.local.easydownload.URL"
const val EXTRA_MEDIA_TYPE = "com.local.easydownload.MEDIA_TYPE"
const val EXTRA_TITLE = "com.local.easydownload.TITLE"
const val EXTRA_SIZE = "com.local.easydownload.SIZE"

private const val CURRENT_VERSION_NAME = "1.32"
private const val CURRENT_VERSION_CODE = 13200
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
