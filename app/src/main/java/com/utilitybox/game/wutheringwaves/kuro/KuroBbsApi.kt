package com.utilitybox.game.wutheringwaves.kuro

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * 库街区（kurobbs）API 客户端 —— 鸣潮账号数据的唯一来源。
 *
 * ### 数据来源与依据
 *
 * 接口规格来自公开的逆向整理仓库
 * [TomyJan/Kuro-API-Collection](https://github.com/TomyJan/Kuro-API-Collection)
 * （`PARAMS.md` / `API/user/getSmsCode.md` / `API/user/sdkLogin.md` /
 * `API/user/role/findRoleList.md` / `API/gamer/widget/game3/getData.md`）。
 * 请求头与请求体按文档逐字段实现，**不是猜的**。
 *
 * ### ★★ 这里处理的是**用户账号凭据**，安全要求高于本项目其它任何代码
 *
 * 1. **token 绝不进日志** —— 本文件里没有任何一处把 token 写进日志或异常消息。
 *    拼 URL、拼异常文案时都要留意，因为异常信息经常被整体打出来。
 * 2. **短信验证码同样绝不进日志** —— 它是账号的临时凭据。
 * 3. **不落盘** —— 本文件不写任何文件；持久化只在 [KuroCredentialStore] 一处，
 *    存进应用私有目录。
 *
 * ### 已知的接口特性（文档原文，已在上方链接核对）
 *
 * - `devCode` 请求头「**必须但不校验**」，值是公开常量 → 可以硬编码
 * - `token` 请求头 + `Cookie: user_token=<同一个 token>` 都要带
 * - APP 端 **同一时间只能有一个登录**，新 token 会让旧 token 失效
 * - `/user/getSmsCode` 的 `data.geeTest == true` 表示**库洛要求极验人机验证**，
 *   此时短信并未发出。这个我们无法程序化通过，只能如实告诉用户
 */
object KuroBbsApi {

    private const val BASE = "https://api.kurobbs.com"

    /** 文档注明「必须但不校验」的公开常量 */
    private const val DEV_CODE = "073A9EFAC18FC50616DD15808DAE719DBCB904B7"

    /** `sdkLogin` 的 **请求体**里要求带它（与请求头的 devCode 是两个值，文档如此） */
    private const val DEV_CODE_LOGIN = "2fba3859fe9bfe9099f2696b8648c2c6"

    /** 库街区的游戏 id：鸣潮 = 3 */
    const val GAME_ID_WUWA = 3

    /**
     * 设备标识。文档要求 UUID 格式，且库洛用它做风控。
     *
     * ★ 由 [KuroCredentialStore] 生成一次并持久化 —— **每次请求都换一个 UUID
     *   会被风控当成异常设备**，所以它必须在登录前后保持一致。
     */
    internal fun newDistinctId(): String = UUID.randomUUID().toString()

    // ---------------------------------------------------------------------
    // 对外接口
    // ---------------------------------------------------------------------

    /** 发短信验证码。返回 [SmsCodeResult]；[SmsCodeResult.needCaptcha] 为 true 时短信没发出去。 */
    suspend fun sendSmsCode(distinctId: String, mobile: String): KuroResult<SmsCodeResult> =
        post(
            path = "/user/getSmsCode",
            form = mapOf("mobile" to mobile, "geeTestData" to ""),
            distinctId = distinctId,
            token = null,
        ) { json ->
            // ★ 注意：`code == 200` 也可能没发出去 —— 文档明确「验证码发送失败也会返回成功,
            //   只有 geeTest 为 false 才是验证码发送成功」。所以判定看 geeTest 而不是 code。
            val gee = json.optJSONObject("data")?.optBoolean("geeTest", false) ?: false
            SmsCodeResult(needCaptcha = gee)
        }

    /** 验证码登录。成功时 [LoginResult.token] 就是后续所有请求要带的凭据。 */
    suspend fun login(
        distinctId: String,
        mobile: String,
        smsCode: String,
    ): KuroResult<LoginResult> =
        post(
            path = "/user/sdkLogin",
            form = mapOf(
                "code" to smsCode,
                "devCode" to DEV_CODE_LOGIN,
                "gameList" to "",
                "mobile" to mobile,
            ),
            distinctId = distinctId,
            token = null,
        ) { json ->
            val d = json.getJSONObject("data")
            LoginResult(
                token = d.getString("token"),
                userId = d.optString("userId"),
                userName = d.optString("userName"),
                headUrl = d.optString("headUrl").takeIf { it.isNotBlank() && it != "null" },
            )
        }

    /** 取该账号绑定的鸣潮角色列表（可能为空数组 = 没绑定）。 */
    suspend fun findWuwaRoles(distinctId: String, token: String): KuroResult<List<KuroRole>> =
        post(
            path = "/user/role/findRoleList",
            form = mapOf("gameId" to GAME_ID_WUWA.toString()),
            distinctId = distinctId,
            token = token,
        ) { json ->
            val arr = json.optJSONArray("data") ?: return@post emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                KuroRole(
                    roleId = o.optString("roleId"),
                    roleName = o.optString("roleName"),
                    serverId = o.optString("serverId"),
                    serverName = o.optString("serverName"),
                    isDefault = o.optBoolean("isDefault", false),
                )
            }
        }

    /** 鸣潮角色实时数据（体力、深塔、周本、电台…）。 */
    suspend fun wuwaWidgetData(
        distinctId: String,
        token: String,
        role: KuroRole?,
    ): KuroResult<KuroWidgetData> =
        post(
            path = "/gamer/widget/game3/getData",
            form = buildMap {
                put("gameId", GAME_ID_WUWA.toString())
                // ★ 文档：不带 serverId 会返回**默认角色**的数据，
                //   serverId 错误会返回全 null。所以有角色时必须两个一起带。
                if (role != null) {
                    put("roleId", role.roleId)
                    put("serverId", role.serverId)
                }
                put("sizeType", "1")
                put("type", "2")
            },
            distinctId = distinctId,
            token = token,
        ) { json -> KuroWidgetParser.parse(json.getJSONObject("data")) }

    // ---------------------------------------------------------------------
    // 传输
    // ---------------------------------------------------------------------

    private suspend fun <T> post(
        path: String,
        form: Map<String, String>,
        distinctId: String,
        token: String?,
        parse: (JSONObject) -> T,
    ): KuroResult<T> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                instanceFollowRedirects = true
                // 文档里的公共请求头。`devCode` 必须但不校验，值是公开常量。
                setRequestProperty("osVersion", "Android")
                setRequestProperty("devCode", DEV_CODE)
                setRequestProperty("distinct_id", distinctId)
                setRequestProperty("countryCode", "CN")
                // ★ 用真实机型而不是文档示例里的机型：这是设备信息，谎报没有收益
                setRequestProperty("model", Build.MODEL ?: "Android")
                setRequestProperty("source", "android")
                setRequestProperty("lang", "zh-Hans")
                setRequestProperty("version", "2.2.0")
                setRequestProperty("versionCode", "2200")
                setRequestProperty("channelId", "2")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                // ★ 不设 Accept-Encoding：让 HttpURLConnection 用默认的 identity，
                //   它就不会自作主张地发 gzip 却**不帮我们解压**。
                //   万一服务端仍然压了，下面的 readBody 会按 Content-Encoding 处理。
                setRequestProperty("User-Agent", "okhttp/3.11.0")
                if (token != null) {
                    setRequestProperty("token", token)
                    // 文档：token 与 Cookie 里的 user_token 是同一个值，两个都要带
                    setRequestProperty("Cookie", "user_token=$token")
                }
            }

            // 文档：请求体是字符串拼接（不是 JSON）
            val body = form.entries.joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = conn.responseCode
            val text = readBody(conn, status)

            if (status !in 200..299) {
                return@withContext KuroResult.Failure(
                    KuroError.Http(status),
                    "服务返回 HTTP $status",
                    transient = status >= 500,
                )
            }

            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            val msg = json.optString("msg").ifBlank { json.optString("message") }

            when {
                code == CODE_TOKEN_EXPIRED -> KuroResult.Failure(
                    KuroError.TokenExpired, msg.ifBlank { "登录已过期" }, transient = false,
                )
                code != 200 -> KuroResult.Failure(
                    KuroError.Business(code), msg.ifBlank { "请求失败（code=$code）" },
                    transient = code == CODE_TOO_FREQUENT,
                )
                else -> runCatching { parse(json) }
                    .fold(
                        onSuccess = { KuroResult.Ok(it) },
                        // 解析失败不把整段响应塞进异常信息 —— 响应里可能含账号信息
                        onFailure = { KuroResult.Failure(KuroError.Parse, "返回结构与预期不符") },
                    )
            }
        } catch (e: Exception) {
            KuroResult.Failure(KuroError.Network, "网络错误：${e.javaClass.simpleName}")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 服务端若仍然用了 gzip，这里兜住 —— 否则拿到的是乱码，报错会变成"解析失败"，很难查 */
    private fun readBody(conn: HttpURLConnection, status: Int): String {
        val raw: InputStream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val stream = if (conn.getHeaderField("Content-Encoding")?.contains("gzip", true) == true) {
            GZIPInputStream(raw)
        } else {
            raw
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    /** 文档里出现的业务码 */
    private const val CODE_TOKEN_EXPIRED = 220
    private const val CODE_TOO_FREQUENT = 242
}

/** 统一的成功/失败包装。失败携带结构化原因，UI 才能给出可操作的提示。 */
sealed interface KuroResult<out T> {
    data class Ok<T>(val value: T) : KuroResult<T>
    data class Failure(
        val error: KuroError,
        val message: String,
        /** 是否值得重试（网络抖动、限流）。凭据失效不算。 */
        val transient: Boolean = false,
    ) : KuroResult<Nothing>
}

sealed interface KuroError {
    /** token 失效 / 被其它端登录顶掉（文档 code=220） */
    data object TokenExpired : KuroError
    data class Business(val code: Int) : KuroError
    data class Http(val status: Int) : KuroError
    data object Network : KuroError
    data object Parse : KuroError
}

data class SmsCodeResult(
    /**
     * ★ 库洛要求极验人机验证。
     *
     * 此时**短信并没有发出去**（文档：「验证码发送失败也会返回成功,
     * 只有这个值为 false 才是验证码发送成功」）。
     * 我们无法程序化通过极验，只能如实告诉用户换个时间/网络重试。
     */
    val needCaptcha: Boolean,
)

data class LoginResult(
    val token: String,
    val userId: String,
    val userName: String,
    val headUrl: String?,
)

data class KuroRole(
    val roleId: String,
    val roleName: String,
    val serverId: String,
    val serverName: String,
    val isDefault: Boolean,
)

/** 鸣潮角色实时数据。字段按 `/gamer/widget/game3/getData` 的响应结构。 */
data class KuroWidgetData(
    val roleName: String,
    val serverName: String,
    val hasSignIn: Boolean,
    val signInTxt: String,
    /** 结晶波片（体力） */
    val energy: KuroMeter?,
    /** 每日活跃度 */
    val liveness: KuroMeter?,
    /** 结晶单质 */
    val storeEnergy: KuroMeter?,
    /** 逆境深塔·深境区 */
    val tower: KuroMeter?,
    /** 冥歌海墟·禁忌海域 */
    val slashTower: KuroMeter?,
    /** 战歌重奏 */
    val weekly: KuroMeter?,
    /** 千道门扉的异想 */
    val weeklyRouge: KuroMeter?,
    /** 电台等级 / 本周经验等 */
    val battlePass: List<KuroMeter>,
    val serverTime: Long,
)

/**
 * 一个"进度条式"的资源项。
 *
 * ★ `total == 0` 时必须当**未知**处理，不能当成 0/0 显示成 0%。
 *   响应里 `slashTowerData` 在没有数据时就是 `cur=0,total=0,status=1,value="奖励进度：暂无数据"` ——
 *   直接按比例算会显示成 0%，那是**把"没有数据"画成了"进度为零"**。
 */
data class KuroMeter(
    val name: String,
    val cur: Int,
    val total: Int,
    val valueText: String?,
    val iconUrl: String?,
    /** 恢复/重置时间戳（秒），0 或 null 表示没有 */
    val refreshTimeStamp: Long?,
    val timePreDesc: String?,
) {
    /** 有意义的进度比例；`null` = 数据不可用（total 为 0），UI 必须区别对待 */
    val fraction: Float?
        get() = if (total > 0) (cur.toFloat() / total).coerceIn(0f, 1f) else null
}

/** `/gamer/widget/game3/getData` 的 `data` 对象解析 */
internal object KuroWidgetParser {

    fun parse(d: JSONObject): KuroWidgetData = KuroWidgetData(
        roleName = d.optString("roleName"),
        serverName = d.optString("serverName"),
        hasSignIn = d.optBoolean("hasSignIn", false),
        signInTxt = d.optString("signInTxt"),
        energy = meter(d.optJSONObject("energyData")),
        liveness = meter(d.optJSONObject("livenessData")),
        storeEnergy = meter(d.optJSONObject("storeEnergyData")),
        tower = meter(d.optJSONObject("towerData")),
        slashTower = meter(d.optJSONObject("slashTowerData")),
        weekly = meter(d.optJSONObject("weeklyData")),
        weeklyRouge = meter(d.optJSONObject("weeklyRougeData")),
        battlePass = d.optJSONArray("battlePassData")?.let { arr ->
            (0 until arr.length()).mapNotNull { meter(arr.optJSONObject(it)) }
        } ?: emptyList(),
        // 文档：库洛服务器有时返回错误时间，其它时间戳要按 serverTime 推算 ——
        // 所以把它留着，UI 用它判断"回满时间"是否可信
        serverTime = d.optLong("serverTime", 0L),
    )

    private fun meter(o: JSONObject?): KuroMeter? {
        if (o == null) return null
        return KuroMeter(
            name = o.optString("name"),
            cur = o.optInt("cur", 0),
            total = o.optInt("total", 0),
            valueText = o.optString("value").takeIf { it.isNotBlank() && it != "null" },
            iconUrl = o.optString("img").takeIf { it.isNotBlank() && it != "null" },
            // refreshTimeStamp 可能是 null、0 或字符串，统一按"没有"处理
            refreshTimeStamp = o.optLong("refreshTimeStamp", 0L).takeIf { it > 0L },
            timePreDesc = o.optString("timePreDesc").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}
