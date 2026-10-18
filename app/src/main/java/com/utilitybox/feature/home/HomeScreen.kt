package com.utilitybox.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.*
import com.utilitybox.domain.model.ToolStatus
import com.utilitybox.domain.tool.UbToolRegistry

/**
 * 游戏工具页（P11 结构调整 + 补 20 视觉重做）。
 *
 * ★ 大标题是**游戏名**（三角洲 / 鸣潮），品牌名在身份卡上方的小字。
 *   用户要求：「把主页改为三角洲名称」。
 *
 * ★ 设置齿轮**移到了「我的」页面**（用户要求），所以这里没有齿轮。
 *
 * ★ 扩展铁律（docs/architecture.md §4.4）：**本文件不出现任何具体工具名**。
 *   卡片完全由 [UbToolRegistry] 驱动 —— 新增工具不需要改这里一行代码。
 *   本页只接收一个 [gameId]，三角洲与鸣潮共用同一份实现。
 *
 * ★ 不可用的工具**仍然显示**（让用户知道有此功能），但带灰态与明确原因。
 *
 * ### 补 20：为什么重做
 *
 * 用户反馈「主页还是太毛胚」。根因不是缺库，而是**Material3 几乎没用起来**
 * （全项目只用了 `Text`/`Switch`/`OutlinedTextField`/`TextButton` 四样），
 * 于是：
 *
 * | 问题 | 现在 |
 * |---|---|
 * | 卡片是纯色矩形，与背景几乎糊在一起 | `UbToolCard` 用 Material3 `Card` 的**描边 + 1dp 高度**分层 |
 * | 游戏身份只有一行文字 | `UbGameHeroCard`：强调色渐变 + 大号半透明水印图标 |
 * | 一屏内容没有分节 | `UbSectionHeader` 分节 |
 * | 工具少时下方大片空白 | `UbStatRow` 数据摘要填底，且数字由注册表**实际统计** |
 *
 * **没有新增任何依赖** —— 用的都是已在依赖里的 Material3。
 */
