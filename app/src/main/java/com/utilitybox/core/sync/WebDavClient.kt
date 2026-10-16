package com.utilitybox.core.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * WebDAV 客户端（P11 补 39）。
 *
 * ### 为什么只用 PUT / GET，不用 PROPFIND / MKCOL
 *
 * `HttpURLConnection` **不允许自定义 HTTP 方法** —— 调 `setRequestMethod("PROPFIND")`
 * 会直接抛 `ProtocolException: Invalid HTTP method`。
 * 而 WebDAV 的目录列举（PROPFIND）和建目录（MKCOL）都是自定义方法。
 *
 * 好在我们的场景**根本不需要它们**：
 *
 * | 需求 | 不用 PROPFIND 怎么做 |
 * |---|---|
 * | 云端有没有文件 | `GET` → 200 有 / 404 没有 |
 * | 上传 | `PUT` |
 * | 目录 | **用户先在网盘里建一次**，程序只往固定路径写 |
 *
 * 于是**零新依赖**（不需要 OkHttp，也不需要 WebDAV 库），
 * 而且只用两个标准方法，兼容性最好。
 *
 * ### 为什么自己写 Base64
 *
 * Basic 认证要 Base64，但两条路都不通：
 * · `android.util.Base64` —— 只在 Android 上有，**纯 JVM 单元测试跑不了**
 * · `java.util.Base64` —— 需要 **API 26**，本项目 minSdk 是 24
 *
 * 所以自己编码一份（见 [base64Encode]）。它只有二十行，且**可以被单测覆盖** ——
 * 比引一个只为 Base64 的依赖划算得多。
 */
object WebDavClient {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * 拼接服务器地址与远端路径。
     *
     * 用户填的地址形式五花八门（末尾有斜杠、没斜杠、路径是 `/a/b.json` 还是 `a/b.json`），
     * 这里统一收口 —— 拼错的表现是 404，而 404 又很容易被当成"云端还没数据"，
     * **静默地什么都不做**。所以这段逻辑单独抽出来测。
     */
    internal fun joinUrl(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        val p = path.trim().trimStart('/')
        return if (p.isEmpty()) b else "$b/$p"
    }

    /** Basic 认证头的值（不含 `Basic ` 前缀） */
    internal fun basicAuth(user: String, pass: String): String =
        base64Encode("$user:$pass".toByteArray(Charsets.UTF_8))

    // ------------------------------------------------------------------
    // 三个操作
    // ------------------------------------------------------------------

    /**
     * 测试连接：对文件路径发一次 GET。
     *
     * ★ 200 和 404 **都算"连得上"** —— 404 只说明云端还没有备份文件，
     *   账号和地址都是对的。把它当失败会让人以为配置错了。
     */
    fun test(config: WebDavConfig): WebDavResult<Unit> = try {
        when (val code = request(config, "GET", null).first) {
            // ★★ 409 也算成功 —— 这是**实测出来的**（P11 补 39）。
            //
            //   坚果云在**父目录不存在**时返回 409（不是 404）。而"测试连接"
            //   要验的是**认证能不能过**，409 恰恰证明认证已经通过了。
            //
            //   实测证据：拿到应用密码后 GET 一个尚不存在的文件返回 409，
            //   而用**登录密码**时同样的请求返回 **401** —— 两者能区分开。
            //
            //   如果把 409 当失败，用户会被卡在鸡生蛋里：
            //   「要建文件夹 → 建之前先测连接 → 测试失败 → 以为密码错」。
            200, 404, 409 -> WebDavResult.Ok(Unit)
            401 -> WebDavResult.Failure(
                // ★ 这条提示是**被实测逼出来的**（P11 补 39）。
                //
                //   坚果云网页版**已经不再生成应用密码**了，入口挪到了
                //   客户端的「设置 → 第三方应用管理」。而用户很自然会拿登录密码去试，
                //   结果就是 401 —— 只显示"密码不对"的话，人会反复重填同一个密码。
                //
                //   所以把**原因和下一步**直接写进提示里。
                "认证失败（401）：坚果云需要的是「应用密码」，不是登录密码。" +
                    "在坚果云客户端 → 设置 → 第三方应用管理 里生成。"
            )
            403 -> WebDavResult.Failure("没有权限访问该路径（403）")
            else -> WebDavResult.Failure("服务器返回 HTTP $code")
        }
    } catch (e: IOException) {
        WebDavResult.Failure("连不上服务器：${e.javaClass.simpleName}")
    }

