package com.utilitybox.game.wutheringwaves.kuro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * 角色 / 武器名 → 图标 URL 的本地索引（P11 补 96 / 102）。
 *
 * ### 要解决的问题
 *
 * 抽卡统计里每个五星原先只有**名字首字**做的圆形占位。P11 补 96 先建了
 * 「共鸣者」索引，解决了角色头像；但用户随后指出「专武目前是没有图片的」——
 * 因为武器池的五星（`resourceType == "武器"`）不在共鸣者分类里，
 * 全部落在了首字兜底。
 *
 * ### 图标从哪来 —— 不编造
 *
 * 库街区 wiki 两个分类列表，每条记录**自带 `imageUrl`**（官方 `contentUrl`）：
 * - 共鸣者（id `1105`）→ 角色头像
 * - 武器（id `1106`）→ 武器图标
 *
 * 两张表**分开存**：目前角色名与武器名没有重名，但一旦重名，
 * 按 `resourceType` 查对应的表才能保证不拿错图。
 *
 * ★ **绝不自造图、绝不用别的图冒充**：表里没有的条目保持首字占位。
 *   宁可显示占位，也不显示错的图标 —— 那是伪造资源（红线）。
 *
 * ### 为什么要落盘
 *
 * 两张表共 180+ 条，每次启动都拉一次是浪费（数据源是社区接口，有额度概念）。
 * 所以存本地、7 天过期。过期后**先用旧表**，后台再更新
 * （见 [ensureLoaded] 的注释）—— 用户不该为了刷新图标表等网络。
 */
object WuwaIconIndex {

    /** 共鸣者分类（与 `WuwaWiki.CAT_RESONATOR` 同一个值） */
    private const val CATEGORY_RESONATOR = 1105

    /** 武器分类（与 `WuwaWiki.CAT_WEAPON` 同一个值） */
    private const val CATEGORY_WEAPON = 1106

    /** 表多久算过期。角色头像 / 武器图标不会天天变，7 天足够 */
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000L

    private const val FILE_NAME = "icons.tsv"

    /**
     * 角色名 → 头像 URL。
     *
     * ★ 用 Compose 的 `mutableStateOf`：加载完成后**读取它的 UI 会自动重组**，
     *   不需要在页面里手动维护"有没有加载好"的状态。
     */
    var charactersByName: Map<String, String> by mutableStateOf(emptyMap())
        private set

    /** 武器名 → 图标 URL。状态语义同上。 */
    var weaponsByName: Map<String, String> by mutableStateOf(emptyMap())
        private set

    private var dir: File? = null
    private var inFlight = false
    private var loadedAt = 0L

