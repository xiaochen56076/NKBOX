package com.utilitybox.core.network

import com.utilitybox.core.json.Json
import com.utilitybox.domain.model.AppError

/**
 * 数据源响应信封解析。
 *
 * 实测（见 DATA_SOURCES.md 第 0 节）：社区接口统一返回
 * `{"code":0,"message":"成功","data":{...}}`，`code == 0` 为成功。
 *
 * ★ 设计要点：**信封差异必须封在各自的数据源实现内**，不得让业务层感知。
 *   已确认不同数据源的信封完全不同（例如数据帝是 `?token=` 形式、
 *   AMS 原始是 `{ret,iRet,sMsg,jData}`），所以本解析器**不是通用工具**，
 *   而是"当前这家数据源的信封解析"。
 *
 * ★ 为什么不用 Kotlin 的 `Result<T>`：它要求失败携带 `Throwable`，
 *   而 [AppError] 是密封类型而非异常。把错误硬塞进异常会丢类型信息，
 *   调用方还得 `as` 强转。故用本项目的 [EnvelopeResult]。
 *
 * ★ 为什么用自写的 [Json] 而不是 `org.json`：后者在单元测试里是空壳 stub，
 *   会让所有解析逻辑无法在无设备环境下验证。详见 `core/json/Json.kt` 的说明。
 *
 * 本类**不依赖 Android 框架**，可直接单元测试。
 */
object ApiEnvelope {

    /** 解析结果：成功给出 `data` 对象，失败给出结构化错误 */
    sealed interface EnvelopeResult {
        data class Ok(val data: Json) : EnvelopeResult
        data class Err(val error: AppError) : EnvelopeResult
    }

    fun parse(body: String): EnvelopeResult {
        val root = Json.parse(body) as? Json.Obj
            ?: return EnvelopeResult.Err(AppError.Malformed)

        val code = root.int("code")
            ?: return EnvelopeResult.Err(AppError.Malformed) // 不是我们认识的信封

        if (code != 0) {
            val message = root.str("message")?.takeIf { it.isNotBlank() }
                ?: root.str("msg").orEmpty()
            return EnvelopeResult.Err(
                when (code) {
                    401, 403 -> AppError.Unauthorized
                    402 -> AppError.Business(code, message.ifBlank { "余额不足" })
                    else -> AppError.Business(code, message)
                }
            )
        }

        val data = root.obj("data") ?: return EnvelopeResult.Err(AppError.Malformed)
        return EnvelopeResult.Ok(data)
    }

    /** 便捷：成功时取 data，否则取错误 */
    fun dataOrError(body: String): Pair<Json?, AppError?> = when (val r = parse(body)) {
        is EnvelopeResult.Ok -> r.data to null
        is EnvelopeResult.Err -> null to r.error
    }
}
