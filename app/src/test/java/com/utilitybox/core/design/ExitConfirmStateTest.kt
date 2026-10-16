package com.utilitybox.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「再按一次返回键退出」状态机测试。
 *
 * ★ 时间窗口是这里唯一容易写错的地方，所以把时钟做成可注入的，
 *   用假时间精确验证边界，而不是 `Thread.sleep` 靠运气。
 */
class ExitConfirmStateTest {

    /** 可手动推进的假时钟 */
    private class FakeClock {
        var now = 1_000_000L
        fun advance(ms: Long) { now += ms }
    }

    private fun state(clock: FakeClock, window: Long = 2000L) =
        ExitConfirmState(windowMillis = window, nowMillis = { clock.now })

    @Test
    fun `第一次返回不退出 而是提示`() {
        val clock = FakeClock()
        val s = state(clock)

        val shouldExit = s.onBack()

        assertFalse("第一次返回必须吞掉，不能退出", shouldExit)
        assertTrue("必须显示提示", s.showHint)
    }

    @Test
    fun `窗口内第二次返回才退出`() {
        val clock = FakeClock()
        val s = state(clock)

        assertFalse(s.onBack())
        clock.advance(500)                       // 0.5 秒后
        val second = s.onBack()

        assertTrue("窗口内第二次应退出", second)
        assertFalse("退出时提示应消失", s.showHint)
    }

    @Test
    fun `超过窗口后再按 视为重新开始 不退出`() {
        val clock = FakeClock()
        val s = state(clock, window = 2000L)

        assertFalse(s.onBack())
        clock.advance(2500)                      // 超过 2 秒
        val afterTimeout = s.onBack()

        assertFalse("超时后应是新一轮的第一次，不能退出", afterTimeout)
        assertTrue("仍应显示提示", s.showHint)
    }

    @Test
    fun `窗口边界值本身算在窗口内`() {
        val clock = FakeClock()
        val s = state(clock, window = 2000L)

        assertFalse(s.onBack())
        clock.advance(2000)                      // 恰好等于窗口
        assertTrue("恰好 2000ms 应算连按（<= 而非 <）", s.onBack())
    }

    @Test
    fun `窗口边界之后 1 毫秒就不算连按`() {
        val clock = FakeClock()
        val s = state(clock, window = 2000L)

        assertFalse(s.onBack())
        clock.advance(2001)
        assertFalse("2001ms 超出窗口", s.onBack())
    }

    @Test
    fun `退出后状态被重置 下一次又是需要按两次`() {
        val clock = FakeClock()
        val s = state(clock)

        assertFalse(s.onBack())
        clock.advance(300)
        assertTrue(s.onBack())                   // 第二次 → 退出

        // 模拟"用户没退成"（例如系统拦截了 finish）：状态必须已重置
        clock.advance(100)
        assertFalse("退出后必须重置，下一次仍是先提示", s.onBack())
        assertTrue(s.showHint)
    }

    @Test
    fun `提示可以被主动关闭`() {
        val clock = FakeClock()
        val s = state(clock)

        s.onBack()
        assertTrue(s.showHint)

        s.dismissHint()
        assertFalse("提示应能自动/主动消失", s.showHint)

        // 关闭提示不应影响计时：仍在窗口内连按就该退出
        clock.advance(300)
        assertTrue("关闭提示不该重置计时", s.onBack())
    }

    @Test
    fun `连续多次按 只在窗口内的那一次退出`() {
        val clock = FakeClock()
        val s = state(clock, window = 2000L)

        assertFalse("第1次", s.onBack())
        clock.advance(1800)
        assertTrue("第2次（窗口内）→ 退出", s.onBack())

        clock.advance(100)
        assertFalse("第3次 → 新一轮第一次", s.onBack())
        clock.advance(1900)
        assertTrue("第4次（窗口内）→ 退出", s.onBack())
    }
}
