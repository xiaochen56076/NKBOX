package com.utilitybox.game.deltaforce.itemprice

import com.utilitybox.core.cache.CacheEntry
import com.utilitybox.core.cache.LocalCache
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.ItemDetail
import com.utilitybox.domain.model.ItemPriceDetailState
import com.utilitybox.domain.model.ItemSummary
import com.utilitybox.domain.model.PriceHistory
import com.utilitybox.domain.model.PricePoint
import com.utilitybox.game.deltaforce.itemprice.data.RemoteItemPriceSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * 物品物价仓库 —— 网络 + 缓存的编排层。
 *
 * ★ 两条红线在这里落地（PROJECT_RULES.md §3.6/§3.7）：
 *   1. **`hasValidPriceData: false` ⇒ 「暂无成交数据」，不得显示 0 为真实价格**
 *      → 由 [ItemPriceDetailState.NoPriceData] 独立表达，不混进错误态
 *   2. **历史缺失点在图上断开，不插值**
 *      → 缓存与透传都**原样保留 null 点**（[PricePoint.avgPrice] 可空）
 *
 * ★ 详情页需要两个请求（当前价 + 历史），二者可独立失败：
 *   价格成功但历史失败时**照常显示价格**，只在图表区说明 —— 不让一个次要请求
 *   把整个页面变成错误页。
 */
