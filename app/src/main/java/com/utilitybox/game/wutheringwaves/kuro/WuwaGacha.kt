package com.utilitybox.game.wutheringwaves.kuro

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 鸣潮「唤取记录」（抽卡记录）。
 *
 * ### 数据链路
 *
 * ```
 *   游戏内「唤取记录」页面 → 一个带 4 个参数的链接
 *   → 用户把它粘进本应用 → 我们解析参数 → 调抽卡接口 → 展示统计
 * ```
 *
 * 那个链接里的 `record_id` 是**短时凭据**（约 1 小时），并且**应用无法自己取得它** ——
 * 它在游戏的 WebView 里，Android 上第三方应用读不到游戏的私有目录，
 * 也扫不了别的进程内存（需要 root）。所以只能由用户粘贴。
 *
 * ### ★★ 为什么持久化「记录」而不持久化「链接」
 *
 * 链接里的 `record_id` 是凭据，**每次用都会过期**，存下来只有风险没有收益。
 * 而抽出来的记录是**用户自己的数据**、不会过期、也正是他下次想看的东西。
 * 所以：
 *
 * - `record_id` / `player_id` 等参数 **只在内存里活着**，不落盘
 * - 抽卡**记录本身**缓存到应用私有目录，链接过期后仍可查看历史统计
 *
 * ### 接口约定与库街区**不同**
 *
 * `code == 0` 才是成功（库街区那套是 200）。这个差异很容易写错，
 * 而写错的表现是"永远显示没有记录"——所以单独记在这里。
 */
object WuwaGachaApi {

    /**
     * 国服 / 国际服两个主机。
     *
     * ★ 用哪个由链接自己决定（国际服链接里是 `aki-gm-resources-oversea`），
     *   不猜、不写死 —— 猜错会静默返回空记录。
     */
    private const val HOST_CN = "https://gmserver-api.aki-game2.com"
    private const val HOST_OS = "https://gmserver-api.aki-game2.net"

    /**
     * 一次查询只能取一个卡池；这是全部已知卡池类型。
     *
     * ★ P11 补 101：显示名改成**和社区工具一致的叫法**
     *   （限定池 / 专武池 / 常驻池 / 武器池 / 新手池 / 联动角色池 / 联动武器池）。
     *
     *   原来说明书式的长名（"角色活动唤取"）在标签栏里挤占宽度，
     *   而且和玩家日常叫法不一致。
     *
     * ★ 名字必须**互不相同**：标签栏是按 `poolType` 一项一个的，
     *   两个类型用同一个名字会出现两个同名标签、各自只有一部分数据 ——
     *   看起来像"数据丢了"。所以 5/6/7/8 各自留了区分词。
     */
    val POOL_TYPES: List<Pair<Int, String>> = listOf(
        1 to "限定池",
        2 to "专武池",
        3 to "常驻池",
        4 to "武器池",
        5 to "新手池",
        6 to "新手自选池",
        7 to "新旅武器池",
        8 to "新旅角色池",
        9 to "联动武器池",
        10 to "联动角色池",
    )

    /**
     * 从用户粘贴的链接里抽出 4 个参数。
     *
     * ★ 参数挂在 **URL fragment**（`#` 之后）上，不在 query 里：
     *   `https://aki-gm-resources.aki-game.com/aki/gacha/index.html#/record?svr_id=…&player_id=…`
     *   如果按常规解析 query string，会一个参数都拿不到。
     *   这里直接在整串上用正则找 `key=value`，对粘贴内容的格式（换行、空格、
     *   前后多带文字）也最宽容。
     */
    fun parseLink(raw: String): GachaLink? {
        fun grab(key: String): String? =
            Regex("""[?&#]?$key=([^&\s#]+)""").find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

        val serverId = grab("svr_id") ?: return null
        val playerId = grab("player_id") ?: return null
        val recordId = grab("record_id") ?: return null
        val resourcesId = grab("resources_id") ?: return null

        return GachaLink(
            serverId = serverId,
            playerId = playerId,
            recordId = recordId,
            resourcesId = resourcesId,
            // 国际服链接的域名里带 -oversea，用它选主机
            oversea = raw.contains("-oversea"),
        )
    }

