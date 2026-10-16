package com.utilitybox.feature.settings

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.core.settings.SplashImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 外观设置（P11 从原设置页抽出）。
 *
 * 目前五组：主题 / 主题色 / 启动页背景 / 液态玻璃 / 减少动态效果。
 *
 * ★ 主题色与启动页背景是「个性化」的核心两项（用户要求）：
 *   前者影响**整个应用**的强调色，后者影响**每次冷启动的第一印象**。
 *
 * ★ 主题色的自定义入口分两级：**先给预设，不够再开色轮**。
 *   直接甩一个色轮给用户是不负责任的 —— 绝大多数人只想快速选个好看的，
 *   预设覆盖了这个需求；色轮留给有明确想法的少数人。
 */
@Composable
fun AppearanceSettingsScreen(
    currentTheme: UbThemeMode,
    onThemeChange: (UbThemeMode) -> Unit,
    accentSeed: Color?,
    onAccentChange: (Color?) -> Unit,
    splashImagePath: String?,
    onSplashPicked: (String?) -> Unit,
    onSplashCleared: () -> Unit,
    glassEnabled: Boolean,
    onGlassChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    // "默认"没有固定色值 —— 显示**主题自带的**强调色（浅色 #2F6BFF / 深色 #5B8CFF）。
    //
    // ★ 不能用 `c.accent`：那是"当前生效的色"。用户选过紫罗兰之后，
    //   `c.accent` 就是紫罗兰，"默认"色块于是变成一个和"紫罗兰"重复的紫色块 ——
    //   用户会以为默认就是紫色。默认必须显示**没自定义时会是什么样**。
    val builtInDefault = if (c.isDark) UbDarkColors.accent else UbLightColors.accent
    // 每个色块都显示**实际会用到的颜色**（整形后），做到所见即所得
    fun shownColor(preset: UbAccentPreset): Color =
        ubAccentFor(preset.seed ?: builtInDefault, c.isDark)

    val ctx = LocalContext.current

    var wheelOpen by remember { mutableStateOf(false) }
    // 色轮的初始色：用户已自定义过就用它，否则用当前生效的强调色（含主题默认）
    val wheelInitial = accentSeed ?: c.accent

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        // ★ 选中即复制进私有目录（见 SplashImageStore 的说明）——
        //   不依赖外部 URI 授权，用户之后删掉原图也不影响启动页。
        onSplashPicked(uri?.let { SplashImageStore.saveFromUri(ctx, it) })
    }

    if (wheelOpen) {
        UbColorWheelDialog(
            initial = wheelInitial,
            onDismiss = { wheelOpen = false },
            onConfirm = { picked ->
                onAccentChange(picked)
                wheelOpen = false
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        SectionCard {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = s.screenH, vertical = s.m),
                ) {
                    Text(text = "主题", style = t.bodyLarge, color = c.textPrimary)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = s.screenH, vertical = s.xs),
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    UbChip("浅色", selected = currentTheme == UbThemeMode.Light) { onThemeChange(UbThemeMode.Light) }
                    UbChip("深色", selected = currentTheme == UbThemeMode.Dark) { onThemeChange(UbThemeMode.Dark) }
                    UbChip("跟随系统", selected = currentTheme == UbThemeMode.System) { onThemeChange(UbThemeMode.System) }
                }
                UbDivider()
                UbSettingRow(
                    title = "液态玻璃效果",
                    subtitle = if (glassEnabled) "已开启" else "已关闭",
                    trailing = {
                        Switch(
                            checked = glassEnabled,
                            onCheckedChange = onGlassChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                        )
                    },
                )
                UbDivider()
                UbSettingRow(
                    title = "减少动态效果",
                    subtitle = "关闭模糊与转场动画",
                    trailing = {
                        Switch(
                            checked = reduceMotion,
                            onCheckedChange = onReduceMotionChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                        )
                    },
                )
            }
        }

        // ---- 主题色 ----
        SectionCard {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = s.screenH, vertical = s.m),
                ) {
                    Text(text = "主题色", style = t.bodyLarge, color = c.textPrimary)
                }
                Text(
                    text = "影响按钮、选中态与标签的强调色",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = s.screenH, end = s.screenH, bottom = s.s),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = s.screenH),
                    horizontalArrangement = Arrangement.spacedBy(s.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UbAccentPresets.forEach { preset ->
                        AccentSwatch(
                            color = shownColor(preset),
                            label = preset.label,
                            selected = when {
                                // "默认"命中条件：没有自定义种子
                                preset.seed == null -> accentSeed == null
                                accentSeed == null -> false
                                else -> sameHueFamily(preset.seed, accentSeed)
                            },
                            onClick = { onAccentChange(preset.seed) },
                        )
                    }
                }
                Box(modifier = Modifier.height(s.m))
                UbDivider()
                UbNavRow(
                    title = "自定义…",
                    icon = UbIcon.Palette,
                    trailingText = if (accentSeed != null) "#" + ubHexOf(accentSeed) else null,
                    onClick = { wheelOpen = true },
                )
            }
        }

        // ---- 启动页背景 ----
        SectionCard {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = s.screenH, vertical = s.m),
                ) {
                    Text(text = "启动页背景", style = t.bodyLarge, color = c.textPrimary)
                }
                SplashPreview(path = splashImagePath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = s.screenH, vertical = s.m),
                    horizontalArrangement = Arrangement.spacedBy(s.m),
                ) {
                    UbSecondaryButton(
                        label = if (splashImagePath == null) "选择图片" else "更换图片",
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (splashImagePath != null) {
                        UbTextButton(
                            label = "移除",
                            onClick = onSplashCleared,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = "图片会被复制到应用内并压缩，不会因相册删除而失效",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = s.screenH, end = s.screenH, bottom = s.m),
                )
            }
        }
    }
}

