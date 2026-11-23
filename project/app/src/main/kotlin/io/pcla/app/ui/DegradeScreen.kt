package io.pcla.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.ui.theme.Ui
import io.pcla.core.device.DeviceProfile
import io.pcla.core.device.DeviceSpec
import io.pcla.core.launch.DegradeLadder
import io.pcla.core.launch.DegradePlan
import io.pcla.core.launch.Lever
import io.pcla.core.launch.RunObservation

/**
 * Enter one real measurement by hand and get back a degrade route with reasons.
 * The UI never invents data: unset fields stay null so the core can say "evidence is insufficient".
 *
 * This is a plain Column (no scrolling of its own) because it is embedded in the settings page.
 */
@Composable
internal fun DegradeContent(spec: DeviceSpec, profile: DeviceProfile) {
    val versions = profile.measured.keys.sorted()
    var version by remember { mutableStateOf(versions.lastOrNull() ?: "1.16.5") }
    var rd by remember { mutableStateOf("${profile.rd.sweet}") }
    var fps by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("") }
    var heap by remember { mutableStateOf("${profile.heapSweetMb}") }
    var gcPause by remember { mutableStateOf("") }
    var gcReclaim by remember { mutableStateOf("") }
    var memFree by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<DegradePlan?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PclCard("实测数据") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PclInput("版本", version, Modifier.weight(1f)) { version = it }
                PclInput("渲染距离", rd, Modifier.weight(1f), KeyboardType.Number) { rd = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PclInput("平均帧率", fps, Modifier.weight(1f), KeyboardType.Decimal) { fps = it }
                PclInput("采样秒数", sample, Modifier.weight(1f), KeyboardType.Number) { sample = it }
            }
            PclInput("堆大小 MB", heap, Modifier.fillMaxWidth(), KeyboardType.Number) { heap = it }
        }

        PclCard("GC 证据（可选）") {
            Text(
                "留空就是不提供。填了才用来判断「是不是堆太紧」，而不是靠猜。",
                fontSize = 10.5.sp,
                color = Ui.TextFaint,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PclInput("停顿占比 %", gcPause, Modifier.weight(1f), KeyboardType.Decimal) { gcPause = it }
                PclInput("回收率 %", gcReclaim, Modifier.weight(1f), KeyboardType.Decimal) { gcReclaim = it }
            }
        }

        PclCard("环境（可选）") {
            PclInput("可用内存 MB", memFree, Modifier.fillMaxWidth(), KeyboardType.Number) { memFree = it }
        }

        PclButton(
            "生成建议",
            onClick = {
                plan = DegradeLadder.plan(
                    RunObservation(
                        avgFps = fps.toDoubleOrNull() ?: 0.0,
                        sampleSeconds = sample.toIntOrNull() ?: 0,
                        renderDistance = rd.toIntOrNull() ?: profile.rd.sweet,
                        heapMb = heap.toIntOrNull() ?: profile.heapSweetMb,
                        gcPausePct = gcPause.toDoubleOrNull(),
                        gcReclaimPct = gcReclaim.toDoubleOrNull(),
                        memAvailableMb = memFree.toIntOrNull(),
                    ),
                    spec, profile, version,
                )
            },
            highlight = true,
            height = 48,
        )

        val current = plan
        if (current == null) {
            Text(
                "填完上面的数字再点按钮。没有样本就不下结论，这是刻意的。",
                fontSize = 10.5.sp,
                color = Ui.TextFaint,
            )
        } else {
            PclCard("$version 的处置顺序") {
                Text(current.heapVerdict, fontSize = 11.sp, color = Ui.TextMuted, lineHeight = 16.sp)
                current.actions.forEachIndexed { index, a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        PclTag(leverLabel(a.lever), TagTone.LIME)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${a.from} → ${a.to}", fontSize = 13.sp, color = Ui.Text)
                            Text(a.reason, fontSize = 10.5.sp, color = Ui.TextMuted, lineHeight = 15.sp)
                            Text(a.expected, fontSize = 10.5.sp, color = Ui.TextFaint, lineHeight = 15.sp)
                        }
                    }
                    if (index != current.actions.lastIndex) PclDivider()
                }
                current.notes.forEach { n ->
                    Text("· $n", fontSize = 10.5.sp, color = Ui.TextFaint, lineHeight = 15.sp)
                }
            }
        }
    }
}

private fun leverLabel(lever: Lever): String = when (lever) {
    Lever.RENDER_DISTANCE -> "渲染距离"
    Lever.VERSION -> "换版本"
    Lever.RENDERER -> "换渲染器"
    Lever.HEAP -> "改堆"
}

@Composable
private fun PclInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}