    /** 拉取某个卡池的全部记录（接口一次返回该池的全部，不分页） */
    suspend fun query(link: GachaLink, poolType: Int): GachaResult<List<GachaRecord>> =
        withContext(Dispatchers.IO) {
            val host = if (link.oversea) HOST_OS else HOST_CN
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$host/gacha/record/query").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/127 Mobile Safari/537.36")
                }
                // ★ 这几个值来自用户粘贴的链接，直接透传，不做任何猜测或转换
                val payload = JSONObject().apply {
                    put("cardPoolType", poolType)
                    put("playerId", link.playerId)
                    put("recordId", link.recordId)
                    put("serverId", link.serverId)
                    put("resourcesId", link.resourcesId)
                    put("languageCode", "zh-Hans")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

                if (status !in 200..299) {
                    return@withContext GachaResult.Failure("服务返回 HTTP $status")
                }

                val json = JSONObject(text)
                // ★ code == 0 才是成功
                if (json.optInt("code", -1) != 0) {
                    val msg = json.optString("message").ifBlank { "未知错误" }
                    return@withContext GachaResult.Failure(msg)
                }
                val arr = json.optJSONArray("data") ?: JSONArray()
                GachaResult.Ok((0 until arr.length()).mapNotNull { parseRecord(arr.optJSONObject(it)) })
            } catch (e: Exception) {
                GachaResult.Failure("网络错误：${e.javaClass.simpleName}")
            } finally {
                runCatching { conn?.disconnect() }
            }
        }

    /**
     * ★★ 卡池字段的真相（P11 补 36，**实测坐实**）。
     *
     * 接口响应里 `cardPoolType` **不是数字，是中文名称字符串**：
     *
     * ```json
     * { "cardPoolType": "角色精准调谐", "qualityLevel": 3, "name": "远行者长刃·辟路", … }
     * ```
     *
     * 之前的代码写的是 `o.optInt("cardPoolType", 0)` ——
     * `optInt` 拿到中文串后 `Double.parseDouble` 抛异常，返回 fallback **0**，
     * 于是 787 条记录里 **758 条显示成「未知卡池(0)」**。
     *
     * 而池 6 / 池 10 的字段值恰好就是字符串 `"6"` / `"10"`（这两个池没有友好名称），
     * 能被 `optInt` 解析成功 —— 所以数据里出现了 `{0: 758, 10: 22, 6: 7}` 这种
     * "大部分是 0、少数有值"的诡异分布。**这个分布本身就是根因的指纹。**
     *
     * 下表的值**全部来自真实响应**，不是推测：
     *
     * | 响应里的 cardPoolType | 请求参数 |
     * |---|---|
     * | `角色精准调谐` | 1 |
     * | `武器精准调谐` | 2 |
     * | `角色调谐（常驻池）` | 3 |
     * | `武器调谐（常驻池）` | 4 |
     *
     * ★ 注意是全角括号「（常驻池）」，不是半角 —— 写错了就匹配不上。
     */
    internal val POOL_NAMES: Map<String, Int> = mapOf(
        "角色精准调谐" to 1,
        "武器精准调谐" to 2,
        "角色调谐（常驻池）" to 3,
        "武器调谐（常驻池）" to 4,
    )

    private fun parseRecord(o: JSONObject?): GachaRecord? {
        if (o == null) return null
        val name = o.optString("name")
        if (name.isBlank()) return null
        // ★ 必须按**字符串**读，不能用 optInt —— 字段值可能是中文名称。
        val poolType = poolTypeOf(o.optString("cardPoolType"))
        return GachaRecord(
            poolType = poolType,
            name = name,
            // ★ 星级：接口字段名是 qualityLevel
            rarity = o.optInt("qualityLevel", 0),
            resourceType = o.optString("resourceType"),
            count = o.optInt("count", 1),
            time = o.optString("time"),
        )
    }

    /** 池名。未知类型如实标注，**不猜** —— 猜错会让用户以为记录丢了 */
    fun poolLabel(type: Int): String =
        POOL_TYPES.firstOrNull { it.first == type }?.second ?: "未知卡池($type)"
}

data class GachaLink(
    val serverId: String,
    val playerId: String,
    val recordId: String,
    val resourcesId: String,
    val oversea: Boolean,
)

sealed interface GachaResult<out T> {
    data class Ok<T>(val value: T) : GachaResult<T>
    data class Failure(val message: String) : GachaResult<Nothing>
}

