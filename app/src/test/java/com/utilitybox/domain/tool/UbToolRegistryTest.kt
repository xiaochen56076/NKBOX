package com.utilitybox.domain.tool

import com.utilitybox.domain.model.ToolDescriptor
import com.utilitybox.domain.model.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具注册表与扩展机制测试。
 *
 * ★ 依据 docs/architecture.md §4/§5：
 *   - 注册表是**单一事实来源**，且**不得自己写死工具列表**，必须由
 *     `AppContainer.providers`（各游戏模块自报）派生 —— 这是"新增工具不改公共层"的前提。
 *   - 根导航靠 `providerByRoute` 分发页面，不写 `when` 分支，因此
 *     "路由能查到 provider" 是必须被钉住的行为。
 *
 * ★ 本测试全部是纯逻辑断言：只读 `AppContainer` / `UbToolRegistry` 的元信息，
 *   不调用任何 Composable、不触碰 android.* 与 org.json（单测中它们不可用）。
 */
class UbToolRegistryTest {

    /** 装配容器里的全部 provider —— 注册表的唯一事实来源 */
    private val providers: List<ToolProvider> = AppContainer.providers

    /** 注册表暴露的工具元信息（每次访问都从 provider 派生） */
    private val tools: List<ToolDescriptor> = UbToolRegistry.tools

    /**
     * 期望存在的工具 id 白名单。
     *
     * ★ 这份清单是**故意写死**的：新增工具时它必然失败，从而强迫开发者显式过目一次
     *   "首页到底该有哪些工具"。同时它防住了另一种错误 ——
     *   在同一份列表里写了重复来源，导致首页出现幽灵工具。
     *
     *   P10 新增「弹药物价」时本清单按预期失败，已显式确认后加入。
     */
    private val expectedIds = listOf(
        "deltaforce.dailycode",
        "deltaforce.itemprice",
        "deltaforce.friendstatus",
        "deltaforce.pricerange",
        "deltaforce.ammoprice",
    )

    // -----------------------------------------------------------------------
    // 1. 注册表完整性
    // -----------------------------------------------------------------------

    @Test
    fun `注册表通过完整性自检不抛异常`() {
        // verifyIntegrity() 内部校验：id 唯一、route 唯一、gameId 已注册、route 非空。
        // 配置错误时 check() 会抛 IllegalStateException —— 不抛即通过。
        UbToolRegistry.verifyIntegrity()

        assertTrue("注册表不能是空的", tools.isNotEmpty())
        assertEquals(
            "tools 必须与 providers 一一对应（数量相等）",
            providers.size,
            tools.size,
        )
    }

    @Test
    fun `预期工具 id 全部在注册表中 且没有多余工具`() {
        val ids = tools.map { it.id }
        assertTrue(
            "注册表缺少工具，实际 id = $ids",
            ids.containsAll(expectedIds),
        )
        // 反向：注册表里不应出现预期之外的孤儿工具（防止写了另一份列表）
        assertEquals(
            "注册表出现了未预期的工具 id，实际 = $ids",
            expectedIds.toSet(),
            ids.toSet(),
        )
    }

    @Test
    fun `工具 id 全局唯一`() {
        val ids = providers.map { it.descriptor.id }
        assertEquals(
            "工具 id 重复: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }}",
            ids.size,
            ids.toSet().size,
        )
    }

    @Test
    fun `每个 provider 的 gameId 都能在注册表 games 里找到`() {
        val gameIds = UbToolRegistry.games.map { it.id }.toSet()
        assertTrue("games 不能是空的", gameIds.isNotEmpty())

        val unknown = providers
            .map { it.descriptor.gameId }
            .filterNot { it in gameIds }
        assertEquals("以下工具声明了未注册的游戏: $unknown", emptyList<String>(), unknown)
    }

    // -----------------------------------------------------------------------
    // 2. 路由：唯一、非空、可反查
    // -----------------------------------------------------------------------

    @Test
    fun `所有工具路由互不相同且不为空白`() {
        assertTrue("provider 列表不能为空，否则断言会空转", providers.isNotEmpty())

        val routes = providers.map { it.descriptor.route }
        assertEquals(
            "路由重复: ${routes.groupingBy { it }.eachCount().filter { it.value > 1 }}",
            routes.size,
            routes.toSet().size,
        )
        routes.forEach { route ->
            assertTrue("路由不得为空白（空白路由点了没反应）: '$route'", route.isNotBlank())
        }
    }

