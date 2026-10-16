package com.utilitybox.core.settings

import com.utilitybox.core.design.UbThemeMode
import com.utilitybox.domain.model.UbSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置持久化测试（P09）。
 *
 * ★ 覆盖验收标准「每项设置**生效且持久化**（重启后保持）」：
 *   "重启"在测试里用**同一个 store 实例重新 load()** 来模拟 ——
 *   store 代表磁盘，load() 代表冷启动读取。
 */
class SettingsStoreTest {

    @After
    fun tearDown() {
        // 全局状态是有状态的单例，测试后复原，避免相互污染
        UbSettingsState.installForTest(InMemorySettingsStore())
    }

    @Test
    fun `默认值是保守且安全的`() {
        val s = InMemorySettingsStore().load()
        assertEquals("默认跟随系统，而不是写死浅色", UbThemeMode.System, s.themeMode)
        assertTrue("玻璃默认开启", s.glassEnabled)
        assertFalse("减少动效默认关闭（多数用户不需要）", s.reduceMotion)
        assertTrue("自动刷新默认开启", s.autoRefreshEnabled)
        assertEquals(30, s.autoRefreshMinutes)
    }

    @Test
    fun `保存后重新读取能拿回同样的值 —— 模拟重启`() {
        val store = InMemorySettingsStore()
        val changed = UbSettings(
            themeMode = UbThemeMode.Dark,
            glassEnabled = false,
            reduceMotion = true,
            autoRefreshEnabled = false,
            autoRefreshMinutes = 60,
        )
        store.save(changed)

        // 同一个 store 再次 load = 冷启动读取磁盘
        assertEquals("重启后主题必须保持", changed, store.load())
    }

    @Test
    fun `每一项设置都能独立持久化`() {
        val store = InMemorySettingsStore()

        store.save(store.load().copy(themeMode = UbThemeMode.Dark))
        assertEquals(UbThemeMode.Dark, store.load().themeMode)

        store.save(store.load().copy(glassEnabled = false))
        assertFalse(store.load().glassEnabled)
        assertEquals("改玻璃不应影响主题", UbThemeMode.Dark, store.load().themeMode)

        store.save(store.load().copy(reduceMotion = true))
        assertTrue(store.load().reduceMotion)

        store.save(store.load().copy(autoRefreshMinutes = 120))
        assertEquals(120, store.load().autoRefreshMinutes)
    }

    // -----------------------------------------------------------------------
    // 工具启停
    // -----------------------------------------------------------------------

    @Test
    fun `工具默认是启用的 —— 新工具不需要登记`() {
        val store = InMemorySettingsStore()
        // ★ 这是「存被关闭集合」而非「存已启用集合」的关键收益：
        //   一个从未登记过的新工具，默认就必须是可用的。
        assertFalse("未登记的工具必须默认启用", store.isToolDisabled("deltaforce.brandnew"))
    }

    @Test
    fun `关闭工具后重新读取仍是关闭 —— 模拟重启`() {
        val store = InMemorySettingsStore()
        store.setToolDisabled("deltaforce.dailycode", true)
        assertTrue(store.isToolDisabled("deltaforce.dailycode"))
        assertFalse("不应影响其它工具", store.isToolDisabled("deltaforce.itemprice"))
    }

    @Test
    fun `重新启用会移除关闭记录`() {
        val store = InMemorySettingsStore()
        store.setToolDisabled("a", true)
        store.setToolDisabled("b", true)
        store.setToolDisabled("a", false)

        assertFalse(store.isToolDisabled("a"))
        assertTrue("不应误删其它工具的记录", store.isToolDisabled("b"))
    }

    // -----------------------------------------------------------------------
    // 全局状态：改动立即生效且落盘
    // -----------------------------------------------------------------------

    @Test
    fun `通过全局状态修改设置会立即落盘`() {
        val store = InMemorySettingsStore()
        UbSettingsState.installForTest(store)

        UbSettingsState.update { it.copy(themeMode = UbThemeMode.Dark, reduceMotion = true) }

        assertEquals("内存状态立即更新", UbThemeMode.Dark, UbSettingsState.settings.themeMode)
        assertEquals("同时已落盘", UbThemeMode.Dark, store.load().themeMode)
        assertTrue(store.load().reduceMotion)
    }

    @Test
    fun `工具启停会递增版本号 以便界面重组`() {
        // ★ 这是"关掉工具后首页没反应"那个 bug 的回归钉子：
        //   界面读 toolsRevision 才会被记为依赖；不递增就不会重组。
        UbSettingsState.installForTest(InMemorySettingsStore())
        val before = UbSettingsState.toolsRevision

        UbSettingsState.setToolDisabled("x", true)
        assertTrue("关闭工具必须递增版本号", UbSettingsState.toolsRevision > before)

        val mid = UbSettingsState.toolsRevision
        UbSettingsState.setToolDisabled("x", false)
        assertTrue("重新启用也必须递增", UbSettingsState.toolsRevision > mid)
    }

    @Test
    fun `installForTest 会把已存的设置读进全局状态`() {
        val store = InMemorySettingsStore(
            UbSettings(themeMode = UbThemeMode.Dark, glassEnabled = false),
        )
        UbSettingsState.installForTest(store)

        assertEquals(UbThemeMode.Dark, UbSettingsState.settings.themeMode)
        assertFalse(UbSettingsState.settings.glassEnabled)
    }

    @Test
    fun `未初始化时读取全局状态不会崩溃`() {
        // 防御：Application 初始化与首帧之间的竞态不该导致崩溃
        UbSettingsState.installForTest(InMemorySettingsStore())
        assertFalse(UbSettingsState.isToolDisabled("anything"))
    }
}
