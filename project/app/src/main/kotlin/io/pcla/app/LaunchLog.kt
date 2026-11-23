package io.pcla.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A tiny append-only log for the launch path.
 *
 * Why a file instead of only logcat: the game runs in `:game`, and when the native side takes the
 * process down logcat is the only witness — but it is awkward to collect on a phone. This file is
 * readable from the Settings page, so "it went landscape then closed" becomes "it stopped after
 * Surface 交接" without any adb.
 */
object LaunchLog {
    private const val FILE_NAME = "launch.log"
    private const val MAX_BYTES = 256 * 1024

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun append(context: Context, line: String) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.length() > MAX_BYTES) file.writeText("")
            file.appendText("${timeFormat.format(Date())}  $line\n")
        }
    }

    fun tail(context: Context, lines: Int = 40): String = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) "" else file.readLines().takeLast(lines).joinToString("\n")
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).writeText("") }
    }
}
