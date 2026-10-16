package com.utilitybox.core.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 自研轻量导航（P01 已实测可行，零新增依赖）。
 *
 * 为什么不用官方库：
 *  - P01 实测 `navigation-compose 2.10.2` 会把 `compose.runtime` 拉高到 1.11.0 并额外要求
 *    compileSdk 37，引入成本与耦合都更高；
 *  - 首期 9 个页面、路由结构简单，自维护回退栈完全够用；
 *  - 工具注册机制需要"模块自治注册"能力，自研方案更直接。
 *
 * 设计稿要求「不得重写整个导航系统即可新增工具」——本实现满足：
 * 新增工具只需在 [UbNavHost] 的 `when` 中加一个分支 + 注册 ToolDescriptor，
 * 根脚手架（底部导航）与首页无需改动。
 */
class UbNavigator(initialRoute: String) {

    init {
        // ★ P11 补 54：登记"最近创建的那个导航器"，供 [currentRoute] 使用。
        //   见 companion 里的说明。
        latestInstance = this
    }

    var current by mutableStateOf(initialRoute)
        private set

    private val backStack = mutableListOf(initialRoute)

    /**
     * 导航栈深度。
     *
     * ★★ 必须是 **Compose 可观察状态**（P11 真机实测踩到的严重 bug）。
     *
     * 原先 [canGoBack] / [isRootTab] 是从**普通的 `mutableListOf`** 计算的：
     *
     * ```kotlin
     * private val backStack = mutableListOf(initialRoute)   // 不是状态
     * val canGoBack get() = backStack.size > 1
     * ```
     *
     * 而 `BackHandler(enabled = navigator.canGoBack)` 在组合期读它时，
     * **Compose 不会把这个读操作记为依赖** —— 于是这个值永远不触发重组，
     * 返回键的启用状态会停在某个过期值上。
     *
     * 后果（用户真机反馈）：进入二级页面后按系统返回，返回键**没有被拦截**，
     * 事件交给系统 → **直接退出应用**。
     *
     * 这与 P09 修工具启停时的 `isToolDisabled()` 是**同一类 bug**：
     * **把状态藏在普通 getter 后面，重组就会静默失效。**
     */
    private var stackDepth by mutableStateOf(1)

    /** 是否为根级页面（决定是否显示返回按钮、底部导航是否可见） */
    val isRootTab: Boolean
        get() {
            // 读一下可观察状态，建立重组依赖（勿删）
            @Suppress("UNUSED_EXPRESSION") stackDepth
            return current in ROOT_TABS
        }

    val canGoBack: Boolean
        get() {
            // 同上：必须读可观察状态，否则 BackHandler 的 enabled 不会更新
            @Suppress("UNUSED_EXPRESSION") stackDepth
            return stackDepth > 1
        }

    /**
     * 最近一次导航的**方向**。
     *
     * ★★ 为什么需要它（P11 用户反馈「转场杂乱无章」）：
     *
     *   原先 [UbNavHost] 的转场**完全不区分方向** —— 无论前进还是返回，
     *   都播放同一个"新页从右滑入、旧页往左滑出"的动画。
     *   于是**按返回键时，页面仍然从右边进来**，与用户的动作预期相反。
     *   注释里写着"由调用方决定的简化处理"，但那个逻辑从来没有实现。
     *
     *   iOS 的惯例是：
     *   - **进入下一级** → 新页从**右**滑入，旧页往左退
     *   - **返回上一级** → 新页从**左**滑入（即当前页往**右**滑出）
     *
     *   方向必须由**导航动作**决定，而不是由页面自己猜。
     */
    var lastDirection: NavDirection by mutableStateOf(NavDirection.Forward)
        private set

