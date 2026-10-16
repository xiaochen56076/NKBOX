package com.utilitybox.domain.model

import java.time.LocalDate

/**
 * 数据来源标识 —— 每个数据都必须知道自己是哪来的。
 * 依据 docs/requirements.md §5.1。
 */
enum class SourceState {
    /** 本机实际调用成功并看到真实数据 */
    VERIFIED,

    /** 有文档或源码依据，但未实际调用确认 */
    PENDING,

    /** 存在但能力不足/条件苛刻，无法满足需求 */
    RESTRICTED,

    /** 不存在或确认无法使用 */
    UNAVAILABLE,
}

data class DataSourceRef(
    val id: String,
    val displayName: String,
    val verified: SourceState,
)

/** 带时间戳的数据封装 —— 让 UI 能区分"新鲜"与"陈旧" */
data class Fresh<T>(
    val value: T,
    val fetchedAtMillis: Long,
    val source: DataSourceRef,
    /** 依据 PROJECT_RULES.md §8：mock 必须可被识别 */
    val isMock: Boolean = false,
)

/** 统一错误模型 —— 每个错误类型都有对应文案与操作（docs/ui-design.md §3.8） */
sealed interface AppError {
    data object NoNetwork : AppError
    data object Timeout : AppError
    data class Server(val code: Int) : AppError

    /**
     * 本机**没有配置** API Key —— 请求根本没发出去。
     *
     * ★ 为什么必须与 [Unauthorized] 分开（P06 实测踩到的设计缺陷）：
     *   二者用户要采取的行动完全不同：
     *     - [ApiKeyMissing]：去设置页**填一个** Key
     *     - [Unauthorized]：Key 填了但服务端**不认**，需要检查/更换
     *   原先两者挤在 [Unauthorized] 里，导致"设置页说已配置、功能页说未配置"
     *   这种自相矛盾的界面。错误类型的粒度必须匹配用户可采取的行动。
     */
    data object ApiKeyMissing : AppError

    /** 服务端**拒绝**了鉴权（Key 无效/过期/权限不足）—— 请求已发出 */
    data object Unauthorized : AppError

    data object Malformed : AppError
    data class Business(val code: Int, val message: String) : AppError
    data class Unknown(val message: String) : AppError

    /** 数据源本身不可用（如好友状态） */
    data object SourceUnavailable : AppError
}

/** 统一状态模型 —— 所有模块复用，不各写一套 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    /** 请求成功且确实有数据 */
    data class Success<T>(val data: T) : UiState<T>

    /** 请求成功但确实没有数据（区别于失败） */
    data object Empty : UiState<Nothing>

    data class Failed(val error: AppError) : UiState<Nothing>
}

// ---------------------------------------------------------------------------
// 工具与游戏（扩展核心）
// ---------------------------------------------------------------------------

enum class ToolCategory(val label: String) {
    GAME_DATA("游戏数据"),
    PRICE("价格"),
    UTILITY("实用"),
    PERSONAL("个人"),
}

enum class ToolStatus {
    /** 正常可用 */
    ENABLED,

    /** 功能已实现，但**数据源尚未就绪**，暂时无法使用。保留接口、将来可点亮 */
    PENDING_SOURCE,

    /** 实验性功能：可进入，但结论/数据可能不完整，界面须如实标注 */
    EXPERIMENTAL,

    /** 用户手动关闭 */
    DISABLED,

    /** 确认无法实现（例如没有任何数据源且短期无望） */
    UNAVAILABLE,
}

data class ToolDescriptor(
    /** 全局唯一、稳定，如 "deltaforce.dailycode" */
    val id: String,
    val gameId: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val icon: com.utilitybox.core.design.UbIcon,
    val route: String,
    val status: ToolStatus = ToolStatus.ENABLED,
    val requiresLogin: Boolean = false,
    /** 工具卡片上的一行状态说明（面向用户，必须诚实） */
    val statusLabel: String? = null,
    /** 详情页的完整原因说明 */
    val unavailableReason: String? = null,
)

data class GameDescriptor(
    val id: String,
    val name: String,
    val region: String,
    val enabled: Boolean = true,
)

// ---------------------------------------------------------------------------
// 今日密码
// ---------------------------------------------------------------------------

data class DailyCode(
    val mapId: String,
    val mapName: String,
    /** 密码本体；空串表示数据源未给出 */
    val code: String,
)

/**
 * 今日密码四态。
 *
 * ★ 依据红线 PROJECT_RULES.md §3.4/§3.5：
 *   - 必须区分「当天有效 / 过期缓存 / 暂无数据 / 请求失败」
 *   - **不得拿昨天的密码冒充今日密码**
 *
 * ★ 已实测（P01）：`/api/v1/df/tools/dailykeyword` 返回字段仅
 *   `mapID` / `mapName` / `secret`，**没有任何日期字段**。
 *   因此 [Stale] 必须携带本机抓取日期，UI 必须写明判定依据。
 */
