package com.utilitybox.game.wutheringwaves.kuro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「常驻池五星角色名单」——用户填写的**游戏事实**（P11 补 100）。
 *
 * ### 为什么需要用户填
 *
 * 判定「这次出的五星是 UP 还是歪」必须先知道**哪些五星是常驻**。
 * 这个数据我查过三个来源，都拿不到（见 `GachaInsights` 与 P11 补 38 的注释）：
 *
 * · 库街区 wiki 的共鸣者标签只有「稀有度 / 属性 / 武器 / 实装版本 / 风格定位」，**没有「常驻」**
 * · wiki 的分类树（实测 45KB 全量）**完全没有卡池分类**
 * · 游戏唤取接口只给 `name / cardPoolType / qualityLevel / count / time`，**没有 UP 标记**
 *
 * 剩下的唯一来源是"用户自己的常驻池记录"，但常驻池出货极少
 * （本机 848 抽里常驻池只出过 1 个），推导不出完整名单。
 *
 * ★ 所以这里让用户自己填 —— 这是**他知道的游戏事实**，不是编造。
 *   应用只负责做数学，不替用户猜名单。
 *
 * ### 与"常驻池推导"的关系
 *
 * 两者取**并集**：
 * · 常驻池里抽到过的五星 → 一定是常驻（机器事实，自动）
 * · 用户填的名单 → 补充那些还没抽到过的常驻角色
 *
 * 这样即使不填也能用（覆盖面小），填了就完整。
 */
object GachaStandardRoster {

    private const val PREFS = "gacha_standard_roster"
    private const val KEY = "names"

    /** 当前名单。Compose 可观察 —— 填完立刻生效，不用重启或重新进页面 */
    var names: Set<String> by mutableStateOf(emptySet())
        private set

    private var prefs: android.content.SharedPreferences? = null

    fun install(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        names = parse(p.getString(KEY, null).orEmpty())
    }

    /**
     * 保存用户输入。
     *
     * 分隔符接受**换行 / 逗号 / 中文逗号 / 顿号 / 空格** ——
     * 用户多半是从公告里整段粘过来的，不该强迫他改成某一种格式。
     */
    fun save(input: String) {
        val parsed = parse(input)
        names = parsed
        runCatching {
            prefs?.edit()?.putString(KEY, parsed.joinToString("\n"))?.apply()
        }
    }

    fun clear() {
        names = emptySet()
        runCatching { prefs?.edit()?.remove(KEY)?.apply() }
    }

    /** 把任意分隔的文本切成名字集合（去空白、去重、保持原顺序） */
    internal fun parse(raw: String): Set<String> =
        raw.split('\n', ',', '，', '、', ' ', '\t', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toCollection(LinkedHashSet())
}