    /**
     * 底部导航**滑块**的连续位置（0.0 = 第 1 个游戏标签 … 1.0 = 第 2 个）。
     *
     * `null` = 当前没有 pager 在驱动滑块（例如停在「我的」页），
     * 此时底栏退回离散的选中索引。
     *
     * ★★ 为什么这里是**连续值**，而不是"高亮哪个路由"
     *   （P11 用户反馈：「tab 和页面应该是同步的，而不是页面滑动了 tab 才动」）：
     *
     *   前一版把高亮做成**离散路由** —— 判据是
     *   `currentPageOffsetFraction` 超过 ±0.15 就 `base ± 1`。
     *   录屏逐帧实测（真机 120fps，模板匹配测页面真实位移）暴露两个问题：
     *
     *   1. **中途来回跳**。`currentPage` 是**四舍五入**后的页号：
     *      拖过 50% 时它从 0 变成 1，同时 `currentPageOffsetFraction`
     *      由 `+0.35` 翻成 `-0.27` —— 于是 `base - 1` 把高亮算
     *      **回源标签**。实测序列是「鸣潮 → 三角洲 → 鸣潮」的闪跳。
     *   2. **不可能同步**。离散值只能整块闪现：实测
     *      「页面已滑到 73%，药丸仍停在源标签」。
     *
     *   改成连续值后两个问题一起消失：页面滑到 73%，滑块就在 73% 处。
     */
    var tabSlideFraction: Float? by mutableStateOf(null)
        private set

    /**
     * 只改滑块位置，**不动导航状态**。
     *
     * 专供 pager 在滑动过程中调用：此时用户还没松手，
     * 任何对返回栈的改动都可能在他反悔（拖回原页）后留下错误痕迹。
     *
     * ★ 名字不能叫 `setTabSlideFraction` —— 那会和 [tabSlideFraction] 的
     *   JVM setter 撞签名（`setTabSlideFraction(Float)`），编译期直接失败。
     *   同理，传 `null` 表示"交还控制权"（pager 卸载时调用）。
     */
    fun updateTabSlideFraction(fraction: Float?) {
        tabSlideFraction = fraction
    }

    fun navigate(route: String) {
        if (route == current) return
        backStack.add(route)
        current = route
        stackDepth = backStack.size
        lastDirection = NavDirection.Forward
    }

    /**
     * 切换根标签：清掉中间栈，避免"首页→A→首页"堆积。
     *
     * ★ 方向恒为 [NavDirection.Forward]。
     *   标签之间没有"层级"关系，用侧滑会让人以为它们有上下级；
     *   但也不该左右乱滑 —— 统一按"前进"处理，配合淡入淡出更自然。
     */
    fun switchTab(route: String) {
        backStack.clear()
        backStack.add(route)
        current = route
        stackDepth = backStack.size
        lastDirection = NavDirection.Forward
    }

    /**
     * 只改当前路由，**不做任何转场**。
     *
     * ★★ 专供「左右滑动切换游戏 tab」（P11 用户要求）：
     *
     *   滑动时页面位置已经由 `HorizontalPager` 自己跟手渲染了，
     *   如果这里再触发一次导航转场，就会出现**两层动画打架** ——
     *   手指拖动的同时页面还要再滑一次，观感很乱。
     *
     *   所以滑动结束时只需要把"当前是哪个标签"同步给导航器
     *   （好让底部导航栏的高亮跟着变），**不要**产生新的转场。
     *
     * 与 [switchTab] 的区别：那个是"点击标签"，需要一次方向明确的转场；
     * 这个是"滑动已经完成，补登记状态"。
     */
    fun syncTab(route: String) {
        if (route == current) return
        backStack.clear()
        backStack.add(route)
        current = route
        stackDepth = backStack.size
        // 方向不动：滑动没有"前进/返回"的语义，
        // 而且此时页面已经渲染到位，方向也不会被用到。
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        current = backStack.last()
        stackDepth = backStack.size
        lastDirection = NavDirection.Backward
        return true
    }

