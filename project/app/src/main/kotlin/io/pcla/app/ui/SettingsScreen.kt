package io.pcla.app.ui

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.BuildConfig
import io.pcla.app.LaunchLog
import io.pcla.app.ui.theme.ThemePrefs
import io.pcla.app.ui.theme.Ui
import io.pcla.core.auth.Account
import io.pcla.core.launch.JavaPolicy

/**
 * 设置 tab: appearance, device facts, bundled JVM runtime, native libraries, the degrade advisor,
 * the launch log and the about block.
 */
@Composable
internal fun SettingsScreen(
    report: Report,
    account: Account? = null,
    onOpenLogin: () -> Unit = {},
    onReload: () -> Unit = {},
) {
    val context = LocalContext.current
    val major = JavaPolicy.requiredMajor(report.recommendation.version ?: "1.16.5")
    var logText by remember { mutableStateOf(LaunchLog.tail(context, 40)) }
    var gameOut by remember { mutableStateOf(LaunchLog.stdoutTail(context, 40)) }

    fun switchTheme(light: Boolean) {
        ThemePrefs.setLight(context, light)
        Ui.apply(light)
        (context as? Activity)?.recreate()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        PageHeader("设置", "账号 · 外观 · 设备 · 运行时 · 诊断")
        Spacer(Modifier.height(18.dp))

        AccountCard(account, onOpenLogin)
        Spacer(Modifier.height(14.dp))

        PclCard("外观") {
            ThemeChoice(
                title = "荧光绿 · 暗色",
                subtitle = "2026 深色皮肤，荧光绿主色",
                selected = Ui.isDark,
                accent = Ui.GreenPaletteLime,
            ) { switchTheme(false) }
            PclDivider()
            ThemeChoice(
                title = "PCL · 蓝白",
                subtitle = "原版 PCL 色板：蓝 #1370F3 + 浅灰底",
                selected = !Ui.isDark,
                accent = Ui.PclPaletteBlue,
            ) { switchTheme(true) }
        }
        Spacer(Modifier.height(14.dp))

        DeviceCard(report.spec, onReload)
        Spacer(Modifier.height(14.dp))

        RuntimeCard(major)
        Spacer(Modifier.height(14.dp))

        NativeCard()
        Spacer(Modifier.height(14.dp))

        PclCard("启动日志") {
            Text(
                "闪退后先看这里：最后一行就是断点。游戏在独立的 :game 进程里跑，崩了也不会带走启动器。",
                fontSize = 10.5.sp,
                color = Ui.TextFaint,
                lineHeight = 15.sp,
            )
            PclCode(logText.ifBlank { "还没有启动记录。" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PclButton(
                    "刷新",
                    onClick = {
                        logText = LaunchLog.tail(context, 40)
                        gameOut = LaunchLog.stdoutTail(context, 40)
                    },
                    modifier = Modifier.weight(1f),
                    height = 40,
                )
                PclButton(
                    "清空",
                    onClick = {
                        LaunchLog.clear(context)
                        LaunchLog.clearStdout(context)
                        logText = ""
                        gameOut = ""
                    },
                    modifier = Modifier.weight(1f),
                    height = 40,
                )
            }
            PclDivider()
            Text(
                "游戏进程输出（Java 的 stderr）",
                fontSize = 10.5.sp,
                color = Ui.TextMuted,
            )
            Text(
                "建窗失败、GLFW 报错、Minecraft 崩溃的异常栈都在这里。游戏窗口黑一下就退回桌面时，先看这段。",
                fontSize = 10.sp,
                color = Ui.TextFaint,
                lineHeight = 14.sp,
            )
            PclCode(gameOut.ifBlank { "还没有游戏进程输出。" })
        }
        Spacer(Modifier.height(14.dp))

        Text("降级建议", fontSize = 15.sp, color = Ui.Text, modifier = Modifier.padding(bottom = 10.dp))
        DegradeContent(report.spec, report.profile)

        Spacer(Modifier.height(14.dp))
        PclCard("关于") {
            PclField("PCLA", BuildConfig.VERSION_NAME)
            PclField("协议", "AGPL-3.0 · 与龙腾猫跃及 PCL 官方无关")
            PclField("原生层", "参考 FCL / PojavLauncher，后续计划重写")
            Text(
                "保持批判性思考。数据说话，配置不靠猜。",
                fontSize = 11.sp,
                color = Ui.TextFaint,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, color = Ui.Text, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 10.sp, color = Ui.TextFaint)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "已选", tint = Ui.Lime, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 设置里的账号块：显示当前是谁，一点就进登录页换人 / 加一个。
 * 正版那块顺带说明「令牌过期会静默续期」，省得玩家看到要求重新登录就以为登录坏了。
 */
@Composable
private fun AccountCard(account: Account?, onOpenLogin: () -> Unit) {
    PclCard("账号") {
        Row(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable(onClick = onOpenLogin)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account?.name?.firstOrNull()?.uppercase() ?: "?")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(account?.name ?: "未登录", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ui.Text)
                    Spacer(Modifier.width(6.dp))
                    if (account != null) {
                        val tone = if (account.isMicrosoft) TagTone.LIME else TagTone.GRAY
                        PclTag(if (account.isMicrosoft) "正版" else "离线", tone)
                    }
                }
                Text(
                    when {
                        account == null -> "点这里登录：正版（微软）或离线"
                        account.isMicrosoft -> "微软正版 · 令牌过期会自动续期"
                        else -> "离线 · 不能进正版服务器"
                    },
                    fontSize = 10.sp,
                    color = Ui.TextFaint,
                )
            }
            Text("切换 / 登录", fontSize = 12.sp, color = Ui.Lime)
        }
    }
}
