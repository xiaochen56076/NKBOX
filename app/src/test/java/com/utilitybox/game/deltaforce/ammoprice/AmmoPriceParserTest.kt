package com.utilitybox.game.deltaforce.ammoprice

import com.utilitybox.core.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹药解析测试。
 *
 * ★ 本测试的字段全部**照抄 P10 实测的原始响应**，
 *   因为此前正是"凭结构猜字段路径"导致价格一直是 0。
 *
 * ★ 价格红线：0 / 负数 / 缺失一律读成 `null`（无数据），绝不显示 0。
 */
class AmmoPriceParserTest {

    /**
     * 取信封里的 `data` 节点再解析。
     *
     * ★ 注意入参语义：`AmmoPriceParser.parse` 接收的是 **data 节点**，
     *   不是整个信封 —— 生产代码里由 `ApiEnvelope.parse` 先拆出来。
     */
    private fun parse(body: String) = run {
        val env = Json.parse(body) ?: error("测试用 JSON 不合法: $body")
        AmmoPriceParser.parse(env.obj("data"))
    }

    private fun parseData(rawData: String) =
        AmmoPriceParser.parse(Json.parse(rawData) ?: error("测试用 JSON 不合法: $rawData"))

    /** P10 实测的真实字段结构（照抄，未简化） */
    private val realShape = """
        {"code":0,"message":"成功","data":{"bullets":[
          {"ammoDetail":{"armorHarmLevel":"高","harmRatio":100,"penetrationLevel":6},
           "avgPrice":4229,"gameName":"5.56x45mm M995","grade":5,"hasValidPriceData":true,
           "id":10305,"length":1,"objectID":37110600001,"objectName":"5.56x45mm M995",
           "pic":"https://playerhub.df.qq.com/playerhub/60004/object/gun/ammo/5.56x45mm.png",
           "prePic":"https://playerhub.df.qq.com/playerhub/60004/object/gun/ammo/p_5.56x45mm.png",
           "priceStats":{"avgPrice":4229,"dataPoints":37,"latestPrice":4229,
             "maxPrice":4500,"minPrice":4000,"priceChange":0,"priceChangePercent":0,
             "queryDays":7},
           "primaryClass":"ammo","secondClass":"ammo5.56x45","secondClassCN":"5.56x45mm",
           "shortName":"5.56x45","shortNameSec":"M995",
           "updatedAt":"2026-09-19T15:54:40.384Z","weight":"0.012","width":1},
          {"ammoDetail":{"armorHarmLevel":"高","harmRatio":100,"penetrationLevel":7},
           "avgPrice":0,"gameName":".338 Lap Mag AP","grade":6,"hasValidPriceData":false,
           "id":10340,"length":1,"objectID":37190600001,"objectName":".338 Lap Mag AP",
           "pic":"https://playerhub.df.qq.com/playerhub/60004/object/gun/ammo/.338.png",
           "prePic":"https://playerhub.df.qq.com/playerhub/60004/object/gun/ammo/p_.338.png",
           "priceStats":{"avgPrice":0,"dataPoints":0,"latestPrice":0,"maxPrice":0,
             "minPrice":0,"priceChange":0,"priceChangePercent":0,"queryDays":7},
           "primaryClass":"ammo","secondClass":"ammo338","secondClassCN":".338 Lap Mag",
           "shortName":".338","shortNameSec":"AP",
           "updatedAt":"2026-09-19T15:54:40.384Z","weight":"0.02","width":1}
        ]}}
    """.trimIndent()

    // -----------------------------------------------------------------------
    // 字段路径（本次踩坑的核心）
    // -----------------------------------------------------------------------

    @Test
    fun `价格读自顶层 avgPrice —— 这是实测位置`() {
        val list = parse(realShape)
        assertNotNull(list)
        assertEquals(2, list!!.size)
        assertEquals(
            "★ 价格在顶层 avgPrice，不在 priceStats 里（此前正是这里写错）",
            4229L,
            list[0].latestPrice,
        )
    }

    @Test
    fun `穿透等级读自 ammoDetail_penetrationLevel —— 用户说的几级弹`() {
        val list = parse(realShape)!!
        assertEquals(6, list[0].penetrationLevel)
        assertEquals("★ 实测存在 7 级弹，筛选选项必须按数据动态生成", 7, list[1].penetrationLevel)
        assertEquals("高", list[0].armorHarmLevel)
    }

    @Test
    fun `稀有度 grade 与穿透等级是两个独立字段`() {
        val list = parse(realShape)!!
        // 第一条：★5 但 6 级弹 —— 两者不同，不能互相顶替
        assertEquals(5, list[0].grade)
        assertEquals(6, list[0].penetrationLevel)
        assertTrue("两者不该相等，否则这个测试没有区分力", list[0].grade != list[0].penetrationLevel)
    }

    @Test
    fun `图片取 prePic 缩略图`() {
        val list = parse(realShape)!!
        assertTrue(
            "列表应优先用 prePic 缩略图省流量",
            list[0].iconUrl!!.endsWith("p_5.56x45mm.png"),
        )
    }

    @Test
    fun `dataPoints 被正确读取`() {
        val list = parse(realShape)!!
        assertEquals(37, list[0].dataPoints)
        assertEquals(0, list[1].dataPoints)
    }

