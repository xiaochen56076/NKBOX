package com.utilitybox.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动页图片的降采样与设置持久化。
 *
 * ★ 采样率这段逻辑出错的表现是「某些图能设、某些图一设就闪退」——
 *   只在特定尺寸的图上复现，手工测试极难覆盖，必须靠单测钉住。
 */
class SplashImageStoreTest {

    @Test
    fun `小图不降采样`() {
        assertEquals(1, SplashImageStore.sampleSizeFor(800, 600, 1600))
        assertEquals(1, SplashImageStore.sampleSizeFor(1600, 1600, 1600))
    }

    @Test
    fun `大图按 2 的幂降采样`() {
        // 3200 长边要降到 1600 以内 → 采样率 2
        assertEquals(2, SplashImageStore.sampleSizeFor(3200, 2400, 1600))
        // 6400 → 4
        assertEquals(4, SplashImageStore.sampleSizeFor(6400, 4800, 1600))
        assertEquals(8, SplashImageStore.sampleSizeFor(12800, 9600, 1600))
    }

    @Test
    fun `降采样后长边不会小于目标`() {
        // 采样率取大了会让启动页背景糊掉 —— 这条守着"刚好覆盖屏幕"这个约束
        listOf(2000, 3000, 4000, 5000, 8000, 12345).forEach { w ->
            val s = SplashImageStore.sampleSizeFor(w, w, 1600)
            assertTrue("宽 $w 采样 $s 后只剩 ${w / s}，低于 1600", w / s >= 1600)
        }
    }

    @Test
    fun `非法尺寸返回 1 而不是崩溃或死循环`() {
        assertEquals(1, SplashImageStore.sampleSizeFor(0, 100, 1600))
        assertEquals(1, SplashImageStore.sampleSizeFor(100, 0, 1600))
        assertEquals(1, SplashImageStore.sampleSizeFor(-5, 100, 1600))
        assertEquals(1, SplashImageStore.sampleSizeFor(100, 100, 0))
    }

    @Test
    fun `极端尺寸不会算出 0`() {
        // 采样率一旦是 0，解码时会除零 —— 表现为启动直接崩
        assertTrue(SplashImageStore.sampleSizeFor(1, 1, 1600) >= 1)
        assertTrue(SplashImageStore.sampleSizeFor(100000, 100000, 1600) >= 1)
    }

    // ★ P11 补 93：这里原有 4 个 `sameHueFamily` 的测试，**已随函数一起删除**。
    //
    //   它们锁的是一个**错误的行为**：「同一色系就算命中同一个预设」。
    //   实测后果：外观页选「朱红」时「橙」也打勾（同属红色系）——用户截图反馈的 bug。
    //
    //   选中态需要的是"就是这一个"，判据只能是**精确相等**。
    //   留着这些测试等于把错误行为固化成"契约"，所以一并删掉。
}
