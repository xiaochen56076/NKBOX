package com.utilitybox.game.wutheringwaves

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.LocalUbColors
import com.utilitybox.core.design.LocalUbRadius
import com.utilitybox.core.design.LocalUbSize
import com.utilitybox.core.design.LocalUbSpacing
import com.utilitybox.core.design.LocalUbTypography
import com.utilitybox.core.design.SectionCard
import com.utilitybox.core.design.UbEmpty
import com.utilitybox.core.design.UbError
import com.utilitybox.core.design.UbItemIcon
import com.utilitybox.core.design.UbLoading
import com.utilitybox.core.design.UbSourceFooter
import com.utilitybox.game.wutheringwaves.kuro.KuroWikiApi
import com.utilitybox.game.wutheringwaves.kuro.WikiComponent
import com.utilitybox.game.wutheringwaves.kuro.WikiEntryDetail
import com.utilitybox.game.wutheringwaves.kuro.WikiResult
import com.utilitybox.game.wutheringwaves.kuro.WikiTable

/**
 * 词条详情 —— **原生 Compose 渲染**（P11 补 64）。
 *
 * ### 为什么重写
 *
 * 用户反馈两件事：
 * > 「里面的显示不好，你使用一些 ui 来替代这个 wiki 的风格，
 * >   原本的不好看，采用苹果设计风格，相当于你要把显示框架自己做一个」
 * > 「记得重写显示信息的界面，不要用 wiki 的那种，加载而且不好看」
 *
 * 原来这一页是 **WebView 装官方 HTML**。问题有两个：
 * 1. **样式不是我们的** —— 官方的字号、间距、配色，和 App 的设计系统完全两套；
 * 2. **要把整个页面塞四个模块**（技能介绍/角色攻略/角色档案/角色语音），
 *    用户明确说这些**都不要**，只要基础参数、共鸣链、突破材料。
 *
 * ### ★ 一个过期的注释
 *
 * `WikiComponent` 上写着「详情页必须用 WebView 渲染，Compose 手写 HTML 渲染器不现实」。
 * **这个判断在 `WikiTable` 写出来之后就不成立了** ——
 * 那些正文 HTML 的主体就是 `<table>`，而 `WikiTable.parse` 正是为它写的
 * （16 条单测，用真实 HTML 片段测的）。所以这里把它接上。
 *
 * ### 白名单
 *
 * 只渲染用户要的模块；**其余一律不显示**（而不是显示成"空"）。
 * 一个都没命中时回退 WebView —— 宁可样式差一点，也不能给用户一个空白页。
 */
