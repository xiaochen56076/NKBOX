package com.utilitybox.core.navigation

/**
 * 应用级路由。
 *
 * ★ 工具自己的路由**不在这里** —— 它们由各自的游戏模块拥有
 *   （见 `game/deltaforce/DeltaForceRoutes.kt`、`game/wutheringwaves/...`）。
 *
 * 为什么这样分：本文件属公共层。若把每个工具的路由都写在这里，
 * 每新增一个工具就要改一次**共享文件**，"新增工具不改公共代码"就不成立了。
 *
 * 采用自研轻量导航（P01 已实测可用，零新增依赖）。
 *
 * ---
 *
 * ★★ P11 结构调整：首页从"工具列表"改为**按游戏分区的 tab**
 * ```
 * [ 三角洲 ] [ 鸣潮 ]        ← 两个游戏 tab
 *          [预留]  [ 我的 ]  ← 右侧预留按钮 + 我的
 * ```
 * 每个游戏 tab 显示**它自己的工具**；「我的」放账号与设置入口。
 */
object UbRoutes {
    // ---- 游戏 tab（每个游戏一个） ----
    const val GAME_DELTA = "game/delta"
    const val GAME_WUWA = "game/wuwa"

    /**
     * 游戏 **id → 路由**（P11 补 47）。
     *
     * ### ★★ 为什么需要这个函数：它们是两个不同的东西
     *
     * 之前 MainActivity 里有这么一句注释（**是错的**）：
     *
     * > ~~「路由规律是 `game/<gameId>` —— 与 UbRoutes 的常量一致，
     * > 所以新增游戏时这里也不需要改（不用 when 枚举）」~~
     *
     * 实测证明这个假设不成立：
     *
     * | 游戏 | 注册表里的 id | 路由后缀 |
     * |---|---|---|
     * | 三角洲行动 | `deltaforce` | **`delta`** ✗ |
     * | 鸣潮 | `wuwa` | `wuwa` ✓ |
     *
     * **`wuwa` 只是碰巧同名**，所以「切换到 鸣潮」一直能走通，
     * 而「切换到 三角洲行动」拼出 `game/deltaforce` —— 未注册 → **「页面不存在」**。
     * 用户反馈的「点击后直接空界面报错」就是这个。
     *
     * ### 修法：把映射收进这里，只此一份
     *
     * 调用点全部改成 `UbRoutes.gameRoute(gameId)`，不再自己拼字符串。
     * 新增游戏时**只需在这里加一行**，漏了会被兜底分支暴露出来（而不是静默生成死路由）。
     *
     * ★ 兜底用 `game/$gameId` 而不是抛异常：万一将来 id 与路由一致，
     *   它能直接工作；不一致时也会走到「未注册」提示页，那是**可见的失败**，
     *   比静默跳错页好。
     */
    fun gameRoute(gameId: String): String = when (gameId) {
        "deltaforce" -> GAME_DELTA
        "wuwa" -> GAME_WUWA
        else -> "game/$gameId"
    }

    /**
     * 鸣潮「唤取记录」（抽卡记录）。
     *
     * ★ 做成**二级页**而不是塞进鸣潮标签页里：抽卡记录是一屏长列表，
     *   和标签页顶部的角色数据面板是两件事，混在一起会让标签页无限变长。
     */
    const val WUWA_GACHA = "wuwa/gacha"

    /**
     * 鸣潮图鉴（库街区公开 WIKI）。
     *
     * ★ **不需要登录** —— 走的是匿名 wiki 路由，与账号接口是两套鉴权。
     *
     * ### 为什么分成三级路由而不是一页内部切状态
     *
     * 分类 → 条目 → 词条 是三层，如果都塞进一个页面用内部状态切换，
     * **系统返回键会直接退出页面**而不是回上一层 —— 这正是用户之前明确抱怨过的
     * 「进入二级菜单，手机返回，结果退出软件」。所以每层一个路由，
     * 交给 [com.utilitybox.core.navigation.UbNavigator] 的栈来管。
     */
    const val WUWA_WIKI = "wuwa/wiki"

    const val PREFIX_WIKI_CAT = "wuwa/wiki/cat/"
    const val PREFIX_WIKI_ENTRY = "wuwa/wiki/entry/"

    fun wuwaWikiCategory(catalogueId: Int): String = PREFIX_WIKI_CAT + catalogueId
    fun wuwaWikiEntry(entryId: String): String = PREFIX_WIKI_ENTRY + entryId

