package com.utilitybox.game.deltaforce.dailycode

import com.utilitybox.core.cache.CacheEntry
import com.utilitybox.core.cache.LocalCache
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCode
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.game.deltaforce.dailycode.data.RemoteDailyCodeSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * 今日密码仓库 —— **网络 + 缓存的编排层**，四态判定在这里完成。
 *
 * ★ 为什么把四态判定放在仓库而不是 ViewModel：
 *   「过期缓存」是**跨请求**的状态（本次失败 + 上次成功的数据），
 *   放在仓库里才能用假数据源 + 假缓存做**纯 JVM 单元测试**，
 *   而这条正是设计稿要求的关键验证（构造过期缓存场景，界面显示原始日期）。
 *
 * ★ 红线（PROJECT_RULES.md §3.4/§3.5）：
 *   - 接口**没有日期字段**，因此"当天有效"只能由**本机抓取日期**推断，UI 必须写明这一点
 *   - 拿不到今天的数据时，**绝不**把昨天的当今天展示 —— 走 [DailyCodeState.Stale] 并带上原始日期
 */
class DailyCodeRepository(
    private val remote: RemoteDailyCodeSource,
    private val cache: LocalCache,
) {

    /**
     * 取今日密码。
     *
     * @param today 注入"今天"，便于单元测试构造过期场景
     */
    suspend fun load(today: LocalDate = LocalDate.now(ZoneId.systemDefault())): DailyCodeState {
        val key = cache.key(GAME_ID, CACHE_TYPE)

        // 1) 先试网络
        when (val r = remote.fetch()) {
            is DataResult.Ok -> {
                writeCache(key, r.value, today)
                return DailyCodeParser.classify(
                    codes = r.value,
                    fetchedAt = today,
                    today = today,
                    source = DeltaForceApi.ref,
                    updatedAtLabel = nowLabel(),
                )
            }

            is DataResult.Err -> {
                // 2) 网络失败 → 回退缓存（这是「过期缓存」态的唯一来源）
                val cached = readCache(key)
                if (cached != null) {
                    return DailyCodeParser.classify(
                        codes = cached.codes,
                        fetchedAt = cached.fetchedDate,
                        today = today,
                        source = DeltaForceApi.ref,
                        updatedAtLabel = "",
                    )
                }
                // 3) 无缓存 → 如实报错，不编造
                return DailyCodeParser.failed(r.error)
            }

            is DataResult.Unavailable -> {
                // 本源无法提供该能力（本期不会走到，保留分支以示完整）
                val cached = readCache(key)
                return if (cached != null) {
                    DailyCodeParser.classify(
                        codes = cached.codes,
                        fetchedAt = cached.fetchedDate,
                        today = today,
                        source = DeltaForceApi.ref,
                        updatedAtLabel = "",
                    )
                } else {
                    DailyCodeParser.failed(AppError.SourceUnavailable)
                }
            }
        }
    }

    private suspend fun writeCache(key: String, codes: List<DailyCode>, today: LocalDate) {
        cache.write(
            key,
            CacheEntry(
                schemaVersion = SCHEMA_VERSION,
                fetchedAtMillis = today.toEpochMillis(),
                sourceId = DeltaForceApi.ref.id,
                isMock = false,
                // 缓存体：简单的行式编码，避免为此引入序列化依赖。
                // 结构变更时靠 schemaVersion 整体丢弃，不做兼容猜测。
                payload = codes.joinToString("\n") { "${it.mapId}\t${it.mapName}\t${it.code}" },
            ),
        )
    }

    private fun readCache(key: String): CachedCodes? {
        val e = cache.read(key) ?: return null
        if (!e.isUsable(SCHEMA_VERSION)) {
            // 结构已变更 → 丢弃，绝不用新代码去读旧结构（红线）
            cache.remove(key)
            return null
        }
        val codes = e.payload.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@mapNotNull null
                DailyCode(mapId = parts[0], mapName = parts[1], code = parts[2])
            }
            .toList()
        if (codes.isEmpty()) return null
        return CachedCodes(codes, epochMillisToDate(e.fetchedAtMillis))
    }

    private data class CachedCodes(val codes: List<DailyCode>, val fetchedDate: LocalDate)

    private fun nowLabel(): String {
        val now = java.time.LocalTime.now()
        return "%02d:%02d".format(now.hour, now.minute)
    }

    companion object {
        const val GAME_ID = "deltaforce"
        const val CACHE_TYPE = "dailycode"

        /** 缓存结构版本。**改动 payload 格式时必须 +1**，旧缓存会被安全丢弃。 */
        const val SCHEMA_VERSION = 1

        // LocalDate <-> epochMillis（用系统时区的当天零点，只用于"哪一天"的比较）
        private fun LocalDate.toEpochMillis(): Long =
            atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        private fun epochMillisToDate(ms: Long): LocalDate =
            java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

/** "今天"的日期字符串（UI 展示用，如 2026 年 10 月 14 日 周三） */
fun formatDateCn(d: LocalDate): String {
    val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
    return "${d.year} 年 ${d.monthValue} 月 ${d.dayOfMonth} 日 $week"
}
