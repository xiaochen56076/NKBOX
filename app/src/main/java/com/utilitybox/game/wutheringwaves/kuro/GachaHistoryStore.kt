package com.utilitybox.game.wutheringwaves.kuro

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一期「唤取记录快照」的元信息（P11 补 97）。
 *
 * ★ 只放**元信息**，不放记录本身：列表页要显示 N 条摘要，
 *   若每条都把 800+ 记录读进内存，列表会明显卡。
 *
 * ★★ [id] 是**存储字段**，由 [GachaHistoryStore] 生成，且它就是文件名主干。
 *   早前版本把 id 做成计算属性（不含格式版本），而文件名里带 `_v<版本>`，
 *   两处各自拼名字 → `load(id)` 找不到文件、读回 0 条
 *   （被 `GachaHistoryStoreTest` 抓出来）。
 *   **同一个标识只能在一处生成**，否则迟早对不上。
 */
data class GachaSnapshotMeta(
    /** 保存时刻（毫秒）。同一个账号多次导入会按这个排序 */
    val savedAtMillis: Long,
    /** 库街区 `player_id` —— 用户自己的 uid，不是凭据 */
    val accountId: String,
    /** 这份快照有多少条记录 */
    val recordCount: Int,
    /** 文件名主干（含格式版本）。也是列表的 key */
    val id: String,
) {
    /** 列表里的日期：`2026年10月18日` */
    val dateLabel: String
        get() = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(savedAtMillis))

    /** 列表里的时刻：`22:41` */
    val timeLabel: String
        get() = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(savedAtMillis))
}

/**
 * 唤取记录的**历史快照**存储（P11 补 97）。
 *
 * ### 为什么要它
 *
 * 原来只有 `WuwaGachaStore` 里的**一份**记录：新导入就覆盖旧的，用户看不到
 * 「上次查过什么」。用户要求：「记录可以改为这样的历史记录…
 * 可以从这个历史记录点击进去查看，这个记录算是属于往期的」。
 *
 * ### 与 [WuwaGachaStore] 的分工
 *
 * · `WuwaGachaStore`：**当前**合并后的全量记录（导入会增量合并，界面主用这一份）
 * · `GachaHistoryStore`：**每次导入那一刻的存档**，只读、可删除、按时间排列
 *
 * ★ 两者**不互相覆盖**：删历史快照不会影响当前记录，清空当前记录也不删历史。
 *   这是用户数据，两个动作语义不同。
 *
 * ### 不存什么
 *
 * **不存链接**。链接里的 `record_id` 是短时凭据（见 `WuwaGachaApi` 的说明），
 * 存下来等于把凭据落盘。快照只存解析后的**记录本身**。
 */
object GachaHistoryStore {

    private const val DIR = "wuwa_gacha_history"

    /** 快照格式版本。字段变化时 +1，旧快照直接当不存在（不做兼容猜测） */
    private const val SCHEMA = 1

    /** 最多保留多少份。超过后删最旧的 —— 无上限会让目录无限增长 */
    private const val MAX_SNAPSHOTS = 30

    private var dir: File? = null

