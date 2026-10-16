package com.utilitybox.core.sync

import com.utilitybox.core.secret.SecretStore

/**
 * WebDAV 配置的存取（P11 补 39）。
 *
 * ★ 密码走 [SecretStore.setSecret] —— 与 API Key **同一个 Keystore 密钥、同一套 AES/GCM**。
 *   地址和用户名不是敏感信息，也一并放加密槽位，省得两套存储逻辑不一致。
 *
 * ★ Keystore 不可用时会降级为明文，且 [SecretStore.isEncrypted] 会返回 false ——
 *   界面必须**如实显示**，不能假称"已加密"。
 */
class WebDavStore(private val secrets: SecretStore) {

    fun load(): WebDavConfig = WebDavConfig(
        serverUrl = secrets.getSecret(KEY_URL),
        username = secrets.getSecret(KEY_USER),
        password = secrets.getSecret(KEY_PASS),
        remotePath = secrets.getSecret(KEY_PATH).ifBlank { DEFAULT_PATH },
        // 默认开：用户配好 WebDAV 就是想要自动备份
        autoUpload = secrets.getSecret(KEY_AUTO).ifBlank { "1" } == "1",
    )

    fun save(config: WebDavConfig) {
        secrets.setSecret(KEY_URL, config.serverUrl.trim())
        secrets.setSecret(KEY_USER, config.username.trim())
        secrets.setSecret(KEY_PASS, config.password)
        secrets.setSecret(KEY_PATH, config.remotePath.trim().ifBlank { DEFAULT_PATH })
        secrets.setSecret(KEY_AUTO, if (config.autoUpload) "1" else "0")
    }

    /** 清空（退出/不再使用云端备份）*/
    fun clear() {
        listOf(KEY_URL, KEY_USER, KEY_PASS, KEY_PATH, KEY_AUTO).forEach { secrets.setSecret(it, "") }
    }

    /** 凭据是否已加密保存。界面如实展示 */
    val isEncrypted: Boolean get() = secrets.isEncrypted

    private companion object {
        const val KEY_URL = "webdav_url"
        const val KEY_USER = "webdav_user"
        const val KEY_PASS = "webdav_pass"
        const val KEY_PATH = "webdav_path"
        const val KEY_AUTO = "webdav_auto"
        // 用户在坚果云里建的文件夹叫 wawu（不是 NBox）——
        // 默认值跟着用户的实际结构走，省得他还要改一次
        const val DEFAULT_PATH = "wawu/wuwa_gacha.json"
    }
}