@Composable
fun WuwaWikiEntryNativeScreen(
    entryId: String,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalUbSpacing.current

    var detail by remember(entryId) { mutableStateOf<WikiEntryDetail?>(null) }
    var loading by remember(entryId) { mutableStateOf(true) }
    var error by remember(entryId) { mutableStateOf<String?>(null) }

    LaunchedEffect(entryId) {
        loading = true
        error = null
        when (val r = KuroWikiApi.detail(entryId)) {
            is WikiResult.Ok -> detail = r.value
            is WikiResult.Failure -> error = r.message
        }
        loading = false
    }

    when {
        loading -> Box(modifier.fillMaxWidth().padding(top = s.xl), Alignment.Center) {
            UbLoading(lines = 6)
        }

        error != null -> Box(modifier.fillMaxWidth().padding(top = s.xl), Alignment.Center) {
            UbError(title = "加载失败", description = error ?: "")
        }

        else -> {
            val d = detail
            val blocks = remember(d) { d?.let { pickDisplayBlocks(it) }.orEmpty() }
            if (d == null || blocks.isEmpty()) {
                // ★ 白名单一个都没命中 → 回退 WebView。
                //   不能给用户"这一页什么都没有"。
                fallback()
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = s.screenH,
                        end = s.screenH,
                        top = s.m,
                        bottom = LocalUbSize.current.bottomBarReserved,
                    ),
                    verticalArrangement = Arrangement.spacedBy(s.m),
                ) {
                    item(key = "header") { NativeDetailHeader(d) }

                    blocks.forEachIndexed { i, b ->
                        item(key = "b$i") {
                            when (b) {
                                is DisplayBlock.RoleInfo -> RoleInfoCard(b.comp)
                                is DisplayBlock.Table -> TableCard(b.title, b.block)
                                is DisplayBlock.Text -> TextCard(b.title, b.block)
                                is DisplayBlock.TabGroup -> TabGroupCard(b.title, b.tabs)
                            }
                        }
                    }

                    item(key = "footer") {
                        UbSourceFooter(
                            source = "库街区 WIKI",
                            updatedAt = d.lastUpdateTime.ifBlank { "实时" },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 白名单
// ---------------------------------------------------------------------------

/** 详情页要渲染的一块内容 */
private sealed interface DisplayBlock {
    data class RoleInfo(val comp: WikiComponent.Role) : DisplayBlock
    data class Table(val title: String, val block: WikiTable.Block.Table) : DisplayBlock
    data class Text(val title: String, val block: WikiTable.Block.Plain) : DisplayBlock
    /**
     * 分页组件（P11 补 65）。
     *
     * ★ **突破材料就在这里** —— 实测「角色突破材料」是一个 Tabs，
     *   之前被 `is WikiComponent.Tabs -> Unit` 整块跳过了，
     *   所以滚到页尾都找不到「一阶~六阶」（关键词计数全为 0）。
     */
    data class TabGroup(val title: String, val tabs: List<TabContent>) : DisplayBlock
}

/** 一个 tab 的内容（已解析） */
data class TabContent(
    val title: String,
    val table: WikiTable.Block.Table?,
    val text: WikiTable.Block.Plain?,
)

/**
 * 要**保留**的模块名。
 *
 * ★ 用户原话：「你只需要给出，这个命座不同阶段的变化以及增伤，
 *   还有基础角色参数信息即可」「那些背景故事啥的不用展示」
 *   「上面招式那技能数据啥的，角色档案也不用」
 */
private val KEEP_MODULES = setOf("基础资料", "角色养成")

/**
 * 要**丢弃**的组件名（模块内的小节）。
 *
 * ★ 单独列出来而不是只靠模块白名单：这两个小节名字里有"角色"，
 *   但和"基础角色参数"完全是两回事 —— 一个是要去掉的攻略聚合，一个是语音。
 */
private val DROP_COMPONENTS = setOf(
    // ★ 「技能说明」是 P11 补 65 补上的：原来只写了「技能介绍」，
    //   而接口里这一段实际叫「技能说明」——**名字对不上，于是漏网了**。
    //   用户明确说「上面招式那技能数据啥的不用」。
    "技能介绍", "技能说明",
    "角色攻略", "角色档案", "角色语音",
)

/**
 * 从整份详情里挑出要显示的部分。
 *
 * ★ 纯函数（不依赖 Compose / Android），逻辑简单但**值得单独可读**：
 *   "显示什么、不显示什么"是产品决策，散在渲染代码里以后就没人看得出来了。
 */
private fun pickDisplayBlocks(detail: WikiEntryDetail): List<DisplayBlock> {
    val out = ArrayList<DisplayBlock>()
    for (module in detail.modules) {
        if (module.title !in KEEP_MODULES) continue
        for (comp in module.components) {
            val title = when (comp) {
                is WikiComponent.Role -> comp.title
                is WikiComponent.Html -> comp.title
                is WikiComponent.Tabs -> comp.title
                is WikiComponent.Audio -> comp.title
            }
            if (title in DROP_COMPONENTS) continue

            when (comp) {
                // ★ 这个类型**本来就是结构化的**（不用解析 HTML）：
                //   立绘 + 一串「键：值」文本
                is WikiComponent.Role -> out += DisplayBlock.RoleInfo(comp)

                is WikiComponent.Html -> {
                    if (comp.html.isBlank()) continue
                    val block = WikiTable.parse(comp.html)
                    out += when (block) {
                        is WikiTable.Block.Table ->
                            if (block.rows.isEmpty()) continue else DisplayBlock.Table(title, block)
                        is WikiTable.Block.Plain ->
                            if (block.text.isBlank()) continue else DisplayBlock.Text(title, block)
                    }
                }

                // ★ 分页组件：每个 tab 通常是一张材料表（如「一阶~六阶」）。
                //   这里**把所有 tab 依次铺开**而不是做成可切换的 TabRow ——
                //   用户要看的就是全部阶段的材料，切换反而多一次点击。
                is WikiComponent.Tabs -> {
                    val contents = comp.tabs.mapNotNull { tab ->
                        if (tab.html.isBlank()) return@mapNotNull null
                        when (val b = WikiTable.parse(tab.html)) {
                            is WikiTable.Block.Table ->
                                if (b.rows.isEmpty()) null else TabContent(tab.title, b, null)
                            is WikiTable.Block.Plain ->
                                if (b.text.isBlank()) null else TabContent(tab.title, null, b)
                        }
                    }
                    if (contents.isNotEmpty()) out += DisplayBlock.TabGroup(title, contents)
                }

                // 语音：用户明确不要
                is WikiComponent.Audio -> Unit
            }
        }
    }
    return out
}

// ---------------------------------------------------------------------------
// 渲染
// ---------------------------------------------------------------------------

@Composable
private fun NativeDetailHeader(d: WikiEntryDetail) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current

    Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
        Text(text = d.name, style = t.titleLarge, color = c.textPrimary)
        val meta = buildList {
            if (d.orgFullName.isNotBlank()) add(d.orgFullName)
            if (d.lastUpdateTime.isNotBlank()) add("更新 ${d.lastUpdateTime}")
            if (d.browseCount > 0) add("浏览 ${d.browseCount}")
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(text = meta, style = t.caption, color = c.textTertiary)
        }
    }
}

/** 角色组件：立绘 + 右侧「键：值」信息 */
@Composable
private fun RoleInfoCard(comp: WikiComponent.Role) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current

    SectionCard(title = comp.title.ifBlank { "角色" }) {
        Column(verticalArrangement = Arrangement.spacedBy(s.m)) {
            // 立绘：横向可滚动 —— 立绘通常是竖长图，一张就够宽了，
            // 挤在一屏里会变成邮票大小
            if (comp.figures.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(s.m),
                ) {
                    comp.figures.forEach { f ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(s.xs),
                        ) {
                            UbItemIcon(url = f.url, contentDescription = f.caption, size = 180.dp)
                            if (f.caption.isNotBlank()) {
                                Text(text = f.caption, style = t.caption, color = c.textTertiary)
                            }
                        }
                    }
                }
            }

            // 信息：「键：值」逐行，键用次级色，值用主色 —— 一眼能扫
            comp.info.filter { it.isNotBlank() }.forEach { line ->
                val key = line.substringBefore("：", "").substringBefore(":", "")
                val value = line.substringAfter("：", "").ifBlank {
                    line.substringAfter(":", "")
                }.ifBlank { line }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(s.m),
                ) {
                    if (key.isNotBlank() && value != line) {
                        Text(
                            text = key,
                            style = t.bodyMedium,
                            color = c.textSecondary,
                            modifier = Modifier.width(76.dp),
                        )
                    }
                    Text(text = value, style = t.bodyMedium, color = c.textPrimary)
                }
            }
        }
    }
}

