package com.utilitybox.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.core.secret.ApiKeyProvider.displayLabel
import com.utilitybox.domain.model.SourceState

/**
 * 数据源状态的一句话摘要。
 *
 * ★ 从注册表**实际统计**得出，按状态分组、只列出非零项。
 *   任何写死的数字都会随工具增减变成假话 —— P09 实测就踩到：
 *   界面写着"2 个可用 · 1 个开发中"，而实际有 4 个工具（还漏了实验性那个）。
 */
/** 设置行右侧的小箭头 —— 多个设置页共用 */
@Composable
internal fun ChevronTrailing() {
    UbIcons.Icon(
        icon = UbIcon.ChevronRight,
        tint = LocalUbColors.current.textTertiary,
        size = 16.dp,
    )
}
internal fun dataSourceSummary(): String {
    // ★ 统计**全部游戏**的工具：数据源状态页是全应用范围的，
    //   只统计"当前激活游戏"会在多游戏下少报（P11 结构调整）。
    val counts = com.utilitybox.domain.tool.UbToolRegistry
        .tools
        .groupingBy { it.status }
        .eachCount()

    fun n(s: com.utilitybox.domain.model.ToolStatus) = counts[s] ?: 0
    val parts = buildList {
        if (n(com.utilitybox.domain.model.ToolStatus.ENABLED) > 0) {
            add("${n(com.utilitybox.domain.model.ToolStatus.ENABLED)} 个可用")
        }
        if (n(com.utilitybox.domain.model.ToolStatus.EXPERIMENTAL) > 0) {
            add("${n(com.utilitybox.domain.model.ToolStatus.EXPERIMENTAL)} 个实验性")
        }
        if (n(com.utilitybox.domain.model.ToolStatus.PENDING_SOURCE) > 0) {
            add("${n(com.utilitybox.domain.model.ToolStatus.PENDING_SOURCE)} 个开发中")
        }
        if (n(com.utilitybox.domain.model.ToolStatus.DISABLED) > 0) {
            add("${n(com.utilitybox.domain.model.ToolStatus.DISABLED)} 个已关闭")
        }
        if (n(com.utilitybox.domain.model.ToolStatus.UNAVAILABLE) > 0) {
            add("${n(com.utilitybox.domain.model.ToolStatus.UNAVAILABLE)} 个不可用")
        }
    }
    return if (parts.isEmpty()) "暂无工具" else parts.joinToString(" · ")
}

/**
 * 缓存管理行（P09）。
 *
 * ★ 显示**真实占用**并真实清空。
 *   P02 原型里这里写的是编造的「12.4 MB」—— 那是在骗用户。
 *   现在按实际字节统计，并在清空后立即刷新显示。
 */
@Composable
internal fun CacheRow() {
    val context = LocalContext.current
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current

    // ★ 必须是 state：清空后要触发重组才能刷新显示
    //   （P06 实测踩过：普通 val 不触发重组，用户以为没清掉）
    var sizeBytes by remember { mutableStateOf(0L) }
    var count by remember { mutableStateOf(0) }
    var justCleared by remember { mutableStateOf(false) }

    fun refresh() {
        val cache = com.utilitybox.core.cache.PrefsLocalCache(context)
        sizeBytes = cache.sizeBytes()
        count = cache.entryCount()
    }

    remember(Unit) { refresh() }

    UbSettingRow(
        title = "缓存管理",
        subtitle = when {
            justCleared -> "已清空 · 页面会重新获取数据"
            count == 0 -> "暂无缓存 · 点击可清空"
            else -> "$count 项 · ${formatBytes(sizeBytes)} · 点击清空"
        },
        onClick = {
            com.utilitybox.core.cache.PrefsLocalCache(context).clear()
            justCleared = true
            refresh()
        },
        trailing = {
            if (count > 0) {
                Text(text = "清空", style = t.labelMedium, color = c.accent)
            } else {
                ChevronTrailing()
            }
        },
    )
}

/**
 * 字节数格式化。
 *
 * ★ 小于 1 KB 时显示实际字节数，**不四舍五入成 "0.0 KB"** ——
 *   那会让用户以为"没有缓存"，与事实不符。
 */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/**
 * 数据源状态页。
 *
 * ★ P09：**从注册表动态生成**，而不是硬编码三行。
 *   每个工具自己声明 [ToolStatus]，这里只做展示映射 ——
 *   新增工具会自动出现在本页，不需要回来改。
 *
 * ★ 状态直出 `ToolStatus`，**不美化、不统一成"可用"**：
 *   验证结论就是验证结论（P01 实测：好友状态无可用数据源）。
 */
