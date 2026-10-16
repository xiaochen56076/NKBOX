package com.utilitybox.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.LocalUbMotion
import com.utilitybox.core.navigation.UbNavigator
import com.utilitybox.core.navigation.UbRoutes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 游戏标签的**左右滑动切换**（P11 用户要求）。
 *
 * > 用户原话：「主界面的应该是可以左右相互滑动的来切换的三角洲和鸣潮之间的界面」
 *
 * ## 为什么用 [HorizontalPager] 而不是自己写手势
 *
 * 分页滑动有几个不容易做对的细节，官方 pager 都已经处理好了：
 * - **速度阈值 + 惯性**：甩一下能翻页，慢慢拖一半松手会回弹
 * - **边缘回弹**：第一页往右拖、最后一页往左拖时有正确的阻尼
 * - **与子元素滚动的冲突**：只接管横向手势，纵向滚动仍归列表
 * 自己实现这些要写不少代码，而且很难做得一样顺。
 * `HorizontalPager` 属于 `compose.foundation`，**没有引入新依赖**。
 *
 * ## ★ 关键的三个设计决定
 *
 * ### 1. 只有**根级游戏标签**可滑，二级页面不可滑
 *
 * 二级页面（工具页、设置各页）不做分页 ——
 * 横向手势在那里会和返回手势、以及图表/横向筛选条的拖拽**冲突**。
 * 所以本组件只在两个游戏标签之间生效，其余页面走原来的单页渲染。
 *
 * ### 2. 滑动**不触发导航转场**（否则两层动画打架）
 *
 * 滑动时页面位置已经由 pager 跟手渲染了。如果结束时再让导航器放一次
 * "新页从右滑入"的转场，就会**再滑一次** —— 观感很乱。
 * 因此结束时调用 [UbNavigator.syncTab]（只同步状态，不产生转场）。
 *
 * ### 3. 点击标签与滑动**双向同步**
 *
 * - 点底部标签 → 导航器 current 变化 → pager `animateScrollToPage` 跟过去
 * - 滑动翻页 → pager 停下来 → `syncTab` 把状态写回导航器（底部高亮跟着变）
 *
 * 少了任何一边，都会出现"点了标签但页面没动"或"滑过去了但底部还亮着上一个"。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamePagerHost(
    navigator: UbNavigator,
    modifier: Modifier = Modifier,
    pageContent: @Composable (gameId: String) -> Unit,
) {
    // ★ 页面顺序就是 tab 顺序。
    //   与底部导航栏的 items 顺序**必须一致**，否则滑动方向会与标签顺序相反。
    val tabs = GAME_TABS

    /**
     * 挂载时就停在**当前路由对应的那一页**。
     *
     * ★★ 不能永远从第 0 页开始（P11 实测）：从「我的」点「鸣潮」时 pager 是全新挂载的，
     *   若固定 `initialPage = 0`，它会先从三角洲滑到鸣潮 ——
     *   用户看到一次毫无意义的横向闪过，底部滑块也会先跳到三角洲再滑回来。
     */
    val initialIndex = tabs.indexOfFirst { it.route == navigator.current }.coerceAtLeast(0)

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val motion = LocalUbMotion.current

    /**
     * 我们**主动**要滚到哪一页（用户点标签时设定）。
     *
     * ★★ 这个值是为了修掉一个真实 bug（用户反馈：
     *   「点击我的后，再想点击鸣潮的时候就会直接跳到三角洲」）。
     *
     * 因果链：
     * 1. 用户在「我的」页 —— 此时 pager **仍然存在**但不可见，停在第 0 页（三角洲）
     * 2. 用户点「鸣潮」→ `navigator.current = game/wuwa`
     * 3. pager 的 `settledPage` 观察者仍在运行，它此时看到的是**陈旧的第 0 页**
     * 4. 于是把导航状态**覆盖回** `game/delta` —— 用户看到的还是三角洲
     *
     * 修法：记录"我们想去哪一页"，只有 pager 停到的正是我们想去的那一页时，
     * 才认为"这是用户自己滑动的结果"并写回导航状态。
     * 于是第 3 步的陈旧值因 `0 != 1` 被忽略。
     */
    /**
     * ~~`intendedPage`~~ —— **已删除**（P11 补 48）。
     *
     * 它记录「我们上次主动滚到哪一页」，曾被用作"这个落点是不是用户滑出来的"判据。
     * 但**用户手动滑动时它从不更新**，于是「滑回原页」会被误判成"我们自己滚的"、
     * 跳过 `syncTab` → **pager 与 navigator 失去同步** → 之后点那个 tab 没反应
     * （用户真机复现：三角洲 → 左划鸣潮 → 右划回三角洲 → 点鸣潮，切不过去）。
     *
     * 真正需要的判据只有"这一跳是不是程序发起的"，那就是 [pendingTarget]。
     *
     * ★ 它原本要修的「pager 重挂载时用陈旧第 0 页覆盖导航」已由 `drop(1)` 解决 ——
     *   两个机制修同一个 bug，而它自己还带来上面这个新 bug，所以整个移除。
     */

    /**
     * 「我们有一跳程序滚动还没落地」的未决目标页。
     *
     * ★★ P11 补 47：修用户反馈的**致命 bug** ——
     *   「不断快速切换就会开始不断自行在鸣潮和三角洲两个界面中跳转」。
     *
     * ### 成环的因果链（三处代码合起来才看得出来）
     *
     * ```
     * 点 A  → intendedPage = A，animateScrollToPage(A) 开始
     * 点 B  → intendedPage = B     ← 把 A 覆盖掉，而 A 的动画还在飞
     * A 落地 → settledPage = A，此时 intendedPage 已是 B
     *        → 判据 `A != B` 成立 → syncTab(A) → navigator.current = A
     *        → 导航变化又让 pager 滚回 A，与 B 的动画互抢
     *        → 无限来回 ✗
     * ```
     *
     * **病根**：`intendedPage` 是**单个变量**，只记得"最新想去哪"，
     * 于是**区分不出「上一跳的落点」和「用户真的滑到了那里」**。
     * 快速切换让两跳重叠，判据必然误判。
     *
     * ### 修法：程序滚动未落地期间，**落点一律不写回**
     *
     * `pendingTarget != null` 表示"这一页是我让你去的"。此时：
     * · 落点**恰好是目标** → 我们成功了，清空、不写回（导航本来就知道）
     * · 落点**不是目标** → 那是被作废的旧一跳，**跳过且不清空**（等真正的目标落地）
     *
     * 于是被覆盖的旧动画落地时**不会** `syncTab`，环从根上断了。
     *
     * ★ 自愈：`animateScrollToPage` 返回后若仍是我们这一跳，主动清空 ——
     *   避免"目标永远没落地"导致之后用户手动滑动被一直忽略。
     */
    var pendingTarget by remember { mutableStateOf<Int?>(null) }

    // 导航器 → pager：点了标签或代码里切了标签时，把页面滚过去
    // 导航器 → pager：点了标签时把页面滚过去。
    //
    // ★★ 关键：**LaunchedEffect 的 key 必须是 Unit**，不能是 (navigator, pagerState)。
    //
    //   踩过的坑（日志实证）：
    //   ```
    //   nav→pager: route=game/wuwa index=1 currentPage=0   ← 要滚到第 1 页
    //   settled=0  intended=1  nav=game/wuwa              ← 动画还没跑起来
    //                                                       → 被覆盖回 delta ❌
    //   ```
    //   `snapshotFlow { navigator.current }` 在**同步写回导航状态**时也会发出新值，
    //   而 key 里含 `navigator` 会让 effect **重启** ——
    //   重启就把正在进行的 `animateScrollToPage` **取消了**，
    //   于是页面永远停在原页，滚动体感就是"点了没反应/跳回三角洲"。
    //
    //   收集本身是挂起的、不会因为上游发新值而重启；把 key 固定成 Unit 即可。
    LaunchedEffect(Unit) {
        snapshotFlow { navigator.current }.collect { route ->
            val index = tabs.indexOfFirst { it.route == route }
            if (com.utilitybox.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "UbPager",
                    "nav→pager: route=$route index=$index currentPage=${pagerState.currentPage}",
                )
            }
            // index < 0 说明当前不在游戏标签上（例如切到「我的」），不动
            if (index >= 0 && pagerState.currentPage != index) {
                pendingTarget = index            // ★ 补 47：登记"这一跳是我们发起的"
                // ★★ P11 补 47 修「底栏点了没反应，但滑动正常」：
                //
                //   原来这里是**直接调用** `animateScrollToPage` ——
                //   它在 collect 里挂起，于是**整个收集器被这一个动画堵住**：
                //   用户在动画期间点的第二次，其 `navigator.current` 只能排队，
                //   要等前一个动画跑完才被处理 → 表现就是"点了像死了一样"。
                //   而滑动走的是 pager 自己的手势通道，不经过这条链路，所以照样能用 ——
                //   这正是用户描述的「点不动，只能滑动」。
                //
                //   更糟的情形：万一动画被取消/迟迟不返回，collect 会**永久挂起**，
                //   此后点击彻底失效。
                //
                //   现在放进独立协程：收集器立刻返回、能马上处理下一次点击；
                //   而 `animateScrollToPage` 内部用 MutatorMutex 串行化 ——
                //   新的一跳会**自动取消**旧的一跳，正是快速连点想要的行为。
                launch {
                    pagerState.animateScrollToPage(index)
                    // 自愈：动画正常结束后若仍是我们这一跳，交还控制权
                    if (pendingTarget == index) pendingTarget = null
                }
            }
        }
    }

    // pager → 导航器：pager 停稳后把状态写回去。
    //
    // ★★ 三处设计各修一个真实问题：
    //
    //   1. **只在停稳后写**（用 `settledPage`）：拖动过程中反复改导航状态会让
    //      底部高亮乱跳，而且会破坏返回栈。
    //
    //   2. **用 [intendedPage] 做归属判定**：区分"用户自己滑过来的"和
    //      "我们主动滚过去的"。少了这一步，不可见的 pager 会用陈旧页号
    //      覆盖用户的选择（就是「我的 → 点鸣潮却跳到三角洲」那个 bug）。
    //
    //   3. **用 `targetPage` 驱动实时高亮**（见下方 `LiveTabSync`）：
    //      `settledPage` 要等动画完全结束才变，导致 tab 比页面慢一拍。
    // pager → 导航器：**用户滑动**停稳后把状态写回去。
    //
    // ★★ 为什么要跳过第一个值（这是「我的 → 点鸣潮却跳到三角洲」的真正病根）：
    //
    //   pager 只在游戏标签路由下挂载。用户点「我的」时它被卸载，
    //   再点「鸣潮」时它**全新挂载**，`rememberPagerState` 从第 0 页开始。
    //   而 `snapshotFlow` 一挂载就立刻发出**当前值 0** ——
    //   这个 0 不是用户滑出来的，是"刚出生"的默认值。
    //   它把用户刚点的「鸣潮」覆盖成「三角洲」，于是永远回不到鸣潮。
    //
    //   `drop(1)` 丢掉这个出生值。之后每次变化都是真正的滑动结果。
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page ->
                // ★★ P11 补 47：**程序滚动未落地期间，落点一律不写回导航**。
                //
                //   这是「快速切换时自行来回跳」的正解：
                //   被新意图覆盖的旧动画落地时，绝不能拿它去 syncTab，
                //   否则会把导航写回旧页 → 触发反向滚动 → 和另一跳互抢 → 成环。
                //
                //   · 落点 == 目标 → 我们成功了，清空标记、交还控制权
                //   · 落点 != 目标 → 是作废的旧一跳，跳过**且不清空**，等真正的目标落地
                val target = pendingTarget
                if (target != null) {
                    if (page == target) pendingTarget = null
                    return@collect
                }
                // ★★ P11 补 48：这里**不再用 `intendedPage` 做判据** ——
                //   它是「我们上次主动滚到哪一页」，但用户**手动滑动时它从不更新**，
                //   于是滑回原页时会被误判成"这是我们自己滚的"，**同步被跳过**。
                //
                // ### 用户给出的复现步骤（真机）
                // ```
                // ① 起始在三角洲 → 左划到鸣潮
                //    settledPage=1，intendedPage=0 → 1!=0 → syncTab(鸣潮) ✓
                // ② 右划回三角洲
                //    settledPage=0，intendedPage 仍是 0 → 0!=0 **不成立** → 跳过同步 ✗
                //    → pager 在三角洲，而 navigator.current 还停在「鸣潮」——**失去同步**
                // ③ 点「鸣潮」tab
                //    → switchTab("game/wuwa")，但 current 本来就是 wuwa
                //    → **值没变 → snapshotFlow 不发射 → pager 不动**
                //    → 表现就是「点了没反应」✗
                // ```
                //
                // ### 正确的判据只有一个：**这一跳是不是程序发起的**
                //
                // 那就是 [pendingTarget]：非空 = 我们正在滚，落点不写回；
                // 空 = 没有任何程序滚动在途，这个落点是**用户滑出来的**，必须写回。
                //
                // ★ `intendedPage` 原本是为了修「pager 重挂载时用陈旧第 0 页覆盖导航」，
                //   但那个问题**已由 `drop(1)` 解决**（丢掉出生值）。
                //   两个机制修同一个 bug，而 `intendedPage` 本身带着上面这个新 bug ——
                //   所以它现在是**冗余且有害的**，不再参与判断。
                tabs.getOrNull(page)?.let { navigator.syncTab(it.route) }
            }
    }

    // ★★ 实时同步：把 pager 的**连续位置**原样交给底部导航栏的滑块。
    //
    //   用户原话：「tab 和页面应该是同步的，而不是工具页面滑动再 tab 动的」。
    //
    //   前一版这里是"过阈值就 `base ± 1`"的**离散高亮**，是错的，两个原因：
    //
    //   1. **中途来回跳**。`currentPage` 是四舍五入后的页号，
    //      拖过 50% 时它从 0 变 1、`currentPageOffsetFraction` 由正转负，
    //      `base - 1` 于是把高亮算**回源标签**。
    //      真机录屏逐帧实测（120fps + 模板匹配测页面真实位移）抓到的序列是
    //      「鸣潮 → 三角洲 → 鸣潮」的闪跳。
    //   2. **不可能同步**。离散值只能整块闪现：实测页面已滑到 73% 时药丸还在源标签。
    //
    //   连续位置 = `currentPage + currentPageOffsetFraction`：
    //   前者是最近页号、后者是它到吸附点的有符号偏移（范围 ±0.5），
    //   两者相加在翻页瞬间**连续**（0 页 +0.6 会写成 1 页 −0.4），
    //   所以拿它驱动滑块不会在过半处跳变。
    //
    //   `coerceIn` 处理边界回弹（第一页往右拖时位置会小于 0）。
    LaunchedEffect(Unit) {
        snapshotFlow {
            pagerSlideFraction(
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                lastIndex = tabs.lastIndex,
            )
        }
            .distinctUntilChanged()
            .collect { navigator.updateTabSlideFraction(it) }
    }

    // pager 只挂在游戏标签路由下。离开时把滑块交还给底栏的离散索引，
    // 否则「我的」页会继续沿用最后一次的连续位置。
    DisposableEffect(Unit) {
        onDispose { navigator.updateTabSlideFraction(null) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 手指拖动时**不要**抢纵向列表的滚动：pager 只接管横向分量，
            // 这是它的默认行为，这里不额外配置，避免过度干预
        ) { page ->
            tabs.getOrNull(page)?.let { tab -> pageContent(tab.gameId) }
        }
    }

    // 记录一下动效令牌，避免"减少动态效果"开启时滚得花哨 ——
    // animateScrollToPage 本身没有时长参数，这里仅作为语义说明保留引用。
    @Suppress("UNUSED_EXPRESSION")
    motion
}

