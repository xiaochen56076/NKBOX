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
 * 库街区公开 WIKI 接口 —— 鸣潮图鉴。
 *
 * ### ★★ 这是**匿名**接口，不需要登录
 *
 * 与 [KuroBbsApi]（账号接口）是**两套完全独立的鉴权**：本文件不带 `token`、
 * 不带 Cookie。请求头只需要：
 *
 * ```
 * source:    h5
 * wiki_type: 9          (鸣潮)
 * devcode:   <任意 32 位十六进制>   ← 实测不校验内容
 * ```
 *
 * ★ 这个事实是**实测确认**的，不是推断：不带 token 请求 `/wiki/…` 返回 `code=200`，
 *   而不带 token 请求 `/aki/roleBox/…` 会失败。
 *
 * ⚠️ 上面两处刻意写成 `/wiki/…` 而不是 `/wiki/` + 星号：**Kotlin 的块注释会嵌套**，
 *   KDoc 里出现 `斜杠 + 星号` 会打开一个永不闭合的嵌套注释，
 *   报错是 `Syntax error: Unclosed comment` —— 而且它会**连累整个文件**，
 *   表现为"这个文件里定义的所有类型都找不到"，很难一眼看出根因。
 *
 * ### 已知的接口陷阱（都是实测踩出来的）
 *
 * 1. **`getPage` 返回的 `id` 不是词条 id**。它是卡片 id（如 `23654`），
 *    拿它去 `getEntryDetail` 会返回 `code=2031 当前词条不存在`。
 *    真正的词条 id 在 **`entryId`** 字段（19 位雪花号，如 `1543050481616060416`）。
 * 2. **记录里有 `invalid` 字段**，表示这是占位卡片（正文不存在）。
 *    要跳过，否则用户点进去只能看到报错。
 * 3. `code == 200` 才是成功 —— 与抽卡那套（`code == 0`）**又不一样**。
 *
 * ### 缓存
 *
 * 接口是逆向来的、无 SLA，且原作者自己也加了 24 小时缓存。这里照做：
 * 分类树 24 小时、条目列表 6 小时、词条详情不缓存（单个 400KB，缓存收益低而占空间）。
 */
object KuroWikiApi {

    private const val BASE = "https://api.kurobbs.com"

    /** 鸣潮的 wiki_type */
    private const val WIKI_TYPE = "9"

    /**
     * 实测不校验内容的随机 hex。
     *
     * ★ 每次会话固定一个而不是每个请求都换：库洛有风控前科，
     *   短时间内大量不同 devcode 不是一个正常客户端的样子。
     */
    private val DEV_CODE: String by lazy {
        val hex = "0123456789abcdef"
        (1..32).map { hex.random() }.joinToString("")
    }

    private var cacheDir: File? = null

    fun install(context: Context) {
        if (cacheDir != null) return
        val d = File(context.applicationContext.filesDir, "wuwa_wiki")
        runCatching { d.mkdirs() }
        cacheDir = d
    }

    // -----------------------------------------------------------------------
    // 分类树
    // -----------------------------------------------------------------------

    /** 分类树。缓存 24 小时 —— 分类很少变。 */
    suspend fun tree(): WikiResult<List<WikiCategory>> =
        cached("tree", 24 * 3600_000L) { post("/wiki/core/catalogue/config/getTree", "") }
            .let { raw ->
                when (raw) {
                    is WikiResult.Ok -> WikiResult.Ok(parseTree(raw.value.optJSONObject("data")))
                    is WikiResult.Failure -> raw
                }
            }

    private fun parseTree(node: JSONObject?): List<WikiCategory> {
        if (node == null) return emptyList()
        return parseChildren(node.optJSONArray("children"))
    }

