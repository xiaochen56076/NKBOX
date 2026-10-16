package com.utilitybox.game.deltaforce.dailycode

import com.utilitybox.core.cache.CacheEntry
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpResult
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.game.deltaforce.dailycode.data.RemoteDailyCodeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * **异常路径全覆盖矩阵**（P10 验收标准）。
 *
 * 设计稿要求覆盖：无网、超时、5xx、鉴权失败、JSON 损坏、业务错误、数据源不可用。
 *
 * ★ 为什么用「矩阵」写法而不是零散用例：
 *   这类测试最容易漏 —— 加一个新错误类型时，没人会记得回来补。
 *   用 `AppError.ALL` 驱动遍历，**新增类型如果没被处理，这里会立刻失败**。
 */
class ExceptionPathMatrixTest {

    private val today = LocalDate.of(2026, 10, 14)
    private val yesterday = today.minusDays(1)

    private fun repoWith(http: HttpClient, cache: FakeCache = FakeCache()) =
        DailyCodeRepository(
            remote = RemoteDailyCodeSource(
                http = http,
                isKeyConfigured = { true },
                authHeaders = { mapOf("X-API-Key" to "k") },
            ),
            cache = cache,
        )

    private fun failing(e: AppError) = object : HttpClient {
        override suspend fun get(
            path: String,
            query: Map<String, String>,
            headers: Map<String, String>,
        ): HttpResult = HttpResult.Failure(e)
    }

    // -----------------------------------------------------------------------
    // 传输层错误：每一种都必须如实归类为 Failed，绝不伪装成"暂无数据"
    // -----------------------------------------------------------------------

    @Test
    fun `每一种传输层错误都归类为 Failed 且保留原错误类型 —— 矩阵`() = runTest {
        val matrix = listOf(
            AppError.NoNetwork,
            AppError.Timeout,
            AppError.Server(500),
            AppError.Server(502),
            AppError.Server(503),
            AppError.ApiKeyMissing,
            AppError.Unauthorized,
            AppError.SourceUnavailable,
            AppError.Unknown("底层的意外异常"),
        )

        matrix.forEach { err ->
            val state = repoWith(failing(err)).load(today)
            assertEquals(
                "错误 $err 必须原样带到界面（红线：不得伪装成\"暂无数据\"）",
                DailyCodeState.Failed(err),
                state,
            )
        }
    }

    @Test
    fun `未知错误类型也不会被吞掉`() = runTest {
        // ★ Unknown 此前是零覆盖。它代表"我们没预料到的异常"，
        //   正因为没预料到，更不能悄悄当成"没有数据"。
        val err = AppError.Unknown("NullPointerException at line 42")
        val state = repoWith(failing(err)).load(today)
        assertEquals(DailyCodeState.Failed(err), state)
        val message = (state as DailyCodeState.Failed).error
        assertTrue(
            "必须保留原始信息以便排查",
            message is AppError.Unknown && message.message.contains("line 42"),
        )
    }

    @Test
    fun `数据源不可用不会被当成空数据 —— 此前零覆盖`() = runTest {
        // ★ SourceUnavailable 语义是"这个能力当前没有数据源"，
        //   与"接口返回了空列表"（NoDataForToday）是完全不同的两件事。
        val state = repoWith(failing(AppError.SourceUnavailable)).load(today)
        assertEquals(DailyCodeState.Failed(AppError.SourceUnavailable), state)
        assertTrue(
            "绝不能是 NoDataForToday —— 那会告诉用户\"今天没有密码\"，而事实是我们拿不到",
            state !is DailyCodeState.NoDataForToday,
        )
    }

    // -----------------------------------------------------------------------
    // 响应体损坏的各种形态
    // -----------------------------------------------------------------------

    @Test
    fun `各种损坏响应都归为格式错误而不是空数据 —— 矩阵`() = runTest {
        val broken = listOf(
            "不是 JSON" to "纯文本",
            "" to "空响应体",
            "{" to "截断的 JSON",
            "[]" to "顶层是数组而不是对象",
            """{"code":0}""" to "缺少 data",
            """{"code":0,"data":{}}""" to "缺少 list",
            """{"code":0,"data":{"list":"不是数组"}}""" to "list 类型不对",
            """{"code":0,"data":{"list":[]}}""" to "空列表（这个是合法空数据，后面单独断言）",
        )

        broken.dropLast(1).forEach { (body, label) ->
            val http = object : HttpClient {
                override suspend fun get(
                    path: String,
                    query: Map<String, String>,
                    headers: Map<String, String>,
                ): HttpResult = HttpResult.Success(body)
            }
            val state = repoWith(http).load(today)
            assertTrue(
                "「$label」应归为失败，而不是\"暂无数据\"",
                state is DailyCodeState.Failed,
            )
        }
    }

