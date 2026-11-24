package io.pcla.app

import io.pcla.core.auth.Account
import io.pcla.core.auth.MicrosoftLogin
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 正版登录：微软账号 → Xbox Live → XSTS → Minecraft Services。
 *
 * 每一步都是官方地址，没有自建中转：
 *   login.live.com        OAuth（拿 code / 换 token / 刷新）
 *   user.auth.xboxlive.com   Xbox Live 身份
 *   xsts.auth.xboxlive.com   换取 XSTS 令牌
 *   api.minecraftservices.com 换成 Minecraft access token + 真实 UUID / 名字
 *
 * 失败信息统一成中文短句，直接把 "用户没开 Xbox 账号" 和 "网络断了" 分开，
 * 否则手机上只会看到一行 JSON。
 */
object MicrosoftAuth {

    private const val XBL_URL = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"

    class LoginError(message: String, val kind: Kind = Kind.OTHER) : Exception(message) {
        enum class Kind { NO_XBOX_ACCOUNT, NO_GAME, NETWORK, OTHER }
    }

    sealed class Result {
        data class Ok(val account: Account, val refreshToken: String) : Result()
        data class Failed(val message: String, val kind: LoginError.Kind = LoginError.Kind.OTHER) : Result()
    }

    fun refreshKey(account: Account): String = MicrosoftLogin.CLIENT_ID + ":" + account.uuid

    /** 浏览器授权回来以后：code → 令牌 → 账号。refresh_token 要存下来给下次续期。 */
    fun finishLogin(code: String): Result = runCatching {
        val tokens = oauthToken("authorization_code", "code" to code, "redirect_uri" to MicrosoftLogin.REDIRECT_URL)
        val account = toMinecraft(tokens.getString("access_token"))
        Result.Ok(account, tokens.optString("refresh_token"))
    }.getOrElse { e -> Result.Failed(message(e), kindOf(e)) }

    /** 用存的 refresh_token 换新令牌，顺带拿到最新的 UUID / 名字（改名、换皮肤都不用重新登录）。 */
    fun refresh(refreshToken: String): Result = runCatching {
        val tokens = oauthToken("refresh_token", "refresh_token" to refreshToken)
        val account = toMinecraft(tokens.getString("access_token"))
        Result.Ok(account, tokens.optString("refresh_token").ifBlank { refreshToken })
    }.getOrElse { e -> Result.Failed(message(e), kindOf(e)) }

    /** 当前令牌还能不能用：打一次 profile，401 表示要续期。 */
    fun tokenExpired(account: Account): Boolean {
        if (!account.isMicrosoft) return false
        return runCatching {
            val (code, _) = request(
                "GET", MC_PROFILE_URL,
                headers = mapOf("Authorization" to "Bearer " + account.accessToken),
            )
            code == 401
        }.getOrDefault(false)
    }

    // ---- 各段 ----

    private fun oauthToken(grant: String, vararg form: Pair<String, String>): JSONObject {
        val body = buildList {
            add("client_id" to MicrosoftLogin.CLIENT_ID)
            add("grant_type" to grant)
            addAll(form)
        }.joinToString("&") { (k, v) -> k + "=" + enc(v) }
        val (code, text) = request(
            "POST", MicrosoftLogin.TOKEN_URL, body = body,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        )
        if (code !in 200..299) {
            val err = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
            throw LoginError(
                when (err) {
                    "invalid_grant" -> "登录已过期，请重新点「微软登录」"
                    "invalid_client" -> "微软客户端 id 被拒（code $code）"
                    else -> "微软账号服务出错（HTTP $code）" + if (err.isNotBlank()) "：$err" else ""
                },
            )
        }
        return JSONObject(text)
    }

    private fun toMinecraft(msToken: String): Account {
        val xbl = postJson(
            XBL_URL, JSONObject().apply {
                put("Properties", JSONObject().apply {
                    put("AuthMethod", "RPS")
                    put("SiteName", "user.auth.xboxlive.com")
                    put("RpsTicket", "d=$msToken")
                })
                put("RelyingParty", "http://auth.xboxlive.com")
                put("TokenType", "JWT")
            },
        )
        val xblToken = xbl.getString("Token")
        val uhs = xbl.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")

        val xsts = runCatching {
            postJson(
                XSTS_URL, JSONObject().apply {
                    put("Properties", JSONObject().apply { put("SandboxId", "RETAIL"); put("UserTokens", org.json.JSONArray().put(xblToken)) })
                    put("RelyingParty", "rp://api.minecraftservices.com/")
                    put("TokenType", "JWT")
                },
            )
        }
        val body = xsts.getOrElse { e ->
            // 423 表示账号没开 Xbox 或被限制；这个错误必须说成人话，否则玩家只会在网上瞎搜
            if (e is HttpError && e.code == 423) {
                throw LoginError(
                    "这个微软账号还没开通 Xbox Live（或地区受限）。\n" +
                        "到 xbox.com 登录并完成头像/年龄设置后再试。",
                    LoginError.Kind.NO_XBOX_ACCOUNT,
                )
            }
            if (e is HttpError && e.code == 401) {
                throw LoginError("Xbox 身份验证失败：这个账号可能是个儿童账号，或没有 Xbox 权限", LoginError.Kind.NO_XBOX_ACCOUNT)
            }
            throw e
        }
        val xstsToken = body.getString("Token")
        val xuid = body.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("xid")

        val mc = postJson(
            MC_LOGIN_URL, JSONObject().put("identityToken", "XBL3.0 x=$uhs;$xstsToken"),
        )
        val mcToken = mc.getString("access_token")

        val profile = runCatching {
            val (code, text) = request("GET", MC_PROFILE_URL, headers = mapOf("Authorization" to "Bearer " + mcToken))
            if (code == 404) {
                throw LoginError("这个微软账号没有购买 Minecraft Java 版", LoginError.Kind.NO_GAME)
            }
            if (code !in 200..299) throw HttpError(code, text)
            JSONObject(text)
        }.getOrElse { throw it }

        return Account(
            name = profile.getString("name"),
            uuid = profile.getString("id"),
            accessToken = mcToken,
            kind = Account.Kind.MICROSOFT,
            xuid = xuid,
        )
    }

    // ---- HTTP（只用 JDK，app 不想为了三次请求再引一个库）----

    private class HttpError(val code: Int, body: String) : IOException("HTTP $code ${body.take(120)}")

    private fun postJson(url: String, json: JSONObject): JSONObject {
        val (code, text) = request("POST", url, body = json.toString(), headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"))
        if (code !in 200..299) throw HttpError(code, text)
        return JSONObject(text)
    }

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15_000,
        redirects: Int = 3,
    ): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "PCLA-Launcher/${BuildConfig.VERSION_NAME} (Java MineCraft launcher for Android)")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val code = conn.responseCode
            // 微软的 token 端点偶尔 302 到另一台机器；关掉自动跟随是为了自己控制次数
            val location = conn.getHeaderField("Location")
            if (code in 300..399 && location != null && redirects > 0) {
                return request(method, location, body, headers, timeoutMs, redirects - 1)
            }
            val text = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            code to text
        } catch (e: IOException) {
            throw LoginError("连不上微软的服务器（" + (e.message ?: e.javaClass.simpleName) + "）", LoginError.Kind.NETWORK)
        } finally {
            conn.disconnect()
        }
    }

    private fun message(e: Throwable): String = when (e) {
        is LoginError -> e.message ?: "登录失败"
        is HttpError -> "服务返回 HTTP ${e.code}"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun kindOf(e: Throwable): LoginError.Kind = (e as? LoginError)?.kind ?: LoginError.Kind.OTHER

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
