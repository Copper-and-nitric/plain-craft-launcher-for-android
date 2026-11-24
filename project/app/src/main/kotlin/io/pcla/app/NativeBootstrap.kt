package io.pcla.app

import android.content.Context
import java.io.File

/**
 * "Power-on self-test" for the in-process JVM: redo FCL's native prep before launchMinecraft.
 *
 * Order and content follow FCL's FCLauncher.setEnv / setUpJavaRuntime /
 * setupGraphicAndSoundEngine (see FCL source 1.3.1.8). A missing step never shows up as an
 * error, but as "the JVM starts yet the game won't" or "dlopen can't find libjli.so".
 */
/** 渲染后端。改这里一个地方，env 和日志就都跟着变。 */
object Renderer {
    /** 传给 POJAV_RENDERER；FCL 用 opengles2 走 gl4es，兼容面最广。 */
    const val ENGINE = "opengles2"
    /** 传给 LIBGL_ES。 */
    const val LIBGL_ES = "2"

    /** 启动日志里显示一句，出问题时先确认它没被别的东西改掉。 */
    fun describe(): String = "$ENGINE / GLES$LIBGL_ES（gl4es）"
}

object NativeBootstrap {
    init {
        System.loadLibrary("pcla_surface")
    }

    private external fun setenv(key: String, value: String): Int
    private external fun dlopen(path: String): Long
    private external fun chdir(path: String): Int

    /**
     * 加载三个原生库。libfcl 必须最先：pojavexec 的 hook 依赖它的表。
     *
     * 每一行的前后都落一次日志：native 库的 JNI_OnLoad 真的会让整个进程消失，
     * 而 System.loadLibrary 之后就不会再有任何 Java 代码执行，文件日志是唯一能指出
     * 到底是哪个 .so 的线索。返回的字符串同时给调用方的日志用。
     */
    fun loadLibs(onStep: (String) -> Unit = {}): List<String> {
        val loaded = ArrayList<String>()
        for (name in listOf("fcl", "pojavexec", "pojavexec_awt")) {
            onStep("正在加载 lib$name.so…")
            val r = runCatching { System.loadLibrary(name) }
            if (r.isSuccess) {
                loaded += name
                onStep("✓ lib$name.so")
            } else {
                val why = r.exceptionOrNull()?.message ?: "未知原因"
                loaded += "$name(失败: $why)"
                onStep("✗ lib$name.so：$why")
            }
        }
        return loaded
    }

    /** Env vars + pre-dlopen. Returns a human-readable report, one line per step into the log. */
    fun prepare(context: Context, runtime: RuntimePack.Installed, gameDir: File): List<String> {
        val out = ArrayList<String>()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val cache = context.cacheDir.absolutePath
        val libPath = listOf(
            GameInstaller.nativesPath(context, gameDir),
            RuntimePack.libraryPath(runtime),
        ).joinToString(":")

        val envMap = linkedMapOf(
            "HOME" to context.filesDir.absolutePath,
            // 清掉外面的 JAVA_TOOL_OPTIONS 之类，免得某些 ROM 塞进来的参数把 JLI_Launch 搅乱
            "JDK_JAVA_OPTIONS" to "",
            "TMPDIR" to cache,
            "JAVA_HOME" to runtime.home.absolutePath,
            "PATH" to runtime.home.absolutePath + "/bin:" + System.getenv("PATH").orEmpty(),
            "LD_LIBRARY_PATH" to libPath,
            "POJAV_NATIVEDIR" to nativeDir,
            "FCL_NATIVEDIR" to nativeDir,
            // glfwCreateWindow 的 native 实现按这个变量选后端：opengles2 / opengles3 / vulkan_zink /
            // gallium_virgl。空串会让它走 "Unimplemented" 分支，窗口建不出来却不一定有 Java 异常，
            // 表现就是「黑一下退回桌面」。这里是 FCL 的默认值。
            "POJAV_RENDERER" to Renderer.ENGINE,
            // gl4es 的 GLES 版本：2 兼容性最好，3 性能更好但老驱动会挂
            "LIBGL_ES" to Renderer.LIBGL_ES,
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOINTOVLHACK" to "1",
            "LIBGL_NOERROR" to "1",
            "FORCE_VSYNC" to "false",
        )
        for ((k, v) in envMap) {
            runCatching { setenv(k, v) }
            out += "env $k=$v"
        }

        // load every .so in the JRE into the global symbol table first (that's what setUpJavaRuntime does)
        val soFiles = ArrayList<File>()
        fun walk(dir: File, depth: Int = 0) {
            if (depth > 4) return
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".so")) soFiles += f
                else if (f.isDirectory) walk(f, depth + 1)
            }
        }
        walk(runtime.home)
        // order: jli/jvm first, then the rest (some .so files depend on symbols in libjvm)
        val ordered = soFiles.sortedBy {
            when {
                it.name == "libjli.so" -> 0
                it.name == "libjvm.so" -> 1
                else -> 2
            }
        }
        var ok = 0
        for (f in ordered) {
            if (dlopen(f.absolutePath) != 0L) ok++ else out += "✗ dlopen ${f.name}"
        }
        out += "JRE 原生库 ${ok}/${ordered.size} 载入"

        // sound + GL backends
        for (name in listOf("libopenal.so", "libgl4es_114.so", "libglxshim.so")) {
            val f = File(nativeDir, name)
            if (!f.isFile) {
                out += "（没有 $name）"
                continue
            }
            val h = dlopen(f.absolutePath)
            out += if (h != 0L) "✓ dlopen $name" else "✗ dlopen $name"
        }
        chdir(gameDir.absolutePath)
        out += "chdir ${gameDir.absolutePath}"
        return out
    }
}
