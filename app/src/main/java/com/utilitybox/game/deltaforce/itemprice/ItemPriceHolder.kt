package com.utilitybox.game.deltaforce.itemprice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.utilitybox.core.navigation.UbNavigator
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.game.deltaforce.DeltaForceRoutes
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.ItemPriceDetailState
import com.utilitybox.domain.model.ItemSummary
import com.utilitybox.domain.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 跨导航保留的搜索状态（P11 补 53）。
 *
 * ### 用户报的问题
 *
 * > 「搜索出结果后，我点击进去再返回来的时候，上一次的搜索状态都没了」
 *
 * ### 为什么丢
 *
 * `ItemPriceScreen` 里是 `val holder = rememberItemListHolder()` ——
 * holder 由 `remember` 创建，**离开组合就被销毁**，`keyword` 和 `state` 一起没了。
 *
 * ### ★ 为什么不是"把 holder 缓存起来复用"
 *
 * 那是更直觉的做法，但会踩一个更隐蔽的坑：
 * holder 里的 `searchJob` 跑在 `rememberCoroutineScope()` 上，
 * 而那个 scope **在离开组合时会被取消**。
 * 复用旧 holder = 复用一个**已取消的 scope** ——
 * 表现就是"返回后能看见上次结果，但**再打字完全没反应**"，
 * 比原来的问题更难查。
 *
 * ### 所以：**只把状态提出来，holder 每次照常新建**
 *
 * holder 新建 → 拿到**新的、有效的** scope → 打字正常；
 * 而 `keyword` / `state` 读写的都是这里的进程级快照状态 → 返回后内容还在。
 *
 * ★ 用 `mutableStateOf` 而不是普通 var：Compose 读 `holder.keyword` 时
 *   实际读的是这里的快照状态，**订阅关系仍然成立**，返回后会正常重组。
 */
private object ItemSearchMemory {
    var keyword: String by mutableStateOf("")
    var state: UiState<List<ItemSummary>> by mutableStateOf(UiState.Empty)

    /** 清空"这次搜索"的状态。**不动搜索历史** —— 那是另一份数据，用户要留下它 */
    fun clear() {
        keyword = ""
        state = UiState.Empty
    }
}

/**
 * 物价页被销毁时决定：保留还是清空搜索状态（P11 补 54）。
 *
 * ### 用户要求（两条，看起来矛盾，其实要区分）
 *
 * > 「搜索出结果后，我点击进去再返回来的时候，上一次的搜索状态都没了」← 希望**保留**
 * > 「如果在物价搜索有搜索状态时候，**返回到主界面**后应该清空状态」← 希望**清空**
 *
 * 差别在于**去了哪**：
 *
 * | 去向 | 期望 | 为什么 |
 * |---|---|---|
 * | 详情页（工具内部） | **保留** | 用户是去看某个物品，回来还要接着看列表 |
 * | 主界面（离开工具） | **清空** | 已经离开这个工具了，下次进来应该是干净的开始 |
 *
 * ### ★ 怎么判断"去了哪"
 *
 * `onDispose` 只告诉"我要没了"，不告诉"我要去哪"。
 * 但 [UbNavigator.currentRoute] 是**同步更新**的 ——
 * `navigate()` 改完路由才触发重组，所以销毁时它**已经是新路由**，判断可靠。
 */
fun onItemPriceScreenDisposed() {
    val route = UbNavigator.currentRoute
    val stillInsideTool = route == DeltaForceRoutes.ITEM_PRICE ||
        route == DeltaForceRoutes.ITEM_DETAIL
    if (!stillInsideTool) ItemSearchMemory.clear()
}

/**
 * 物品列表 / 搜索的状态持有者。
 *
 * ★ 设计稿要求「名称搜索（**防抖**）」—— 防抖在这里实现：
 *   每次关键字变化取消上一个待执行的搜索协程，延迟 [DEBOUNCE_MS] 后才真正发请求。
 *   放在这一层而不是 Repository：时间控制属于**交互**职责，不是数据职责。
 *
 * ★ 状态实际存放在 [ItemSearchMemory]，本类只做转发 —— 见那里的说明。
 */
