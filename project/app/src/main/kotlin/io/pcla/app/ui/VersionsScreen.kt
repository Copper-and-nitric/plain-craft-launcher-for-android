package io.pcla.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.BuildConfig
import io.pcla.app.ui.theme.Ui
import io.pcla.core.device.Measured
import io.pcla.core.launch.Severity

/**
 * 版本 tab: every version with a measured score, plus the JVM argument block for the recommended one.
 * The card list is deliberately plain text + numbers — a bar chart here would imply more precision
 * than one phone's measurements can support.
 */
@Composable
internal fun VersionsScreen(report: Report, onReload: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        PageHeader("版本", "按实测分数排序 · ${report.ranking.size} 个版本")
        Spacer(Modifier.height(18.dp))

        DeviceCard(report.spec, onReload)
        Spacer(Modifier.height(14.dp))

        PclCard("实测排序") {
            if (report.ranking.isEmpty()) {
                Text("这台机型还没有实测数据，先按通用默认值跑。", fontSize = 12.sp, color = Ui.TextFaint)
            }
            report.ranking.forEachIndexed { index, (version, measured) ->
                VersionRow(version, measured, CubePalette[index % CubePalette.size], recommended = index == 0)
                if (index != report.ranking.lastIndex) PclDivider()
            }
        }

        Spacer(Modifier.height(14.dp))
        val version = report.recommendation.version
        val cfg = report.configs[version]
        if (version != null && cfg != null) {
            PclCard("JVM 参数 · $version") {
                Text(
                    "堆 ${cfg.heapMb} MB · 建议渲染距离 ${cfg.recommendedRenderDistance} · 综合分 ${
                        report.ranking.firstOrNull { it.first == version }?.second?.score ?: "—"
                    }",
                    fontSize = 11.sp,
                    color = Ui.TextMuted,
                )
                cfg.diagnostics.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        PclTag(
                            when (d.severity) {
                                Severity.BLOCK -> "拦截"
                                Severity.WARN -> "注意"
                                Severity.INFO -> "信息"
                            },
                            when (d.severity) {
                                Severity.BLOCK -> TagTone.RED
                                Severity.WARN -> TagTone.RED
                                Severity.INFO -> TagTone.OUTLINE
                            },
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(d.message, Modifier.weight(1f), fontSize = 11.sp, lineHeight = 16.sp, color = Ui.TextMuted)
                    }
                }
                PclCode(cfg.args.joinToString(" "))
            }
            Spacer(Modifier.height(14.dp))
        }

        PclCard("关于") {
            PclField("版本", BuildConfig.VERSION_NAME)
            PclField("档位", report.profile.id.uppercase())
            PclField("许可", "AGPL-3.0 · 第三方修改版")
            PclField("数据", "全部数字来自真机实测，不按机型外推")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VersionRow(version: String, measured: Measured, color: androidx.compose.ui.graphics.Color, recommended: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
        VersionCube(color, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(version, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = Ui.Text)
                if (recommended) {
                    Spacer(Modifier.width(7.dp))
                    PclTag("推荐", TagTone.LIME)
                }
                Spacer(Modifier.width(7.dp))
                val (label, tone) = verdictTag(measured.verdict)
                PclTag(label, tone)
            }
            Text(
                "综合分 ${measured.score} · 平均 ${measured.rd2Max ?: "—"} fps · 谷底 ${measured.tntTrough ?: "—"} fps",
                fontSize = 10.5.sp,
                color = Ui.TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("RD17 ${measured.rd17Max ?: "—"} fps")
                if (measured.genSeconds != null) Pill("生成 ${measured.genSeconds}s")
            }
            if (measured.note.isNotBlank()) {
                Text(measured.note, fontSize = 10.sp, color = Ui.TextFaint, lineHeight = 14.sp)
            }
        }
    }
}
