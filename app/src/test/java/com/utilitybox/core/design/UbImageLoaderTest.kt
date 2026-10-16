package com.utilitybox.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标缓存的纯逻辑部分。
 *
 * ★ 这一组测的是**磁盘缓存的文件名映射** —— 它出错的表现是
 *   "某个物品显示成另一个物品的图"，只在特定数据下复现，极难排查。
 */
class UbImageLoaderTest {

    @Test
    fun `文件名不含路径分隔符与查询字符`() {
        // URL 里有 / ? & = 这些，直接当文件名会被文件系统截断或拒绝
        val url = "https://playerhub.df.qq.com/obj?x=1&y=2/a.png"
        val key = UbImageLoader.diskKey(url)
        listOf("/", "\\", "?", "&", "=", ":", "*", "\"").forEach { bad ->
            assertTrue("文件名不得包含 $bad：$key", !key.contains(bad))
        }
    }

    @Test
    fun `同一 URL 稳定映射到同一文件名`() {
        val url = "https://example.test/a.png"
        assertEquals(UbImageLoader.diskKey(url), UbImageLoader.diskKey(url))
    }

    @Test
    fun `不同 URL 映射到不同文件名`() {
        // ★ 这是这条测试存在的理由：如果这里用 `hashCode()`（32 位），
        //   113 个 URL 就有可观的碰撞概率，碰撞后两张图互相覆盖 ——
        //   表现为"某个物品显示成别人的图"，且只在特定数据下复现。
        val urls = (1..500).map { "https://playerhub.df.qq.com/item/$it.png" }
        val keys = urls.map { UbImageLoader.diskKey(it) }
        assertEquals("文件名校验：有碰撞", urls.size, keys.toSet().size)
    }

    @Test
    fun `文件名长度可控`() {
        // 文件系统对文件名长度有限制（常见 255 字节）；URL 可能很长
        val longUrl = "https://example.test/" + "x".repeat(2000) + ".png"
        val key = UbImageLoader.diskKey(longUrl)
        assertTrue("文件名过长：${key.length}", key.length <= 64)
        assertTrue("文件名不该为空", key.isNotEmpty())
    }

    @Test
    fun `相似 URL 不会被当成同一个`() {
        // 只差一个字符也必须分开 —— 物品图标 URL 常常只有末尾的 id 不同
        assertNotEquals(
            UbImageLoader.diskKey("https://a.test/1.png"),
            UbImageLoader.diskKey("https://a.test/2.png"),
        )
    }
}
