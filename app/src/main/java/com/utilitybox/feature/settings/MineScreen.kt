package com.utilitybox.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.utilitybox.core.account.AccountState
import com.utilitybox.core.design.*
import com.utilitybox.domain.model.AccountProfile
import com.utilitybox.domain.tool.UbToolRegistry

/**
 * 「我的」页面（P11 按用户提供的参考图重构）。
 *
 * ## 参考图的结构，以及本页借用了哪些层次
 *
 * 用户给的参考图（一个社区 App 的「我的」页）有以下层次，本页**按同样的层次感**组织：
 *
 * | 参考图元素 | 本页对应 |
 * |---|---|
 * | 顶部图标 + 设置齿轮 | 大标题「我的」（齿轮已按用户要求移到下方列表） |
 * | 头像 + 昵称 + 等级 + 进度条 | 账号卡片（头像 / 昵称 / 登录方式） |
 * | 右侧「签到」主要动作按钮 | 右侧「去登录 / 切换账号」（保留"右侧一个动作"的布局） |
 * | 两栏数字（动态 / 关注） | 两栏统计（已接入游戏 / 已启用工具） |
 * | 「我的资产」信息密集卡 | 入口卡（工具管理 / 外观 / 数据 / 设置） |
 * | 「更多服务」文字行列表 | 已并入上面的入口卡（关于与开源许可移到「设置」页） |
 *
 * ## 明确**不照搬**的部分
 *
 * - ❌ 签到、会员中心、任务中心、积分商城、应用升级 —— 本应用没有这些功能。
 *      画上去就是**假按钮**（红线：不得用空按钮冒充已完成的交互）。
 * - ❌ 关注 / 动态等社交数字 —— 没有后端。
 * - ❌ 那套粉色视觉 —— 与 Liquid Glass 设计系统冲突。
 *      但"整体风格的颜色"这个需求通过**主题快捷切换**满足（见下）。
 *
 * ## 用户特别要求：主题放在这个界面
 *
 * 用户原话：「例如主题可以放在这个界面，就是整体风格的颜色」。
 * 所以本页直接放一排主题色块（浅色 / 深色 / 跟随系统），
 * 不必再走「设置 → 外观」两步才能换。
 */
@Composable
fun MineScreen(
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    currentTheme: UbThemeMode,
    onThemeChange: (UbThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val profile = AccountState.profile

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        // ---- 大标题 ----
        // ★ P11 补：用户要求「右上角的设置去掉，直接变成选项放到数据下面去一起排着」。
        //   所以这里不再有齿轮 —— 设置降级为下方列表里的第四行，
        //   与「工具管理 / 外观 / 数据」并列在**同一个卡片**内。
        //   好处是入口只有一处、顺序一致，不再出现"齿轮进设置"和"列表也进设置"两条路。
        //
        // ★ 上方留白 s.l：与二级页标题栏保持一致，不再"顶到状态栏"
        Text(
            text = "我的",
            style = t.displayLarge,
            color = c.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = s.screenH, end = s.screenH, top = s.l, bottom = s.m),
        )

        ProfileCard(
            profile = profile,
            onOpenLogin = onOpenLogin,
            modifier = Modifier.ubEnter(index = 0),
        )

        StatRow(modifier = Modifier.ubEnter(index = 1))

        // ---- 主题快捷切换（用户明确要求放在这个界面）----
        ThemePickerCard(
            current = currentTheme,
            onChange = onThemeChange,
            modifier = Modifier.ubEnter(index = 2),
        )

        // ---- 入口列表（P11 补 25 精简）----
        //
        // ★ 用户把「工具管理 / 数据 / 交互」圈出来，要求**移到「设置」页**。
        //   本页只留「外观」与「设置」。
        //
        // ★ 为什么「外观」留下而「数据」「交互」走了：
        //   这是**用户的圈选**，不是我自作主张。功能上三者都是"设置"的子类，
        //   严格说「外观」也该一起走 —— 已把这处不对称在报告里提出，
        //   等用户裁决；**不擅自扩大改动范围**。
        SectionCard(modifier = Modifier.ubEnter(index = 3)) {
            Column {
                UbNavRow(title = "外观", icon = UbIcon.Palette, onClick = onOpenAppearance)
                UbDivider()
                UbNavRow(title = "设置", icon = UbIcon.Gear, onClick = onOpenSettings)
            }
        }

        // ★ P11 补：「关于与开源许可」已移到「设置」页内（用户要求）。
        //   这里不再保留入口 —— 同一目标有两个入口会让人不知道哪个才是"正式"的。
    }
}

