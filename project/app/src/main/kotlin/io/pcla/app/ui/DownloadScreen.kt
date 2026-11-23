package io.pcla.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.GameInstaller
import io.pcla.app.ui.theme.Ui
import io.pcla.core.download.DownloadConfig
import io.pcla.core.download.DownloadQueue
import io.pcla.core.download.DownloadTask
import io.pcla.core.download.OverallProgress
import io.pcla.core.download.TaskResult
import io.pcla.core.version.VersionEntry
import io.pcla.core.version.VersionManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private enum class DlTab(val label: String) {
    GAME("游戏"),
    MODPACK("整合包"),
    MOD("模组"),
    RESOURCE("资源包"),
    SHADER("光影"),
    LINK("链接"),
}

private enum class VersionFilter(val label: String) {
    RELEASE("正式版"),
    SNAPSHOT("快照"),
    ALL("全部"),
}

/**
 * 下载 tab, modelled on FCL's download page: category chips, search, version filter and a paged
 * list. Only the 游戏 category is wired to a real source (the version manifest); the others state
 * that plainly instead of showing a fake empty list. 链接 keeps the direct-URL queue.
 */
@Composable
fun DownloadScreen(heapMb: Int) {
    var tab by remember { mutableStateOf(DlTab.GAME) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        PageHeader("下载", "分类 · 搜索 · 一键安装")
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DlTab.entries.forEach { item ->
                CategoryChip(item.label, tab == item) { tab = item }
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            DlTab.GAME -> GameVersionBrowser(heapMb)
            DlTab.LINK -> LinkQueue()
            else -> PendingTab(tab) { tab = DlTab.GAME }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Ui.Lime else Ui.Surface)
            .border(1.dp, if (selected) Ui.Lime else Ui.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Ui.OnLime else Ui.TextMuted,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun GameVersionBrowser(heapMb: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var versions by remember { mutableStateOf<List<VersionEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VersionFilter.RELEASE) }
    var page by remember { mutableStateOf(0) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installing by remember { mutableStateOf<String?>(null) }
    var installStage by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(retry) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { VersionCatalog.load() } }
            .onSuccess {
                versions = it
                installed = it.mapNotNull { v -> v.id.takeIf { id -> isInstalled(context, id) } }.toSet()
                loading = false
            }
            .onFailure {
                error = it.message ?: it.javaClass.simpleName
                loading = false
            }
    }
    LaunchedEffect(query, filter) { page = 0 }

    val filtered = remember(versions, query, filter) {
        versions.filter { v ->
            val typeOk = when (filter) {
                VersionFilter.ALL -> true
                VersionFilter.RELEASE -> v.type == "release"
                VersionFilter.SNAPSHOT -> v.type == "snapshot"
            }
            typeOk && (query.isBlank() || v.id.contains(query.trim(), ignoreCase = true))
        }
    }
    val pageSize = 20
    val pageCount = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
    val current = page.coerceIn(0, pageCount - 1)
    val shown = filtered.drop(current * pageSize).take(pageSize)

    fun install(id: String) {
        if (installing != null) return
        installing = id
        installStage = "0%"
        installError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    GameInstaller.install(context, id, heapMb) { p ->
                        installStage = if (p.total > 0) "${p.done * 100 / p.total}%" else p.stage.take(12)
                    }
                }
            }.onSuccess {
                installed = installed + id
            }.onFailure {
                installError = "安装 $id 失败：" + (it.message ?: it.javaClass.simpleName)
            }
            installing = null
            installStage = ""
        }
    }

    PclCard {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text("搜索版本号，例如 1.20.1", fontSize = 13.sp) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            VersionFilter.entries.forEach { f ->
                MiniButton(f.label, enabled = true, active = filter == f) { filter = f }
            }
            Spacer(Modifier.weight(1f))
            Text("${filtered.size} 个", fontSize = 10.5.sp, color = Ui.TextFaint)
        }
        if (installing != null) {
            Text("正在安装 $installing · $installStage", fontSize = 10.5.sp, color = Ui.Lime)
        }
        installError?.let { Text(it, fontSize = 10.5.sp, color = Ui.Red) }
    }

    Spacer(Modifier.height(12.dp))

    when {
        loading -> PclCard {
            Text("正在读取版本清单…", fontSize = 12.sp, color = Ui.TextMuted)
        }
        error != null -> PclCard {
            Text("读取清单失败：$error", fontSize = 12.sp, color = Ui.TextMuted)
            PclButton("重试", onClick = { retry++ }, height = 42)
        }
        else -> {
            if (shown.isEmpty()) {
                Text("没有匹配的版本。", fontSize = 12.sp, color = Ui.TextFaint, modifier = Modifier.padding(vertical = 16.dp))
            }
            Column {
                shown.forEachIndexed { index, v ->
                    GameVersionRow(
                        entry = v,
                        cubeColor = CubePalette[index % CubePalette.size],
                        installed = installed.contains(v.id),
                        installing = installing == v.id,
                        stage = installStage,
                        onInstall = { install(v.id) },
                    )
                    if (index != shown.lastIndex) PclDivider()
                }
            }
            if (filtered.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${current + 1} / $pageCount 页", fontSize = 10.5.sp, color = Ui.TextFaint)
                    Spacer(Modifier.weight(1f))
                    MiniButton("上一页", enabled = current > 0) { page = current - 1 }
                    Spacer(Modifier.width(8.dp))
                    MiniButton("下一页", enabled = current < pageCount - 1) { page = current + 1 }
                }
            }
        }
    }
}

