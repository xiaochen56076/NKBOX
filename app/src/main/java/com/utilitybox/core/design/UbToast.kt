package com.utilitybox.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 浮动提示气泡（iOS 风格 toast）。
 *
 * ## 为什么要有它
 *
 * 用户反馈（P11）：「复制问题，点击后**不用显示成为已复制**，直接不用变即可，
 * 来一个**小的提示气泡**来提示已复制即可，这样效果更加好些」。
 *
 * 原做法是把复制按钮本身变成「✓ 已复制」并在若干秒后复原 ——
 * 问题是：
 * 1. 按钮**变了样子**，用户会以为这是一个可切换的状态，而不是一次性动作；
 * 2. 每行都有自己的"已复制"态，滚回来还残留着，让人分不清哪个刚复制过；
 * 3. 按钮的文字从「复制」变「已复制」会**改变宽度**，整行跟着抖动。
 *
 * 气泡方案把"反馈"与"控件"分开：按钮**永远保持原样**，
 * 反馈由一个短暂的浮层承担，看完即消失。
 *
 * ## 与平台 Toast 的区别
 *
 * 不用 `android.widget.Toast`：它在各厂商 ROM 上位置、样式、时长都不一致
 * （vivo / MIUI / ColorOS 各有各的改法），无法保证与设计系统一致，
 * 也无法控制它在悬浮导航栏之下的位置。这里自己画，样式可控。
 *
 * ## 无障碍
 *
 * 标了 `liveRegion = Polite`，屏幕阅读器会在不打断当前朗读的前提下念出提示。
 */
@Composable
fun UbToast(
    message: String?,
    modifier: Modifier = Modifier,
    durationMillis: Long = 1600L,
    onDismiss: () -> Unit = {},
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val motion = LocalUbMotion.current
    val duration = motion.transition

    // 气泡自己管"显示多久"，调用方只需要给出消息内容。
    // 用 key 记住当前消息：新消息到来时重新计时（连点两次复制不会立刻消失）。
    var visible by remember(message) { mutableStateOf(message != null) }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        visible = true
        delay(durationMillis)
        visible = false
        onDismiss()
    }

    // 用 Box 撑满宽度以便居中；气泡本身是包裹内容的小胶囊
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible && message != null,
            enter = fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = 0.92f),
            exit = fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = 0.96f),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.bgInverse.copy(alpha = 0.92f))
                    .padding(horizontal = s.l, vertical = s.m)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = message.orEmpty(),
                    style = LocalUbTypography.current.bodyMedium,
                    color = c.textInverse,
                )
            }
        }
    }
}

/** 供页面使用的便捷包装：把"显示一条气泡"变成一个动作 */
@Composable
fun rememberUbToast(): UbToastState = remember { UbToastState() }

/**
 * 气泡状态。
 *
 * ★ 用 `mutableStateOf<String?>` 而不是"可见性布尔 + 文本"两个状态：
 *   两个状态会出现"文本已换但布尔还是 true"的中间态，
 *   表现为第二条提示**不重新计时**、一闪而过。
 *   把消息本身当状态，`LaunchedEffect(message)` 就会在消息变化时重新启动。
 */
class UbToastState {
    var message by mutableStateOf<String?>(null)
        private set

    fun show(text: String) {
        message = text
    }

    fun dismiss() {
        message = null
    }
}
