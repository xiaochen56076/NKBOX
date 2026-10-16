package com.utilitybox.core.network

import android.util.Log
import com.utilitybox.domain.model.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * 基于平台内置 `HttpURLConnection` 的实现（**零新依赖**）。
 *
 * 选择它的理由（见 PROJECT_RULES.md 第 5 节）：本机已验证可用；
 * 且网络层已被 [HttpClient] 抽象，将来要换 OkHttp 等只需替换本类。
 */
class UrlConnectionHttpClient(
    private val config: HttpConfig,
) : HttpClient {

    override suspend fun get(
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
    ): HttpResult = withContext(Dispatchers.IO) {
        val url = buildUrl(path, query)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = config.connectTimeoutMs
                readTimeout = config.readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                config.defaultHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                // ★★ P07 实测踩到的关键设计问题：
                //   原先这里**直接**把非 2xx 映射成 AppError.Server 就返回了，
                //   于是响应体从未被解析。但本数据源在 4xx 时会把**业务原因写在 body 里**：
                //     HTTP 400  {"code":400,"message":"AMS 响应缺少 dataMap"}
                //   这条信息决定了"这是没有数据还是服务故障"——正是红线所在，
                //   丢掉它就只能给出"对方临时故障，稍后重试"这种**误导性**的结论。
                //
                //   因此改为：**4xx 把响应体带回上层解析**（上层能识别业务码）；
                //   若解析不出有效信封，再退回按 HTTP 码归类。
                //   5xx 仍然直接归为服务端故障（那不是业务语义）。
                val errBody = try {
                    conn.errorStream?.use { it.readBytes().decodeToString() }
                } catch (e: Exception) {
                    null
                }
                Log.w(TAG, "HTTP $code  $url\n  响应体: ${errBody?.take(500) ?: "（无）"}")

                return@withContext if (code in 400..499 && !errBody.isNullOrBlank()) {
                    // 交给上层解析信封；解析不出来时上层会归为 Malformed，
                    // 那也比笼统的"服务异常"更接近事实（至少说明响应不可识别）。
                    HttpResult.Success(errBody)
                } else {
                    HttpResult.Failure(mapHttpError(code))
                }
            }

            val body = conn.inputStream.use { it.readBytes().decodeToString() }
            if (body.isBlank()) {
                HttpResult.Failure(AppError.Malformed)
            } else {
                // ★ 成功时也留一条**结构摘要**（P10 实测排查需要）：
                //   弹药接口全部返回 hasValidPriceData=false 时，只看界面无法判断
                //   是"接口确实没给价格"还是"我们字段名解析错了"。
                //   摘要只记路径与响应体**前 300 字符**，不含请求头（因此不含 API Key）。
                if (DEBUG_LOG_BODY) {
                    Log.d(TAG, "HTTP $code  $url\n  响应摘要: ${body.take(BODY_LOG_CHARS)}")
                }
                HttpResult.Success(body)
            }
        } catch (e: SocketTimeoutException) {
            HttpResult.Failure(AppError.Timeout)
        } catch (e: UnknownHostException) {
            HttpResult.Failure(AppError.NoNetwork)
        } catch (e: IOException) {
            // 无连接、连接被拒、读取中断等统一归为网络不可用
            HttpResult.Failure(AppError.NoNetwork)
        } catch (e: Exception) {
            HttpResult.Failure(AppError.Unknown(e.message ?: e::class.java.simpleName))
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = config.baseUrl.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        if (query.isEmpty()) return base + p
        val qs = query.entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        return "$base$p?$qs"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun mapHttpError(code: Int): AppError = when (code) {
        401, 403 -> AppError.Unauthorized
        // 402 是本数据源的「余额不足」（积分制），归为业务错误并保留原文
        402 -> AppError.Business(code, "余额不足（该数据源按积分计费）")
        in 500..599 -> AppError.Server(code)
        else -> AppError.Server(code)
    }

    private companion object {
        const val USER_AGENT = "UtilityBox/0.1 (Android)"
        const val TAG = "UbHttp"

        /**
         * 是否记录成功响应的**结构摘要**（仅前 300 字符，不含请求头）。
         *
         * ★ 为什么需要：与"仅失败时打日志"不同，数据源可能返回
         *   `hasValidPriceData=false` 这类**成功但没数据**的响应。
         *   这时只看界面无法区分"接口确实没给"与"我们字段名解析错了"——
         *   P10 实测就卡在这个判断上。
         *
         * 安全性：只记路径与响应体前 300 字符，**不含请求头**，因此不含 API Key。
         * debug 构建开启；release 关闭，避免把业务数据写进日志。
         */
        val DEBUG_LOG_BODY: Boolean = com.utilitybox.BuildConfig.DEBUG

        /**
         * 摘要记录多少字符。
         *
         * ★ 取值经过实测调整：300 字符在弹药接口上**刚好截断在字段中间**，
         *   看不到价格字段到底叫什么 —— 那次排查就是因此多绕了几轮。
         *   2000 字符足以覆盖单条记录的全部字段。
         */
        const val BODY_LOG_CHARS = 2000
    }
}