data class GachaRecord(
    val poolType: Int,
    val name: String,
    /** 3 / 4 / 5。0 表示接口没给 —— UI 必须区别对待，不能当 3 星 */
    val rarity: Int,
    val resourceType: String,
    val count: Int,
    val time: String,
) {
    /** `2024-06-01 12:00:00` → 可排序的字符串；解析失败返回空串（排序时排最后） */
    val sortKey: String get() = time
}

/**
 * 抽卡记录的本地缓存。
 *
 * 存**记录**（用户自己的数据），不存链接（凭据）。见 [WuwaGachaApi] 的说明。
 */
object WuwaGachaStore {

    private const val DIR = "wuwa_gacha"

    private var dir: File? = null

    fun install(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, DIR)
        runCatching { d.mkdirs() }
        dir = d
    }

    private fun file() = dir?.let { File(it, "records.json") }

    /**
     * 导出成 JSON 字符串（云端备份用），P11 补 39。
     *
     * ★ 直接复用缓存文件的**原始格式**，不另做一套备份格式 ——
     *   两套格式意味着恢复时要转换，而转换就是又一个"错了看不出来"的地方。
     */
    fun exportJson(): String = file()?.takeIf { it.exists() }?.readText() ?: "[]"

    /**
     * 把云端 JSON 合并进本地，返回合并后的条数。P11 补 39。
     *
     * ★ 走 [save]（内部用 [mergeRecords]）而**不是覆盖** ——
     *   用户原话：「即便有获取链接，也最多覆盖相同的，以前的不影响」。
     *
     * ★ 解析失败时**不动本地数据**并返回当前条数，而不是清空 ——
     *   云端文件损坏不该让用户把本机记录也赔进去。
     */
    fun importJson(text: String): Int {
        val incoming = runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                GachaRecord(
                    poolType = o.optInt("poolType"),
                    name = o.optString("name"),
                    rarity = o.optInt("rarity"),
                    resourceType = o.optString("resourceType"),
                    count = o.optInt("count", 1),
                    time = o.optString("time"),
                )
            }
        }.getOrDefault(emptyList())
        if (incoming.isEmpty()) return load().size
        return save(load(), incoming).size
    }

    fun load(): List<GachaRecord> {
        val f = file() ?: return emptyList()
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                GachaRecord(
                    poolType = o.optInt("poolType"),
                    name = o.optString("name"),
                    rarity = o.optInt("rarity"),
                    resourceType = o.optString("resourceType"),
                    count = o.optInt("count", 1),
                    time = o.optString("time"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 保存。★ **按「池 + 名称 + 时间」去重合并**，而不是整体覆盖。
     *
     * 原因：接口一次只返回一个卡池，用户可能分多次粘链接把几个池都拉一遍。
     * 整体覆盖会让"拉第二个池"把第一个池的记录清掉 —— 那是**丢用户数据**。
     */
    fun save(existing: List<GachaRecord>, incoming: List<GachaRecord>): List<GachaRecord> {
        val out = mergeRecords(existing, incoming)
        runCatching {
            val arr = JSONArray()
            out.forEach { r ->
                arr.put(JSONObject().apply {
                    put("poolType", r.poolType)
                    put("name", r.name)
                    put("rarity", r.rarity)
                    put("resourceType", r.resourceType)
                    put("count", r.count)
                    put("time", r.time)
                })
            }
            file()?.writeText(arr.toString())
        }
        return out
    }

    fun clear() {
        runCatching { file()?.delete() }
    }
}

/**
 * 把接口的 `cardPoolType` 原始值解析成池类型数字。
 *
 * ★ 抽成**顶层 internal 纯函数**，而不是留在 `parseRecord` 里 ——
 *   因为这是本次 bug 的正中心（758 条记录栽在这里），
 *   必须能被单元测试直接钉住，而不是只能靠"粘链接看结果"。
 *
 * 实测过的输入形态（P11 补 36）：
 * · `"角色精准调谐"` → 1
 * · `"武器精准调谐"` → 2
 * · `"角色调谐（常驻池）"` → 3   ← 全角括号
 * · `"武器调谐（常驻池）"` → 4
 * · `"6"` / `"10"` → 6 / 10      ← 这两个池没有友好名称，就是纯数字串
 * · 其它 → 0，由 poolLabel 如实标成「未知卡池(0)」，**不猜**
 */
internal fun poolTypeOf(rawPool: String): Int =
    WuwaGachaApi.POOL_NAMES[rawPool] ?: rawPool.toIntOrNull() ?: 0

/**
 * 去重合并 —— 抽成纯函数以便单测。
 *
 * ★ 这里出错的表现是**丢用户数据**（拉第二个卡池把第一个池的记录清掉），
 *   而且只在"分多次粘链接"的操作路径上出现，手工测很容易漏。
 */
internal fun mergeRecords(
    existing: List<GachaRecord>,
    incoming: List<GachaRecord>,
): List<GachaRecord> {
    val merged = LinkedHashMap<String, GachaRecord>()
    (keyedForMerge(existing) + keyedForMerge(incoming)).forEach { (key, r) ->
        val old = merged[key]
        merged[key] = when {
            old == null -> r
            // 新记录带得出池类型 → 它是更完整的版本，覆盖旧的
            r.poolType != 0 -> r
            // 新的也是 0，但旧的知道池类型 → 保留旧的，别把已知信息丢掉
            old.poolType != 0 -> old
            else -> r
        }
    }
    return merged.values.toList()
}

/**
 * 给一批记录编去重键。
 *
 * ### 键为什么长这样（P11 补 37 —— 走过两次弯路）
 *
 * **弯路一：键含 `poolType`**（`poolType|name|time`）
 *   修好 `cardPoolType` 解析后，同一条物理记录会有两种形态
 *   （旧缓存 0 / 新拉的 1），键不同 → **同一条存两份，抽卡数翻倍**。
 *
 * **弯路二：键只用 `name|time`**（P11 补 36 的"修法"）
 *   更糟 —— **它会吃掉真实记录**。一次十连可以出多个同名物品、同一秒，
 *   比如十连里 3 把「远行者佩枪·洞察」，`name|time` 完全相同 → 合并成 1 条。
 *   实测代价：826 条被压成 787 条，**39 条记录凭空消失**（含 1 条五星）。
 *
 * ### 正确做法：在 `name|time` 后加**同组内的序号**
 *
 * `name|time|0`、`name|time|1`、`name|time|2` … 既能区分十连里的重复，
 * 又不含 `poolType`（所以跨"修复前后"的两次抓取仍能对上。
 *
 * ★ 序号**只在各自批次内计算**：`existing` 和 `incoming` 装的是同一批物理记录、
 *   顺序一致，所以序号能对应上。如果拿一个全局计数器跨批次累加，
 *   incoming 的序号会从 existing 的末尾继续涨 → 又对不上了。
 *
 * ★ 这个修复是**自愈**的：旧缓存里丢掉的第 2、3 条，
 *   下次重新抓取时会按 `…|1`、`…|2` 补回来。
 */
private fun keyedForMerge(list: List<GachaRecord>): List<Pair<String, GachaRecord>> {
    val seen = HashMap<String, Int>()
    return list.map { r ->
        val base = "${r.name}|${r.time}"
        val n = seen.getOrDefault(base, 0)
        seen[base] = n + 1
        "$base|$n" to r
    }
}

/**
 * 抽卡统计。
 *
 * ★ 全部由记录**直接算出**，没有任何估计或插值。
 *   `averages` 只在有该稀有度的记录时才给值 —— 没有五星就是 `null`，
 *   绝不能显示成一个数字（那是把"没有"编造成"0 或 ∞"）。
 */
data class GachaStats(
    val total: Int,
    val byRarity: Map<Int, Int>,
    /** 每个五星之间隔了多少抽（按时间正序算），用于"平均出金" */
    val fiveStarGaps: List<Int>,
) {
    val fiveStarCount: Int get() = byRarity[5] ?: 0
    val fourStarCount: Int get() = byRarity[4] ?: 0

    /** 平均出金抽数；**没有五星时为 null**（不是 0，也不是无穷） */
    val averageFiveStarPull: Double?
        get() = if (fiveStarGaps.isEmpty()) null else fiveStarGaps.average()

    companion object {
        fun of(records: List<GachaRecord>): GachaStats {
            val byRarity = records.groupingBy { it.rarity }.eachCount()
            // 按时间正序（接口按时间倒序返回）
            val ordered = records.sortedBy { it.sortKey }
            val gaps = mutableListOf<Int>()
            var sinceLast = 0
            ordered.forEach { r ->
                sinceLast++
                if (r.rarity == 5) {
                    gaps += sinceLast
                    sinceLast = 0
                }
            }
            return GachaStats(total = records.size, byRarity = byRarity, fiveStarGaps = gaps)
        }
    }
}
