package com.utilitybox.game.wutheringwaves.kuro

/**
 * 把词条结构拼成一份自包含的 HTML 文档，交给 WebView 渲染。
 *
 * ### 为什么用 WebView 而不是 Compose
 *
 * 词条正文是**原样 HTML**：`<table>` 排布、`<span style="color: rgb(…)">` 上色、
 * `<img>` 嵌图、`<hr>` 分隔。用 Compose 手写一个 HTML 渲染器意味着要实现
 * 表格布局、行内样式、图片流式排版 —— 那是重造一个浏览器，而且必然还原不准。
 *
 * ### ★ 内容区强制浅色
 *
 * 游戏方写的 HTML 里带的是**浅色假设**的硬编码颜色：
 * 正文 `color: rgb(0,0,0)`、表格底 `background-color: rgb(247,247,247)`。
 * 这套颜色放到深色背景上直接不可读。
 *
 * 所以内容区固定白底黑字，**但不动游戏自己的语义色**（五星金色之类）——
 * 那些颜色本身有含义，覆盖掉就丢了信息。
 * 页面外壳（标题栏）仍然跟随应用主题，所以深色模式下不会突兀。
 *
 * ### 安全
 *
 * 拼出来的 HTML **只做转义，不做过滤**（正文本来就有表格和行内样式，过滤会毁掉内容）。
 * 因此 WebView 侧必须关掉 JS 与文件访问、并拦掉所有跳转 —— 见 `WuwaWikiDetailScreen`。
 */
object WikiHtml {

    /** HTML 文本转义。用于我们自己插入的字段（标题、图注、台词） */
    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun build(detail: WikiEntryDetail): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">")
        append("<style>")
        append(
            """
            :root { color-scheme: light; }
            body {
              margin: 0; padding: 12px 16px 40px;
              background: #FFFFFF; color: #1A1A1C;
              font-family: -apple-system, "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
              font-size: 15px; line-height: 1.65;
              -webkit-text-size-adjust: 100%;
              word-break: break-word;
            }
            /* ★ P11 补 46「简化」：标题从 24px 降到 19px。
               页头（Compose 侧）已经写着「图鉴词条」，正文再喊一遍 24px 的大名
               是重复信息，还白占一屏顶部的注意力。19px 保留层级但不抢戏。 */
            h1 { font-size: 19px; margin: 0 0 4px; }
            .meta { color: #8A8A93; font-size: 11px; margin-bottom: 12px; }
            /* 章节标题的上下留白收紧：26px → 18px，一页能少滚半屏 */
            h2 {
              font-size: 16px; margin: 18px 0 8px; padding-left: 9px;
              border-left: 3px solid #BB9F5E; line-height: 1.3;
            }
            h3 { font-size: 14px; margin: 13px 0 6px; color: #4A4A52; }
            /* ★★ 最关键的一条：**给图片加高度上限**。
               官方立绘是竖构图、原图极大 —— 不加 max-height 时，
               「基本信息 + 编队立绘」两张就吃掉一整屏（截图实测），
               用户要滑很久才看得到下面的养成材料。
               限高 + object-fit:contain 保证完整可见且不裁切。 */
            img {
              max-width: 100%; max-height: 260px;
              width: auto; height: auto; object-fit: contain;
              border-radius: 8px;
            }
            table { max-width: 100%; border-collapse: collapse; font-size: 13px; }
            td, th { padding: 5px 8px; }
            hr { border: 0; border-top: 1px solid #E6E6EA; margin: 14px 0; }
            p { margin: 8px 0; }
            /* ★ 基础资料（性别/出生/武器/属性）改成**两列网格** ——
               原来一列四行，改成 2×2 后高度直接砍半。 */
            .info {
              list-style: none; padding: 0; margin: 8px 0 0;
              display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px;
            }
            .info li { padding: 3px 0; color: #4A4A52; font-size: 13px; }
            .figs { display: flex; gap: 10px; overflow-x: auto; padding: 2px 0 8px; }
            .figs figure { margin: 0; flex: 0 0 auto; width: 44%; }
            .figs img { max-height: 190px; }
            .figs figcaption { font-size: 11px; color: #8A8A93; text-align: center; margin-top: 4px; }
            .tab-title { font-weight: 600; margin: 13px 0 5px; color: #1A1A1C; }
            .line { padding: 6px 0; border-bottom: 1px solid #F0F0F3; }
            .line .label { display: block; font-size: 11px; color: #BB9F5E; margin-bottom: 2px; }
            .empty { color: #8A8A93; font-size: 13px; }
            """.trimIndent(),
        )
        append("</style></head><body>")

        append("<h1>").append(escape(detail.name)).append("</h1>")
        val meta = buildList {
            if (detail.orgFullName.isNotBlank()) add(escape(detail.orgFullName))
            if (detail.lastUpdateTime.isNotBlank()) add("更新 " + escape(detail.lastUpdateTime))
            if (detail.browseCount > 0) add("浏览 ${detail.browseCount}")
        }
        if (meta.isNotEmpty()) append("<div class=\"meta\">").append(meta.joinToString(" · ")).append("</div>")

        detail.modules.forEach { m ->
            if (m.title.isNotBlank()) append("<h2>").append(escape(m.title)).append("</h2>")
            m.components.forEach { appendComponent(it) }
        }

        append("</body></html>")
    }

    private fun StringBuilder.appendComponent(c: WikiComponent) {
        when (c) {
            is WikiComponent.Html -> {
                // 正文原样插入（含表格与行内样式）
                if (c.title.isNotBlank()) append("<h3>").append(escape(c.title)).append("</h3>")
                if (c.html.isNotBlank()) append(c.html)
            }
            is WikiComponent.Role -> {
                if (c.figures.isNotEmpty()) {
                    append("<div class=\"figs\">")
                    c.figures.forEach { f ->
                        append("<figure><img src=\"").append(escape(f.url)).append("\" alt=\"\">")
                        if (f.caption.isNotBlank()) {
                            append("<figcaption>").append(escape(f.caption)).append("</figcaption>")
                        }
                        append("</figure>")
                    }
                    append("</div>")
                }
                if (c.info.isNotEmpty()) {
                    append("<ul class=\"info\">")
                    c.info.forEach { append("<li>").append(escape(it)).append("</li>") }
                    append("</ul>")
                }
            }
            is WikiComponent.Tabs -> {
                c.tabs.forEach { tab ->
                    // 用 <details> 做折叠：原生、零 JS（WebView 里 JS 是关的）
                    append("<details").apply { if (tab === c.tabs.first()) append(" open") }.append(">")
                    append("<summary class=\"tab-title\">").append(escape(tab.title)).append("</summary>")
                    if (tab.html.isNotBlank()) append(tab.html)
                    append("</details>")
                }
            }
            is WikiComponent.Audio -> {
                c.tabs.forEach { tab ->
                    append("<h3>").append(escape(tab.title)).append("</h3>")
                    if (tab.lines.isEmpty()) {
                        append("<p class=\"empty\">（无台词文本）</p>")
                    } else {
                        tab.lines.forEach { line ->
                            append("<div class=\"line\">")
                            if (line.label.isNotBlank()) {
                                append("<span class=\"label\">").append(escape(line.label)).append("</span>")
                            }
                            append(escape(line.text))
                            append("</div>")
                        }
                    }
                }
            }
        }
    }
}
