package com.utilitybox.core.cache

/**
 * 缓存新鲜度策略。**纯逻辑，无 Android 依赖，可直接单测。**
 *
 * 依据 docs/data-sources.md 第 5 节的缓存策略表：
 *
 * | 数据类型 | 有效期 | 过期后行为 |
 * |---|---|---|
 * | 今日密码 | 当天 | 显示为「过期缓存」并标注原始日期 |
 * | 物品列表 / 搜索 | 24 小时 | 显示缓存 + 提示可刷新 |
 * | 当前价格 | 10 分钟 | 显示缓存 + 标注时间 |
 * | 价格历史 | 24 小时 | 显示缓存 + 标注时间 |
 * | 好友状态 | 60 秒 | 显示为「状态可能已过期」 |
 *
 * 今日密码的实际接口**不提供日期字段**（P01 已实测坐实），
 * 因此其"当天有效"只能由**本机抓取日期**推断 —— 见 [DailyCodeFreshness]。
 */
enum class CachePolicy(val ttlMillis: Long) {
    /** 今日密码：按自然日判定，不用固定毫秒数 */
    DAILY_CODE(ttlMillis = Long.MAX_VALUE),

    ITEM_LIST(ttlMillis = 24 * 60 * 60 * 1000L),
    CURRENT_PRICE(ttlMillis = 10 * 60 * 1000L),
    PRICE_HISTORY(ttlMillis = 24 * 60 * 60 * 1000L),
    FRIEND_STATUS(ttlMillis = 60 * 1000L),
}

/** 新鲜度判定结果 —— UI 需要据此决定是否显示"陈旧"提示 */
enum class Freshness {
    /** 新鲜，可直接当当前数据用 */
    FRESH,

    /** 陈旧，**必须**显示原始抓取时间，不得当作新鲜数据呈现 */
    STALE,

    /** 结构版本不匹配，应丢弃并重新请求 */
    SCHEMA_MISMATCH,
}

/**
 * 判定缓存新鲜度。
 *
 * @param nowMillis 当前时间（传入而非内部取，便于单测）
 */
fun CacheEntry.freshness(
    policy: CachePolicy,
    currentSchemaVersion: Int,
    nowMillis: Long,
): Freshness {
    if (!isUsable(currentSchemaVersion)) return Freshness.SCHEMA_MISMATCH

    // 今日密码不用固定 TTL —— 它按"是否同一天"判定，交给 DailyCodeFreshness 处理
    if (policy == CachePolicy.DAILY_CODE) return Freshness.FRESH

    val age = nowMillis - fetchedAtMillis
    // 时间倒退（用户改了系统时间）时按陈旧处理，不要给出"未来数据"的错觉
    if (age < 0) return Freshness.STALE
    return if (age <= policy.ttlMillis) Freshness.FRESH else Freshness.STALE
}
