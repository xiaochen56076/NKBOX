package com.utilitybox.game.wutheringwaves.kuro

/**
 * 图鉴正文里的 **HTML 表格** → 结构化数据（P11 补 46）。
 *
 * ### 为什么要写这个
 *
 * 用户原话：
 * > 「这种界面简化下吧…**使用 UI 库的来展示这些数据**吧，直接套用这个 wiki 不太顺眼」
 *
 * 之前详情页是把整篇 HTML 丢进 WebView 渲染 —— 样式是库街区的，
 * 和我们 App 的设计系统完全两张皮，而且技能/档案/语音这些**用户明确不要的内容**也在里面。
 *
 * 改成原生 Compose 的前提是：**先把表格解析成行×列**。
 *
 * ### 原始 HTML 长什么样（接口实测，不是推测）
 *
 * ```html
 * <table style="border-collapse: collapse; …" border="1">
 *   <colgroup><col style="width: 34.9%;"><col style="width: 65.0%;"></colgroup>
 *   <tbody>
 *     <tr style="height: 27.98px;">
 *       <td colspan="2" style="background-color: rgb(247,247,247); text-align:center;">
 *         <span style="color: rgb(91,84,74); font-size:14px;"><strong>身份</strong></span>
 *       </td>
 *       <td style="padding: 8px;">岁主「心」，梦州文明的引导者</td>
 *     </tr>
 *   </tbody>
 * </table>
 * ```
 *
 * 几个**必须处理的真实情况**：
 * 1. `<td>` 带 `colspan` —— 角色统计表大量使用（`无突破 | 无突破(colspan=2) | …`）
 * 2. 单元格里有 `<img>` —— 共鸣链第一列是 48×48 的命座图标
 * 3. 单元格里有彩色 `<span style="color: rgb(241,196,15)">` —— 技能名的高亮，丢了就没重点
 * 4. HTML 实体：`&middot;` `&nbsp;` `&amp;` 等
 * 5. `style` 里带 `background-color: rgb(247,247,247)` —— 那是**表头底色**，
 *    可以用来判断"这一格是标签还是值"
 *
 * ### ★ 解析失败时的原则：**原样保留文本，绝不丢数据**
 *
 * 表结构不认识（嵌套表、rowspan）时退化成"每行一句话"，
 * 而不是丢掉整段。**宁可难看，不能少内容** —— 用户是来看资料的。
 */
object WikiTable {

    /** 一个单元格里的一段内容 */
    sealed interface Span {
        /** 普通文本。[bold] 来自 `<strong>`，[color] 来自 `style` 里的 rgb |
         *  高亮色（如技能名），没有就是 null */
        data class Text(val text: String, val bold: Boolean = false, val color: Int? = null) : Span

        /** 单元格里的图片（命座图标等） */
        data class Image(val url: String) : Span
    }

    data class Cell(
        /** `colspan`，默认 1 */
        val span: Int,
        val parts: List<Span>,
        /** 该格是否带底色（大概率是标签列）—— 用来决定排版是"标签: 值"还是"整行文本" */
        val shaded: Boolean,
    ) {
        /** 该格的纯文本（多段拼接），用于兜底与调试 */
        val text: String get() = parts.filterIsInstance<Span.Text>().joinToString("") { it.text }.trim()
        val images: List<String> get() = parts.filterIsInstance<Span.Image>().map { it.url }
    }

    data class Row(val cells: List<Cell>) {
        /**
         * 这一行横跨的**网格列数**（各格 colspan 之和）。
         *
         * ★ 别把它和 [cells]`.size` 混为一谈 —— 那是 **`<td>` 元素个数**。
         *   实测角色统计表里 `无突破 | 无突破(colspan=2) | 攻击提升 | 0%（12%）`
         *   是 **4 个 td、5 列**。我写测试时就踩过这个坑（断言成 5 个 td）。
         */
        val columns: Int get() = cells.sumOf { it.span }
    }

    /** 解析结果：可能是表格，也可能只是普通文本 */
    sealed interface Block {
        data class Table(val rows: List<Row>) : Block
        /** 不是表格（纯文本/段落），退化为文本块 */
        data class Plain(val text: String) : Block
    }

