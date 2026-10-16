package com.utilitybox.game.wutheringwaves.kuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HTML 表格解析器测试（P11 补 46）。
 *
 * ★ 这里的 HTML **全部是从接口真实响应里剪下来的**，不是我手写的理想样例。
 *   手写样例只会测出"我以为是那样"，真实 HTML 里的 colspan、彩色 span、
 *   48×48 图标、`&middot;` 实体才是难点。
 */
class WikiTableTest {

    /** 真实片段：图鉴「心」的「其他信息」表（2 列，首列带底色） */
    private val otherInfo = """
        <table style="border-collapse: collapse; width: 99.9802%;" border="1"><colgroup><col style="width: 34.9042%;"><col style="width: 65.0836%;"></colgroup>
        <tbody>
        <tr style="height: 27.9811px;">
        <td style="background-color: rgb(247, 247, 247); text-align: center; padding: 8px;"><span style="color: rgb(91, 84, 74); font-size: 14px;"><strong>身份</strong></span></td>
        <td style="height: 27.9811px; padding: 8px;">岁主「心」，梦州文明的引导者</td>
        </tr>
        <tr>
        <td style="background-color: rgb(247, 247, 247); text-align: center; padding: 8px;"><span style="color: rgb(91, 84, 74);"><strong>所属</strong></span></td>
        <td style="padding: 8px;">梦州边庭</td>
        </tr>
        <tr>
        <td style="background-color: rgb(247, 247, 247); text-align: center; padding: 8px;"><strong>特殊料理</strong></td>
        <td style="padding: 8px;"><a href="https://wiki.kurobbs.com/mc/item/155" target="_blank">盈青阙</a></td>
        </tr>
        </tbody></table>
    """.trimIndent()

    /** 真实片段：角色统计表（含 colspan=2） */
    private val roleStats = """
        <table border="1"><tbody>
        <tr><td style="text-align: center; background-color: rgb(247, 247, 247);">无突破</td>
        <td style="text-align: center;" colspan="2">无突破</td>
        <td style="text-align: center; background-color: rgb(247, 247, 247);">攻击提升</td>
        <td style="text-align: center;">0%（12%）</td></tr>
        <tr><td style="text-align: center; background-color: rgb(247, 247, 247);">基础生命</td>
        <td style="text-align: center;" colspan="2">824</td>
        <td style="text-align: center; background-color: rgb(247, 247, 247);">暴击伤害</td>
        <td style="text-align: center;">150%</td></tr>
        </tbody></table>
    """.trimIndent()

    /** 真实片段：共鸣链表（首列 = 图标 + 名称，次列 = 含金色高亮的效果描述） */
    private val resonanceChain = """
        <table border="1"><tbody>
        <tr><td style="text-align: center; background-color: rgb(247, 247, 247);"><span><strong>名称</strong></span></td>
        <td style="text-align: center; background-color: rgb(247, 247, 247);"><span><strong>效果</strong></span></td></tr>
        <tr><td style="text-align: center; padding: 8px;">
        <p><img src="https://prod-alicdn-community.kurobbs.com/forum/abc20260930.png" alt="" width="48" height="48"></p>
        <p><strong>授以舟楫渡潮生</strong></p></td>
        <td style="padding: 8px;"><p>施放<span style="color: rgb(241, 196, 15);"><strong>共鸣解放&middot;转相</strong></span>时，心获得2层<span style="color: rgb(241, 196, 15);"><strong>敛华障</strong></span>。</p></td></tr>
        </tbody></table>
    """.trimIndent()

    // ---- 基础 ----

    @Test
    fun `非表格内容退化为纯文本_而不是丢掉`() {
        // ★ 最重要的一条兜底：解析不出来也必须把内容留下
        val b = WikiTable.parse("<p>这是一段普通说明</p>")
        assertTrue(b is WikiTable.Block.Plain)
        assertEquals("这是一段普通说明", (b as WikiTable.Block.Plain).text)
    }

    @Test
    fun `空内容不崩`() {
        assertEquals("", (WikiTable.parse("") as WikiTable.Block.Plain).text)
    }

    // ---- 其他信息 ----

    @Test
    fun `其他信息表解析成 3 行 2 列`() {
        val t = WikiTable.parse(otherInfo) as WikiTable.Block.Table
        assertEquals(3, t.rows.size)
        assertEquals(listOf(2, 2, 2), t.rows.map { it.cells.size })
    }

    @Test
    fun `标签列与值列都能读出文本`() {
        val t = WikiTable.parse(otherInfo) as WikiTable.Block.Table
        assertEquals("身份", t.rows[0].cells[0].text)
        assertEquals("岁主「心」，梦州文明的引导者", t.rows[0].cells[1].text)
        assertEquals("所属", t.rows[1].cells[0].text)
        assertEquals("梦州边庭", t.rows[1].cells[1].text)
    }

