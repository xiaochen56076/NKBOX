package com.utilitybox.core.account

import android.content.Context
import com.utilitybox.domain.model.AccountProfile

/**
 * 账号状态（P11 新增）。
 *
 * ## ★ 关于 QQ 登录：这里**没有**伪造它
 *
 * 用户要求「优先显示 QQ 登录，登录后即可显示一些关于 QQ 的信息」。
 *
 * 但 QQ 登录需要接入**腾讯开放平台 SDK**，而本项目自 P00 起维持
 * **零新增依赖**（除用户裁决引入的 Haze）。因此：
 *
 * - 登录界面**按 QQ 优先的位置和样式做好**；
 * - QQ 按钮明确标注「待接入」，点击后如实说明需要 SDK，
 *   **而不是假装登录成功**（那是红线：不得用假交互冒充已完成的功能）；
 * - 同时提供**本地昵称**，让"登录后"的界面状态现在就能看到、能验证。
 *
 * 将来接入真 QQ 时，只需要实现 [AccountStore.loginWithQq]，
 * UI 与状态流**一行都不用改** —— 这是把两种登录方式抽象成同一契约的目的。
 *
 * ## 不做的事
 *
 * - 不索取 QQ 密码 / 游戏账号密码（PROJECT_RULES 红线）
 * - 不把任何凭据写入日志
 * - 不上传任何东西：本期没有后端
 */
interface AccountStore {

    fun load(): AccountProfile

    /** 本地昵称登录（无后端，纯本地） */
    fun loginLocal(nickname: String)

    /**
     * QQ 登录。
     *
     * ★ 当前实现**如实返回失败**并说明原因，绝不返回一个假的成功。
     *   接入 SDK 后在这里替换实现即可，调用方无需改动。
     */
    fun loginWithQq(): QqLoginResult

    fun logout()
}

/** QQ 登录的结果。用密封类而不是 Boolean，好让界面能给出**具体**原因。 */
sealed interface QqLoginResult {
    data class Success(val profile: AccountProfile) : QqLoginResult

    /** 尚未接入 SDK —— 这是当前的真实状态，必须如实告诉用户 */
    data object SdkNotIntegrated : QqLoginResult

    data class Failed(val message: String) : QqLoginResult
}

/** 基于 SharedPreferences 的实现（无后端，纯本地） */
class PrefsAccountStore(context: Context) : AccountStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): AccountProfile {
        val nickname = prefs.getString(KEY_NICKNAME, null)
        val provider = prefs.getString(KEY_PROVIDER, null)
        return if (nickname.isNullOrBlank()) {
            AccountProfile.LoggedOut
        } else {
            AccountProfile(
                isLoggedIn = true,
                nickname = nickname,
                provider = when (provider) {
                    PROVIDER_QQ -> AccountProfile.Provider.QQ
                    else -> AccountProfile.Provider.LOCAL
                },
                // 本地昵称没有头像；QQ 接入后才会填充
                avatarUrl = prefs.getString(KEY_AVATAR, null),
            )
        }
    }

    override fun loginLocal(nickname: String) {
        prefs.edit()
            .putString(KEY_NICKNAME, nickname.trim())
            .putString(KEY_PROVIDER, PROVIDER_LOCAL)
            .remove(KEY_AVATAR)
            .apply()
    }

    override fun loginWithQq(): QqLoginResult = QqLoginResult.SdkNotIntegrated

    override fun logout() {
        // 只清本地登录态。★ 绝不动 API Key 等其它设置 ——
        // 「退出登录」不应该顺手把用户的 API Key 也删掉。
        prefs.edit()
            .remove(KEY_NICKNAME)
            .remove(KEY_PROVIDER)
            .remove(KEY_AVATAR)
            .apply()
    }

    private companion object {
        const val PREFS = "utilitybox_account"
        const val KEY_NICKNAME = "nickname"
        const val KEY_PROVIDER = "provider"
        const val KEY_AVATAR = "avatar"
        const val PROVIDER_LOCAL = "local"
        const val PROVIDER_QQ = "qq"
    }
}

/** 测试用内存实现 */
class InMemoryAccountStore(initial: AccountProfile = AccountProfile.LoggedOut) : AccountStore {
    private var profile = initial

    override fun load(): AccountProfile = profile

    override fun loginLocal(nickname: String) {
        profile = AccountProfile(
            isLoggedIn = true,
            nickname = nickname,
            provider = AccountProfile.Provider.LOCAL,
            // 本地登录没有头像；显式写 null 以免将来漏字段
            avatarUrl = null,
        )
    }

    override fun loginWithQq(): QqLoginResult = QqLoginResult.SdkNotIntegrated

    override fun logout() {
        profile = AccountProfile.LoggedOut
    }
}
