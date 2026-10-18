package com.utilitybox.game.wutheringwaves.kuro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * 角色名 → 头像图片 URL 的本地索引（P11 补 96）。
 *
 * ### 要解决的问题
 *
 * 抽卡统计里每个五星只有**名字首字**做的圆形占位（用户反馈：
 * 「每个角色能不能获取其角色头像…而不是使用文字当头像」）。
 *
 * ### 头像从哪来 —— 不编造
 *
 * 库街区 wiki 的「共鸣者」分类（id `1105`）列表接口
 * (`KuroWikiApi.entries`) 每条记录**自带 `imageUrl`**（官方 `contentUrl`）。
 * 一次请求就能拿到全部角色，于是建一张 `名字 → 头像 URL` 的表。
 *
 * ★ **绝不自造图、绝不用别的图冒充**：表里没有的角色就保持首字占位。
 *   宁可显示占位，也不显示错的头像 —— 那是伪造资源（红线）。
 *
 * ### 为什么要落盘
 *
 * 表有 60+ 条，每次启动都拉一次是浪费（数据源是社区接口，有额度概念）。
 * 所以存本地、7 天过期。过期后**先用旧表**，后台再更新
 * （见 [ensureLoaded] 的注释）—— 用户不该为了刷新头像表等网络。
 */
object WuwaAvatarIndex {

    /** 共鸣者分类（与 `WuwaWiki.CAT_RESONATOR` 同一个值） */
    private const val CATEGORY_RESONATOR = 1105

    /** 表多久算过期。角色头像不会天天变，7 天足够 */
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000L

    private const val FILE_NAME = "avatars.tsv"

    /**
     * 名字 → 头像 URL。
     *
     * ★ 用 Compose 的 `mutableStateOf`：加载完成后**读取它的 UI 会自动重组**，
     *   不需要在页面里手动维护"有没有加载好"的状态。
     */
    var byName: Map<String, String> by mutableStateOf(emptyMap())
        private set

    private var dir: File? = null
    private var inFlight = false
    private var loadedAt = 0L

    /** 在 `UbApplication.onCreate` 调用一次，指定缓存目录 */
    fun install(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, "wuwa_avatar")
        runCatching { d.mkdirs() }
        dir = d
    }

    fun urlOf(name: String): String? = byName[name]

    /**
     * 确保索引可用。
     *
     * 顺序：**内存 → 磁盘 → 网络**，能少发请求就少发。
     * ★ 磁盘命中且未过期时**直接返回**，不发网络；
     *   逾期时仍先用旧表（头像不急着换），然后才去更新。
     */
    suspend fun ensureLoaded(force: Boolean = false) {
        if (inFlight) return

        // 1) 内存已有且未过期
        if (!force && byName.isNotEmpty() && !expired()) return

        // 2) 内存没有 → 先读磁盘（有就立刻可用，不必等网络）
        if (!force && byName.isEmpty()) {
            readDisk()?.let { (saved, ts) ->
                byName = saved
                loadedAt = ts
                if (!expired()) return
            }
        }

        inFlight = true
        try {
            val r = KuroWikiApi.entries(CATEGORY_RESONATOR)
            if (r is WikiResult.Ok) {
                val fresh = r.value
                    .mapNotNull { e -> e.imageUrl?.let { url -> e.name to url } }
                    .toMap()
                // ★ 空结果不覆盖已有表：一次接口异常不该把头像全清空
                if (fresh.isNotEmpty()) {
                    byName = fresh
                    loadedAt = System.currentTimeMillis()
                    writeDisk(fresh)
                }
            }
            // Failure：保持现状（可能是磁盘里的旧表），不编造
        } finally {
            inFlight = false
        }
    }

    private fun expired(): Boolean =
        loadedAt <= 0L || System.currentTimeMillis() - loadedAt > TTL_MS

    private fun file(): File? = dir?.let { File(it, FILE_NAME) }

    /** 文件格式：第一行时间戳，其后每行 `名字<TAB>URL` */
    private fun writeDisk(map: Map<String, String>) {
        val f = file() ?: return
        runCatching {
            val sb = StringBuilder()
            sb.append(System.currentTimeMillis()).append('\n')
            map.forEach { (k, v) -> sb.append(k).append('\t').append(v).append('\n') }
            f.writeText(sb.toString())
        }
    }

    private fun readDisk(): Pair<Map<String, String>, Long>? {
        val f = file() ?: return null
        if (!f.exists()) return null
        return runCatching {
            val lines = f.readLines()
            if (lines.size < 2) return null
            val ts = lines.first().trim().toLongOrNull() ?: return null
            val map = lines.drop(1).mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) null
                else parts[0] to parts[1]
            }.toMap()
            if (map.isEmpty()) null else map to ts
        }.getOrNull()
    }
}
