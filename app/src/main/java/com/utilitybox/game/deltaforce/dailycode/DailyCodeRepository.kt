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
     * @param forceRefresh 跳过"同一天直接读缓存"的短路，强制走网络。
     *        **手动刷新 / 自动刷新定时器**必须传 true；页面首次进入传 false。
     */
    suspend fun load(
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        forceRefresh: Boolean = false,
    ): DailyCodeState {
        val key = cache.key(GAME_ID, CACHE_TYPE)

        // 0) ★★ P11 补 91：**同一自然日内直接用缓存，不发网络请求**。
        //
        //   用户反馈：「每次切换界面后今日密码都会重新加载，应该有一个缓存吧，
        //   记录一个时间节点，一旦过了当天就自动刷新」。
        //
        //   原来这条短路不存在 —— 每次 `load()` 都先打网络（见旧代码注释"1) 先试网络"），
        //   于是每次进首页都会闪一下「加载中…」，即使数据就是几分钟前刚取的。
        //
        //   现在的规则（就是用户说的那条）：
        //   · 缓存日期 == 今天 → 直接返回，**零请求**、无 Loading；
        //   · 缓存日期 < 今天（跨天）→ 当作过期，走下面的正常网络流程；
        //   · 没有缓存 → 走网络。
        //
        //   ★ 为什么用「是否同一天」而不是「几小时」：
        //     今日密码的语义就是**按自然日**变化（接口无日期字段，
        //     只能靠本机抓取日期推断 —— 见 `CachePolicy` 的说明）。
        //     用小时数会在 23:59 抓、00:01 还是"新鲜"的错误状态。
        if (!forceRefresh) {
            val cached = readCache(key)
            if (cached != null && cached.fetchedDate == today) {
                return DailyCodeParser.classify(
                    codes = cached.codes,
                    fetchedAt = today,
                    today = today,
                    source = DeltaForceApi.ref,
                    // 用**缓存写入时的真实时刻**，不是"现在" ——
                    // 否则每次进页面时间戳都会变，看起来像刚刷新过（假话）
                    updatedAtLabel = cached.updatedAtLabel,
                )
            }
        }

        // 1) 网络
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
                // ★ P11 补 91：存**注入日期的当天真实时刻**，而不是当天零点。
                //
                //   原因：缓存命中时要显示"更新于 HH:mm"，那必须是**写入时**的时刻，
                //   不能是"现在"（否则每次进页面时间戳都在变，看起来像刚刷新过）。
                //   零点本身没有时分信息，做不到这一点。
                //
                //   ★ 为什么是 `today` + 当前时分，而不是 `System.currentTimeMillis()`：
                //     单元测试会**注入** today 来构造"昨天的缓存"，
                //     直接取系统时间会让注入失效。用注入日期 + 今天的时分，
                //     既保留可测试性，又能还原出真实时分。
                fetchedAtMillis = today.withCurrentTimeOfDay(),
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
        return CachedCodes(
            codes = codes,
            fetchedDate = epochMillisToDate(e.fetchedAtMillis),
            // ★ P11 补 91：从缓存时间戳还原"更新于 HH:mm"
            updatedAtLabel = labelOf(e.fetchedAtMillis),
        )
    }

    private data class CachedCodes(
        val codes: List<DailyCode>,
        val fetchedDate: LocalDate,
        /** 缓存写入时的时分标签，供"更新于"显示 —— 必须是写入时刻，不是读取时刻 */
        val updatedAtLabel: String,
    )

    private fun nowLabel(): String {
        val now = java.time.LocalTime.now()
        return "%02d:%02d".format(now.hour, now.minute)
    }

    /** 把 epochMillis 还原成 `HH:mm`（系统时区） */
    private fun labelOf(epochMillis: Long): String {
        val t = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return "%02d:%02d".format(t.hour, t.minute)
    }

    companion object {
        const val GAME_ID = "deltaforce"
        const val CACHE_TYPE = "dailycode"

        /**
         * 缓存结构版本。**改动 payload 格式时必须 +1**，旧缓存会被安全丢弃。
         *
         * 历史：
         * - 1：初版
         * - 2：P11 补 91 —— `fetchedAtMillis` 的语义从「当天零点」改成
         *      「当天 + 真实时分」。payload 格式没变，但**这个字段的含义变了**：
         *      旧缓存按新代码读出来会显示「更新于 00:00」（假的时间）。
         *      所以必须整体丢弃一次，让它们重新抓一次。
         *      ★ 这类"格式没变但语义变了"同样要升版本 —— 否则会显示错误数据。
         */
        const val SCHEMA_VERSION = 2

        // LocalDate <-> epochMillis（用系统时区的当天零点，只用于"哪一天"的比较）
        private fun LocalDate.toEpochMillis(): Long =
            atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        /**
         * 注入日期 + **当前时分** 的时间戳（P11 补 91）。
         *
         * 用来把"更新于 HH:mm"持久化进缓存，同时保证单元测试注入的 today 仍然生效。
         */
        private fun LocalDate.withCurrentTimeOfDay(): Long {
            val nanos = java.time.LocalTime.now().toNanoOfDay()
            return atStartOfDay(ZoneId.systemDefault())
                .plusNanos(nanos)
                .toInstant()
                .toEpochMilli()
        }

        private fun epochMillisToDate(ms: Long): LocalDate =
            java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

/** "今天"的日期字符串（UI 展示用，如 2026 年 10 月 14 日 周三） */
fun formatDateCn(d: LocalDate): String {
    val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
    return "${d.year} 年 ${d.monthValue} 月 ${d.dayOfMonth} 日 $week"
}