@Composable
fun GameToolsScreen(
    gameId: String,
    onOpenTool: (route: String) -> Unit,
    /**
     * 切换到另一个游戏（方案 C）。
     *
     * ★ **不写死为"三角洲 ↔ 鸣潮"**：从注册表取"下一个游戏"，
     *   所以加到三款、四款游戏时，这里会自动循环，不需要改代码
     *   （架构规则：不允许把架构写死为只能容纳两个游戏）。
     */
    onSwitchGame: (gameId: String) -> Unit = {},
    /** 是否显示切换入口（受设置「交互 → 游戏切换入口」控制） */
    showSwitch: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val tools = UbToolRegistry.homeTools(gameId)
    val game = UbToolRegistry.games.firstOrNull { it.id == gameId }

    // ★ 切换目标从注册表**循环取下一个**（见 [nextGameId]）。
    //   设置里关掉时直接取 null —— 入口与"没有下一个游戏"走同一条分支，
    //   不需要在 UI 里再判断一次设置。
    val nextGame = if (showSwitch) {
        nextGameId(gameId)?.let { id -> UbToolRegistry.games.firstOrNull { it.id == id } }
    } else {
        null
    }

    // ★ 统计从注册表**实际数**出来，不写死。写死的数字会随工具增减变成假话
    //   （P09 实测踩过：界面写着"2 个可用 · 1 个开发中"，实际有 4 个工具）。
    val all = UbToolRegistry.toolsOf(gameId)
    val enabledCount = all.count { it.status == ToolStatus.ENABLED }
    val experimentalCount = all.count { it.status == ToolStatus.EXPERIMENTAL }
    val pendingCount = all.count {
        it.status == ToolStatus.PENDING_SOURCE || it.status == ToolStatus.UNAVAILABLE
    }

    if (tools.isEmpty()) {
        // ★ 空态要区分"这个游戏还没做"与"你把工具都关掉了" ——
        //   两者对用户意味着完全不同的下一步。
        val totallyEmpty = all.isEmpty()
        Column(modifier = modifier.fillMaxWidth()) {
            HeroBlock(
                gameName = game?.name ?: gameId,
                meta = if (totallyEmpty) "工具开发中" else "工具都被你关掉了",
                watermark = game?.let { watermarkOf(it.id) } ?: UbIcon.Game,
                nextGameName = nextGame?.name,
                onSwitchGame = nextGame?.let { g -> { onSwitchGame(g.id) } },
            )
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = s.xxl),
                contentAlignment = Alignment.Center,
            ) {
                UbEmpty(
                    title = if (totallyEmpty) "这里还空着" else "工具都被你关掉了",
                    description = if (totallyEmpty) {
                        "该游戏的工具还在开发中。"
                    } else {
                        "到「我的 → 设置 → 工具管理」里重新打开。"
                    },
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = s.screenH,
            end = s.screenH,
            top = s.l,
            // 悬浮底部导航会盖住内容，故底部要留出它的占位
            bottom = LocalUbSize.current.bottomBarReserved,
        ),
        horizontalArrangement = Arrangement.spacedBy(s.m),
        // ★ 卡片间距比列间距更克制：横向 12dp 分组感更强，
        //   纵向 8dp 让同一屏能容纳更多卡片而不显拥挤。
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        // ---- 游戏身份卡（占满整行）----
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            UbGameHeroCard(
                brand = UbStrings.appName,
                title = game?.name ?: gameId,
                meta = buildString {
                    append("${all.size} 个工具")
                    if (enabledCount > 0) append(" · $enabledCount 个可用")
                    if (pendingCount > 0) append(" · $pendingCount 个待接入")
                },
                watermark = watermarkOf(gameId),
                switchLabel = nextGame?.name,
                onSwitch = nextGame?.let { g -> { onSwitchGame(g.id) } },
            )
        }

        // ---- 工具自带的首页摘要（P11 补 79）----
        //
        // ★ 用户要求把「今日密码」抠到身份卡下方，免去一次点击。
        // ★ 但**首页不知道具体是谁提供的** —— 只问注册表"这个游戏有没有摘要"。
        //   具体渲染在工具自己的包里（`ToolProvider.homeSummary`）。
        //   这样首页仍然不出现任何具体工具名，架构铁律不被破坏。
        val summary = UbToolRegistry.homeSummaryOf(gameId)
        if (summary != null) {
            item(key = "tool_summary", span = { GridItemSpan(maxLineSpan) }) {
                summary()
            }
        }

        // ---- 分节标题 ----
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            UbSectionHeader(title = "工具", trailing = "共 ${tools.size} 个")
        }

        itemsIndexed(tools, key = { _, it -> it.id }) { index, tool ->
            UbToolCard(
                title = tool.name,
                subtitle = tool.statusLabel ?: tool.description,
                icon = tool.icon,
                statusColor = when (tool.status) {
                    ToolStatus.PENDING_SOURCE, ToolStatus.UNAVAILABLE -> c.warning
                    ToolStatus.EXPERIMENTAL -> c.accent
                    else -> c.textTertiary
                },
                enabled = tool.status == ToolStatus.ENABLED,
                onClick = { onOpenTool(tool.route) },
                // ★ 工具卡片是**大块可点区域** → 强按压档（0.95）。
                //   入场按 index 错峰（我们自己的补充，逆向报告只有整体入场）。
                modifier = Modifier.ubEnter(index = index),
            )
        }

        // ---- 数据摘要（占满整行）----
        item(key = "stats", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                UbSectionHeader(title = "当前状态")
                UbStatRow(
                    stats = buildList {
                        add(Triple("${all.size}", "工具总数", c.textPrimary))
                        add(Triple("$enabledCount", "可使用", c.success))
                        if (experimentalCount > 0) {
                            add(Triple("$experimentalCount", "实验性", c.accent))
                        }
                        if (pendingCount > 0) {
                            add(Triple("$pendingCount", "待接入", c.warning))
                        }
                    },
                )
                Text(
                    text = "数据来源与更新时间见各工具页底部。",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = s.xs, top = s.m),
                )
            }
        }
    }

    // ★ 这里不再有任何尾部来源声明。
    //
    //   历史：曾有一行「来源：社区接口 · 以游戏内数据为准」（P11 按用户反馈删除），
    //   删除原因：来源声明放在**每个工具页自己的底部**才有意义 ——
    //   用户在列表页还没看到任何数据，此时声明来源没有信息量。
    //   各工具页内的声明**全部保留**。
}

