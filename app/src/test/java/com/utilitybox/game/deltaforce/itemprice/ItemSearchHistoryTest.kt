package com.utilitybox.game.deltaforce.itemprice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索历史的纯逻辑测试（P11 补 53）。
 *
 * ★ 这里只测**纯函数**（`mergeHistory` / `encode` / `decode`）——
 *   不需要设备、不需要 Context，跑得快且稳定。
 *   `ItemSearchHistory` 里剩下的部分只是把结果存进 SharedPreferences，
 *   那部分没有"规则"，不值得为它搭一套 Robolectric。
 */
class ItemSearchHistoryTest {

    // ---- mergeHistory：用户要求的四条规则 ----

    @Test
    fun `最近搜的排最前`() {
        val r = mergeHistory(listOf("钢", "钛"), "铝", max = 20)
        assertEquals(listOf("铝", "钢", "钛"), r)
    }

    @Test
    fun `重复的提到最前_而不是多占一条`() {
        // ★ 这条最关键：否则反复搜同一个词会把历史撑满，把别的都挤掉
        val r = mergeHistory(listOf("钢", "钛", "铝"), "钛", max = 20)
        assertEquals(listOf("钛", "钢", "铝"), r)
        assertEquals("不能变成 4 条", 3, r.size)
    }

    @Test
    fun `去重忽略大小写`() {
        val r = mergeHistory(listOf("Titanium"), "titanium", max = 20)
        assertEquals(1, r.size)
        assertEquals("titanium", r[0])
    }

    @Test
    fun `超出上限丢最旧的`() {
        val r = mergeHistory(listOf("c", "b", "a"), "d", max = 3)
        assertEquals(listOf("d", "c", "b"), r)
        assertTrue("最旧的 a 应被丢掉", "a" !in r)
    }

    @Test
    fun `空白关键字不记录`() {
        // 清空输入框不是一次搜索，不该在历史里留一条空标签
        val before = listOf("钢")
        assertEquals(before, mergeHistory(before, "", max = 20))
        assertEquals(before, mergeHistory(before, "   ", max = 20))
    }

    @Test
    fun `首尾空白被裁掉`() {
        val r = mergeHistory(emptyList(), "  钛  ", max = 20)
        assertEquals(listOf("钛"), r)
    }

    @Test
    fun `带空白的同名词不会重复`() {
        val r = mergeHistory(listOf("钛"), " 钛 ", max = 20)
        assertEquals(1, r.size)
    }

    @Test
    fun `上限为0时返回空`() {
        assertEquals(emptyList<String>(), mergeHistory(listOf("a"), "b", max = 0))
    }

    @Test
    fun `空历史能正常加入第一条`() {
        assertEquals(listOf("钛"), mergeHistory(emptyList(), "钛", max = 20))
    }

    // ---- 编码 / 解码 ----

    @Test
    fun `编解码往返一致`() {
        val list = listOf("钛", "钢", "Titanium")
        assertEquals(list, decode(encode(list)))
    }

    @Test
    fun `物品名里常见的标点不会破坏分隔`() {
        // ★ 这就是选 U+0001 作分隔符的原因：逗号/竖线/空格都可能出现在名字里
        val tricky = listOf("M4A1, 5.56mm", "钢|钛", "a b c", "【限定】")
        assertEquals(tricky, decode(encode(tricky)))
    }

    @Test
    fun `脏数据当作没有_不抛异常`() {
        // 本地缓存坏了最坏是"历史空了"，不该让搜索页崩掉
        assertEquals(emptyList<String>(), decode(null))
        assertEquals(emptyList<String>(), decode(""))
    }

    @Test
    fun `解码丢掉空白项`() {
        // 历史里出现一条点不动的空标签是很糟的体验
        assertEquals(listOf("钛"), decode("钛\u0001\u0001   \u0001"))
    }

    @Test
    fun `解码会裁掉每项首尾空白`() {
        assertEquals(listOf("钛", "钢"), decode(" 钛 \u0001 钢 "))
    }
}