/** 一个预设色块。选中态用**外圈 + 对勾**两层表达，不只靠颜色（无障碍要求） */
@Composable
private fun AccentSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) c.textPrimary else c.divider,
                    shape = CircleShape,
                )
                .ubClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(text = "✓", style = t.bodyMedium, color = ubOnAccent(color))
            }
        }
        Text(
            text = label,
            style = t.caption,
            color = if (selected) c.textPrimary else c.textTertiary,
        )
    }
}

/** 启动页预览。做成一比一的手机比例，用户能预判实际效果 */
@Composable
private fun SplashPreview(path: String?) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bmp = withContext(Dispatchers.IO) { SplashImageStore.load(path, 480, 960) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH)
            .height(150.dp)
            .clip(RoundedCornerShape(r.card))
            .background(c.bgSubtle),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "启动页背景预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                Text(text = "默认启动页", style = t.bodyMedium, color = c.textSecondary)
                Text(text = "纯色底 + 应用标志", style = t.caption, color = c.textTertiary)
            }
        }
    }
}

/**
 * 两个颜色是否"看起来是同一个预设"。
 *
 * ★ 不能直接 `==`：用户从色轮选出的颜色会被 [ubAccentFor] 按主题明度整形，
 *   落盘的是**用户选的原色**、而显示的是整形后的色，两者永远不相等，
 *   于是"自定义"永远高亮、预设永远不高亮 —— 选中态就废了。
 *
 * ★★ 判定分两层（第一版只用了硬阈值，被单测抓出问题）：
 *
 *   1. **两个都接近无色** → 都算"中性"，互相视为同一族。
 *      第一版写的是「饱和度 < 0.15 就算中性」，而灰蓝 `#6B7280` 的饱和度是
 *      0.164 —— 刚好卡在阈值外面，于是它和"更灰一点"的 `#7A818C`（0.129）
 *      被判成两族。**阈值卡在两个相邻颜色的中间，就是设计错了**。
 *   2. 否则按色相距离判定，且**饱和度越低容差越大** ——
 *      低饱和时色相本身就不稳定（RGB 的轻微变化会让色相大幅跳），
 *      用固定容差会产生"看起来一样却不算命中"的结果。
 */
internal fun sameHueFamily(a: Color, b: Color): Boolean {
    /** 低于此饱和度视为"中性色"（石墨那类），色相没有意义 */
    val neutralSat = 0.25f

    val ha = UbHsv.fromRgb(a.red, a.green, a.blue)
    val hb = UbHsv.fromRgb(b.red, b.green, b.blue)
    val sa = ha[1]
    val sb = hb[1]

    if (sa < neutralSat && sb < neutralSat) return true

    val d = kotlin.math.abs(ha[0] - hb[0])
    val cyclic = minOf(d, 360f - d)
    val tolerance = 12f + 40f * (1f - minOf(sa, sb))
    return cyclic <= tolerance
}
