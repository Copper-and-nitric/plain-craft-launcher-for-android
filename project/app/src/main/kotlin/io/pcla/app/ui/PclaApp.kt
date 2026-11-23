package io.pcla.app.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.R
import io.pcla.app.ui.theme.Ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Four tabs, bottom bar: 启动 / 版本 / 下载 / 设置.
 *
 * One probe result ([Report]) is shared by all tabs — probing per tab would give two different
 * heap ceilings on the same screen pair.
 */
@Composable
fun PclaApp() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(reload) {
        report = null
        error = null
        try {
            report = withContext(Dispatchers.IO) { buildReport(context) }
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        }
    }

    Column(Modifier.fillMaxSize().background(Ui.Bg)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val message = error
            val r = report
            when {
                message != null -> CenteredMessage("设备探测失败：$message") { reload++ }
                r == null -> LoadingView()
                else -> when (tab) {
                    0 -> HomeScreen(r, onOpenVersions = { tab = 1 })
                    1 -> VersionsScreen(r, onReload = { reload++ })
                    2 -> DownloadScreen(
                        heapMb = r.configs[r.recommendation.version]?.heapMb ?: r.profile.heapSweetMb,
                    )
                    else -> SettingsScreen(r, onReload = { reload++ })
                }
            }
        }
        PclaBottomBar(tab) { tab = it }
    }
}

@Composable
private fun PclaBottomBar(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        Triple("启动", Icons.Filled.Home, 0),
        Triple("版本", Icons.Filled.List, 1),
        Triple("下载", Icons.Filled.KeyboardArrowDown, 2),
        Triple("设置", Icons.Filled.Settings, 3),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ui.BgElevated)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.Stroke))
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            items.forEach { (label, icon, index) ->
                BottomItem(
                    label = label,
                    icon = icon,
                    selected = tab == index,
                    modifier = Modifier.weight(1f),
                    onClick = { onTab(index) },
                )
            }
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (selected) Ui.Lime else Ui.TextFaint
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = color,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun LoadingView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(58.dp),
        )
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(
            color = Ui.Lime,
            strokeWidth = 2.dp,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("正在读取 GPU 与内存…", fontSize = 11.sp, color = Ui.TextMuted)
    }
}

@Composable
private fun CenteredMessage(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, fontSize = 13.sp, color = Ui.TextMuted)
        Spacer(Modifier.height(16.dp))
        PclButton("重试", onClick = onRetry, modifier = Modifier.width(160.dp), height = 44)
    }
}
