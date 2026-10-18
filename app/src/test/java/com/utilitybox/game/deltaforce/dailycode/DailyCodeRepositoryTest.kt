package com.utilitybox.game.deltaforce.dailycode

import com.utilitybox.core.cache.CacheEntry
import com.utilitybox.core.network.HttpResult
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.domain.model.SourceState
import com.utilitybox.game.deltaforce.dailycode.data.RemoteDailyCodeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 今日密码仓库测试 —— 覆盖网络 + 缓存 + 四态判定的完整链路。
 *
 * ★ 设计稿 P06 的 ★关键验证在此：
 *   **「构造过期缓存场景，界面显示原始日期而非『今日』」**
 *
 * ★ 依赖 [FakeHttpClient] 与 [FakeCache]，因此**无网络、无设备**即可验证红线。
 */
class DailyCodeRepositoryTest {

    private val today = LocalDate.of(2026, 10, 14)
    private val yesterday = today.minusDays(1)
    private val key = "cache:deltaforce:dailycode"

    private fun repo(http: FakeHttpClient, cache: FakeCache = FakeCache()) =
        DailyCodeRepository(
            // 测试显式声明 Key 已配置（单元测试环境没有真实 Key）。
            // 守卫本身的验证见下方专门用例。
            remote = RemoteDailyCodeSource(
                http = http,
                isKeyConfigured = { true },
                authHeaders = { mapOf("X-API-Key" to "test-key") },
            ),
            cache = cache,
        )

    // -----------------------------------------------------------------------
    // 态一：当天有效
    // -----------------------------------------------------------------------

    @Test
    fun `网络成功且抓取日为今天 判定为 Today`() = runTest {
        val http = FakeHttpClient.ok()
        val state = repo(http).load(today)

        assertTrue("应判定为当天有效", state is DailyCodeState.Today)
        val s = state as DailyCodeState.Today
        assertEquals(6, s.codes.size)
        assertEquals("零号大坝", s.codes[0].mapName)
        assertEquals("0144", s.codes[0].code)
        assertEquals("接口未提供日期，因此来源必须如实标注", "社区接口", s.source.displayName)
    }

    @Test
    fun `请求带上了正确的端点与鉴权头`() = runTest {
        val http = FakeHttpClient.ok()
        repo(http).load(today)

        assertEquals("/api/v1/df/tools/dailykeyword", http.lastPath)
        // 未配置 Key 时 RemoteSource 会提前返回 Unauthorized，不会发请求；
        // 这里只断言"若发了请求，路径必须正确"
    }

    @Test
    fun `成功后写入了缓存`() = runTest {
        val cache = FakeCache()
        repo(FakeHttpClient.ok(), cache).load(today)

        assertEquals(1, cache.writeCount)
        assertTrue("缓存必须落盘，否则离线时无数据可用", !cache.isEmpty())
    }

    // -----------------------------------------------------------------------
    // 态二：过期缓存（★ 设计稿关键验证）
    // -----------------------------------------------------------------------

    @Test
    fun `网络失败且缓存是昨天的 判定为 Stale 且携带原始日期 —— 红线`() = runTest {
        val cache = FakeCache()
        // 先用成功响应把"昨天"的数据写入缓存
        repo(FakeHttpClient.ok(), cache).load(yesterday)
        assertTrue("前置：缓存已写入", !cache.isEmpty())

        // 本次网络失败 → 应回退到缓存，且**必须**标记为过期
        val state = repo(FakeHttpClient.failing(AppError.NoNetwork), cache).load(today)
        assertTrue("必须走 Stale 分支，绝不能当成今日", state is DailyCodeState.Stale)

        val s = state as DailyCodeState.Stale
        assertEquals("必须携带原始抓取日期", yesterday, s.fetchedDate)
        assertNotEquals("红线：过期数据的日期绝不能等于今天", today, s.fetchedDate)
        assertEquals(6, s.codes.size)
    }

    @Test
    fun `过期缓存不会因为跨多天而变成 Today`() = runTest {
        val cache = FakeCache()
        repo(FakeHttpClient.ok(), cache).load(today.minusDays(10))

        val state = repo(FakeHttpClient.failing(AppError.Timeout), cache).load(today)
        val s = state as DailyCodeState.Stale
        assertEquals(today.minusDays(10), s.fetchedDate)
    }

