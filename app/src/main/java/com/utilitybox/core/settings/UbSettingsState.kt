package com.utilitybox.core.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.utilitybox.domain.model.UbSettings

/**
 * 全局可观察的设置状态。
 *
 * ★ 为什么是 object（全局单例）而不是 CompositionLocal：
 *   - 设置要在**整棵树**（主题、玻璃档位、各工具页）被读取
 *   - 且要能被**任意位置**修改（设置页、未来的快捷开关）
 *   用 CompositionLocal 需要层层透传，反而更绕。
 *
 * ★ 为什么内部状态是 `mutableStateOf`：
 *   设置改动必须**立即触发重组**（P06 实测踩过：普通 `val` 不触发重组，
 *   用户以为没保存成功）。这里把"可观察"做进状态本身，调用方不会忘记。
 *
 * ★ 为什么立即落盘（写入即 save）：
 *   设置是低频操作，不需要批处理省 IO；而"改完没保存、重启丢失"
 *   是更糟糕的体验。每次改动都同步写入。
 */
object UbSettingsState {

    @Volatile
    private var store: SettingsStore? = null

    var settings: UbSettings by mutableStateOf(UbSettings())
        private set

    /**
     * 工具启停的**可观察版本号**。
     *
     * ★ 为什么需要它：工具启停按 id 存在 store 里，本身不是 Compose 状态。
     *   若界面只调 `isToolDisabled()`（普通函数调用），**读的时候不会被记录为依赖**，
     *   于是在设置页关掉工具后，首页不会重组、工具仍然显示 ——
     *   表现为"开关点了没反应"。
     *
     *   读这个版本号的界面会在启停时自动重组；写入时递增一次即可。
     */
    var toolsRevision: Int by mutableStateOf(0)
        private set

    /** 由 Application 启动时注入；重复调用是幂等的 */
    fun init(context: Context) {
        if (store == null) {
            synchronized(this) {
                if (store == null) {
                    val s = PrefsSettingsStore(context.applicationContext)
                    store = s
                    // 首次读取：把持久化的值加载进状态
                    settings = s.load()
                }
            }
        }
    }

    /** 供测试替换 */
    fun installForTest(s: SettingsStore) {
        store = s
        settings = s.load()
    }

    // ---- 修改入口：改状态 + 立即落盘 ----

    fun update(transform: (UbSettings) -> UbSettings) {
        val next = transform(settings)
        settings = next
        store?.save(next)
    }

    fun isToolDisabled(toolId: String): Boolean = store?.isToolDisabled(toolId) ?: false

    fun setToolDisabled(toolId: String, disabled: Boolean) {
        store?.setToolDisabled(toolId, disabled)
        // 递增版本号 → 读它的界面（首页工具列表、设置页开关）自动重组
        toolsRevision++
    }
}
