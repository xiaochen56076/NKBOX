package com.utilitybox.core.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 自动刷新调度（P11 接通）。
 *
 * ## 用户已被告知的行为
 *
 * 数据设置页上写着：
 * > 「自动刷新只在你打开应用时生效，不会在后台常驻请求。」
 *
 * 所以这里**只做前台定时**，不注册 `AlarmManager`、不起前台服务、
 * 不申请任何后台权限。这不是偷懒，是对用户承诺的兑现：
 * 一个工具类应用没有理由在后台偷偷发请求消耗数据源额度。
 *
 * ## 为什么把时间计算抽成纯函数
 *
 * 定时逻辑最容易错的地方是**边界**：interval 改了要重新计时吗？
 * 关掉再打开会不会立刻触发一次？这些问题在真机上"感觉一下"很难判断对错。
 * 抽成 [nextDelayMillis] 之后可以用纯 JVM 单测精确断言。
 */
object AutoRefreshPolicy {

    /**
     * 本次 tick 之后应等待多久（毫秒）。
     *
     * @return 等待时长；**null 表示不该启动定时器**（关闭 / 间隔非法）
     *
     * ★ 关掉时返回 null 而不是返回 0 或一个极大值：
     *   返回 0 会变成"疯狂循环"，返回极大值会留下一个永远不会醒的协程。
     *   用 null 让调用方**显式地不启动**，语义最清楚。
     *
     * ★ 间隔下限保护：即便设置里被写进 0 或负数（旧数据、手工改 prefs），
     *   这里也会拒绝启动，绝不出现"无限请求"。
     */
    fun nextDelayMillis(enabled: Boolean, minutes: Int): Long? {
        if (!enabled) return null
        if (minutes !in MIN_MINUTES..MAX_MINUTES) return null
        return minutes * 60_000L
    }

    /**
     * 是否应当**立即**先刷一次。
     *
     * ★ 刻意返回 false：开启自动刷新时不该顺手多发一次请求。
     *   各页面进入时本来就会自己加载一次，再补一次是重复请求 ——
     *   而数据源是**积分制**的。
     */
    fun shouldFireImmediately(): Boolean = false

    /** 间隔下限（分钟）。与数据设置页的输入范围保持一致。 */
    const val MIN_MINUTES = 1

    /** 间隔上限（分钟）＝ 24 小时。再长就不如直接手动刷新了。 */
    const val MAX_MINUTES = 1440
}

/**
 * 自动刷新状态。
 *
 * ★ 用**递增的计数器**而不是"一个待处理的布尔标志"：
 *   布尔标志在"上一次刷新还没结束、下一次 tick 就到了"时会丢失事件；
 *   计数器每次都变，消费方用 `LaunchedEffect(tick)` 就能做到
 *   **每一个 tick 都被处理**，且重复值不会误触发。
 *
 * ★★ tick 必须是 **Compose 可观察状态**（`mutableStateOf`）：
 *   否则 `LaunchedEffect(AutoRefreshState.tick)` 读到的值不会触发重组，
 *   定时器到点了界面**毫无反应** —— 这正是本项目已经踩过两次的
 *   "把状态藏在普通属性后面"（P09 工具启停、P11 返回键）。
 *   第三次不重复犯。
 */
object AutoRefreshState {

    /** 每完成一次"该刷新了"就 +1。初始为 0（尚无需刷新）。 */
    var tick: Long by mutableStateOf(0L)
        private set

    /**
     * 通知一次自动刷新。
     *
     * @return 递增后的 tick 值
     */
    fun onTick(): Long {
        tick += 1
        return tick
    }

    /** 供测试复位（生产代码不应调用） */
    internal fun resetForTest() {
        tick = 0L
    }
}
