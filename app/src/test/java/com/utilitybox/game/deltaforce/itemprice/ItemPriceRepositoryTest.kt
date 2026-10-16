package com.utilitybox.game.deltaforce.itemprice

import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpResult
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.ItemPriceDetailState
import com.utilitybox.domain.model.ItemSummary
import com.utilitybox.game.deltaforce.dailycode.FakeCache
import com.utilitybox.game.deltaforce.itemprice.data.RemoteItemPriceSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 物品物价仓库测试。
 *
 * ★ 覆盖设计稿 P07 的两条 ★ 关键验证：
 *   1. `hasValidPriceData: false` ⇒ **[NoPriceData] 而非把 0 当真实价格**
 *   2. 历史缺失点**原样保留**（图表据此断开，不插值）
 */
class ItemPriceRepositoryTest {

    private val summary = ItemSummary(
        objectId = "13020000524",
        name = "M250钛金长枪管",
        shortName = "M250",
        grade = 5,
        category = "枪械配件",
        avgPrice = 49258,
    )

    /**
     * 按端点分派的 HTTP 替身。
     *
     * 详情页会打两个端点，用一个"看路径决定回什么"的替身最清晰。
     */
    private class RoutingHttp(
        private val latestBody: () -> String?,
        private val historyBody: () -> String?,
        private val latestError: AppError? = null,
        private val historyError: AppError? = null,
    ) : HttpClient {
        val paths = mutableListOf<String>()

        override suspend fun get(
            path: String,
            query: Map<String, String>,
            headers: Map<String, String>,
        ): HttpResult {
            paths += path
            return if (path.contains("history")) {
                historyError?.let { return HttpResult.Failure(it) }
                HttpResult.Success(historyBody() ?: "")
            } else {
                latestError?.let { return HttpResult.Failure(it) }
                HttpResult.Success(latestBody() ?: "")
            }
        }
    }

    private fun repo(http: HttpClient, cache: FakeCache = FakeCache()) =
        ItemPriceRepository(
            RemoteItemPriceSource(
                http = http,
                isKeyConfigured = { true },
                authHeaders = { mapOf("X-API-Key" to "test-key") },
            ),
            cache,
        )

    private fun latest(price: Long?) = """
        {"code":0,"message":"成功","data":{"objectIDs":["$@"],"dataMap":{},"source":"ams"}}
    """.trimIndent().let { _ ->
        if (price == null) {
            """{"code":0,"message":"成功","data":{"dataMap":{},"items":[],"source":"ams"}}"""
        } else {
            """{"code":0,"message":"成功","data":{"dataMap":{"13020000524":$price},"source":"ams"}}"""
        }
    }

    private fun history(vararg pairs: Pair<String, Pair<Long?, Int>>) = buildString {
        append("""{"code":0,"message":"成功","data":{"granularity":"day","source":"ams","history":[""")
        pairs.forEachIndexed { i, (date, v) ->
            if (i > 0) append(",")
            val price = v.first?.toString() ?: "0"
            append("""{"date":"$date","avgPrice":$price,"count":${v.second}}""")
        }
        append("]}}")
    }

    // -----------------------------------------------------------------------
    // ★ 红线 1：无成交数据
    // -----------------------------------------------------------------------