/** 表格 → 卡片式网格（不用 HTML 的边框，改用底色区分） */
@Composable
private fun TableCard(title: String, block: WikiTable.Block.Table) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current

    SectionCard(title = title.ifBlank { "详情" }) {
        Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
            block.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(s.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.cells.forEach { cell ->
                        val weight = cell.span.coerceAtLeast(1).toFloat()
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier
                                .weight(weight)
                                .clip(RoundedCornerShape(r.button))
                                // ★ 用 `shaded`（原 HTML 的 background-color）决定底色，
                                //   而不是自己按行列奇偶涂色 —— 那是**编样式**，
                                //   官方标了底色的格子才是有含义的（比如高亮某项）
                                .background(if (cell.shaded) c.bgSubtle else Color.Transparent)
                                .padding(horizontal = s.s, vertical = s.xs),
                            horizontalArrangement = Arrangement.spacedBy(s.xs),
                            verticalArrangement = Arrangement.spacedBy(s.xs),
                        ) {
                            // ★★ P11 补 65：**按文档顺序**渲染，而不是"先全部文字、再全部图标"。
                            //
                            //   实测问题：共鸣链的效果文本读起来是残缺的 ——
                            //   「处于时：、的伤害倍率提升15%」
                            //   中间空的地方，原文是**内联技能小图标**。
                            //   原来所有 Text 先排完、所有 Image 排在最后，
                            //   于是图标全部跑到段落末尾，句子中间就出现了空档。
                            //
                            //   `cell.parts` 本来就是**文档顺序**的，所以只要按它遍历即可。
                            //   这里用 FlowRow 而不是纯 Column：图标能和文字在同一行内混排，
                            //   越界才换行，接近原文的排版。
                            cell.parts.forEach { part ->
                                when (part) {
                                    is WikiTable.Span.Text -> {
                                        if (part.text.isBlank()) return@forEach
                                        Text(
                                            text = part.text,
                                            style = if (part.bold) t.bodyMedium else t.caption,
                                            color = part.color
                                                ?.let { Color(it) }
                                                ?: if (part.bold) c.textPrimary else c.textSecondary,
                                        )
                                    }
                                    is WikiTable.Span.Image -> UbItemIcon(
                                        url = part.url,
                                        contentDescription = null,
                                        size = 22.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分页组 → **每个页签各一张卡**。
 *
 * ★ 不做成可切换的 TabRow：用户要看的是「全部阶段要什么材料」，
 *   做成切换反而多一次点击、还看不全。
 *   每个页签的标题（一阶/二阶/…）直接当卡片标题，扫一眼就知道阶段。
 *
 * ★ 直接复用 [TableCard]：它本来就是一个「标题 + 表格」的卡片，
 *   和这里要的形态完全一致，没必要再写一遍。
 */
@Composable
private fun TabGroupCard(title: String, tabs: List<TabContent>) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current

    Column(verticalArrangement = Arrangement.spacedBy(s.m)) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = t.titleMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(top = s.s),
            )
        }
        tabs.forEachIndexed { index, tab ->
            val table = tab.table
            // ★ P11 补 66：**命座标签**（用户指定）。
            //
            //   接口给的 tab 标题是 `1 / 20 / 40 / 50 / 60 / 70 / 80 / 90`，
            //   直接显示会让人以为是等级 —— 用户明确说：
            //   「那个 1，20，40… 是**命座**，**1 是 0 命的意思**，后面是依次增加」。
            //
            //   所以按**序号**映射：第 1 个 tab → 0 命，第 2 个 → 1 命 …… 依次递增。
            //   ★ 不用原始数字本身做运算 —— 那些数字是官方自己的编号，
            //     从中反推命座级数是在**猜规则**；而"第几个 tab = 第几命"
            //     是用户直接告诉我的事实。
            val label = "${index} 命"
            if (table != null) {
                TableCard(label, table)
            } else {
                tab.text?.let { TextCard(label, it) }
            }
        }
    }
}

/** 纯文本兜底 */
@Composable
private fun TextCard(title: String, block: WikiTable.Block.Plain) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current

    SectionCard(title = title.ifBlank { "详情" }) {
        Text(text = block.text, style = t.bodyMedium, color = c.textPrimary)
    }
}
