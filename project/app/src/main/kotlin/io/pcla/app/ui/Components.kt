package io.pcla.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.ui.theme.Ui
import io.pcla.core.device.DeviceSpec
import io.pcla.core.device.Verdict

/** Colors used for the little isometric block next to a version. Index is the row order, not the version. */
internal val CubePalette = listOf(Ui.Orange, Ui.Grass, Ui.Sand, Ui.Purple)

/** 22dp rounded surface used for every block in the new UI. */
@Composable
fun PclCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ui.Surface)
            .border(1.dp, Ui.Stroke, shape)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Ui.TextMuted,
                letterSpacing = 0.4.sp,
            )
        }
        content()
    }
}

/**
 * Pill button. highlight is the lime primary action from the home hero; the plain variant is a
 * dark tile used for secondary actions.
 */
@Composable
fun PclButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    enabled: Boolean = true,
    height: Int = 52,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape((height / 2).dp)
    val textColor = when {
        !enabled -> Ui.TextFaint
        highlight -> Ui.OnLime
        else -> Ui.Text
    }
    val bg: Brush = when {
        !enabled -> Brush.linearGradient(listOf(Ui.SurfaceHigh, Ui.SurfaceHigh))
        highlight -> Brush.linearGradient(listOf(Ui.Lime, Ui.LimeDeep))
        else -> Brush.linearGradient(listOf(Ui.SurfaceHigh, Ui.SurfaceHigh))
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(bg)
            .then(if (highlight || !enabled) Modifier else Modifier.border(1.dp, Ui.Stroke, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

enum class TagTone { LIME, GRAY, OUTLINE, RED }

@Composable
fun PclTag(text: String, tone: TagTone, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        TagTone.LIME -> Ui.Lime to Ui.OnLime
        TagTone.GRAY -> Ui.SurfaceHigh to Ui.TextMuted
        TagTone.OUTLINE -> Color.Transparent to Ui.TextMuted
        TagTone.RED -> Color(0xFF3A1A15) to Ui.Red
    }
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(container)
            .then(if (tone == TagTone.OUTLINE) Modifier.border(1.dp, Ui.Stroke, RoundedCornerShape(11.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun verdictTag(verdict: Verdict): Pair<String, TagTone> = when (verdict) {
    Verdict.OK -> "可跑" to TagTone.LIME
    Verdict.AVOID -> "避开" to TagTone.RED
    Verdict.UNTESTED -> "未测" to TagTone.OUTLINE
    Verdict.UNKNOWN -> "无数据" to TagTone.OUTLINE
}

/** Key/value pair, label above value — the settings-page rhythm from PCL. */
@Composable
fun PclField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 10.5.sp, color = Ui.TextFaint)
        Text(value, fontSize = 13.sp, color = Ui.Text, maxLines = 2)
    }
}

/** Monospace block for logs and JVM arguments. */
@Composable
fun PclCode(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ui.BgElevated)
            .border(1.dp, Ui.Stroke, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            text,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = Ui.TextMuted,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

/** Baseline row height used by the recent-version list. */
@Composable
fun PclRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
fun PclDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.Stroke))
}

/** "最近版本   全部" style header. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
        if (action != null) {
            Text(
                action,
                fontSize = 11.5.sp,
                color = Ui.Lime,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** Small outlined chip: "内存 2048 MB", "Java 8". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Ui.Stroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 10.5.sp, color = Ui.TextMuted, maxLines = 1)
    }
}

/** "就绪" / "未安装" with a dot. */
@Composable
fun StatusPill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) Ui.Lime else Ui.TextFaint
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Ui.Lime.copy(alpha = 0.12f) else Ui.SurfaceHigh)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 10.5.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

/** Round blue avatar in the header. */
@Composable
fun Avatar(initial: String, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Ui.Blue, Ui.BlueDeep))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Isometric block used as the version icon. A square game icon would be a lie: Minecraft's own
 * identity is the cube, so draw three shaded faces instead of shipping five near-identical PNGs.
 */
@Composable
fun VersionCube(color: Color, side: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(side)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val topFace = lerp(color, Color.White, 0.34f)
        val leftFace = color
        val rightFace = lerp(color, Color.Black, 0.30f)

        val top = Path().apply {
            moveTo(cx, h * 0.04f)
            lineTo(w * 0.96f, h * 0.30f)
            lineTo(cx, h * 0.56f)
            lineTo(w * 0.04f, h * 0.30f)
            close()
        }
        val left = Path().apply {
            moveTo(w * 0.04f, h * 0.30f)
            lineTo(cx, h * 0.56f)
            lineTo(cx, h * 0.96f)
            lineTo(w * 0.04f, h * 0.70f)
            close()
        }
        val right = Path().apply {
            moveTo(w * 0.96f, h * 0.30f)
            lineTo(cx, h * 0.56f)
            lineTo(cx, h * 0.96f)
            lineTo(w * 0.96f, h * 0.70f)
            close()
        }
        drawPath(top, topFace)
        drawPath(left, leftFace)
        drawPath(right, rightFace)
        drawPath(top, Color.White.copy(alpha = 0.16f), style = Stroke(width = w * 0.018f))
    }
}

/** Page title used on the non-home tabs. */
@Composable
fun PageHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text, letterSpacing = (-0.2).sp)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Ui.TextMuted)
    }
}

/** Device facts card shared by 版本 and 设置. */
@Composable
fun DeviceCard(spec: DeviceSpec, onReload: (() -> Unit)? = null) {
    PclCard("设备") {
        val items = listOf(
            "SoC" to spec.socKey.ifBlank { "未知" },
            "机型" to spec.deviceModel.ifBlank { "未知" },
            "GPU" to spec.glRenderer.ifBlank { "未知" },
            "GL 版本" to spec.glVersion.ifBlank { "未知" },
            "内存" to "${spec.ramMb / 1024} GB",
            "ABI" to spec.abi.ifBlank { "未知" },
        )
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, pair ->
                    if (i > 0) Spacer(Modifier.width(14.dp))
                    PclField(pair.first, pair.second, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        onReload?.let { reload ->
            PclButton("重新探测", onClick = reload, height = 44)
        }
    }
}
