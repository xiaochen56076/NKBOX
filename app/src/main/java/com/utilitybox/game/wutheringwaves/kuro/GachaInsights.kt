package com.utilitybox.game.wutheringwaves.kuro

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 唤取记录的进阶统计（P11 补 38）。
 *
 * ★ 全部是**纯函数**，不依赖 Android/Compose —— 因为这里的错误
 *   **界面上完全看不出来**：数字照样显示，只是不对。
 *   上一轮那个"去重吃掉 39 条记录"就是同一类问题。
 *
 * ### 哪些能算、哪些不能（必须分清）
 *
 * | 指标 | 依据 | 可靠性 |
 * |---|---|---|
 * | 总抽数 / 各稀有度数量 | 记录直接数 | ✅ 精确 |
 * | 平均出金 | 总抽数 ÷ 五星数 | ✅ 精确 |
 * | 每个五星"垫了多少抽" | 记录顺序 | ✅ 精确 |
 * | 已垫抽数 | 最后一条五星之后的抽数 | ✅ 精确 |
 * | 按卡池分段 | 相邻记录时间间隔 | ✅ 精确 |
 * | 星声花费 | 抽数 × 160 | ✅ 精确（单价是固定值） |
 * | **UP / 歪** | **常驻池里出过的五星名字** | ⚠️ **推导，可能不准** |
 *
 * ### ★ UP 判定为什么只是"推导"
 *
 * 接口**不告诉你哪一个是 UP**。这里用的办法是：
 *
 * > 常驻池（角色常驻 / 武器常驻）里出过的五星 = 常驻名单。
 * > 活动池里出的五星，名字在名单里 → **歪**；不在 → **UP**。
 *
 * 已知局限：如果你**从没在常驻池抽到过**某个常驻五星，它会被误判成 UP。
 * 所以 [GachaInsights.standardSampleSize] 会被如实暴露给界面，
 * 样本太小时必须**在界面上标出来**，而不是假装精确。
 */
data class GachaInsights(
    val total: Int,
    val fiveStar: Int,
    val fourStar: Int,
    val threeStar: Int,
    /** 平均出金（抽/个）。没有五星时为 null —— **不编造成 0 或 ∞** */
    val avgFiveStar: Double?,
    /** 每个卡池的汇总 */
    val perPool: List<PoolSummary>,
    /** 按时间分段出的卡池期 */
    val periods: List<PoolPeriod>,
    /** 五星逐个列出（含"垫了多少抽"） */
    val fiveStarPulls: List<FiveStarPull>,
    /** 各池「已垫」抽数：该池最后一条五星之后抽了多少 */
    val pity: Map<Int, Int>,
    /** 从常驻池推导出的常驻五星名单 */
    val standardNames: Set<String>,
    /** 推导名单的样本量（常驻池五星个数）。太小则 UP 判定不可信 */
    val standardSampleSize: Int,
    /** 活动池里 UP 五星的平均抽数（角色） */
    val upCharacterAvg: Double?,
    /** 活动池里 UP 五星的平均抽数（武器） */
    val upWeaponAvg: Double?,
    /** 小保底不歪：活动池五星中判为 UP 的比例 */
    val winRate: Double?,
    /** 活动池五星总数（winRate 的分母，用于判断样本是否够） */
    val eventFiveStarCount: Int,
    /**
     * UP 相关指标是否可信。
     *
     * ★ `false` 时 [upCharacterAvg] / [upWeaponAvg] / [winRate] 一定是 `null`，
     *   而且**界面上必须说明原因** —— 不能只是空着，
     *   用户会以为"没数据"，实际是"算不准所以不算"。
     *
     * 判据：[standardSampleSize] 是否达到 [MIN_STANDARD_SAMPLE]。
     */
    val upStatsReliable: Boolean,
)

/** 一个卡池的汇总 */
data class PoolSummary(
    val poolType: Int,
    val label: String,
    val total: Int,
    val fiveStar: Int,
    val fourStar: Int,
    /** 该池的星声花费（抽数 × 160） */
    val astrite: Long,
)

/** 一个卡池期（按时间间隔切出来的一段） */
data class PoolPeriod(
    val startTime: String,
    val endTime: String,
    val total: Int,
    val fiveStar: Int,
    val fourStar: Int,
    val astrite: Long,
    /** 这一段里获得的五星，按时间排序 */
    val fiveStarNames: List<String>,
)

/** 一次五星，以及它"垫了多少抽" */
data class FiveStarPull(
    val name: String,
    val resourceType: String,
    val time: String,
    val poolType: Int,
    /** 距上一次五星（或该池开头）抽了多少抽 */
    val pityUsed: Int,
    /** true = UP，false = 歪，null = **无法判定**（常驻池 / 名单样本不足）*/
    val isUp: Boolean?,
)

/** 单抽消耗的星声（固定值，不是估的） */
const val ASTRITE_PER_PULL = 160L

/** 活动池：角色活动 / 武器活动 */
private val EVENT_POOLS = setOf(1, 2)

/** 常驻池：角色常驻 / 武器常驻 */
private val STANDARD_POOLS = setOf(3, 4)

