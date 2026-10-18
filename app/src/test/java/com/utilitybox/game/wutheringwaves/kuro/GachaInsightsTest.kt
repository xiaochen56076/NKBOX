package com.utilitybox.game.wutheringwaves.kuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进阶统计的单元测试（P11 补 38）。
 *
 * ★ 这里每一条都在防"**界面正常但数字错**"：
 *   少一条记录、保底算错池、分母为 0 时显示成 0 ——
 *   用户看到的都是一个像模像样的数字，看不出问题。
 */
class GachaInsightsTest {

    private var seq = 0
    private fun r(
        pool: Int,
        name: String,
        rarity: Int,
        time: String,
        type: String = if (rarity == 5) "角色" else "武器",
    ) = GachaRecord(
        poolType = pool, name = name, rarity = rarity,
        resourceType = type, count = 1, time = time,
    )

    /** 造一个从 10:00:00 开始、每抽间隔 1 秒的连续段 */
    private fun secs(n: Int) = "2026-10-01 10:00:%02d".format(n % 60).let {
        val h = 10 + n / 3600; val m = (n / 60) % 60; val s = n % 60
        "2026-10-01 %02d:%02d:%02d".format(h, m, s)
    }

    @Test
    fun `空记录不崩溃且全部给 null 而不是 0`() {
        val g = computeInsights(emptyList())
        assertEquals(0, g.total)
        assertNull("没有五星时平均出金必须是 null，不能编成 0", g.avgFiveStar)
        assertNull(g.winRate)
        assertNull(g.upCharacterAvg)
        assertTrue(g.fiveStarPulls.isEmpty())
    }

    // -----------------------------------------------------------------------
    // P11 补 100：用户填的「常驻名单」参与判断 UP / 歪
    //
    // ★ 这组测试锁的是**判定能不能用**，而不是判得准不准 ——
    //   名单本身是用户提供的游戏事实，代码只负责按名单做数学。
    // -----------------------------------------------------------------------

    /** 常驻池只出过 1 个五星（维里奈），活动池出了两个五星 */
    private fun sampleWithOneStandard(): List<GachaRecord> = listOf(
        r(3, "维里奈", 5, "2026-10-01 10:00:00"),
        r(1, "忌炎", 5, "2026-10-02 10:00:00"),
        r(1, "卡卡罗", 5, "2026-10-03 10:00:00"),
    )

    @Test
    fun `没有名单时 一律不判 UP 或歪`() {
        val g = computeInsights(sampleWithOneStandard())
        // 只有 1 个推导出来的常驻，低于门槛 4
        assertEquals(1, g.standardSampleSize)
        assertFalse(g.upStatsReliable)
        assertTrue(
            "名单不足时必须全部 null —— 否则常驻角色会被判成 UP，算出假的不歪率",
            g.fiveStarPulls.filter { it.poolType == 1 }.all { it.isUp == null },
        )
        assertNull(g.winRate)
    }

    @Test
    fun `填了名单后 不在名单里的判 UP 在名单里的判歪`() {
        val roster = setOf("维里奈", "卡卡罗", "鉴心", "安可")
        val g = computeInsights(sampleWithOneStandard(), roster)

        assertTrue("名单够了，指标必须可用", g.upStatsReliable)

        val event = g.fiveStarPulls.filter { it.poolType == 1 }.associateBy { it.name }
        assertEquals("忌炎 不在常驻名单 → UP", true, event["忌炎"]!!.isUp)
        assertEquals("卡卡罗 在常驻名单 → 歪", false, event["卡卡罗"]!!.isUp)
    }

    @Test
    fun `名单和常驻池推导取并集`() {
        val roster = setOf("卡卡罗", "鉴心", "安可")
        val g = computeInsights(sampleWithOneStandard(), roster)

        // 维里奈来自常驻池推导，其余 3 个来自名单
        assertEquals("并集应当是 4 个", 4, g.standardSampleSize)
        assertTrue(g.upStatsReliable)
    }

    @Test
    fun `名单足够时 不歪率按活动池五星算`() {
        val roster = setOf("维里奈", "卡卡罗", "鉴心", "安可")
        val g = computeInsights(sampleWithOneStandard(), roster)

        // 活动池 2 个五星：忌炎(UP) / 卡卡罗(歪) → 50%
        assertEquals(0.5, g.winRate!!, 0.0001)
    }

