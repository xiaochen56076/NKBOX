package com.utilitybox.game.deltaforce.dailycode.data

import com.utilitybox.core.network.ApiEnvelope
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpResult
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCode
import com.utilitybox.game.deltaforce.dailycode.DailyCodeParser

/**
 * 今日密码的**真实**数据源。
 *
 * 职责边界（重要）：本类**只负责"取回并解析"**，不碰缓存、不判定四态。
 * 缓存与四态判定在 `DailyCodeRepository` 里 —— 那样才能对"过期缓存"这类
 * 跨请求的状态做单元测试（见 docs/architecture.md §2 的分层原则）。
 *
 * 依赖 [HttpClient] 接口而非具体实现，因此**可用假实现做单元测试**，无需真实网络。
 *
 * ★ 关于「是否已配置 Key」为什么是**构造参数**而不是直接读全局配置：
 *   P06 实测踩到 —— 直接读全局配置会让单元测试（没有 Key）在守卫处全部短路，
 *   测试根本走不到被测逻辑。改成参数后：
 *     - 生产：由 `DailyCodeGraph` 传入 [ApiKeyProvider] 的**实时读取**
 *     - 测试：可显式指定，**守卫本身也能被验证**
 *
 * ★ 为什么 Key 是**函数**而不是字符串：
 *   用户在设置页填完 Key 后必须立即生效。若在构造时把 Key 拷成字符串，
 *   填完得重启应用 —— 体验很差。
 */
class RemoteDailyCodeSource(
    private val http: HttpClient,
    /** 是否已配置 Key。未配置时不发无意义的请求，直接给出可解释的结果。 */
    private val isKeyConfigured: () -> Boolean = { DeltaForceApi.isConfigured },
    /** 鉴权头。每次请求时求值，因此设置页改完立即生效。 */
    private val authHeaders: () -> Map<String, String> = DeltaForceApi::authHeaders,
) {

    suspend fun fetch(): DataResult<List<DailyCode>> {
        // 未配置 Key 时不发请求：请求注定失败，而"没配 Key"与"请求失败"是两回事，
        // UI 需要能分别提示。
        // ★ 用 ApiKeyMissing 而不是 Unauthorized —— 后者是"服务端拒绝了 Key"，
        //   两者用户要采取的行动不同（见 AppError.ApiKeyMissing 的注释）。
        if (!isKeyConfigured()) {
            return DataResult.Err(AppError.ApiKeyMissing)
        }

        return when (val r = http.get(
            path = DeltaForceApi.PATH_DAILY_KEYWORD,
            headers = authHeaders(),
        )) {
            is HttpResult.Failure -> DataResult.Err(r.error)

            is HttpResult.Success -> when (val env = ApiEnvelope.parse(r.body)) {
                is ApiEnvelope.EnvelopeResult.Err -> DataResult.Err(env.error)

                is ApiEnvelope.EnvelopeResult.Ok -> {
                    val codes = DailyCodeParser.parse(env.data)
                        ?: return DataResult.Err(AppError.Malformed)
                    DataResult.Ok(codes)
                }
            }
        }
    }
}