    companion object {
        /**
         * 当前路由的**进程级只读快照**（P11 补 54）。
         *
         * ### 为什么需要它
         *
         * 有些清理只能在**页面被销毁时**决定去留。典型场景：
         *
         * > 物价页有搜索状态时 → 去**详情页**再返回来，要**保留**；
         * > 回**主界面**（离开这个工具），要**清空**。
         *
         * 而 `DisposableEffect` 的 `onDispose` 拿不到"我要去哪" ——
         * 它只知道"我要没了"。此时必须问：新路由是详情页，还是别处？
         *
         * ### ★ 为什么这样能work
         *
         * `navigate()` / `switchTab()` / `syncTab()` / `back()` 都是**同步**改 `current`，
         * 而 Compose 的重组是之后才发生的。所以页面被销毁时，
         * 这里的值**已经是新路由** —— 判断是可靠的。
         *
         * ### 为什么不是"给每个页面传 navigator"
         *
         * 那会让每个需要这个判断的页面都多一个参数，只为了一件与它无关的事。
         * 只读快照是更轻的耦合。
         *
         * ★ 这里**只读**：任何写入都必须走 navigator 自己的方法，
         *   否则就有了第二个改路由的地方 —— 那正是导航最容易出 bug 的形态。
         */
        @Volatile
        private var latestInstance: UbNavigator? = null

        /** 最近一次导航到的路由。没有导航器时为空串（不会误判为"在某页"）。 */
        val currentRoute: String
            get() = latestInstance?.current ?: ""

        /**
         * 显示悬浮底部导航的根标签页。
         *
         * ★ 直接转发到 [UbRoutes.ROOT_TABS]，**避免两份定义漂移**。
         *   此前这里和 `UbRoutes` 各写了一份集合，
         *   加路由时容易只改一处 → `isRootTab` 判断错误、导航栏不显示。
         *
         * ★ [UbRoutes.GLASS_PROBE] 也在其中 —— 它是**诊断页**，
         *   唯一目的是验证"玻璃有没有在模糊"，因此**必须让导航栏显示出来**。
         *   放在 sub-page 里会导致栏不渲染，诊断页就失去了意义（P10 实测踩到）。
         */
        val ROOT_TABS: Set<String> get() = UbRoutes.ROOT_TABS
    }
}

@Composable
fun rememberUbNavigator(initialRoute: String = UbRoutes.GAME_DELTA): UbNavigator =
    remember { UbNavigator(initialRoute) }

/**
 * 页面转场方向。
 *
 * ★ 用独立的枚举而不是布尔值：将来若要加"横向切换标签"这类第三种方向，
 *   布尔值就表达不了了，届时全项目都要跟着改。
 */
enum class NavDirection {
    /** 进入下一级：新页从右侧滑入 */
    Forward,

    /** 返回上一级：新页从左侧滑入（当前页往右滑出） */
    Backward,
}

/**
 * 导航容器的"转场状态"。
 *
 * ★★ 这是修复「滑动切换游戏标签后高亮不跟着变」的关键（P11 实测踩到）。
 *
 * ## 出问题的因果链
 *
 * `AnimatedContent` 的 `targetState` 原本直接用 `navigator.current`。于是：
 *
 * 1. 用户滑动 → pager 到达第 1 页（鸣潮）
 * 2. 同步 `navigator.current = game/wuwa`
 * 3. `AnimatedContent` 看到 targetState 变了 → **重建内容**
 * 4. `rememberPagerState` 是内容里的状态 → **pager 被重置回第 0 页**
 * 5. 第 4 步又同步 `nav.current = game/delta` → 回到步骤 1，形成**死循环**
 *
 * 表现就是：滑过去后底部高亮仍停在三角洲，点标签也像没反应。
 *
 * ## 修法
 *
 * 两个游戏标签在**转场层面是同一个状态** —— 它们之间的切换由
 * `HorizontalPager` 自己跟手渲染，**不需要 nav host 参与**。
 * 所以把这两个路由折叠成一个常量 [GAME_PAGER] 作为 targetState：
 *
 * - 三角洲 ⇄ 鸣潮：targetState 不变 → **pager 不被重建**，状态得以保留
 * - 进入二级页 / 切到我的：targetState 变化 → 正常播放转场
 *
 * ★ 用常量而不是把两个路由"相等化"：前者只影响转场，导航语义（current 到底是什么）
 *   完全不变 —— 底部高亮、返回栈、面包屑都还能正确区分两个标签。
 */
const val GAME_PAGER = "__game_pager__"

/**
 * 把真实路由映射成"转场状态"。
 *
 * 只有两个可滑动的游戏标签会被折叠；其余路由原样返回。
 * 判定复用了 `feature.home.isGameTabRoute` 的同一份列表，避免两处定义漂移。
 */
fun navHostStateOf(route: String): String =
    if (com.utilitybox.feature.home.isGameTabRoute(route)) GAME_PAGER else route

/**
 * 页面容器：统一转场动画。
 *
 * ★★ 方向感知（P11 修复用户反馈的「转场杂乱无章」）：
 *
 *   - **进入**：新页从**右**滑入，旧页往**左**退并淡出
 *   - **返回**：新页从**左**滑入，旧页往**右**退并淡出
 *
 *   方向取自 [UbNavigator.lastDirection]，由导航动作（navigate / back）决定。
 *   此前两者共用同一个动画，导致"按返回键时页面还从右边进来"。
 *
 * ★★ 游戏标签折叠（见 [GAME_PAGER]）：两个游戏标签在转场层面是同一个状态，
 *   它们之间的切换由 pager 自己负责 —— 否则会互相重置，形成死循环。
 *
 * ★ 转场时长取自 [com.utilitybox.core.design.LocalUbMotion]，
 *   "减少动态效果"开启时时长会被压到 0，从而**自动**关闭转场（无需各页面判断）。
 *   此时只做瞬时切换，不做位移动画 —— 避免"关掉动画却还在滑"的矛盾。
 */
