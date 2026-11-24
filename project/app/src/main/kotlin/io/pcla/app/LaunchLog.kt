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

    /** 进程自己写的游戏日志：stderr 重定向到这里，崩之前的异常栈就在里面。 */
    private const val STDOUT_NAME = "game-stdout.log"

    fun stdoutFile(context: Context): File = File(context.filesDir, STDOUT_NAME)

    @Synchronized
    fun stdout(context: Context, line: String) {
        runCatching {
            val file = stdoutFile(context)
            if (file.length() > 4L * 1024 * 1024) {
                // 保留最后 1MB，别把整个日志清空（清空后崩之前的栈就没了）
                val keep = file.readText().takeLast(1024 * 1024)
                file.writeText(keep)
            }
            file.appendText(line + "\n")
        }
    }

    fun stdoutTail(context: Context, lines: Int = 40): String = runCatching {
        val file = stdoutFile(context)
        if (!file.isFile) "" else file.readLines().takeLast(lines).joinToString("\n")
    }.getOrDefault("")

    fun clearStdout(context: Context) {
        runCatching { stdoutFile(context).writeText("") }
    }

    /**
     * 把 Java 的 System.err / System.out 接到日志文件。
     *
     * 为什么必须做：JVM 在 App 进程里跑，崩溃前 Minecraft 打的异常栈走的是 JVM 的 System.err，
     * 不是 Android 的 logcat；进程被 native 带走后，这些输出就全丢了。接一次，之后「建窗失败」
     * 就能看到到底是 HeadlessException、还是 UnsatisfiedLinkError、还是 Cacio 的 NPE。
     */
    fun hookVmStreams(context: Context) {
        val ctx = context.applicationContext
        // 只走 JDK 一定有的那个重载 PrintStream(OutputStream, boolean)；
        // 行拆分和 UTF-8 编码都在 LineWriter 里做，这里只负责接到 System 上
        runCatching {
            val out = java.io.PrintStream(LineWriter { stdout(ctx, it) }, true)
            System.setOut(out)
            System.setErr(out)
        }
    }

    private class LineWriter(private val sink: (String) -> Unit) : java.io.Writer() {
        private val buf = StringBuilder()

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            synchronized(buf) {
                buf.append(cbuf, off, len)
                while (true) {
                    val nl = buf.indexOf("\n")
                    if (nl < 0) break
                    val line = buf.substring(0, nl).trimEnd('\r')
                    buf.delete(0, nl + 1)
                    sink(line)
                }
                // 没有换行的半行（进度条之类）攒够 4KB 也落一次，否则卡在这里会丢
                if (buf.length > 4096) {
                    sink(buf.toString())
                    buf.setLength(0)
                }
            }
        }

        override fun flush() {
            synchronized(buf) {
                if (buf.isNotEmpty()) {
                    sink(buf.toString())
                    buf.setLength(0)
                }
            }
        }

        override fun close() = flush()
    }

    fun tail(context: Context, lines: Int = 40): String = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) "" else file.readLines().takeLast(lines).joinToString("\n")
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).writeText("") }
    }
}
