package com.utilitybox.core.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * 全局主题色（强调色自定义）的派生逻辑。
 *
 * ★ 这一组测试存在的理由：派生逻辑的作用是**保证用户选任何颜色都不会读出问题**
 *   （太亮看不见、太暗看不见、前景字与底色撞色）。
 *   这类"保证"如果只靠人眼抽查，就会在某个没人试过的颜色上翻车。
 */
class UbAccentTest {

    // -----------------------------------------------------------------------
    // HSV 换算：所有派生的地基，错了后面全错
    // -----------------------------------------------------------------------

    @Test
    fun `RGB 转 HSV 再转回来 不丢色`() {
        val samples = listOf(
            Color(0xFFFF0000), Color(0xFF00FF00), Color(0xFF0000FF),
            Color(0xFF2F6BFF), Color(0xFFF59F00), Color(0xFF6B7280),
            Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF7F3F1F),
        )
        samples.forEach { col ->
            val hsv = UbHsv.fromRgb(col.red, col.green, col.blue)
            val rgb = UbHsv.toRgb(hsv[0], hsv[1], hsv[2])
            // 8bit 量化误差允许 ±1/255
            assertTrue(
                "$col 往返后偏了：$rgb",
                abs(rgb[0] - col.red) <= 0.004f &&
                    abs(rgb[1] - col.green) <= 0.004f &&
                    abs(rgb[2] - col.blue) <= 0.004f,
            )
        }
    }

    @Test
    fun `标准色的色相落在教科书的度数上`() {
        // 红 0 / 绿 120 / 蓝 240 —— 这三条错了说明通道顺序写错
        assertEquals(0f, UbHsv.fromRgb(1f, 0f, 0f)[0], 0.01f)
        assertEquals(120f, UbHsv.fromRgb(0f, 1f, 0f)[0], 0.01f)
        assertEquals(240f, UbHsv.fromRgb(0f, 0f, 1f)[0], 0.01f)
    }

    @Test
    fun `灰色没有色相 饱和度为 0`() {
        val hsv = UbHsv.fromRgb(0.5f, 0.5f, 0.5f)
        assertEquals(0f, hsv[1], 0.001f)
    }

    @Test
    fun `深红也能得到正的色相 不会出现负数`() {
        // 红为主、蓝偏高时 (g-b)/d 为负，60 倍后是负角度 —— 必须归一到 0..360
        val h = UbHsv.fromRgb(1f, 0f, 0.5f)[0]
        assertTrue("色相不得为负：$h", h >= 0f && h < 360f)
    }

    // -----------------------------------------------------------------------
    // 明度整形：用户选什么都不能变成"看不见的按钮"，
    // 但**普通颜色必须原样保留**（第一版无差别夹取，把自带默认蓝都改暗了）
    // -----------------------------------------------------------------------

    private fun lum(c: Color) = ubLuminance(c.red, c.green, c.blue)

    /** 强调色当"白底上的文字"时的对比度 */
    private fun onWhite(c: Color) = 1.05f / (lum(c) + 0.05f)

    @Test
    fun `★ 应用自带的强调色在各自主题下原样通过`() {
        // 这是第一版真实翻车的地方：按 HSV value 夹取，把 #2F6BFF（v=1.0）
        // 压到 0.62，整排预设色块都变灰扑扑。
        // 自带色本身就是校准过的（它定义了整形的上界），整形**不能**碰它们。
        assertEquals(UbLightColors.accent, ubAccentFor(UbLightColors.accent, dark = false))
        assertEquals(UbDarkColors.accent, ubAccentFor(UbDarkColors.accent, dark = true))
    }

    @Test
    fun `★ 全部预设色在浅色主题下原样通过`() {
        // 预设就是为浅色主题挑的（亮度 ≤ 自带强调色），任何一个被改色都说明阈值定错了
        UbAccentPresets.mapNotNull { it.seed }.forEach { seed ->
            assertEquals("浅色主题改动了预设 $seed", seed, ubAccentFor(seed, dark = false))
        }
    }

    @Test
    fun `★ 每个预设当白底上的文字都够看`() {
        // 强调色会被当文字/图标画在白底上（底部导航选中项、强调色标签）。
        // 第一版凭手感挑的琥珀 #F59F00 亮度 0.442，这条只有 2.13:1 ——
        // 当色块底很好，当文字等于"选中了却看不见字"。
        // 这条断言就是那条选色硬约束。
        val all = UbAccentPresets.mapNotNull { it.seed } +
            listOf(UbLightColors.accent)
        all.forEach { seed ->
            val ratio = onWhite(ubAccentFor(seed, dark = false))
            assertTrue(
                "预设 $seed 当白底文字只有 ${"%.2f".format(ratio)}:1，低于 4.4",
                ratio >= 4.4f,
            )
        }
    }

    @Test
    fun `深色主题把预设提亮到深色可用区间`() {
        // 浅色主题那批深色在近黑底上读不出来，深色主题必须把它们提亮
        val floor = lum(UbDarkColors.accent)
        UbAccentPresets.mapNotNull { it.seed }.forEach { seed ->
            val lifted = ubAccentFor(seed, dark = true)
            assertTrue(
                "深色主题下预设 $seed 只到亮度 ${lum(lifted)}，低于自带深色强调色 $floor",
                lum(lifted) >= floor - 0.005f,
            )
        }
    }

    @Test
    fun `浅色主题把过亮的种子压到可读范围`() {
        // 纯白当强调色在白底上完全看不见 —— 必须被压暗
        val accent = ubAccentFor(Color(0xFFFFFFFF), dark = false)
        assertTrue("亮度过高：${lum(accent)}", lum(accent) <= lum(UbLightColors.accent) + 0.005f)
        assertTrue("压得过暗：${lum(accent)}", lum(accent) >= 0.03f)
    }

    @Test
    fun `深色主题把过暗的种子提亮`() {
        // 纯黑当强调色在近黑底上完全看不见 —— 必须被提亮
        val accent = ubAccentFor(Color(0xFF000000), dark = true)
        assertTrue("亮度过低：${lum(accent)}", lum(accent) >= lum(UbDarkColors.accent) - 0.005f)
    }

    @Test
    fun `整形只动亮度 色相基本保持`() {
        val violet = Color(0xFF7C4DFF) // 极端色会被整形，用它验证色相不漂
        val extreme = Color(0xFFFFFFFF)
        listOf(false, true).forEach { dark ->
            val before = UbHsv.fromRgb(violet.red, violet.green, violet.blue)[0]
            val out = ubAccentFor(violet, dark)
            val after = UbHsv.fromRgb(out.red, out.green, out.blue)[0]
            assertTrue("dark=$dark 色相从 $before 漂到 $after", abs(after - before) <= 2f)
        }
        // 白色种子的饱和度是 0，整形后应当仍是无彩色（不凭空造出一个色相）
        val fromWhite = ubAccentFor(extreme, dark = false)
        assertEquals(0f, UbHsv.fromRgb(fromWhite.red, fromWhite.green, fromWhite.blue)[1], 0.01f)
    }

    @Test
    fun `浅色与深色主题下 同一个种子的亮度方向相反`() {
        val seed = Color(0xFFFFFFFF)
        assertTrue(
            "深色主题的强调色应当更亮",
            lum(ubAccentFor(seed, dark = true)) > lum(ubAccentFor(seed, dark = false)),
        )
    }

    // -----------------------------------------------------------------------
    // 前景色：这是"选了亮黄就看不见字"的唯一防线
    // -----------------------------------------------------------------------

    @Test
    fun `亮色强调色上配深色文字`() {
        val yellow = ubAccentFor(Color(0xFFFFE600), dark = true) // 深色主题下会保持较亮
        val fg = ubOnAccent(yellow)
        assertTrue("亮底应配深字，实际 $fg", fg.red < 0.2f && fg.green < 0.2f && fg.blue < 0.2f)
    }

    @Test
    fun `深色强调色上配白色文字`() {
        val navy = ubAccentFor(Color(0xFF102060), dark = false)
        val fg = ubOnAccent(navy)
        assertTrue("暗底应配白字，实际 $fg", fg.red > 0.9f && fg.green > 0.9f && fg.blue > 0.9f)
    }

    @Test
    fun `任意强调色下的前景都有足够对比度`() {
        // 覆盖一圈色相 + 极端明度，逐一验证"自动选出的前景色"确实是最优的那个
        val seeds = buildList {
            for (h in 0 until 360 step 30) {
                for (v in listOf(0.05f, 0.3f, 0.6f, 0.95f)) {
                    val rgb = UbHsv.toRgb(h.toFloat(), 1f, v)
                    add(Color(rgb[0], rgb[1], rgb[2], 1f))
                }
            }
        }
        seeds.forEach { seed ->
            listOf(false, true).forEach { dark ->
                val accent = ubAccentFor(seed, dark)
                val fg = ubOnAccent(accent)
                val ratio = contrastRatio(accent, fg)
                // ★ 门槛取 4.4 而不是 4.5：应用自带的 #2F6BFF 自身对白字是 4.50，
                //   而整形边界就锚在它身上，再叠加 Color 的 8bit 量化（±1/255，
                //   在低亮度端会把对比度推动约 0.01），4.5 这个整数关口会被量化误差击穿。
                //   真正的保证是"不比自带强调色更差"，4.4 是这条保证的保守表述。
                assertTrue(
                    "对比度不足：seed=$seed dark=$dark accent=$accent fg=$fg ratio=$ratio",
                    ratio >= 4.4f,
                )
            }
        }
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        // ★ 必须用 WCAG 的 2.4 次幂，不能用 x⁴ 近似 ——
        //   近似值在中等明度上会明显偏低（实测把 4.94 算成 3.31），
        //   于是测试会"证明"一个并不存在的对比度缺陷。
        fun lin(c: Float): Float =
            if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        fun lum(c: Color) = 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
        val l1 = maxOf(lum(a), lum(b))
        val l2 = minOf(lum(a), lum(b))
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    // -----------------------------------------------------------------------
    // withAccent：只改该改的
    // -----------------------------------------------------------------------

    @Test
    fun `种子为 null 时原样返回 不覆盖默认色`() {
        // 这是"恢复默认"能回到主题自带那支颜色的前提
        assertEquals(UbLightColors, UbLightColors.withAccent(null))
        assertEquals(UbDarkColors, UbDarkColors.withAccent(null))
    }

    @Test
    fun `withAccent 只改强调色三件套 其余令牌不动`() {
        val out = UbLightColors.withAccent(Color(0xFFE03131))
        assertEquals(UbLightColors.bgBase, out.bgBase)
        assertEquals(UbLightColors.bgElevated, out.bgElevated)
        assertEquals(UbLightColors.textPrimary, out.textPrimary)
        assertEquals(UbLightColors.isDark, out.isDark)
        assertNotEquals(UbLightColors.accent, out.accent)
    }

    @Test
    fun `accentSoft 与 accent 同色相 且带透明度`() {
        val out = UbLightColors.withAccent(Color(0xFF0CA678))
        assertTrue("软色底必须半透明", out.accentSoft.alpha < 1f)
        val h1 = UbHsv.fromRgb(out.accent.red, out.accent.green, out.accent.blue)[0]
        val h2 = UbHsv.fromRgb(out.accentSoft.red, out.accentSoft.green, out.accentSoft.blue)[0]
        assertTrue("软色底应与强调色同色相：$h1 vs $h2", abs(h1 - h2) <= 2f)
    }

    @Test
    fun `深色主题的软色底比浅色更实`() {
        val seed = Color(0xFF4C6EF5)
        assertTrue(
            UbDarkColors.withAccent(seed).accentSoft.alpha >
                UbLightColors.withAccent(seed).accentSoft.alpha,
        )
    }

    // -----------------------------------------------------------------------
    // 落盘编码 + 预设表
    // -----------------------------------------------------------------------

    @Test
    fun `ARGB 整数往返无损`() {
        listOf(0xFF2F6BFFL, 0xFFE03131L, 0xFF000000L, 0xFFFFFFFFL, 0x804C6EF5L).forEach { v ->
            assertEquals(v, ubColorOfArgb(v).toArgbLong())
        }
    }

    @Test
    fun `预设 id 唯一 且包含默认项`() {
        val ids = UbAccentPresets.map { it.id }
        assertEquals("预设 id 不能重复：$ids", ids.size, ids.toSet().size)
        assertEquals("必须且只能有一个『默认』（seed 为 null）", 1, UbAccentPresets.count { it.seed == null })
        assertTrue("预设太少，达不到『默认几种颜色』的要求", UbAccentPresets.size >= 6)
    }

    @Test
    fun `预设色本身不重复`() {
        val seeds = UbAccentPresets.mapNotNull { it.seed }
        assertEquals("有重复的预设色：$seeds", seeds.size, seeds.toSet().size)
    }

    @Test
    fun `十六进制格式为大写六位`() {
        assertEquals("2F6BFF", ubHexOf(Color(0xFF2F6BFF)))
        assertEquals("000000", ubHexOf(Color(0xFF000000)))
        assertEquals("FFFFFF", ubHexOf(Color(0xFFFFFFFF)))
    }

    // -----------------------------------------------------------------------
    // 入场错峰延迟的上界 —— 曾经造成"滑动越快、空白越久"的事故
    // -----------------------------------------------------------------------

    /** 与生产值保持一致：100ms 首延迟、40ms 每步（见 UbMotion） */
    private val enterDelay = 100
    private val stagger = 40

    @Test
    fun `★ 入场延迟与索引无关地上界`() {
        // 事故原式是 enterDelay + index × stagger，随索引无上限增长：
        // 第 30 项 1.3 秒、第 100 项 4.1 秒 —— 而这些项**已经进入视口**，
        // 却因为 alpha=0 一直空白。用户原话「滑动过快就会造成全白」。
        val max = ubEnterMaxDelayMillis(enterDelay, stagger)
        listOf(0, 1, 5, 6, 7, 30, 100, 113, 10_000).forEach { index ->
            val d = ubEnterDelayMillis(index, enterDelay, stagger)
            assertTrue("index=$index 的延迟 $d 超过了上界 $max", d <= max)
        }
    }

    @Test
    fun `★ 上界本身是"感知不到在等"的量级`() {
        // 上界如果只是"比 4 秒小"，问题依然存在。
        // 真正的验收标准是：任何元素的最长等待都在半秒以内。
        val max = ubEnterMaxDelayMillis(enterDelay, stagger)
        assertTrue("入场延迟上界 $max ms 太长，用户会感到在等", max <= 500)
    }

    @Test
    fun `错峰在小索引上仍然生效`() {
        // 修 bug 不能把功能一起删掉：前几个元素仍应依次错开
        assertEquals(enterDelay, ubEnterDelayMillis(0, enterDelay, stagger))
        assertEquals(enterDelay + stagger, ubEnterDelayMillis(1, enterDelay, stagger))
        assertEquals(enterDelay + 3 * stagger, ubEnterDelayMillis(3, enterDelay, stagger))
    }

    @Test
    fun `超过上限的索引全部落在同一个延迟上`() {
        val capped = ubEnterDelayMillis(6, enterDelay, stagger)
        assertEquals(capped, ubEnterDelayMillis(7, enterDelay, stagger))
        assertEquals(capped, ubEnterDelayMillis(1000, enterDelay, stagger))
    }

    @Test
    fun `负数索引不会产生负延迟`() {
        // 负 delayMillis 会让 tween 直接抛异常 / 行为未定义
        assertTrue(ubEnterDelayMillis(-5, enterDelay, stagger) >= enterDelay)
    }

    // -----------------------------------------------------------------------
    // 入场闸门 —— 让"动画回来了"和"滚动不空白"同时成立
    // -----------------------------------------------------------------------

    @Test
    fun `★ 闸门只放行一屏的名额`() {
        // 这是"动画回来了 + 滚动不空白"能同时成立的关键：
        // 数据到达时那一屏元素认领名额播入场，之后滚动进来的元素一律直接显示。
        val gate = UbListEntrance()
        val firstScreen = (1..8).map { gate.claim() }
        assertTrue("第一屏应当全部放行：$firstScreen", firstScreen.all { it })
        // 第 9 个开始（也就是滚动进来的）必须被拒绝，否则又会"越滑越白"
        val later = (1..50).map { gate.claim() }
        assertTrue("滚动进入的元素不得再播入场：${later.count { it }} 个被放行", later.none { it })
    }

    @Test
    fun `闸门名额用尽后恒为 false`() {
        val gate = UbListEntrance()
        repeat(8) { gate.claim() }
        repeat(100) { assertTrue(!gate.claim()) }
    }

    @Test
    fun `不同闸门互不影响`() {
        // 每次列表组合一个独立闸门；否则一个页面的滚动会吃掉另一个页面的名额
        val a = UbListEntrance()
        val b = UbListEntrance()
        repeat(8) { a.claim() }
        assertTrue("另一个闸门应当仍有名额", b.claim())
        assertTrue("a 已用尽", !a.claim())
    }
}
