package com.utilitybox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.utilitybox.core.design.*
import com.utilitybox.core.navigation.GAME_PAGER
import com.utilitybox.core.navigation.UbNavHost
import com.utilitybox.core.navigation.UbRoutes
import com.utilitybox.core.navigation.rememberUbNavigator
import com.utilitybox.domain.tool.UbToolRegistry
import com.utilitybox.feature.common.UbToolHost
import com.utilitybox.feature.home.GamePagerHost
import com.utilitybox.feature.home.GameToolsScreen
import com.utilitybox.feature.home.isGameTabRoute
import com.utilitybox.feature.home.ProtoStatesScreen
import com.utilitybox.feature.settings.AboutScreen
import com.utilitybox.feature.settings.ApiKeyScreen
import com.utilitybox.feature.settings.AppearanceSettingsScreen
import com.utilitybox.feature.settings.DataSettingsScreen
import com.utilitybox.feature.settings.DataSourceStatusScreen
import com.utilitybox.feature.settings.ExplorePlaceholderScreen
import com.utilitybox.feature.settings.LoginScreen
import com.utilitybox.feature.settings.MineScreen
import com.utilitybox.feature.settings.SettingsHubScreen
import com.utilitybox.feature.settings.ToolManagementScreen
import com.utilitybox.game.deltaforce.DeltaForceRoutes
import com.utilitybox.game.deltaforce.itemprice.ItemDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册表自检与 API Key 初始化已移到 `UbApplication.onCreate`
        // ——那是应用级一次性初始化更合适的位置（不再依赖本 Activity 被创建）。
        setContent { UbApp() }
    }
}

/**
 * 应用根。
 *
 * 职责：① 提供主题令牌；② 计算玻璃档位并下发；③ 组装导航与底部导航。
 *
 * ★ 扩展铁律：新增工具**不需要改本文件**。
 *   根导航按路由查 `UbToolRegistry.providerByRoute(route)` 分发，
 *   因此工具页面不在这里出现；本文件的 `when` 只处理根标签页与二级页面。
 *   新增工具的完整清单见 docs/architecture.md §4.4（已实测验证：公共层 0 改动）。
 */