    @Test
    fun `用户填空白或分隔符混乱时 名单解析仍然正确`() {
        assertEquals(
            setOf("维里奈", "卡卡罗", "鉴心", "安可"),
            GachaStandardRoster.parse(" 维里奈 , 卡卡罗、鉴心\n安可 \n\n"),
        )
        assertTrue("只有空白应当解析成空集", GachaStandardRoster.parse("   \n ,、 ").isEmpty())
    }

    @Test
    fun `各稀有度计数正确`() {
        val recs = listOf(
            r(1, "心", 5, secs(0)),
            r(1, "远行者佩枪·洞察", 3, secs(1)),
            r(1, "远行者佩枪·洞察", 3, secs(2)),
            r(1, "某四星", 4, secs(3)),
        )
        val g = computeInsights(recs)
        assertEquals(4, g.total)
        assertEquals(1, g.fiveStar)
        assertEquals(1, g.fourStar)
        assertEquals(2, g.threeStar)
    }

    @Test
    fun `垫抽数按池分别计算_不跨池`() {
        // ★ 关键：一个池的保底不会被另一个池消耗。
        //   如果用一个全局计数器，这里会算成 5 抽，而正确答案是 2 抽。
        val recs = listOf(
            r(1, "心", 5, secs(0)),      // 池1 第 1 抽就出金
            r(3, "甲", 3, secs(1)),
            r(3, "乙", 3, secs(2)),
            r(3, "丙", 3, secs(3)),
            r(1, "远行者佩枪·洞察", 3, secs(4)),
            r(1, "远行者佩枪·洞察", 3, secs(5)),
            r(1, "千咲", 5, secs(6)),    // 池1：距上次金 3 抽
        )
        val g = computeInsights(recs)
        val qianxiao = g.fiveStarPulls.first { it.name == "千咲" }
        assertEquals("池1 里两次金之间隔了 3 抽（4、5、6）", 3, qianxiao.pityUsed)
    }

    @Test
    fun `已垫抽数按池给出`() {
        val recs = listOf(
            r(1, "心", 5, secs(0)),
            r(1, "a", 3, secs(1)),
            r(1, "b", 3, secs(2)),
            r(2, "c", 3, secs(3)),
        )
        val g = computeInsights(recs)
        assertEquals("池1 出了金之后又垫了 2 抽", 2, g.pity[1])
        assertEquals("池2 没出过金，垫了 1 抽", 1, g.pity[2])
    }

    /**
     * 造一个**足够大的**常驻名单（4 个常驻五星）。
     *
     * ★ 为什么需要这个 helper（P11 补 38）：
     *   UP 判定依赖"常驻池里出过的五星"当名单。名单太小时，
     *   本来就在常驻池里的角色会因为"没抽到过"被误判成 UP ——
     *   在真实数据上这算出了 **89.5% 的假不歪率**。
     *
     *   所以 `MIN_STANDARD_SAMPLE = 4` 以下的样本**一律不判**。
     *   想测 UP 逻辑，就必须把样本造够 —— 这也正是门槛在起作用。
     */
    private fun standardSeed() = listOf(
        r(3, "维里奈", 5, secs(0)),
        r(3, "卡卡罗", 5, secs(1)),
        r(3, "鉴心", 5, secs(2)),
        r(3, "安可", 5, secs(3)),
    )

    @Test
    fun `常驻名单样本不足时_UPP相关指标全部为 null 且标记不可信`() {
        // ★★ 这条是整份文件里最要紧的一条。
        //   真实数据上（常驻池只出过 1 个五星）会算出 89.5% 的不歪率 ——
        //   真实的 50/50 不可能有 89.5%，那**不是统计，是编造**。
        val recs = listOf(
            r(3, "维里奈", 5, secs(0)),   // 名单只有 1 个
            r(1, "卡卡罗", 5, secs(1)),   // 它其实是常驻角色，但不该被误判成 UP
            r(1, "心", 5, secs(2)),
        )
        val g = computeInsights(recs)
        assertFalse("样本不足必须标记为不可信", g.upStatsReliable)
        assertNull("不能给不歪率", g.winRate)
        assertNull("不能给每UP需", g.upCharacterAvg)
        assertTrue("一个都不该判", g.fiveStarPulls.all { it.isUp == null })
    }

