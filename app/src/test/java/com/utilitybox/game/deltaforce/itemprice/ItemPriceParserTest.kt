package com.utilitybox.game.deltaforce.itemprice

import com.utilitybox.core.json.Json
import com.utilitybox.domain.model.PriceTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 物品物价解析测试。
 *
 * ★ 核心红线：**无成交数据不得被当成「价格 0」**。
 *   实测接口在无成交时返回 `avgPrice: 0` 且 `hasValidPriceData: false`，
 *   解析层必须产出 null，让 UI 能显示「暂无成交数据」。
 */
class ItemPriceParserTest {

    private fun json(s: String): Json = Json.parse(s)!!

    @Test
    fun `解析搜索结果（真实响应结构）`() {
        val data = json(
            """
            {"limit":100,"page":1,"total":4,"list":[
              {"objectID":"13020000524","objectName":"M250钛金长枪管","shortName":"M250",
               "grade":5,"avgPrice":49258,"secondClassCN":"枪械配件"}
            ]}
            """.trimIndent()
        )
        val items = ItemPriceParser.parseSearch(data)!!
        assertEquals(1, items.size)
        assertEquals("13020000524", items[0].objectId)
        assertEquals("M250钛金长枪管", items[0].name)
        assertEquals(5, items[0].grade)
        assertEquals(49258L, items[0].avgPrice)
        assertEquals("枪械配件", items[0].category)
    }

    @Test
    fun `数字型 objectID 能转成字符串`() {
        // 实测当前价响应里 objectID 是数字
        val data = json("""{"list":[{"objectID":13020000524,"objectName":"A","avgPrice":1}]}""")
        assertEquals("13020000524", ItemPriceParser.parseSearch(data)!![0].objectId)
    }

    @Test
    fun `价格为 0 视为无成交数据而不是 0 元 —— 红线`() {
        val data = json("""{"list":[{"objectID":"1","objectName":"某物","avgPrice":0}]}""")
        assertNull(
            "0 必须被解读为『无数据』，绝不能是『价格 0』",
            ItemPriceParser.parseSearch(data)!![0].avgPrice,
        )
    }

    @Test
    fun `价格缺失或为 null 同样视为无数据`() {
        for (s in listOf(
            """{"list":[{"objectID":"1","objectName":"A"}]}""",
            """{"list":[{"objectID":"1","objectName":"A","avgPrice":null}]}""",
            """{"list":[{"objectID":"1","objectName":"A","avgPrice":"abc"}]}""",
            """{"list":[{"objectID":"1","objectName":"A","avgPrice":-5}]}""",
        )) {
            assertNull("$s 应解析为无数据", ItemPriceParser.parseSearch(json(s))!![0].avgPrice)
        }
    }

    @Test
    fun `当前价优先取 dataMap`() {
        val data = json(
            """{"objectIDs":[13020000524],"dataMap":{"13020000524":49444},
                "items":[{"objectID":13020000524,"avgPrice":1}],"source":"ams"}"""
        )
        assertEquals(49444L, ItemPriceParser.parseLatestPrice(data, "13020000524"))
        assertEquals("ams", ItemPriceParser.parseSource(data))
    }

    @Test
    fun `当前价回退到 items 数组`() {
        val data = json("""{"items":[{"objectID":"42","avgPrice":1234}]}""")
        assertEquals(1234L, ItemPriceParser.parseLatestPrice(data, "42"))
    }

    @Test
    fun `当前价查不到返回 null 而不是 0`() {
        val data = json("""{"items":[{"objectID":"other","avgPrice":99}]}""")
        assertNull(ItemPriceParser.parseLatestPrice(data, "missing"))
    }

    @Test
    fun `dataMap 里价格为 0 时视为无数据`() {
        val data = json("""{"dataMap":{"7":0}}""")
        assertNull(ItemPriceParser.parseLatestPrice(data, "7"))
    }

    @Test
    fun `解析历史价格保留真实样本数`() {
        val data = json(
            """
            {"granularity":"day","source":"ams","history":[
              {"avgPrice":49268,"count":371,"date":"2026-10-08"},
              {"avgPrice":49288,"count":130,"date":"2026-10-14"}
            ]}
            """.trimIndent()
        )
        val points = ItemPriceParser.parseHistory(data)!!
        assertEquals(2, points.size)
        assertEquals(371, points[0].sampleCount)
        assertEquals(49268L, points[0].avgPrice)
    }

    @Test
    fun `样本数为 0 的日期视为无成交，即使接口给了价格 —— 红线`() {
        val data = json("""{"history":[{"avgPrice":12345,"count":0,"date":"2026-10-12"}]}""")
        val p = ItemPriceParser.parseHistory(data)!![0]
        assertEquals(0, p.sampleCount)
        assertNull("count=0 说明当日无成交，价格不可信", p.avgPrice)
    }

    @Test
    fun `日期格式非法的点被跳过而不是猜一个日期`() {
        val data = json(
            """{"history":[{"avgPrice":1,"count":1,"date":"not-a-date"},
                           {"avgPrice":2,"count":1,"date":"2026-10-09"}]}"""
        )
        val points = ItemPriceParser.parseHistory(data)!!
        assertEquals(1, points.size)
        assertEquals("2026-10-09", points[0].date.toString())
    }

    @Test
    fun `带时间戳的日期只取日期部分`() {
        // 兼容对方将来改成 ISO 时间戳的情况
        val data = json("""{"history":[{"avgPrice":9,"count":3,"date":"2026-10-09T15:54:40.384Z"}]}""")
        assertEquals("2026-10-09", ItemPriceParser.parseHistory(data)!![0].date.toString())
    }

    @Test
    fun `有效点不足两个时趋势为 UNKNOWN 而不是持平`() {
        val data = json("""{"history":[{"avgPrice":100,"count":5,"date":"2026-10-09"}]}""")
        assertEquals(PriceTrend.UNKNOWN, ItemPriceParser.trendOf(ItemPriceParser.parseHistory(data)!!))
    }

    @Test
    fun `趋势由首尾有效点计算，中间缺失点不影响`() {
        val data = json(
            """
            {"history":[
              {"avgPrice":100,"count":5,"date":"2026-10-08"},
              {"avgPrice":999,"count":0,"date":"2026-10-09"},
              {"avgPrice":80,"count":5,"date":"2026-10-10"}
            ]}
            """.trimIndent()
        )
        val points = ItemPriceParser.parseHistory(data)!!
        assertEquals(PriceTrend.DOWN, ItemPriceParser.trendOf(points))
        assertEquals("缺失点必须保留在列表里（图表要断开）", 3, points.size)
        assertNull(points[1].avgPrice)
    }

    @Test
    fun `全部无成交时趋势仍是 UNKNOWN`() {
        val data = json(
            """{"history":[{"avgPrice":0,"count":0,"date":"2026-10-08"},
                           {"avgPrice":0,"count":0,"date":"2026-10-09"}]}"""
        )
        assertEquals(PriceTrend.UNKNOWN, ItemPriceParser.trendOf(ItemPriceParser.parseHistory(data)!!))
    }

    @Test
    fun `缺少 history 返回 null`() {
        assertNull(ItemPriceParser.parseHistory(json("""{"granularity":"day"}""")))
    }

    @Test
    fun `搜索缺少 list 返回 null 而不是空列表`() {
        assertNull(ItemPriceParser.parseSearch(json("""{"total":0}""")))
        assertTrue(ItemPriceParser.parseSearch(json("""{"list":[]}"""))!!.isEmpty())
    }
}
