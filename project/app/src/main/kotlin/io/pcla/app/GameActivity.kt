package io.pcla.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The game window: one full-screen SurfaceView plus the in-process JVM.
 *
 * Why a separate Activity is mandatory: `glfwCreateWindow` wants a Surface that is **currently
 * displayed**. The 660x192 preview on the launcher page is for "inspection" only, wrong size and
 * wrong lifetime; here it gets full screen, landscape, keep-awake, and the Surface is handed to
 * libpojavexec.so only after `surfaceCreated` — reversed, native stores a dangling pointer.
 *
 * The few lines of text on screen are just a temporary status bar (the JVM covers it once up);
 * when something breaks and logcat isn't handy, it's the only way to see how far we got.
 */
class GameActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LaunchLog.append(this, "GameActivity onCreate")
        // 线程里没接住的异常默认只进 logcat；同进程 JVM 崩了 App 直接死，所以这里先写文件
        val appContext = applicationContext
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            LaunchLog.append(appContext, "崩溃 ${t.name}：$e")
            e.stackTraceToString().lineSequence().take(25).forEach { LaunchLog.stdout(appContext, it) }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        val root = FrameLayout(this)
        surfaceView = SurfaceView(this)
        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        status = TextView(this).apply {
            setTextColor(0xFFE8E8E8.toInt())
            textSize = 11f
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0x99000000.toInt())
            text = "PCLA 游戏窗口\n正在等 Surface…"
        }
        root.addView(
            status,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
        setContentView(root)
        surfaceView.holder.addCallback(this)
    }

    private fun log(line: String) {
        status.text = "PCLA 游戏窗口\n" + line
        LaunchLog.append(this, "Game: $line")
        android.util.Log.i("PCLA-Game", line)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (started) return
        started = true
        val versionId = intent.getStringExtra(EXTRA_VERSION) ?: "1.16.5"
        val heapMb = intent.getIntExtra(EXTRA_HEAP, 2048)
        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)
        val f = holder.surfaceFrame
        log("Surface 已创建 ${f.width()}x${f.height()}")
        // 从这一刻起，JVM 里 Minecraft 打的每一行 stderr（包括建窗失败的异常栈）都落进 game-stdout.log
        LaunchLog.clearStdout(this)
        LaunchLog.hookVmStreams(this)
        // the JVM runs until the game exits, so this whole block goes on a worker thread;
        // this Activity's UI thread only displays status.
        //
        // 看门狗：游戏起来就沉到画面下面，起不来则把 game-stdout.log 的尾巴刷到状态条上，
        // 玩家截个图就能看到崩溃原因，不用连电脑看 logcat。
        Thread({
            var lastShown = ""
            while (true) {
                Thread.sleep(2000)
                val tail = LaunchLog.stdoutTail(this, 6)
                if (tail.isNotBlank() && tail != lastShown) {
                    lastShown = tail
                    runOnUiThread {
                        // 只在 JVM 还没接管画面时刷新状态条（JVM 起来后这个 TextView 本来就被盖住了）
                        if (status.text.startsWith("PCLA 游戏窗口")) status.text = "PCLA 游戏窗口\n" + tail
                    }
                }
            }
        }, "pcla-watchdog").apply { isDaemon = true; start() }
        Thread({
            try {
                // libfcl 必须在 libpojavexec 之前加载：pojavexec 的 JNI_OnLoad 会用到 fcl 的表，
                // 而 setupBridgeWindow 又依赖 JNI_OnLoad 初始化好的 pojav_environ。
                LaunchLog.append(this, "Game: 开始加载原生库（版本 ${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}）")
                NativeBootstrap.loadLibs { step ->
                    LaunchLog.append(this, "Game: $step")
                    runOnUiThread { status.text = "PCLA 游戏窗口\n" + step }
                }
                LaunchLog.append(this, "Game: attach begin")
                val err = SurfaceBridge.attach(holder.surface)
                LaunchLog.append(this, "Game: attach " + (err ?: "ok"))
                if (err != null) {
                    runOnUiThread { log("✗ Surface 交接失败：$err") }
                    return@Thread
                }
                runOnUiThread { log("✓ Surface 已交给 libpojavexec.so，准备 JVM…") }
                // 身份不在 Intent 里传（令牌太敏感），只带 UUID，在这里从账号库取；取不到就按离线默认名启动
                val account = accountUuid?.let { AccountStore.load(this).byUuid(it) }
                val installed = GameInstaller.assemble(this, versionId, account)
                runOnUiThread {
                    val who = account?.let { "${it.name}${if (it.isMicrosoft) "（正版）" else "（离线）"}" } ?: "未登录"
                    log("以 $who 启动\nJava ${installed.javaMajor}，主类 ${installed.plan.mainClass}\n开始启动 JVM…")
                }
                LaunchLog.append(this, "Game: 身份 $account")
                // 正版令牌如果是续期来的，只在内存里亮一下，退出游戏时再落盘，游戏中途崩了也不丢
                account?.takeIf { it.isMicrosoft }?.let { AccountStore.patch(this, it, flush = false) }
                val code = InProcessLauncher.launch(this, installed, heapMb) { line ->
                    LaunchLog.append(this, "JVM $line")
                    runOnUiThread { status.append("\n" + line.take(90)) }
                }
                runOnUiThread { log("launchJVM 返回 $code") }
            } catch (t: Throwable) {
                LaunchLog.append(this, "Game 失败：" + (t.message ?: t.javaClass.simpleName))
                runOnUiThread { log("✗ 失败：" + (t.message ?: t.javaClass.simpleName)) }
            }
        }, "pcla-jvm").start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        android.util.Log.i("PCLA-Game", "surfaceChanged ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        LaunchLog.append(this, "Game: surfaceDestroyed")
        android.util.Log.i("PCLA-Game", "surfaceDestroyed")
    }

    override fun onDestroy() {
        LaunchLog.append(this, "GameActivity onDestroy")
        runCatching { AccountStore.flush(this) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VERSION = "versionId"
        const val EXTRA_HEAP = "heapMb"
        const val EXTRA_ACCOUNT = "accountUuid"
    }
}