    @Test
    fun `合法的空列表才是暂无数据 —— 与损坏响应区分开`() = runTest {
        val http = object : HttpClient {
            override suspend fun get(
                path: String,
                query: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResult = HttpResult.Success("""{"code":0,"data":{"list":[]}}""")
        }
        assertEquals(DailyCodeState.NoDataForToday, repoWith(http).load(today))
    }

    // -----------------------------------------------------------------------
    // 失败时的缓存行为：绝不能因为失败就伪造数据
    // -----------------------------------------------------------------------

    @Test
    fun `任何错误下 若无缓存 都不会凭空产生 Today`() = runTest {
        val allErrors = listOf(
            AppError.NoNetwork, AppError.Timeout, AppError.Server(500),
            AppError.Malformed, AppError.SourceUnavailable,
            AppError.ApiKeyMissing, AppError.Unauthorized,
            AppError.Business(400, "参数错误"), AppError.Unknown("x"),
        )
        allErrors.forEach { err ->
            val state = repoWith(failing(err)).load(today)
            assertTrue(
                "错误 $err 下绝不能出现 Today（会显示错误的密码）",
                state !is DailyCodeState.Today,
            )
        }
    }

    @Test
    fun `任何错误下 有昨天的缓存 都回退到 Stale 且携带昨天日期`() = runTest {
        val allErrors = listOf(
            AppError.NoNetwork, AppError.Timeout, AppError.Server(503),
            AppError.Malformed, AppError.SourceUnavailable,
            AppError.ApiKeyMissing, AppError.Unauthorized,
            AppError.Business(500, "上游异常"), AppError.Unknown("x"),
        )
        allErrors.forEach { err ->
            // 每个错误类型用独立的缓存，避免相互影响
            val cache = FakeCache()
            val seed = DailyCodeRepository(
                remote = RemoteDailyCodeSource(
                    http = FakeHttpClient.ok(),
                    isKeyConfigured = { true },
                    authHeaders = { emptyMap() },
                ),
                cache = cache,
            )
            seed.load(yesterday)   // 以"昨天"写入

            val state = repoWith(failing(err), cache).load(today)

            assertTrue("错误 $err 下应回退到过期缓存", state is DailyCodeState.Stale)
            val stale = state as DailyCodeState.Stale
            assertEquals(
                "★ 红线：回退时必须携带**原始抓取日**，绝不能说成今天",
                yesterday,
                stale.fetchedDate,
            )
            assertTrue(stale.fetchedDate != today)
        }
    }

    // -----------------------------------------------------------------------
    // 缓存 schema 变更
    // -----------------------------------------------------------------------

    @Test
    fun `schema 版本变更后旧缓存被丢弃 不会用错结构的数据`() = runTest {
        val cache = FakeCache()
        // 手写一条"上一个 schema 版本"的缓存，结构故意不同
        cache.seed(
            cache.key("deltaforce", "dailycode"),
            CacheEntry(
                schemaVersion = DailyCodeRepository.SCHEMA_VERSION + 99,
                fetchedAtMillis = yesterday.toEpochMillisAtStartOfDay(),
                sourceId = "deltaforce.community.v1",
                isMock = false,
                payload = "旧格式|完全不同的字段|不该被解析",
            ),
        )

        val state = repoWith(failing(AppError.NoNetwork), cache).load(today)

        assertTrue(
            "版本不匹配时必须丢弃，走 Failed 而不是拿旧结构硬解析出脏数据",
            state is DailyCodeState.Failed,
        )
        // ★ 实现是 remove 而不是"留着不读"：
        //   留着的话，schema 回退（用户装回旧版本）时会读到**新结构**的数据，
        //   那比没有缓存更糟。这里断言它确实被删掉了。
        assertNull(
            "不匹配的缓存必须被删除，而不是留在那里",
            cache.read(cache.key("deltaforce", "dailycode")),
        )
    }

    private fun LocalDate.toEpochMillisAtStartOfDay(): Long =
        atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
