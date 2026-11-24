package io.pcla.app

import android.content.Context
import io.pcla.core.asset.AssetIndex
import io.pcla.core.auth.Account
import io.pcla.core.auth.LaunchAuth
import io.pcla.core.game.GamePlan
import io.pcla.core.game.NativeExtractor
import io.pcla.core.game.RemoteFile
import io.pcla.core.launch.AndroidRuntime
import io.pcla.core.launch.AwtRuntime
import io.pcla.core.launch.ClasspathBuilder
import io.pcla.core.launch.JavaPolicy
import io.pcla.core.launch.LaunchCommand
import io.pcla.core.launch.LaunchPlan
import io.pcla.core.launch.Preflight
import io.pcla.core.launch.VersionMeta
import io.pcla.core.version.VersionManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Install a version from scratch: manifest → version json → libraries/assets/client.jar →
 * extract natives → assemble the command line.
 *
 * Network requests deliberately use HttpURLConnection instead of reusing the download module:
 * this is a bulk "a few json files plus a thousand small files" case that needs wide concurrency
 * and per-file retry, not the download engine's segmentation and rate limiting.
 */
object GameInstaller {

    private const val BMCL = "https://bmclapi2.bangbang93.com"
    private val MANIFEST_URLS = listOf(
        "$BMCL/mc/game/version_manifest_v2.json",
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json",
    )

    data class Progress(
        val stage: String,
        val done: Int = 0,
        val total: Int = 0,
        val bytes: Long = 0,
        val message: String = "",
    )

    data class Installed(
        val versionId: String,
        val root: File,
        val plan: LaunchPlan,
        val javaMajor: Int,
    )

    /** Mojang download URLs often time out in China, so everything goes through the BMCLAPI mirror. */
    fun mirrored(url: String): String = url
        .replace("https://launchermeta.mojang.com", BMCL)
        .replace("https://piston-meta.mojang.com", BMCL)
        .replace("https://launcher.mojang.com", BMCL)
        .replace("https://resources.download.minecraft.net", "$BMCL/assets")

