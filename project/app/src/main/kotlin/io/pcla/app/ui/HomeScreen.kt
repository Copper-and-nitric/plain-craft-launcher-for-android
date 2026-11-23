package io.pcla.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.GameActivity
import io.pcla.app.GameInstaller
import io.pcla.app.R
import io.pcla.app.ui.theme.Ui
import io.pcla.core.launch.JavaPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * The home screen: greeting, the recommended version as one big hero, recent versions, a small
 * stats strip and a recommendation card.
 *
 * Every number on this page is measured data or a local file check — the hero's "就绪" means the
 * version json is already on disk, and the button really downloads + opens the game window.
 */
@Composable
internal fun HomeScreen(report: Report, onOpenVersions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val version = report.recommendation.version
        ?: report.ranking.firstOrNull()?.first
        ?: "1.16.5"
    val heap = report.configs[version]?.heapMb ?: report.profile.heapSweetMb
    val major = JavaPolicy.requiredMajor(version)
    val measured = report.profile.measuredOrNull(version)

    var stage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ready by remember(version) { mutableStateOf(versionReady(context, version)) }

    fun launch(v: String, h: Int) {
        if (busy) return
        busy = true
        stage = "检查本地文件…"
        scope.launch {
            runCatching {
                val installed = withContext(Dispatchers.IO) {
                    runCatching { GameInstaller.assemble(context, v) }.getOrNull()
                } ?: run {
                    stage = "开始下载 $v…"
                    withContext(Dispatchers.IO) {
                        GameInstaller.install(context, v, h) { p ->
                            stage = buildString {
                                append(p.stage)
                                if (p.total > 0) append(" ${p.done}/${p.total}")
                            }
                        }
                    }
                }
                if (v == version) ready = true
                stage = "打开游戏窗口…"
                context.startActivity(
                    Intent(context, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_VERSION, installed.versionId)
                        .putExtra(GameActivity.EXTRA_HEAP, h)
                )
                stage = ""
            }.onFailure {
                stage = "失败：" + (it.message ?: it.javaClass.simpleName)
            }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        HomeHeader()
        Spacer(Modifier.height(24.dp))

        Text(greeting(), fontSize = 12.sp, color = Ui.TextMuted)
        Spacer(Modifier.height(6.dp))
        Text(
            "今天想玩哪个版本？",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ui.Text,
            lineHeight = 33.sp,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(18.dp))

        HeroCard(
            version = version,
            subtitle = measured?.let {
                "实测平均 ${it.rd2Max ?: "—"} fps · TNT 谷底 ${it.tntTrough ?: "—"}"
            } ?: "本机型暂无实测数据",
            heapMb = heap,
            javaMajor = major,
            ready = ready,
            busy = busy,
            stage = stage,
            onLaunch = { launch(version, heap) },
            onMore = onOpenVersions,
        )

        Spacer(Modifier.height(26.dp))
        SectionHeader("最近版本", "全部", onOpenVersions)
        Spacer(Modifier.height(2.dp))
        val recent = report.ranking.take(3)
        if (recent.isEmpty()) {
            Text(
                "档位表里还没有这台机器的实测数据。",
                fontSize = 12.sp,
                color = Ui.TextFaint,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
        recent.forEachIndexed { index, (v, m) ->
            RecentVersionRow(
                version = v,
                subtitle = "平均 ${m.rd2Max ?: "—"} fps · 谷底 ${m.tntTrough ?: "—"} fps",
                color = CubePalette[index % CubePalette.size],
                onOpen = onOpenVersions,
                onLaunch = { launch(v, report.configs[v]?.heapMb ?: heap) },
            )
            if (index != recent.lastIndex) PclDivider()
        }

        Spacer(Modifier.height(20.dp))
        StatsStrip(report, heap)

        Spacer(Modifier.height(14.dp))
        RecommendCard(
            title = report.recommendation.alternatives.firstOrNull() ?: version,
            onClick = onOpenVersions,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Ui.SurfaceHigh)
                .border(1.dp, Ui.Stroke, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "PCLA",
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("PCLA", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text, letterSpacing = 0.4.sp)
            Text("Plain Craft Launcher", fontSize = 10.5.sp, color = Ui.TextFaint)
        }
        Avatar("S")
    }
}

@Composable
private fun HeroCard(
    version: String,
    subtitle: String,
    heapMb: Int,
    javaMajor: Int,
    ready: Boolean,
    busy: Boolean,
    stage: String,
    onLaunch: () -> Unit,
    onMore: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1C2216), Color(0xFF111309))))
            .border(1.dp, Ui.Stroke, shape)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("Vanilla")
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多版本", tint = Ui.TextFaint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            VersionCube(Ui.Orange, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(version, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text, letterSpacing = (-0.4).sp)
                Text(subtitle, fontSize = 11.sp, color = Ui.TextMuted, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(if (ready) "就绪" else "未安装", ready)
        }
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("内存 $heapMb MB")
            Pill("Java $javaMajor")
        }
        Spacer(Modifier.height(16.dp))
        PclButton(
            text = when {
                busy -> "正在准备…"
                ready -> "启动游戏"
                else -> "下载并启动"
            },
            onClick = onLaunch,
            enabled = !busy,
            highlight = true,
            height = 54,
            icon = Icons.Filled.PlayArrow,
        )
        if (stage.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stage, fontSize = 10.5.sp, color = Ui.TextFaint, maxLines = 2)
        }
    }
}

@Composable
private fun RecentVersionRow(
    version: String,
    subtitle: String,
    color: Color,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionCube(color, 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(version, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = Ui.Text)
            Text(subtitle, fontSize = 10.5.sp, color = Ui.TextFaint)
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Ui.SurfaceHigh)
                .border(1.dp, Ui.Stroke, CircleShape)
                .clickable(onClick = onLaunch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "启动 $version", tint = Ui.Lime, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StatsStrip(report: Report, heapMb: Int) {
    val bestFps = report.ranking.mapNotNull { it.second.rd2Max }.maxOrNull()
    val bestTrough = report.ranking.mapNotNull { it.second.tntTrough }.maxOrNull()
    PclCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatCell("最高平均帧", bestFps?.toString() ?: "—", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(34.dp).background(Ui.Stroke))
            StatCell("TNT 谷底", bestTrough?.toString() ?: "—", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(34.dp).background(Ui.Stroke))
            StatCell("堆上限", heapMb.toString(), Modifier.weight(1f), unit = "MB")
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(unit, fontSize = 10.sp, color = Ui.TextFaint, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(label, fontSize = 10.sp, color = Ui.TextFaint)
    }
}

@Composable
private fun RecommendCard(title: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2A1F45), Color(0xFF15121F))))
            .border(1.dp, Color(0xFF3B2C5E), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersionCube(Ui.Purple, 42.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("为你推荐", fontSize = 10.sp, color = Ui.TextMuted)
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = Ui.Text)
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Ui.Lime),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Ui.OnLime, modifier = Modifier.size(18.dp))
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        else -> "晚上好"
    }
    return "$part，Steve"
}

private fun versionReady(context: Context, version: String): Boolean =
    File(context.filesDir, "game/versions/$version/$version.json").isFile