    @Test
    fun `按路由能查到同一个 provider 实例`() {
        assertTrue("provider 列表不能为空，否则断言会空转", providers.isNotEmpty())

        providers.forEach { provider ->
            val route = provider.descriptor.route
            val found = UbToolRegistry.providerByRoute(route)
            assertNotNull("路由 '$route' 查不到 provider", found)
            assertSame(
                "路由 '$route' 必须返回装配容器里的同一个 provider 实例",
                provider,
                found,
            )
        }
    }

    @Test
    fun `按 id 与按路由查到的描述符完全一致`() {
        assertTrue("provider 列表不能为空，否则断言会空转", providers.isNotEmpty())

        providers.forEach { provider ->
            val descriptor = provider.descriptor
            val byId = UbToolRegistry.byId(descriptor.id)
            val byRoute = UbToolRegistry.byRoute(descriptor.route)

            assertEquals("按 id 查不到 ${descriptor.id}", descriptor, byId)
            assertEquals("按路由查不到 ${descriptor.route}", descriptor, byRoute)
            assertNotNull(byId)
            assertNotNull(byRoute)
            // 交叉一致性：两条查询路径必须落到同一个描述符上。
            //
            // ★ 注意不能写成 byId(id)?.route == byRoute(route)?.id ——
            //   id 是全局标识（"deltaforce.dailycode"），route 是导航路径（"df/dailycode"），
            //   二者本就是两套命名，要求的"一致"只能是：
            //   ① 两条路径查到同一个描述符；② 各自字段回指原查询键。
            assertEquals("两条查询路径必须查到同一个描述符", byId, byRoute)
            assertEquals("byId(id).id 必须回指 id", descriptor.id, byId!!.id)
            assertEquals("byId(id).route 必须回指该工具的 route", descriptor.route, byId.route)
            assertEquals("byRoute(route).id 必须回指该工具的 id", descriptor.id, byRoute!!.id)
            assertEquals("byRoute(route).route 必须回指 route", descriptor.route, byRoute.route)
            // 交叉：两条路径给出的 id 与 route 必须两两相同
            assertEquals("两条路径给出的 id 必须相同", byRoute.id, byId.id)
            assertEquals("两条路径给出的 route 必须相同", byRoute.route, byId.route)
        }
    }

    @Test
    fun `未知路由不返回 provider 与描述符`() {
        assertNull(UbToolRegistry.providerByRoute("no/such/route"))
        assertNull(UbToolRegistry.byRoute("no/such/route"))
        // 空路由同样不得命中任何工具（防止 firstOrNull 误配空白实现）
        assertNull(UbToolRegistry.providerByRoute(""))
        assertNull(UbToolRegistry.byRoute(""))
        // 已知路由的相邻写法也不得命中（不做前缀匹配）
        assertNull(UbToolRegistry.providerByRoute("df/itemprice/detail"))
    }

    // -----------------------------------------------------------------------
    // 3. 首页视图只反映当前激活的游戏
    // -----------------------------------------------------------------------

    @Test
    fun `homeTools 只返回当前激活游戏的工具`() {
        val active = UbToolRegistry.activeGame
        val home = UbToolRegistry.homeTools("deltaforce")

        assertTrue("首页工具不能为空", home.isNotEmpty())
        assertTrue(
            "首页出现了非激活游戏的工具: ${home.filter { it.gameId != active.id }.map { it.id }}",
            home.all { it.gameId == active.id },
        )
        // ★ P11 补 80：`homeTools` **不再等于 `toolsOf`**。
        //
        //   有两类**合法的**过滤（都不是 bug）：
        //   ① 用户在「工具管理」里显式关掉的（P09 起就有的行为）；
        //   ② 声明了 `showInToolList = false` 的工具 —— 它的信息已经显示在
        //      首页摘要区（`homeSummary`），列表里再挂一张卡就是重复。
        //
        //   所以断言改成：homeTools == 「应显示的工具」去掉「被关闭的」，
        //   而不是拿 toolsOf 直接比 —— 后者会把 ② 当成回归。
        assertEquals(
            "homeTools 必须等于 visibleToolsOf 去掉用户关闭的那些",
            UbToolRegistry.visibleToolsOf(active.id).filterNot { UbToolRegistry.isDisabled(it.id) },
            home,
        )
        // 但绝不能反过来多出来：首页看到的工具一定是注册表里存在的
        assertTrue(
            "homeTools 不能多于 toolsOf（不能凭空多出工具）",
            home.size <= UbToolRegistry.toolsOf(active.id).size,
        )
        // 而且必须是 toolsOf 的子集（顺序可以不同，id 必须都在）
        val allIds = UbToolRegistry.toolsOf(active.id).map { it.id }.toSet()
        assertTrue(
            "homeTools 出现了 toolsOf 里没有的工具: ${home.map { it.id }.filter { it !in allIds }}",
            home.all { it.id in allIds },
        )

        // 非激活游戏（当前只有一款，故此处为空）的工具绝不能出现在首页。
        // 将来接入第二款游戏时，这一条才会真正起作用。
        val otherGameIds = UbToolRegistry.games.map { it.id }.filter { it != active.id }
        assertTrue(
            "首页出现了非激活游戏的工具",
            otherGameIds.none { otherId -> home.any { it.gameId == otherId } },
        )
    }