    @Test
    fun `常驻名单够大时才判UP`() {
        val recs = standardSeed() + listOf(
            r(1, "维里奈", 5, secs(10)),  // 名单里 → 歪
            r(1, "心", 5, secs(11)),      // 不在 → UP
        )
        val g = computeInsights(recs)
        assertTrue(g.upStatsReliable)
        val ev = g.fiveStarPulls.filter { it.poolType == 1 }
        assertEquals(false, ev.first { it.name == "维里奈" }.isUp)
        assertEquals(true, ev.first { it.name == "心" }.isUp)
    }

    @Test
    fun `常驻名单从常驻池推导_活动池里名字在名单里判为歪`() {
        val recs = standardSeed() + listOf(
            r(1, "卡卡罗", 5, secs(10)),  // 活动池又出 → 歪
            r(1, "心", 5, secs(11)),      // 名单里没有 → 判为 UP
        )
        val g = computeInsights(recs)
        assertEquals(setOf("维里奈", "卡卡罗", "鉴心", "安可"), g.standardNames)
        val inEvent = g.fiveStarPulls.filter { it.poolType == 1 }
        assertEquals("卡卡罗是歪", false, inEvent.first { it.name == "卡卡罗" }.isUp)
        assertEquals("心 是 UP", true, inEvent.first { it.name == "心" }.isUp)
    }

    @Test
    fun `常驻池的五星不做 UP 判定_给 null`() {
        val recs = listOf(r(3, "卡卡罗", 5, secs(0)))
        val g = computeInsights(recs)
        assertNull("常驻池没有 UP 概念，不能判成 UP 或歪", g.fiveStarPulls.first().isUp)
    }

    @Test
    fun `常驻名单为空时活动池也不判_而不是全判成UP`() {
        // ★ 这条最重要：如果常驻名单是空的（用户没抽过常驻池），
        //   "不在名单里"会把**每一个**五星都判成 UP ——
        //   于是"小保底不歪 100%"，完全是错的。
        val recs = listOf(r(1, "心", 5, secs(0)), r(1, "千咲", 5, secs(1)))
        val g = computeInsights(recs)
        assertTrue("名单为空，不能判", g.fiveStarPulls.all { it.isUp == null })
        assertNull("也就不能给不歪率", g.winRate)
    }

    @Test
    fun `小保底不歪率`() {
        val recs = standardSeed() + listOf(
            r(1, "卡卡罗", 5, secs(10)),  // 名单里 → 歪
            r(1, "心", 5, secs(11)),      // UP
            r(1, "千咲", 5, secs(12)),    // UP
            r(1, "尤诺", 5, secs(13)),    // UP
        )
        val g = computeInsights(recs)
        assertEquals(4, g.eventFiveStarCount)
        assertEquals(0.75, g.winRate!!, 0.0001)
    }

    @Test
    fun `没有UP时每UP需给 null 而不是 0`() {
        // ★ 分母为 0 时若给 0，界面会显示"每 UP 需 0 抽" —— 荒谬但看着正常
        val recs = listOf(
            r(3, "卡卡罗", 5, secs(0)),
            r(1, "卡卡罗", 5, secs(1)),  // 活动池只有一个五星，且是歪
        )
        val g = computeInsights(recs)
        assertNull("没有 UP 就不能给平均抽数", g.upCharacterAvg)
    }

    @Test
    fun `每UP需 = 该池总抽数 除以 该池UP个数`() {
        val recs = standardSeed() + listOf(
            r(1, "心", 5, secs(10)),
            r(1, "a", 3, secs(11)),
            r(1, "b", 3, secs(12)),
            r(1, "千咲", 5, secs(13)),
        )
        val g = computeInsights(recs)
        // 池1 共 4 抽，2 个 UP → 每 UP 需 2 抽
        assertEquals(2.0, g.upCharacterAvg!!, 0.0001)
    }

    @Test
    fun `星声 = 抽数 乘 160`() {
        val recs = (0 until 10).map { r(1, "x$it", 3, secs(it)) }
        val g = computeInsights(recs)
        assertEquals(1600L, g.perPool.first { it.poolType == 1 }.astrite)
    }

