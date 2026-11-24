package io.pcla.app.ui

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pcla.app.AccountStore
import io.pcla.app.LaunchLog
import io.pcla.app.MicrosoftLoginFlow
import io.pcla.app.MicrosoftAuth
import io.pcla.app.ui.theme.Ui
import io.pcla.core.auth.Account
import io.pcla.core.auth.OfflineAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 首页 / 启动页要求「弹出登录」时用这个计数，登录成功后账号变化自己会收尾。 */
internal object LoginSignal {
    var request by mutableIntStateOf(0)
}

/** 当前账号读一次就够，各页共用；变了用 [notifyAccountChanged] 让界面跟着刷新。 */
internal object AccountState {
    private var version by mutableStateOf(0)

    @Composable
    fun remember(context: Context): AccountStore.Saved {
        version // 订阅变动
        return remember(version) { AccountStore.load(context) }
    }

    fun notifyAccountChanged() {
        version++
    }

    /** 启动前用：离线直接用；正版令牌若已过期就静默续期。失败返回 (null, 原因)，调用方提示重新登录。 */
    suspend fun freshForLaunch(context: Context, account: Account): Pair<Account?, String?> = withContext(Dispatchers.IO) {
        if (!account.isMicrosoft) return@withContext account to null
        if (!MicrosoftAuth.tokenExpired(account)) return@withContext account to null
        val rt = AccountStore.refreshToken(context, account)
            ?: return@withContext null to "正版令牌已过期，请到「设置 → 账号」重新登录"
        when (val r = MicrosoftAuth.refresh(rt)) {
            is MicrosoftAuth.Result.Ok -> {
                AccountStore.upsert(context, r.account, r.refreshToken)
                if (r.account.name != account.name) {
                    LaunchLog.append(context, "正版账号改名：${account.name} → ${r.account.name}")
                }
                notifyAccountChanged()
                r.account to null
            }
            is MicrosoftAuth.Result.Failed -> null to r.message
        }
    }
}

/**
 * 登录页：正版走微软官方授权（系统浏览器 + App 接管回调），离线只要起个名字。
 */
@Composable
internal fun LoginScreen(onBack: () -> Unit, highlightMicrosoft: Boolean = true) {
    val context = LocalContext.current
    val saved = AccountState.remember(context)
    var offlineName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun signInMicrosoft() {
        failed = false
        message = null
        val act = context.findActivity()
        if (act == null) {
            message = "找不到当前页面，登录暂时打不开"
            return
        }
        // 登录回来后的失败原因直接挂在这一页上，不用去翻通知
        // 结果页 toast + 记日志；回到这一页时账号列表本身就会变
        MicrosoftLoginFlow.start(act) { why -> if (why != null) message = why }
    }

    fun signInOffline() {
        val problem = OfflineAccount.problem(offlineName)
        if (problem != null) {
            failed = true
            message = problem
            return
        }
        val a = AccountStore.offline(context, offlineName)
        AccountState.notifyAccountChanged()
        LaunchLog.append(context, "切换离线账号 ${a.name} (${a.uuid})")
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ 返回",
                fontSize = 14.sp,
                color = Ui.TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .align(Alignment.CenterVertically),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("登录", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(4.dp))
        Text("正版可进正版服务器、用正版皮肤；离线只填个名字就能启动。", fontSize = 11.sp, color = Ui.TextMuted)
        Spacer(Modifier.height(18.dp))

        // ---- 正版 ----
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Ui.Surface)
                .border(if (highlightMicrosoft) 1.5.dp else 1.dp, if (highlightMicrosoft) Ui.Lime else Ui.Stroke, shape)
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Ui.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    // 四色方块：微软
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(Ui.Blue))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("正版登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
                    Text("微软官方授权 · 不经过第三方", fontSize = 10.sp, color = Ui.TextFaint)
                }
            }
            Text(
                "会跳转到微软的登录页，登录完自动切回来。密码只在微软页面上输入，启动器从不经手。",
                fontSize = 10.5.sp,
                color = Ui.TextFaint,
                lineHeight = 15.sp,
            )
            PclButton(
                text = "用微软账号登录",
                onClick = ::signInMicrosoft,
                highlight = true,
                height = 48,
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- 离线 ----
        PclCard("离线登录") {
            Text(
                "起一个名字就行。3~16 位，字母、数字、下划线。离线账号不能进正版服务器，皮肤也是默认的。",
                fontSize = 10.5.sp,
                color = Ui.TextFaint,
                lineHeight = 15.sp,
            )
            OutlinedTextField(
                value = offlineName,
                onValueChange = {
                    offlineName = it.take(16)
                    failed = false
                    message = null
                },
                singleLine = true,
                placeholder = { Text("比如 Steve", fontSize = 14.sp, color = Ui.TextFaint) },
                isError = failed,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { signInOffline() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ui.Lime,
                    unfocusedBorderColor = Ui.Stroke,
                    errorBorderColor = Ui.Red,
                    cursorColor = Ui.Lime,
                    focusedTextColor = Ui.Text,
                    unfocusedTextColor = Ui.Text,
                    focusedContainerColor = Ui.SurfaceHigh,
                    unfocusedContainerColor = Ui.SurfaceHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (offlineName.isNotEmpty() && OfflineAccount.problem(offlineName) == null) {
                Text("UUID ${OfflineAccount.uuid(offlineName)}", fontSize = 9.5.sp, color = Ui.TextFaint)
            }
            PclButton("用这个名字离线登录", onClick = ::signInOffline, height = 46)
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 11.sp, color = Ui.Red, lineHeight = 16.sp)
        }

        if (saved.accounts.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("这个手机上的账号", fontSize = 15.sp, color = Ui.Text, modifier = Modifier.padding(bottom = 6.dp))
            PclCard {
                saved.accounts.forEachIndexed { index, a ->
                    AccountRow(
                        account = a,
                        current = a.uuid == saved.current,
                        onPick = {
                            AccountStore.setCurrent(context, a)
                            AccountState.notifyAccountChanged()
                            LaunchLog.append(context, "切换到账号 ${a.name}")
                            onBack()
                        },
                        onDelete = {
                            AccountStore.remove(context, a)
                            AccountState.notifyAccountChanged()
                        },
                    )
                    if (index != saved.accounts.lastIndex) PclDivider()
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

/** Compose 的 LocalContext 可能是 ContextWrapper，一层层拆到 Activity 才能拿 startActivityForResult。 */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return c as? Activity
}

@Composable
private fun AccountRow(account: Account, current: Boolean, onPick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onPick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(account.name.firstOrNull()?.uppercase() ?: "?")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(account.name, fontSize = 14.5.sp, color = Ui.Text, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                val tone = if (account.isMicrosoft) TagTone.LIME else TagTone.GRAY
                PclTag(if (account.isMicrosoft) "正版" else "离线", tone)
            }
            Text(account.uuid.take(8) + "…", fontSize = 9.5.sp, color = Ui.TextFaint)
        }
        if (current) {
            Icon(Icons.Filled.Check, contentDescription = "当前", tint = Ui.Lime, modifier = Modifier.size(18.dp))
        } else {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除 ${account.name}",
                tint = Ui.TextFaint,
                modifier = Modifier.size(18.dp).clickable(onClick = onDelete),
            )
        }
    }
}

