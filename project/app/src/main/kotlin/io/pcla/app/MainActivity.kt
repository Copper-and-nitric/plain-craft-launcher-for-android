package io.pcla.app

import android.graphics.Color
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
        // The skin is read before the first frame so the window background and bar icons match it.
        val light = ThemePrefs.isLight(this)
        Ui.apply(light)
        super.onCreate(savedInstanceState)
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