    @Test
    fun `带背景色的一格被标为标签列`() {
        // ★ 这不是装饰信息：它决定排版是「标签: 值」还是「整行文本」
        val t = WikiTable.parse(otherInfo) as WikiTable.Block.Table
        assertTrue("首列有 background-color，应判为标签列", t.rows[0].cells[0].shaded)
        assertTrue("值列没有底色", !t.rows[0].cells[1].shaded)
    }

    @Test
    fun `链接只取文字不要 URL`() {
        val t = WikiTable.parse(otherInfo) as WikiTable.Block.Table
        assertEquals("盈青阙", t.rows[2].cells[1].text)
        assertTrue(!t.rows[2].cells[1].text.contains("http"))
    }

    // ---- 角色统计（colspan）----

    @Test
    fun `colspan 被正确读出_注意 td 个数与列数是两回事`() {
        val t = WikiTable.parse(roleStats) as WikiTable.Block.Table
        // ★ 真实那行是 4 个 <td>，但横跨 5 列（1+2+1+1）。
        //   我第一版测试断言成 5 个 td —— **测试写错了**，不是解析器错了。
        assertEquals(4, t.rows[0].cells.size)
        assertEquals(listOf(1, 2, 1, 1), t.rows[0].cells.map { it.span })
        assertEquals("列数才是 5", 5, t.rows[0].columns)
        assertEquals(5, t.rows[1].columns)
    }

    @Test
    fun `跨列表格的文本完整`() {
        val t = WikiTable.parse(roleStats) as WikiTable.Block.Table
        assertEquals("无突破", t.rows[0].cells[1].text)
        assertEquals("824", t.rows[1].cells[1].text)
        // 「150%」是第 4 个 td（索引 3），不是索引 4 —— 同上
        assertEquals("150%", t.rows[1].cells[3].text)
        assertEquals("暴击伤害", t.rows[1].cells[2].text)
        assertEquals("基础生命", t.rows[1].cells[0].text)
    }

    // ---- 共鸣链 ----

    @Test
    fun `共鸣链表头是 名称 与 效果`() {
        val t = WikiTable.parse(resonanceChain) as WikiTable.Block.Table
        assertEquals("名称", t.rows[0].cells[0].text)
        assertEquals("效果", t.rows[0].cells[1].text)
    }

    @Test
    fun `命座图标被解析成图片而不是丢掉`() {
        val t = WikiTable.parse(resonanceChain) as WikiTable.Block.Table
        val imgs = t.rows[1].cells[0].images
        assertEquals(1, imgs.size)
        assertTrue(imgs[0].endsWith(".png"))
    }

    @Test
    fun `命座名称从图标格里读出来`() {
        // 这一格同时有 <img> 和 <p><strong>名称</strong></p>
        val t = WikiTable.parse(resonanceChain) as WikiTable.Block.Table
        assertEquals("授以舟楫渡潮生", t.rows[1].cells[0].text)
    }

    @Test
    fun `金色高亮的技能名被单独拆成一个 span`() {
        // ★ 这是"语义"不是装饰：去掉之后整段效果描述就看不出重点在哪
        val t = WikiTable.parse(resonanceChain) as WikiTable.Block.Table
        val parts = t.rows[1].cells[1].parts
        val colored = parts.filterIsInstance<WikiTable.Span.Text>().filter { it.color != null }
        assertEquals(2, colored.size)
        assertEquals("共鸣解放·转相", colored[0].text)
        assertEquals("敛华障", colored[1].text)
        assertTrue("高亮片段应带加粗", colored[0].bold)
    }

    @Test
    fun `效果描述里没有残留的 HTML 标签`() {
        val t = WikiTable.parse(resonanceChain) as WikiTable.Block.Table
        val txt = t.rows[1].cells[1].text
        assertTrue("不应含尖括号: $txt", !txt.contains("<"))
        assertTrue("实体应已解码: $txt", !txt.contains("&middot;"))
        assertTrue("内容要在", txt.contains("施放"))
    }

    // ---- 实体解码 ----

    @Test
    fun `HTML 实体解码`() {
        assertEquals("a·b", WikiTable.decodeEntities("a&middot;b"))
        assertEquals("a b", WikiTable.decodeEntities("a&nbsp;b"))
        assertEquals("a&b", WikiTable.decodeEntities("a&amp;b"))
        assertEquals("a<b>c", WikiTable.decodeEntities("a&lt;b&gt;c"))
    }

    @Test
    fun `amp 最后解码_避免二次解码`() {
        // ★ 顺序错了 "&amp;lt;" 会被解成 "<" —— 那是把一个转义序列解两次
        assertEquals("&lt;", WikiTable.decodeEntities("&amp;lt;"))
    }

    @Test
    fun `去标签后压缩空白`() {
        assertEquals("a b", WikiTable.stripTags("<p>a</p>\n\n  <p>b</p>"))
    }
}
