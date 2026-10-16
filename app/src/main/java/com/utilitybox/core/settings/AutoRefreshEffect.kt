package com.utilitybox.core.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.utilitybox.domain.model.UbSettings
import kotlinx.coroutines.delay

/**
 * 自动刷新定时器（P11 接通）。
 *
 * 挂在根壳里（`UbRootShell`）一处，全应用共用：
 * 到点后递增 [AutoRefreshState.tick]，各页面的 Holder 观察到 tick 变化就重新拉数据。
 *
 * ## ★ 只在应用处于 STARTED 时计时
 *
 * 数据设置页上对用户写着：
 * > 「自动刷新只在你打开应用时生效，不会在后台常驻请求。」
 *
 * 这里用 [repeatOnLifecycle] 兑现它：
 * 应用退到后台 → 协程被取消，**定时器停止**；回到前台 → 重新开始计时。
 *
 * 为什么不是"后台继续跑"：
 * - 数据源是**积分制**的，后台请求会白白消耗用户的额度；
 * - 要让定时器在后台存活就得申请 `FOREGROUND_SERVICE` / 精确闹钟等权限，
 *   一个工具类应用没有理由要这些权限。
 *
 * ## ★ 不"开启即刷一次"
 *
 * 各页面进入时本来就会自己加载一次。开启自动刷新时再补一次是**重复请求**。
 * 见 [AutoRefreshPolicy.shouldFireImmediately]。
 */
@Composable
fun AutoRefreshEffect(settings: UbSettings) {
    val delayMillis = AutoRefreshPolicy.nextDelayMillis(
        enabled = settings.autoRefreshEnabled,
        minutes = settings.autoRefreshMinutes,
    ) ?: return  // 关闭 / 间隔非法 → 不启动定时器

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(delayMillis, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // 回到前台后重新开始计时（而不是补算离开期间漏掉的次数）——
            // 补算会在回到前台的瞬间连发多次请求，那正是我们要避免的。
            while (true) {
                delay(delayMillis)
                AutoRefreshState.onTick()
            }
        }
    }
}
