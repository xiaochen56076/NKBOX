package com.utilitybox.domain.mock

import com.utilitybox.domain.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ★ 模拟数据源 —— 开发期占位。
 *
 * 依据 PROJECT_RULES.md §8，mock 必须满足**全部三条**：
 *  1. 类名以 `Mock` 开头（本文件即是）
 *  2. 模型携带 `isMock = true`
 *  3. UI 上有可见标记（`UbMockBadge`）
 *
 * 交付前必须能通过全局搜索 `isMock = true` 定位所有残留 mock。
 * ★ 绝不允许把本文件的任何数据当作真实功能交付（红线 §2.2）。
 *
 * 各数据源的**真实可用性**结论见 DATA_SOURCES.md：
 *  - 今日密码  ✅ 已验证可用（但接口无日期字段）
 *  - 物品物价  ✅ 已验证可用
 *  - 好友状态  🔴 **无任何可用数据源**（P01 已实测排除 6 个候选端点）
 */

private val MOCK_SOURCE = DataSourceRef(
    id = "mock",
    displayName = "模拟数据（非真实）",
    verified = SourceState.UNAVAILABLE,
)

/** 数据源状态文案 —— 供 UI 如实展示 */
object FriendStatusCopy {
    /** 数据源不可用时的诚实文案（真实实现走这条路径） */
    const val UNAVAILABLE_TITLE = "当前无可用数据源"
    const val UNAVAILABLE_DESC =
        "好友在线状态需要游戏提供好友状态接口。经实测核验，现有公开数据源均未提供该能力，" +
            "因此本功能暂停开发。为避免误导，我们不会用任何推测或模拟数据填充这个列表。"

    /** 已核验并被排除的候选接口（用于让用户看到"我们确实查过"） */
    val VERIFIED_CANDIDATES = listOf(
        "friendinfo → 需付费，且只返回昵称/头像，无状态字段",
        "gamesafe/online → 只能查自己，需微信凭据，无「游戏中」",
        "person/friends、friend、friendlist → 实测 404",
        "df/friend/status、social/friends → 实测 404",
    )
}

/** 今日密码数据源（真实源已验证，字段：mapID / mapName / secret） */
object MockDailyCodeSource {

    val sourceRef = MOCK_SOURCE

    /** 真实接口实测返回的 6 张地图（结构与真实一致） */
    fun codes(): List<DailyCode> = listOf(
        DailyCode("1", "零号大坝", "0144"),
        DailyCode("2", "长弓溪谷", "8288"),
        DailyCode("3", "巴克什", "4400"),
        DailyCode("4", "航天基地", "0201"),
        DailyCode("5", "潮汐监狱", "2643"),
        DailyCode("6", "AZ3", "2520"),
    )

    private val fmt = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")

    fun today(date: LocalDate = LocalDate.now()): DailyCodeState.Today = DailyCodeState.Today(
        codes = codes(),
        source = sourceRef,
        updatedAtLabel = date.format(fmt) + " 08:12",
        isMock = true,
    )

    /** 过期缓存样例 —— 用于验证"过期数据不冒充今日"的红线 */
    fun stale(date: LocalDate = LocalDate.now().minusDays(1)): DailyCodeState.Stale =
        DailyCodeState.Stale(
            codes = codes(),
            fetchedDate = date,
            source = sourceRef,
            isMock = true,
        )
}

/** 物品物价数据源 */
object MockItemPriceSource {

    val sourceRef = MOCK_SOURCE

    private val today: LocalDate get() = LocalDate.now()

    fun items(): List<ItemSummary> = listOf(
        // avgPrice = null ⇒ 无成交数据（**不是 0**）
        ItemSummary("13020000524", "M250钛金长枪管", "M250", 5, "枪械配件", 49258),
        ItemSummary("15010010004", "含氟牙膏", null, 2, "制造材料", 2263),
        ItemSummary("15020010003", "布基胶带", null, 1, "制造材料", 1215),
        ItemSummary("15020010007", "电动爆破锤", null, 4, "制造材料", 4197),
        ItemSummary("37150600001", "6.8x51mm AP", "6.8x51", 6, "弹药", null),
    )

    /** 真实密度样例：7 天 7 点，含 count（P01 实测结构） */
    fun history(objectId: String): PriceHistory {
        val base = items().firstOrNull { it.objectId == objectId }?.avgPrice ?: 49000
        val days = listOf(49268L, 49268L, 49322L, 53894L, 49248L, 49270L, base)
        val counts = listOf(371, 356, 359, 357, 359, 339, 130)
        return PriceHistory(
            objectId = objectId,
            points = days.indices.map { i ->
                PricePoint(
                    date = today.minusDays((days.size - 1 - i).toLong()),
                    avgPrice = days[i],
                    sampleCount = counts[i],
                )
            },
            source = sourceRef,
            isMock = true,
        )
    }

    /** 缺失点在图上是**断开**的，不是插值补出来的 —— 用于验证图表空态 */
    fun historyWithGap(objectId: String): PriceHistory {
        val base = listOf<Long?>(49268, null, 49322, null, 49248, 49270, 49288)
        return PriceHistory(
            objectId = objectId,
            points = base.mapIndexed { i, v ->
                PricePoint(
                    date = today.minusDays((base.size - 1 - i).toLong()),
                    avgPrice = v,
                    sampleCount = if (v == null) 0 else 350,
                )
            },
            source = sourceRef,
            isMock = true,
        )
    }

    /** 有效点不足 2 个 ⇒ trend 必须是 UNKNOWN，不得伪装成持平 */
    fun historyInsufficient(): PriceHistory = PriceHistory(
        objectId = "x",
        points = listOf(PricePoint(today.minusDays(1), 49268, 371)),
        source = sourceRef,
        isMock = true,
    )

    fun detail(objectId: String): ItemDetail {
        val s = items().first { it.objectId == objectId }
        return ItemDetail(
            summary = s,
            latestPrice = s.avgPrice,
            priceUpdatedAtLabel = "10 分钟前",
            hasValidPriceData = s.avgPrice != null,
            source = sourceRef,
            isMock = true,
        )
    }
}

/** 供 UI 显示的"最近更新时间"文案 */
internal fun Long.asRelativeLabel(): String {
    val minutes = ((System.currentTimeMillis() - this) / 60000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}

/** 调试用：便于在页面里显示一个稳定的"当前时刻"文案 */
internal fun nowLabel(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