    @Test
    fun `toolsOf 对未注册的游戏返回空列表`() {
        assertTrue(UbToolRegistry.toolsOf("no-such-game").isEmpty())
        assertEquals(
            "toolsOf 不得跨游戏串味",
            providers.count { it.descriptor.gameId == UbToolRegistry.activeGame.id },
            UbToolRegistry.toolsOf(UbToolRegistry.activeGame.id).size,
        )
    }

    // -----------------------------------------------------------------------
    // 3b. ★★ 第二款游戏接入（P11 结构调整的核心验收）
    // -----------------------------------------------------------------------

    @Test
    fun `鸣潮已注册为第二个游戏`() {
        val ids = UbToolRegistry.games.map { it.id }
        assertTrue("三角洲必须在", ids.contains("deltaforce"))
        assertTrue("鸣潮必须已注册（tab 栏要先预留好）", ids.contains("wuwa"))
    }

    @Test
    fun `鸣潮目前没有工具 但仍然是合法游戏`() {
        // 用户明确要求「鸣潮的界面可以选空着，但是 tab 栏要先预留做好」。
        // 所以这里断言的是：它**可以被查询**，只是结果为空。
        assertTrue(
            "鸣潮当前应无工具（还没有需求）",
            UbToolRegistry.toolsOf("wuwa").isEmpty(),
        )
        assertTrue(
            "鸣潮应能被 homeTools 查询而不抛异常",
            UbToolRegistry.homeTools("wuwa").isEmpty(),
        )
    }

    @Test
    fun `三角洲的工具没有被鸣潮的接入影响`() {
        // ★ 这是"接第二款游戏时第一款 0 改动"的可执行断言。
        val delta = UbToolRegistry.toolsOf("deltaforce")
        assertTrue("三角洲工具不能因为接入鸣潮而丢失", delta.size >= 5)

        // 反向：两个游戏的工具不能互相串味
        val both = UbToolRegistry.tools.map { it.gameId }.distinct()
        assertTrue(
            "工具的游戏归属只有已注册的那些",
            both.all { it in UbToolRegistry.games.map { g -> g.id } },
        )
    }

    @Test
    fun `gamesWithTools 只列出确实有工具的游戏`() {
        val withTools = UbToolRegistry.gamesWithTools().map { it.id }
        assertTrue("三角洲有工具", withTools.contains("deltaforce"))
        assertTrue(
            "鸣潮没有工具，不该出现在工具管理里（否则会有一个空分组）",
            !withTools.contains("wuwa"),
        )
    }

    // -----------------------------------------------------------------------
    // 4. ★ 扩展机制核心：注册表由 provider 派生，没有第二份写死的列表
    // -----------------------------------------------------------------------

