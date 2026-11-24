package io.pcla.core.auth

import io.pcla.core.launch.LaunchPlan

/**
 * 启动时交给 JVM 的身份：用户名、UUID、令牌。
 *
 * 两个来源：
 * - 离线登录——玩家自己起个名字，UUID 由名字派生（和原版离线模式同一条规则，见 [offline]）；
 * - 正版登录——微软 OAuth → Xbox Live → Minecraft Services，拿到的是真 UUID 和真令牌（见 app 模块）。
 *
 * 注意：正版令牌是敏感数据，不写日志、不进异常信息，[toString] 里已经抹掉了。
 */
data class Account(
    val name: String,
    val uuid: String,
    val accessToken: String,
    val kind: Kind,
    /** 正版账号的 Xbox 用户 id（XUID），离线账号为空串。 */
    val xuid: String = "",
) {
    enum class Kind { OFFLINE, MICROSOFT }

    val isMicrosoft: Boolean get() = kind == Kind.MICROSOFT

    override fun toString(): String =
        "Account($name, ${uuid.take(8)}…, ${if (isMicrosoft) "正版" else "离线"})"
}

object OfflineAccount {

    /** 原版的离线 UUID：`UUID.nameUUIDFromBytes("OfflinePlayer:<名字>")`，1.7 以后一直没变。 */
    fun uuid(name: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val bytes = md.digest(("OfflinePlayer:" + name).toByteArray(Charsets.UTF_8))
        // Java 的 nameUUIDFromBytes：把版本位（3）和变体位（IETF）写死，其余照抄 MD5
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x30).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" +
            hex.substring(16, 20) + "-" + hex.substring(20)
    }

    /** 「设个用户名就完事」：校验通过就建账号，不通过返回 null。 */
    fun of(name: String): Account? {
        val clean = normalize(name)
        if (!isValid(clean)) return null
        return Account(clean, uuid(clean), "0", Account.Kind.OFFLINE)
    }

    /**
     * 原版只认 3~16 位 `[A-Za-z0-9_]`。这里不去重、不改名：玩家输入什么就是什么，
     * 唯一多做的一步是去掉首尾空格——手机键盘很容易带出来。
     */
    fun normalize(name: String): String = name.trim()

    fun isValid(name: String): Boolean =
        name.length in 3..16 && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

    /** 为什么不行，一句话给 UI 用。 */
    fun problem(name: String): String? {
        val n = normalize(name)
        return when {
            n.isEmpty() -> "先起个名字"
            n.length < 3 -> "至少 3 个字符"
            n.length > 16 -> "最多 16 个字符"
            n.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_') } ->
                "只能用字母、数字和下划线"
            else -> null
        }
    }
}

/**
 * 把一个 [LaunchPlan] 的参数换成这个账号的身份。
 *
 * 原版的 jvm 参数用 `${auth_access_token}` / `${auth_uuid}` 这类占位符，
 * 旧版本还会用 `-Dminecraft.api.auth.host` 指到 authserver.mojang.com。
 * 拼顺序：先把可重填的 auth 参数去掉，再追加新的——JetBrains Runtime 的 `-D` 后写覆盖先写。
 */
object LaunchAuth {

    /** 交给 Java 的属性：令牌和 UUID，1.6~1.14 都读过。 */
    fun jvmArgs(account: Account): List<String> = buildList {
        add("-Dminecraft.api.auth.host=https://api.minecraftservices.com/authenticate")
        add("-Dminecraft.api.accessToken=${account.accessToken}")
        add("-Dminecraft.api.uuid=${account.uuid}")
        if (account.isMicrosoft) {
            // 1.17.1 之后的原版从这里判断「是否在线模式」：令牌与 UUID 都对了才允许进正版服务器
            add("-Dminecraft.api.account.type=msa")
            add("-Dminecraft.api.user.xuid=${account.xuid}")
        }
    }