    /** 在 `UbApplication.onCreate` 调用一次，指定缓存目录 */
    fun install(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, "wuwa_icon")
        runCatching { d.mkdirs() }
        dir = d
    }

    /**
     * 按资源类型取图标。
     *
     * 先查对应的表，再回退到另一张 —— 抽卡接口偶尔缺 `resourceType`，
     * 两张表没有重名时这个回退不会拿错图。
     */
    fun urlOf(name: String, isWeapon: Boolean): String? =
        if (isWeapon) weaponsByName[name] ?: charactersByName[name]
        else charactersByName[name] ?: weaponsByName[name]

    /**
     * 确保两张索引可用。
     *
     * 顺序：**内存 → 磁盘 → 网络**，能少发请求就少发。
     * ★ 磁盘命中且未过期时**直接返回**，不发网络；
     *   逾期时仍先用旧表（图标不急着换），然后才去更新。
     * ★ 两张表分别补拉：只有一边缺失 / 过期时，不重复请求另一边。
     */
    suspend fun ensureLoaded(force: Boolean = false) {
        if (inFlight) return

        // 1) 内存已有且未过期
        if (!force && charactersByName.isNotEmpty() && weaponsByName.isNotEmpty() && !expired()) return

        // 2) 内存没有 → 先读磁盘（有就立刻可用，不必等网络）
        if (!force && (charactersByName.isEmpty() || weaponsByName.isEmpty())) {
            readDisk()?.let { saved ->
                if (charactersByName.isEmpty() && saved.characters.isNotEmpty()) {
                    charactersByName = saved.characters
                }
                if (weaponsByName.isEmpty() && saved.weapons.isNotEmpty()) {
                    weaponsByName = saved.weapons
                }
                loadedAt = saved.savedAt
            }
        }

        val fetchCharacters = force || expired() || charactersByName.isEmpty()
        val fetchWeapons = force || expired() || weaponsByName.isEmpty()
        if (!fetchCharacters && !fetchWeapons) return

        inFlight = true
        try {
            var charactersFresh = !fetchCharacters
            var weaponsFresh = !fetchWeapons

            if (fetchCharacters) {
                val r = KuroWikiApi.entries(CATEGORY_RESONATOR)
                if (r is WikiResult.Ok) {
                    val fresh = r.value
                        .mapNotNull { e -> e.imageUrl?.let { url -> e.name to url } }
                        .toMap()
                    // ★ 空结果不覆盖已有表：一次接口异常不该把图标全清空
                    if (fresh.isNotEmpty()) {
                        charactersByName = fresh
                        charactersFresh = true
                    }
                }
            }

            if (fetchWeapons) {
                val r = KuroWikiApi.entries(CATEGORY_WEAPON)
                if (r is WikiResult.Ok) {
                    val fresh = r.value
                        .mapNotNull { e -> e.imageUrl?.let { url -> e.name to url } }
                        .toMap()
                    if (fresh.isNotEmpty()) {
                        weaponsByName = fresh
                        weaponsFresh = true
                    }
                }
            }

            // 两张表都可用才刷新时间戳并落盘：只有一边成功时保持旧时间戳，
            // 下次进页面会继续补拉失败的那一边。
            if (charactersFresh && weaponsFresh &&
                charactersByName.isNotEmpty() && weaponsByName.isNotEmpty()
            ) {
                loadedAt = System.currentTimeMillis()
                writeDisk(charactersByName, weaponsByName)
            }
        } finally {
            inFlight = false
        }
    }

    private fun expired(): Boolean =
        loadedAt <= 0L || System.currentTimeMillis() - loadedAt > TTL_MS

    private fun file(): File? = dir?.let { File(it, FILE_NAME) }

    private class DiskCache(
        val characters: Map<String, String>,
        val weapons: Map<String, String>,
        val savedAt: Long,
    )

    /**
     * 文件格式：第一行时间戳，其后每行 `类型<TAB>名字<TAB>URL`。
     * 类型：`c` = 角色，`w` = 武器。
     */
    private fun writeDisk(characters: Map<String, String>, weapons: Map<String, String>) {
        val f = file() ?: return
        runCatching {
            val sb = StringBuilder()
            sb.append(System.currentTimeMillis()).append('\n')
            characters.forEach { (k, v) ->
                sb.append("c\t").append(k).append('\t').append(v).append('\n')
            }
            weapons.forEach { (k, v) ->
                sb.append("w\t").append(k).append('\t').append(v).append('\n')
            }
            f.writeText(sb.toString())
        }
    }

    private fun readDisk(): DiskCache? {
        val f = file() ?: return null
        if (!f.exists()) return null
        return runCatching {
            val lines = f.readLines()
            if (lines.size < 2) return null
            val ts = lines.first().trim().toLongOrNull() ?: return null
            val characters = mutableMapOf<String, String>()
            val weapons = mutableMapOf<String, String>()
            lines.drop(1).forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@forEach
                val name = parts[1]
                val url = parts[2]
                if (name.isBlank() || url.isBlank()) return@forEach
                when (parts[0]) {
                    "c" -> characters[name] = url
                    "w" -> weapons[name] = url
                }
            }
            if (characters.isEmpty() && weapons.isEmpty()) null
            else DiskCache(characters, weapons, ts)
        }.getOrNull()
    }
}