@Composable
fun UbNavHost(
    navigator: UbNavigator,
    modifier: Modifier = Modifier,
    content: @Composable (route: String) -> Unit,
) {
    val motion = com.utilitybox.core.design.LocalUbMotion.current
    val duration = motion.transition
    val direction = navigator.lastDirection

    AnimatedContent(
        // ★ 用折叠后的状态，而不是 navigator.current —— 见 GAME_PAGER 的说明
        targetState = navHostStateOf(navigator.current),
        modifier = modifier,
        transitionSpec = {
            // ★ 缓动纲领（P11 统一）：**进场 Decelerate / 退场 Accelerate**。
            //
            //   来源：`REPORT-base1-apk.md` §9.1 ——
            //   一个成熟商业 App 的实测规范，且特别指出用 3.0f 幂次
            //   （而非默认二次），曲线更「脆」、更有力。
            val easeIn = com.utilitybox.core.design.UbEaseEnter

            if (duration == 0) {
                // 减少动态效果：只做极短的淡入淡出，不做位移
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else if (direction == NavDirection.Forward) {
                // 进入：新页从右边进来，旧页往左退
                (
                    slideInHorizontally(tween(duration, easing = easeIn)) { full -> full } +
                        fadeIn(tween(duration, easing = easeIn))
                    ).togetherWith(
                    // ★★ 旧页**故意不用**报告的 Accelerate（t³）—— 这是踩过一次的：
                    //
                    //   Accelerate(3f) 在 t=0.5 处才走 12.5%，即旧页前半程几乎**不动**。
                    //   报告把这条曲线用在"飞出去就消失"的元素（FAB）上没问题，
                    //   但这里是**新旧两页同时在屏上交叉** ——
                    //   旧页赖在屏幕中间不动，就会与新页叠成一层重影
                    //   （用户反馈：「每次点开某一个界面都有重影」）。
                    //
                    //   所以旧页改成：**位移用减速曲线**（快速让开中心），
                    //   **淡出只用一半时长**（尽早隐形）。让位要果断。
                    slideOutHorizontally(tween(duration, easing = easeIn)) { full -> -full / 3 } +
                        fadeOut(tween(duration / 2, easing = easeIn))
                )
            } else {
                // 返回：新页从左边进来（即当前页往右退出去）
                (
                    slideInHorizontally(tween(duration, easing = easeIn)) { full -> -full / 3 } +
                        fadeIn(tween(duration, easing = easeIn))
                    ).togetherWith(
                    slideOutHorizontally(tween(duration, easing = easeIn)) { full -> full } +
                        fadeOut(tween(duration / 2, easing = easeIn))
                )
            }
        },
        label = "ub-nav",
    ) { state ->
        // ★★ 必须用 AnimatedContent **传进来的这个 `state`**，
        //    绝不能读 `navigator.current`。
        //
        //    踩过的坑（P11 用户反馈「每次点开某一个界面都有重影」）：
        //
        //    转场期间 AnimatedContent 会**同时保留两个槽位**（旧页槽 + 新页槽），
        //    并分别用各自的状态调用这个 lambda。旧实现忽略了参数、改读
        //    `navigator.current` —— 而它在转场开始的瞬间**已经是新路由**了，
        //    于是两个槽位都渲染新页 → 屏幕上同一个页面错位两份 = 重影。
        //
        //    更糟的是当时还把病因写成了理由：
        //    「读 current 会建立重组依赖，所以路由变化时这里也会跟着重组」——
        //    那个"跟着重组"正是两个槽位渲染同一页的原因。
        //
        //    `state` 是**折叠过**的转场状态：
        //    - [GAME_PAGER] → 渲染分页容器；由 pager 自己读 current 决定显示哪个游戏
        //      （这个读发生在 pager **内部**，是正确的：pager 本来就该跟着当前标签）
        //    - 其它 → 就是那个路由本身
        content(state)
    }
}
