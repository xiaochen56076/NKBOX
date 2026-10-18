package com.utilitybox.feature.settings

import androidx.compose.ui.graphics.Color
import com.utilitybox.core.design.UbAccentPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外观页「主题色」预设的**选中态判定**（P11 补 93）。
 *
 * ### 为什么要有这组测试
 *
 * 用户真机反馈：「这里的颜色有 bug，选一个会多选，不止这一个」——
 * 截图里「朱红」和「橙」**同时打了勾**。
 *
 * 根因是选中判定用了模糊的 `sameHueFamily`（同色系即命中），
 * 而选中态表达的是「就是这一个」。这类 bug **光编译和跑旧测试都发现不了**，
 * 只能靠针对性的用例锁住。
 *
 * ### 测法的关键：每个用例都断言"只有恰好 N 个被选中"
 *
 * 只断言"该选中的选中了"是**不够的** ——
 * 原来的 bug 恰恰是"该选中的确实选中了，只是别的也被选中了"。
 * 所以这里统一用 [selectedCount] 数个数。
 */
class AccentPresetSelectionTest {

    /** 数一下在给定 accentSeed 下有多少个预设会打勾 */
    private fun selectedCount(accentSeed: Color?): Int =
        UbAccentPresets.count { isAccentPresetSelected(it.seed, accentSeed) }

    /** 找出打勾的那个预设的标签（应为唯一） */
    private fun selectedLabels(accentSeed: Color?): List<String> =
        UbAccentPresets.filter { isAccentPresetSelected(it.seed, accentSeed) }.map { it.label }

    @Test
    fun `没有自定义种子时 只有默认被选中`() {
        assertEquals("默认态必须恰好选中 1 个", 1, selectedCount(null))
        assertTrue(
            "默认态选中的必须是「默认」那一项，实际是 ${selectedLabels(null)}",
            selectedLabels(null).any { it.contains("默认") },
        )
    }

    @Test
    fun `选中任意一个预设时 有且仅有它自己被选中`() {
        val presets = UbAccentPresets.filter { it.seed != null }
        assertTrue("预设不能为空，否则本测试没有意义", presets.isNotEmpty())

        // ★ 逐个遍历：这就是「不止这一个」——任何一对同色系预设都可能踩到
        presets.forEach { p ->
            val labels = selectedLabels(p.seed)
            assertEquals(
                "选中「${p.label}」时应当恰好选中 1 个，实际选中的是 $labels",
                1,
                labels.size,
            )
            assertEquals(
                "选中「${p.label}」时选中的必须是它自己，实际是 $labels",
                p.label,
                labels.first(),
            )
        }
    }

    @Test
    fun `自定义颜色时 所有预设都不选中`() {
        // 色轮里选的、不属于任何预设的颜色
        val custom = Color(0xFF123456)
        assertEquals(
            "自定义色不属于任何预设，不该有预设打勾，实际是 ${selectedLabels(custom)}",
            0,
            selectedCount(custom),
        )
    }

    @Test
    fun `同色系的不同预设不会互相命中`() {
        // ★ 这就是用户截图里的那一对：「朱红」与「橙」同属红色系
        val red = UbAccentPresets.firstOrNull { it.label.contains("朱红") }
        val orange = UbAccentPresets.firstOrNull { it.label.contains("橙") }
        if (red?.seed != null && orange?.seed != null) {
            assertTrue(
                "选中朱红时 橙不能也打勾",
                !isAccentPresetSelected(orange.seed, red.seed),
            )
            assertTrue(
                "选中橙时 朱红不能也打勾",
                !isAccentPresetSelected(red.seed, orange.seed),
            )
        }
    }

    @Test
    fun `空种子的语义 默认只在没有自定义色时成立`() {
        // 预设的 seed 为 null = 「默认」那一项
        assertTrue("无自定义色 → 默认选中", isAccentPresetSelected(null, null))
        assertFalse(
            "有自定义色 → 默认不选中",
            isAccentPresetSelected(null, Color(0xFF123456)),
        )
    }
}