@Composable
private fun GameVersionRow(
    entry: VersionEntry,
    cubeColor: androidx.compose.ui.graphics.Color,
    installed: Boolean,
    installing: Boolean,
    stage: String,
    onInstall: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionCube(cubeColor, 38.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.id, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ui.Text)
                Spacer(Modifier.width(7.dp))
                PclTag(typeLabel(entry.type), if (entry.type == "release") TagTone.LIME else TagTone.OUTLINE)
            }
            Text(
                (entry.releaseTime?.take(10) ?: "日期未知") + " · " + typeLabel(entry.type),
                fontSize = 10.sp,
                color = Ui.TextFaint,
            )
        }
        when {
            installed -> PclTag("已安装", TagTone.GRAY)
            installing -> Text(stage, fontSize = 10.5.sp, color = Ui.Lime)
            else -> PclButton(
                "安装",
                onClick = onInstall,
                modifier = Modifier.width(76.dp),
                height = 36,
                highlight = true,
            )
        }
    }
}

@Composable
private fun PendingTab(tab: DlTab, onOpenGame: () -> Unit) {
    PclCard("${tab.label}还没接入") {
        Text(
            "这个分类需要 CurseForge / Modrinth 的搜索、版本筛选和依赖解析，这一版没有假装有列表。",
            fontSize = 12.sp,
            color = Ui.TextMuted,
            lineHeight = 17.sp,
        )
        Text(
            "现在能用的是「游戏」里的版本下载，和「链接」里的直链队列。",
            fontSize = 11.sp,
            color = Ui.TextFaint,
        )
        PclButton("去看游戏版本", onClick = onOpenGame, height = 44)
    }
}