    private fun parseChildren(arr: JSONArray?): List<WikiCategory> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WikiCategory(
                id = o.optInt("id"),
                name = o.optString("name"),
                // hasItem=true 才说明这个分类下有内容可列，否则是个纯分组
                hasItem = o.optBoolean("hasItem", false),
                children = parseChildren(o.optJSONArray("children")),
            )
        }.filter { it.name.isNotBlank() }
    }

    // -----------------------------------------------------------------------
    // 分类下的条目
    // -----------------------------------------------------------------------

    /** 某个分类的条目列表。缓存 6 小时。 */
    suspend fun entries(catalogueId: Int, pageNo: Int = 1, pageSize: Int = 100): WikiResult<List<WikiEntry>> =
        cached("page_${catalogueId}_$pageNo", 6 * 3600_000L) {
            post("/wiki/core/catalogue/item/getPage", "catalogueId=$catalogueId&pageNo=$pageNo&pageSize=$pageSize")
        }.let { raw ->
            when (raw) {
                is WikiResult.Ok -> WikiResult.Ok(parseEntries(raw.value))
                is WikiResult.Failure -> raw
            }
        }

    /**
     * 分类页：条目 + 筛选条件树。
     *
     * ★ 与 [entries] 是**同一个请求**，只是多解析出 `tagTree` ——
     *   单独再发一次请求只为拿筛选条件，是白白多一次网络往返。
     */
    suspend fun page(catalogueId: Int): WikiResult<WikiPage> =
        cached("page_${catalogueId}_1", 6 * 3600_000L) {
            post("/wiki/core/catalogue/item/getPage", "catalogueId=$catalogueId&pageNo=1&pageSize=100")
        }.let { raw ->
            when (raw) {
                is WikiResult.Ok -> WikiResult.Ok(
                    WikiPage(
                        entries = parseEntries(raw.value),
                        filterGroups = parseTagGroups(raw.value),
                    ),
                )
                is WikiResult.Failure -> raw
            }
        }

    private fun parseEntries(root: JSONObject): List<WikiEntry> {
        val records = root.optJSONObject("data")
            ?.optJSONObject("results")
            ?.optJSONArray("records") ?: return emptyList()
        return (0 until records.length()).mapNotNull { i ->
            val o = records.optJSONObject(i) ?: return@mapNotNull null
            val entryId = o.optLong("entryId", 0L)
            val name = o.optString("name").ifBlank {
                o.optJSONObject("content")?.optString("title").orEmpty()
            }
            // ★ 跳过占位卡片：invalid=true 或没有 entryId 的，点进去只会看到 2031
            if (name.isBlank() || entryId == 0L || o.optBoolean("invalid", false)) return@mapNotNull null
            val content = o.optJSONObject("content")
            WikiEntry(
                entryId = entryId.toString(),
                name = name,
                star = content?.optString("star")?.toIntOrNull() ?: 0,
                imageUrl = content?.optString("contentUrl")?.takeIf { it.startsWith("http") },
                subtitle = content?.optString("optionalTitle").orEmpty(),
                relateTagIds = content?.optJSONArray("relateTagIds")?.let { arr ->
                    (0 until arr.length()).mapNotNull { j -> arr.optString(j).takeIf { it.isNotBlank() } }
                }.orEmpty(),
                cornerMark = content?.optBoolean("cornerMark", false) ?: false,
                cornerMarkUrl = content?.optString("cornerMarkUrl")?.takeIf { it.startsWith("http") },
            )
        }
    }

    /**
     * 分类的筛选条件树。
     *
     * ★ 实测结构（见 `docs/wiki-filter-api.md`）：
     *   `data.tagTree` 是**当前分类这个节点本身**（不是数组），
     *   筛选分组在它的 `children` 里，分组的子项才是可选标签。
     *
     * ★ **必须从接口拿，不能写死**：不同分类的筛选条件不同
     *   （「共鸣者」是稀有度/属性/武器/实装版本/风格定位，
     *     「武器投影」显然不一样）。
     */
    private fun parseTagGroups(root: JSONObject): List<WikiTagGroup> {
        val children = root.optJSONObject("data")
            ?.optJSONObject("tagTree")
            ?.optJSONArray("children") ?: return emptyList()
        return (0 until children.length()).mapNotNull { i ->
            val g = children.optJSONObject(i) ?: return@mapNotNull null
            val name = g.optString("name")
            val tags = g.optJSONArray("children")?.let { arr ->
                (0 until arr.length()).mapNotNull { j ->
                    val t = arr.optJSONObject(j) ?: return@mapNotNull null
                    val tName = t.optString("name")
                    if (tName.isBlank()) null else WikiTag(id = t.optInt("id").toString(), name = tName)
                }
            }.orEmpty()
            // 没有叶子标签的分组不显示（空分组点了没反应，只会让人困惑）
            if (name.isBlank() || tags.isEmpty()) null else WikiTagGroup(id = g.optInt("id"), name = name, tags = tags)
        }
    }

    // -----------------------------------------------------------------------
    // 词条详情
    // -----------------------------------------------------------------------

    /** 词条详情。**不缓存** —— 单个约 400KB，缓存收益低而占空间。 */
    suspend fun detail(entryId: String): WikiResult<WikiEntryDetail> =
        post("/wiki/core/catalogue/item/getEntryDetail", "id=$entryId").let { raw ->
            when (raw) {
                is WikiResult.Ok -> runCatching {
                    val d = raw.value.getJSONObject("data")
                    WikiEntryDetail(
                        name = d.optString("name"),
                        orgFullName = d.optString("orgFullName"),
                        browseCount = d.optLong("browseCount", 0L),
                        lastUpdateTime = d.optString("lastUpdateTime"),
                        modules = parseModules(d.optJSONObject("content")),
                    )
                }.fold(
                    onSuccess = { WikiResult.Ok(it) },
                    onFailure = { WikiResult.Failure("返回结构与预期不符") },
                )
                is WikiResult.Failure -> raw
            }
        }

    private fun parseModules(content: JSONObject?): List<WikiModule> {
        val arr = content?.optJSONArray("modules") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val comps = m.optJSONArray("components") ?: JSONArray()
            WikiModule(
                title = m.optString("title"),
                components = (0 until comps.length()).mapNotNull { j ->
                    comps.optJSONObject(j)?.let(::parseComponent)
                },
            )
        }.filter { it.components.isNotEmpty() }
    }

    private fun parseComponent(o: JSONObject): WikiComponent = when (o.optString("type")) {
        "role-component" -> {
            val role = o.optJSONObject("role")
            WikiComponent.Role(
                title = role?.optString("title").orEmpty(),
                info = role?.optJSONArray("info")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("text") }
                        .filter { it.isNotBlank() }
                } ?: emptyList(),
                figures = role?.optJSONArray("figures")?.let { arr ->
                    (0 until arr.length()).mapNotNull { k ->
                        val f = arr.optJSONObject(k) ?: return@mapNotNull null
                        val url = f.optString("url")
                        if (!url.startsWith("http")) null
                        else WikiFigure(url = url, caption = f.optString("name"))
                    }
                } ?: emptyList(),
            )
        }
        "tabs-component" -> WikiComponent.Tabs(
            title = o.optString("title"),
            tabs = o.optJSONArray("tabs")?.let { arr ->
                (0 until arr.length()).mapNotNull { k ->
                    val tb = arr.optJSONObject(k) ?: return@mapNotNull null
                    WikiTab(title = tb.optString("title"), html = tb.optString("content"))
                }
            } ?: emptyList(),
        )
        "audio-component" -> WikiComponent.Audio(
            title = o.optString("title"),
            tabs = o.optJSONArray("mediaTabs")?.let { arr ->
                (0 until arr.length()).mapNotNull { k ->
                    val tb = arr.optJSONObject(k) ?: return@mapNotNull null
                    val list = tb.optJSONArray("mediaList") ?: JSONArray()
                    WikiAudioTab(
                        title = tb.optString("title"),
                        lines = (0 until list.length()).mapNotNull { n ->
                            val item = list.optJSONObject(n) ?: return@mapNotNull null
                            // 语音正文就是台词文本 —— 可读且有信息量
                            val text = item.optString("content")
                            if (text.isBlank()) null
                            else WikiAudioLine(label = item.optString("audioTitle"), text = text)
                        },
                    )
                }
            } ?: emptyList(),
        )
        // basic-component / text-component / 其它未知类型：正文都是 HTML
        else -> WikiComponent.Html(
            title = o.optString("title"),
            html = o.optString("content"),
        )
    }

    // -----------------------------------------------------------------------
    // 传输
    // -----------------------------------------------------------------------

    private suspend fun cached(
        key: String,
        ttlMillis: Long,
        fetch: suspend () -> WikiResult<JSONObject>,
    ): WikiResult<JSONObject> {
        val f = cacheDir?.let { File(it, "$key.json") }
        if (f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < ttlMillis) {
            runCatching { return WikiResult.Ok(JSONObject(f.readText())) }
        }
        val fresh = fetch()
        if (fresh is WikiResult.Ok && f != null) {
            runCatching { f.writeText(fresh.value.toString()) }
        }
        return fresh
    }

    private suspend fun post(path: String, form: String): WikiResult<JSONObject> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                setRequestProperty("source", "h5")
                setRequestProperty("wiki_type", WIKI_TYPE)
                setRequestProperty("devcode", DEV_CODE)
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/127 Mobile Safari/537.36")
            }
            conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }

            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

            if (status !in 200..299) return@withContext WikiResult.Failure("服务返回 HTTP $status")

            val json = JSONObject(text)
            // ★ 这套接口 code == 200 才是成功
            if (json.optInt("code", -1) != 200) {
                return@withContext WikiResult.Failure(json.optString("msg").ifBlank { "请求失败" })
            }
            WikiResult.Ok(json)
        } catch (e: Exception) {
            WikiResult.Failure("网络错误：${e.javaClass.simpleName}")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}