@Composable
fun DataSourceStatusScreen(
    onOpenGlassProbe: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val c = LocalUbColors.current

    // ★ 列出**全部游戏**的工具：数据源状态是全应用范围的核查页，
    //   只看"当前激活游戏"会在多游戏下漏报（P11 结构调整）。
    val tools = com.utilitybox.domain.tool.UbToolRegistry.tools

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = LocalUbSize.current.bottomBarReserved),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        SectionCard(title = "数据源状态") {
            Column {
                tools.forEachIndexed { i, tool ->
                    if (i > 0) UbDivider()
                    val state = tool.status.toSourceState()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = s.screenH, vertical = s.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(s.m),
                    ) {
                        UbStatusDot(color = stateColor(state))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = tool.name, style = t.bodyLarge, color = c.textPrimary)
                            Text(
                                text = tool.status.sourceNote(),
                                style = t.caption,
                                color = c.textTertiary,
                            )
                        }
                        Text(text = stateLabel(state), style = t.labelMedium, color = stateColor(state))
                    }
                }
            }
        }

        SectionCard(title = "环境") {
            Column {
                UbSettingRow(
                    title = "API Key",
                    subtitle = if (com.utilitybox.core.secret.ApiKeyProvider.isConfigured()) {
                        "已配置 · ${com.utilitybox.core.secret.ApiKeyProvider.masked()}"
                    } else {
                        "未配置 —— 需要你自己填写"
                    },
                    trailing = {
                        Text(
                            text = com.utilitybox.core.secret.ApiKeyProvider.source().displayLabel(),
                            style = t.labelMedium,
                            color = c.textTertiary,
                        )
                    },
                )
                UbDivider()
                // 诊断入口：底部铺条纹穿过导航栏，用来验证玻璃模糊是否真的生效
                UbSettingRow(
                    title = "玻璃效果验证",
                    subtitle = "诊断用 · 在导航栏后方铺条纹，检查模糊是否生效",
                    onClick = onOpenGlassProbe,
                    trailing = { ChevronTrailing() },
                )
            }
        }

        Text(
            text = "说明：本应用使用社区第三方接口，非游戏官方接口。数据以游戏内为准，" +
                "仅供个人学习与技术研究使用。接口可能随时变更或停止服务。",
            style = t.caption,
            color = c.textTertiary,
            modifier = Modifier.padding(horizontal = s.screenH),
        )
    }
}

/** 工具状态 → 数据源状态的映射（只做展示语义转换，不改判定本身） */
private fun com.utilitybox.domain.model.ToolStatus.toSourceState(): SourceState = when (this) {
    com.utilitybox.domain.model.ToolStatus.ENABLED -> SourceState.VERIFIED
    com.utilitybox.domain.model.ToolStatus.EXPERIMENTAL -> SourceState.PENDING
    com.utilitybox.domain.model.ToolStatus.PENDING_SOURCE -> SourceState.PENDING
    com.utilitybox.domain.model.ToolStatus.DISABLED -> SourceState.RESTRICTED
    com.utilitybox.domain.model.ToolStatus.UNAVAILABLE -> SourceState.UNAVAILABLE
}

/**
 * 状态的**事实说明**。
 *
 * ★ 每一条都要说得出依据 —— 尤其 UNAVAILABLE 必须写清"实测过什么"，
 *   而不是笼统写"暂不支持"。
 */
private fun com.utilitybox.domain.model.ToolStatus.sourceNote(): String = when (this) {
    com.utilitybox.domain.model.ToolStatus.ENABLED ->
        "真实接口 · 已实测可用"
    com.utilitybox.domain.model.ToolStatus.EXPERIMENTAL ->
        "实验性 · 接口为公开端点，字段可能变动"
    com.utilitybox.domain.model.ToolStatus.PENDING_SOURCE ->
        "开发中 · 数据源待接入"
    com.utilitybox.domain.model.ToolStatus.DISABLED ->
        "已被你在上方工具管理中关闭"
    com.utilitybox.domain.model.ToolStatus.UNAVAILABLE ->
        "经实测核验：无任何可用数据源（个人数据端点需付费且无朋友列表）"
}

@Composable
private fun stateColor(state: SourceState) = when (state) {
    SourceState.VERIFIED -> LocalUbColors.current.success
    SourceState.PENDING -> LocalUbColors.current.warning
    SourceState.RESTRICTED -> LocalUbColors.current.warning
    SourceState.UNAVAILABLE -> LocalUbColors.current.danger
}

private fun stateLabel(state: SourceState) = when (state) {
    SourceState.VERIFIED -> "已验证"
    SourceState.PENDING -> "待验证"
    SourceState.RESTRICTED -> "受限"
    SourceState.UNAVAILABLE -> "不可用"
}