    fun install(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, DIR)
        runCatching { d.mkdirs() }
        dir = d
    }

    /**
     * 测试用：直接指定目录（单元测试没有 Context）。
     *
     * ★ 为什么要开这个口子：文件名编解码（`<时间>_<条数>_<账号>_v<版本>`）
     *   和「同账号同条数视为同一次查询」的去重逻辑**都是纯逻辑**，
     *   但只靠真机试没法覆盖边界（重名、损坏文件、超上限）。
     */
    internal fun installForTest(testDir: File) {
        runCatching { testDir.mkdirs() }
        dir = testDir
    }

    /**
     * 列出所有快照，**按时间倒序**（最新的在最前）。
     *
     * ★ 只用文件名解析，不读文件内容 —— 列表要快。
     *   文件名格式：`<savedAt>_<recordCount>_<accountId>_v<SCHEMA>.json`
     */
    fun list(): List<GachaSnapshotMeta> {
        val d = dir ?: return emptyList()
        val files = runCatching { d.listFiles() }.getOrNull() ?: return emptyList()
        return files.asSequence()
            .filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { parseName(it.name) }
            .sortedByDescending { it.savedAtMillis }
            .toList()
    }

    /**
     * 保存一份快照。
     *
     * ★ **同账号同条数视为同一次查询**：重查一遍不该在列表里堆出两条一模一样的。
     *   此时覆盖旧文件（更新它的时间），返回新的元信息。
     */
    fun save(records: List<GachaRecord>, accountId: String): GachaSnapshotMeta? {
        if (records.isEmpty()) return null
        val d = dir ?: return null
        val safeAccount = accountId.filter { it.isDigit() }.ifBlank { "unknown" }

        // 去重：同账号 + 同记录数 → 删掉旧的
        list().filter { it.accountId == safeAccount && it.recordCount == records.size }
            .forEach { delete(it) }

        val ts = System.currentTimeMillis()
        val meta = GachaSnapshotMeta(
            savedAtMillis = ts,
            accountId = safeAccount,
            recordCount = records.size,
            id = idOf(ts, records.size, safeAccount),
        )
        val ok = runCatching {
            File(d, fileNameOf(meta)).writeText(toJson(records))
            true
        }.getOrDefault(false)
        if (!ok) return null

        // 超出上限 → 删最旧的
        list().drop(MAX_SNAPSHOTS).forEach { delete(it) }
        return meta
    }

    /** 读取某份快照的记录。不存在或损坏返回空列表（不抛） */
    fun load(id: String): List<GachaRecord> {
        val d = dir ?: return emptyList()
        val f = File(d, "$id.json")
        if (!f.exists()) return emptyList()
        return runCatching { fromJson(f.readText()) }.getOrDefault(emptyList())
    }

    fun delete(meta: GachaSnapshotMeta) {
        val d = dir ?: return
        runCatching { File(d, fileNameOf(meta)).delete() }
    }

    fun delete(id: String) {
        val d = dir ?: return
        runCatching { File(d, "$id.json").delete() }
    }

    // ------------------------------------------------------------------
    // 文件名编解码
    //
    // ★ 标识**只在这一处生成**：id 就是文件名主干，文件名 = id + ".json"。
    //   早前 id 由数据类计算、文件名另拼一份，两处漏了版本号导致读回 0 条。
    // ------------------------------------------------------------------

    private fun idOf(savedAtMillis: Long, recordCount: Int, accountId: String): String =
        "${savedAtMillis}_${recordCount}_${accountId}_v$SCHEMA"

    private fun fileNameOf(m: GachaSnapshotMeta): String = "${m.id}.json"

    private fun parseName(name: String): GachaSnapshotMeta? {
        val base = name.removeSuffix(".json")
        val parts = base.split('_')
        if (parts.size != 4) return null
        val version = parts[3].removePrefix("v").toIntOrNull() ?: return null
        // 旧版本格式 → 当作不存在，而不是按新格式硬读
        if (version != SCHEMA) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val count = parts[1].toIntOrNull() ?: return null
        return GachaSnapshotMeta(
            savedAtMillis = ts,
            accountId = parts[2],
            recordCount = count,
            id = base,
        )
    }

    // ------------------------------------------------------------------
    // JSON —— 与 WuwaGachaStore 同一份字段，避免两套格式
    // ------------------------------------------------------------------

    private fun toJson(records: List<GachaRecord>): String {
        val arr = org.json.JSONArray()
        records.forEach { r ->
            arr.put(org.json.JSONObject().apply {
                put("poolType", r.poolType)
                put("name", r.name)
                put("rarity", r.rarity)
                put("resourceType", r.resourceType)
                put("count", r.count)
                put("time", r.time)
            })
        }
        return arr.toString()
    }

    private fun fromJson(text: String): List<GachaRecord> {
        val arr = org.json.JSONArray(text)
        return (0 until arr.length()).mapNotNull { i ->
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
    }
}
