package com.utilitybox.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp

/**
 * 分组卡片：页面内容的基本视觉单位。
 *
 * ★ [title] 可选：一张卡片本身就是一个分组，很多时候再给它起个标题是冗余的。
 *
 * ### 迁移记录（P11 补 16）
 *
 * 它原本定义在 `feature/settings/SettingsScreen.kt` 里 —— 当时只有设置页在用。
 * 现在鸣潮页（`game/wutheringwaves`）也需要它，而 **`game/` 去 import `feature/`
 * 是反向依赖**（架构规则第 9 条：业务模块禁止互相依赖；`feature/` 依赖 `game/`，
 * 不能倒过来，否则就成了环）。
 *
 * ★ 判断一个组件该放哪一层的标准不是"谁先写的"，而是**有没有第二个不同层的调用方**。
 *   出现第二个调用方，就是它该被提升的信号 —— 这次正好撞上。
 *
 * ★ 迁移成本为零：设置包下 9 个文件全部使用 `import com.utilitybox.core.design.*`
 *   通配导入，所以只是把定义搬了个位置，一行 import 都不用改。
 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    /**
     * 横向外边距（P11 补 83）。
     *
     * ### 为什么需要这个参数
     *
     * `SectionCard` **自带** `padding(horizontal = s.screenH)` —— 这在普通页面里是对的：
     * 调用方什么都不用做，卡片就自然左右留出屏边距。
     *
     * 但如果调用方**所在容器已经加了 screenH**（例如 `LazyVerticalGrid` 的
     * `contentPadding`、或外层 `Modifier.padding(horizontal = s.screenH)`），
     * 就会变成**双重缩进（64+64=128px）**，卡片比同页的英雄卡窄一圈。
     * 实测症状：首页「今日密码」摘要卡和鸣潮「手机号登录」卡
     * 都比英雄卡左右各缩进 64px（用户圈出来说「没有对齐」）。
     *
     * 传 `0.dp` 即可与已加边的父容器对齐。
     *
     * ### 默认值
     *
     * `Dp.Unspecified` 表示"用默认的 `s.screenH`"。
     * 不能用 `s.screenH` 直接做默认值 —— 默认参数只求值一次，
     * 而 `LocalUbSpacing` 是组合局部变量，必须在使用处读取。
     */
    horizontalPadding: Dp = Dp.Unspecified,
    content: @Composable () -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val edge = if (horizontalPadding == Dp.Unspecified) s.screenH else horizontalPadding

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = edge),
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = t.labelMedium,
                color = c.textTertiary,
                modifier = Modifier.padding(start = s.xs, top = s.s),
            )
        } else {
            // 无标题时仍要有上边距，否则卡片会贴着上一块内容
            Spacer(modifier = Modifier.padding(top = s.s))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalUbRadius.current.card))
                .background(c.bgElevated),
        ) {
            content()
        }
    }
}
