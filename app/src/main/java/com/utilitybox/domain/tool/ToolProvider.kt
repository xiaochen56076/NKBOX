package com.utilitybox.domain.tool

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.utilitybox.domain.model.ToolDescriptor

/**
 * 工具提供者 —— **扩展机制的核心契约**。
 *
 * 依据 docs/architecture.md §4.4「新增一个工具的完整清单」：
 * 新增工具需要 ① 建包 ② 定模型 ③ 实现数据层 ④ 实现页面 ⑤ **实现 ToolProvider**
 * ⑥ 在游戏模块暴露它 ⑦ 在 `AppContainer.providers` 加一项。
 *
 * ★ 关键点：**页面与路由由 provider 自带**，集中在注册表里。
 *   因此根导航不再需要为每个工具写一个 `when` 分支 ——
 *   这正是"不改首页、不重写导航"能成立的原因。
 *
 * ★ provider 声明 [descriptor]（元信息）与 [screen]（页面），二者必须描述同一个工具；
 *   由注册表负责校验一致性（见 `UbToolRegistry` 的启动断言）。
 */
interface ToolProvider {

    /** 工具的元信息：名称、图标、分类、状态、**路由** */
    val descriptor: ToolDescriptor

    /**
     * 该工具的工具页内容。
     *
     * 传入的 [nav] 由外层导航提供 —— 页面自己不需要知道导航是怎么实现的
     * （自研导航、官方导航库、甚至将来的多窗口，对工具页面都是透明的）。
     * **注意**：provider 只提供"内容"，统一的标题栏与返回入口由
     * `UbToolPage` 外壳负责，保证视觉一致 —— 各工具不必重复实现。
     */
    @Composable
    fun screen(nav: ToolNavActions, modifier: Modifier)
}

/**
 * 宿主向工具页面提供的导航能力。
 *
 * ★ 存在的意义：工具的二级页面（例如物品详情）需要能被打开，
 *   但工具**不应该**因此依赖根导航器或写死路由。
 *   由宿主把能力注入进来，工具只调它需要的动作。
 *
 * ★ 为什么不让工具直接持有一个"跳任意路由"的方法：
 *   那等于把整个路由表暴露给工具，会重新引入隐式耦合（architecture.md R6）。
 *   这里**只暴露被明确批准的动作**，新增动作需要双方显式改动。
 */
interface ToolNavActions {

    /**
     * 打开物品详情页。
     *
     * ★ 传完整 [com.utilitybox.domain.model.ItemSummary] 而不是只传 id：
     *   详情页要显示物品名称与稀有度，而这些在列表里已经有了。
     *   只传 id 会迫使详情页再去查一次名称 —— 多一次请求，且首屏会先空一下。
     */
    fun openItemDetail(item: com.utilitybox.domain.model.ItemSummary)
}

/** 空实现：供测试或不需要导航能力的场景使用 */
object NoOpToolNav : ToolNavActions {
    override fun openItemDetail(item: com.utilitybox.domain.model.ItemSummary) = Unit
}

/**
 * 游戏模块契约。
 *
 * 依据 architecture.md §5.4：新增游戏 = 建 `game/<id>/` 包 + 实现本接口 +
 * 在 `AppContainer.games` 加一项，**其他游戏的代码 0 改动**。
 *
 * 公共层（`core/`、`feature/`）**不得**依赖任何具体游戏模块，
 * 只依赖本接口 —— 这样将来接入第二款游戏时，三角洲的代码一行都不用动。
 */
interface GameModule {

    /** 游戏元信息 */
    val game: com.utilitybox.domain.model.GameDescriptor

    /** 本游戏提供的全部工具 */
    val providers: List<ToolProvider>
}