/**
 * 相邻两次抽卡间隔超过这么多**天**，就切出一个新的「活跃时段」。
 *
 * ★★ 这个常量我改过三次，前两次的论证都是**错的**，如实记下来（P11 补 43）：
 *
 * **第一次：12 小时** —— 想当然认为那是"一次上号"的边界。
 *   结果 848 条被切成 **72 段**，每段 1~2 抽，没有任何意义。
 *
 * **第二次：20 天** —— 我看到「04-30 → 05-21 = 21 天、05-21 → 07-10 = 50 天」
 *   就认定那是版本分界。**但这个观察本身是错的**：那些日期是我用 12 小时阈值
 *   切出来的**活跃簇起止点**，不是相邻记录的真实间隔。
 *   真实数据里**相邻记录的最大间隔只有 12 天**（见下），所以 20 天阈值
 *   切出来的是 **1 段**（整段历史），我却在模拟器上把它当成了"分段成功"。
 *
 * **实测的真实间隔分布**（模拟器 848 条缓存，独立用 Python 复算）：
 *
 * ```
 *   7 天   08-01 → 08-09      12 天   06-23 → 07-06
 *   8 天   05-13 → 05-21      12 天   09-04 → 09-17
 *  11 天   08-09 → 08-20      12 天   09-17 → 09-30
 * ```
 *
 * **所以：版本边界根本无法从时间间隔推导出来。** 用户每隔几天就抽一点，
 * 版本更替的时候并没有"空档"。真实版本分界（V3.6 = 08-20、V3.7 = 09-30）
 * 是我们从第三方工具的截图上**读到的**，不是从间隔算出来的。
 *
 * 取 **10 天**：它是"能真正把这段数据切开"的最小合理值（最大间隔 12 天）。
 *
 * ★ 因此界面上这块叫**「活跃时段」而不是「版本总结」** ——
 *   它统计的是"我这段时间抽了多少"，不是"这个版本我抽了多少"。
 *   叫后者就是给一个数据支撑不了的名字。
 */
private const val PERIOD_GAP_DAYS = 10

/**
 * 「常驻名单」至少要有这么多个五星，UP 判定才敢显示。
 *
 * ★★ 这个门槛是**被真实数据逼出来的**（P11 补 38）。
 *
 * 常驻名单的推导办法是"常驻池里出过的五星"。但本机这份 848 条数据里，
 * 常驻池一共只抽了 120 次、只出过 **1 个**五星（维里奈）。
 *
 * 于是名单 = {维里奈}，而**卡卡罗、鉴心、安可 这些本来就是常驻角色**，
 * 因为没在常驻池抽到过，全被误判成了 UP —— 算出「不歪率 89.5%」。
 *
 * 真实的 50/50 不可能有 89.5%。**那就不是统计，是编造。**
 *
 * 所以：名单不够大时**不显示** UP 相关指标，并如实告诉用户为什么，
 * 而不是给一个看起来很专业的错数字。
 */
private const val MIN_STANDARD_SAMPLE = 4

private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

/**
 * 计算全部进阶统计。
 *
 * ★ `records` 的顺序**不影响结果** —— 内部先按时间排序。
 *   这样调用方不必关心缓存里是什么顺序。
 */
