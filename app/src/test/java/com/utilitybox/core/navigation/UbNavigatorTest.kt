package com.utilitybox.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * 导航器测试。
 *
 * ★ 核心是**回归钉子**：真机实测踩到「进二级页面按返回直接退出应用」，
 *   根因是 [UbNavigator.canGoBack] 从普通 `mutableListOf` 计算，
 *   不构成 Compose 依赖，导致 `BackHandler(enabled = …)` 停在过期值。
 *
 *   在纯 JVM 单测里无法直接断言"Compose 会不会重组"，但可以断言
 *   **判定值的正确性**（栈深变化后 canGoBack 必须同步变化）——
 *   这正是当初出错的那一环（值没跟着栈走）。
 *   可观察性本身由 `stackDepth by mutableStateOf` 保证。
 *
 * ★ 本文件曾因工具链问题损坏后重建（P11）：Windows PowerShell 5.1 的
 *   `Get-Content`/`Set-Content` 默认按 GBK 读写，把 UTF-8 源码双重编码并丢字节。
 *   用例名单由 `app/build/test-results/…UbNavigatorTest.xml` 恢复。
 *   **结论：源码只用 `edit`/`write` 工具改，不再用 PowerShell 读写源码文件。**
 */
class UbNavigatorTest {

    private fun nav(route: String = UbRoutes.GAME_DELTA) = UbNavigator(route)

    @Test
    fun `初始在首页时不能返回 且不显示底部导航之外的东西`() {
        val n = nav()
        assertEquals(UbRoutes.GAME_DELTA, n.current)
        assertFalse("栈里只有一项，不该能返回", n.canGoBack)
        assertTrue("首页是根标签", n.isRootTab)
    }

