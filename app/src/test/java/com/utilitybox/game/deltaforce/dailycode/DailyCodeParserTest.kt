package com.utilitybox.game.deltaforce.dailycode

import com.utilitybox.core.json.Json
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.domain.model.DataSourceRef
import com.utilitybox.domain.model.SourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 今日密码解析与四态判定测试。
 *
 * ★ 这是红线最集中的模块：
 *   - 过期数据**绝不**能以今日数据的样式呈现
 *   - 没有数据时不得编造
 *
 * 用例的 JSON 直接取自 P01 的真实响应结构。
 */
class DailyCodeParserTest {

    private val source = DataSourceRef("test", "测试源", SourceState.VERIFIED)
    private val today = LocalDate.of(2026, 10, 14)

    private fun json(s: String): Json = Json.parse(s)!!

    /** P01 实测到的真实响应 data 部分 */
    private val realData = json(
        """
        {"list":[
          {"mapID":1,"mapName":"零号大坝","secret":"0144"},
          {"mapID":2,"mapName":"长弓溪谷","secret":"8288"},
          {"mapID":3,"mapName":"巴克什","secret":"4400"},
          {"mapID":4,"mapName":"航天基地","secret":"0201"},
          {"mapID":5,"mapName":"潮汐监狱","secret":"2643"},
          {"mapID":6,"mapName":"AZ3","secret":"2520"}
        ]}
        """.trimIndent()
    )

    @Test
    fun `解析真实响应得到 6 张地图`() {
        val codes = DailyCodeParser.parse(realData)!!
        assertEquals(6, codes.size)
        assertEquals("零号大坝", codes[0].mapName)
        assertEquals("0144", codes[0].code)
        assertEquals("2520", codes[5].code)
    }

    @Test
    fun `数字型 mapID 能转成字符串`() {
        val codes = DailyCodeParser.parse(realData)!!
        // 接口里 mapID 是数字 1，模型里是字符串
        assertEquals("1", codes[0].mapId)
        assertEquals("6", codes[5].mapId)
    }

    @Test
    fun `兼容 password 字段名以防对方改版`() {
        val data = json("""{"list":[{"mapID":9,"mapName":"某图","password":"1234"}]}""")
        assertEquals("1234", DailyCodeParser.parse(data)!![0].code)
    }

    @Test
    fun `缺少 list 返回 null 而不是空列表`() {
        // 区分「结构不对」与「确实没有密码」，调用方才能给出正确文案
        assertNull(DailyCodeParser.parse(json("""{"other":1}""")))
    }

    @Test
    fun `空 list 返回空列表而不是 null`() {
        assertTrue(DailyCodeParser.parse(json("""{"list":[]}"""))!!.isEmpty())
    }

    @Test
    fun `抓取日为今天判定为 Today`() {
        val state = DailyCodeParser.classify(
            codes = DailyCodeParser.parse(realData),
            fetchedAt = today,
            today = today,
            source = source,
            updatedAtLabel = "08:12",
        )
        assertTrue(state is DailyCodeState.Today)
    }

    @Test
    fun `抓取日为昨天判定为 Stale 且携带原始日期`() {
        val yesterday = today.minusDays(1)
        val state = DailyCodeParser.classify(
            codes = DailyCodeParser.parse(realData),
            fetchedAt = yesterday,
            today = today,
            source = source,
            updatedAtLabel = "08:12",
        )
        assertTrue("过期数据必须走 Stale 分支", state is DailyCodeState.Stale)
        assertEquals(
            "必须携带原始抓取日期，UI 才能如实展示",
            yesterday,
            (state as DailyCodeState.Stale).fetchedDate,
        )
    }

    @Test
    fun `空列表判定为 NoDataForToday`() {
        val state = DailyCodeParser.classify(
            codes = emptyList(),
            fetchedAt = today,
            today = today,
            source = source,
            updatedAtLabel = "08:12",
        )
        assertEquals(DailyCodeState.NoDataForToday, state)
    }

    @Test
    fun `null 数据判定为 NoDataForToday`() {
        val state = DailyCodeParser.classify(
            codes = null,
            fetchedAt = today,
            today = today,
            source = source,
            updatedAtLabel = "08:12",
        )
        assertEquals(DailyCodeState.NoDataForToday, state)
    }

    @Test
    fun `失败状态保留原始错误类型`() {
        assertEquals(
            DailyCodeState.Failed(AppError.NoNetwork),
            DailyCodeParser.failed(AppError.NoNetwork),
        )
    }

    @Test
    fun `Stale 状态的日期与 Today 不同 —— 这是红线断言`() {
        val stale = DailyCodeParser.classify(
            codes = DailyCodeParser.parse(realData),
            fetchedAt = today.minusDays(3),
            today = today,
            source = source,
            updatedAtLabel = "",
        ) as DailyCodeState.Stale
        // 若将来有人把 Stale 改成携带 today，这条会失败 —— 正是想要的效果
        assertTrue(
            "过期数据的日期绝不能等于今天，否则就变成了冒充今日",
            stale.fetchedDate != today,
        )
    }
}