    // -----------------------------------------------------------------------
    // 态三：暂无数据
    // -----------------------------------------------------------------------

    @Test
    fun `数据源返回空列表 判定为 NoDataForToday`() = runTest {
        val http = FakeHttpClient.ok("""{"code":0,"message":"成功","data":{"list":[]}}""")
        assertEquals(DailyCodeState.NoDataForToday, repo(http).load(today))
    }

    @Test
    fun `响应缺少 list 字段 判定为请求失败而不是暂无数据`() = runTest {
        // 结构不对 ≠ 没有数据。必须区分，否则会把"数据源改版"误报成"今天没密码"
        val http = FakeHttpClient.ok("""{"code":0,"message":"成功","data":{"other":1}}""")
        val state = repo(http).load(today)
        assertTrue(state is DailyCodeState.Failed)
        assertEquals(AppError.Malformed, (state as DailyCodeState.Failed).error)
    }

    // -----------------------------------------------------------------------
    // 态四：请求失败
    // -----------------------------------------------------------------------

    @Test
    fun `网络失败且无缓存 判定为 Failed 并保留错误类型`() = runTest {
        val state = repo(FakeHttpClient.failing(AppError.NoNetwork)).load(today)
        assertEquals(DailyCodeState.Failed(AppError.NoNetwork), state)
    }

    @Test
    fun `超时与无网络的错误类型不被混淆`() = runTest {
        assertEquals(
            DailyCodeState.Failed(AppError.Timeout),
            repo(FakeHttpClient.failing(AppError.Timeout)).load(today),
        )
        assertEquals(
            DailyCodeState.Failed(AppError.NoNetwork),
            repo(FakeHttpClient.failing(AppError.NoNetwork)).load(today),
        )
    }

    @Test
    fun `业务错误保留数据源原文`() = runTest {
        val state = repo(FakeHttpClient.business(402, "余额不足，本次请求需要 1 积分")).load(today)
        val e = (state as DailyCodeState.Failed).error as AppError.Business
        assertEquals(402, e.code)
        assertTrue("用户需要看到数据源的原话", e.message.contains("积分"))
    }

    @Test
    fun `非 JSON 响应归为格式错误`() = runTest {
        val state = repo(FakeHttpClient.ok("<html>502 Bad Gateway</html>")).load(today)
        assertEquals(DailyCodeState.Failed(AppError.Malformed), state)
    }

    // -----------------------------------------------------------------------
    // 缓存健壮性
    // -----------------------------------------------------------------------

    @Test
    fun `schema 版本不匹配的缓存被丢弃且不当作数据使用`() = runTest {
        val cache = FakeCache()
        cache.seed(
            key,
            CacheEntry(
                schemaVersion = DailyCodeRepository.SCHEMA_VERSION + 1, // 旧/新结构
                fetchedAtMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                sourceId = "x",
                isMock = false,
                payload = "1\t零号大坝\t0144",
            ),
        )

        val state = repo(FakeHttpClient.failing(AppError.NoNetwork), cache).load(today)
        // 结构不匹配 → 必须走 Failed，而不是拿旧结构的数据硬解
        assertTrue("结构不匹配的缓存必须被拒绝", state is DailyCodeState.Failed)
        assertEquals("并且应当被删除", 1, cache.removeCount)
    }

    @Test
    fun `缓存为空串时视为无缓存`() = runTest {
        val cache = FakeCache()
        cache.seed(
            key,
            CacheEntry(
                schemaVersion = DailyCodeRepository.SCHEMA_VERSION,
                fetchedAtMillis = 0L,
                sourceId = "x",
                isMock = false,
                payload = "",
            ),
        )
        val state = repo(FakeHttpClient.failing(AppError.NoNetwork), cache).load(today)
        assertTrue(state is DailyCodeState.Failed)
    }

