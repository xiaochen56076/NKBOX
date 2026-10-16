package com.utilitybox.game.wutheringwaves.kuro

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `cardPoolType` 解析的单元测试（P11 补 36）。
 *
 * ★ 这里每一个**输入值都来自真实接口响应**，不是编的。
 *   之前 787 条记录里 758 条显示成「未知卡池(0)」，
 *   根因就是拿 `optInt` 去读一个中文名称字符串。
 *
 * ★ 为什么抽出来单独测：
 *   这个 bug 的表现是"界面正常、只是卡池名不对"，
 *   靠粘链接手工看很难察觉 —— 必须有测试直接钉住映射关系。
 */
class GachaPoolTypeTest {

    @Test
    fun `角色活动池`() {
        assertEquals(1, poolTypeOf("角色精准调谐"))
    }

    @Test
    fun `武器活动池`() {
        assertEquals(2, poolTypeOf("武器精准调谐"))
    }

    @Test
    fun `角色常驻池_注意是全角括号`() {
        // ★ 实测值用的是全角「（）」。写成半角就匹配不上，
        //   而失败表现只是"卡池名变成未知" —— 很难发现。
        assertEquals(3, poolTypeOf("角色调谐（常驻池）"))
    }

    @Test
    fun `武器常驻池`() {
        assertEquals(4, poolTypeOf("武器调谐（常驻池）"))
    }

    @Test
    fun `半角括号不该被当成常驻池`() {
        // 反向用例：确认全角是**必须**的，而不是两种都能过。
        // 如果哪天接口改成半角，这个测试会失败 —— 那是**提醒**，不是噪音。
        assertEquals(0, poolTypeOf("角色调谐(常驻池)"))
    }

    @Test
    fun `纯数字串照常解析_池6和池10就是这种`() {
        assertEquals(6, poolTypeOf("6"))
        assertEquals(10, poolTypeOf("10"))
    }

    @Test
    fun `认不出来的一律返回0而不是猜`() {
        // ★ 宁可显示「未知卡池(0)」也不猜一个池 ——
        //   猜错会让用户以为自己的记录跑到别的池子里去了。
        assertEquals(0, poolTypeOf(""))
        assertEquals(0, poolTypeOf("未来某个新池的名字"))
        assertEquals(0, poolTypeOf("abc"))
    }

    @Test
    fun `返回0时标签如实标注而不是冒充某个池`() {
        assertEquals("未知卡池(0)", WuwaGachaApi.poolLabel(0))
    }
}
