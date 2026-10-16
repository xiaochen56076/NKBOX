package com.utilitybox.core.cache

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 缓存新鲜度与版本判定测试。
 *
 * 依据红线：UI 必须能区分「新鲜」与「陈旧」，
 * 且结构变更时旧缓存必须被**安全丢弃**而不是被误读。
 */
class CachePolicyTest {

    private val now = 1_800_000_000_000L
    private val schema = 1

    private fun entry(fetchedAt: Long = now, version: Int = schema) = CacheEntry(
        schemaVersion = version,
        fetchedAtMillis = fetchedAt,
        sourceId = "test",
        isMock = false,
        payload = "{}",
    )

    @Test
    fun `刚抓取的缓存在有效期内为 FRESH`() {
        assertEquals(
            Freshness.FRESH,
            entry(now).freshness(CachePolicy.CURRENT_PRICE, schema, now),
        )
    }

    @Test
    fun `超过有效期为 STALE`() {
        val old = now - CachePolicy.CURRENT_PRICE.ttlMillis - 1
        assertEquals(
            Freshness.STALE,
            entry(old).freshness(CachePolicy.CURRENT_PRICE, schema, now),
        )
    }

    @Test
    fun `恰好等于有效期边界仍算新鲜`() {
        val edge = now - CachePolicy.CURRENT_PRICE.ttlMillis
        assertEquals(
            Freshness.FRESH,
            entry(edge).freshness(CachePolicy.CURRENT_PRICE, schema, now),
        )
    }

    @Test
    fun `schema 版本不匹配时判定为 SCHEMA_MISMATCH`() {
        assertEquals(
            "结构变更后旧缓存必须被丢弃，不能按新结构误读",
            Freshness.SCHEMA_MISMATCH,
            entry(now, version = 1).freshness(CachePolicy.ITEM_LIST, currentSchemaVersion = 2, nowMillis = now),
        )
    }

    @Test
    fun `时间倒退（用户改系统时间）按 STALE 处理`() {
        val future = now + 60_000
        assertEquals(
            "不能因为抓取时间在『未来』就当成新鲜数据",
            Freshness.STALE,
            entry(future).freshness(CachePolicy.CURRENT_PRICE, schema, now),
        )
    }

    @Test
    fun `今日密码的策略特殊：不走固定 TTL`() {
        // 今日密码按"是否同一天"判定，该判定在 DailyCodeParser.classify 中完成，
        // 这里只确认它不会被毫秒 TTL 误判为过期
        val veryOld = now - 30L * 24 * 60 * 60 * 1000
        assertEquals(
            Freshness.FRESH,
            entry(veryOld).freshness(CachePolicy.DAILY_CODE, schema, now),
        )
    }

    @Test
    fun `缓存键必须带游戏命名空间`() {
        val key = object : LocalCache {
            override fun read(key: String) = null
            override fun write(key: String, entry: CacheEntry) = Unit
            override fun remove(key: String) = Unit
            override fun clear() = Unit
            override fun keys(): Set<String> = emptySet()
        }.key("deltaforce", "dailycode")

        assertEquals("cache:deltaforce:dailycode", key)
    }

    @Test
    fun `缓存键支持附加 id`() {
        val key = object : LocalCache {
            override fun read(key: String) = null
            override fun write(key: String, entry: CacheEntry) = Unit
            override fun remove(key: String) = Unit
            override fun clear() = Unit
            override fun keys(): Set<String> = emptySet()
        }.key("deltaforce", "itemprice", "13020000524")

        assertEquals("cache:deltaforce:itemprice:13020000524", key)
    }
}
