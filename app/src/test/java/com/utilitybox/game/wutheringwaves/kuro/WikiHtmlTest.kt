package com.utilitybox.game.wutheringwaves.kuro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图鉴 HTML 组装。
 *
 * ★ 这里有两类会出事的地方：
 *   1. **转义** —— 拼出来的 HTML 要交给 WebView，我们自己插入的文本（词条名、图注、台词）
 *      必须转义。漏掉一处就是一个注入点。
 *   2. **正文必须原样保留** —— 词条正文本来就是 HTML（表格、行内样式），
 *      如果被"顺手也转义了"，页面会变成一堆可见的尖括号，图鉴直接废掉。
 *   两条要求方向相反，所以必须都有测试钉住。
 */
class WikiHtmlTest {

    private fun detail(
        name: String = "心",
        modules: List<WikiModule> = emptyList(),
    ) = WikiEntryDetail(
        name = name,
        orgFullName = "角色组 > 共鸣者前瞻",
        browseCount = 216354,
        lastUpdateTime = "2026-10-09",
        modules = modules,
    )

    @Test
    fun `★ 我们自己插入的文本会被转义`() {
        // 词条名里的尖括号/引号必须被转义，否则就是一个注入点
        val html = WikiHtml.build(detail(name = "<script>alert(1)</script>"))
        assertFalse("脚本标签不能原样出现在文档里", html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        // 但标题外层标签还在，说明是"内容被转义"而不是"整段丢了"
        assertTrue(html.contains("<h1>"))
    }

    @Test
    fun `转义函数覆盖五个字符`() {
        assertEquals("&amp;", WikiHtml.escape("&"))
        assertEquals("&lt;", WikiHtml.escape("<"))
        assertEquals("&gt;", WikiHtml.escape(">"))
        assertEquals("&quot;", WikiHtml.escape("\""))
        assertEquals("a&amp;b&lt;c", WikiHtml.escape("a&b<c"))
    }

    @Test
    fun `★ 词条正文的 HTML 原样保留 不会被转义`() {
        // 正文是表格 + 行内样式。如果这里被转义，图鉴页会变成一堆可见的尖括号。
        val rawTable = "<table><tbody><tr><td style=\"color: rgb(187, 159, 94);\">万相生华</td></tr></tbody></table>"
        val html = WikiHtml.build(
            detail(modules = listOf(WikiModule("基础资料", listOf(WikiComponent.Html("其他信息", rawTable))))),
        )
        assertTrue("表格必须原样出现", html.contains(rawTable))
        assertFalse("表不能被转义", html.contains("&lt;table&gt;"))
    }

    @Test
    fun `图片行内样式与 src 都保留`() {
        val rawImg = "<p><img src=\"https://example.com/a.png\" width=\"92\"></p>"
        val html = WikiHtml.build(
            detail(modules = listOf(WikiModule("m", listOf(WikiComponent.Html("", rawImg))))),
        )
        assertTrue(html.contains("https://example.com/a.png"))
    }

    @Test
    fun `角色立绘的图注被转义 但 url 保持可用`() {
        val html = WikiHtml.build(
            detail(
                modules = listOf(
                    WikiModule(
                        "基础资料",
                        listOf(
                            WikiComponent.Role(
                                title = "心",
                                info = listOf("性别：女", "属性：导电"),
                                figures = listOf(WikiFigure("https://cdn/a.png", "基本信息")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(html.contains("https://cdn/a.png"))
        assertTrue(html.contains("基本信息"))
        assertTrue(html.contains("性别：女"))
    }

    @Test
    fun `★ 折叠标签用原生 details 不需要 JS`() {
        // WebView 里 JS 是**关掉**的（安全设置）。所以折叠必须靠原生 <details>，
        // 一旦改成需要 JS 的实现，标签页在真机上就永远是展开/折叠不了。
        val html = WikiHtml.build(
            detail(
                modules = listOf(
                    WikiModule(
                        "技能",
                        listOf(
                            WikiComponent.Tabs(
                                "技能",
                                listOf(WikiTab("常态攻击", "<p>a</p>"), WikiTab("共鸣技能", "<p>b</p>")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(html.contains("<details"))
        assertTrue(html.contains("<summary"))
        assertFalse("不能依赖 JS", html.contains("<script"))
    }

    @Test
    fun `语音组件展示台词文本`() {
        val html = WikiHtml.build(
            detail(
                modules = listOf(
                    WikiModule(
                        "角色语音",
                        listOf(
                            WikiComponent.Audio(
                                "个性语音",
                                listOf(WikiAudioTab("中文：菊花花", listOf(WikiAudioLine("普攻1", "华绽。"))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(html.contains("华绽。"))
        assertTrue(html.contains("普攻1"))
    }

    @Test
    fun `空模块不会产生空标题`() {
        val html = WikiHtml.build(detail(modules = listOf(WikiModule("", emptyList()))))
        // 没有模块时不应出现内容区标题
        assertFalse(html.contains("<h2>"))
    }

    @Test
    fun `文档是自包含的且有 viewport`() {
        // 没有 viewport 的话移动端会按 980px 排版，字小到看不清
        val html = WikiHtml.build(detail())
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("viewport"))
        assertTrue(html.contains("charset=\"utf-8\""))
    }
}
