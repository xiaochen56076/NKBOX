package com.utilitybox.domain.tool

import androidx.compose.runtime.Composable
import com.utilitybox.domain.model.GameDescriptor
import com.utilitybox.domain.model.ToolDescriptor

/**
 * 工具注册表 —— 面向 UI 的**只读视图**。
 *
 * 依据 docs/architecture.md §4：首页与工具列表从注册表读取，首页代码中不出现任何具体工具名。
 *
 * ★ 关键：本对象**不再自己写死工具列表**，而是从 [AppContainer] 读取各游戏模块
 *   自报的 provider。这样公共层就不会依赖具体游戏 ——
 *   接第二款游戏时，三角洲的代码一行都不用动（architecture.md §5.4）。
 */
object UbToolRegistry {

    /** 已接入的游戏（来自装配容器） */
    val games: List<GameDescriptor> get() = AppContainer.games.map { it.game }

    val activeGame: GameDescriptor get() = AppContainer.activeGame

    /** 全部工具元信息 */
    val tools: List<ToolDescriptor> get() = AppContainer.providers.map { it.descriptor }

    fun toolsOf(gameId: String): List<ToolDescriptor> =
        tools.filter { it.gameId == gameId }

    fun byId(toolId: String): ToolDescriptor? = tools.firstOrNull { it.id == toolId }

    fun byRoute(route: String): ToolDescriptor? = tools.firstOrNull { it.route == route }

    /**
     * 按路由查 provider —— **根导航靠它分发页面**，而不是写 `when` 分支。
     * 这是"新增工具不改根导航"能成立的关键。
     */
    fun providerByRoute(route: String): ToolProvider? =
        AppContainer.providers.firstOrNull { it.descriptor.route == route }

    /**
     * 某个游戏 tab 展示的工具：包含待数据源/实验性的（让用户知道有此功能），
     * 但**排除用户显式关闭的**（P09）。
     *
     * ★ 先读一次 [com.utilitybox.core.settings.UbSettingsState.toolsRevision]：
     *   Compose 只把「读过的状态」记为依赖。工具启停存在 store 里（不是 Compose 状态），
     *   若不读这个版本号，在设置页关掉工具后**首页不会重组**，
     *   表现为"开关点了没反应"。读它是让重组正确发生的最小代价。
     *
     * ★★ 参数从"当前激活游戏"改为**显式传入 gameId**（P11 结构调整）：
     *   现在同时展示三角洲与鸣潮两个 tab，不再有"当前激活游戏"这个隐含状态。
     *   隐式状态在单游戏时够用；多 tab 下会把两个游戏的工具混在一起。
     */
    fun homeTools(gameId: String): List<ToolDescriptor> {
        // 这一行看着没用，但它建立了重组依赖 —— 见上方说明，勿删
        @Suppress("UNUSED_EXPRESSION")
        com.utilitybox.core.settings.UbSettingsState.toolsRevision

        val hidden = hiddenFromToolList()
        return toolsOf(gameId).filterNot { isDisabled(it.id) || it.id in hidden }
    }

    /**
     * 首页要显示的"工具总数"（P11 补 80）。
     *
     * ★ 与 [toolsOf] 的区别：**排除** `showInToolList = false` 的工具
     *   （今日密码 —— 它的信息已经显示在首页摘要区了）。
     *   首页身份卡的「N 个工具 · M 个可用」必须和网格里**实际看得到的卡**对得上，
     *   否则会出现"写着 5 个工具、数出来只有 4 张卡"的假话。
     */
    fun visibleToolsOf(gameId: String): List<ToolDescriptor> {
        @Suppress("UNUSED_EXPRESSION")
        com.utilitybox.core.settings.UbSettingsState.toolsRevision

        val hidden = hiddenFromToolList()
        return toolsOf(gameId).filterNot { it.id in hidden }
    }