sealed interface DailyCodeState {
    /** 当天有效数据 */
    data class Today(
        val codes: List<DailyCode>,
        val source: DataSourceRef,
        val updatedAtLabel: String,
        val isMock: Boolean = false,
    ) : DailyCodeState

    /** 过期缓存：必须携带原始抓取日期 */
    data class Stale(
        val codes: List<DailyCode>,
        val fetchedDate: LocalDate,
        val source: DataSourceRef,
        val isMock: Boolean = false,
    ) : DailyCodeState

    /** 请求成功但数据源没给今天的密码 */
    data object NoDataForToday : DailyCodeState

    data class Failed(val error: AppError) : DailyCodeState
}

// ---------------------------------------------------------------------------
// 物品物价
// ---------------------------------------------------------------------------

/**
 * 账号资料（P11 新增）。
 *
 * ★ 本期**没有后端**：本地昵称是真实可用的登录态；
 *   QQ 登录需要腾讯开放平台 SDK，尚未接入（见 `core/account/AccountStore.kt`）。
 *   UI 必须如实区分这两种状态，**不得让本地昵称看起来像 QQ 已登录**。
 */
data class AccountProfile(
    val isLoggedIn: Boolean,
    val nickname: String?,
    val provider: Provider,
    /** QQ 接入后才有；本地登录恒为 null */
    val avatarUrl: String?,
) {
    enum class Provider {
        /** 未登录 */
        NONE,

        /** 本地昵称（无后端，仅保存在本机） */
        LOCAL,

        /** QQ 登录（预留，待接入 SDK） */
        QQ,
    }

    /** 当前是否是真的第三方账号登录（本地昵称不算） */
    val isThirdParty: Boolean get() = provider == Provider.QQ

    companion object {
        val LoggedOut = AccountProfile(
            isLoggedIn = false,
            nickname = null,
            provider = Provider.NONE,
            avatarUrl = null,
        )
    }
}

/**
 * 物品条目摘要（搜索结果）。
 *
 * 字段名与接口保持一致，避免阅读代码时来回对照。
 */
data class ItemSummary(
    val objectId: String,
    val name: String,
    val shortName: String?,
    val grade: Int?,
    val category: String?,
    /** null ⇒ 无成交数据（**不是 0**） */
    val avgPrice: Long?,
    /**
     * 物品图片 URL（接口的 `pic` 字段）。
     *
     * ★ 来源是**腾讯官方图床** `playerhub.df.qq.com`，不是第三方站的服务器。
     *   实测确认（P10）：物品搜索与弹药列表都返回该字段。
     *   null ⇒ 该条目没有图片，UI 应显示占位而不是空白。
     */
    val iconUrl: String? = null,
    /**
     * 一级分类（接口的 `primaryClass`）：`gun` / `ammo` / `acc` / `prop` 等。
     *
     * ★ 用来做分类筛选。皮肤与实物的 `primaryClass` 相同（都是 `gun`），
     *   所以**不能靠它区分皮肤**（见 [isSkin]）。
     */
    val primaryClass: String? = null,
    /** 二级分类中文名（接口的 `secondClassCN`），如「弹匣」「枪管」 */
    val categoryCn: String? = null,
)

/**
 * 名称看起来像**皮肤/涂装**而不是实物。
 *
 * ★ 实测依据（P10）：搜索「M250」返回的条目里，
 *   `M250通用机枪-三角洲力量`、`M250通用机枪-冥夜蔷薇` 这类是枪械皮肤，
 *   而 `M250 75发弹链`、`M250通用机枪` 是实物。
 *   可观察的差别是**名称里带连字符后缀**。
 *
 * ★ 但这只是启发式判断，**不能当作事实展示**：
 *   所以 UI 提供「隐藏皮肤」开关让用户自己决定，而不是我们替他过滤掉。
 *   宁可多给一个开关，也不要悄悄吞掉用户可能想找的东西。
 */
fun ItemSummary.looksLikeSkin(): Boolean {
    val n = name
    val dash = n.indexOf('-').takeIf { it > 0 } ?: return false
    // 后缀要够长才像皮肤名（避免把 "6.8x51mm AP" 这类误判）
    return n.length - dash - 1 >= 2
}

data class PricePoint(
    val date: LocalDate,
    /** null ⇒ 该日无成交记录（图表必须留空断线） */
    val avgPrice: Long?,
    /** 该日真实样本数；0 表示无成交 */
    val sampleCount: Int,
)