    // -----------------------------------------------------------------------
    // 涨跌幅的红线：样本为 0 时不得显示 0%
    // -----------------------------------------------------------------------

    @Test
    fun `样本为 0 时涨跌幅必须是 null 而不是 0`() {
        val list = parse(realShape)!!
        assertNull(
            "★ 接口在无成交时给 priceChangePercent: 0，" +
                "但显示「↑ 0.0%」等于替数据源编造「价格持平」的结论",
            list[1].changePercent,
        )
    }

    @Test
    fun `有样本且有涨跌时正常显示`() {
        val list = parse(
            """{"data":{"bullets":[
               {"objectID":"1","objectName":"测试弹","avgPrice":1000,
                "priceStats":{"dataPoints":12,"priceChangePercent":4.17}}
            ]}}"""
        )!!
        assertEquals(4.17, list[0].changePercent!!, 0.001)
    }

    @Test
    fun `有样本但涨跌恰好为 0 时 显示 0 是合理的`() {
        // 与上一条的区别：这里是"真有样本且确实持平"，显示 0% 是事实
        val list = parse(
            """{"data":{"bullets":[
               {"objectID":"1","objectName":"测试弹","avgPrice":1000,
                "priceStats":{"dataPoints":20,"priceChangePercent":0}}
            ]}}"""
        )!!
        assertEquals(0.0, list[0].changePercent!!, 0.0001)
    }

    // -----------------------------------------------------------------------
    // 红线：0 / 负数 / 缺失 → null
    // -----------------------------------------------------------------------

    @Test
    fun `价格为 0 读成无数据 —— 红线`() {
        val list = parse(realShape)!!
        assertNull("★ 0 必须读成 null，绝不能变成 0", list[1].latestPrice)
        assertFalse(list[1].hasValidPriceData)
    }

    @Test
    fun `负数价格也读成无数据 —— 红线`() {
        val list = parse(
            """{"data":{"bullets":[
               {"objectID":"1","objectName":"异常弹","avgPrice":-5,
                "priceStats":{"dataPoints":3}}
            ]}}"""
        )!!
        assertNull("负价格不可能是真的", list[0].latestPrice)
    }

    @Test
    fun `grade 为 0 时不显示星级`() {
        val list = parse(
            """{"data":{"bullets":[{"objectID":"1","objectName":"无稀有度","grade":0}]}}"""
        )!!
        assertNull("接口用 0 表示无稀有度，那不是 ★0", list[0].grade)
    }

    // -----------------------------------------------------------------------
    // 兼容性
    // -----------------------------------------------------------------------

    @Test
    fun `顶层 avgPrice 缺失时回退到 priceStats`() {
        val list = parse(
            """{"data":{"bullets":[
               {"objectID":"1","objectName":"测试弹",
                "priceStats":{"latestPrice":999,"dataPoints":5}}
            ]}}"""
        )!!
        assertEquals("对方若把字段挪进嵌套结构，仍能读到", 999L, list[0].latestPrice)
    }

    @Test
    fun `hasValidPriceData 缺失时用有无价格推断`() {
        val withPrice = parse(
            """{"data":{"bullets":[{"objectID":"1","objectName":"有价弹","avgPrice":100}]}}"""
        )!!
        assertTrue(withPrice[0].hasValidPriceData)

        val withoutPrice = parse(
            """{"data":{"bullets":[{"objectID":"2","objectName":"无价弹","priceStats":{}}]}}"""
        )!!
        assertFalse("确实没价格才是 false", withoutPrice[0].hasValidPriceData)
    }

    @Test
    fun `缺少 objectID 或名称的条目被跳过`() {
        val list = parse(
            """{"data":{"bullets":[
               {"objectName":"没有ID"},
               {"objectID":"2"},
               {"objectID":"3","objectName":"   "},
               {"objectID":"4","objectName":"完整条目","avgPrice":50}
            ]}}"""
        )!!
        assertEquals(1, list.size)
        assertEquals("4", list[0].objectId)
    }

    @Test
    fun `缺少 bullets 字段返回 null 表示结构不符 而不是空列表`() {
        assertNull(parseData("""{}"""))
        assertNull(parseData("""{"bullets":"不是数组"}"""))
    }

    @Test
    fun `bullets 是空数组时返回空列表`() {
        val list = parseData("""{"bullets":[]}""")
        assertNotNull(list)
        assertTrue(list!!.isEmpty())
    }

    @Test
    fun `全部条目都无价格时 列表非空但每项都是无数据`() {
        val list = parse(
            """{"data":{"bullets":[
               {"objectID":"1","objectName":"甲","avgPrice":0,"priceStats":{"dataPoints":0}},
               {"objectID":"2","objectName":"乙","priceStats":{}}
            ]}}"""
        )!!
        assertEquals("列表本身不是空的（确实有这两种弹药）", 2, list.size)
        assertTrue("但每一项都没有价格", list.all { it.latestPrice == null })
        assertTrue("且都不该有涨跌幅", list.all { it.changePercent == null })
    }

    // -----------------------------------------------------------------------
    // 口径分组（筛选用）
    // -----------------------------------------------------------------------

    @Test
    fun `口径分组来自 secondClassCN`() {
        val list = parse(realShape)!!
        assertEquals("5.56x45mm", list[0].caliberGroup)
        assertEquals(".338 Lap Mag", list[1].caliberGroup)
    }
}
