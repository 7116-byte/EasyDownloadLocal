package com.local.easydownload

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            .orEmpty()

        setContent {
            EasyDownloadApp(initialText = sharedText)
        }
    }
}

private data class ParsedPage(
    val inputUrl: String,
    val finalUrl: String,
    val title: String,
    val description: String,
    val candidates: List<String>,
    val error: String? = null
)

private data class DownloadRow(
    val id: Long,
    val title: String,
    val status: String,
    val reason: String,
    val uri: String
)

private val AppColors = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFF1565C0),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White
)

@Composable
private fun EasyDownloadApp(initialText: String) {
    MaterialTheme(colorScheme = AppColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var tab by remember { mutableStateOf(0) }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        0 -> HomeScreen(initialText)
                        1 -> DownloadScreen()
                        2 -> ToolsScreen()
                        else -> MineScreen()
                    }
                }
                NavigationBar(containerColor = Color.White) {
                    listOf("首页", "任务", "工具", "我的").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("↓", "≡", "◇", "我")[index]) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(initialText: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialText) }
    var busy by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<ParsedPage?>(null) }
    var message by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            HeaderBlock()
        }
        item {
            SectionCard {
                Text("链接解析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    label = { Text("粘贴分享文本或网页链接") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    minLines = 5
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            message = ""
                            scope.launch {
                                parsed = parsePage(input)
                                busy = false
                            }
                        }
                    ) { Text(if (busy) "解析中" else "解析") }
                    OutlinedButton(onClick = {
                        val first = extractFirstUrl(input)
                        if (first == null) {
                            toast(context, "没有找到链接")
                        } else {
                            enqueueDownload(context, first)
                        }
                    }) { Text("直接下载") }
                    TextButton(onClick = {
                        input = ""
                        parsed = null
                    }) { Text("清空") }
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        parsed?.let { page ->
            item {
                SectionCard {
                    if (page.error != null) {
                        Text("解析失败", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        Spacer(Modifier.height(6.dp))
                        Text(page.error)
                    } else {
                        ResultLine("原始链接", page.inputUrl)
                        ResultLine("跳转后", page.finalUrl)
                        ResultLine("标题", page.title.ifBlank { "未识别" })
                        ResultLine("描述", page.description.ifBlank { "未识别" })
                    }
                }
            }
            if (page.candidates.isNotEmpty()) {
                item {
                    Text("候选资源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(page.candidates) { url ->
                    CandidateRow(
                        url = url,
                        onCopy = {
                            copyText(context, url)
                            toast(context, "已复制")
                        },
                        onDownload = { enqueueDownload(context, url) }
                    )
                }
            }
        }
        item {
            SectionCard {
                Text("本地版说明", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("无广告、无登录、无付费入口。解析只读取公开网页内容，下载交给系统下载器处理。")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun DownloadScreen() {
    val context = LocalContext.current
    val rows = remember { mutableStateListOf<DownloadRow>() }

    fun refresh() {
        rows.clear()
        rows.addAll(queryDownloads(context))
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("下载任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { refresh() }) { Text("刷新") }
                OutlinedButton(onClick = { openDownloads(context) }) { Text("打开下载目录") }
            }
        }
        if (rows.isEmpty()) {
            item {
                SectionCard {
                    Text("暂无系统下载记录")
                    Spacer(Modifier.height(4.dp))
                    Text("从首页点击下载后，任务会显示在这里。", color = Color(0xFF667085))
                }
            }
        } else {
            items(rows) { row ->
                SectionCard {
                    Text(row.title, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("${row.status} ${row.reason}".trim(), color = Color(0xFF667085))
                    if (row.uri.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(row.uri, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hashText by remember { mutableStateOf("请选择文件") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                hashText = "计算中..."
                hashText = fileHashReport(context, uri)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("本地工具", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard {
            Text("文件校验", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(hashText)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { filePicker.launch("*/*") }) { Text("选择文件") }
        }
        ToolGrid(
            listOf(
                "短链解析" to "解析跳转、标题和候选资源",
                "系统下载" to "调用系统下载器保存文件",
                "文件哈希" to "计算 MD5 与 SHA-256",
                "下载目录" to "快速打开系统下载位置",
                "文本清理" to "从分享文案中抽取链接",
                "本地优先" to "不含广告 SDK 和支付 SDK"
            )
        )
    }
}

@Composable
private fun MineScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard {
            Text("便捷下载本地版", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("版本 1.21")
            Text("本地解析、本地下载、无订阅、无广告。")
        }
        SectionCard {
            Text("隐私", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("应用不会收集账号信息，不集成广告 ID，不包含支付入口。网络请求仅由你输入的链接触发。")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { openDownloads(context) }) { Text("下载目录") }
            OutlinedButton(onClick = { shareAppText(context) }) { Text("分享说明") }
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        Text("便捷下载", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("本地版", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Text(label, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
    Text(value)
    Spacer(Modifier.height(8.dp))
    Divider(color = Color(0xFFE5E7EB))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CandidateRow(url: String, onCopy: () -> Unit, onDownload: () -> Unit) {
    SectionCard {
        Text(url, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDownload) { Text("下载") }
            OutlinedButton(onClick = onCopy) { Text("复制") }
        }
    }
}

@Composable
private fun ToolGrid(items: List<Pair<String, String>>) {
    items.forEach { item ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
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

private suspend fun parsePage(input: String): ParsedPage = withContext(Dispatchers.IO) {
    val url = extractFirstUrl(input)
    if (url == null) {
        return@withContext ParsedPage("", "", "", "", emptyList(), "没有找到 http/https 链接")
    }
    try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
        }
        val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val finalUrl = connection.url.toString()
        ParsedPage(
            inputUrl = url,
            finalUrl = finalUrl,
            title = htmlTitle(html),
            description = metaContent(html, "description").ifBlank { metaContent(html, "og:description") },
            candidates = candidateUrls(html, finalUrl)
        )
    } catch (e: Exception) {
        ParsedPage(url, url, "", "", emptyList(), e.message ?: "网络请求失败")
    }
}

private fun extractFirstUrl(text: String): String? {
    return Regex("""https?://[^\s"'<>]+""")
        .find(text)
        ?.value
        ?.trimEnd('.', ',', ';', ')', ']', '}')
}

private fun htmlTitle(html: String): String {
    val og = metaContent(html, "og:title")
    if (og.isNotBlank()) return og
    return Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()
}

private fun metaContent(html: String, name: String): String {
    val escaped = Regex.escape(name)
    val patterns = listOf(
        Regex("""<meta\s+(?:property|name)=["']$escaped["']\s+content=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE),
        Regex("""<meta\s+content=["']([^"']+)["']\s+(?:property|name)=["']$escaped["'][^>]*>""", RegexOption.IGNORE_CASE)
    )
    return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
        ?.htmlDecode()
        .orEmpty()
}

private fun candidateUrls(html: String, finalUrl: String): List<String> {
    val hostTerms = listOf("douyin", "snssdk", "byteimg", "douyinvod", "ixigua", "pstatp", "amemv", "bilibili", "kuaishou")
    val values = mutableListOf<String>()
    Regex("""https?:\\?/\\?/[^"' <>\n\r]+|https?://[^"' <>\n\r]+""", RegexOption.IGNORE_CASE)
        .findAll(html)
        .map { it.value.unescapeUrl() }
        .forEach { found ->
            if (hostTerms.any { found.contains(it, ignoreCase = true) } && found !in values) {
                values += found
            }
        }
    if (finalUrl !in values) values.add(0, finalUrl)
    return values.take(40)
}

private fun String.unescapeUrl(): String {
    return replace("\\u0026", "&")
        .replace("\\/", "/")
        .replace("&amp;", "&")
}

private fun String.htmlDecode(): String {
    return replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}

private fun enqueueDownload(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val name = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "download-${System.currentTimeMillis()}"
        val request = DownloadManager.Request(uri)
            .setTitle(name)
            .setDescription(url)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name.safeFileName())
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.enqueue(request)
        toast(context, "已加入下载任务")
    } catch (e: Exception) {
        toast(context, e.message ?: "无法下载")
    }
}

private fun queryDownloads(context: Context): List<DownloadRow> {
    val manager = context.getSystemService(DownloadManager::class.java)
    val query = DownloadManager.Query()
    val rows = mutableListOf<DownloadRow>()
    manager.query(query)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
        val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
        while (cursor.moveToNext() && rows.size < 50) {
            val status = cursor.getInt(statusCol)
            rows += DownloadRow(
                id = cursor.getLong(idCol),
                title = cursor.getString(titleCol).orEmpty().ifBlank { "未命名任务" },
                status = status.label(),
                reason = cursor.getInt(reasonCol).reasonLabel(status),
                uri = cursor.getString(uriCol).orEmpty()
            )
        }
    }
    return rows.sortedByDescending { it.id }
}

private suspend fun fileHashReport(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    } ?: uri.lastPathSegment.orEmpty()
    val md5 = MessageDigest.getInstance("MD5")
    val sha = MessageDigest.getInstance("SHA-256")
    context.contentResolver.openInputStream(uri)?.use { raw ->
        BufferedInputStream(raw).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md5.update(buffer, 0, read)
                sha.update(buffer, 0, read)
            }
        }
    }
    "文件：$name\nMD5：${md5.digest().hex()}\nSHA-256：${sha.digest().hex()}"
}

private fun Int.label(): String {
    return when (this) {
        DownloadManager.STATUS_PENDING -> "等待中"
        DownloadManager.STATUS_RUNNING -> "下载中"
        DownloadManager.STATUS_PAUSED -> "已暂停"
        DownloadManager.STATUS_SUCCESSFUL -> "已完成"
        DownloadManager.STATUS_FAILED -> "失败"
        else -> "未知"
    }
}

private fun Int.reasonLabel(status: Int): String {
    if (status != DownloadManager.STATUS_FAILED && status != DownloadManager.STATUS_PAUSED) return ""
    return when (this) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi"
        DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "存储不可用"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP 数据错误"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "空间不足"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "跳转过多"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP 错误"
        else -> ""
    }
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

private fun String.safeFileName(): String {
    val cleaned = replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
    return cleaned.ifBlank {
        SimpleDateFormat("'download-'yyyyMMdd-HHmmss", Locale.US).format(Date())
    }
}

private fun copyText(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager.setPrimaryClip(ClipData.newPlainText("link", text))
}

private fun openDownloads(context: Context) {
    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { toast(context, "无法打开下载目录") }
}

private fun shareAppText(context: Context) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "便捷下载本地版：无广告、无登录、无付费入口。")
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
