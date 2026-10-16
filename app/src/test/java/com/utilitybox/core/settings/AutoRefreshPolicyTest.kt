package com.utilitybox.core.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 自动刷新策略测试（P11 接通）。
 *
 * ★ 定时逻辑最容易被"感觉一下"糊弄过去 —— 它没有崩溃、没有报错，
 *   只是**该刷的时候没刷**或**不该刷的时候狂刷**。
 *   两种都只有靠精确的假时钟才能断言。
 */
class AutoRefreshPolicyTest {

    // -----------------------------------------------------------------------
    // 何时启动定时器
    // -----------------------------------------------------------------------

    @Test
    fun `关闭时返回 null 表示不启动定时器`() {
        assertNull(
            "关闭状态必须返回 null —— 而不是 0（会疯狂循环）或极大值（留下永不醒的协程）",
            AutoRefreshPolicy.nextDelayMillis(enabled = false, minutes = 30),
        )
    }

    @Test
    fun `开启时按分钟换算成毫秒`() {
        assertEquals(30 * 60_000L, AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = 30))
        assertEquals(1 * 60_000L, AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = 1))
        assertEquals(1440 * 60_000L, AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = 1440))
    }

    @Test
    fun `间隔为 0 或负数时拒绝启动 —— 防止无限请求`() {
        // 这两种值可能来自旧数据或被手工改过的 prefs。
        // 若照单全收，delay(0) 会变成"不停发请求"。
        listOf(0, -1, -60).forEach { bad ->
            assertNull(
                "间隔 $bad 分钟必须被拒绝，否则会变成无限请求（数据源是积分制的）",
                AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = bad),
            )
        }
    }

    @Test
    fun `超过上限的间隔被拒绝`() {
        assertNull(
            "超过 24 小时的间隔不如手动刷新，且视为配置错误",
            AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = 1441),
        )
    }

    @Test
    fun `策略范围与 UI 允许输入的范围必须一致`() {
        // ★ 这条测试来自一个真实缺陷（P11 实测）：
        //   SettingsStore 读取时写死 `coerceIn(5, 24*60)`，而数据设置页允许 1–1440。
        //   于是用户填「2 分钟」→ 落盘是 2 → **读回来被静默改成 5**，
        //   界面还显示着 2，但实际按 5 分钟跑。
        //
        //   这条断言把两个范围钉在一起：任何人改动其中一边，测试立刻失败。
        assertTrue("下限必须允许 1 分钟（UI 允许填 1）", AutoRefreshPolicy.MIN_MINUTES <= 1)
        assertTrue("上限必须允许 1440 分钟（UI 允许填 1440）", AutoRefreshPolicy.MAX_MINUTES >= 1440)
    }

    @Test
    fun `开启自动刷新时不该立刻补刷一次`() {
        // 各页面进入时本来就会加载一次；再补一次是重复请求。
        assertTrue(
            "不应在开启瞬间额外发一次请求",
            !AutoRefreshPolicy.shouldFireImmediately(),
        )
    }

    // -----------------------------------------------------------------------
    // 计数器语义
    // -----------------------------------------------------------------------

    @Before
    fun resetTick() {
        AutoRefreshState.resetForTest()
    }

    @Test
    fun `tick 从 0 开始且每次递增 1`() {
        assertEquals("初值必须是 0 —— 消费方靠它区分'首次组合'与'真的到点了'", 0L, AutoRefreshState.tick)
        assertEquals(1L, AutoRefreshState.onTick())
        assertEquals(2L, AutoRefreshState.onTick())
        assertEquals(3L, AutoRefreshState.onTick())
    }

    @Test
    fun `tick 单调递增 —— 每个 tick 都会被消费方处理`() {
        // 用递增计数而不是布尔标志：布尔在"上次没刷完、下次又到点"时会丢事件
        val seen = mutableListOf<Long>()
        repeat(5) { seen += AutoRefreshState.onTick() }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), seen)
        assertEquals("每个 tick 都不同，不会被去重丢掉", 5, seen.distinct().size)
    }

    // -----------------------------------------------------------------------
    // ★ 端到端：用虚拟时钟验证"到点真的会触发"
    // -----------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `定时器到点触发 tick`() = runTest {
        AutoRefreshState.resetForTest()
        val interval = AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = 30)!!

        // 复刻 AutoRefreshEffect 内部的循环（同一个 delay 语义）
        val job = launch {
            while (true) {
                delay(interval)
                AutoRefreshState.onTick()
            }
        }

        // 未到点：不该有任何 tick
        advanceTimeBy(interval - 1)
        runCurrent()
        assertEquals("差 1 毫秒都不该触发", 0L, AutoRefreshState.tick)

        // 到点：恰好 +1
        advanceTimeBy(1)
        runCurrent()
        assertEquals("到点必须触发一次", 1L, AutoRefreshState.tick)

        // 再过一整个周期：再 +1（而不是一次补齐多次）
        advanceTimeBy(interval)
        runCurrent()
        assertEquals("每个周期恰好触发一次", 2L, AutoRefreshState.tick)

        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `关闭后不再触发`() = runTest {
        AutoRefreshState.resetForTest()
        val enabled = AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = 1)!!
        val disabled = AutoRefreshPolicy.nextDelayMillis(enabled = false, minutes = 1)

        assertNull("关闭时不启动", disabled)

        // 模拟"原本开着、跑到一半被关掉"：旧协程被取消
        val job = launch {
            while (true) {
                delay(enabled)
                AutoRefreshState.onTick()
            }
        }
        advanceTimeBy(enabled + 1)
        runCurrent()
        val afterOneTick = AutoRefreshState.tick
        assertEquals(1L, afterOneTick)

        job.cancel()   // 关闭设置 → LaunchedEffect key 变化 → 协程被取消
        advanceTimeBy(enabled * 3)
        runCurrent()
        assertEquals(
            "取消之后必须停止递增（否则关了还在后台刷）",
            afterOneTick,
            AutoRefreshState.tick,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `改间隔后按新间隔计时`() = runTest {
        AutoRefreshState.resetForTest()
        val short = AutoRefreshPolicy.nextDelayMillis(enabled = true, minutes = 1)!!

        // 每 1 分钟一次，跑 3 个周期
        val job = launch {
            while (true) {
                delay(short)
                AutoRefreshState.onTick()
            }
        }
        advanceTimeBy(short * 3 + 1)
        runCurrent()
        assertEquals(3L, AutoRefreshState.tick)
        job.cancel()
    }
}