/** 账号卡片：头像 + 昵称 + 登录方式 + 右侧一个主要动作 */
@Composable
private fun ProfileCard(
    profile: AccountProfile,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH)
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            .ubClickable(scale = UbPressScale.Card, onClick = onOpenLogin)
            .padding(s.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        if (profile.avatarUrl != null) {
            UbItemIcon(url = profile.avatarUrl, contentDescription = profile.nickname, size = 56.dp)
        } else {
            AvatarFallback(profile.nickname)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.xxs)) {
            Text(
                text = profile.nickname ?: "未登录",
                style = t.titleMedium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (profile.provider) {
                    // ★ 如实标注登录方式：本地昵称**不是** QQ 登录。
                    //   不标出来会让用户以为已经绑定了 QQ。
                    AccountProfile.Provider.QQ -> "QQ 登录"
                    AccountProfile.Provider.LOCAL -> "本地昵称 · 未绑定 QQ"
                    AccountProfile.Provider.NONE -> "登录后可同步偏好"
                },
                style = t.caption,
                color = c.textTertiary,
            )
        }

        // 参考图右侧是一个主要动作按钮（"签到"）。
        // 这里的对应物是"登录 / 切换账号" —— ★ 只做**真实存在**的动作。
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(r.capsule))
                .background(c.accentSoft)
                .padding(horizontal = s.l, vertical = s.s),
        ) {
            Text(
                text = if (profile.isLoggedIn) "切换账号" else "去登录",
                style = t.labelMedium,
                color = c.accent,
            )
        }
    }
}

/** 无头像时的占位：昵称首字，没有昵称就显示中性符号 */
@Composable
private fun AvatarFallback(nickname: String?) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val initial = nickname?.trim()?.firstOrNull()?.toString()

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.bgSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial ?: "—", style = t.titleMedium, color = c.textSecondary)
    }
}

/**
 * 两栏统计。
 *
 * ★ 数字**全部来自注册表实时统计**，不写死 ——
 *   写死的数字会随工具增减变成假话（P09 已因硬编码"2 个可用"踩过一次）。
 */
@Composable
private fun StatRow(modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    val games = UbToolRegistry.games.size
    val enabledTools = UbToolRegistry.tools.count { !UbToolRegistry.isDisabled(it.id) }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.screenH),
        horizontalArrangement = Arrangement.spacedBy(s.s),
    ) {
        StatTile(
            value = games.toString(),
            label = "已接入游戏",
            modifier = Modifier.weight(1f),
            valueStyle = t.numericLarge,
            labelStyle = t.caption,
            valueColor = c.textPrimary,
            labelColor = c.textTertiary,
            cardColor = c.bgElevated,
            radius = r.card,
        )
        StatTile(
            value = enabledTools.toString(),
            label = "已启用工具",
            modifier = Modifier.weight(1f),
            valueStyle = t.numericLarge,
            labelStyle = t.caption,
            valueColor = c.textPrimary,
            labelColor = c.textTertiary,
            cardColor = c.bgElevated,
            radius = r.card,
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier,
    valueStyle: TextStyle,
    labelStyle: TextStyle,
    valueColor: Color,
    labelColor: Color,
    cardColor: Color,
    radius: Dp,
) {
    val s = LocalUbSpacing.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(cardColor)
            .padding(vertical = s.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.xxs),
    ) {
        Text(text = value, style = valueStyle, color = valueColor)
        Text(text = label, style = labelStyle, color = labelColor)
    }
}

/**
 * 主题快捷切换（用户要求放在「我的」）。
 *
 * ★ 色块用**各自主题真实的背景/强调色**做预览，而不是统一用当前主题的颜色 ——
 *   那样三个块长得一模一样，用户看不出区别。
 */
@Composable
private fun ThemePickerCard(
    current: UbThemeMode,
    onChange: (UbThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    SectionCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(s.cardPad),
            verticalArrangement = Arrangement.spacedBy(s.s),
        ) {
            Text(text = "主题", style = t.bodyLarge, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(s.m)) {
                UbThemeMode.entries.forEach { mode ->
                    ThemeSwatch(mode = mode, selected = current == mode, onClick = { onChange(mode) })
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(mode: UbThemeMode, selected: Boolean, onClick: () -> Unit) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    val scheme = when (mode) {
        UbThemeMode.Light -> UbLightColors
        UbThemeMode.Dark -> UbDarkColors
        UbThemeMode.System -> if (isSystemInDarkTheme()) UbDarkColors else UbLightColors
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.xs),
        modifier = Modifier
            .clip(RoundedCornerShape(r.button))
            .ubClickable(onClick = onClick)
            .padding(s.xs),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(r.button))
                .background(scheme.bgElevated)
                // ★ 选中态用**外框**表示，不改色块本身 ——
                //   改了就看不出这个色块代表什么颜色了
                .border(
                    width = 1.5.dp,
                    // 用 divider 令牌作为未选中态的边框色：
                    // 它是"分隔线"语义，正好适合做细边框，避免再引入一个相近的令牌
                    color = if (selected) c.accent else c.divider,
                    shape = RoundedCornerShape(r.button),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 色块里放一小条强调色，让预览有层次而不是一块纯色
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(scheme.accent),
            )
        }
        Text(
            text = mode.label,
            style = t.caption,
            color = if (selected) c.accent else c.textTertiary,
        )
    }
}

/** 主题模式的中文名（设置页与「我的」共用同一份文案，避免两处漂移） */
internal val UbThemeMode.label: String
    get() = when (this) {
        UbThemeMode.Light -> "浅色"
        UbThemeMode.Dark -> "深色"
        UbThemeMode.System -> "跟随系统"
    }