    @Test
    fun `缓存往返后地图名与密码完整保留（含中文与制表符编码）`() = runTest {
        val cache = FakeCache()
        // 用「昨天」写入，这样网络失败回退时才会走 Stale —— 才能验证缓存内容本身
        repo(FakeHttpClient.ok(), cache).load(yesterday)
        val state = repo(FakeHttpClient.failing(AppError.NoNetwork), cache).load(today) as DailyCodeState.Stale

        assertEquals(6, state.codes.size)
        assertEquals("潮汐监狱", state.codes[4].mapName)
        assertEquals("2643", state.codes[4].code)
        assertEquals("AZ3", state.codes[5].mapName)
        assertEquals("2520", state.codes[5].code)
        // 顺带确认所有地图名都没在缓存编码里损坏
        assertEquals(
            listOf("零号大坝", "长弓溪谷", "巴克什", "航天基地", "潮汐监狱", "AZ3"),
            state.codes.map { it.mapName },
        )
    }

    @Test
    fun `网络恢复正常后重新判定为 Today`() = runTest {
        val cache = FakeCache()
        repo(FakeHttpClient.ok(), cache).load(yesterday) // 昨天写入
        assertTrue(repo(FakeHttpClient.failing(AppError.NoNetwork), cache).load(today) is DailyCodeState.Stale)

        // 今天网络恢复
        val state = repo(FakeHttpClient.ok(), cache).load(today)
        assertTrue("网络恢复后必须回到 Today，而不是停留在 Stale", state is DailyCodeState.Today)
    }

    @Test
    fun `数据源元信息为已验证状态`() = runTest {
        val state = repo(FakeHttpClient.ok()).load(today) as DailyCodeState.Today
        assertEquals(SourceState.VERIFIED, state.source.verified)
        assertEquals("deltaforce.community.v1", state.source.id)
    }

    // -----------------------------------------------------------------------
    // 未配置 API Key 的守卫（P06 实测踩到：守卫直接读全局配置会让测试全部短路）
    // -----------------------------------------------------------------------

    @Test
    fun `未配置 API Key 时不发请求 直接返回鉴权失败`() = runTest {
        val http = FakeHttpClient.ok()
        val source = RemoteDailyCodeSource(
            http = http,
            isKeyConfigured = { false },
            authHeaders = { emptyMap() },
        )
        val r = source.fetch()

        assertEquals("不应发出注定失败的请求", 0, http.callCount)
        assertTrue(r is com.utilitybox.domain.datasource.DataResult.Err)
        assertEquals(
            AppError.ApiKeyMissing,
            (r as com.utilitybox.domain.datasource.DataResult.Err).error,
        )
    }

    @Test
    fun `未配置 Key 时界面走 Failed 而不是伪装成暂无数据`() = runTest {
        val source = RemoteDailyCodeSource(
            http = FakeHttpClient.ok(),
            isKeyConfigured = { false },
            authHeaders = { emptyMap() },
        )
        val state = DailyCodeRepository(source, FakeCache()).load(today)

        // 「没配 Key」和「今天没有密码」是两回事，绝不能混
        // 且必须是 ApiKeyMissing 而非 Unauthorized —— 后者表示"服务端拒绝了 Key"，
        // 两者用户要采取的行动不同（P06 实测踩到的设计缺陷，见 AppError 的注释）
        assertEquals(DailyCodeState.Failed(AppError.ApiKeyMissing), state)
    }

    @Test
    fun `服务端拒绝 Key 与未配 Key 是两种不同的错误`() = runTest {
        // 服务端明确返回 401 → 这是 Unauthorized（Key 无效），不是"没填"
        val serverReject = repo(FakeHttpClient.business(401, "invalid api key")).load(today)
        assertEquals(
            DailyCodeState.Failed(AppError.Unauthorized),
            serverReject,
        )

        // 本机没配 Key → ApiKeyMissing（请求根本没发出去）
        val missing = DailyCodeRepository(
            RemoteDailyCodeSource(
                http = FakeHttpClient.ok(),
                isKeyConfigured = { false },
                authHeaders = { emptyMap() },
            ),
            FakeCache(),
        ).load(today)
        assertEquals(DailyCodeState.Failed(AppError.ApiKeyMissing), missing)

        // 红线：两者绝不能被归为同一种，否则界面会自相矛盾
        assertNotEquals(serverReject, missing)
    }

