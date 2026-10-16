package com.utilitybox.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.*

/**
 * 工具子页面的统一外壳。
 *
 * 所有从首页进入的工具页面都用它，保证：一致的返回入口、标题排版、底部安全边距。
 * 这样各工具页面只需专注自己的业务内容，视觉一致性由公共层保证。
 *
 * ★★ P11 调整（用户反馈「设置界面太靠上了」）：
 *
 *   原先标题行只有 `top = s.xs`（4dp）的上边距，标题紧贴状态栏 ——
 *   截图看确实"顶到了天上"。现在：
 *   1. **上方留白**（`top = s.l`）+ 标题用 `displaySmall` 而非 `titleLarge`；
 *   2. 返回按钮与标题**基线对齐**，且给返回按钮足够的触控尺寸；
 *   3. 标题行下方再留一段间距，让标题与内容**分层**而不是黏在一起。
 *
 *   这与 iOS 的二级页一致：标题是"这一页叫什么"，需要呼吸空间。
 */
@Composable
fun UbToolPage(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题 + 返回
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = s.xs,
                    end = s.screenH,
                    // ★ 上方留白：这是"太靠上"的直接修复
                    top = s.l,
                    bottom = s.m,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            UbIconButton(icon = UbIcon.ChevronLeft, onClick = onBack, contentDescription = "返回")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = t.displaySmall,
                    color = c.textPrimary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = t.caption,
                        color = c.textTertiary,
                        modifier = Modifier.padding(top = s.xxs),
                    )
                }
            }
        }
        content(Modifier.weight(1f))
    }
}

/** 供需要"整页滚动 + 底部留白"的页面复用 */
@Composable
fun UbPageBottomSpacer(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(bottom = LocalUbSize.current.bottomBarInset))
}

/**
 * 把一个 [com.utilitybox.domain.tool.ToolProvider] 渲染成完整页面。
 *
 * ★ 这是"扩展机制"能成立的关键一步：provider 只声明**内容**与**标题**，
 *   统一的返回入口、标题排版、底部安全边距一律由这里提供。
 *   于是：
 *     - 各工具页面不必重复实现标题栏（视觉一致性由公共层保证）
 *     - **根导航不需要为每个工具写 `when` 分支** —— 直接按路由查注册表即可
 */
@Composable
fun UbToolHost(
    provider: com.utilitybox.domain.tool.ToolProvider,
    nav: com.utilitybox.domain.tool.ToolNavActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = provider.descriptor
    UbToolPage(
        title = d.name,
        subtitle = d.statusLabel ?: d.description,
        onBack = onBack,
        modifier = modifier,
    ) { inner ->
        provider.screen(nav = nav, modifier = inner)
    }
}
