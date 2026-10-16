package com.utilitybox.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*

/**
 * 数据设置（P11 从原设置页抽出，并**重做自动刷新**）。
 *
 * ★ 用户反馈：「设置的自动刷新这里到 120 后，每次点击只会在关闭和 120 之间切换，
 *   能不能改为自定义呀，在弄点小警示就好了」。
 *
 * 原实现的缺陷：用"轮换"在 15/30/60/120 之间切，但到 120 后再点会关闭，
 * 于是 **120 → 关闭 → 120** 来回跳，永远回不到 15/30/60。这是个死循环式交互。
 *
 * 新交互：点开一个选择面板 —— 常用档位 + **自定义分钟数输入** + 警示说明。
 */
@Composable
fun DataSettingsScreen(
    onOpenApiKey: () -> Unit,
    onOpenDataSources: () -> Unit,
    autoRefreshEnabled: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit,
    autoRefreshMinutes: Int,
    onAutoRefreshMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        SectionCard {
            Column {
                UbNavRow(
                    title = "API Key",
                    icon = UbIcon.Key,
                    onClick = onOpenApiKey,
                    trailingText = if (com.utilitybox.core.secret.ApiKeyProvider.isConfigured()) {
                        com.utilitybox.core.secret.ApiKeyProvider.masked()
                    } else {
                        "未配置"
                    },
                )
                UbDivider()
                UbNavRow(
                    title = "数据源状态",
                    icon = UbIcon.Info,
                    onClick = onOpenDataSources,
                    trailingText = dataSourceSummary(),
                )
                UbDivider()
                CacheRow()
            }
        }

        // ---- 自动刷新 ----
        SectionCard(title = "自动刷新") {
            Column {
                UbSettingRow(
                    title = "开启自动刷新",
                    trailing = {
                        Switch(
                            checked = autoRefreshEnabled,
                            onCheckedChange = onAutoRefreshChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                        )
                    },
                )
                UbDivider()
                UbSettingRow(
                    title = "刷新间隔",
                    // 关闭时点间隔没有意义，如实灰掉而不是让用户白点
                    subtitle = if (autoRefreshEnabled) {
                        "$autoRefreshMinutes 分钟"
                    } else {
                        "自动刷新已关闭"
                    },
                    onClick = if (autoRefreshEnabled) ({ showPicker = true }) else null,
                    trailing = { ChevronTrailing() },
                )

                // ★ 小警示：间隔太短会消耗数据源额度（该数据源是**积分制**），
                //   这不是危言耸听 —— `/developer/subscription` 明确按积分计费。
                val warn = autoRefreshWarning(autoRefreshEnabled, autoRefreshMinutes)
                if (warn != null) {
                    WarningNote(text = warn, tone = warningTone(autoRefreshMinutes))
                }
            }
        }

        Text(
            text = "自动刷新只在你打开应用时生效，不会在后台常驻请求。",
            style = t.caption,
            color = c.textTertiary,
            modifier = Modifier.padding(horizontal = s.screenH),
        )
    }

    if (showPicker) {
        IntervalPickerDialog(
            current = autoRefreshMinutes,
            onDismiss = { showPicker = false },
            onPick = {
                onAutoRefreshMinutesChange(it)
                showPicker = false
            },
        )
    }
}

/** 警示文案。返回 null 表示当前值不需要提醒 —— 不为了"有警示"而无条件显示。 */
internal fun autoRefreshWarning(enabled: Boolean, minutes: Int): String? {
    if (!enabled) return null
    return when {
        minutes < 5 -> "间隔过短：数据源按积分计费，可能快速消耗额度。"
        minutes < 15 -> "间隔较短，会明显增加请求次数。"
        minutes > 360 -> "间隔很长，价格数据可能明显滞后。"
        else -> null
    }
}

@Composable
private fun warningTone(minutes: Int): WarningTone = when {
    minutes < 5 -> WarningTone.Danger
    minutes < 15 -> WarningTone.Warn
    else -> WarningTone.Info
}

internal enum class WarningTone { Info, Warn, Danger }

/** 一行警示说明（图标 + 文字），颜色随严重程度变化 */
@Composable
internal fun WarningNote(text: String, tone: WarningTone) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val tint = when (tone) {
        WarningTone.Info -> c.textTertiary
        WarningTone.Warn -> c.warning
        WarningTone.Danger -> c.danger
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.s),
        horizontalArrangement = Arrangement.spacedBy(s.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UbIcons.Icon(icon = UbIcon.Warning, tint = tint, size = 16.dp)
        Text(text = text, style = t.caption, color = tint)
    }
}

/**
 * 刷新间隔选择面板。
 *
 * ★ 用**行内展开**而不是 ModalBottomSheet：
 *   引入 material3 的 sheet 需要额外状态管理，而这里是设置页里的一个小选择，
 *   行内展开更轻、也更容易测试。
 */
@Composable
private fun IntervalPickerDialog(
    current: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    var custom by remember { mutableStateOf("") }

    // 常用档位。★ 不再只用这四档 —— 用户要的是**自定义**。
    val presets = listOf(5, 15, 30, 60, 120, 240)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.s)
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            .padding(s.cardPad),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(s.s)) {
            Text(text = "刷新间隔", style = t.titleMedium, color = c.textPrimary)

            // 档位：一行两个，避免长文字换行
            presets.chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(s.s)) {
                    rowItems.forEach { m ->
                        UbChip(
                            label = if (m >= 60) "${m / 60} 小时" else "$m 分钟",
                            selected = current == m,
                            onClick = { onPick(m) },
                        )
                    }
                }
            }

            Text(
                text = "自定义（分钟）",
                style = t.caption,
                color = c.textTertiary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.s),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = custom,
                    // 只收数字：非数字直接丢弃，比事后校验更早拦住错误
                    onValueChange = { input -> custom = input.filter { it.isDigit() }.take(4) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("例如 45", style = t.bodyMedium, color = c.textTertiary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                UbPrimaryButton(
                    label = "确定",
                    // 空输入或 0 都不可接受 —— 0 分钟意味着无限刷新
                    enabled = custom.toIntOrNull()?.let { it in 1..1440 } == true,
                    onClick = { custom.toIntOrNull()?.let(onPick) },
                )
            }
            Text(
                text = "范围 1–1440 分钟。间隔越短，消耗的数据源额度越多。",
                style = t.caption,
                color = c.textTertiary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = c.textSecondary)
                }
            }
        }
    }
}
