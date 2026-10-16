package com.utilitybox.game.deltaforce.ammoprice

import com.utilitybox.core.cache.CacheEntry
import com.utilitybox.core.cache.LocalCache
import com.utilitybox.core.network.ApiEnvelope
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpResult
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.AmmoPrice
import com.utilitybox.domain.model.AppError
import java.time.LocalDate
import java.time.ZoneId

/**
 * 弹药物价的真实数据源 + 缓存编排。
 *
 * 与物品物价同一模式，但**没有**独立的详情页请求：
 * 弹药接口一次性返回全部条目的价格与涨跌，不需要二次请求。
 */
class AmmoPriceRepository(
    private val http: HttpClient,
    private val cache: LocalCache,
    private val isKeyConfigured: () -> Boolean = { DeltaForceApi.isConfigured },
    private val authHeaders: () -> Map<String, String> = DeltaForceApi::authHeaders,
) {

    suspend fun load(): DataResult<List<AmmoPrice>> {
        if (!isKeyConfigured()) return DataResult.Err(AppError.ApiKeyMissing)

        return when (val r = http.get(
            path = DeltaForceApi.PATH_OBJECT_AMMO,
            // ★ 参数名 days：P01 实测确认（弹药端点的参数与价格端点不同）
            query = mapOf("days" to "7"),
            headers = authHeaders(),
        )) {
            is HttpResult.Failure -> fallbackCache(r.error)
            is HttpResult.Success -> when (val env = ApiEnvelope.parse(r.body)) {
                is ApiEnvelope.EnvelopeResult.Err -> fallbackCache(env.error)
                is ApiEnvelope.EnvelopeResult.Ok -> {
                    val list = AmmoPriceParser.parse(env.data)
                    when {
                        list == null -> DataResult.Err(AppError.Malformed)
                        else -> {
                            // ★ 记一条**聚合**摘要，而不是原始响应。
                            //   原始响应很长，会被 logcat 的行长度上限截断
                            //   （实测 2000 字符仍拿不全 113 条），
                            //   而"多少条有价格、等级分布如何"才是排查真正需要的。
                            if (com.utilitybox.BuildConfig.DEBUG) {
                                val withPrice = list.count { it.latestPrice != null }
                                val levels = list.mapNotNull { it.penetrationLevel }.distinct().sorted()
                                android.util.Log.d(
                                    "UbAmmo",
                                    "弹药 ${list.size} 条｜有价格 $withPrice 条｜" +
                                        "击穿等级 $levels｜样本合计 ${list.sumOf { it.dataPoints ?: 0 }}",
                                )
                            }
                            writeCache(list)
                            DataResult.Ok(list)
                        }
                    }
                }
            }
        }
    }

    /** 网络失败时回退缓存；没有缓存才如实报错 */
    private fun fallbackCache(error: AppError): DataResult<List<AmmoPrice>> {
        val cached = readCache()
        return if (cached != null) DataResult.Ok(cached) else DataResult.Err(error)
    }

    // -----------------------------------------------------------------------
    // 缓存
    // -----------------------------------------------------------------------

    private fun key() = cache.key(GAME_ID, "ammoprice")

    private fun writeCache(list: List<AmmoPrice>) {
        cache.write(
            key(),
            CacheEntry(
                schemaVersion = SCHEMA_VERSION,
                fetchedAtMillis = LocalDate.now(ZoneId.systemDefault())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                sourceId = DeltaForceApi.ref.id,
                isMock = false,
                // ★ 价格用空串表示"无成交"，读回时还原成 null —— 绝不变成 0
                //
                // ★★ 字段集扩过一次（SCHEMA_VERSION 1 → 2）：
                //   旧格式只存了 6 个字段，读回来**没有图片和击穿等级**，
                //   表现为"冷启动后图片消失、等级不显示" —— 一个很容易被忽略的 bug。
                //   教训：缓存格式必须与展示所需的字段**同步演进**，
                //   否则离线/冷启动路径会退化成一个功能更弱的版本。
                payload = list.joinToString("\n") { a ->
                    listOf(
                        a.objectId,
                        a.name,
                        a.caliber ?: "",
                        a.grade?.toString() ?: "",
                        a.latestPrice?.toString() ?: "",
                        a.changePercent?.toString() ?: "",
                        a.iconUrl ?: "",
                        a.penetrationLevel?.toString() ?: "",
                        a.armorHarmLevel ?: "",
                        a.caliberGroup ?: "",
                        a.dataPoints?.toString() ?: "",
                    ).joinToString("\t")
                },
            ),
        )
    }

    private fun readCache(): List<AmmoPrice>? {
        val e = cache.read(key()) ?: return null
        if (!e.isUsable(SCHEMA_VERSION)) {
            cache.remove(key())
            return null
        }
        val list = e.payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < CACHE_FIELDS) return@mapNotNull null
                AmmoPrice(
                    objectId = p[0],
                    name = p[1],
                    caliber = p[2].ifBlank { null },
                    grade = p[3].toIntOrNull(),
                    // ★ 空串 → null；0 或负数也视为无数据（红线）
                    latestPrice = p[4].toLongOrNull()?.takeIf { it > 0 },
                    changePercent = p[5].toDoubleOrNull(),
                    hasValidPriceData = p[4].toLongOrNull()?.let { it > 0 } ?: false,
                    source = DeltaForceApi.ref,
                    iconUrl = p[6].ifBlank { null },
                    penetrationLevel = p[7].toIntOrNull(),
                    armorHarmLevel = p[8].ifBlank { null },
                    caliberGroup = p[9].ifBlank { null },
                    dataPoints = p[10].toIntOrNull(),
                )
            }
            .toList()
        return list.ifEmpty { null }
    }

    companion object {
        const val GAME_ID = "deltaforce"

        /**
         * 缓存结构版本。改动 payload 格式时必须 +1，旧缓存会被安全丢弃。
         *
         * 变更记录：1 → 2 时新增了图片 URL、击穿等级、护甲伤害、口径分组、样本数。
         */
        const val SCHEMA_VERSION = 2

        /** payload 每行的字段数（与 writeCache 保持一致） */
        private const val CACHE_FIELDS = 11
    }
}
