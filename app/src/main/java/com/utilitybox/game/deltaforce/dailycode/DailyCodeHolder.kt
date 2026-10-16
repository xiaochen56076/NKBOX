package com.utilitybox.game.deltaforce.dailycode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.domain.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 今日密码的界面状态持有者。
 *
 * ★ 为什么不用 `androidx.lifecycle.ViewModel`：
 *   那需要引入 `lifecycle-viewmodel-compose` 依赖。而本项目的状态逻辑
 *   **已经全部在 [DailyCodeRepository] 里**（可纯 JVM 单测），
 *   ViewModel 只剩"持有状态 + 触发加载"，用 Compose 原语即可胜任。
 *   在设计稿「零新依赖优先」与「依赖必须有明确理由」的规则下，
 *   为这点职责引入一个依赖不划算。将来若需要跨配置变更保活或 SavedState，
 *   再引入官方 ViewModel 也只是替换本类。
 *
 * ★ 界面状态用 [UiState] 包裹 [DailyCodeState]：
 *   `UiState` 表达"加载中/成功/失败"这类**界面**概念，
 *   `DailyCodeState` 表达**业务**四态。两层分开，避免把"加载中"混进业务模型。
 */
class DailyCodeHolder(
    private val repo: DailyCodeRepository,
    private val scope: CoroutineScope,
) {
    var state: UiState<DailyCodeState> by mutableStateOf(UiState.Loading)
        private set

    /** 是否已至少加载过一次（用于区分"首次加载"与"手动刷新"） */
    var hasLoaded: Boolean by mutableStateOf(false)
        private set

    /**
     * 是否有一次刷新正在进行。
     *
     * ★ 用于**防止自动刷新与手动刷新撞在一起**：
     *   用户点「刷新」的瞬间正好到点，两次请求会同时飞出去。
     *   数据源是积分制的，重复请求是实打实的浪费。
     */
    private var inFlight: Boolean = false

    init {
        refresh()
    }

    fun refresh(today: LocalDate = LocalDate.now(ZoneId.systemDefault())) {
        // 已在刷新中就不再叠加一次（自动刷新与手动刷新共用这个入口）
        if (inFlight) return
        inFlight = true
        scope.launch {
            state = UiState.Loading
            // 仓库已经保证不抛异常（所有失败都归为 DailyCodeState.Failed），
            // 但这里仍不做 try/catch —— 若真抛了，那是仓库的 bug，应当暴露而不是吞掉。
            val result = repo.load(today)
            state = UiState.Success(result)
            hasLoaded = true
            inFlight = false
        }
    }
}

/** 组合期构造。使用方（工具页面）无需知道 Repository 怎么来的。 */
@Composable
fun rememberDailyCodeHolder(): DailyCodeHolder {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember(context) { DailyCodeHolder(DailyCodeGraph.repository(context), scope) }

    // ★ 自动刷新：观察全局 tick。
    //   tick 是 Compose 可观察状态，到点会自动重组并进入这个 LaunchedEffect。
    //   ★ 初值 0 时也会执行一次 composition —— 那正好等于 Holder.init 里的首次加载，
    //     所以这里用 `if (tick > 0)` 跳过，避免**开局就请求两次**。
    val tick = com.utilitybox.core.settings.AutoRefreshState.tick
    LaunchedEffect(tick) {
        if (tick > 0L) holder.refresh()
    }
    return holder
}