    /**
     * 把一段 HTML 解析成块。
     *
     * ★ 只解析**第一张表**：实测每个 component 的 `content` 就是一张表，
     *   多表的情况（`getPage` 的富文本）不在这里处理。
     */
    fun parse(html: String): Block {
        val tableStart = html.indexOf("<table", ignoreCase = true)
        if (tableStart < 0) return Block.Plain(stripTags(html))
        val tableEnd = html.indexOf("</table>", tableStart, ignoreCase = true)
        val body = if (tableEnd < 0) html.substring(tableStart) else html.substring(tableStart, tableEnd)

        val rows = ArrayList<Row>()
        for (tr in splitTag(body, "tr")) {
            val cells = ArrayList<Cell>()
            // ★ th 与 td 同等对待：实测有些表用 th 做表头，丢掉就少一列
            for (td in splitAnyTag(tr, listOf("td", "th"))) {
                cells += Cell(
                    span = readColspan(td),
                    parts = parseSpans(td),
                    shaded = td.contains("background-color", ignoreCase = true),
                )
            }
            if (cells.isNotEmpty()) rows += Row(cells)
        }
        return if (rows.isEmpty()) Block.Plain(stripTags(body)) else Block.Table(rows)
    }

    // ------------------------------------------------------------------
    // 解析细节
    // ------------------------------------------------------------------

    /** 取出一层同名标签的内容（不处理嵌套同名，够用且不会误吞） */
    private fun splitTag(html: String, tag: String): List<String> {
        val open = "<$tag"
        val close = "</$tag>"
        val out = ArrayList<String>()
        var i = 0
        while (true) {
            val s = html.indexOf(open, i, ignoreCase = true)
            if (s < 0) break
            val e = html.indexOf(close, s, ignoreCase = true)
            if (e < 0) break
            out += html.substring(s, e)
            i = e + close.length
        }
        return out
    }

    private fun splitAnyTag(html: String, tags: List<String>): List<String> {
        val all = tags.flatMap { splitTag(html, it) }
        // 按在原串里的位置排序，保持列顺序
        return all.sortedBy { html.indexOf(it) }
    }

    private fun readColspan(td: String): Int {
        val m = Regex("""colspan\s*=\s*"?(\d+)""", RegexOption.IGNORE_CASE).find(td) ?: return 1
        return m.groupValues[1].toIntOrNull()?.coerceIn(1, 20) ?: 1
    }

    /**
     * 把一个单元格拆成若干 span。
     *
     * ★ 保留 `<strong>`（加粗）与彩色 `<span style="color: rgb(...)">`（技能名高亮）——
     *   这两样是**语义**，不是装饰：共鸣链的效果描述里，
     *   技能名用金色标出来，去掉之后整段话就看不出重点了。
     */
    private fun parseSpans(cell: String): List<Span> {
        // 先取 td 标签之后的内容（去掉 td 自身的 style）
        val body = cell.substringAfter('>', "")
        val out = ArrayList<Span>()

        // 1) 图片
        Regex("""<img[^>]*src\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE).findAll(body).forEach { m ->
            out += Span.Image(m.groupValues[1])
        }

        // 2) 带颜色的片段（技能名高亮）
        Regex(
            """<span[^>]*color\s*:\s*rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)[^>]*>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(body).forEach { m ->
            val r = m.groupValues[1].toIntOrNull() ?: return@forEach
            val g = m.groupValues[2].toIntOrNull() ?: return@forEach
            val b = m.groupValues[3].toIntOrNull() ?: return@forEach
            val txt = stripTags(m.groupValues[4]).trim()
            if (txt.isNotEmpty()) out += Span.Text(txt, bold = true, color = argb(r, g, b))
        }

        // 3) 其余文本（去掉已经处理过的图片与彩色 span，避免重复）
        val rest = body
            .replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(
                Regex(
                    """<span[^>]*color\s*:\s*rgb\([^)]*\)[^>]*>.*?</span>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                "",
            )
        val plain = stripTags(rest).trim()
        if (plain.isNotEmpty()) out += Span.Text(plain, bold = false)

        return out
    }

    /** 去标签 + 解实体 + 压空白。**这是唯一的文本出口**，所以实体解码只需在这里做一次 */
    internal fun stripTags(html: String): String = decodeEntities(
        html.replace(Regex("<[^>]*>"), "")
    ).replace(Regex("\\s+"), " ").trim()

    internal fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&middot;", "·")
        .replace("&hellip;", "…")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        // ★ &amp; 必须放最后：否则 "&amp;lt;" 会被解成 "<"
        .replace("&amp;", "&")

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
