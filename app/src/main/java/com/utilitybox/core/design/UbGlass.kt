package com.utilitybox.core.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 玻璃分层策略（P04 改版：改用 Haze 2.0.1 折射玻璃）
 *
 * | 档位 | 条件 | 实现 |
 * |---|---|---|
 * | Glass | SDK_INT >= 31 且用户开启玻璃且未开启"减少动态效果" | **Haze 折射玻璃**（模糊 + 边缘折射 + 菲涅尔 + 高光） |
 * | Static | 其余情况 | 半透明材质 + 描边（主动降级，不依赖系统静默忽略） |
 *
 * ### 为什么从自研改为 Haze（用户于 P04 裁决）
 *
 * 自研实现（`Modifier.blur` + 半透明覆盖）**只能做整体均质模糊**，
 * 做不出边缘折射。并排对比实测见 `docs/liquid-glass-library-eval.md`：
 * 自研版本的胶囊边缘会溢出底色、无边缘光学；Haze 精确裁切且有折射收缩。
 *
 * ### Haze 用法要点（实测踩坑）
 *
 *  1. **内容必须标记为源**：`Modifier.hazeSource(state)`。否则玻璃没有可采样的像素。
 *  2. **`shape` 属于 `GlassStyle`，不是 `hazeGlass` 的参数**。
 *  3. **`HazeInput.Backdrop(state)` 需要 state 参数**（不能空参）。
 *  4. 降级是 Haze 内置能力：简化渲染器会**自动省略折射/色散**，
 *     但保留色调、形状、边缘 —— 因此**不需要写第二套降级样式**。
 *     我们的 [GlassTier.Static] 是更彻底的降级（完全不透明），用于用户主动关闭玻璃的场景。
 *
 * ### 可读性红线（P02 实测踩坑，仍然成立）
 *
 * 模糊**只能作用于背景**。自研时代把 `Modifier.blur` 加在容器上，
 * 子内容（图标/文字）一起被模糊，底部导航文字几乎不可见。
 * Haze 的玻璃是独立绘制层，天然不会模糊自己的子内容 —— 这个坑随之消失。
 */
enum class GlassTier { Glass, Static }

/**
 * 玻璃材质变体，直接对应 Haze 的两种内置材质。
 *
 *  - [Regular] 承载文字/控件的表面。Haze 官方说明其光色
 *    **「calibrated against native iOS 27 light appearance」**，扩散随表面尺寸自适应。
 *  - [Clear] 更通透，保留更多背景，适合图片/视频上的控件。
 *
 * 官方明确不承诺与 iOS 像素级一致，只表示「informed by the platform distinction」。
 */
enum class UbGlassStyle {
    Regular,
    Clear,
}

/** Haze 状态在整棵树内共享（玻璃只能采样同一 window 内更早绘制的内容） */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * 在子树内提供 Haze 状态。必须包裹「内容 + 玻璃」的共同父节点。
 * 应用根已调用一次（见 `MainActivity`）。
 */
@Composable
fun ProvideHazeState(content: @Composable () -> Unit) {
    val state = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides state) { content() }
}

/**
 * 把一块区域标记为"可被玻璃采样"的源。
 *
 * 玻璃要透出的内容都必须包在这里面，否则玻璃后面什么都没有。
 * 若外层没有提供 Haze 状态（例如单测或预览），本组件退化为普通容器，不报错。
 */
@Composable
fun UbGlassSource(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val st = LocalHazeState.current
    Box(modifier = if (st != null) modifier.hazeSource(st) else modifier) { content() }
}

@Composable
fun rememberGlassTier(reduceMotion: Boolean, glassEnabled: Boolean): GlassTier {
    val apiSupported = Build.VERSION.SDK_INT >= 31
    return if (apiSupported && glassEnabled && !reduceMotion) GlassTier.Glass else GlassTier.Static
}

/**
 * 玻璃表面。
 *
 * **所有玻璃效果必须经此组件**，不得在页面里直接用 Haze 或 `Modifier.blur`，
 * 以保证「可独立降级或关闭」这一设计稿硬性要求始终成立 ——
 * 将来要换玻璃实现（或再升级 Haze 版本），只改这一个文件。
 *
 * @param style 材质变体；浮层（底部导航）用 [UbGlassStyle.Regular]（可读性优先）
 * @param tier  档位；[GlassTier.Static] 时走不透明降级
 */
@Composable
fun UbGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalUbRadius.current.card),
    tier: GlassTier = GlassTier.Glass,
    style: UbGlassStyle = UbGlassStyle.Regular,
    /** 保留参数以兼容既有调用；Haze 的模糊半径由材质自适应，不再逐个指定 */
    blurRadius: Dp? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSize.current
    val hazeState = LocalHazeState.current

    if (tier == GlassTier.Glass && hazeState != null) {
        // Haze 的 shape 通过 GlassStyle 传入，且要求是 RoundedCornerShape
        val rounded = remember(shape) { shape.asRoundedForGlass() }
        val hazeStyle = remember(style, rounded) {
            when (style) {
                UbGlassStyle.Regular -> GlassStyle.regular.then { shape(rounded) }
                UbGlassStyle.Clear -> GlassStyle.clear.then { shape(rounded) }
            }
        }
        Box(
            modifier = modifier
                .hazeGlass(input = HazeInput.Backdrop(hazeState), style = hazeStyle)
                // ★ 裁切内容到同一形状：Haze 只负责**玻璃层**的形状，
                //   不会裁切我们的子内容。若不裁，子内容在形状之外依然可见
                //   （P09 实测：底栏溢出屏幕时，导航文字直接露在栏外）。
                .clip(rounded)
                .border(width = s.glassStroke, color = c.glassStroke, shape = rounded),
        ) {
            // ★★ 材质色调层（P09-补 深色缺陷修复）
            //
            //   为什么需要：Haze 的 `GlassStyle.regular` 官方说明是按
            //   **iOS 浅色外观**校准的，**没有深色变体**。深色主题下它会采样
            //   页面里的浅色卡片，输出一块亮灰 —— 实测像素 `#999A9D`，
            //   在近黑界面上就是"灰白色块"。
            //
            //   原生 iOS/Android 的材质都随主题切换（浅色偏白、深色偏黑），
            //   所以这里补一层与主题同向的色调。
            //
            //   它画在 Haze 玻璃**之上**（Haze 先绘制模糊背景，子内容后绘制），
            //   所以能真实压暗玻璃，而不是被玻璃盖住 —— 以实测像素为准，
            //   见 `docs/apple-design-notes.md` 的采样数据。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(c.glassTint.copy(alpha = c.glassTintAlpha)),
            )
            content()
        }
    } else {
        // 降级档：不透明材质 + 描边。保证可读性与信息层级不退化。
        Box(
            modifier = modifier
                .clip(shape)
                .background(c.bgElevated)
                .border(width = s.glassStroke, color = c.glassStroke, shape = shape),
        ) {
            content()
        }
    }
}

/**
 * 把通用 [Shape] 归一成 Haze 要求的 `RoundedCornerShape`。
 *
 * 设计系统的 API 用通用 [Shape]（调用方可以传任何形状），但 Haze 的
 * `GlassStyle.shape(...)` 只接受 `RoundedCornerShape`。
 * 已是圆角形状的直接复用；其余退化为全圆角胶囊 —— 这是本项目实际用到的形态
 * （导航栏、按钮、卡片都是圆角矩形/胶囊）。
 */
private fun Shape.asRoundedForGlass(): RoundedCornerShape =
    this as? RoundedCornerShape ?: RoundedCornerShape(percent = 50)