    @Test
    fun `未配置 Key 且有过期缓存时 仍回退到缓存`() = runTest {
        val cache = FakeCache()
        // 有 Key 时先成功写入昨天的缓存
        repo(FakeHttpClient.ok(), cache).load(yesterday)

        // 换成"没配 Key"的数据源
        val noKeySource = RemoteDailyCodeSource(
            http = FakeHttpClient.ok(),
            isKeyConfigured = { false },
            authHeaders = { emptyMap() },
        )
        val state = DailyCodeRepository(noKeySource, cache).load(today)

        assertTrue("有缓存可用时应当降级展示而不是报错", state is DailyCodeState.Stale)
        assertEquals(yesterday, (state as DailyCodeState.Stale).fetchedDate)
    }

    @Test
    fun `已配置 Key 时鉴权头被正确带上`() = runTest {
        val http = FakeHttpClient.ok()
        repo(http).load(today)
        assertEquals("test-key", http.lastHeaders["X-API-Key"])
    }

    // -----------------------------------------------------------------------
    // P11 补 91：同一天内缓存优先（用户要求「不用每次切界面都重新加载」）
    //
    // ★ 这组测试锁的是**请求次数**，不是返回值 ——
    //   返回 Today 这条老测试已经覆盖了，但它**抓不到"又多打了一次网络"**这个回归。
    //   以前每次进页面都会闪一下「加载中…」，根因就是这里没有短路。
    // -----------------------------------------------------------------------

    @Test
    fun `同一天内再次加载 直接用缓存且不再请求网络`() = runTest {
        val cache = FakeCache()
        val http = FakeHttpClient.ok()
        val r = repo(http, cache)

        // 第一次：没有缓存 → 必须走网络
        val first = r.load(today)
        assertTrue(first is DailyCodeState.Today)
        assertEquals("首次加载必须请求网络", 1, http.callCount)

        // 第二次（例如用户切到鸣潮再切回来）：缓存是今天的 → 不该再请求
        val second = r.load(today)
        assertEquals("同一天内不得重复请求网络", 1, http.callCount)
        assertTrue("仍应返回当天有效态", second is DailyCodeState.Today)
        assertEquals(
            "返回的密码必须与第一次一致",
            (first as DailyCodeState.Today).codes,
            (second as DailyCodeState.Today).codes,
        )
    }

    @Test
    fun `跨天后不再用缓存 会重新请求网络`() = runTest {
        val cache = FakeCache()
        val http = FakeHttpClient.ok()
        val r = repo(http, cache)

        // 昨天抓的缓存
        r.load(yesterday)
        assertEquals(1, http.callCount)

        // 今天再进页面 —— 缓存已跨天，必须重新取
        val state = r.load(today)
        assertEquals("跨天后必须重新请求网络", 2, http.callCount)
        assertTrue("跨天重新取到的是当天数据", state is DailyCodeState.Today)
    }

    @Test
    fun `forceRefresh 为 true 时 即使缓存是今天也请求网络`() = runTest {
        val cache = FakeCache()
        val http = FakeHttpClient.ok()
        val r = repo(http, cache)

        r.load(today)
        assertEquals(1, http.callCount)

        // 用户手动点「刷新」/ 自动刷新定时器走的就是这条
        r.load(today, forceRefresh = true)
        assertEquals(
            "手动刷新必须真的打网络，否则用户会以为按钮坏了",
            2,
            http.callCount,
        )
    }

    @Test
    fun `缓存命中时 更新时刻取自缓存而非当前时间`() = runTest {
        val cache = FakeCache()
        repo(FakeHttpClient.ok(), cache).load(today)

        // 把缓存里的时间戳改成一个固定时刻，再读
        val entry = cache.read(key)!!
        cache.write(
            key,
            entry.copy(fetchedAtMillis = today.atStartOfDay(ZoneId.systemDefault())
                .plusHours(8).plusMinutes(5).toInstant().toEpochMilli()),
        )

        val state = repo(FakeHttpClient.ok(), cache).load(today) as DailyCodeState.Today
        assertEquals(
            "读到缓存时必须显示缓存写入的时刻，不能显示\"现在\"（那会让时间戳每次都在变）",
            "08:05",
            state.updatedAtLabel,
        )
    }
}