class ItemListHolder(
    private val repo: ItemPriceRepository,
    private val scope: CoroutineScope,
) {
    var keyword: String
        get() = ItemSearchMemory.keyword
        private set(value) {
            ItemSearchMemory.keyword = value
        }

    var state: UiState<List<ItemSummary>>
        get() = ItemSearchMemory.state
        private set(value) {
            ItemSearchMemory.state = value
        }

    private var searchJob: Job? = null

    fun onKeywordChange(value: String) {
        keyword = value
        searchJob?.cancel()

        if (value.isBlank()) {
            state = UiState.Empty
            return
        }
        searchJob = scope.launch {
            // 防抖：用户还在打字时不要发请求
            delay(DEBOUNCE_MS)
            state = UiState.Loading
            when (val r = repo.search(value)) {
                is DataResult.Ok -> {
                    state = if (r.value.isEmpty()) UiState.Empty else UiState.Success(r.value)
                    // ★ P11 补 53：**搜索成功之后**才记进历史。
                    //
                    //   为什么不是"打字停止就记"：那样用户打了一半的字
                    //   （`钛合` 还没打完）、或者打错导致零结果，都会进历史 —— 那是污染。
                    //   只在**真的有结果**时记录，历史里每一条都是"搜到过东西的词"。
                    if (r.value.isNotEmpty()) ItemSearchHistory.record(value)
                }
                is DataResult.Err -> state = UiState.Failed(r.error)
                is DataResult.Unavailable -> state = UiState.Failed(AppError.SourceUnavailable)
            }
        }
    }

    /** 立即重试当前关键字（不做防抖延迟） */
    fun retryNow() {
        val kw = keyword
        if (kw.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            state = UiState.Loading
            when (val r = repo.search(kw)) {
                is DataResult.Ok -> {
                    state = if (r.value.isEmpty()) UiState.Empty else UiState.Success(r.value)
                    if (r.value.isNotEmpty()) ItemSearchHistory.record(kw)
                }
                is DataResult.Err -> state = UiState.Failed(r.error)
                is DataResult.Unavailable -> state = UiState.Failed(AppError.SourceUnavailable)
            }
        }
    }

    companion object {
        /** 防抖窗口。300ms 是"打字停顿"与"响应及时"之间的常用平衡点。 */
        const val DEBOUNCE_MS = 300L
    }
}

/**
 * 物品详情的状态持有者。
 *
 * 详情页要打两个端点（当前价 + 历史），仓库已把它们编排成一个状态，
 * 这里只负责触发与持有。
 */
class ItemDetailHolder(
    private val repo: ItemPriceRepository,
    private val scope: CoroutineScope,
    private val summary: ItemSummary,
) {
    var state: ItemPriceDetailState by mutableStateOf(ItemPriceDetailState.Idle)
        private set

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            // 重试时回到 Idle 会闪一下空态；保留上一次结果更稳，
            // 但如果当前是失败态则应清掉，避免"旧错误 + 新加载"混在一起。
            if (state is ItemPriceDetailState.Failed) state = ItemPriceDetailState.Idle
            state = repo.detail(summary)
        }
    }
}

@Composable
fun rememberItemListHolder(): ItemListHolder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context) { ItemListHolder(ItemPriceGraph.repository(context), scope) }
}

/**
 * 详情页持有者。
 *
 * ★ [summary] 由导航以参数形式传入（当前实现：`MainActivity` 里保存选中的物品）。
 *   这里的 `remember(summary)` 保证切换物品时会重建持有者并重新加载。
 */
@Composable
fun rememberItemDetailHolder(summary: ItemSummary): ItemDetailHolder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, summary.objectId) {
        ItemDetailHolder(ItemPriceGraph.repository(context), scope, summary)
    }
}