    @Test
    fun `注册表工具列表与 AppContainer providers 派生结果完全同组`() {
        assertTrue("provider 列表不能为空，否则集合相等会空转成立", providers.isNotEmpty())

        val providerRoutes = AppContainer.providers.map { it.descriptor.route }
        val registryRoutes = tools.map { it.route }

        // 顺序与内容都一致 ⇒ 注册表确实只是 providers 的投影
        assertEquals("注册表路由列表必须与 provider 路由列表逐项相同", providerRoutes, registryRoutes)
        // 集合相等（题目要求的"同一组"断言）
        assertEquals("注册表与 providers 必须是同一组路由", providerRoutes.toSet(), registryRoutes.toSet())
        assertEquals("数量必须相等", providerRoutes.size, registryRoutes.size)
        // 描述符整体一致
        assertEquals(
            "注册表 tools 必须等于 providers 的 descriptor 投影",
            AppContainer.providers.map { it.descriptor },
            tools,
        )

        // 反向：注册表里每个描述符都能在 providers 里找到来源，且实例一致
        tools.forEach { descriptor ->
            val owner = AppContainer.providers.firstOrNull { it.descriptor == descriptor }
            assertNotNull("注册表里的 ${descriptor.id} 没有任何 provider 与之对应", owner)
            assertSame(
                "注册表里的描述符必须是 provider 自己的那个对象",
                owner!!.descriptor,
                descriptor,
            )
        }

        // 注册表里也不允许出现 gameId 不在 providers 中的工具
        assertEquals(
            "注册表出现了 providers 未提供的 gameId",
            AppContainer.providers.map { it.descriptor.gameId }.toSet(),
            tools.map { it.gameId }.toSet(),
        )
    }

    // -----------------------------------------------------------------------
    // 5. 诚实标注（项目红线）：状态与状态说明必须如实
    // -----------------------------------------------------------------------

    @Test
    fun `实验性工具 pricerange 在注册表中如实标注`() {
        val descriptor = UbToolRegistry.byId("deltaforce.pricerange")
        assertNotNull("实验性工具 deltaforce.pricerange 必须存在于注册表", descriptor)

        assertEquals(ToolStatus.EXPERIMENTAL, descriptor!!.status)
        assertTrue(
            "实验性工具必须给出非空 statusLabel，否则用户无从得知数据不可靠",
            !descriptor.statusLabel.isNullOrBlank(),
        )
        // 交叉核对：provider 侧的声明与注册表一致（没有被谁改写过）
        val provider = UbToolRegistry.providerByRoute(descriptor.route)
        assertNotNull(provider)
        assertEquals(descriptor, provider!!.descriptor)
        assertEquals(ToolStatus.EXPERIMENTAL, provider.descriptor.status)
    }

    @Test
    fun `好友状态工具仍为待数据源状态且入口未隐藏`() {
        val descriptor = UbToolRegistry.byId("deltaforce.friendstatus")
        assertNotNull("好友状态工具必须存在于注册表", descriptor)

        assertEquals(ToolStatus.PENDING_SOURCE, descriptor!!.status)
        assertTrue(
            "待数据源的工具必须给出非空 statusLabel，避免用户以为功能坏了",
            !descriptor.statusLabel.isNullOrBlank(),
        )
        // 项目决策：不隐藏入口，而是带标注地保留在首页
        assertTrue(
            "待数据源的工具应仍出现在首页（带状态标注），而不是被隐藏",
            UbToolRegistry.homeTools("deltaforce").any { it.id == descriptor.id },
        )
    }

    @Test
    fun `所有非 ENABLED 状态的工具都必须带状态说明`() {
        val silent = tools.filter {
            it.status != ToolStatus.ENABLED && it.statusLabel.isNullOrBlank()
        }
        assertEquals(
            "以下工具状态不是 ENABLED 却没给 statusLabel（违反诚实标注红线）: ${silent.map { it.id }}",
            emptyList<String>(),
            silent.map { it.id },
        )
    }

    // -----------------------------------------------------------------------
    // 6. 元信息自洽
    // -----------------------------------------------------------------------

    @Test
    fun `每个描述符的关键字段都非空白且 id 以 gameId 为前缀`() {
        assertTrue("provider 列表不能为空，否则断言会空转", providers.isNotEmpty())

        providers.forEach { provider ->
            val d = provider.descriptor
            assertTrue("id 不得为空: '${d.id}'", d.id.isNotBlank())
            assertTrue("gameId 不得为空: '${d.gameId}'", d.gameId.isNotBlank())
            assertTrue("name 不得为空: '${d.id}'", d.name.isNotBlank())
            assertTrue("description 不得为空: '${d.id}'", d.description.isNotBlank())
            assertTrue("route 不得为空: '${d.id}'", d.route.isNotBlank())
            // Models.kt 约定：id 形如 "deltaforce.dailycode"（全局唯一、稳定）
            assertTrue(
                "${d.id} 的 id 应以 gameId 前缀 '${d.gameId}.' 开头",
                d.id.startsWith("${d.gameId}."),
            )
        }
    }
}