/**
 * 关于页（P11 补 84 —— 按用户要求改成「AppShare 那种版式」）。
 *
 * ### 改版要点
 *
 * 用户给了一张参考图（图三）并说「把阶段去掉，改为和图三一样的版式」。
 * 参考图的结构是：
 * ```
 *        [应用图标]
 *         应用名
 *       当前版本：x.y.z
 *
 *   ┌─────────────────────────┐
 *   │ 更新通道        正式版 ▼ │  ← 高亮行
 *   └─────────────────────────┘
 *   ┌────────┬────────┐
 *   │ 块1     │ 块2     │        ← 块状卡片网格
 *   ├────────┼────────┤
 *   │ 块3     │ 块4     │
 *   └────────┴────────┘
 *          ©2020-2026 xxx
 *        协议 | 隐私政策
 * ```
 *
 * **已删除**：原来的「已实现的阶段」整节（用户明确要求去掉）。
 *
 * ★ **不编造内容**：参考图里那些块是"开发动态 / 联系我们 / 致谢名单 / 捐赠支持"，
 *   本项目**没有**对应的真实页面或链接，照抄就会变成点了没反应、
 *   或者指向不存在的地址 —— 那是**造数据**。
 *   所以块里放的是**真实存在**的信息：开源许可、使用声明。
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val c = LocalUbColors.current
    val r = LocalUbRadius.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(s.xl))

        // ---- 应用标识（居中）----
        //
        // ★ P11 补 85：改用**应用自己的图标**（用户：「这个界面的图标就使用应用图标即可」）。
        //
        //   ★ 不能直接 `painterResource(R.mipmap.ic_launcher)` 塞进 72dp：
        //   那是个**自适应图标** —— 108dp 画布、中心 72dp 才是安全区
        //   （见 `mipmap-anydpi-v26/ic_launcher.xml`：背景色 + 前景图）。
        //   整张塞进去，图形只占 48dp，比桌面上的图标小一圈。
        //
        //   所以按系统规则自己合成：背景色铺满 + 前景图放大 1.5 倍（108/72），
        //   图形视觉大小就和桌面一致，超出的部分由 clip 裁掉。
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(r.cardLarge))
                .background(colorResource(id = com.utilitybox.R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = com.utilitybox.R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(1.5f),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(s.m))
        Text(text = UbStrings.appName, style = t.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(s.xs))
        Text(
            // ★ 版本取自构建配置，不写死 —— 写死会让"界面版本"和"装上的版本"不一致
            text = "当前版本：${com.utilitybox.BuildConfig.VERSION_NAME}",
            style = t.caption,
            color = c.textTertiary,
        )

        Spacer(Modifier.height(s.xl))

        // ---- 数据源（高亮行，对应参考图的"更新通道"）----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH)
                .clip(RoundedCornerShape(r.card))
                .background(c.accentSoft)
                .padding(s.cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "数据源", style = t.bodyLarge, color = c.textPrimary)
                Text(
                    text = "应用里所有游戏数据的来源",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = s.xxs),
                )
            }
            Text(text = "社区第三方接口", style = t.bodyLarge, color = c.accent)
        }

        Spacer(Modifier.height(s.m))

        // ---- 块状卡片（对应参考图的 2×2 网格）----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH),
            horizontalArrangement = Arrangement.spacedBy(s.m),
        ) {
            AboutBlock(
                modifier = Modifier.weight(1f),
                icon = UbIcon.Info,
                title = "开源许可",
                lines = listOf("AndroidX / Compose", "Kotlin · Apache-2.0"),
            )
            AboutBlock(
                modifier = Modifier.weight(1f),
                icon = UbIcon.Warning,
                title = "声明",
                lines = listOf("不做自动化操作", "不抓包、不索取密码"),
            )
        }

        Spacer(Modifier.height(s.xl))

        // ---- 居中页脚 ----
        Text(text = "©2026 ${UbStrings.appName}", style = t.caption, color = c.textTertiary)
        Spacer(Modifier.height(s.xs))
        Text(
            text = "完整许可清单见 LICENSES.md",
            style = t.caption,
            color = c.textTertiary,
        )
        Spacer(Modifier.height(s.l))
    }
}

/**
 * 关于页的块状卡片。
 *
 * ★ 做成**纯展示块**而不是按钮：参考图里那几块是可点的入口，
 *   但本项目没有对应的真实页面 —— 做成可点的会变成"点了没反应"。
 *   视觉上保留同样的块状层次，交互上不假装。
 */
@Composable
private fun AboutBlock(
    icon: UbIcon,
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val c = LocalUbColors.current
    val r = LocalUbRadius.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            .padding(s.cardPad),
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(r.button))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            UbIcons.Icon(icon = icon, tint = c.accent, size = 20.dp)
        }
        Spacer(Modifier.height(s.xxs))
        Text(text = title, style = t.bodyLarge, color = c.textPrimary)
        lines.forEach { line ->
            Text(text = line, style = t.caption, color = c.textTertiary)
        }
    }
}