    @Test
    fun `真实数据的时间戳能正确分段`() {
        // ★★ 这条是被**真机截图**逼出来的（P11 补 43）。
        //
        //   模拟器上「版本总结」只显示了一期：「04-30 至 10-15 / 848 抽 / 21 金」——
        //   整段没被切开。而上面那条用 **10-01 / 10-31** 的单测是过的。
        //
        //   差别就在**时间戳的字面值**。所以这里直接用真实数据里的两个日期，
        //   它们之间隔了 50 天（实测数据里 05-21 → 07-10 正是 50 天）。
        val recs = listOf(
            r(1, "a", 3, "2026-05-21 12:00:00"),
            r(1, "b", 3, "2026-07-10 12:00:00"),
        )
        assertEquals("真实时间戳必须能解析出 50 天的间隔", 2, computeInsights(recs).periods.size)
    }

    @Test
    fun `真实数据的完整分段应多于一期`() {
        // 真实数据里有 5 个大间隔点，若整段只有 1 期就说明解析全失败了
        val recs = listOf(
            r(1, "a", 3, "2026-04-30 09:05:27"),
            r(1, "b", 3, "2026-05-21 12:51:56"),
            r(1, "c", 3, "2026-07-10 19:27:32"),
            r(1, "d", 3, "2026-08-20 10:36:47"),
            r(1, "e", 3, "2026-09-30 09:48:57"),
        )
        assertEquals("五个大间隔点应切成 5 期", 5, computeInsights(recs).periods.size)
    }

    @Test
    fun `时间分段_间隔超过10天切开`() {
        // ★ 阈值改过三次：12 小时 → 20 天 → 10 天。
        //   前两次的论证都是错的（详见 GachaInsights.PERIOD_GAP_DAYS 注释）。
        //   取 10 天的依据是**实测**：这份数据里相邻记录的最大间隔是 12 天。
        val recs = listOf(
            r(1, "a", 3, "2026-10-01 10:00:00"),
            r(1, "b", 3, "2026-10-01 10:00:05"),
            r(1, "c", 3, "2026-10-15 10:00:00"),  // 隔 14 天 → 必须切开
        )
        assertEquals(2, computeInsights(recs).periods.size)
    }

    @Test
    fun `实测的真实间隔能切开`() {
        // ★★ 这条直接用模拟器缓存里**实测到的最大间隔**：12 天
        //   （09-17 16:06:31 → 09-30 09:36:24）。
        //   如果阈值大于 12 天，这份数据就切不开 —— 那正是 20 天阈值的错误。
        val recs = listOf(
            r(1, "a", 3, "2026-09-17 16:06:31"),
            r(1, "b", 3, "2026-09-30 09:36:24"),
        )
        assertEquals("12 天的间隔必须能切开", 2, computeInsights(recs).periods.size)
    }

    @Test
    fun `间隔不到10天不切开`() {
        // 反向用例：几天不玩不等于换了时段。
        // 实测数据里 08-09 → 08-20 是 11 天，而 08-01 → 08-09 是 7 天 ——
        // 后者不该切开。
        val recs = listOf(
            r(1, "a", 3, "2026-08-01 18:38:10"),
            r(1, "b", 3, "2026-08-09 09:48:04"),
        )
        assertEquals(1, computeInsights(recs).periods.size)
    }

    @Test
    fun `记录顺序不影响结果`() {
        // 缓存里是什么顺序不该影响统计 —— 内部会排序
        val a = listOf(r(1, "心", 5, secs(0)), r(1, "x", 3, secs(1)), r(1, "y", 3, secs(2)))
        val g1 = computeInsights(a)
        val g2 = computeInsights(a.reversed())
        assertEquals(g1.total, g2.total)
        assertEquals(g1.fiveStarPulls.first().pityUsed, g2.fiveStarPulls.first().pityUsed)
        assertEquals(g1.periods.size, g2.periods.size)
    }

    @Test
    fun `五星逐个列出保留时间与池`() {
        val recs = listOf(r(1, "心", 5, secs(0)), r(2, "某武器", 5, secs(1), "武器"))
        val g = computeInsights(recs)
        assertEquals(listOf("心", "某武器"), g.fiveStarPulls.map { it.name })
        assertEquals(listOf(1, 2), g.fiveStarPulls.map { it.poolType })
        assertFalse(g.fiveStarPulls.any { it.time.isBlank() })
    }
}
