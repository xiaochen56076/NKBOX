package com.utilitybox.game.deltaforce.itemprice.data

import com.utilitybox.core.network.ApiEnvelope
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpResult
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.ItemSummary
import com.utilitybox.domain.model.PricePoint
import com.utilitybox.game.deltaforce.itemprice.ItemPriceParser

/**
 * 物品物价的**真实**数据源。
 *
 * 与今日密码一样，本类**只负责"取回并解析"**，不碰缓存、不做业务判定。
 *
 * 实测端点（P01 确认真实可用。**参数名照抄实测结果，勿凭直觉或从响应字段反推**）：
 *  - 搜索：`GET /api/v1/df/object/search?objectName=<名称>`
 *  - 当前价：`GET /api/v1/df/object/price/ams/latest?id=<id>`
 *  - 历史：`GET /api/v1/df/object/price/ams/history/v1?id=<id>&days=7`
 *
 * ★★ P07 实测踩坑（三个参数名全写错，全部 HTTP 400）：
 *
 * | 我凭直觉写的 | 实测正确 | 误区来源 |
 * |---|---|---|
 * | `keyword` | **`objectName`** | 以为搜索参数叫 keyword |
 * | `objectIDs` | **`id`** | 从**响应体**字段 `objectIDs` 反推了请求参数名 |
 * | `objectID` | **`id`** | 同上，从响应字段反推 |
 *
 * **教训**：请求参数名与响应字段名**可以不一致**，绝不能从响应结构反推请求契约。
 *   本项目在 P01 已经把正确参数名实测并写进了 `docs/data-sources.md`，
 *   写代码时应当**照抄文档**而不是凭印象。已加单元测试钉住这三个名字。
 *
 * ★ 三个端点分别封装，而不是合成一个大方法 —— 搜索与详情的数据需求不同，
 *   合成会让"只想要列表"的调用被迫等待不必要的历史请求。
 */
class RemoteItemPriceSource(
    private val http: HttpClient,
    private val isKeyConfigured: () -> Boolean = { DeltaForceApi.isConfigured },
    private val authHeaders: () -> Map<String, String> = DeltaForceApi::authHeaders,
) {

    /** 搜索物品。空关键字由数据源决定行为（当前实现直接返回失败，不发无意义请求）。 */
    suspend fun search(keyword: String, limit: Int = 50): DataResult<List<ItemSummary>> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return DataResult.Ok(emptyList())
        if (!isKeyConfigured()) return DataResult.Err(AppError.ApiKeyMissing)

        return when (val r = http.get(
            path = DeltaForceApi.PATH_OBJECT_SEARCH,
            // ★ 参数名必须是 objectName（P01 实测 + P07 复验）。用 keyword 会得到 HTTP 400。
            query = mapOf("objectName" to kw, "limit" to limit.toString()),
            headers = authHeaders(),
        )) {
            is HttpResult.Failure -> DataResult.Err(r.error)
            is HttpResult.Success -> when (val env = ApiEnvelope.parse(r.body)) {
                is ApiEnvelope.EnvelopeResult.Err -> DataResult.Err(env.error)
                is ApiEnvelope.EnvelopeResult.Ok ->
                    ItemPriceParser.parseSearch(env.data)
                        ?.let { DataResult.Ok(it) }
                        ?: DataResult.Err(AppError.Malformed)
            }
        }
    }

    /**
     * 取当前价。
     *
     * ★ 返回值语义：`DataResult.Ok(null)` 表示**请求成功但该物品无成交数据**，
     *   与 `Err` 完全不同。红线要求"无成交数据"必须显示为「暂无成交数据」，
     *   所以必须能把"没有价格"和"请求失败"区分开。
     */
    suspend fun latestPrice(objectId: String): DataResult<Long?> {
        if (!isKeyConfigured()) return DataResult.Err(AppError.ApiKeyMissing)

        return when (val r = http.get(
            path = DeltaForceApi.PATH_PRICE_LATEST,
            // ★ 参数名是 id（不是 objectIDs —— 那是**响应体**里的字段名）
            query = mapOf("id" to objectId),
            headers = authHeaders(),
        )) {
            is HttpResult.Failure -> DataResult.Err(r.error)
            is HttpResult.Success -> when (val env = ApiEnvelope.parse(r.body)) {
                is ApiEnvelope.EnvelopeResult.Err -> DataResult.Err(env.error)
                is ApiEnvelope.EnvelopeResult.Ok ->
                    // 解析出 null 是合法结果（= 无成交数据），不是错误
                    DataResult.Ok(ItemPriceParser.parseLatestPrice(env.data, objectId))
            }
        }
    }

    /**
     * 取历史价格。
     *
     * ★ 列表中的 `avgPrice` 可能为 null（当日无成交），**必须原样保留**，
     *   图表据此断开而不是插值（红线 PROJECT_RULES.md §3.7）。
     */
    suspend fun history(objectId: String, days: Int = 7): DataResult<List<PricePoint>> {
        if (!isKeyConfigured()) return DataResult.Err(AppError.ApiKeyMissing)

        return when (val r = http.get(
            path = DeltaForceApi.PATH_PRICE_HISTORY,
            // ★ 参数名是 id（不是 objectID）
            query = mapOf("id" to objectId, "days" to days.toString()),
            headers = authHeaders(),
        )) {
            is HttpResult.Failure -> DataResult.Err(r.error)
            is HttpResult.Success -> when (val env = ApiEnvelope.parse(r.body)) {
                is ApiEnvelope.EnvelopeResult.Err -> DataResult.Err(env.error)
                is ApiEnvelope.EnvelopeResult.Ok ->
                    ItemPriceParser.parseHistory(env.data)
                        ?.let { DataResult.Ok(it) }
                        ?: DataResult.Err(AppError.Malformed)
            }
        }
    }
}