    @Test
    fun `服务端回应缺少 dataMap 时必须归为无成交数据 而不是服务故障 —— 实测`() = runTest {
        // ★★ P07 用真实请求拿到的服务端原话：
        //   HTTP 400  {"code":400,"message":"AMS 响应缺少 dataMap"}
        //   这意味着上游对该物品没有价格数据 —— 重试永远不会成功。
        //   若显示成"临时故障，稍后重试"，就是在骗用户。
        val businessFail = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Success(
                """{"code":400,"message":"AMS 响应缺少 dataMap"}"""
            )
        }
        assertEquals(
            "缺少 dataMap 是数据缺失，不是故障",
            ItemPriceDetailState.NoPriceData,
            repo(businessFail).detail(summary),
        )
    }

    @Test
    fun `真正的服务端错误不会被吞成无数据`() = runTest {
        // 反向保护：不能为了"看起来正常"而把故障伪装成"没有数据"
        val serverDown = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Failure(AppError.Server(500))
        }
        assertEquals(
            ItemPriceDetailState.Failed(AppError.Server(500)),
            repo(serverDown).detail(summary),
        )

        val otherBusiness = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Success(
                """{"code":429,"message":"请求过于频繁"}"""
            )
        }
        val r = repo(otherBusiness).detail(summary)
        assertTrue("限流不是'没有数据'，应如实报错", r is ItemPriceDetailState.Failed)
    }

    @Test
    fun `当前价为空时判定为 NoPriceData 而不是显示 0 —— 红线`() = runTest {
        val http = RoutingHttp(
            latestBody = { latest(null) },
            historyBody = { history("2026-10-14" to (0L to 0)) },
        )
        val state = repo(http).detail(summary)

        assertEquals(
            "无成交数据必须是独立状态，不能显示 0 也不能当成错误",
            ItemPriceDetailState.NoPriceData,
            state,
        )
    }

    @Test
    fun `当前价为 0 时同样判定为 NoPriceData —— 红线`() = runTest {
        // 接口在无成交时正是返回 0；解析层必须把它读成"无数据"
        val http = RoutingHttp(
            latestBody = { """{"code":0,"message":"成功","data":{"dataMap":{"13020000524":0}}}""" },
            historyBody = { history() },
        )
        assertEquals(ItemPriceDetailState.NoPriceData, repo(http).detail(summary))
    }

    @Test
    fun `有价格时判定为 Ready 且 hasValidPriceData 为真`() = runTest {
        val http = RoutingHttp(
            latestBody = { latest(49258) },
            historyBody = { history("2026-10-13" to (49268L to 371), "2026-10-14" to (49258L to 130)) },
        )
        val state = repo(http).detail(summary)
        assertTrue(state is ItemPriceDetailState.Ready)
        val d = (state as ItemPriceDetailState.Ready).detail
        assertEquals(49258L, d.latestPrice)
        assertTrue(d.hasValidPriceData)
        assertEquals(2, state.history!!.points.size)
    }

    // -----------------------------------------------------------------------
    // ★ 红线 2：历史缺失点必须保留
    // -----------------------------------------------------------------------

    @Test
    fun `历史中的无成交日保留为 null 且不被丢弃 —— 红线`() = runTest {
        val http = RoutingHttp(
            latestBody = { latest(49258) },
            historyBody = {
                history(
                    "2026-10-12" to (49248L to 100),
                    "2026-10-13" to (0L to 0),      // 无成交
                    "2026-10-14" to (49258L to 130),
                )
            },
        )
        val state = repo(http).detail(summary) as ItemPriceDetailState.Ready
        val points = state.history!!.points

        assertEquals("三个日期点都必须保留，缺失日不能消失", 3, points.size)
        assertNull("无成交日必须是 null（图表据此断开）", points[1].avgPrice)
        assertEquals("样本数如实保留", 0, points[1].sampleCount)
    }

    @Test
    fun `缺失点经过缓存往返后仍然是缺失而不是 0 —— 红线`() = runTest {
        val cache = FakeCache()
        val http = RoutingHttp(
            latestBody = { latest(100) },
            historyBody = {
                history(
                    "2026-10-12" to (100L to 10),
                    "2026-10-13" to (0L to 0),
                    "2026-10-14" to (200L to 10),
                )
            },
        )
        repo(http, cache).detail(summary)

        // 断网后走缓存
        val offline = RoutingHttp(
            latestBody = { latest(100) },
            historyBody = { null },
            historyError = AppError.NoNetwork,
        )
        val state = repo(offline, cache).detail(summary) as ItemPriceDetailState.Ready
        val points = state.history!!.points

        assertEquals(3, points.size)
        assertNull("缓存往返后缺失点必须仍是 null，绝不能变成 0", points[1].avgPrice)
        assertEquals(100L, points[0].avgPrice)
        assertEquals(200L, points[2].avgPrice)
    }

    @Test
    fun `缺失点不影响趋势计算（只用有效点）`() = runTest {
        val http = RoutingHttp(
            latestBody = { latest(200) },
            historyBody = {
                history(
                    "2026-10-12" to (100L to 10),
                    "2026-10-13" to (0L to 0),   // 缺失
                    "2026-10-14" to (200L to 10),
                )
            },
        )
        val state = repo(http).detail(summary) as ItemPriceDetailState.Ready
        // 首=100，尾=200 → 上涨
        assertEquals(com.utilitybox.domain.model.PriceTrend.UP, state.history!!.trend)
        assertEquals(100.0, state.history!!.changePercent!!, 0.001)
    }

    // -----------------------------------------------------------------------
    // 两个请求可独立失败
    // -----------------------------------------------------------------------

    @Test
    fun `历史失败但价格成功时仍显示价格 并把历史错误如实带出`() = runTest {
        val http = RoutingHttp(
            latestBody = { latest(49258) },
            historyBody = { null },
            historyError = AppError.NoNetwork,
        )
        val state = repo(http).detail(summary)

        assertTrue("次要请求失败不应把整页变成错误页", state is ItemPriceDetailState.Ready)
        val r = state as ItemPriceDetailState.Ready
        assertNull("历史取不到时为 null", r.history)
        assertEquals("必须如实带出历史失败原因", AppError.NoNetwork, r.historyError)
    }

    @Test
    fun `价格失败时整页失败 并保留错误类型`() = runTest {
        val http = RoutingHttp(
            latestBody = { null },
            historyBody = { history() },
            latestError = AppError.Timeout,
        )
        assertEquals(
            ItemPriceDetailState.Failed(AppError.Timeout),
            repo(http).detail(summary),
        )
    }

    @Test
    fun `未配 Key 时价格请求返回 ApiKeyMissing 而不是 Unauthorized`() = runTest {
        val http = RoutingHttp(latestBody = { latest(1) }, historyBody = { history() })
        val r = RepoWithoutKey(http).detail(summary)
        assertEquals(ItemPriceDetailState.Failed(AppError.ApiKeyMissing), r)
    }

    private fun RepoWithoutKey(http: HttpClient) = ItemPriceRepository(
        RemoteItemPriceSource(
            http = http,
            isKeyConfigured = { false },
            authHeaders = { emptyMap() },
        ),
        FakeCache(),
    )

    // -----------------------------------------------------------------------
    // 搜索与缓存
    // -----------------------------------------------------------------------

    @Test
    fun `搜索会走搜索端点并解析出列表`() = runTest {
        val http = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Success(
                """{"code":0,"message":"成功","data":{"total":1,"list":[
                   {"objectID":"1","objectName":"钛合金板","avgPrice":8800}
                ]}}"""
            )
        }
        val r = repo(http).search("钛")
        assertTrue(r is DataResult.Ok)
        val items = (r as DataResult.Ok).value
        assertEquals(1, items.size)
        assertEquals("钛合金板", items[0].name)
    }

    @Test
    fun `三个端点的请求参数名必须是实测值 —— 回归钉子`() = runTest {
        // ★★ P07 实测踩坑：三个参数名我全凭直觉写错，全部 HTTP 400。
        //   参数名**不能从响应体字段名反推**（响应里是 objectIDs/objectID，
        //   但请求参数一律叫 id）。这条用例把三个契约一起钉住。
        val queries = mutableListOf<Pair<String, Map<String, String>>>()
        val http = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult {
                queries += path to query
                val body = when {
                    path.contains("search") ->
                        """{"code":0,"message":"成功","data":{"list":[]}}"""
                    path.contains("history") ->
                        """{"code":0,"message":"成功","data":{"history":[]}}"""
                    else ->
                        """{"code":0,"message":"成功","data":{"dataMap":{}}}"""
                }
                return HttpResult.Success(body)
            }
        }
        val r = repo(http)
        r.search("钛")
        r.detail(summary)

        val searchQ = queries.first { it.first.contains("search") }.second
        assertTrue("搜索参数名必须是 objectName", searchQ.containsKey("objectName"))
        assertFalse("不得用 keyword", searchQ.containsKey("keyword"))

        val latestQ = queries.first { it.first.contains("latest") }.second
        assertTrue("当前价参数名必须是 id，实际 $latestQ", latestQ.containsKey("id"))
        assertFalse("不得用 objectIDs（那是响应体字段名）", latestQ.containsKey("objectIDs"))

        val historyQ = queries.first { it.first.contains("history") }.second
        assertTrue("历史参数名必须是 id，实际 $historyQ", historyQ.containsKey("id"))
        assertFalse("不得用 objectID", historyQ.containsKey("objectID"))
        assertEquals("7", historyQ["days"])
    }

    @Test
    fun `搜索参数名必须是 objectName 而不是 keyword —— 实测回归`() = runTest {
        // ★ P07 实测踩到：用 keyword 会被服务端拒绝（HTTP 400）。
        //   P01 的实测记录里参数名就是 objectName，写代码时凭直觉写错了一次，
        //   这条用例把契约钉住，防止再犯。
        var capturedQuery: Map<String, String> = emptyMap()
        val http = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult {
                capturedQuery = query
                return HttpResult.Success("""{"code":0,"message":"成功","data":{"list":[]}}""")
            }
        }
        repo(http).search("钛")

        assertTrue(
            "搜索参数必须叫 objectName，实际为 $capturedQuery",
            capturedQuery.containsKey("objectName"),
        )
        assertFalse("不得使用 keyword（服务端会返回 400）", capturedQuery.containsKey("keyword"))
        assertEquals("钛", capturedQuery["objectName"])
    }

    @Test
    fun `空关键字不发请求 直接返回空列表`() = runTest {
        val http = RoutingHttp(latestBody = { latest(1) }, historyBody = { history() })
        val r = repo(http).search("   ")
        assertTrue(r is DataResult.Ok)
        assertTrue((r as DataResult.Ok).value.isEmpty())
        assertTrue("不应为空关键字发请求", http.paths.isEmpty())
    }

    @Test
    fun `搜索失败时回退到同关键字缓存`() = runTest {
        val cache = FakeCache()
        val ok = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Success(
                """{"code":0,"message":"成功","data":{"list":[
                   {"objectID":"1","objectName":"钛合金板","avgPrice":8800}
                ]}}"""
            )
        }
        repo(ok, cache).search("钛")   // 先写缓存

        val failing = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Failure(AppError.NoNetwork)
        }
        val r = repo(failing, cache).search("钛")
        assertTrue("断网时应回退缓存而不是空白", r is DataResult.Ok)
        assertEquals("钛合金板", (r as DataResult.Ok).value[0].name)
    }

    @Test
    fun `搜索结果里的 0 价被读成无数据 缓存往返后也不会变成 0 —— 红线`() = runTest {
        val cache = FakeCache()
        val http = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Success(
                """{"code":0,"message":"成功","data":{"list":[
                   {"objectID":"1","objectName":"某物","avgPrice":0},
                   {"objectID":"2","objectName":"有价物","avgPrice":500}
                ]}}"""
            )
        }
        val first = (repo(http, cache).search("x") as DataResult.Ok).value
        assertNull("0 必须是 null", first[0].avgPrice)
        assertEquals(500L, first[1].avgPrice)

        val failing = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Failure(AppError.NoNetwork)
        }
        val cached = (repo(failing, cache).search("x") as DataResult.Ok).value
        assertNull("缓存往返后仍必须是 null，绝不能变成 0", cached[0].avgPrice)
        assertEquals(500L, cached[1].avgPrice)
    }

    @Test
    fun `详情页同时打了价格与历史两个端点`() = runTest {
        val http = RoutingHttp(
            latestBody = { latest(100) },
            historyBody = { history("2026-10-14" to (100L to 5)) },
        )
        repo(http).detail(summary)
        assertTrue("应请求当前价", http.paths.any { it.contains("latest") })
        assertTrue("应请求历史", http.paths.any { it.contains("history") })
    }

    @Test
    fun `历史为空数组时 history 为 null 而不是空图表`() = runTest {
        val http = RoutingHttp(
            latestBody = { latest(100) },
            historyBody = { """{"code":0,"message":"成功","data":{"history":[]}}""" },
        )
        val state = repo(http).detail(summary) as ItemPriceDetailState.Ready
        assertNotNull("价格仍在", state.detail.latestPrice)
        assertNull("无历史点时应为 null，UI 才能给出解释", state.history)
    }
}
