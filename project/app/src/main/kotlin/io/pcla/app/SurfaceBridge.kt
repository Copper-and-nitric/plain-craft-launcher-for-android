package io.pcla.app

import android.view.Surface

/**
 * Hand the Android Surface to the bundled libpojavexec.so.
 *
 * The entry name is Java_org_lwjgl_glfw_CallbackBridge_setupBridgeWindow — a JNI symbol name is
 * decided by the class declaring the native method, so the bundle ships a class of the same name,
 * app/src/main/java/org/lwjgl/glfw/CallbackBridge.java, and the native method resolves (primary
 * path). The dlopen + dlsym in src/main/cpp/surface_bridge.c is the fallback for the same thing.
 */
object SurfaceBridge {
    init {
        System.loadLibrary("pcla_surface")
    }

    private external fun install(surface: Surface): Int
    private external fun probe(): String

    /** Which Surface entry points libpojavexec.so has, for the self-check card. */
    fun describe(): String = runCatching { probe() }.getOrElse { "加载失败：${it.message}" }

    /**
     * Reflectively call `org.lwjgl.glfw.CallbackBridge.setupBridgeWindow(Object)`.
     *
     * Why reflection instead of a direct reference: that class lives in `src/main/java` (the Java
     * source set), and the Kotlin compiler's visibility into this module's Java sources depends on
     * the AGP version and build order (CI #44 died on `Unresolved reference: lwjgl`); reflection
     * only needs the class at runtime, zero compile-time coupling, and dlsym still backstops it.
     */
    /**
     * null means success; otherwise a Chinese-language reason.
     *
     * Always goes through the C guard: the native side checks `pojav_environ` before calling
     * setupBridgeWindow, so "library loaded but JNI_OnLoad never ran" becomes a readable error
     * instead of a SIGSEGV that takes the whole :game process down.
     */
    fun attach(surface: Surface): String? {
        return when (val r = runCatching { install(surface) }.getOrElse { -99 }) {
            0 -> null
            -1 -> "dlopen libpojavexec.so 失败（原生库没打包进来？）"
            -2 -> "libpojavexec.so 里没有 Surface 入口"
            -3 -> "Surface 还没准备好"
            -4 -> "pojav_environ 还是 NULL：JNI_OnLoad 没跑到，已拦截（不会再闪退）"
            -99 -> "调用 native 时崩了，见 logcat（tag PclaSurface）"
            else -> "dlopen 返回 $r"
        }
    }
}
