package com.utilitybox.game.deltaforce.itemprice

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 搜索历史（P11 补 53）。
 *
 * ### 用户要求
 *
 * > 「应该加上**历史记录**，**多了会自动折叠**那种…
 * >   历史记录是**可以全部清除**的」
 * > 「**只清空历史**」（明确：不动当前搜索结果）
 *
 * ### 为什么不用 `SecretStore`
 *
 * 搜索历史**不是密钥** —— 走 Keystore 加密既没必要，也会让"清除"变成一个
 * 涉及密钥的操作。用普通 `SharedPreferences` 就够，与项目里 `Prefs*` 的约定一致。
 *
 * ### ★ 去重与截断做成纯函数
 *
 * [mergeHistory] 是纯函数，**有单测钉住语义**（见 `ItemSearchHistoryTest`）。
 * 这样"最近搜的排最前""重复的提到最前而不是多占一条""超出上限丢最旧的"
 * 这三条规则不靠"跑一下看看"来验证。
 */
object ItemSearchHistory {

    private const val PREFS = "ubox_item_search_history"
    private const val KEY = "keywords"

    /**
     * 最多保留多少条。
     *
     * ★ 20 的依据：界面上折叠后只显示一行左右，展开也就三四行 ——
     *   再多就变成"又一个需要滚动的列表"，而搜索历史的价值是**一眼看到最近的**。
     */
    const val MAX = 20

    /**
     * 存储分隔符。
     *
     * ★ 用 `U+0001`（Start of Heading）而不是逗号/竖线：
     *   物品名里可能出现任何可见标点，但**不可能出现控制字符** ——
     *   选一个"绝对不可能与内容冲突"的分隔符，就不需要转义逻辑。
     *
     * ★ 放在**顶层**而不是 object 内部：下面的 `encode` / `decode` 是顶层函数，
     *   object 里的 `private const` 它们看不见（编译报 Unresolved reference 'SEP'）。
     */
    private var prefs: SharedPreferences? = null

    /** 供 Compose 订阅 —— 历史变化时界面自动重组 */
    var keywords: List<String> by mutableStateOf(emptyList())
        private set

    /**
     * 在 `Application` 启动时调用一次（与 `WuwaGachaStore.install` 同一模式）。
     *
     * ★ 不在这里读盘：读盘放 [refresh]，这样 install 是幂等的、不触发 IO。
     */
    fun install(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        refresh()
    }

    fun refresh() {
        keywords = decode(prefs?.getString(KEY, null))
    }

    /**
     * 记录一次搜索。
     *
     * ★ 什么时候记：**搜索成功之后**，不是"打字停止之后"。
     *   否则用户打了一半的字、或者打错导致零结果，都会被存进历史 —— 那是污染。
     *   调用点在 `ItemListHolder` 拿到成功结果之后。
     */
    fun record(keyword: String) {
        val next = mergeHistory(keywords, keyword, MAX)
        if (next == keywords) return
        keywords = next
        prefs?.edit()?.putString(KEY, encode(next))?.apply()
    }

    /** 只清空历史，**不影响当前搜索结果**（用户明确要求） */
    fun clear() {
        keywords = emptyList()
        prefs?.edit()?.remove(KEY)?.apply()
    }
}

/**
 * 存储分隔符（顶层，供 [encode] / [decode] 共用）。
 *
 * ★ 用 `U+0001`（Start of Heading）而不是逗号/竖线：
 *   物品名里可能出现任何可见标点，但**不可能出现控制字符** ——
 *   选一个"绝对不可能与内容冲突"的分隔符，就不需要转义逻辑。
 */
private const val SEP = "\u0001"

/**
 * 把一次新搜索并入历史。**纯函数**，有单测。
 *
 * 规则：
 * 1. 空白关键字**不记录**（用户清空输入框不是一次搜索）
 * 2. 已存在的**提到最前**，而不是多占一条 —— 否则反复搜同一个词会把历史撑满
 * 3. 最近搜的排最前
 * 4. 超过 [max] 条时**丢最旧的**
 * 5. 大小写/首尾空白归一后再比较，但**存归一后的形式**（免得历史里 `钛` 和 `钛 ` 两条）
 */
internal fun mergeHistory(existing: List<String>, keyword: String, max: Int): List<String> {
    val k = keyword.trim()
    if (k.isEmpty()) return existing
    if (max <= 0) return emptyList()
    // 先移除同名的（忽略大小写），再插到最前
    val rest = existing.filter { !it.equals(k, ignoreCase = true) }
    return (listOf(k) + rest).take(max)
}

/** 存盘编码。`List<String>` ↔ 单个字符串，用 [ItemSearchHistory.SEP] 分隔 */
internal fun encode(list: List<String>): String = list.joinToString(SEP)

/**
 * 读盘解码。
 *
 * ★ 脏数据一律**当作没有**而不是抛异常：
 *   这是本地缓存，坏了最坏结果是"历史空了"，不该让整个搜索页崩掉。
 *   同时丢掉空白项 —— 历史里出现一条点不动的空标签是很糟的体验。
 */
internal fun decode(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }
}