/** 链接分类：原来的多文件直链队列，逻辑不动，只换了外壳。 */
@Composable
private fun LinkQueue() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    val meter = remember { SpeedMeter() }

    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var cancelFlag by remember { mutableStateOf(false) }
    var overall by remember { mutableStateOf(OverallProgress(0, 0, 0, 0)) }
    var speedText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    val rows = remember { mutableStateListOf<Pair<String, String>>() }

    fun start() {
        val tasks = parseTasks(context, input)
        if (tasks.isEmpty()) {
            note = "没识别到 http/https 地址。一行一个，例如：\nhttps://bmclapi2.bangbang93.com/…/client.jar"
            return
        }
        rows.clear()
        tasks.forEach { rows.add(it.label to "排队中") }
        note = null
        running = true
        cancelFlag = false
        speedText = ""
        meter.reset()
        overall = OverallProgress(0, tasks.size, 0, tasks.sumOf { it.sizeHint ?: 0L })

        scope.launch {
            val queue = DownloadQueue(DownloadConfig(), maxConcurrent = 2) { msg ->
                android.util.Log.d("PCLA-DL", msg)
            }
            val results = withContext(Dispatchers.IO) {
                queue.run(
                    tasks = tasks,
                    onOverall = { o ->
                        val sp = meter.sample(o.doneBytes)
                        main.post {
                            overall = o
                            if (sp > 0) speedText = humanSpeed(sp)
                        }
                    },
                    onTaskDone = { r ->
                        val label = taskOf(r).label
                        main.post {
                            val i = rows.indexOfFirst { it.first == label }
                            if (i >= 0) rows[i] = label to statusOf(r)
                        }
                    },
                    isCancelled = { cancelFlag },
                )
            }
            val ok = results.count { it is TaskResult.Ok }
            val bad = results.count { it is TaskResult.Err || it is TaskResult.Bad }
            val skipped = results.count { it is TaskResult.Skipped }
            note = "结束：成功 $ok" +
                (if (bad > 0) "，失败 $bad" else "") +
                (if (skipped > 0) "，跳过 $skipped" else "") +
                "。文件在 ${downloadDir(context).absolutePath}"
            running = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PclCard("下载队列") {
            Text(
                "一行一个地址，地址后面空一格可以跟 SHA-1。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { if (!running) input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("https://…/client.jar", fontSize = 13.sp) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PclButton(
                    text = if (running) "下载中…" else "开始下载",
                    onClick = { start() },
                    modifier = Modifier.weight(1f),
                    highlight = true,
                    enabled = !running && input.isNotBlank(),
                )
                if (running) {
                    PclButton(
                        text = "取消",
                        onClick = { cancelFlag = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (overall.totalTasks > 0) {
                ProgressBar(overall.percent / 100f)
                Text(
                    "${overall.doneTasks}/${overall.totalTasks} 个文件 · ${humanBytes(overall.doneBytes)}" +
                        (if (speedText.isNotBlank()) " · $speedText" else ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            note?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (rows.isNotEmpty()) {
            PclCard("任务") {
                rows.forEach { (label, status) ->
                    PclRow {
                        Text(label, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                        Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        PclCard("说明") {
            Text(
                "· 小于 10MB 单连接，10–60MB 默认 4 分片，超过 60MB 动态加片。\n" +
                    "· 分片数改得动，但别调到 16 以上——这台机器实测 6 线程就开始吃 429。\n" +
                    "· 下载完成会校验 SHA-1；不匹配时坏文件不覆盖原文件，会先停下来问你。\n" +
                    "· 文件落在应用私有目录，暂时不影响文件管理器里的 Download 文件夹。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Ui.Stroke),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Ui.Lime),
        )
    }
}

@Composable
private fun MiniButton(text: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) Ui.Lime else Ui.Surface)
            .border(1.dp, if (active) Ui.Lime else Ui.Stroke, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = when {
                active -> Ui.OnLime
                enabled -> Ui.TextMuted
                else -> Ui.TextFaint
            },
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

private fun typeLabel(type: String): String = when (type) {
    "release" -> "正式版"
    "snapshot" -> "快照"
    "old_beta", "old_alpha" -> "远古版"
    else -> type
}

private fun isInstalled(context: android.content.Context, id: String): Boolean =
    File(context.filesDir, "game/versions/$id/$id.json").isFile

/** Version manifest source used by the 游戏 category. Cached for the process lifetime. */
private object VersionCatalog {
    private const val BMCL = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json"
    private const val MOJANG = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    private var cached: List<VersionEntry>? = null

    suspend fun load(): List<VersionEntry> = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        var last: Throwable? = null
        for (url in listOf(BMCL, MOJANG)) {
            try {
                val entries = VersionManifest.parse(fetchText(url))
                if (entries.isNotEmpty()) {
                    cached = entries
                    return@withContext entries
                }
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("版本清单为空")
    }

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PCLA/0.7")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

private fun taskOf(r: TaskResult): DownloadTask = when (r) {
    is TaskResult.Ok -> r.task
    is TaskResult.Bad -> r.task
    is TaskResult.Err -> r.task
    is TaskResult.Skipped -> r.task
}

private fun statusOf(r: TaskResult): String = when (r) {
    is TaskResult.Ok -> "完成 · ${humanBytes(r.bytes)}" + (if (r.sha1 != null) " · 校验通过" else "")
    is TaskResult.Bad -> "SHA-1 不匹配"
    is TaskResult.Err -> "失败：${r.reason.take(40)}"
    is TaskResult.Skipped -> "跳过：${r.reason}"
}

/** One URL per line; `url sha1` separated by a space. */
private fun parseTasks(context: android.content.Context, raw: String): List<DownloadTask> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            val url = parts[0]
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
            val sha = parts.getOrNull(1)?.trim()?.takeIf { it.length in 40..45 }
            DownloadTask(url = url, dest = destFile(context, url), sha1 = sha)
        }
        .distinctBy { it.dest.absolutePath }
        .toList()

private class SpeedMeter {
    private var lastBytes = 0L
    private var lastAt = 0L

    fun reset() {
        lastBytes = 0L
        lastAt = 0L
    }

    /** Returns instantaneous speed (B/s); returns 0 on too-short sampling intervals so the number does not jitter. */
    fun sample(done: Long): Double {
        val now = System.currentTimeMillis()
        if (lastAt == 0L) {
            lastAt = now
            lastBytes = done
            return 0.0
        }
        val dt = (now - lastAt) / 1000.0
        if (dt < 0.3) return 0.0
        val speed = (done - lastBytes) / dt
        lastBytes = done
        lastAt = now
        return speed
    }
}

private fun humanSpeed(bytesPerSecond: Double): String = when {
    bytesPerSecond >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / 1024 / 1024)
    bytesPerSecond >= 1024 -> String.format("%.0f KB/s", bytesPerSecond / 1024)
    else -> String.format("%.0f B/s", bytesPerSecond)
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun downloadDir(context: android.content.Context): File =
    File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

private fun destFile(context: android.content.Context, url: String): File {
    val raw = url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
    val safe = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    return File(downloadDir(context), safe)
}
