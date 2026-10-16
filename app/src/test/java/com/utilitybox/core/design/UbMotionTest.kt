package com.utilitybox.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动效令牌与缓动曲线的测试（P11 交互连贯性整改）。
 *
 * ★ 这里钉的是三类**只能靠肉眼发现、写错了不会崩**的问题：
 * 1. 「减少动态效果」漏掉新档位（静默失效）
 * 2. 缓动曲线幂次写错（观感变"软"，不报错）
 * 3. 按压档位大小关系反了
 */
class UbMotionTest {

    private val eps = 1e-4f

    // -----------------------------------------------------------------------
    // 档位归零：P11 实测踩到的那个静默失效
    // -----------------------------------------------------------------------

    @Test
    fun `reduced 必须把每一档都归零 —— 新增档位忘了加会被这条抓住`() {
        // ★★ 为什么用 toString() 而不是逐个 assertEquals：
        //
        //   真正的 bug 不是"某个已知档位忘了归零"，而是
        //   **将来新增的档位**忘了归零 —— 那种错逐个断言永远抓不到。
        //
        //   UbMotion 是 data class，toString() 会列出**所有**属性
        //   （`UbMotion(press=0, pressWeak=0, …)`），
        //   所以"文本里一个非零数字都不许有"这条断言会自动覆盖新字段。
        //
        //   背景：上一版写的是 `UbMotion(0, 0, 0, 0)` —— 按**位置**传前四个参数，
        //   后来新增的 enter/enterDelay 保留了非零默认值，
        //   于是打开"减少动态效果"后入场动画照放，而且完全看不出来。
        val text = UbMotion.reduced().toString()
        val numbers = Regex("""-?\d+""").findAll(text).map { it.value }.toList()

        assertTrue("reduced() 的文本里没有数字，断言失效了：$text", numbers.isNotEmpty())
        assertTrue(
            "UbMotion.reduced() 必须一个非零时长都不含（新增档位请一并归零）。实际：$text",
            numbers.all { it == "0" },
        )
    }

    @Test
    fun `默认值必须是可动的 reduced 必须不可动`() {
        assertTrue("默认令牌必须允许动效", UbMotion().enabled)
        assertFalse(
            "reduced 之后 enabled 必须为 false —— 这是所有动效判断的总开关",
            UbMotion.reduced().enabled,
        )
    }

    // -----------------------------------------------------------------------
    // 缓动纲领：进场 Decelerate(3f) / 退场 Accelerate(3f)
    // -----------------------------------------------------------------------

    @Test
    fun `两条缓动都必须从 0 到 1 —— 否则动画会从半路开始或停不到终点`() {
        listOf("UbEaseEnter" to UbEaseEnter, "UbEaseExit" to UbEaseExit).forEach { (name, ease) ->
            assertEquals("$name 必须从 0 开始", 0f, ease.transform(0f), eps)
            assertEquals("$name 必须到 1 结束", 1f, ease.transform(1f), eps)
        }
    }

    @Test
    fun `进场是减速 退场是加速`() {
        // 中点的位置就足以区分两类曲线
        assertTrue(
            "进场曲线在中点必须已经走完大半（减速 = 前快后慢），" +
                "实际 ${UbEaseEnter.transform(0.5f)}",
            UbEaseEnter.transform(0.5f) > 0.5f,
        )
        assertTrue(
            "退场曲线在中点必须还没走完一半（加速 = 前慢后快），" +
                "实际 ${UbEaseExit.transform(0.5f)}",
            UbEaseExit.transform(0.5f) < 0.5f,
        )
    }

    @Test
    fun `幂次必须是 3 而不是默认的 2 —— 否则观感会变软`() {
        // 报告 §9.1 特别指出用 AccelerateInterpolator(3.0f) / DecelerateInterpolator(3.0f)：
        //   1 − (1−t)³ 在 t=0.5 处 = 0.875
        //   若是默认的二次 1 − (1−t)² 则 = 0.75（Android 默认 factor=1 即二次）
        // 这条断言把"三次"钉住：改成二次或线性都会失败。
        assertEquals(
            "进场必须是三次方减速（1−(1−t)³），t=0.5 处应为 0.875",
            0.875f,
            UbEaseEnter.transform(0.5f),
            eps,
        )
        assertEquals(
            "退场必须是三次方加速（t³），t=0.5 处应为 0.125",
            0.125f,
            UbEaseExit.transform(0.5f),
            eps,
        )
    }

    @Test
    fun `两条缓动都必须单调不减 —— 否则动画会中途倒退`() {
        listOf("UbEaseEnter" to UbEaseEnter, "UbEaseExit" to UbEaseExit).forEach { (name, ease) ->
            var prev = -1f
            var t = 0f
            while (t <= 1.0001f) {
                val v = ease.transform(t)
                assertTrue("$name 在 t=$t 处倒退了：$prev → $v", v >= prev - eps)
                prev = v
                t += 0.02f
            }
        }
    }

    // -----------------------------------------------------------------------
    // 按压档位
    // -----------------------------------------------------------------------

    @Test
    fun `按压档位的大小关系必须是 卡片小于行小于不动`() {
        // 缩小得越多 = 反馈越强。卡片（大块区域）要比列表行更强，否则大卡片按下去几乎看不出。
        assertTrue(
            "卡片档必须比列表行档缩得更多（反馈更强）：" +
                "Card=${UbPressScale.Card} Row=${UbPressScale.Row}",
            UbPressScale.Card < UbPressScale.Row,
        )
        assertTrue(
            "行档必须真的会缩小（不能等于不动）：Row=${UbPressScale.Row}",
            UbPressScale.Row < UbPressScale.None,
        )
        assertEquals("不动档必须恰好是 1", 1f, UbPressScale.None, eps)
    }

    @Test
    fun `按压档位取值与报告一致`() {
        // 报告 §5.3④：强反馈 0.95、弱反馈 0.98
        assertEquals(0.95f, UbPressScale.Card, eps)
        assertEquals(0.98f, UbPressScale.Row, eps)
    }
}
