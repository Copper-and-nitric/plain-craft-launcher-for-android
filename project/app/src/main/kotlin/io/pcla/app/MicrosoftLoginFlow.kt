package io.pcla.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.pcla.app.ui.AccountState
import io.pcla.core.auth.MicrosoftLogin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 正版登录的那一跳：起系统浏览器让玩家在**微软自己的页面**输密码，回到 App 后拿 code 换账号。
 *
 * 为什么不把账号密码抄进启动器的网页框里：密码只该进 login.live.com，启动器经手一次就多一份泄露面；
 * 而且微软在浏览器里能处理双重验证、验证码、陌生设备提醒，这些自己模仿不出来。
 *
 * 回调靠 Manifest 里注册的 `login.live.com` scheme：微软 OAuth 允许把桌面型的 redirect_uri
 * （https://login.live.com/oauth20_desktop.srf）声明成可被 App 接管的 scheme。
 */
object MicrosoftLoginFlow {

    private const val REQUEST_CODE = 0x4D53 // "MS"
    private const val PREF = "msa_login"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending: ((String?) -> Unit)? = null
    private var busy = false

    /** 发起登录。[onDone] 在结果回来后于主线程调用，参数是给玩家看的一句话。 */
    fun start(activity: Activity, onDone: (String?) -> Unit = {}) {
        if (busy) {
            onDone("正在登录中，先去浏览器完成授权")
            return
        }
        pending = onDone
        busy = true
        val state = UUID.randomUUID().toString()
        activity.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("state", state).apply()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MicrosoftLogin.authorizeUrl(state))).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            // 不能再用 startActivityForResult 直接开浏览器：浏览器可能是另一个任务，结果回不来。
            // 真正的回调走 Manifest 里的 login.live.com intent-filter → MainActivity.onNewIntent。
            activity.startActivity(intent)
        } catch (t: Throwable) {
            busy = false
            pending = null
            Toast.makeText(activity, "打不开浏览器：" + (t.message ?: "没有可用的浏览器，请先装一个"), Toast.LENGTH_LONG).show()
            onDone("打不开浏览器")
        }
    }

    /** MainActivity.onNewIntent / onActivityResult 转发到这里。 */
    fun onResult(context: Context, data: Uri?) {
        val expected = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("state", null)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("state").apply()
        busy = false
        val cb = pending
        pending = null
        if (data == null) {
            cb?.invoke(null) // 玩家按了返回，不算错
            return
        }
        val gotState = data.getQueryParameter("state")
        if (!expected.isNullOrBlank() && gotState != null && gotState != expected) {
            cb?.invoke("登录回调对不上（state 不一致），可能是别的程序发起的，请重新登录")
            return
        }
        val (code, err) = MicrosoftLogin.parseRedirect(data.toString())
        if (code == null) {
            LaunchLog.append(context, "微软登录未完成：$err")
            cb?.invoke(err)
            return
        }
        LaunchLog.append(context, "拿到微软授权码，开始换账号")
        scope.launch {
            val result = withContext(Dispatchers.IO) { MicrosoftAuth.finishLogin(code) }
            when (result) {
                is MicrosoftAuth.Result.Ok -> {
                    AccountStore.upsert(context, result.account, result.refreshToken)
                    AccountState.notifyAccountChanged()
                    LaunchLog.append(context, "正版登录成功：${result.account.name}")
                    Toast.makeText(context, "已登录：${result.account.name}", Toast.LENGTH_SHORT).show()
                    cb?.invoke(null)
                }
                is MicrosoftAuth.Result.Failed -> {
                    LaunchLog.append(context, "正版登录失败：${result.message}")
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    cb?.invoke(result.message)
                }
            }
        }
    }
}
