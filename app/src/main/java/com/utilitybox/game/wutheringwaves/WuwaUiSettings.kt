package com.utilitybox.game.wutheringwaves

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 鸣潮页的界面开关（P11 补 77）。
 *
 * ### 目前只有一项：是否显示「登录入口」
 *
 * ### 为什么需要这个开关
 *
 * 库街区登录被**极验人机验证**挡住：
 * `/user/getSmsCode` 要求带真实的 `geeTestData`，我们拿不到 → 只能发空串
 * → 服务端返回 `geeTest = true` → **短信根本不会发出**。
 *
 * 也就是说，这个登录表单**用户填了也不会成功**。
 * 在极验流程补齐之前，让用户能把它藏起来 ——
 * 比摆一个永远失败的表单诚实，也更不碍眼。
 *
 * ### 默认值
 *
 * 默认**显示**（保持原有行为，不改变老用户看到的界面）。
 * 关掉后鸣潮页不再出现登录区块，图鉴/唤取记录仍然可用
 * （它们本来就不需要登录）。
 *
 * ### 存在哪
 *
 * SharedPreferences（和搜索历史一样）。**不存任何凭据** ——
 * 这里只有一个布尔值。
 */
object WuwaUiSettings {
    private const val PREFS = "ubox_wuwa_ui"
    private const val KEY_SHOW_LOGIN = "show_login_entry"

    /** 是否显示登录入口。Compose 状态，改动立即生效。 */
    var showLoginEntry by mutableStateOf(true)
        private set

    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        showLoginEntry = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_LOGIN, true)
    }

    /**
     * 写入开关值。
     *
     * ★ 名字不能叫 `setShowLoginEntry` —— `var showLoginEntry` 自动生成的 setter
     *   与它 JVM 签名相同（`setShowLoginEntry(Z)V`），会直接编译失败。
     *   （实测踩过：`Platform declaration clash`）
     */
    fun updateShowLoginEntry(value: Boolean) {
        showLoginEntry = value
        appContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_SHOW_LOGIN, value)
            ?.apply()
    }
}
