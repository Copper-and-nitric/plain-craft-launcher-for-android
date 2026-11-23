package io.pcla.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.BuildConfig
import io.pcla.app.ui.theme.Ui
import io.pcla.core.launch.JavaPolicy

/**
 * 设置 tab: device facts, bundled JVM runtime, native libraries, the degrade advisor and the
 * about block. The degrade advisor used to be its own tab; it lives here now so the bottom bar
 * matches the four-entry design (启动 / 版本 / 下载 / 设置).
 */
@Composable
internal fun SettingsScreen(report: Report, onReload: () -> Unit) {
    val major = JavaPolicy.requiredMajor(report.recommendation.version ?: "1.16.5")
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        PageHeader("设置", "设备 · 运行时 · 降级")
        Spacer(Modifier.height(18.dp))

        DeviceCard(report.spec, onReload)
        Spacer(Modifier.height(14.dp))

        RuntimeCard(major)
        Spacer(Modifier.height(14.dp))

        NativeCard()
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