fun computeInsights(records: List<GachaRecord>): GachaInsights {
    // 按时间排序（字符串排序对 `yyyy-MM-dd HH:mm:ss` 就是时间序）
    val all = records.sortedBy { it.time }
    if (all.isEmpty()) return emptyGachaInsights()

    val five = all.filter { it.rarity == 5 }

    // ---- 常驻名单：从常驻池的五星推导 ----
    val standardNames = all
        .filter { it.poolType in STANDARD_POOLS && it.rarity == 5 }
        .map { it.name }
        .toSet()

    // ---- 逐个五星：算出"垫了多少抽" ----
    // ★ 按池分别计数：一个池的保底不会被另一个池消耗
    val lastFiveIdx = HashMap<Int, Int>()
    val pullIndex = HashMap<Int, Int>()
    val fiveStarPulls = ArrayList<FiveStarPull>()
    all.forEach { r ->
        val p = r.poolType
        // 每个池的当前抽数（从 1 开始）
        val n = (pullIndex[p] ?: 0) + r.count
        if (r.rarity == 5) {
            val used = n - (lastFiveIdx[p] ?: 0)
            val isUp = when {
                p in STANDARD_POOLS -> null // 常驻池没有 UP 概念
                p !in EVENT_POOLS -> null // 未知池不判
                // ★ 名单样本不足时**一律不判** —— 否则"不在名单里"会把
                //   每一个常驻角色都判成 UP（实测算出过 89.5% 的假不歪率）
                standardNames.size < MIN_STANDARD_SAMPLE -> null
                else -> r.name !in standardNames
            }
            fiveStarPulls += FiveStarPull(
                name = r.name,
                resourceType = r.resourceType,
                time = r.time,
                poolType = p,
                pityUsed = used,
                isUp = isUp,
            )
            lastFiveIdx[p] = n
        }
        pullIndex[p] = n
    }

    // ---- 已垫：每个池当前抽数 - 该池最后一次五星时的抽数 ----
    val pity = pullIndex.mapValues { (p, n) -> n - (lastFiveIdx[p] ?: 0) }

    // ---- 按池汇总 ----
    val perPool = all.groupBy { it.poolType }
        .map { (p, rs) ->
            PoolSummary(
                poolType = p,
                label = WuwaGachaApi.poolLabel(p),
                total = rs.sumOf { it.count },
                fiveStar = rs.count { it.rarity == 5 },
                fourStar = rs.count { it.rarity == 4 },
                astrite = rs.sumOf { it.count }.toLong() * ASTRITE_PER_PULL,
            )
        }
        .sortedBy { it.poolType }

    // ---- UP 统计：**只在名单样本够大时才算** ----
    val upReliable = standardNames.size >= MIN_STANDARD_SAMPLE
    val eventFive = if (upReliable) {
        fiveStarPulls.filter { it.poolType in EVENT_POOLS && it.isUp != null }
    } else {
        emptyList()
    }
    val eventUp = eventFive.filter { it.isUp == true }
    val winRate = if (eventFive.isEmpty()) null else eventUp.size.toDouble() / eventFive.size

    // "每 UP 需" = 该池总抽数 / 该池 UP 个数
    // ★ 分母为 0 时给 null，**不是 0** —— 0 会被读成"不需要抽"
    fun avgPerUp(pool: Int, kind: String): Double? {
        val poolTotal = perPool.firstOrNull { it.poolType == pool }?.total ?: return null
        val ups = eventUp.count { it.poolType == pool && it.resourceType == kind }
        return if (ups == 0) null else poolTotal.toDouble() / ups
    }

    return GachaInsights(
        total = all.sumOf { it.count },
        fiveStar = five.size,
        fourStar = all.count { it.rarity == 4 },
        threeStar = all.count { it.rarity == 3 },
        avgFiveStar = if (five.isEmpty()) null else all.sumOf { it.count }.toDouble() / five.size,
        perPool = perPool,
        periods = segmentPeriods(all),
        fiveStarPulls = fiveStarPulls,
        pity = pity,
        standardNames = standardNames,
        standardSampleSize = standardNames.size,
        upCharacterAvg = avgPerUp(1, "角色"),
        upWeaponAvg = avgPerUp(2, "武器"),
        winRate = winRate,
        eventFiveStarCount = eventFive.size,
        upStatsReliable = upReliable,
    )
}

/**
 * 按时间间隔把记录切成一个个「活跃时段」。
 *
 * ★ 为什么用**时间间隔**而不是版本号：
 *   接口不返回版本，而版本号需要外部日历（我没有可靠来源）。
 *
 * ★★ 但要说清一件事：**这切出来的不是游戏版本**（P11 补 43）。
 *   实测相邻记录最大间隔只有 12 天 —— 用户每隔几天就抽一点，
 *   版本更替时并没有空档，所以**版本边界无法从数据里推出来**。
 *   界面因此叫「活跃时段」，不叫「版本总结」。
 */
fun segmentPeriods(records: List<GachaRecord>): List<PoolPeriod> {
    if (records.isEmpty()) return emptyList()
    val sorted = records.sortedBy { it.time }
    val groups = ArrayList<MutableList<GachaRecord>>()
    var cur = ArrayList<GachaRecord>().also { it += sorted.first() }
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val now = sorted[i]
        if (hoursBetween(prev.time, now.time) > PERIOD_GAP_DAYS * 24L) {
            groups += cur
            cur = ArrayList()
        }
        cur += now
    }
    groups += cur

    return groups.map { g ->
        PoolPeriod(
            startTime = g.first().time,
            endTime = g.last().time,
            total = g.sumOf { it.count },
            fiveStar = g.count { it.rarity == 5 },
            fourStar = g.count { it.rarity == 4 },
            astrite = g.sumOf { it.count }.toLong() * ASTRITE_PER_PULL,
            fiveStarNames = g.filter { it.rarity == 5 }.map { it.name },
        )
    }
}

/** 两个时间字符串相差多少小时。解析失败返回 0（不切断，宁可少分段也不误分）*/
private fun hoursBetween(a: String, b: String): Long = runCatching {
    val ta: Date = TIME_FMT.parse(a)!!
    val tb: Date = TIME_FMT.parse(b)!!
    (tb.time - ta.time) / 3_600_000L
}.getOrDefault(0L)

private fun emptyGachaInsights() = GachaInsights(
    total = 0, fiveStar = 0, fourStar = 0, threeStar = 0,
    avgFiveStar = null, perPool = emptyList(), periods = emptyList(),
    fiveStarPulls = emptyList(), pity = emptyMap(),
    standardNames = emptySet(), standardSampleSize = 0,
    upCharacterAvg = null, upWeaponAvg = null,
    winRate = null, eventFiveStarCount = 0,
    // 空数据当然不可信 —— 说"可信"就是在撒谎
    upStatsReliable = false,
)
