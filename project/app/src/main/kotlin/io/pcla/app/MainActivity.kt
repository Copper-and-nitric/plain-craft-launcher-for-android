package io.pcla.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.pcla.app.ui.PclaApp
import io.pcla.app.ui.theme.PclaTheme
import io.pcla.app.ui.theme.ThemePrefs
import io.pcla.app.ui.theme.Ui

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The skin is read before the first frame so the window background and bar icons match it.
        val light = ThemePrefs.isLight(this)
        Ui.apply(light)
        installCrashHandler()
        if (light) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
            window.setBackgroundDrawable(ColorDrawable(0xFFF5F5F5.toInt()))
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            )
            window.setBackgroundDrawable(ColorDrawable(0xFF0B0C09.toInt()))
        }
        setContent {
            PclaTheme {
                PclaApp()
            }
        }
        // 从浏览器回调进来时 App 可能是冷启动，onNewIntent 不会触发，要在这里也检查一次。
        // 放在 setContent 之后：结果要弹 Toast，界面得先存在。
        intent?.data?.takeIf { it.scheme == "login.live.com" }?.let {
            MicrosoftLoginFlow.onResult(this, it)
        }
    }

    /**
     * 微软登录完成：浏览器带着 login.live.com 链接回到本 App，走这里。
     * 用 onNewIntent 而不是只靠 onActivityResult——浏览器常常是另一个任务，结果不会回到发起者。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data
        if (uri != null && uri.scheme == "login.live.com") {
            setIntent(intent)
            MicrosoftLoginFlow.onResult(this, uri)
        }
    }

    /** 微软登录授权完成后浏览器把 control 交回来，这里是唯一入口。 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri? = data?.data
        if (uri != null && uri.scheme == "login.live.com") {
            MicrosoftLoginFlow.onResult(this, uri)
            return
        }
        // 有些浏览器会把结果包在 EXTRA_INTENT 里
        val inner = data?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.data
        if (inner != null && inner.scheme == "login.live.com") {
            MicrosoftLoginFlow.onResult(this, inner)
        }
    }

    /**
     * Dump one copy of a Java/Kotlin crash to the app's external dir (Android/data/io.pcla.app/files/)
     * so the user can drag it out. Native crashes (SIGSEGV) never reach here, but InProcessLauncher
     * writes a start marker before calling the JVM, telling "died in native" from "never got there".
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            LaunchLog.append(this, "CRASH ${thread.name}: $error")
            runCatching {
                val dir = getExternalFilesDir("crash") ?: filesDir
                dir.mkdirs()
                val f = java.io.File(dir, "crash-${System.currentTimeMillis()}.txt")
                f.writeText(
                    buildString {
                        appendLine("thread=${thread.name}")
                        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine(error.toString())
                        appendLine(error.stackTraceToString())
                    }
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