sealed interface WikiResult<out T> {
    data class Ok<T>(val value: T) : WikiResult<T>
    data class Failure(val message: String) : WikiResult<Nothing>
}

data class WikiCategory(
    val id: Int,
    val name: String,
    /** 该分类下是否直接有内容条目 */
    val hasItem: Boolean,
    val children: List<WikiCategory>,
)

data class WikiEntry(
    /** ★ 词条 id（19 位雪花号）。**不是** `getPage` 里的 `id` 字段 */
    val entryId: String,
    val name: String,
    val star: Int,
    val imageUrl: String?,
    val subtitle: String,
    /**
     * 该词条关联的标签 id（对应 [WikiTagGroup.tags]）。
     *
     * ★ 这是**筛选的唯一依据**，来自接口的 `content.relateTagIds`。
     *   实测「心」→ `[210,214,532,1058,758,760,764]`，
     *   分别落在 稀有度 / 属性 / 武器 / 实装版本 / 风格定位 五个分组里。
     */
    val relateTagIds: List<String> = emptyList(),
    /** 左上角角标（新 / 复刻 / 预告）。官方卡片有这个，实测字段存在 */
    val cornerMark: Boolean = false,
    /**
     * 角标图片 URL。
     *
     * ★ 用它而不是自己按 [cornerMark] 画一个「新」字：
     *   那个布尔值只能说明"有角标"，**说不出是「新」还是「复刻」还是「预」**。
     *   自己填一个字就是编数据。官方既然给了图，就直接显示官方那张图。
     */
    val cornerMarkUrl: String? = null,
)

