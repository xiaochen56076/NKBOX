package com.utilitybox.game.wutheringwaves

import com.utilitybox.domain.model.GameDescriptor
import com.utilitybox.domain.tool.GameModule
import com.utilitybox.domain.tool.ToolProvider

/**
 * 鸣潮游戏模块（P11 接入）。
 *
 * ★ 本模块目前**只提供游戏身份，没有工具** —— 用户明确要求
 *   「鸣潮的界面可以选空着，但是 tab 栏要先预留做好…我会后面和你说需求的」。
 *
 * ★ 它的存在同时是架构承诺的第二次实测：
 *   接入第二款游戏只做了两件事 ——
 *   ① 新建本包（3 个文件）② 在 `AppContainer.games` 加一行。
 *   **三角洲的代码一行未改**（architecture.md §5.4）。
 *
 * 后续加工具时，照 `game/deltaforce/` 的样子写 provider 并加进 [providers] 即可，
 * 公共层同样不需要改动。
 */
object WutheringWavesModule : GameModule {

    override val game = GameDescriptor(
        id = "wuwa",
        name = "鸣潮",
        region = "CN",
        enabled = true,
    )

    /**
     * 暂无工具。
     *
     * 留空而不是删掉这个属性：`GameModule` 契约要求它存在，
     * 而且工具管理页会据此判断"这个游戏没有工具可管"，
     * 从而不显示一个空的分组标题。
     */
    override val providers: List<ToolProvider> = emptyList()
}

/** 鸣潮的路由常量。目前为空 —— 加工具时在这里加常量，与三角洲保持同一模式。 */
object WutheringWavesRoutes
