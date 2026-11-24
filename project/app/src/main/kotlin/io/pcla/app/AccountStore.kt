package io.pcla.app

import android.content.Context
import io.pcla.core.auth.Account
import io.pcla.core.auth.OfflineAccount
import java.io.File
import org.json.JSONObject

/**
 * 账号库：当前用哪个、都有哪些。
 *
 * 正版账号的令牌、微软 refresh_token 存在应用私有目录（filesDir/pcla-account.json），
 * 不进日志、不进备份（Manifest 里 allowBackup=false）。
 */
object AccountStore {

    private const val FILE_NAME = "pcla-account.json"
    private const val KEY_CURRENT = "current"

    data class Saved(
        val accounts: List<Account>,
        val current: String?,
        /** 微软 refresh_token，按客户端 id+uuid 存，用来静默续期。 */
        val refreshTokens: Map<String, String>,
    ) {
        fun byUuid(uuid: String): Account? = accounts.firstOrNull { it.uuid == uuid }
        fun tokenOf(a: Account): String? = refreshTokens[MicrosoftAuth.refreshKey(a)]
        fun currentAccount(): Account? = current?.let(::byUuid)
    }

    fun load(context: Context): Saved {
        val root = runCatching { JSONObject(File(context.filesDir, FILE_NAME).readText()) }.getOrNull()
            ?: return Saved(emptyList(), null, emptyMap())
        val accounts = runCatching {
            val arr = root.optJSONArray("accounts") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { parse(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        val tokens = runCatching {
            val o = root.optJSONObject("refresh") ?: return@runCatching emptyMap<String, String>()
            o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotBlank() }
        }.getOrDefault(emptyMap())
        return Saved(accounts, root.optString("current").takeIf { it.isNotBlank() }, tokens)
    }

    private fun parse(o: JSONObject): Account? {
        val name = o.optString("name")
        val uuid = o.optString("uuid")
        if (name.isBlank() || uuid.isBlank()) return null
        val kind = if (o.optString("kind") == "microsoft") Account.Kind.MICROSOFT else Account.Kind.OFFLINE
        return Account(
            name = name,
            uuid = uuid,
            accessToken = o.optString("token").ifBlank { "0" },
            kind = kind,
            xuid = o.optString("xuid"),
        )
    }

    private fun dump(a: Account): JSONObject = JSONObject().apply {
        put("name", a.name)
        put("uuid", a.uuid)
        put("token", a.accessToken)
        put("kind", if (a.isMicrosoft) "microsoft" else "offline")
        put("xuid", a.xuid)
    }

    @Synchronized
    fun save(context: Context, saved: Saved) {
        val root = JSONObject().apply {
            put("current", saved.current ?: "")
            put("accounts", org.json.JSONArray().apply { saved.accounts.forEach { put(dump(it)) } })
            put("refresh", JSONObject().apply { saved.refreshTokens.forEach { (k, v) -> put(k, v) } })
        }
        write(context, root)
    }

    private fun write(context: Context, root: JSONObject) {
        runCatching {
            val f = File(context.filesDir, FILE_NAME)
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }.onFailure { LaunchLog.append(context, "账号写入失败：${it.javaClass.simpleName}") }
    }

    /** 切换当前账号。 */
    fun setCurrent(context: Context, account: Account?) = update(context) { _, root ->
        root.put("current", account?.uuid ?: "")
    }

    /** 新增或替换一个账号，并设为当前。 */
    fun upsert(context: Context, account: Account, refreshToken: String? = null) = update(context) { _, root ->
        val accounts = root.optJSONArray("accounts") ?: org.json.JSONArray()
        val list = (0 until accounts.length()).map { accounts.getJSONObject(it) }
            .filter { it.optString("uuid") != account.uuid }
            .toMutableList()
        list += dump(account)
        root.put("accounts", org.json.JSONArray().apply { list.forEach { put(it) } })
        root.put("current", account.uuid)
        if (!refreshToken.isNullOrBlank()) {
            val r = root.optJSONObject("refresh") ?: JSONObject()
            r.put(MicrosoftAuth.refreshKey(account), refreshToken)
            root.put("refresh", r)
        }
        Unit
    }

    fun remove(context: Context, account: Account) = update(context) { saved, root ->
        val accounts = root.optJSONArray("accounts") ?: org.json.JSONArray()
        val list = (0 until accounts.length()).map { accounts.getJSONObject(it) }
            .filter { it.optString("uuid") != account.uuid }
        root.put("accounts", org.json.JSONArray().apply { list.forEach { put(it) } })
        if (root.optString("current") == account.uuid) {
            root.put("current", list.firstOrNull()?.optString("uuid") ?: "")
        }
        val r = root.optJSONObject("refresh") ?: JSONObject()
        r.remove(MicrosoftAuth.refreshKey(account))
        root.put("refresh", r)
        Unit
    }

    fun refreshToken(context: Context, account: Account): String? =
        load(context).refreshTokens[MicrosoftAuth.refreshKey(account)]

    private fun update(context: Context, block: (Saved, JSONObject) -> Unit) {
        synchronized(this) {
            val file = File(context.filesDir, FILE_NAME)
            val root = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
            block(load(context), root)
            write(context, root)
        }
    }

    /**
     * 改一个已有账号的字段（比如正版令牌）。**不动 current**——静默续期不能把玩家正在玩的手柄换人。
     * [flush] 为真时写回磁盘。
     */
    fun patch(context: Context, account: Account, refreshToken: String? = null, flush: Boolean = true) {
        synchronized(this) {
            val file = File(context.filesDir, FILE_NAME)
            val root = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
            val accounts = root.optJSONArray("accounts") ?: org.json.JSONArray()
            val list = (0 until accounts.length()).map { accounts.getJSONObject(it) }
                .filter { it.optString("uuid") != account.uuid }.toMutableList()
            list += dump(account)
            root.put("accounts", org.json.JSONArray().apply { list.forEach { put(it) } })
            if (!refreshToken.isNullOrBlank()) {
                val r = root.optJSONObject("refresh") ?: JSONObject()
                r.put(MicrosoftAuth.refreshKey(account), refreshToken)
                root.put("refresh", r)
            }
            if (flush) write(context, root) else PENDING = root
        }
    }

    private var PENDING: JSONObject? = null

    /** 把 [patch] 暂存的令牌落盘（退出游戏时调用）。 */
    fun flush(context: Context) {
        synchronized(this) {
            PENDING?.let { write(context, it) }
            PENDING = null
        }
    }

    /** 离线账号按名字建，同名就认同一个（UUID 本来就一样）。 */
    fun offline(context: Context, name: String): Account {
        val a = OfflineAccount.of(name) ?: error(OfflineAccount.problem(name) ?: "名字不合法")
        upsert(context, a)
        return a
    }
}
