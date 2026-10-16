package com.utilitybox.core.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远程图片加载（零新增依赖）。
 *
 * ### 为什么不用 Coil / Glide
 *
 * 项目从 P00 起维持**零新增依赖**（除用户裁决引入的 Haze）：
 * 核心工具层（JSON 解析、HTTP 客户端、图标）都是自写的。
 *
 * ⚠️ 写这段文档时踩过一个坑：Kotlin 的块注释**支持嵌套**（与 Java 不同），
 * 注释正文里出现斜杠加星号会被当成新的注释开头，导致整个文件"注释未闭合"编译失败。
 * 这里同样用 `HttpURLConnection` + `BitmapFactory` 自己加载 —— 功能足够，
 * 且不引入一个只为显示缩略图的图片库。
 *
 * ### 缓存层次（P11 补 13 补齐第三层）
 *
 * ```
 *   内存 LRU  →  磁盘缓存  →  网络
 * ```
 *
 * 原本只有内存层：**每次冷启动都要把 113 张图标重新下载一遍**。
 * 用户反馈"要等一下才能加载出来"，这是其中一半的原因（另一半是入场动画，见 UbInteraction）。
 *
 * ★★ 顺带修掉一个更严重的问题：`download()` 原本**开两次连接** ——
 *   先用 `conn` 读一次边界算采样率，再开 `conn2` 重新请求拿真图解码。
 *   原因是 `BitmapFactory.decodeStream` 会把流消费掉，第二遍必须重新请求。
 *   但这样**每张图两个网络往返**，加载耗时直接翻倍。
 *   正确做法：**先把字节读进内存**（一次请求），再从字节数组解码两次
 *   （`decodeByteArray` 可以反复调用）。图标只有几十 KB，全部读进内存没有压力。
 */
object UbImageLoader {

    /**
     * 内存缓存。
     *
     * ★ 用**字节数**而不是条目数作为上限：
     *   一张缩略图和一个原图的体积可能差两个数量级，
     *   按数量限制会让缓存的实际内存占用失控。
     *   取可用堆的 1/8（Android 官方推荐值）。
     */
    private val cache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        object : LruCache<String, Bitmap>(maxKb / 8) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    /** 正在下载中的 URL —— 防止同一张图并发重复下载 */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Bitmap?>>()

    /** 磁盘缓存目录；未调用 [install] 时为 null（此时退化为纯内存 + 网络） */
    @Volatile
    private var diskDir: java.io.File? = null

    /** 磁盘缓存上限。图标是几十 KB 级，48MB 足够装下全部条目还有余量 */
    private const val DISK_LIMIT_BYTES = 48L * 1024 * 1024

    /**
     * 初始化磁盘缓存。**必须在 [load] 之前调用一次**（MainActivity.onCreate 里调）。
     *
     * ★ 接收 applicationContext：持有 Activity 的 Context 会泄漏整个界面。
     */
    fun install(context: android.content.Context) {
        if (diskDir != null) return
        val dir = java.io.File(context.applicationContext.cacheDir, "ub_img")
        runCatching { dir.mkdirs() }
        diskDir = dir
        // 启动时清一次超额（上次运行可能退出得很突然，没来得及回收）
        runCatching { trimDisk() }
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(
        url: String,
        reqWidthPx: Int,
        reqHeightPx: Int,
        scope: CoroutineScope,
    ): Bitmap? {
        cache.get(url)?.let { return it }
        val deferred = inFlight.getOrPut(url) {
            scope.async(Dispatchers.IO) { loadFromDiskOrNetwork(url, reqWidthPx, reqHeightPx) }
        }
        return try {
            deferred.await()?.also { cache.put(url, it) }
        } finally {
            inFlight.remove(url)
        }
    }

    /** 磁盘 → 网络。磁盘命中时**完全不联网**，这是"滑动不再等"的关键 */
    private fun loadFromDiskOrNetwork(url: String, reqW: Int, reqH: Int): Bitmap? {
        val file = diskFile(url)

        if (file != null && file.exists() && file.length() > 0) {
            decodeFromBytes(runCatching { file.readBytes() }.getOrNull(), reqW, reqH)?.let { return it }
            // 文件损坏（半截写入 / 磁盘错误）→ 删掉，下次重新下载
            runCatching { file.delete() }
        }

        val bytes = fetchBytes(url) ?: return null
        // 先落盘再解码：解码失败不该让这份字节白下载
        file?.let { f -> runCatching { f.writeBytes(bytes); trimDisk() } }
        return decodeFromBytes(bytes, reqW, reqH)
    }

    /** 一次网络请求拿全部字节 —— 取代原先的"两个连接、两次请求" */
    private fun fetchBytes(url: String): ByteArray? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            // 图床按 UA 可能做防盗链；带一个正常 UA 更稳
            setRequestProperty("User-Agent", "UtilityBox/0.1 (Android)")
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** 从字节解码两次（第一遍只读边界算采样率，第二遍真解码）—— 不再需要第二次请求 */
    private fun decodeFromBytes(bytes: ByteArray?, reqW: Int, reqH: Int): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
                inPreferredConfig = Bitmap.Config.RGB_565   // 缩略图不需要 ARGB
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun diskFile(url: String): java.io.File? =
        diskDir?.let { java.io.File(it, diskKey(url)) }

    /** 超出上限时按**最后修改时间**从旧到新删。简单可靠，无需维护额外的索引文件 */
    private fun trimDisk() {
        val dir = diskDir ?: return
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_LIMIT_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (total <= DISK_LIMIT_BYTES) return
            val len = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) total -= len
        }
    }

    /** 标准 2 的幂采样率算法（避免解码出远大于需要的位图） */
    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var sample = 1
        if (width <= 0 || height <= 0) return 1
        while (width / (sample * 2) >= reqWidth && height / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    /**
     * URL → 磁盘文件名。
     *
     * ★ 用 SHA-256 而不是 `hashCode()`：
     *   `hashCode` 只有 32 位，113 个 URL 就有可观的碰撞概率；
     *   一旦碰撞，两张不同的图会互相覆盖 —— 表现为"某个物品显示成另一个物品的图"，
     *   而且**只在特定数据下复现**，极难排查。
     *
     * ★ 也不能直接拿 URL 当文件名：里面有 `/`、`?`、`&`，在文件系统上是非法或被截断的。
     */
    internal fun diskKey(url: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * 圆形物品图标。
 *
 * @param url 为 null 或加载失败时显示**占位方块**（而不是留白）
 */
@Composable
fun UbItemIcon(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val c = LocalUbColors.current
    val r = LocalUbRadius.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sizePx = with(density) { size.roundToPx() }

    var bitmap by remember(url) {
        mutableStateOf(url?.let { UbImageLoader.cached(it) })
    }

    LaunchedEffect(url) {
        if (url == null || bitmap != null) return@LaunchedEffect
        bitmap = UbImageLoader.load(url, sizePx, sizePx, scope)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(r.field))
            .background(c.bgSubtle),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        }
        // 无图/失败时不画任何东西 —— 底色本身就是占位，
        // 用户看到的是一个空框，能明确知道"这里本该有图"
    }
}
