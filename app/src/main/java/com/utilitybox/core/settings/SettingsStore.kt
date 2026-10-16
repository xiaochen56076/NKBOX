package com.utilitybox.core.settings

import android.content.Context
import com.utilitybox.core.design.UbThemeMode
import com.utilitybox.domain.model.UbSettings

/**
 * 设置的读写契约。
 *
 * 与 `LocalCache` / `SecretStore` 同一取向：业务只依赖契约，
 * 单测可注入内存实现，不必碰 SharedPreferences。
 */
interface SettingsStore {
    fun load(): UbSettings
    fun save(settings: UbSettings)

    /**
     * 某个工具是否被用户**显式关闭**。
     *
     * ★ 为什么逐项查询而不是返回「已启用集合」：
     *   1. 不需要在 `core/` 里持有"全部工具清单" —— 那会让 core 依赖业务模块
     *      （违反 architecture.md R1/R6）
     *   2. 语义更准：未记录的工具**默认启用**
     *
     * ★ 存储上存的是**「被关闭的集合」**而不是「已启用的集合」：
     *   这样新增工具时**默认就是启用的**。若存"已启用集合"，
     *   每加一个工具都要记得补进集合，否则新工具会莫名其妙默认关闭 ——
     *   那会直接破坏 architecture.md §4.4「新增工具不改公共代码」。
     */
    fun isToolDisabled(toolId: String): Boolean
    fun setToolDisabled(toolId: String, disabled: Boolean)
}

/**
 * 基于 SharedPreferences 的实现（零新依赖）。
 *
 * ★ 每个读取都带**默认值与容错**：设置项损坏或缺失时回落到默认，
 *   绝不让应用因为一条设置读不出来而崩溃或白屏。
 */
class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): UbSettings = UbSettings(
        themeMode = readEnum(KEY_THEME, UbThemeMode.System),
        glassEnabled = prefs.getBoolean(KEY_GLASS, true),
        reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
        // 默认开启：老用户升级后行为不变（入口照常显示）
        showGameSwitch = prefs.getBoolean(KEY_SHOW_GAME_SWITCH, true),
        autoRefreshEnabled = prefs.getBoolean(KEY_AUTO_REFRESH, true),
        // ★★ 范围必须与 UI 允许输入的范围**完全一致**（P11 实测踩到的真 bug）。
        //
        //   原先写死 `coerceIn(5, 24 * 60)`，而数据设置页允许输入 1–1440 分钟。
        //   于是：用户在界面上填「2 分钟」→ 落盘确实是 2 →
        //   但**读回来被静默改成 5**，实际按 5 分钟跑，界面还显示着 2。
        //
        //   这类"静默改写用户输入"的问题特别难发现：保存成功、界面正确、
        //   只有行为不符预期。**这也是端到端验证才能抓到的缺陷**（单测全绿）。
        //
        //   现在两处共用同一个常量来源，不会再漂移。
        autoRefreshMinutes = prefs.getInt(KEY_AUTO_REFRESH_MIN, DEFAULT_AUTO_REFRESH_MIN)
            .coerceIn(AutoRefreshPolicy.MIN_MINUTES, AutoRefreshPolicy.MAX_MINUTES),
        // ★ 主题色种子：**没存过就是 null**（跟随主题默认色），不要在这里填一个具体蓝色 ——
        //   默认色随深浅主题不同，落盘成具体值会把用户钉死在其中一支。
        accentSeed = if (prefs.contains(KEY_ACCENT_SEED)) {
            prefs.getLong(KEY_ACCENT_SEED, 0L).takeIf { it != 0L }
        } else {
            null
        },
        // 启动页背景：路径可能指向已被清理的文件 —— 这里**只回路径**，
        // 存活性由读取方判断（文件不存在就当没设，回落到默认启动页）。
        splashImagePath = prefs.getString(KEY_SPLASH_IMAGE, null)?.takeIf { it.isNotBlank() },
    )

    override fun save(settings: UbSettings) {
        prefs.edit()
            .putString(KEY_THEME, settings.themeMode.name)
            .putBoolean(KEY_GLASS, settings.glassEnabled)
            .putBoolean(KEY_REDUCE_MOTION, settings.reduceMotion)
            .putBoolean(KEY_SHOW_GAME_SWITCH, settings.showGameSwitch)
            .putBoolean(KEY_AUTO_REFRESH, settings.autoRefreshEnabled)
            .putInt(KEY_AUTO_REFRESH_MIN, settings.autoRefreshMinutes)
            .apply {
                // 用 remove 表达"回到默认"，而不是写一个魔数 —— 见 UbSettings.accentSeed 的说明
                val seed = settings.accentSeed
                if (seed == null) remove(KEY_ACCENT_SEED) else putLong(KEY_ACCENT_SEED, seed)
                val splash = settings.splashImagePath
                if (splash == null) remove(KEY_SPLASH_IMAGE) else putString(KEY_SPLASH_IMAGE, splash)
            }
            .apply()
    }

    override fun isToolDisabled(toolId: String): Boolean =
        disabledTools().contains(toolId)

    override fun setToolDisabled(toolId: String, disabled: Boolean) {
        val set = disabledTools().toMutableSet()
        if (disabled) set.add(toolId) else set.remove(toolId)
        prefs.edit().putStringSet(KEY_DISABLED_TOOLS, set).apply()
    }

    private fun disabledTools(): Set<String> =
        prefs.getStringSet(KEY_DISABLED_TOOLS, emptySet()) ?: emptySet()

    private fun readEnum(key: String, fallback: UbThemeMode): UbThemeMode =
        prefs.getString(key, null)?.let { name ->
            runCatching { UbThemeMode.valueOf(name) }.getOrNull()
        } ?: fallback

    private companion object {
        const val PREFS_NAME = "utilitybox_settings"
        const val KEY_THEME = "theme"
        const val KEY_GLASS = "glass"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_SHOW_GAME_SWITCH = "show_game_switch"
        const val KEY_AUTO_REFRESH = "auto_refresh"
        const val KEY_AUTO_REFRESH_MIN = "auto_refresh_min"
        const val KEY_DISABLED_TOOLS = "disabled_tools"
        const val KEY_ACCENT_SEED = "accent_seed"
        const val KEY_SPLASH_IMAGE = "splash_image"

        /**
         * 自动刷新的默认间隔（分钟）。
         *
         * ★ 与数据设置页的预设档位「30 分钟」保持一致；
         *   更重要是：**读取时的范围校验必须与 UI 允许的范围一致**
         *   （见 `load()` 里的说明 —— 曾经两处不一致导致静默改写用户输入）。
         */
        const val DEFAULT_AUTO_REFRESH_MIN = 30
    }
}

/** 内存实现：供单元测试与预览使用 */
class InMemorySettingsStore(
    private var settings: UbSettings = UbSettings(),
) : SettingsStore {
    private val disabled = mutableSetOf<String>()

    override fun load(): UbSettings = settings
    override fun save(settings: UbSettings) {
        this.settings = settings
    }

    override fun isToolDisabled(toolId: String): Boolean = disabled.contains(toolId)
    override fun setToolDisabled(toolId: String, disabled: Boolean) {
        if (disabled) this.disabled.add(toolId) else this.disabled.remove(toolId)
    }
}