    fun placeholders(account: Account): Map<String, String> = mapOf(
        "auth_player_name" to account.name,
        "auth_uuid" to account.uuid.replace("-", ""),
        "auth_access_token" to account.accessToken,
        "auth_session" to "token:${account.accessToken}:${account.uuid}",
        "user_type" to if (account.isMicrosoft) "msa" else "legacy",
        "user_properties" to "{}",
        "auth_xuid" to account.xuid,
        "clientid" to if (account.isMicrosoft) MicrosoftLogin.CLIENT_ID else "",
    )

    /** 哪些参数算「身份参数」，重新登录后要被替换掉。 */
    private val AUTH_JVM_PREFIXES = listOf(
        "-Dminecraft.api.auth.host=",
        "-Dminecraft.api.accessToken=",
        "-Dminecraft.api.uuid=",
        "-Dminecraft.api.session=",
        "-Dminecraft.api.account.type=",
        "-Dminecraft.api.user.xuid=",
        "-Dminecraft.api.token=",
        "-Duser.name=",
    )

    private val AUTH_GAME_KEYS = setOf("--username", "--uuid", "--accessToken", "--userType", "--userProperties", "--xuid", "--clientId")

    fun apply(plan: LaunchPlan, account: Account): LaunchPlan {
        val jvm = plan.jvmArgs.filterNot { a -> AUTH_JVM_PREFIXES.any { a.startsWith(it) } } + jvmArgs(account)
        val game = stripGameArgs(plan.gameArgs) + listOf(
            "--username", account.name,
            "--uuid", account.uuid.replace("-", ""),
            "--accessToken", account.accessToken,
            "--userType", if (account.isMicrosoft) "msa" else "legacy",
            "--userProperties", "{}",
        ) + if (account.isMicrosoft) listOf("--xuid", account.xuid) else emptyList()
        return plan.copy(jvmArgs = jvm, gameArgs = game)
    }

    /** `--username 名字` 两段一起删；`--username=名字` 一段删。其它参数原样保留。 */
    fun stripGameArgs(args: List<String>): List<String> {
        val out = ArrayList<String>(args.size)
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--") && AUTH_GAME_KEYS.any { a == it || a.startsWith("$it=") }) {
                i += if (a.contains('=')) 1 else 2
                continue
            }
            out.add(a)
            i++
        }
        return out
    }
}

/** 微软公开的 Minecraft Java 启动器 client id（PCL / HMCL / Pojav / FCL 用的都是这一个），不是秘密。 */
object MicrosoftLogin {
    const val CLIENT_ID = "00000000402b5328"
    const val SCOPE = "XboxLive.signin offline_access"
    const val AUTH_URL = "https://login.live.com/oauth20_authorize.srf"
    const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
    /** 只能填微软该链接类型的白名单地址；AndroidManifest 里注册了同名 scheme。 */
    const val REDIRECT_URL = "https://login.live.com/oauth20_desktop.srf"

    fun authorizeUrl(state: String): String =
        AUTH_URL + "?client_id=" + CLIENT_ID +
            "&response_type=code" +
            "&scope=" + SCOPE.replace(" ", "%20") +
            "&redirect_uri=" + REDIRECT_URL +
            "&prompt=select_account" +
            "&state=" + state

    /**
     * 微软把结果拼在 redirect_uri 后面的查询串里。成功带 `code`，用户取消带 `error=access_denied`。
     * 失败返回 (null, 原因)。
     */
    fun parseRedirect(url: String): Pair<String?, String?> {
        val query = url.substringAfter('?', "")
        val params = query.split('&').mapNotNull {
            val k = it.substringBefore('=')
            if (k.isEmpty()) null else k to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }.toMap()
        val err = params["error"]
        if (err != null) {
            return null to when (err) {
                "access_denied" -> "取消了登录"
                else -> "微软返回错误：$err ${params["error_description"].orEmpty()}".trim()
            }
        }
        val code = params["code"]
        return if (code.isNullOrBlank()) null to "没拿到授权码" else code to null
    }
}