/**
 * 一个筛选分组（如「稀有度」「属性」）。
 *
 * ★ [id] 是分组 id，**筛选语义按分组走**：
 *   同一分组内多选是**或**，不同分组之间是**与**。
 *   所以过滤时必须知道每个选中标签属于哪个分组 ——
 *   只用一个扁平集合会得到"全或"的错误语义（见 `WikiFilter.kt`）。
 */
data class WikiTagGroup(
    val id: Int,
    val name: String,
    val tags: List<WikiTag>,
)

/** 分组下的一个可选标签（如「五星」「导电」） */
data class WikiTag(val id: String, val name: String)

/** 分类页的一次请求结果：条目 + 筛选条件树（同一个响应里出来的） */
data class WikiPage(
    val entries: List<WikiEntry>,
    val filterGroups: List<WikiTagGroup>,
)

data class WikiEntryDetail(
    val name: String,
    val orgFullName: String,
    val browseCount: Long,
    val lastUpdateTime: String,
    val modules: List<WikiModule>,
)

data class WikiModule(val title: String, val components: List<WikiComponent>)

data class WikiFigure(val url: String, val caption: String)

data class WikiTab(val title: String, val html: String)

data class WikiAudioLine(val label: String, val text: String)

data class WikiAudioTab(val title: String, val lines: List<WikiAudioLine>)

/**
 * 词条组件。
 *
 * ★ 除 [Role] / [Tabs] / [Audio] 外的类型，正文都是**原样 HTML**
 *   （`<table>` / `<span style>` / `<img>` / `<hr>`）—— 所以详情页必须用 WebView 渲染，
 *   用 Compose 手写一个 HTML 渲染器是不现实的。
 */
sealed interface WikiComponent {
    data class Html(val title: String, val html: String) : WikiComponent
    data class Role(val title: String, val info: List<String>, val figures: List<WikiFigure>) : WikiComponent
    data class Tabs(val title: String, val tabs: List<WikiTab>) : WikiComponent
    data class Audio(val title: String, val tabs: List<WikiAudioTab>) : WikiComponent
}