/**
 * 可滑动的游戏标签。
 *
 * ★ 顺序必须与底部导航栏一致：三角洲在左、鸣潮在右。
 *   顺序错了会导致"往左滑却到了右边的标签"这种反直觉效果。
 */
private val GAME_TABS = listOf(
    GameTab(route = UbRoutes.GAME_DELTA, gameId = "deltaforce"),
    GameTab(route = UbRoutes.GAME_WUWA, gameId = "wuwa"),
)

private data class GameTab(val route: String, val gameId: String)

/** 当前路由是否属于"可滑动的游戏标签" */
fun isGameTabRoute(route: String): Boolean = GAME_TABS.any { it.route == route }

/**
 * pager 状态 → 底部滑块位移比例（`0.0` = 第 1 个标签 … `lastIndex` = 最后一个）。
 *
 * ★★ 抽成**纯函数**是为了能被单测钉住 —— 这里曾经算错过，
 *   而算错的表现只有"肉眼觉得 tab 不跟手"，靠人工看很难稳定复现。
 *
 * ### 为什么不能直接用 `currentPageOffsetFraction` 判方向
 *
 * 前一版写的是「`offset > 0.15` 就高亮 `base + 1`，`offset < -0.15` 就 `base - 1`」。
 * 两个坑叠在一起：
 *
 * 1. `currentPage` 是**四舍五入**后的页号 —— 拖过 50% 它就从 0 变成 1，
 *    同时 `currentPageOffsetFraction` 由 `+0.35` 翻成 `-0.27`。
 *    于是 `base - 1` 把高亮算**回源标签**，实测出现
 *    「鸣潮 → 三角洲 → 鸣潮」的闪跳。
 * 2. 就算阈值调对，**离散值也不可能同步**：只能整块闪现。
 *    实测页面已滑到 73% 时，药丸还停在源标签。
 *
 * ### 正确做法
 *
 * `currentPage + currentPageOffsetFraction` 在翻页瞬间是**连续**的：
 * 第 0 页 `+0.6` 会被 Compose 表达成第 1 页 `-0.4`，两者相加都得 `0.6`。
 * 所以拿它驱动滑块，过半处不会跳变，而且天然跟手。
 *
 * `coerceIn` 处理两端回弹：第一页往右拖时相加会小于 0，最后一页往左拖会大于 `lastIndex`。
 *
 * @param currentPage [PagerState.currentPage]，**最近**的页号（已四舍五入）
 * @param currentPageOffsetFraction [PagerState.currentPageOffsetFraction]，范围 ±0.5
 * @param lastIndex 最后一页的下标（两个标签时是 1）
 */
fun pagerSlideFraction(
    currentPage: Int,
    currentPageOffsetFraction: Float,
    lastIndex: Int,
): Float {
    if (lastIndex <= 0) return 0f
    return (currentPage + currentPageOffsetFraction).coerceIn(0f, lastIndex.toFloat())
}
