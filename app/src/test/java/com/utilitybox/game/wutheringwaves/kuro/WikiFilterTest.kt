package com.utilitybox.game.wutheringwaves.kuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图鉴筛选逻辑的单元测试。
 *
 * ★ 为什么这些测试必须有：
 *   筛选**错了界面完全正常** —— 只是结果多了或少了，用户不会知道。
 *   而最容易写错的一处是语义：把"同一分组内或 / 不同分组间与"
 *   实现成"一律或"，看起来代码更简单，行为却是错的。
 */
class WikiFilterTest {

    // ---- 测试数据：结构照抄实测响应 ----
    // 「心」   五星(210) 导电(214) 音感仪(532)
    // 「千咲」 五星(210) 湮灭(218) 长刃(528)
    // 「秋水」 四星(211) 导电(214) 佩枪(531)
    private val xin = WikiEntry("1", "心", 5, null, "", listOf("210", "214", "532"))
    private val qianxiao = WikiEntry("2", "千咲", 5, null, "", listOf("210", "218", "528"))
    private val qiushui = WikiEntry("3", "秋水", 4, null, "", listOf("211", "214", "531"))
    private val all = listOf(xin, qianxiao, qiushui)

    private val gStar = WikiTagGroup(209, "稀有度", listOf(WikiTag("210", "五星"), WikiTag("211", "四星")))
    private val gElem = WikiTagGroup(
        212, "属性",
        listOf(WikiTag("213", "气动"), WikiTag("214", "导电"), WikiTag("218", "湮灭")),
    )

    private fun names(list: List<WikiEntry>) = list.map { it.name }

    @Test
    fun `没有选中任何条件时返回全部`() {
        assertEquals(all, filterWikiEntries(all, emptyMap()))
    }

    @Test
    fun `空集合等同于未筛选`() {
        // 分组键存在但集合为空 —— 必须和"没选"一样，不能把结果清空
        assertEquals(all, filterWikiEntries(all, mapOf(209 to emptySet())))
    }

    @Test
    fun `同一分组内多选是或`() {
        // 五星 ∪ 四星 = 全部三条
        val r = filterWikiEntries(all, mapOf(209 to setOf("210", "211")))
        assertEquals(listOf("心", "千咲", "秋水"), names(r))
    }

    @Test
    fun `不同分组之间是与`() {
        // ★ 本测试钉住最容易写错的一条：
        //   五星 ∩ 导电 = 只有「心」（「千咲」是五星但湮灭；「秋水」导电但四星）
        //   若实现成"全或"，会错误地返回三条
        val r = filterWikiEntries(all, mapOf(209 to setOf("210"), 212 to setOf("214")))
        assertEquals(listOf("心"), names(r))
    }

    @Test
    fun `只选一个分组时按该分组或`() {
        val r = filterWikiEntries(all, mapOf(212 to setOf("218")))
        assertEquals(listOf("千咲"), names(r))
    }

    @Test
    fun `全部条件都不匹配时返回空`() {
        // 四星 ∩ 湮灭 —— 没有这样的角色
        val r = filterWikiEntries(all, mapOf(209 to setOf("211"), 212 to setOf("218")))
        assertTrue(r.isEmpty())
    }

    @Test
    fun `未参与筛选的分组不影响结果`() {
        // 只筛属性，稀有度分组未选 → 不应把五星过滤掉
        val r = filterWikiEntries(all, mapOf(212 to setOf("214"), 209 to emptySet()))
        assertEquals(listOf("心", "秋水"), names(r))
    }

    @Test
    fun `取分组下的标签名`() {
        assertEquals("导电", wikiTagNameOf(xin, gElem))
        assertEquals("五星", wikiTagNameOf(xin, gStar))
    }

    @Test
    fun `词条在该分组下没有标签时返回 null 而不是默认值`() {
        // ★ 关键：调用方拿到 null 必须显示"无"，绝不能编一个默认属性 ——
        //   那等于告诉用户一个错误的事实
        val noTag = WikiEntry("9", "材料", 0, null, "", emptyList())
        assertNull(wikiTagNameOf(noTag, gElem))
    }

    @Test
    fun `按分组顺序取出全部标签名`() {
        val r = wikiTagNamesOf(xin, listOf(gStar, gElem))
        assertEquals(listOf("稀有度" to "五星", "属性" to "导电"), r)
    }

    @Test
    fun `已选标签计数用于筛选按钮角标`() {
        assertEquals(0, selectedTagCount(emptyMap()))
        assertEquals(3, selectedTagCount(mapOf(209 to setOf("210", "211"), 212 to setOf("214"))))
    }
}