@Composable
fun UbApp() {
    // ★ 设置来自全局可观察状态（P09），不再用 remember —— 这样重启后保持，
    //   且任何位置改动设置都会立即触发重组。
    val settings = com.utilitybox.core.settings.UbSettingsState.settings
    val themeMode = settings.themeMode
    val glassEnabled = settings.glassEnabled
    val reduceMotion = settings.reduceMotion
    // 清启动页背景要落到文件系统，需要 Context
    val appContext = LocalContext.current.applicationContext

    // 主题色种子：落盘的是 ARGB 整数（领域模型不依赖 Compose），这里转回 Color。
    // null = 不覆盖，跟随主题默认色（见 UbSettings.accentSeed）。
    val accentSeed = remember(settings.accentSeed) {
        settings.accentSeed?.let { ubColorOfArgb(it) }
    }

    fun update(transform: (com.utilitybox.domain.model.UbSettings) -> com.utilitybox.domain.model.UbSettings) {
        com.utilitybox.core.settings.UbSettingsState.update(transform)
    }

    // ★★ 启动页闸门。
    //
    //   ★ 关键取向：**主界面在启动页背后就开始组合**，启动页只是一层盖在上面的遮罩。
    //     这样启动页遮住的是"本来就要等的这段时间"，而**不额外增加**启动耗时。
    //     若写成"启动页结束后才开始组合主界面"，用户实际要多等一个启动页时长 ——
    //     那是把装饰当成了流程，是很常见的实现错误。
    var splashVisible by remember { mutableStateOf(true) }
    val splashAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    // 用户点一下 → 立刻跳过（见 UbSplash 的 onSkip 说明）
    val splashSkipped = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // ★ 停留时间从 600ms 缩到 320ms（P11 补 12）：
        //   启动页的净收益是"遮住本来就有的等待"，而主界面在它背后已经组合完了。
        //   超过这个时长就从"遮住等待"变成"制造等待" —— 用户反馈
        //   「内容显示应该再快些」正是在说这件事。
        val holdMs = if (reduceMotion) 120L else 320L
        // 要么自然到时、要么用户点了 —— 两条路径都继续往下走
        kotlinx.coroutines.withTimeoutOrNull(holdMs) {
            snapshotFlow { splashSkipped.value }.first { it }
        }
        // 退场用全局纲领的 Accelerate：遮罩"加速离开"，把画面交给主界面
        splashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = androidx.compose.animation.core.tween(
                if (reduceMotion) 0 else 240,
                easing = UbEaseExit,
            ),
        )
        splashVisible = false
    }

    UbTheme(
        mode = themeMode,
        reduceMotion = reduceMotion,
        glassEnabled = glassEnabled,
        accentSeed = accentSeed,
    ) {
        // 玻璃档位在此统一计算一次，供全树下发（组件不再各自判断设备能力）
        val tier = rememberGlassTier(reduceMotion = reduceMotion, glassEnabled = glassEnabled)
        CompositionLocalProvider(LocalUbGlassVariantTier provides tier) {
            Box(modifier = Modifier.fillMaxSize()) {
                UbRootShell(
                    themeMode = themeMode,
                    onThemeChange = { m -> update { it.copy(themeMode = m) } },
                    accentSeed = accentSeed,
                    onAccentChange = { col ->
                        // 用户选的颜色落盘为 ARGB 整数；null 表示恢复默认
                        update { it.copy(accentSeed = col?.toArgbLong()) }
                    },
                    splashImagePath = settings.splashImagePath,
                    onSplashPicked = { path ->
                        // ★ 保存失败（path == null）时**不改设置** ——
                        //   绝不能把 null 写进去，那会把用户原来还能用的背景图也清掉。
                        //   界面上的提示由外观页自己负责。
                        if (path != null) update { it.copy(splashImagePath = path) }
                    },
                    onSplashCleared = {
                        // 顺手把私有目录里的图也删掉 —— 只清设置会留下一个永远读不到的文件
                        com.utilitybox.core.settings.SplashImageStore.clear(appContext)
                        update { it.copy(splashImagePath = null) }
                    },
                    glassEnabled = glassEnabled,
                    onGlassChange = { v -> update { it.copy(glassEnabled = v) } },
                    reduceMotion = reduceMotion,
                    onReduceMotionChange = { v -> update { it.copy(reduceMotion = v) } },
                    showGameSwitch = settings.showGameSwitch,
                    onShowGameSwitchChange = { v -> update { it.copy(showGameSwitch = v) } },
                    autoRefreshEnabled = settings.autoRefreshEnabled,
                    onAutoRefreshChange = { v -> update { it.copy(autoRefreshEnabled = v) } },
                    autoRefreshMinutes = settings.autoRefreshMinutes,
                    onAutoRefreshMinutesChange = { m -> update { it.copy(autoRefreshMinutes = m) } },
                )

                if (splashVisible) {
                    UbSplash(
                        imagePath = settings.splashImagePath,
                        reduceMotion = reduceMotion,
                        onSkip = { splashSkipped.value = true },
                        modifier = Modifier.graphicsLayer { alpha = splashAlpha.value },
                    )
                }
            }
        }
    }
}

/**
 * 把应用导航器适配成工具页面能用的 [ToolNavActions]。
 *
 * ★ 这层适配的意义：工具只依赖 [ToolNavActions] 这个**窄接口**，
 *   不知道根导航是自研的还是官方库、也不知道路由表长什么样。
 *   将来换导航实现时，只需要改这一个函数。
 */
@Composable
private fun rememberToolNav(
    navigator: com.utilitybox.core.navigation.UbNavigator,
    onOpenItemDetail: (com.utilitybox.domain.model.ItemSummary) -> Unit,
): com.utilitybox.domain.tool.ToolNavActions =
    remember(navigator) {
        object : com.utilitybox.domain.tool.ToolNavActions {
            override fun openItemDetail(item: com.utilitybox.domain.model.ItemSummary) =
                onOpenItemDetail(item)
        }
    }

/**
 * 子页面外壳：统一提供返回入口与标题排版。
 * 根标签页（首页/设置）不使用它 —— 它们的标题在各自页面内。
 */
@Composable
private fun SubPage(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    com.utilitybox.feature.common.UbToolPage(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) { inner ->
        Box(modifier = inner.fillMaxWidth()) { content() }
    }
}