class ItemPriceRepository(
    private val remote: RemoteItemPriceSource,
    private val cache: LocalCache,
) {

    // -----------------------------------------------------------------------
    // 列表 / 搜索
    // -----------------------------------------------------------------------

    /**
     * 搜索物品。
     *
     * ★ 设计稿要求「名称搜索（防抖）」—— 防抖在 UI 层做（见 `ItemPriceScreen`），
     *   仓库只负责"给什么关键字就查什么"，不做时间控制（那是界面职责）。
     */
    suspend fun search(keyword: String): DataResult<List<ItemSummary>> {
        val r = remote.search(keyword)
        if (r is DataResult.Ok) writeSearchCache(keyword, r.value)
        // 搜索失败时回退到上次同关键字的缓存，避免断网后列表完全空白
        if (r is DataResult.Err) {
            readSearchCache(keyword)?.let { return DataResult.Ok(it) }
        }
        return r
    }

    // -----------------------------------------------------------------------
    // 详情
    // -----------------------------------------------------------------------

    /**
     * 取物品详情（当前价 + 历史）。
     *
     * @param summary 列表里已有的条目，避免为了拿名称再请求一次
     */
    suspend fun detail(summary: ItemSummary): ItemPriceDetailState {
        // 1) 当前价
        val price = remote.latestPrice(summary.objectId)
        if (price is DataResult.Err) {
            // ★ 特殊情况（P07 实测发现）：服务端对"该物品在上游没有价格数据"的回应
            //   是 HTTP 400 + `{"code":400,"message":"AMS 响应缺少 dataMap"}`。
            //   这是**没有数据**，不是服务端故障 —— 必须归为 NoPriceData，
            //   否则界面会告诉用户"对方临时故障，稍后重试"，而重试永远不会成功。
            //   红线要求「无成交数据显示『暂无成交数据』」，这里正是那条红线的实际形态。
            if (price.error.noPriceDataSignal()) {
                return ItemPriceDetailState.NoPriceData
            }
            // 其余错误 → 整页失败（价格是详情页的主体）
            return ItemPriceDetailState.Failed(price.error)
        }
        val priceValue = (price as DataResult.Ok).value

        // 2) 历史（次要请求：失败不影响价格展示）
        val historyResult = remote.history(summary.objectId)
        val history: PriceHistory? = when (historyResult) {
            is DataResult.Ok -> {
                writeHistoryCache(summary.objectId, historyResult.value)
                // ★ 空数组必须归一成 null（P07 实测由测试发现）：
                //   若包装成 PriceHistory(points=[])，图表会画出一个**空图表**，
                //   而用户需要的是"为什么没有历史"的解释。
                //   「没有数据」与「有一份空数据」在界面上应当是同一种呈现。
                historyResult.value.takeIf { it.isNotEmpty() }?.let {
                    PriceHistory(
                        objectId = summary.objectId,
                        points = it,
                        source = DeltaForceApi.ref,
                    )
                }
            }

            // 历史取不到（失败或本源不支持）→ 回退缓存；仍没有就交给 UI 说明
            is DataResult.Err,
            is DataResult.Unavailable,
            -> readHistoryCache(summary.objectId)?.let {
                PriceHistory(
                    objectId = summary.objectId,
                    points = it,
                    source = DeltaForceApi.ref,
                )
            }
        }
        val historyError = when (historyResult) {
            is DataResult.Err -> historyResult.error
            is DataResult.Unavailable -> AppError.SourceUnavailable
            is DataResult.Ok -> null
        }

        // 3) 红线：无有效价格数据 → 独立状态，绝不显示 0
        if (priceValue == null) {
            return ItemPriceDetailState.NoPriceData
        }

        return ItemPriceDetailState.Ready(
            detail = ItemDetail(
                summary = summary,
                latestPrice = priceValue,
                priceUpdatedAtLabel = nowLabel(),
                // 价格非空即表示有有效数据（解析层已把 0 归为 null）
                hasValidPriceData = true,
                source = DeltaForceApi.ref,
            ),
            history = history,
            historyError = historyError,
        )
    }

    // -----------------------------------------------------------------------
    // 缓存：列表
    // -----------------------------------------------------------------------

    private fun searchKey(keyword: String) =
        cache.key(GAME_ID, "itemsearch", keyword.trim().lowercase())

    private fun writeSearchCache(keyword: String, items: List<ItemSummary>) {
        cache.write(
            searchKey(keyword),
            CacheEntry(
                schemaVersion = SCHEMA_VERSION,
                fetchedAtMillis = LocalDate.now(ZoneId.systemDefault()).toEpochMillis(),
                sourceId = DeltaForceApi.ref.id,
                isMock = false,
                payload = items.joinToString("\n") {
                    // 价格可能为 null（无成交）；用空串表示，读回时还原为 null
                    listOf(
                        it.objectId,
                        it.name,
                        it.shortName ?: "",
                        it.grade?.toString() ?: "",
                        it.category ?: "",
                        it.avgPrice?.toString() ?: "",
                    ).joinToString("\t")
                },
            ),
        )
    }

    private fun readSearchCache(keyword: String): List<ItemSummary>? {
        val e = cache.read(searchKey(keyword)) ?: return null
        if (!e.isUsable(SCHEMA_VERSION)) {
            cache.remove(searchKey(keyword))
            return null
        }
        val items = e.payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 6) return@mapNotNull null
                ItemSummary(
                    objectId = p[0],
                    name = p[1],
                    shortName = p[2].ifBlank { null },
                    grade = p[3].toIntOrNull(),
                    category = p[4].ifBlank { null },
                    // ★ 空串 → null，绝不还原成 0（红线）
                    avgPrice = p[5].toLongOrNull()?.takeIf { it > 0 },
                )
            }
            .toList()
        return items.ifEmpty { null }
    }

    // -----------------------------------------------------------------------
    // 缓存：历史
    // -----------------------------------------------------------------------

    private fun historyKey(objectId: String) = cache.key(GAME_ID, "itemhistory", objectId)

    private fun writeHistoryCache(objectId: String, points: List<PricePoint>) {
        cache.write(
            historyKey(objectId),
            CacheEntry(
                schemaVersion = SCHEMA_VERSION,
                fetchedAtMillis = LocalDate.now(ZoneId.systemDefault()).toEpochMillis(),
                sourceId = DeltaForceApi.ref.id,
                isMock = false,
                // ★ 价格空串表示"该日无成交"，读回时还原为 null —— 缺失点必须保留，
                //   否则图表会把断点连起来（红线）。样本数也一并存，供 UI 说明。
                payload = points.joinToString("\n") {
                    "${it.date}\t${it.avgPrice?.toString() ?: ""}\t${it.sampleCount}"
                },
            ),
        )
    }

    private fun readHistoryCache(objectId: String): List<PricePoint>? {
        val e = cache.read(historyKey(objectId)) ?: return null
        if (!e.isUsable(SCHEMA_VERSION)) {
            cache.remove(historyKey(objectId))
            return null
        }
        val points = e.payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 3) return@mapNotNull null
                val date = ItemPriceParser.parseDate(p[0]) ?: return@mapNotNull null
                PricePoint(
                    date = date,
                    avgPrice = p[1].toLongOrNull()?.takeIf { it > 0 },
                    sampleCount = p[2].toIntOrNull() ?: 0,
                )
            }
            .toList()
        return points.ifEmpty { null }
    }

    private fun nowLabel(): String {
        val now = java.time.LocalTime.now()
        return "%02d:%02d".format(now.hour, now.minute)
    }

    companion object {
        const val GAME_ID = "deltaforce"

        /** 缓存结构版本。改动 payload 格式时必须 +1，旧缓存会被安全丢弃。 */
        const val SCHEMA_VERSION = 1

        private fun LocalDate.toEpochMillis(): Long =
            atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

/** 供 UI 复用的"未配 Key / 拒绝"判定（与今日密码保持一致的错误语义） */
fun AppError.isAuthRelated(): Boolean =
    this == AppError.ApiKeyMissing || this == AppError.Unauthorized

/**
 * 判断一个错误是否其实是**「该物品没有价格数据」**。
 *
 * ★ 依据（P07 用真实请求拿到服务端原话）：
 *   对上游没有价格数据的物品，服务端返回
 *   `HTTP 400  {"code":400,"message":"AMS 响应缺少 dataMap"}`
 *
 *   这是**数据缺失**而非**服务故障** —— 重试永远不会成功，
 *   所以不能显示成"临时故障，稍后重试"（那是误导用户）。
 *   红线要求「无成交数据 ⇒ 显示『暂无成交数据』」，本函数就是那条判定。
 *
 * ★ 只在文案明确指向"缺少数据"时才这样归类，避免把真正的服务端错误
 *   也吞成"没有数据"——那会把故障伪装成正常状态，是更严重的问题。
 */
internal fun AppError.noPriceDataSignal(): Boolean {
    val e = this as? AppError.Business ?: return false
    val m = e.message
    return m.contains("缺少 dataMap") || m.contains("缺少dataMap") ||
        m.contains("暂无数据") || m.contains("无成交")
}
