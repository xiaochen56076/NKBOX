package com.utilitybox.core.secret

/**
 * API Key 的存取契约。
 *
 * ★ 为什么需要这个抽象（P06 用户裁决）：
 *   用户要求「数据方面的 API 就采用设置里面可以输入那种」——
 *   即 Key 由用户在**设置页输入**，而不是构建期注入。
 *   这样任何人拿到 APK 都能自己填 Key 使用，不必重新构建。
 *
 * ★ 与红线的衔接：
 *   红线原话是「API Key 不得写入任何文件」——指的是**不得进入版本库与交付产物**
 *   （源码、文档、日志、APK 内置值）。用户输入后的 Key 属于**本机用户数据**，
 *   存在应用私有目录里是行业常规做法（等同于登录态）。
 *   因此：
 *     - ✅ 允许：存进应用私有存储（其他应用读不到）
 *     - ❌ 禁止：写进源码/文档/日志、打进 APK、提交到版本库
 *
 * ★ 存储实现优先用 Android Keystore 加密；不可用时**降级为明文并如实告知用户**
 *   （不静默降级 —— 用户有权知道安全等级）。
 */
interface SecretStore {
    fun getApiKey(): String
    fun setApiKey(value: String)

    /** 是否以加密方式保存。UI 需要如实展示，不能假称"已加密"。 */
    val isEncrypted: Boolean

    /**
     * **通用**加密槽位（P11 补 39）。
     *
     * ★ 为什么需要它：API Key 之外还有别的凭证 —— 目前是 **WebDAV 的账号密码**。
     *   给每种凭证各开一个接口方法（`getWebDavPassword()`…）会让接口无限膨胀，
     *   而它们要的其实是同一件事：**一个能安全落盘的字符串槽位**。
     *
     * ★ 安全等级与 [getApiKey] 完全一致（同一个 Keystore 密钥、同一套 AES/GCM）。
     *   降级行为也一样：Keystore 不可用时**明文存储并由 [isEncrypted] 如实告知**。
     */
    fun getSecret(key: String): String = ""

    /** 写入 [getSecret] 的槽位。空值表示删除。 */
    fun setSecret(key: String, value: String) = Unit
}

/** 内存实现：供单元测试与预览使用，不落盘 */
class InMemorySecretStore(initial: String = "") : SecretStore {
    private var value = initial
    private val extra = HashMap<String, String>()

    override fun getApiKey(): String = value
    override fun setApiKey(value: String) {
        this.value = value
    }

    override fun getSecret(key: String): String = extra[key].orEmpty()
    override fun setSecret(key: String, value: String) {
        if (value.isBlank()) extra.remove(key) else extra[key] = value
    }

    override val isEncrypted: Boolean get() = false
}