@Composable
private fun UbRootShell(
    themeMode: UbThemeMode,
    onThemeChange: (UbThemeMode) -> Unit,
    accentSeed: androidx.compose.ui.graphics.Color?,
    onAccentChange: (androidx.compose.ui.graphics.Color?) -> Unit,
    splashImagePath: String?,
    onSplashPicked: (String?) -> Unit,
    onSplashCleared: () -> Unit,
    glassEnabled: Boolean,
    onGlassChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    showGameSwitch: Boolean = true,
    onShowGameSwitchChange: (Boolean) -> Unit = {},
    autoRefreshEnabled: Boolean = true,
    onAutoRefreshChange: (Boolean) -> Unit = {},
    autoRefreshMinutes: Int = 30,
    onAutoRefreshMinutesChange: (Int) -> Unit = {},
) {
    val navigator = rememberUbNavigator()
    val s = LocalUbSpacing.current
    // 选中的物品（P07：详情页需要完整条目，故保存整个 ItemSummary 而不是 id）
    var selectedItem by remember { mutableStateOf<com.utilitybox.domain.model.ItemSummary?>(null) }

    // ★★ 自动刷新定时器（P11 接通）。
    //
    //   挂在这里一处即可，全应用共用：它只负责按设定间隔递增
    //   `AutoRefreshState.tick`，各页面的 Holder 观察到变化后重新拉自己的数据。
    //
    //   这样"什么时候刷"与"刷什么"是分开的：
    //   新增工具时不用碰定时逻辑，只要在它的 Holder 里观察 tick。
    //
    //   ★ 仅在应用处于前台时计时（内部用 repeatOnLifecycle）——
    //     这是对数据设置页上那句「不会在后台常驻请求」的兑现。
    com.utilitybox.core.settings.AutoRefreshEffect(
        com.utilitybox.core.settings.UbSettingsState.settings,
    )

    // ★★ 返回键处理（P11 真机反馈后重写）
    //
    //  原先写的是 `BackHandler(enabled = navigator.canGoBack)`，有两个问题：
    //
    //  1. **严重**：`canGoBack` 当时是从普通 `mutableListOf` 计算的，
    //     Compose 不把它当依赖 → enabled 停在过期值 → 二级页面按返回**直接退出应用**。
    //     （已在 UbNavigator 里改成可观察状态，并加了回归测试）
    //  2. **交互**：即使值正确，`enabled = false` 时返回事件也会直接交给系统
    //     → 首页按一次返回就退出，没有"再按一次"的缓冲。
    //
    //  现在改为**常开**，由这里的逻辑自己决定三种行为：后退 / 提示 / 退出。
    val exitConfirm = rememberExitConfirmState()
    val activity = LocalContext.current as? android.app.Activity
    BackHandler(enabled = true) {
        when {
            // 有历史 → 正常后退
            navigator.canGoBack -> navigator.back()

            // 已在根标签 → 双击退出（第一次只提示）
            else -> if (exitConfirm.onBack()) {
                activity?.finish()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Haze 状态由这一层提供：它同时包含「内容」（要透过玻璃被看到的东西）
        // 与「玻璃表面」（底部导航），这是 Haze 能工作的前提。
        ProvideHazeState {
            // ★★ 结构关键（P10 实测踩到，导致玻璃完全没在模糊）：
            //
            //   内容源码区必须**铺满整个屏幕**，底部导航**覆盖在它上面**。
            //
            //   原先内容是一个 `weight(1f)` 的子项，高度止于导航栏上方 ——
            //   于是 Haze 在栏的位置**采样不到任何像素**（那里是布局之外），
            //   玻璃既没有模糊、也不渲染任何东西。
            //   实测证据：在栏后铺棋盘格时，栏**整个消失了**。
            //
            //   iOS 的悬浮 tab bar 也是这个结构：内容在栏下面继续延伸，
            //   栏只是浮在内容之上的一层玻璃。内容自己的底部留白由各页的
            //   `bottomBarReserved` 提供（那是"阅读时不要被挡住"的问题，
            //   与"玻璃能不能采样"是两件事 —— 之前把它们混为一谈了）。
            UbGlassSource(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    // ★★ 这里的 `route` 是 **AnimatedContent 的槽位状态**，不是 navigator.current：
                    //   转场期间两个槽位各拿一个状态，旧页槽可能是折叠值 GAME_PAGER。
                    //   详见 UbNavHost 内容 lambda 上的说明（它就是「重影」的修复点）。
                    UbNavHost(navigator = navigator, modifier = Modifier.fillMaxWidth()) { route ->
                        // ★ 工具页面统一由注册表按路由分发 —— 这里**没有**任何具体工具的分支。
                        //   新增工具时本文件不需要改（docs/architecture.md §4.4 的验收标准）。
                        // ★ GAME_PAGER 不是真实路由，注册表查不到它 —— 这是预期行为，
                        //   它会落到下面的游戏分支去。
                        val toolProvider = UbToolRegistry.providerByRoute(route)
                        if (toolProvider != null) {
                            UbToolHost(
                                provider = toolProvider,
                                nav = rememberToolNav(navigator) { item ->
                                    selectedItem = item
                                    navigator.navigate(DeltaForceRoutes.ITEM_DETAIL)
                                },
                                onBack = { navigator.back() },
                            )
                        } else if (route == GAME_PAGER || isGameTabRoute(route)) {
                            // ---- 游戏标签：**可左右滑动切换** ----
                            //
                            // ★★ 这两个标签合并进一个 HorizontalPager（P11 用户要求
                            //   「主界面可以左右相互滑动来切换三角洲和鸣潮」）。
                            //
                            //   注意：这里用 `isGameTabRoute(route)` 判断而不是 `when` 分支，
                            //   因为**两个游戏现在是同一个分页容器的两个页面** ——
                            //   若拆成两个 when 分支，滑动时 AnimatedContent 会认为
                            //   "路由变了"而重新进场，与 pager 自己的跟手滑动**打架**。
                            //
                            //   同时这也让"接入第三款游戏"依然不需要改本文件的结构：
                            //   加一个 route 常量 + 在 GamePagerHost 的 GAME_TABS 里加一项即可。
                            //
                            // ★★ P11 补：现在 `route` 是 **AnimatedContent 的槽位状态**，
                            //   转场期间它可能是折叠值 [GAME_PAGER]（不是真实路由）。
                            //   这**正是修掉"重影"的关键** —— 旧页槽拿到 GAME_PAGER、
                            //   新页槽拿到目标路由，两个槽位才会渲染**不同的内容**。
                            //   显示哪个游戏由 GamePagerHost 内部读 navigator.current 决定。
                            GamePagerHost(navigator = navigator) { gameId ->
                                // ★ 页面里依然**不出现任何具体工具名**，工具由注册表按 gameId 提供
                                if (gameId == "wuwa") {
                                    // 鸣潮目前不是"工具网格"，而是**账号数据面板**
                                    // （用户 P11 补 16 裁决：接入库街区）。
                                    // 它由 wutheringwaves 包自己实现，公共层不知道库街区的存在。
                                    com.utilitybox.game.wutheringwaves.WuwaScreen(
                                        onOpenGacha = { navigator.navigate(UbRoutes.WUWA_GACHA) },
                                        onOpenWiki = { navigator.navigate(UbRoutes.WUWA_WIKI) },
                                        // 与三角洲对称：身份卡 + 可选的切换入口
                                        switchToGameId = if (showGameSwitch) com.utilitybox.feature.home.nextGameId(gameId) else null,
                                        onSwitchGame = { targetId ->
                                            // ★ 补 47：**必须走 UbRoutes.gameRoute** ——
                                            //   游戏 id（deltaforce）和路由后缀（delta）不是一回事。
                                            //   原来这里自己拼 "game/$targetId"，
                                            //   拼出 game/deltaforce → 未注册 → 「页面不存在」。
                                            navigator.switchTab(UbRoutes.gameRoute(targetId))
                                        },
                                    )
                                } else {
                                    GameToolsScreen(
                                        gameId = gameId,
                                        onOpenTool = { navigator.navigate(it) },
                                        // ★ 方案 C：身份卡上的"切换到 XX"入口。
                                        // ★ 补 47：~~「路由规律是 game/<gameId>，与 UbRoutes 常量一致，
                                        //   所以新增游戏时这里也不需要改」~~ —— **这句注释是错的**。
                                        //   实测：三角洲的 id 是 `deltaforce`，而路由是 `game/delta`。
                                        //   所以必须经 UbRoutes.gameRoute 转换，不能自己拼。
                                        // ★ 受设置「交互 → 游戏切换入口」控制（用户 P11 补 22 要求）。
                                        onSwitchGame = { targetId ->
                                            navigator.switchTab(UbRoutes.gameRoute(targetId))
                                        },
                                        showSwitch = showGameSwitch,
                                    )
                                }
                            }
                        } else {
                            // ★ 路径带参数的路由（图鉴的三级）必须用前缀匹配，
                            //   而 `when (route) { route.startsWith(...) -> }` **不是合法 Kotlin** ——
                            //   带主语的 when 里分支必须与主语同类型（String），Boolean 条件会报
                            //   "Incompatible types 'String' and 'Boolean'"。
                            //   所以这三条走 if/else，落到 else 才进原来的 when。
                            val wikiEntryId = route
                                .takeIf { it.startsWith(UbRoutes.PREFIX_WIKI_ENTRY) }
                                ?.removePrefix(UbRoutes.PREFIX_WIKI_ENTRY)
                            val wikiCatId = route
                                .takeIf { it.startsWith(UbRoutes.PREFIX_WIKI_CAT) }
                                ?.removePrefix(UbRoutes.PREFIX_WIKI_CAT)

                            if (wikiEntryId != null) {
                                SubPage(title = "图鉴词条", onBack = { navigator.back() }) {
                                    // ★ P11 补 64：**原生 Compose 渲染**，不再用 WebView 装官方 HTML。
                                    //
                                    //   用户明确要求：「重写显示信息的界面，不要用 wiki 的那种」
                                    //   「采用苹果设计风格，相当于你要把显示框架自己做一个」。
                                    //
                                    //   `fallback` 是白名单一个都没命中时的退路 ——
                                    //   宁可样式差一点，也不能给用户一个空白页。
                                    com.utilitybox.game.wutheringwaves.WuwaWikiEntryNativeScreen(
                                        entryId = wikiEntryId,
                                        fallback = {
                                            com.utilitybox.game.wutheringwaves.WuwaWikiDetailScreen(
                                                entryId = wikiEntryId,
                                            )
                                        },
                                    )
                                }
                            } else if (wikiCatId != null) {
                                // ★ P11 补 45：哨兵 id 表示「角色与武器」合并列表。
                                //   wikiCatId 是从路由里取出的 **String**，所以比字符串。
                                if (wikiCatId == com.utilitybox.game.wutheringwaves.MERGED_CHAR_WEAPON.toString()) {
                                    SubPage(title = "角色与武器", onBack = { navigator.back() }) {
                                        com.utilitybox.game.wutheringwaves.WuwaWikiCharWeaponScreen(
                                            onOpenEntry = { entryId, _ ->
                                                navigator.navigate(UbRoutes.wuwaWikiEntry(entryId))
                                            },
                                        )
                                    }
                                } else {
                                    // ★ P11 补 63：标题**按分类走**，不能写死「图鉴」。
                                    //
                                    //   用户明确指出「这里是养成，不是图鉴」——
                                    //   从「角色养成」进来时顶栏写着「图鉴」是错的。
                                    //
                                    //   ★ 上一轮我在这一行**只加了注释、没改代码**，
                                    //     而注释写得像已经改过了。注释不能代替改动，
                                    //     这次是真改了。
                                    //
                                    //   为什么用 when(id) 而不是把名字塞进路由：
                                    //   路由只带 id，往里塞中文名字要编码、还要处理冲突；
                                    //   而这里只需要区分"是不是养成"，一个判断就够。
                                    val wikiTitle = when (wikiCatId.toIntOrNull()) {
                                        com.utilitybox.game.wutheringwaves.CAT_RESONATOR -> "角色养成"
                                        else -> "图鉴"
                                    }
                                    SubPage(title = wikiTitle, onBack = { navigator.back() }) {
                                        com.utilitybox.game.wutheringwaves.WuwaWikiEntriesScreen(
                                            catalogueId = wikiCatId.toIntOrNull() ?: 0,
                                            title = "图鉴",
                                            onOpenEntry = { entryId, _ ->
                                                navigator.navigate(UbRoutes.wuwaWikiEntry(entryId))
                                            },
                                            // ★ P11 补 60：**子分类**（如「养成」下的 59 个角色）。
                                            //   复用分类路由，只是换成子分类自己的 catalogueId ——
                                            //   它来自 `content.linkConfig.catalogueId`，
                                            //   **不是**记录的 `id`（拿后者查会返回 0 条，已实测）。
                                            onOpenCategory = { id, _ ->
                                                navigator.navigate(UbRoutes.wuwaWikiCategory(id))
                                            },
                                        )
                                    }
                                }
                            } else when (route) {
                                UbRoutes.WUWA_WIKI -> SubPage(
                                    title = "鸣潮图鉴",
                                    subtitle = "内容来自库街区 WIKI，无需登录",
                                    onBack = { navigator.back() },
                                ) {
                                    com.utilitybox.game.wutheringwaves.WuwaWikiCategoriesScreen(
                                        onOpenCategory = { id, _ ->
                                            navigator.navigate(UbRoutes.wuwaWikiCategory(id))
                                        },
                                    )
                                }

                                // ---- 我的 ----
                                UbRoutes.MINE -> MineScreen(
                                    onOpenLogin = { navigator.navigate(UbRoutes.LOGIN) },
                                    onOpenSettings = { navigator.navigate(UbRoutes.SETTINGS) },
                                    // ★ P11 补 25：「工具管理 / 数据 / 交互」已迁到「设置」页。
                                    //   用户把这三项圈出来要求移走；「外观」与「设置」留在本页。
                                    //   本页因此只剩这两项，不再需要那三个回调。
                                    onOpenAppearance = { navigator.navigate(UbRoutes.SETTINGS_APPEARANCE) },
                                    // ★ P11 补：「关于与开源许可」已移到「设置」页内
                                    //   （用户要求），这里不再有 onOpenAbout。
                                    currentTheme = themeMode,
                                    onThemeChange = onThemeChange,
                                )

                                // ---- 右下角预留页 ----
                                UbRoutes.EXPLORE -> ExplorePlaceholderScreen()

                                // ---- 设置：只放不归属上面三类的全局信息 ----
                                // ★ P11 补 3：用户要求去掉这里重复的
                                //   「工具管理 / 外观 / 数据」——它们已经在「我的」页的入口卡里。
                                //
                                // ★★ P11 补 23：**套上 SubPage**（用户指出「为什么设置的界面
                                //   没有返回按键，其他都有」）。
                                //
                                //   根因：SETTINGS 原本是底部第二个**根标签页**，
                                //   见 UbRoutes.ROOT_TABS 的注释。P11 把「设置」挪进「我的」之后，
                                //   它是二级页了，但这里一直**裸调** SettingsHubScreen ——
                                //   而 SettingsHubScreen 内部还留着根页时代的
                                //   「大标题 + 上方留白、无返回键」排版。
                                //
                                //   于是全 App 只有这一页没有返回键：连它的子页
                                //   SETTINGS_TOOLS / SETTINGS_APPEARANCE / SETTINGS_DATA / ABOUT
                                //   全都套了 SubPage。
                                //
                                //   ★ 教训：**页面的导航层级变了，页头的实现也要跟着变。**
                                //     两处描述同一件事（这里是"它是不是根页"），
                                //     改了一处忘了另一处，就会长出这种"只有它不一样"的怪页面。
                                UbRoutes.SETTINGS -> SubPage(
                                    title = "设置",
                                    onBack = { navigator.back() },
                                ) {
                                    SettingsHubScreen(
                                        onOpenAbout = { navigator.navigate(UbRoutes.ABOUT) },
                                        // P11 补 25：这三项从「我的」页迁到本页
                                        onOpenTools = { navigator.navigate(UbRoutes.SETTINGS_TOOLS) },
                                        onOpenData = { navigator.navigate(UbRoutes.SETTINGS_DATA) },
                                        onOpenInteraction = { navigator.navigate(UbRoutes.SETTINGS_INTERACTION) },
                                        // P11 补 39：账号管理（WebDAV 云备份）
                                        onOpenAccount = { navigator.navigate(UbRoutes.SETTINGS_ACCOUNT) },
                                    )
                                }

                                // 账号管理（P11 补 39 / 40）—— 用户点名要这个入口。
                                // 本页只放**入口列表**，具体配置在下一级
                                UbRoutes.SETTINGS_ACCOUNT -> {
                                    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
                                    val davStore = remember {
                                        com.utilitybox.core.sync.WebDavStore(
                                            com.utilitybox.core.secret.KeystoreSecretStore(appCtx)
                                        )
                                    }
                                    val cfg = remember { davStore.load() }
                                    SubPage(title = "账号管理", onBack = { navigator.back() }) {
                                        com.utilitybox.feature.settings.AccountManagerScreen(
                                            onOpenCloudBackup = {
                                                navigator.navigate(UbRoutes.SETTINGS_CLOUD_BACKUP)
                                            },
                                            cloudLoggedIn = cfg.isConfigured && cfg.password.isNotBlank(),
                                        )
                                    }
                                }

                                // 云备份（P11 补 40）—— 登录式界面，服务器地址与路径都隐式
                                UbRoutes.SETTINGS_CLOUD_BACKUP -> {
                                    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
                                    val davStore = remember {
                                        com.utilitybox.core.sync.WebDavStore(
                                            com.utilitybox.core.secret.KeystoreSecretStore(appCtx)
                                        )
                                    }
                                    SubPage(title = "云备份", onBack = { navigator.back() }) {
                                        com.utilitybox.feature.settings.CloudBackupScreen(
                                            store = davStore,
                                            exportJson = {
                                                com.utilitybox.game.wutheringwaves.kuro.WuwaGachaStore.exportJson()
                                            },
                                            importJson = { text ->
                                                com.utilitybox.game.wutheringwaves.kuro.WuwaGachaStore.importJson(text)
                                            },
                                        )
                                    }
                                }

                                // 交互偏好（P11 补 24）—— 与工具管理/外观/数据同级的二级页
                                UbRoutes.SETTINGS_INTERACTION -> SubPage(
                                    title = "交互",
                                    onBack = { navigator.back() },
                                ) {
                                    com.utilitybox.feature.settings.InteractionSettingsScreen(
                                        showGameSwitch = showGameSwitch,
                                        onShowGameSwitchChange = onShowGameSwitchChange,
                                    )
                                }

                                UbRoutes.SETTINGS_TOOLS -> SubPage(
                                    title = "工具管理",
                                    onBack = { navigator.back() },
                                ) {
                                    ToolManagementScreen()
                                }

                                UbRoutes.SETTINGS_APPEARANCE -> SubPage(
                                    title = "外观",
                                    onBack = { navigator.back() },
                                ) {
                                    AppearanceSettingsScreen(
                                        currentTheme = themeMode,
                                        onThemeChange = onThemeChange,
                                        accentSeed = accentSeed,
                                        onAccentChange = onAccentChange,
                                        splashImagePath = splashImagePath,
                                        onSplashPicked = onSplashPicked,
                                        onSplashCleared = onSplashCleared,
                                        glassEnabled = glassEnabled,
                                        onGlassChange = onGlassChange,
                                        reduceMotion = reduceMotion,
                                        onReduceMotionChange = onReduceMotionChange,
                                    )
                                }

                                // ---- 鸣潮：唤取记录（二级页）----
                                // ★ 它不依赖库街区登录，所以不做登录门禁
                                UbRoutes.WUWA_GACHA -> SubPage(
                                    title = "唤取记录",
                                    subtitle = "粘贴游戏内「唤取记录」链接即可查询",
                                    onBack = { navigator.back() },
                                ) {
                                    com.utilitybox.game.wutheringwaves.WuwaGachaScreen(
                                        onOpenStats = { navigator.navigate(UbRoutes.WUWA_GACHA_STATS) },
                                    )
                                }

                                // ---- 鸣潮：唤取统计（P11 补 44，独立页面）----
                                //
                                // ★ 用户要求「统计好后应该是一个**新的界面**，
                                //   而不是仍然显示在那里下面」。
                                //
                                // ★ 它自己从 WuwaGachaStore 读记录 —— 不需要跨页面传状态。
                                //   好处是统计页可以有独立的滚动位置与生命周期。
                                UbRoutes.WUWA_GACHA_STATS -> SubPage(
                                    title = "唤取统计",
                                    onBack = { navigator.back() },
                                ) {
                                    com.utilitybox.game.wutheringwaves.GachaStatsScreen(
                                        records = com.utilitybox.game.wutheringwaves.kuro.WuwaGachaStore.load(),
                                    )
                                }

                                UbRoutes.SETTINGS_DATA -> SubPage(
                                    title = "数据",
                                    onBack = { navigator.back() },
                                ) {
                                    DataSettingsScreen(
                                        onOpenApiKey = { navigator.navigate(UbRoutes.API_KEY) },
                                        onOpenDataSources = { navigator.navigate(UbRoutes.DATA_SOURCES) },
                                        autoRefreshEnabled = autoRefreshEnabled,
                                        onAutoRefreshChange = onAutoRefreshChange,
                                        autoRefreshMinutes = autoRefreshMinutes,
                                        onAutoRefreshMinutesChange = onAutoRefreshMinutesChange,
                                    )
                                }

                                UbRoutes.LOGIN -> SubPage(
                                    title = "登录",
                                    onBack = { navigator.back() },
                                ) {
                                    LoginScreen(onDone = { navigator.back() })
                                }

                                UbRoutes.API_KEY -> SubPage(
                                    title = "API Key",
                                    subtitle = "由你自己填写，只存在本机",
                                    onBack = { navigator.back() },
                                ) {
                                    ApiKeyScreen()
                                }

                                UbRoutes.DATA_SOURCES -> SubPage(
                                    title = "数据源状态",
                                    onBack = { navigator.back() },
                                ) {
                                    DataSourceStatusScreen(onOpenGlassProbe = { navigator.navigate(UbRoutes.GLASS_PROBE) })
                                }

                                UbRoutes.ABOUT -> SubPage(title = "关于", onBack = { navigator.back() }) {
                                    AboutScreen()
                                }

                                // 二级页面：不属于首页工具，因此不注册为 provider（architecture.md §4.4）
                                DeltaForceRoutes.ITEM_DETAIL -> {
                                    val item = selectedItem
                                    if (item == null) {
                                        // 保底：直接进这个路由而没有选中物品时，如实说明而不是空白页
                                        SubPage(title = "物品详情", onBack = { navigator.back() }) {
                                            UbError(
                                                title = "没有选中物品",
                                                description = "请从「物品物价」列表里点选一个物品。",
                                            )
                                        }
                                    } else {
                                        SubPage(title = "物品详情", onBack = { navigator.back() }) {
                                            ItemDetailScreen(summary = item)
                                        }
                                    }
                                }

                                UbRoutes.PROTO_STATES -> SubPage(
                                    title = "状态组件",
                                    onBack = { navigator.back() },
                                ) {
                                    ProtoStatesScreen()
                                }

                                // 玻璃验证页（诊断用）：底部条纹穿过导航栏，验证模糊是否生效。
                                // ★ 它是**根标签页**（见 UbNavigator.ROOT_TABS），
                                //   否则底部导航不渲染，就验证不了玻璃。
                                UbRoutes.GLASS_PROBE -> com.utilitybox.feature.settings.GlassProbeScreen()

                                else -> UbError(
                                    title = "页面不存在",
                                    description = "路由 $route 未注册。这通常是开发期错误。",
                                )
                            }
                        }
                    }
                }
            }

            // 注意：这里**不再**画底部渐隐层。
            // 渐隐是自研玻璃时代的替代方案（用渐变模仿"内容淡入材质"）；
            // 换成 Haze 折射玻璃后，玻璃本身就会折射并模糊其下的内容 ——
            // 再叠一层渐变会让栏下的文字显得脏（P04 实测）。
            // 内容与导航的避让由各页的 `bottomBarReserved` 内边距保证。

            // 底部悬浮导航（Liquid Glass 首要应用位置）
            //
            // ★★ P11 结构调整后的构成（用户要求）：
            //   左侧胶囊 = **两个游戏 tab**（三角洲 / 鸣潮）
            //   右侧独立圆钮 = **预留入口**（点进去是占位页，用户说后面再提需求）
            //
            //   移除的两项及原因：
            //   - 「设置」tab → 设置移到「我的」右上角齿轮（用户要求）
            //   - 「全部工具」按钮 → 用户明确要求"点击右下角的选项不用显示任何工具"
            if (navigator.isRootTab) {
                UbBottomBar(
                    items = listOf(
                        UbBottomItem(UbRoutes.GAME_DELTA, "三角洲", UbIcon.Game),
                        UbBottomItem(UbRoutes.GAME_WUWA, "鸣潮", UbIcon.Wave),
                    ),
                    // ★★ 读 navigator.current（不再有单独的 highlightedRoute）：
                    //   滑块位置改由**连续值** selectedFraction 驱动，
                    //   这里只负责"不在游戏标签上时选中哪一项"。
                    currentRoute = navigator.current,
                    onSelect = { navigator.switchTab(it) },
                    // ★★ 连续滑块：在游戏标签上时由 pager 逐帧灌入，
                    //   于是"页面滑到 73% → 药丸也在 73%"，两者同源同帧。
                    //   不在游戏标签上时给 null，底栏退回离散索引 + 动画。
                    //   背景见 UbNavigator.tabSlideFraction 与 GamePagerHost 的说明。
                    selectedFraction = if (isGameTabRoute(navigator.current)) {
                        navigator.tabSlideFraction
                    } else {
                        null
                    },
                    trailingAction = UbBottomAction(
                        label = "我的",
                        icon = UbIcon.User,
                        onClick = { navigator.switchTab(UbRoutes.MINE) },
                        isSelected = navigator.current == UbRoutes.MINE,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // ★ 顺序很重要（P09 实测踩到）：
                        //   `padding` 必须在 `fillMaxWidth` **之前**，
                        //   否则先占满全宽再把内容内缩，胶囊就会溢出屏幕左右边缘。
                        .padding(
                            horizontal = s.screenH,
                            vertical = LocalUbSize.current.bottomBarInset,
                        )
                        .fillMaxWidth(),
                )
            }

            // 「再按一次返回键退出」提示（仅在根标签页、且用户按过一次返回时出现）
            AutoHideExitHint(exitConfirm)
            UbExitHint(
                visible = exitConfirm.showHint,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
