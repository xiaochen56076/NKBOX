package com.utilitybox.core.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.utilitybox.domain.model.AccountProfile

/**
 * 账号状态的 **Compose 可观察** 外壳。
 *
 * ★ 为什么不直接在 Composable 里读 `AccountStore.load()`：
 *   那是"把状态藏在普通 getter 后面"，本项目的 P09（工具启停）与
 *   P11（返回键）已经因为同一个原因出过两次 bug ——
 *   **Compose 只把读过的 Compose 状态记为依赖**，普通 getter 不触发重组，
 *   表现为"操作了但界面没变"。
 *
 *   所以这里把 profile 提升为 `mutableStateOf`，所有写操作都经由它。
 */
object AccountState {

    var profile: AccountProfile by mutableStateOf(AccountProfile.LoggedOut)
        private set

    private var store: AccountStore? = null

    fun init(store: AccountStore) {
        this.store = store
        profile = store.load()
    }

    /** 测试注入 */
    fun installForTest(store: AccountStore) = init(store)

    fun loginLocal(nickname: String) {
        store?.loginLocal(nickname)
        refresh()
    }

    fun loginWithQq(): QqLoginResult {
        val result = store?.loginWithQq() ?: QqLoginResult.SdkNotIntegrated
        if (result is QqLoginResult.Success) refresh()
        return result
    }

    fun logout() {
        store?.logout()
        refresh()
    }

    private fun refresh() {
        profile = store?.load() ?: AccountProfile.LoggedOut
    }
}
