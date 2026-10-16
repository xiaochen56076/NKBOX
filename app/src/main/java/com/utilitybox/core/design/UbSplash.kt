package com.utilitybox.core.design

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.utilitybox.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import com.utilitybox.core.settings.SplashImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动页。
 *
 * ### 两种形态
 *
 * - **自定义背景**：用户选过图 → 全屏 [ContentScale.Crop] 铺满，
 *   外层压一层主题方向的暗化/提亮纱，保证上面的文字无论图是亮是暗都读得清。
 * - **默认品牌页**：没设图 / 图读不出来 → 纯色底 + 标志 + 应用名。
 *   ★ 这一档必须存在：**不能因为用户删了张图就白屏**。
 *
 * ### 动效
 *
 * 进场用全局缓动纲领（减速就位）：图从 1.06 缩到 1.0 + 淡入。
 * 「减少动态效果」开启时全部退化为瞬时，只保留必要的可读性。
 *
 * ★ 这个缩放不是装饰：启动页到主界面的切换是**淡出**，
 *   背景图静止不动地淡出会显得"卡住了一下"；轻微缩放让画面始终有生命感。
 */
@Composable
fun UbSplash(
    imagePath: String?,
    reduceMotion: Boolean,
    /** 用户点一下就直接跳过 —— 见下面 Box 上的 pointerInput 说明 */
    onSkip: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current
    val s = LocalUbSpacing.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val motion = LocalUbMotion.current

    // 目标解码尺寸按屏幕来 —— 不按屏幕解码会在高分辨率机型上糊，按屏幕以上解码是浪费
    val targetPx = with(density) { 1080.dp.roundToPx() }

    var bitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            SplashImageStore.load(imagePath, targetPx, targetPx)
        }
    }

    // ★★ 这里**刻意不做淡入**（第一版做了，实测踩到）。
    //
    //   Android 12+ 在应用进程起来之前会先显示**系统启动页**（应用图标 + windowBackground）。
    //   如果本层再从 alpha 0 淡入，用户看到的就是：
    //
    //       系统启动页 → **一片空白** → 标志浮现 → 淡出 → 主界面
    //
    //   中间那段空白是纯粹的观感缺陷，而且**只在真机冷启动时明显**
    //   （模拟器上进程起得慢，反而更容易抓到）。
    //
    //   系统启动页已经完成了"从无到有"的过渡，本层要做的只是**接手并让出**，
    //   所以只有缩放（1.06 → 1.0）没有淡入 —— 缩放不会产生空白帧。
    val settle = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            settle.animateTo(1f, tween(motion.enter, easing = UbEaseEnter))
        }
    }
    val p = settle.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bgBase)
            // ★★ 点一下即跳过（P11 补 12，用户反馈「内容显示应该再快些，
            //   用户互动过快反而导致内容会全白」）。
            //
            //   启动页是一层**挡住全部交互**的遮罩：手快的用户在主界面已经就绪时
            //   还在盯着它，点哪儿都没反应 —— 那正是"全白且没反馈"的观感。
            //   启动页的职责是**遮住等待**，不是**规定等待**；
            //   一旦用户想进去，就应该立刻让开。
            .pointerInput(Unit) {
                detectTapGestures { onSkip() }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f + 0.06f * (1f - p)
                        scaleX = scale
                        scaleY = scale
                        // 同样不做淡入 —— 只缩放，见下面 settle 的说明
                    },
                contentScale = ContentScale.Crop,
            )
            // 主题方向纱层：浅色主题提亮、深色主题压暗。
            // 目的不是"好看"，是让黑色文字/白色文字在任意背景图上都有对比度。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (c.isDark) Color.Black.copy(alpha = 0.34f)
                        else Color.White.copy(alpha = 0.28f)
                    ),
            )
            // 自定义图时不压品牌文字：用户选这张图就是想看它，盖一行字反而碍事
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(s.m),
                modifier = Modifier.graphicsLayer {
                    val scale = 0.96f + 0.04f * p
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                BrandMark()
                Text(text = UbStrings.appName, style = t.titleLarge, color = c.textPrimary)
                Text(text = UbStrings.appTagline, style = t.caption, color = c.textTertiary)
            }
        }
    }
}

/**
 * 默认标志：用户提供的 logo（黑底白标），与桌面图标用的是**同一份图形**。
 *
 * ★ P11 补 15 起改为使用真实 logo —— 此前是用代码画的"强调色方块 + 内嵌方框"，
 *   那是在应用还没有图标时临时顶上的。现在应用有了正式图标，
 *   启动页再画一个不一样的东西就成了"两个标志"。
 *
 * ★ 用 `ic_launcher_foreground` 而不是 `ic_launcher`：
 *   前者是 108dp 画布上的**白标（带透明通道）**，正好可以叠在自己画的黑色圆角底板上；
 *   后者已经把黑底烤进像素里，尺寸和圆角都不受控。
 *   而且 foreground 在 xxxhdpi 是 432px，显示 96dp 时仍然清晰。
 */
@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    val r = LocalUbRadius.current
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(r.cardLarge))
            // 黑色底板必须写死：logo 本身是"白标配黑底"设计的，
            // 放到主题色底板上白标会糊掉
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            // Fit 而不是 Crop：foreground 自带安全区留白，Crop 会把标志裁掉一圈
            contentScale = ContentScale.Fit,
        )
    }
}