    /** 声明了「不出现在工具列表」的工具 id 集合 */
    private fun hiddenFromToolList(): Set<String> =
        AppContainer.providers
            .filter { !it.showInToolList }
            .map { it.descriptor.id }
            .toSet()

    /**
     * 用户是否显式关闭了某工具。
     *
     * ★ 未记录 ⇒ **默认启用**（见 SettingsStore 的说明：存"被关闭集合"而非"已启用集合"，
     *   这样新增工具默认可用，不需要在别处补登记）。
     */
    fun isDisabled(toolId: String): Boolean =
        com.utilitybox.core.settings.UbSettingsState.isToolDisabled(toolId)

    fun setDisabled(toolId: String, disabled: Boolean) {
        com.utilitybox.core.settings.UbSettingsState.setToolDisabled(toolId, disabled)
    }

    /** 设置页的「工具管理」用：某游戏的**全部**工具（含被关闭的），供逐个开关 */
    fun allToolsOf(gameId: String): List<ToolDescriptor> {
        @Suppress("UNUSED_EXPRESSION")
        com.utilitybox.core.settings.UbSettingsState.toolsRevision
        return toolsOf(gameId)
    }

    /** 工具管理页按游戏分组展示，需要知道哪些游戏**确实有工具**（鸣潮目前没有） */
    fun gamesWithTools(): List<GameDescriptor> {
        val ids = tools.map { it.gameId }.toSet()
        return games.filter { it.id in ids }
    }

    /**
     * 某游戏「首页摘要」的提供者（P11 补 79）。
     *
     * ★ 取第一个**已启用**且提供了摘要的工具。用户关掉那个工具后摘要也跟着消失 ——
     *   否则会出现"工具被关了，但它的数据还占着首页"的矛盾。
     *
     * ★ 返回类型是 `@Composable () -> Unit` 而不是某个具体数据：
     *   注册表不需要知道摘要长什么样，首页也不知道 —— 渲染完全在工具自己的包里。
     *
     * ★ 与 [homeTools] 一样先读 `toolsRevision`：工具启停不是 Compose 状态，
     *   不读它的话在设置里关掉工具后首页摘要不会消失。
     */
    fun homeSummaryOf(gameId: String): (@Composable () -> Unit)? {
        @Suppress("UNUSED_EXPRESSION")
        com.utilitybox.core.settings.UbSettingsState.toolsRevision

        return AppContainer.providers
            .firstOrNull {
                it.descriptor.gameId == gameId &&
                    !isDisabled(it.descriptor.id) &&
                    it.homeSummary != null
            }
            ?.homeSummary
    }

    // -----------------------------------------------------------------------
    // 启动自检：注册表是"单一事实来源"，必须保证自身一致。
    // 这些断言在首次访问时执行，配置错误会**立刻**暴露，而不是等到用户点进去才发现。
    // -----------------------------------------------------------------------
    private val integrityChecked: Boolean by lazy {
        checkIntegrity()
        true
    }

    /** 触发自检（在应用启动时调用一次；测试也可直接调用） */
    fun verifyIntegrity() {
        integrityChecked
    }

    private fun checkIntegrity() {
        val ps = AppContainer.providers
        val dupIds = ps.map { it.descriptor.id }.groupingBy { it }.eachCount().filter { it.value > 1 }
        check(dupIds.isEmpty()) { "工具 id 重复: $dupIds" }

        val dupRoutes = ps.map { it.descriptor.route }.groupingBy { it }.eachCount().filter { it.value > 1 }
        check(dupRoutes.isEmpty()) { "工具路由重复: $dupRoutes" }

        val gameIds = games.map { it.id }.toSet()
        ps.forEach { p ->
            check(p.descriptor.gameId in gameIds) {
                "工具 ${p.descriptor.id} 声明了未注册的游戏 ${p.descriptor.gameId}"
            }
        }

        // 路由不得为空，否则首页点了没有任何反应
        ps.forEach { p ->
            check(p.descriptor.route.isNotBlank()) { "工具 ${p.descriptor.id} 路由为空" }
        }
    }
}
