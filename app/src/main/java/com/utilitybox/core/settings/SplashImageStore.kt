package com.utilitybox.core.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 启动页背景图的存取（零新依赖）。
 *
 * ### 为什么"选中即复制"而不是保存外部 URI
 *
 * 用户从相册选一张图，拿到的是 `content://` URI。直接存它有三个坑：
 *
 * 1. **跨重启失效** —— 必须 `takePersistableUriPermission` 才能长期有效，
 *    而很多相册应用返回的 URI 并不支持持久化授权。
 * 2. **用户随时能删** —— 原图删了、或撤销了授权，启动页就变空白，
 *    而用户根本不会把这两件事联系起来。
 * 3. **可能是云图** —— 部分 URI 指向云端，启动时要等下载。
 *
 * 复制进应用私有目录后，这三条一次性消失：不依赖任何外部授权、
 * 删原图不影响、本地读取无延迟。代价是占一份存储 —— 用降采样把它压到可控范围。
 *
 * ### 为什么要降采样
 *
 * 手机相册里一张图可能是 4000×3000 / 8 MB。启动页最大也就是屏幕尺寸，
 * 原样保存既浪费空间，解码时还会直接 OOM（启动页 OOM = 打不开应用）。
 * 这里按 [MAX_EDGE] 长边限制做 2 的幂次降采样，再转 JPEG。
 */
object SplashImageStore {

    private const val DIR = "splash"
    private const val FILE_NAME = "bg.jpg"

    /** 长边上限：足够覆盖 1440p 屏幕，又不会让解码占用失控 */
    private const val MAX_EDGE = 1600

    private fun target(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }.let { File(it, FILE_NAME) }

    /** 保存结果：成功返回**绝对路径**，失败返回 null（调用方据此提示用户） */
    fun saveFromUri(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        return try {
            // 第一遍：只读尺寸，不算采样率就解码必 OOM
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val file = target(context)
            FileOutputStream(file).use { out ->
                // JPEG 没有透明通道 —— 启动页背景本来也不需要，换来明显更小的体积
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            // 选图失败不该让应用崩 —— 返回 null，界面提示"这张图读不了"
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** 读取启动页背景；路径为空、文件丢失、解码失败都返回 null（调用方回落到默认启动页） */
    fun load(path: String?, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxOf(reqWidth, reqHeight))
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** 删除已保存的背景（用户选择"移除"时） */
    fun clear(context: Context) {
        runCatching { target(context).delete() }
    }

    /**
     * 标准 2 的幂次采样率。
     *
     * ★ 抽成 `internal` 纯函数是为了能单测 —— 这段逻辑出错的表现是
     *   "某些图能设、某些图一设就闪退"，只在特定尺寸的图上复现，很难手工覆盖。
     */
    internal fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }
}
