package com.utilitybox.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 色轮取色器（用户要求：「自定义采用默认几种颜色，不够可以自己轮盘选择」）。
 *
 * ### 结构
 *
 * ```
 *   ╭───────────╮     外圈：色相环（sweep 渐变，拖动改色相）
 *   │   ┌───┐   │     内芯：饱和度 × 明度方块（拖动改 S/V）
 *   │   │   │   │
 *   │   └───┘   │
 *   ╰───────────╯
 * ```
 *
 * ### 两个必须做对的地方
 *
 * 1. **色相环的 0° 在 3 点钟方向、顺时针增长**，与 `Brush.sweepGradient` 的排布一致。
 *    若按数学惯例逆时针算，拖动时指示点会往反方向跑 —— 那种"手在上面滑、点往下面走"
 *    的手感是最容易被察觉的错误。
 * 2. **命中判定分两块**：外环按"到圆心的距离与夹角"算，内方块按矩形内归一化坐标算。
 *    若只用一个 pointerInput 去猜，环与方块的边界处会出现选不中的死区。
 *
 * ★ 两个区域各自持有自己的 pointerInput，拖拽不会互相抢事件。
 */
@Composable
fun UbColorWheelDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    // 初始值拆成 HSV，之后三个分量各自独立变化，避免 RGB↔HSV 往返丢精度
    val start = remember(initial) { UbHsv.fromRgb(initial.red, initial.green, initial.blue) }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2]) }

    val current = remember(hue, sat, value) {
        val rgb = UbHsv.toRgb(hue, sat, value)
        Color(rgb[0], rgb[1], rgb[2], 1f)
    }

    // ★★ 预览必须显示**整形后**的颜色，也就是"确认之后真正会用到的那个色"。
    //
    //   第一版直接预览原始选色，被模拟器截图抓到问题：
    //   用户选 #7C4DFF（明度 1.0）→ 对话框里是一块亮紫 →
    //   一确认，浅色主题按可读性把它压到明度 0.62 → 变成明显更深的紫。
    //   用户看到的是"我选的颜色变了"，而这是一次**纯静默**的改写。
    //
    //   所以：取色环与方块仍然按原始 HSV 走（手指的位置要跟手），
    //   但**所有"结果"展示统一用整形后的色**，并明说会微调。
    val effective = remember(current) { ubAccentFor(current, c.isDark) }
    val shaped = effective != current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = s.l)
                .clip(RoundedCornerShape(LocalUbRadius.current.card))
                .background(c.bgElevated)
                .padding(s.l),
            verticalArrangement = Arrangement.spacedBy(s.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "自定义主题色", style = t.titleMedium, color = c.textPrimary)

            Box(
                modifier = Modifier.size(WHEEL),
                contentAlignment = Alignment.Center,
            ) {
                HueRing(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier.size(WHEEL),
                )
                SvSquare(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { newS, newV -> sat = newS; value = newV },
                    modifier = Modifier.size(SQUARE),
                )
            }

            // 实时预览：让用户直接看到"确认之后按钮长什么样"，
            // 而不是只有一个色块 —— 强调色的可读性只有在真实控件上才看得出来
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(s.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(effective),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#" + ubHexOf(effective),
                        style = t.bodyMedium,
                        color = c.textPrimary,
                    )
                    Text(
                        text = if (shaped) "已按可读性微调明度" else "强调色预览",
                        style = t.caption,
                        color = c.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(LocalUbRadius.current.chip))
                        .background(effective)
                        .padding(horizontal = s.l, vertical = s.s),
                ) {
                    // 用 ubOnAccent 算出的前景色 —— 用户选了亮黄也不会看不清字
                    Text(
                        text = "按钮",
                        style = t.bodyMedium,
                        color = ubOnAccent(effective),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(s.m),
            ) {
                UbTextButton(label = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                UbPrimaryButton(
                    label = "确定",
                    onClick = { onConfirm(current) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val WHEEL = 248.dp
private val SQUARE = 150.dp

/** 色相环：外圈 sweep 渐变 + 一个跟着走的指示点 */
@Composable
private fun HueRing(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stroke = 30.dp
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onHueChange(hueAt(pos, size.width.toFloat(), size.height.toFloat()))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange(hueAt(change.position, size.width.toFloat(), size.height.toFloat()))
                }
            },
    ) {
        val strokePx = stroke.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // HSV 色相 0=红 60=黄 120=绿 180=青 240=蓝 300=品红；
        // sweepGradient 从 3 点钟起、顺时针，所以按这个顺序铺色即可对齐
        val ring = listOf(
            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
            Color(0xFFFF0000),
        )
        drawCircle(
            brush = Brush.sweepGradient(ring, center = center),
            radius = radius,
            center = center,
            style = Stroke(width = strokePx),
        )

        // 指示点：内外各描一圈，保证在浅色和深色色相上都能看见
        val rad = Math.toRadians(hue.toDouble())
        val dot = Offset(
            center.x + radius * cos(rad).toFloat(),
            center.y + radius * sin(rad).toFloat(),
        )
        drawCircle(Color.White, radius = strokePx * 0.40f, center = dot)
        drawCircle(Color.Black.copy(alpha = 0.28f), radius = strokePx * 0.40f, center = dot, style = Stroke(2f))
    }
}

/** 饱和度 × 明度方块（x = 饱和度，y = 明度自上而下递减） */
@Composable
private fun SvSquare(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = remember(hue) {
        val rgb = UbHsv.toRgb(hue, 1f, 1f)
        Color(rgb[0], rgb[1], rgb[2], 1f)
    }
    // 外圈描边让方块在深色主题下也有边界
    val border = LocalUbColors.current.divider

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LocalUbRadius.current.field))
            .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            .pointerInput(Unit) {
                fun pick(pos: Offset) {
                    onChange(
                        (pos.x / size.width).coerceIn(0f, 1f),
                        (1f - pos.y / size.height).coerceIn(0f, 1f),
                    )
                }
                detectTapGestures { pick(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (1f - change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        // 自上而下叠一层透明→黑：把"明度"维度做出来
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(color = border, style = Stroke(1.5f))
            val x = sat * size.width
            val y = (1f - value) * size.height
            drawCircle(Color.White, radius = 9f, center = Offset(x, y))
            drawCircle(
                Color.Black.copy(alpha = 0.30f),
                radius = 9f,
                center = Offset(x, y),
                style = Stroke(2f),
            )
        }
    }
}

/** `0xRRGGBB` 十六进制（大写，不带 alpha）—— 与用户在别处看到的色值一致 */
fun ubHexOf(color: Color): String {
    fun c(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return "%02X%02X%02X".format(c(color.red), c(color.green), c(color.blue))
}

/**
 * 触点到环心的色相角。
 *
 * ★ 抽成顶层函数是为了**两处 pointerInput 共用同一套换算** ——
 *   点击与拖动若各写一份，迟早会改一处漏一处，表现为"点得准、拖不准"。
 *
 * ★ `atan2` 在屏幕坐标系（y 轴向下）里得到的角度本身就是顺时针增长，
 *   与 `Brush.sweepGradient` 的铺色方向一致 —— 所以不需要取反。
 *   取反的话指示点会往手指的反方向跑，那种手感错误一试就知道。
 */
private fun hueAt(pos: Offset, width: Float, height: Float): Float {
    val cx = width / 2f
    val cy = height / 2f
    var deg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat()
    if (deg < 0f) deg += 360f
    return deg
}