    /** 上传（覆盖）。父目录必须已存在 —— 我们不建目录（见类注释） */
    fun upload(config: WebDavConfig, content: String): WebDavResult<Unit> = try {
        when (val code = request(config, "PUT", content.toByteArray(Charsets.UTF_8)).first) {
            in 200..299 -> WebDavResult.Ok(Unit)
            401 -> WebDavResult.Failure(
                // ★ 这条提示是**被实测逼出来的**（P11 补 39）。
                //
                //   坚果云网页版**已经不再生成应用密码**了，入口挪到了
                //   客户端的「设置 → 第三方应用管理」。而用户很自然会拿登录密码去试，
                //   结果就是 401 —— 只显示"密码不对"的话，人会反复重填同一个密码。
                //
                //   所以把**原因和下一步**直接写进提示里。
                "认证失败（401）：坚果云需要的是「应用密码」，不是登录密码。" +
                    "在坚果云客户端 → 设置 → 第三方应用管理 里生成。"
            )
            403 -> WebDavResult.Failure("没有写入权限（403）")
            404 -> WebDavResult.Failure("路径不存在（404）—— 请先在网盘里建好文件夹")
            409 -> WebDavResult.Failure("父目录不存在（409）—— 请先在网盘里建好文件夹")
            else -> WebDavResult.Failure("上传失败，HTTP $code")
        }
    } catch (e: IOException) {
        WebDavResult.Failure("上传失败：${e.javaClass.simpleName}")
    }

    /**
     * 下载。
     *
     * ★ 返回 `Ok(null)` 表示**云端还没有文件** —— 这不是错误。
     *   第一次用的人云端当然是空的，报错会让人以为配置有问题。
     */
    fun download(config: WebDavConfig): WebDavResult<String?> = try {
        val (code, body) = request(config, "GET", null)
        when (code) {
            200 -> WebDavResult.Ok(body)
            404 -> WebDavResult.Ok(null)
            401 -> WebDavResult.Failure(
                // ★ 这条提示是**被实测逼出来的**（P11 补 39）。
                //
                //   坚果云网页版**已经不再生成应用密码**了，入口挪到了
                //   客户端的「设置 → 第三方应用管理」。而用户很自然会拿登录密码去试，
                //   结果就是 401 —— 只显示"密码不对"的话，人会反复重填同一个密码。
                //
                //   所以把**原因和下一步**直接写进提示里。
                "认证失败（401）：坚果云需要的是「应用密码」，不是登录密码。" +
                    "在坚果云客户端 → 设置 → 第三方应用管理 里生成。"
            )
            403 -> WebDavResult.Failure("没有读取权限（403）")
            else -> WebDavResult.Failure("下载失败，HTTP $code")
        }
    } catch (e: IOException) {
        WebDavResult.Failure("下载失败：${e.javaClass.simpleName}")
    }

    // ------------------------------------------------------------------
    // 底层
    // ------------------------------------------------------------------

    /** @return HTTP 状态码 to 响应体（失败时为 null） */
    private fun request(config: WebDavConfig, method: String, body: ByteArray?): Pair<Int, String?> {
        val conn = (URL(joinUrl(config.serverUrl, config.remotePath)).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Authorization", "Basic " + basicAuth(config.username, config.password))
            conn.setRequestProperty("User-Agent", "NBox/1.0 (Android)")
            if (body != null) {
                conn.doOutput = true
                // 固定长度：否则 HttpURLConnection 会退化成 chunked，
                // 部分 WebDAV 服务端对 chunked 的 PUT 支持不好
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream?.use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                null
            }
            code to text
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 标准 Base64 编码（RFC 4648，带 `=` 填充）。
     *
     * ★ 为什么不用现成的：见类注释 —— Android 的只存在于 Android 上（单测跑不了），
     *   JDK 的又要 API 26。自己写二十行，还能被单测钉住。
     */
    internal fun base64Encode(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            sb.append(table[b0 shr 2])
            sb.append(table[((b0 and 0x03) shl 4) or (b1 shr 4)])
            if (i + 1 < bytes.size) sb.append(table[((b1 and 0x0F) shl 2) or (b2 shr 6)]) else sb.append('=')
            if (i + 2 < bytes.size) sb.append(table[b2 and 0x3F]) else sb.append('=')
            i += 3
        }
        return sb.toString()
    }
}

/** WebDAV 配置。密码存在加密槽位里，不在这里持久化 */
data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    /** 云端文件路径（相对服务器地址）。**父目录要先在网盘里建好** */
    val remotePath: String = "wawu/wuwa_gacha.json",
    /** 每次获取唤取记录后自动上传 */
    val autoUpload: Boolean = true,
) {
    /** 是否填齐了必要项 */
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && remotePath.isNotBlank()
}

sealed interface WebDavResult<out T> {
    data class Ok<T>(val value: T) : WebDavResult<T>
    data class Failure(val message: String) : WebDavResult<Nothing>
}