/** 空态下也要有身份卡，所以单独抽出来（复用同一套视觉，不重复实现） */
@Composable
private fun HeroBlock(
    gameName: String,
    meta: String,
    watermark: UbIcon,
    nextGameName: String?,
    onSwitchGame: (() -> Unit)?,
) {
    val s = LocalUbSpacing.current
    Box(modifier = Modifier.padding(start = s.screenH, end = s.screenH, top = s.l)) {
        UbGameHeroCard(
            brand = UbStrings.appName,
            title = gameName,
            meta = meta,
            watermark = watermark,
            switchLabel = nextGameName,
            onSwitch = onSwitchGame,
        )
    }
}

/**
 * 游戏 → 水印图标。
 *
 * ★ **不在本文件写具体游戏名**（架构规则）：映射靠 gameId 的前缀判断，
 *   新增游戏时即使忘了加映射也只是水印回退成通用图标，不会崩、不会显示错的名字。
 */
private fun watermarkOf(gameId: String): UbIcon = when {
    gameId.startsWith("delta") -> UbIcon.Game
    gameId.startsWith("wuwa") -> UbIcon.Wave
    else -> UbIcon.Game
}

/**
 * 取"下一个游戏"的 id（**循环**）。
 *
 * ★ 抽成公开函数是因为**两个游戏页都要用**：三角洲页（[GameToolsScreen]）
 *   和鸣潮页（`WuwaScreen`）都要在身份卡上放「切换到 XX」。
 *   两处各写一份取法，迟早会出现"一个循环、一个不循环"的不一致。
 *
 * ★ 注册表只有一款游戏时返回 `null` —— 调用方据此自动隐藏入口，
 *   不需要在任何地方判断"够不够两款"。
 * ★ 找不到 currentId 时也返回 null（而不是硬返回第一个）——
 *   那会让"未知游戏"莫名其妙跳到第一款游戏。
 */
fun nextGameId(currentId: String): String? {
    val games = UbToolRegistry.games
    if (games.size <= 1) return null
    val i = games.indexOfFirst { it.id == currentId }
    if (i < 0) return null
    val next = games[(i + 1) % games.size]
    return next.id.takeIf { it != currentId }
}

/** 状态组件演示页 —— P02 原型要求"空数据与错误状态"有独立产物 */
@Composable
fun ProtoStatesScreen(modifier: Modifier = Modifier) {
    val s = LocalUbSpacing.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.screenH),
        verticalArrangement = Arrangement.spacedBy(s.l),
    ) {
        Text(text = "加载中", style = LocalUbTypography.current.labelMedium, color = LocalUbColors.current.textTertiary)
        UbLoading(lines = 2)

        Text(text = "空数据", style = LocalUbTypography.current.labelMedium, color = LocalUbColors.current.textTertiary)
        UbEmpty(
            title = "今日密码尚未发布",
            description = "数据源目前还没有给出今天的密码，通常在北京时间 00:05 后更新。",
            actionLabel = "重新加载",
            onAction = {},
        )

        Text(text = "错误", style = LocalUbTypography.current.labelMedium, color = LocalUbColors.current.textTertiary)
        UbError(
            title = "网络不可用",
            description = "请检查网络连接后重试。",
            onRetry = {},
        )

        Text(text = "陈旧数据横幅", style = LocalUbTypography.current.labelMedium, color = LocalUbColors.current.textTertiary)
        UbStaleBanner(
            message = "这是 10 月 13 日的数据，今日密码尚未更新。",
            actionLabel = "刷新",
            onAction = {},
        )
    }
}
