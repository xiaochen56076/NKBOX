package com.utilitybox.game.wutheringwaves.kuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 唤取记录：链接解析与统计。
 *
 * ★ 这两块都是**纯函数**，也都会**静默出错**：
 *   - 解析错了 → 用户看到"解析失败"，但他贴的链接其实是对的
 *   - 统计错了 → 数字看着合理但是错的，没人会发现
 *   所以必须由单测钉住。
 */
class WuwaGachaTest {

    // -----------------------------------------------------------------------
    // 链接解析 —— fragment 陷阱
    // -----------------------------------------------------------------------

    private val realLink = "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
        "#/record?svr_id=76402e5b20be2c39f095a152090afddc&player_id=101812955" +
        "&record_id=0123456789abcdef&resources_id=abcdef0123456789"

    @Test
    fun `★ 参数在 URL fragment 里 不在 query 里`() {
        // 这是最容易写错的地方：`#/record?svr_id=…` 的参数挂在 fragment 上，
        // 按常规 query string 解析一个都拿不到 —— 表现是"用户贴对了却说解析失败"。
        val link = WuwaGachaApi.parseLink(realLink)
        assertNotNull("fragment 里的参数必须能解析出来", link)
        assertEquals("76402e5b20be2c39f095a152090afddc", link!!.serverId)
        assertEquals("101812955", link.playerId)
        assertEquals("0123456789abcdef", link.recordId)
        assertEquals("abcdef0123456789", link.resourcesId)
        assertFalse("国服链接不应判为国际服", link.oversea)
    }

    @Test
    fun `国际服链接会被识别`() {
        val os = realLink.replace("-resources.aki-game.com", "-resources-oversea.aki-game.com")
        assertTrue("域名带 -oversea 时应当判为国际服", WuwaGachaApi.parseLink(os)!!.oversea)
    }

    @Test
    fun `粘贴时带前后文字或换行也能解析`() {
        // 用户从聊天窗口复制时经常带上多余内容
        val messy = "  唤取记录链接：\n$realLink\n（复制于 2026-10-15）  "
        val link = WuwaGachaApi.parseLink(messy)
        assertNotNull(link)
        assertEquals("0123456789abcdef", link!!.recordId)
    }

    @Test
    fun `缺少任一必需参数就返回 null`() {
        listOf("svr_id", "player_id", "record_id", "resources_id").forEach { missing ->
            val broken = Regex("""[?&]$missing=[^&\s#]+""").replace(realLink, "")
            assertNull("缺 $missing 时必须判为解析失败", WuwaGachaApi.parseLink(broken))
        }
    }

    @Test
    fun `完全无关的文本不会误判为链接`() {
        assertNull(WuwaGachaApi.parseLink("https://www.bilibili.com/video/BV1xx"))
        assertNull(WuwaGachaApi.parseLink(""))
        assertNull(WuwaGachaApi.parseLink("随便一段中文"))
    }

    // -----------------------------------------------------------------------
    // 统计
    // -----------------------------------------------------------------------

    private fun rec(name: String, rarity: Int, time: String, pool: Int = 1) =
        GachaRecord(poolType = pool, name = name, rarity = rarity, resourceType = "角色", count = 1, time = time)

    @Test
    fun `总抽数与稀有度计数`() {
        val stats = GachaStats.of(
            listOf(
                rec("A", 3, "2026-01-01 10:00:00"),
                rec("B", 4, "2026-01-01 11:00:00"),
                rec("C", 5, "2026-01-01 12:00:00"),
                rec("D", 3, "2026-01-01 13:00:00"),
            ),
        )
        assertEquals(4, stats.total)
        assertEquals(1, stats.fiveStarCount)
        assertEquals(1, stats.fourStarCount)
    }

    @Test
    fun `★ 没有五星时平均出金是 null 而不是 0 或无穷`() {
        // 把"没有"显示成 0 或 ∞ 都是在编造 —— UI 上必须显示成"—"
        val stats = GachaStats.of(listOf(rec("A", 3, "2026-01-01 10:00:00")))
        assertNull(stats.averageFiveStarPull)
    }

    @Test
    fun `★ 平均出金按时间正序算间隔 与接口返回顺序无关`() {
        // 接口是**按时间倒序**返回的。如果直接按数组下标累加，
        // 算出来的是"两次五星之间隔了几条"，方向对但边界会错位。
        val newestFirst = listOf(
            rec("金2", 5, "2026-01-01 10:04:00"),
            rec("x", 3, "2026-01-01 10:03:00"),
            rec("金1", 5, "2026-01-01 10:02:00"),
            rec("y", 3, "2026-01-01 10:01:00"),
            rec("z", 3, "2026-01-01 10:00:00"),
        )
        val stats = GachaStats.of(newestFirst)
        assertEquals("第一个金在第 3 抽、第二个金在第 2 抽", listOf(3, 2), stats.fiveStarGaps)
        assertEquals(2.5, stats.averageFiveStarPull!!, 0.001)
    }

    @Test
    fun `空记录不会崩`() {
        val stats = GachaStats.of(emptyList())
        assertEquals(0, stats.total)
        assertNull(stats.averageFiveStarPull)
    }

    @Test
    fun `未知卡池类型如实标注 不猜名字`() {
        assertEquals("角色活动唤取", WuwaGachaApi.poolLabel(1))
        // 猜错会让用户以为记录丢了；如实标出类型号才是能排查的信息
        assertTrue(WuwaGachaApi.poolLabel(99).contains("99"))
    }

    // -----------------------------------------------------------------------
    // 去重合并 —— 出错的表现是丢用户数据
    // -----------------------------------------------------------------------

    @Test
    fun `★ 分多次拉不同卡池不会互相覆盖`() {
        // 接口一次只返回一个池，用户会分几次粘链接。
        // 如果 save 是整体覆盖，拉第二个池就会把第一个池的记录清掉 —— 那是丢数据。
        val pool1 = listOf(rec("角色A", 5, "2026-01-01 10:00:00", pool = 1))
        val pool2 = listOf(rec("武器B", 5, "2026-01-01 10:00:00", pool = 2))

        val afterFirst = mergeRecords(emptyList(), pool1)
        val afterSecond = mergeRecords(afterFirst, pool2)

        assertEquals("两个池的记录都要在", 2, afterSecond.size)
    }

    @Test
    fun `重复获取同一批记录不会翻倍`() {
        val batch = listOf(
            rec("A", 3, "2026-01-01 10:00:00"),
            rec("B", 5, "2026-01-01 10:01:00"),
        )
        val once = mergeRecords(emptyList(), batch)
        val twice = mergeRecords(once, batch)
        assertEquals("同一批记录合并两次仍应是 2 条", 2, twice.size)
    }

    @Test
    fun `同名不同时间算两条`() {
        // 同一个角色在不同时间各出一次，是两条真实记录，不能被去重掉
        val r = listOf(
            rec("漂泊者", 5, "2026-01-01 10:00:00"),
            rec("漂泊者", 5, "2026-01-02 10:00:00"),
        )
        assertEquals(2, mergeRecords(emptyList(), r).size)
    }
}