enum class PriceTrend(val label: String) {
    UP("↑"),
    DOWN("↓"),
    FLAT("→"),
    /** 无法计算（数据点不足）—— 不得伪装成持平 */
    UNKNOWN("—"),
}

data class PriceHistory(
    val objectId: String,
    val points: List<PricePoint>,
    val source: DataSourceRef,
    val isMock: Boolean = false,
) {
    /** 仅用真实有效点计算；无有效点时返回 UNKNOWN，不伪造 */
    val trend: PriceTrend
        get() {
            val valid = points.mapNotNull { it.avgPrice }
            if (valid.size < 2) return PriceTrend.UNKNOWN
            val first = valid.first()
            val last = valid.last()
            return when {
                last > first -> PriceTrend.UP
                last < first -> PriceTrend.DOWN
                else -> PriceTrend.FLAT
            }
        }

    /** 首尾变化百分比；有效点不足时为 null */
    val changePercent: Double?
        get() {
            val valid = points.mapNotNull { it.avgPrice }
            if (valid.size < 2) return null
            val first = valid.first().toDouble()
            if (first == 0.0) return null
            return (valid.last() - first) / first * 100.0
        }
}

data class ItemDetail(
    val summary: ItemSummary,
    /** 实时价；null ⇒ 未取到 */
    val latestPrice: Long?,
    val priceUpdatedAtLabel: String?,
    /** 取自接口的 hasValidPriceData；false ⇒ UI 必须显示「暂无成交数据」 */
    val hasValidPriceData: Boolean,
    val source: DataSourceRef,
    val isMock: Boolean = false,
)

/**
 * 弹药条目（P10 新增工具「弹药物价」）。
 *
 * ★ 红线同样适用：`latestPrice == null` ⇒ 显示「暂无成交数据」，**不显示 0**。
 *   与物品物价的区别：弹药接口**自带** `hasValidPriceData` 字段，
 *   所以这里额外保留原始标记 [hasValidPriceData]，便于对照接口原话排查。
 */
data class AmmoPrice(
    val objectId: String,
    val name: String,
    val caliber: String?,
    val grade: Int?,
    /** 最新成交均价；null ⇒ 该弹药近期无成交 */
    val latestPrice: Long?,
    /** 涨跌百分比；null ⇒ 无法计算（不伪造为 0） */
    val changePercent: Double?,
    /** 接口原样返回的标记，仅用于排查，不参与展示判断 */
    val hasValidPriceData: Boolean,
    val source: DataSourceRef,
    val isMock: Boolean = false,
    /** 图片 URL（接口 `pic`，腾讯官方图床）；null ⇒ 无图 */
    val iconUrl: String? = null,
    /**
     * 穿透等级（`ammoDetail.penetrationLevel`）—— 这是玩家真正关心的核心属性。
     *
     * ★ 与 [grade] 是**两个不同概念**：
     *   [grade] 是稀有度（★1–★6），penetrationLevel 是穿透等级（1–6 级弹）。
     *   用户说的"一级还是二级"指的正是后者。
     */
    val penetrationLevel: Int? = null,
    /** 护甲伤害等级描述（`ammoDetail.armorHarmLevel`），如「高」 */
    val armorHarmLevel: String? = null,
    /** 口径分类中文名（`secondClassCN`），用于筛选 */
    val caliberGroup: String? = null,
    /**
     * 价格样本数（`priceStats.dataPoints`）。
     *
     * ★ 这个字段决定**涨跌幅能不能显示**：
     *   样本数为 0 时，接口返回的 `priceChangePercent: 0` 不是"价格没变"，
     *   而是"根本没有可用于比较的成交"。把 0 显示成「↑ 0.0%」是在编数据。
     */
    val dataPoints: Int? = null,
)

/**
 * 物品详情页的数据状态。
 *
 * ★ 为什么要单独定义而不是复用 `UiState<ItemDetail>`：
 *   详情页是**两个独立请求**的组合（当前价 + 历史）。二者可以一个成功一个失败，
 *   而 `UiState` 只能表达整体的成功/失败 —— 那会丢失信息：
 *   "价格取到了但历史失败了"与"两个都失败了"对用户是**不同**的处境，
 *   前者应该照常显示价格，只在图表位置说明历史取不到。
 *
 * ★ [NoPriceData] 是**独立状态**而不是 [Failed]：
 *   红线要求「`hasValidPriceData: false` 时显示『暂无成交数据』，不显示 0 为真实价格」。
 *   把它混进错误态会让用户以为是故障，而实际上这只是"该物品今天没人成交"。
 */
sealed interface ItemPriceDetailState {
    /** 还没开始加载 */
    data object Idle : ItemPriceDetailState