    @Test
    fun `navigate 后可以返回 且不再是根标签`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)

        assertEquals(UbRoutes.ABOUT, n.current)
        assertTrue("进二级页后必须能返回（否则返回键会被系统接管→退出应用）", n.canGoBack)
        assertFalse("二级页不该显示底部导航", n.isRootTab)
    }

    @Test
    fun `多层进入后逐层返回 直到首页`() {
        val n = nav()
        n.navigate(UbRoutes.SETTINGS)
        n.navigate(UbRoutes.DATA_SOURCES)
        n.navigate(UbRoutes.GLASS_PROBE)

        assertEquals(UbRoutes.GLASS_PROBE, n.current)
        assertTrue(n.canGoBack)

        assertTrue(n.back())
        assertEquals(UbRoutes.DATA_SOURCES, n.current)
        assertTrue(n.canGoBack)

        assertTrue(n.back())
        assertEquals(UbRoutes.SETTINGS, n.current)
        assertTrue(n.canGoBack)

        assertTrue(n.back())
        assertEquals(UbRoutes.GAME_DELTA, n.current)
        assertFalse("回到首页后不该能再返回", n.canGoBack)
    }

    @Test
    fun `已在栈底时 back 返回 false 而不是抛异常`() {
        val n = nav()
        assertFalse("栈底调用 back 应返回 false，交给系统处理", n.back())
        assertEquals(UbRoutes.GAME_DELTA, n.current)
    }

    @Test
    fun `switchTab 会清空中间栈 —— 避免首页与标签之间来回堆积`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        n.navigate(UbRoutes.DATA_SOURCES)
        assertTrue(n.canGoBack)

        n.switchTab(UbRoutes.MINE)

        assertEquals(UbRoutes.MINE, n.current)
        assertTrue("我的 是根标签", n.isRootTab)
        assertFalse(
            "切标签后不该还能返回上一个标签的二级页 —— 否则会退到别的标签里去",
            n.canGoBack,
        )
    }

    @Test
    fun `★ 设置不是根标签 —— 它是从「我的」进入的二级页`() {
        // 回归钉子（P11 补 12）：`ROOT_TABS` 里曾经还留着 SETTINGS ——
        // 那是"设置还是底部标签"时代留下的，后来设置被移进「我的」，
        // 集合却没跟着改。后果是**设置页下面画着一条毫无意义的底部导航**，
        // 而且上面没有任何一项高亮（当前路由不在那两项里）。
        val n = nav()
        n.navigate(UbRoutes.SETTINGS)
        assertEquals(UbRoutes.SETTINGS, n.current)
        assertFalse("设置页不该显示底部导航", n.isRootTab)
        assertTrue("设置是从「我的」推进来的，应当能返回", n.canGoBack)
    }

    @Test
    fun `navigate 到当前路由是空操作 —— 不应让栈虚假增长`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        val before = n.current

        n.navigate(UbRoutes.ABOUT) // 重复导航

        assertEquals(before, n.current)
        assertTrue(n.back())
        assertEquals("重复导航不该多压一层", UbRoutes.GAME_DELTA, n.current)
        assertFalse(n.canGoBack)
    }

    @Test
    fun `GLASS_PROBE 是根标签 —— 它需要导航栏可见才能做诊断`() {
        val n = nav(UbRoutes.GLASS_PROBE)
        assertTrue("玻璃验证页必须在 ROOT_TABS 里，否则导航栏不渲染", n.isRootTab)
    }

    // -----------------------------------------------------------------------
    // 转场方向（P11 用户反馈「返回应该从左边往右淡出，进入则反过来，而不是杂乱无章」）
    // -----------------------------------------------------------------------

    @Test
    fun `初始方向是前进`() {
        assertEquals(NavDirection.Forward, nav().lastDirection)
    }

    @Test
    fun `navigate 记为前进`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        assertEquals(
            "进入下一层必须是前进方向，否则转场会反向播放",
            NavDirection.Forward,
            n.lastDirection,
        )
    }

    @Test
    fun `back 记为返回`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        n.back()

        assertEquals(
            "返回必须记为 Backward，否则会从右侧滑出（观感是又前进了一次）",
            NavDirection.Backward,
            n.lastDirection,
        )
    }

    @Test
    fun `多层返回时方向始终保持返回`() {
        val n = nav()
        n.navigate(UbRoutes.SETTINGS)
        n.navigate(UbRoutes.SETTINGS_TOOLS)
        n.navigate(UbRoutes.DATA_SOURCES)

        repeat(3) {
            assertTrue(n.back())
            assertEquals(
                "逐层返回的每一步都必须是 Backward",
                NavDirection.Backward,
                n.lastDirection,
            )
        }
    }

    @Test
    fun `返回后再进入 方向重新变为前进`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        n.back()
        assertEquals(NavDirection.Backward, n.lastDirection)

        n.navigate(UbRoutes.SETTINGS)
        assertEquals(
            "返回之后再进入，方向必须翻回 Forward",
            NavDirection.Forward,
            n.lastDirection,
        )
    }

    @Test
    fun `重复 navigate 到当前路由不改变方向`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        n.back()
        assertEquals(NavDirection.Backward, n.lastDirection)

        // 当前已经回到首页；再 navigate 到首页是空操作，不该把方向刷成 Forward
        n.navigate(UbRoutes.GAME_DELTA)
        assertEquals(
            "空操作不该改动方向 —— 否则会凭空播一次前进转场",
            NavDirection.Backward,
            n.lastDirection,
        )
    }

    @Test
    fun `switchTab 记为前进 —— 标签之间没有层级关系`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        n.back()
        assertEquals(NavDirection.Backward, n.lastDirection)

        n.switchTab(UbRoutes.MINE)
        assertEquals(
            "切标签用前进方向（配合淡入淡出），不该沿用上一个动作的返回方向",
            NavDirection.Forward,
            n.lastDirection,
        )
    }

    @Test
    fun `栈底按返回返回 false 且不改变方向`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        n.back()
        val dirAfterBack = n.lastDirection

        assertFalse("栈底按返回必须返回 false，交给上层去处理双击退出", n.back())
        assertEquals("失败的 back 不该改变方向", dirAfterBack, n.lastDirection)
    }

    // -----------------------------------------------------------------------
    // syncTab：滑动停稳后补登记状态
    // -----------------------------------------------------------------------

    @Test
    fun `syncTab 切换根标签并清空中间栈`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        assertTrue(n.canGoBack)

        n.syncTab(UbRoutes.GAME_WUWA)

        assertEquals(UbRoutes.GAME_WUWA, n.current)
        assertTrue("鸣潮是根标签", n.isRootTab)
        assertFalse("切换后不该还能返回上一个标签的二级页", n.canGoBack)
    }

    @Test
    fun `syncTab 不改变方向 —— 滑动没有前进返回的语义`() {
        val n = nav()
        n.navigate(UbRoutes.ABOUT)
        n.back()
        val before = n.lastDirection

        n.syncTab(UbRoutes.GAME_WUWA)

        assertEquals("syncTab 不该改动方向（否则会误触发一次转场）", before, n.lastDirection)
    }

    @Test
    fun `syncTab 到当前路由是空操作`() {
        val n = nav()
        n.syncTab(UbRoutes.GAME_DELTA)
        assertEquals(UbRoutes.GAME_DELTA, n.current)
        assertFalse(n.canGoBack)
    }

    @Test
    fun `两个游戏标签都是根标签 且都不在可返回状态`() {
        listOf(UbRoutes.GAME_DELTA, UbRoutes.GAME_WUWA).forEach { route ->
            val n = nav(route)
            assertTrue("$route 必须是根标签，否则底部导航不显示", n.isRootTab)
            assertFalse("刚进入的标签不该能返回", n.canGoBack)
        }
    }

    // -----------------------------------------------------------------------
    // ★★ 连续滑块（P11 用户反馈「tab 和页面应该是同步的，而不是页面滑动了 tab 才动」）
    //
    //   滑动时页面由 pager 跟手渲染，而导航状态要等停稳才更新。
    //   若在滑动过程中改导航状态，会破坏返回栈、
    //   并被"不可见的 pager"用陈旧页号覆盖用户的选择。
    //
    //   所以滑块位置**单独**用一个连续值承载，由 pager 逐帧灌入；
    //   导航状态仍然只在停稳后由 [UbNavigator.syncTab] 更新。
    //
    //   ★ 公式本身（`currentPage + currentPageOffsetFraction`）在
    //     `GamePagerHostTest` 里单独钉 —— 那里才有 pager 语义。
    // -----------------------------------------------------------------------

    @Test
    fun `初始没有连续滑块 —— 交回底栏按离散索引渲染`() {
        assertNull(
            "还没开始滑动时不该有连续值，否则底栏会用到没有意义的 0",
            nav().tabSlideFraction,
        )
    }

    @Test
    fun `updateTabSlideFraction 只改滑块 不动导航状态`() {
        val n = nav()
        n.navigate(UbRoutes.SETTINGS_TOOLS)

        n.updateTabSlideFraction(0.42f)

        assertEquals("滑动过程中不该改当前路由", UbRoutes.SETTINGS_TOOLS, n.current)
        assertEquals(0.42f, n.tabSlideFraction)
    }

    @Test
    fun `updateTabSlideFraction 不破坏返回栈`() {
        val n = nav()
        n.navigate(UbRoutes.SETTINGS)
        n.navigate(UbRoutes.SETTINGS_TOOLS)

        n.updateTabSlideFraction(1f)

        assertTrue("滑动过程中不该清空返回栈", n.canGoBack)
        assertTrue(n.back())
        assertEquals("返回必须回到上一层，而不是被滑块改动影响", UbRoutes.SETTINGS, n.current)
    }

    @Test
    fun `拖动后反悔退回原页 —— 滑块跟着回但导航状态始终没被动过`() {
        val n = nav(UbRoutes.GAME_DELTA)

        // 用户拖到一半（滑块跟手往前走）
        n.updateTabSlideFraction(0.6f)
        assertEquals(0.6f, n.tabSlideFraction)
        assertEquals("导航状态此时必须仍是三角洲", UbRoutes.GAME_DELTA, n.current)

        // 松手回弹到原页
        n.updateTabSlideFraction(0f)
        assertEquals(0f, n.tabSlideFraction)
        assertEquals(UbRoutes.GAME_DELTA, n.current)
        assertFalse("全程不该产生可返回的历史", n.canGoBack)
    }

    @Test
    fun `清空滑块后回到离散索引 —— 离开游戏标签时必须交还`() {
        val n = nav()
        n.updateTabSlideFraction(1f)
        assertEquals(1f, n.tabSlideFraction)

        // pager 被卸载（例如切到「我的」）时会清空
        n.updateTabSlideFraction(null)

        assertNull("必须能回到 null，否则「我的」页会继续沿用最后一次的滑动位置", n.tabSlideFraction)
    }

    @Test
    fun `滑块只管位置 不影响谁能返回`() {
        // 把最容易出错的一条单独钉死：滑块在滑动过程中会被高频写入，
        // 任何一次写入都不允许让"能不能返回"发生变化。
        val n = nav()
        val before = n.canGoBack

        listOf(0f, 0.3f, 0.5f, 0.9f, 1f).forEach { f ->
            n.updateTabSlideFraction(f)
            assertEquals("滑块写入不得改变 canGoBack", before, n.canGoBack)
            assertEquals("滑块写入不得改变当前路由", UbRoutes.GAME_DELTA, n.current)
        }
    }

    // -----------------------------------------------------------------------
    // ★★ 转场状态折叠（navHostStateOf）
    //
    //   背景：用户反馈「每次点开某一个界面都有重影」。
    //   根因是 AnimatedContent 的内容 lambda 忽略了**槽位状态**、
    //   改读全局的 navigator.current，于是转场期间旧页槽与新页槽渲染同一个页面。
    //
    //   修好之后，这个折叠函数就承担了一个新职责：
    //   **它必须让"不同的页面"映射到"不同的状态"**，
    //   否则两个槽位仍然分不出彼此 —— 重影会以另一种形式回来。
    // -----------------------------------------------------------------------

    @Test
    fun `两个游戏标签折叠成同一个状态 —— 否则滑动会把分页容器重建`() {
        assertEquals(
            "三角洲与鸣潮必须折叠成同一个转场状态：它们由 HorizontalPager 自己跟手切换，" +
                "若状态不同，AnimatedContent 会重建内容（rememberPagerState 被重置），" +
                "进而与状态同步形成死循环",
            navHostStateOf(UbRoutes.GAME_DELTA),
            navHostStateOf(UbRoutes.GAME_WUWA),
        )
    }

    @Test
    fun `折叠值不得与任何真实路由相同`() {
        val allRoutes = listOf(
            UbRoutes.GAME_DELTA, UbRoutes.GAME_WUWA, UbRoutes.MINE, UbRoutes.EXPLORE,
            UbRoutes.SETTINGS, UbRoutes.SETTINGS_TOOLS, UbRoutes.SETTINGS_APPEARANCE,
            UbRoutes.SETTINGS_DATA, UbRoutes.DATA_SOURCES, UbRoutes.ABOUT,
            UbRoutes.API_KEY, UbRoutes.LOGIN, UbRoutes.PROTO_STATES, UbRoutes.GLASS_PROBE,
        )
        allRoutes.forEach { r ->
            assertFalse(
                "折叠值 $GAME_PAGER 不能等于真实路由 $r —— 否则分页容器会和那个页面混淆",
                GAME_PAGER == r,
            )
        }
    }

    @Test
    fun `★ 不同的非游戏路由必须映射到不同的转场状态 —— 这是"不重影"的前提`() {
        // 若这里失败，说明折叠过度：转场时旧页槽与新页槽会拿到**同一个状态**，
        // 于是渲染出同一份内容 → 用户看到的还是"两个一样的页面错位叠着"。
        val pages = listOf(
            UbRoutes.MINE,
            UbRoutes.SETTINGS,
            UbRoutes.SETTINGS_TOOLS,
            UbRoutes.SETTINGS_APPEARANCE,
            UbRoutes.SETTINGS_DATA,
            UbRoutes.DATA_SOURCES,
            UbRoutes.ABOUT,
            UbRoutes.API_KEY,
            UbRoutes.LOGIN,
            UbRoutes.EXPLORE,
            UbRoutes.PROTO_STATES,
            UbRoutes.GLASS_PROBE,
        )
        val seen = mutableMapOf<String, String>()
        pages.forEach { r ->
            val state = navHostStateOf(r)
            val clash = seen[state]
            assertNull(
                "路由 $r 与 $clash 折叠成了同一个转场状态「$state」—— " +
                    "两者之间的转场会渲染出同一份内容，也就是重影",
                clash,
            )
            seen[state] = r
        }
    }

    @Test
    fun `非游戏路由原样返回 不做任何包装`() {
        listOf(UbRoutes.MINE, UbRoutes.SETTINGS, UbRoutes.ABOUT).forEach { r ->
            assertEquals("$r 不该被折叠", r, navHostStateOf(r))
        }
    }
}
