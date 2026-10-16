package com.utilitybox.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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


import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 「再按一次返回键退出」提示条。
 *
 * ### 为什么要自己做而不是用平台 Toast
 *
 * 平台 `Toast` 的样式**在不同厂商 ROM 上差异很大**（vivo / MIUI / ColorOS 各不相同），
 * 位置、圆角、字号都不受控 —— 与设计系统不统一。
 * 本项目本来就要求「Apple 原生观感」，所以用 Compose 画一个受控的提示条。
 *
 * ### 无障碍
 *
 * 标了 `liveRegion = Polite` —— 屏幕阅读器会在内容出现时朗读，
 * 而不是等用户主动聚焦。这是无障碍要求（提示类信息必须能被听到）。
 */
@Composable
fun UbExitHint(
    visible: Boolean,
    text: String = "再按一次返回键退出",
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current
    val sz = LocalUbSize.current
    val motion = LocalUbMotion.current

    // 动效时长取自设计令牌：「减少动态效果」开启时会被压到 0，自动变成瞬时切换
    val duration = motion.transition

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(duration)) +
            slideInVertically(tween(duration)) { it / 3 },
        exit = fadeOut(tween(duration)) +
            slideOutVertically(tween(duration)) { it / 3 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH)
                // 抬到底部导航之上，避免遮挡
                .padding(bottom = sz.bottomBarReserved)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            UbGlassSurface(
                shape = RoundedCornerShape(LocalUbRadius.current.card),
                tier = LocalUbGlassVariantTier.current,
                style = UbGlassStyle.Regular,
            ) {
                Text(
                    text = text,
                    style = t.bodyLarge,
                    color = c.textPrimary,
                    modifier = Modifier.padding(horizontal = s.l, vertical = s.m),
                )
            }
        }
    }
}

/**
 * 「再按一次退出」的**双击返回状态机**。
 *
 * 抽出来是为了能被单元测试覆盖 —— 时间窗口是这里唯一容易写错的地方。
 *
 * @param windowMillis 第二次返回必须在这个窗口内才算"连按"
 */
class ExitConfirmState(
    private val windowMillis: Long = 2000L,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    var showHint by mutableStateOf(false)
        private set

    /**
     * 上一次返回的时刻；**null 表示还没按过**。
     *
     * ★★ 这里踩过一个真实缺陷（P11，由单元测试发现）：
     *   最初把「没按过」写成哨兵值 `Long.MIN_VALUE`，然后比较 `now - lastBackAt <= window`。
     *   但 `now - Long.MIN_VALUE` 会**整数溢出变成负数**，负数恒 `<= window` ——
     *   于是"第一次返回"被判成"窗口内第二次"，**按一次就直接退出**，
     *   恰好是这个功能本该避免的行为。
     *
     *   **教训：用哨兵值做减法比较必然踩溢出。**
     *   表达"还没有值"要用 `null`，而不是 `Long.MIN_VALUE` 这类极端值。
     */
    private var lastBackAt: Long? = null

    /**
     * 处理一次返回。
     *
     * @return true 表示**可以退出**（窗口内第二次返回）；false 表示已吞掉本次返回并提示
     */
    fun onBack(): Boolean {
        val now = nowMillis()
        val last = lastBackAt
        return if (last != null && now - last <= windowMillis) {
            showHint = false
            lastBackAt = null
            true
        } else {
            lastBackAt = now
            showHint = true
            false
        }
    }

    /** 提示条自动消失 */
    fun dismissHint() {
        showHint = false
    }
}

@Composable
fun rememberExitConfirmState(): ExitConfirmState =
    remember { ExitConfirmState() }

/**
 * 让提示条在 [ExitConfirmState.showHint] 变 true 后自动消失。
 *
 * 不自动消失的话，用户按了返回又做了别的事，提示会一直挂在那里。
 */
@Composable
fun AutoHideExitHint(state: ExitConfirmState, durationMillis: Long = 2000L) {
    val visible = state.showHint
    LaunchedEffect(visible) {
        if (visible) {
            delay(durationMillis)
            state.dismissHint()
        }
    }
}