    /** 「我的」：账号、设置入口 */
    const val MINE = "mine"

    /** 右下角预留按钮的落点 —— 目前是占位页，等需求（用户明确要求先预留） */
    const val EXPLORE = "explore"

    // ---- 设置（现在从「我的」右上角齿轮进入，并**按类别拆成三个入口**） ----
    const val SETTINGS = "settings"

    /** 设置分组：工具管理 */
    const val SETTINGS_TOOLS = "settings/tools"

    /** 设置分组：外观 */
    const val SETTINGS_APPEARANCE = "settings/appearance"

    /** 设置分组：数据（API Key / 缓存 / 数据源状态 / 自动刷新） */
    const val SETTINGS_DATA = "settings/data"

    /** 设置分组：交互（P11 补 24 —— 与工具管理/外观/数据并列的入口） */
    const val SETTINGS_INTERACTION = "settings/interaction"

    // ---- 数据相关的二级页 ----
    const val DATA_SOURCES = "settings/datasources"
    const val ABOUT = "settings/about"

    /** API Key 设置（P06：Key 由用户在设置页输入，不依赖构建期注入） */
    const val API_KEY = "settings/apikey"

    // ---- 账号 ----
    const val LOGIN = "mine/login"

    /**
     * 账号管理（P11 补 39）。
     *
     * ★ 用户原话：「如果是百度网盘那种需要登录，直接放到设置选项中的一个叫
     *   **账号管理**的选项中」。里面目前是 **WebDAV 云备份**的凭证与操作。
     */
    const val SETTINGS_ACCOUNT = "settings/account"

    /**
     * 唤取统计（P11 补 44）。
     *
     * ★ 用户要求统计「应该是一个**新的界面**，而不是仍然显示在那里下面」——
     *   所以从唤取记录页拆出来，成为同级二级页。
     */
    const val WUWA_GACHA_STATS = "wuwa/gacha/stats"

    /**
     * 云备份（P11 补 40）。
     *
     * ★ 用户要求「云备份是一个单独的选项，**进去才是**」——
     *   所以它是 [SETTINGS_ACCOUNT] 的下一级，账号管理页只放入口。
     */
    const val SETTINGS_CLOUD_BACKUP = "settings/account/cloud"

    /** P02 原型专用：状态组件演示页（错误/空数据态） */
    const val PROTO_STATES = "proto/states"

    /**
     * 玻璃验证页（诊断用，非产品页面）。
     *
     * 用途：在底部铺高低对比条纹**穿过悬浮导航栏**，
     * 用来证伪/证实"玻璃到底有没有在模糊"。
     * 正常页面做不到这件事 —— 内容被 `bottomBarReserved` 挡在栏外，
     * 栏后面本来就是空的，看不出玻璃行为。
     */
    const val GLASS_PROBE = "settings/glassprobe"

    /**
     * 根标签页集合：处于这些路由时，**底部导航可见**，
     * 且按返回键应先出"再按一次退出"提示而不是继续后退。
     *
     * ★★ P11 补 12 修掉的遗留缺陷：
     *
     *   `SETTINGS` 原本也在这个集合里 —— 那是 P11 之前「设置」还是底部第二个
     *   标签页时留下的。后来用户要求把设置移进「我的」右上角，路由本身
     *   从"根标签"变成了"二级页"，但**这个集合没有跟着改**。
     *
     *   后果：用户在设置页看到一条毫无意义的底部导航
     *   （用户反馈「设置界面下面很显然不能显示这个 tab 栏了」），
     *   而且它上面任何一个 tab 都不高亮 —— 因为当前路由根本不在那两项里。
     *
     *   ★ `GLASS_PROBE` **保留**在集合里：它是刻意做成"根标签"的开发自检页，
     *     存在的意义就是在**导航栏可见**的前提下铺高低对比条纹穿过栏，
     *     以此证伪/证实玻璃到底有没有在模糊。把它移出去等于废掉这个页面。
     *     有一条单测（`GLASS_PROBE 是根标签`）专门守住这个意图。
     *
     * **教训**：路由的角色（根标签 / 二级页）是**两处**定义的 ——
     *   跳转代码里用 `navigate` 还是 `switchTab`，以及这个集合。
     *   改了一处忘了另一处，界面就会自相矛盾，而且**不报错**。
     */
    val ROOT_TABS = setOf(
        GAME_DELTA,
        GAME_WUWA,
        MINE,
        EXPLORE,
        GLASS_PROBE,
    )
}