    suspend fun install(
        context: Context,
        versionId: String,
        heapMb: Int,
        onProgress: (Progress) -> Unit,
    ): Installed = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "game").apply { mkdirs() }
        onProgress(Progress("读取版本清单"))
        val manifestText = fetchText(MANIFEST_URLS.first())
        val entry = VersionManifest.find(VersionManifest.parse(manifestText), versionId)
            ?: error("清单里没有版本 $versionId")

        val versionDir = File(root, "versions/$versionId").apply { mkdirs() }
        val versionJson = File(versionDir, "$versionId.json")
        writeText(versionJson, fetchText(mirrored(entry.url)))

        val meta = VersionMeta.parse(versionJson.readText())
        val assetsName = meta.assetsIndex ?: versionId
        val indexFile = File(root, "assets/indexes/$assetsName.json").apply { parentFile?.mkdirs() }
        meta.assetIndexUrl?.let { writeText(indexFile, fetchText(mirrored(it))) }
        val assets = if (indexFile.isFile) AssetIndex.parse(indexFile.readText()) else emptyList()

        val (runtimeMeta, droppedLwjgl) = AndroidRuntime.strip(meta)
        onProgress(Progress("换上随包的 LWJGL 3.3.3（清单里 $droppedLwjgl 条 org.lwjgl 库不再下载）", 0, 0))
        val plan = GamePlan.build(runtimeMeta, assets, versionId, clientUrl = meta.clientUrl?.let { mirrored(it) })
        if (plan.missingUrls.isNotEmpty()) {
            onProgress(Progress("清单里有 ${plan.missingUrls.size} 个库没有下载地址", message = plan.missingUrls.take(3).joinToString()))
        }

        val files = plan.all
        var done = 0
        var bytes = 0L
        onProgress(Progress("下载 ${files.size} 个文件", 0, files.size))
        val gate = Semaphore(4)
        coroutineScope {
            files.map { f ->
                async {
                    gate.withPermit {
                        val target = File(root, f.relativePath)
                        if (!isUpToDate(target, f.sha1)) {
                            target.parentFile?.mkdirs()
                            fetchTo(target, f.url)
                            if (f.sha1 != null && !sha1(target).equals(f.sha1, true)) {
                                target.delete()
                                error("校验失败：${f.relativePath}")
                            }
                        }
                        synchronized(gate) {
                            done++
                            bytes += target.length()
                            onProgress(Progress("下载文件", done, files.size, bytes))
                        }
                    }
                }
            }.awaitAll()
        }

        onProgress(Progress("解压原生库"))
        val nativesDir = File(root, "natives").apply { deleteRecursively(); mkdirs() }
        NativeExtractor.extractAll(plan.natives.map { File(root, it.relativePath) to emptyList() }, nativesDir)

        val lwjgl = LwjglAssets.install(context, root)
        if (lwjgl.copied > 0) onProgress(Progress("拷贝 LWJGL 运行库 ${lwjgl.copied} 个", 0, 0))

        val clientJar = plan.clientJar?.let { File(root, it.relativePath).absolutePath } ?: ""
        val placeholders = ClasspathBuilder.placeholders(
            classpath = "",
            nativesDir = listOf(
                nativesDir.absolutePath,
                lwjgl.nativesDir,
                // libpojavexec.so extracted from jniLibs lives here; glfw's input callbacks rely on it
                context.applicationInfo.nativeLibraryDir,
            ).joinToString(":"),
            gameDir = root.absolutePath,
            assetsDir = File(root, "assets").absolutePath,
            assetsIndex = assetsName,
            versionName = versionId,
        )
        val launchPlan = ClasspathBuilder.build(
            runtimeMeta, File(root, "libraries").absolutePath, clientJar, placeholders, lwjgl.jars,
        )
        val major = meta.javaMajor ?: JavaPolicy.requiredMajor(versionId)
        onProgress(Progress("准备完成", files.size, files.size, bytes, "Java $major"))
        Installed(versionId, root, launchPlan, major)
    }

    /**
     * Rebuild [Installed] from the files already downloaded on disk, no network.
     *
     * Why it exists: the download step is on the launcher page, but the JVM is actually started
     * by another Activity (the full-screen Surface lives there) and a LaunchPlan doesn't fit in
     * an Intent, so we reassemble it from the version id. Only local json and downloaded
     * libraries are read; no request is made.
     */
    fun assemble(context: Context, versionId: String, account: Account? = null): Installed {
        val root = File(context.filesDir, "game")
        val versionJson = File(root, "versions/$versionId/$versionId.json")
        require(versionJson.isFile) {
            "还没下载 $versionId（找不到 ${versionJson.absolutePath}），先回启动页点①"
        }
        val meta = VersionMeta.parse(versionJson.readText())
        val assetsName = meta.assetsIndex ?: versionId
        val indexFile = File(root, "assets/indexes/$assetsName.json")
        val assets = if (indexFile.isFile) AssetIndex.parse(indexFile.readText()) else emptyList()

        val (runtimeMeta, _) = AndroidRuntime.strip(meta)
        // clientUrl is only a placeholder here (GamePlan uses it for relative paths); nothing re-downloads
        val plan0 = GamePlan.build(runtimeMeta, assets, versionId, clientUrl = meta.clientUrl?.let { mirrored(it) })

        val nativesDir = File(root, "natives")
        val lwjgl = LwjglAssets.install(context, root)
        val clientJar = plan0.clientJar?.let { File(root, it.relativePath).absolutePath } ?: ""
        val placeholders = ClasspathBuilder.placeholders(
            classpath = "",
            nativesDir = listOf(
                nativesDir.absolutePath,
                lwjgl.nativesDir,
                context.applicationInfo.nativeLibraryDir,
            ).joinToString(":"),
            gameDir = root.absolutePath,
            assetsDir = File(root, "assets").absolutePath,
            assetsIndex = assetsName,
            versionName = versionId,
        )
        val plan1 = ClasspathBuilder.build(
            runtimeMeta, File(root, "libraries").absolutePath, clientJar, placeholders, lwjgl.jars,
        )
        // 玩家名字、UUID、令牌：登录页选了谁就以谁的身份进游戏；没登录时保留原版默认（PCLA / 全零）
        val launchPlan = account?.let { LaunchAuth.apply(plan1, it) } ?: plan1

        // 启动前体检：classpath 里的 jar 与三处 natives 目录真的存在吗？
        // 这一步在真机上比什么都值钱——「文件少一个」会表现成建窗时莫名其妙的 UnsatisfiedLinkError。
        val missing = plan1.classpath.split(":")
            .filter { it.endsWith(".jar") && !File(it).isFile }
        val nativeDir = File(root, "natives")
        val nativeFiles = listOf(nativeDir, File(root, AndroidRuntime.BUNDLE_DIR + "/natives"),
            File(context.applicationInfo.nativeLibraryDir))
            .flatMap { d -> d.listFiles()?.map { it.name } ?: emptyList() }
        val checks = Preflight.run(
            plan = launchPlan,
            jarNames = missing,
            nativePaths = listOf(
                nativeDir.absolutePath,
                File(root, AndroidRuntime.BUNDLE_DIR + "/natives").absolutePath,
                context.applicationInfo.nativeLibraryDir,
            ),
            nativeFiles = nativeFiles,
            expectedMajor = meta.javaMajor ?: JavaPolicy.requiredMajor(versionId),
            libraryPath = listOf(
                nativeDir.absolutePath,
                File(root, AndroidRuntime.BUNDLE_DIR + "/natives").absolutePath,
                context.applicationInfo.nativeLibraryDir,
            ).joinToString(":"),
        )
        checks.filter { !it.ok }.forEach {
            LaunchLog.append(context, "体检 ✗ ${it.name} —— ${it.detail}")
        }
        if (missing.isNotEmpty()) {
            error("classpath 里有 ${missing.size} 个 jar 不存在（第一个：${File(missing.first()).name}），先到「版本」页重新下载")
        }
        val major = meta.javaMajor ?: JavaPolicy.requiredMajor(versionId)
        return Installed(versionId, root, launchPlan, major)
    }

    /** All three natives dirs go into java.library.path: manifest-extracted, bundled LWJGL, jniLibs. */
    fun nativesPath(context: Context, root: File): String = listOf(
        // order is priority: the bundled arm64 LWJGL must come before the manifest-extracted x86,
        // otherwise a same-named liblwjgl.so resolves to the x86 one (wrong ELF class)
        File(root, AndroidRuntime.BUNDLE_DIR + "/natives").absolutePath,
        File(root, "natives").absolutePath,
        // 同进程跑 JVM 时，nativeLibraryDir 里的 libfcl/libpojavexec 已经由 System.loadLibrary 载进
        // 全局符号表；但 dlopen 按 soname 找 libpojavexec_awt.so 时需要它真的在这个目录下。
        // 兜底再加一遍 App 私有目录（有的 ROM 会把 nativeLibraryDir 放在 /data/app 只读挂载上）。
        File(context.applicationInfo.nativeLibraryDir).absolutePath,
    ).joinToString(":")

    /** Unpack the JRE and start the process; logs stream line by line. Caller must waitFor it. */
    suspend fun launch(
        context: Context,
        installed: Installed,
        heapMb: Int,
        onLine: (String) -> Unit,
    ): Process = withContext(Dispatchers.IO) {
        val runtime = RuntimePack.ensure(context, installed.javaMajor)
        val argv = LaunchCommand.build(
            javaBin = runtime.javaBin.absolutePath,
            plan = installed.plan,
            heapMb = heapMb,
            nativesDir = nativesPath(context, installed.root),
            extraJvm = listOf("-Djava.home=" + runtime.home.absolutePath) + AwtRuntime.jvmProps(1280, 720),
        )
        onLine("$ " + LaunchCommand.render(argv).take(4000))
        val process = ProcessBuilder(argv)
            .directory(installed.root)
            .redirectErrorStream(true)
            .apply {
                // the Android JRE is bionic-linked; without these two even libjli won't be found
                environment()["LD_LIBRARY_PATH"] = RuntimePack.libraryPath(runtime)
                environment()["HOME"] = installed.root.absolutePath
                environment()["TMPDIR"] = File(installed.root, "tmp").apply { mkdirs() }.absolutePath
                // let LWJGL's GL bridge load gl4es (libgl4es_114.so from jniLibs), translating
                // through GLES2 the slice of OpenGL that 1.16.5 needs
                environment()["LIBGL_NAME"] = "libgl4es_114.so"
                environment()["LIBGL_ES"] = "2"
                environment()["LIBGL_NOINIT"] = "0"
            }
            .start()
        Thread {
            process.inputStream.bufferedReader().forEachLine { line -> onLine(line) }
        }.apply { isDaemon = true }.start()
        process
    }

    // ---- network and files ----

    private fun fetchText(url: String): String = URL(url).openConnection().let { conn ->
        (conn as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PCLA/0.5")
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun fetchTo(target: File, url: String, attempts: Int = 3) {
        var last: Exception? = null
        repeat(attempts) { i ->
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PCLA/0.5")
                }
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                return
            } catch (e: Exception) {
                last = e
                Thread.sleep(800L * (i + 1))
            }
        }
        throw last ?: IllegalStateException("下载失败 $url")
    }

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun isUpToDate(file: File, sha1: String?): Boolean =
        file.isFile && file.length() > 0 && (sha1 == null || sha1(file).equals(sha1, true))

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