    data class Ready(
        val detail: ItemDetail,
        /** 历史取不到时为 null；此时只影响图表区，不影响价格展示 */
        val history: PriceHistory?,
        /** 历史请求失败的原始错误，供图表区如实说明 */
        val historyError: AppError?,
    ) : ItemPriceDetailState

    /** 请求成功，但数据源明确表示**该物品没有有效成交数据** */
    data object NoPriceData : ItemPriceDetailState

    data class Failed(val error: AppError) : ItemPriceDetailState
}

// ---------------------------------------------------------------------------
// 好友状态
// ---------------------------------------------------------------------------

/**
 * 好友状态四态。
 * ★ 红线：`UNKNOWN` **绝不**自动降级为 `OFFLINE`；排序固定为 游戏中→在线→离线→未知。
 * ★ 且：只有数据源能可靠提供「游戏中」时才可出现 [IN_GAME]。
 */
enum class FriendStatus(val label: String) {
    IN_GAME("游戏中"),
    ONLINE("在线"),
    OFFLINE("离线"),
    UNKNOWN("未知"),
}

data class Friend(
    val id: String,
    /** null ⇒ 未取到昵称，UI 显示脱敏 ID 尾号 */
    val displayName: String?,
    val status: FriendStatus,
    val statusSource: DataSourceRef,
    val statusKnownAtLabel: String?,
    val lastSeenLabel: String?,
)

data class FriendGroup(
    val status: FriendStatus,
    val friends: List<Friend>,
)

/** 分组排序（纯函数，可单测）。未知分组排在最后，且不与其他组合并。 */
fun groupAndSortFriends(friends: List<Friend>): List<FriendGroup> =
    FriendStatus.entries
        .map { st -> FriendGroup(st, friends.filter { it.status == st }) }
        .filter { it.friends.isNotEmpty() }

// ---------------------------------------------------------------------------
// 设置
// ---------------------------------------------------------------------------

data class UbSettings(
    /**
     * 主题模式。
     *
     * ★ 默认 [UbThemeMode.System]（跟随系统）而不是写死浅色：
     *   1. 这是 Android/iOS 的通行默认，也是用户对"应用应该适应我的设置"的预期
     *   2. **实测教训（P09）**：写死浅色时，系统处于深色的用户打开应用会看到
     *      一个亮色界面，第一反应就是"这应用坏了"。默认跟随系统能从根上避免这类困惑。
     */
    val themeMode: com.utilitybox.core.design.UbThemeMode =
        com.utilitybox.core.design.UbThemeMode.System,
    val glassEnabled: Boolean = true,
    val reduceMotion: Boolean = false,

    /**
     * 是否在游戏身份卡上显示「切换到 XX」入口。
     *
     * ★ 默认开启，但**做成开关**：用户 P11 补 22 要求
     *   「这个切换可有可无，加个开关」。
     *
     * ★ 为什么它和 [reduceMotion] 放在一起：两者都是**交互偏好** ——
     *   一个控制"动不动"，一个控制"多不多一个入口"，
     *   都属于"这个界面按我的习惯来"，而不是外观或数据。
     */
    val showGameSwitch: Boolean = true,

    val autoRefreshEnabled: Boolean = true,
    val autoRefreshMinutes: Int = 30,

    /**
     * 全局主题色（强调色）种子，`0xAARRGGBB`。
     *
     * ★ 为什么是 `Long?` 而不是 `Color`：
     *   本文件属于 `domain/model`，**不依赖 Compose**（`Color` 是 UI 类型）。
     *   存整型让领域模型保持纯净，转换放在 `core/design` 里做。
     *
     * ★ 为什么用 `null` 表示"默认"而不是存一个具体蓝色：
     *   默认色是**随主题变化的**（浅色 `#2F6BFF` / 深色 `#5B8CFF`）。
     *   若把默认值也落盘成具体颜色，用户一旦"恢复默认"就会被钉死在浅色那支，
     *   深色主题下拿到一个偏暗的强调色。`null` = 不覆盖，交给主题自己决定。
     */
    val accentSeed: Long? = null,

    /**
     * 自定义启动页背景图的**应用私有目录内路径**（不是 content:// URI）。
     *
     * ★ 为什么落盘成路径而不是保存外部 URI：
     *   外部 URI 需要 `takePersistableUriPermission` 才能跨重启存活，
     *   而用户随时可能在相册里删掉那张图 / 撤销授权 —— 那时启动页会变空白。
     *   选中即**复制进应用私有目录**，之后不再依赖任何外部授权，
     *   删掉原图也不影响（代价是占一份存储，已做降采样控制体积）。
     */
    val splashImagePath: String? = null,
)
