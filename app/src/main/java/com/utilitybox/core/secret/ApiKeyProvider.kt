package com.utilitybox.core.secret

import android.content.Context

/**
 * 运行时 API Key 提供器。
 *
 * ★ 优先级（P06 用户裁决后）：
 *   1. **用户在设置页输入的值**（存在应用私有存储，优先）
 *   2. 构建期注入值（`local.properties` / 环境变量 —— 便于开发与 CI）
 *   3. 空串 = 未配置
 *
 * ★ 为什么做成"请求时读取"而不是构造时快照：
 *   用户在设置页填完 Key 后应立即生效，不需要重启应用。
 *   若在构造数据源时把 Key 拷进去，填完必须重启才起作用 —— 那是很差的体验。
 *
 * ★ 为什么是 object（全局单例）：
 *   设置页与各数据源都要访问它，而它本身无状态（状态在 [SecretStore] 里）。
 *   为避免在每层透传 Context，用一个显式的 `init` 在 Application 启动时注入一次。
 *   这比引入 DI 框架轻得多，也符合本项目「依赖必须有充分理由」的规则。
 */
object ApiKeyProvider {

    @Volatile
    private var store: SecretStore? = null

    /** 构建期注入的兜底值（未在设置页填写时使用） */
    @Volatile
    private var buildTimeKey: String = ""

    fun init(context: Context, buildTimeKey: String) {
        if (store == null) {
            synchronized(this) {
                if (store == null) {
                    store = KeystoreSecretStore(context.applicationContext)
                }
            }
        }
        this.buildTimeKey = buildTimeKey
    }

    /** 供测试替换 */
    fun installForTest(store: SecretStore, buildTimeKey: String = "") {
        this.store = store
        this.buildTimeKey = buildTimeKey
    }

    /** 当前生效的 Key（每次调用都重新读取，保证设置页改完立即生效） */
    fun current(): String {
        val userValue = store?.getApiKey().orEmpty()
        if (userValue.isNotBlank()) return userValue
        return buildTimeKey
    }

    fun isConfigured(): Boolean = current().isNotBlank()

    /** 来源说明，供 UI 如实展示（用户需要知道当前用的是哪一个） */
    fun source(): KeySource = when {
        !store?.getApiKey().isNullOrBlank() -> KeySource.USER
        buildTimeKey.isNotBlank() -> KeySource.BUILD_TIME
        else -> KeySource.NONE
    }

    fun save(value: String) {
        store?.setApiKey(value.trim())
    }

    fun clear() {
        store?.setApiKey("")
    }

    /** 是否以加密方式保存（UI 必须如实展示，不能假称已加密） */
    fun isEncryptedAtRest(): Boolean = store?.isEncrypted ?: false

    /** 打码后的展示形式，例如 `sk-dfa4…97d`。**绝不返回完整 Key。** */
    fun masked(): String {
        val k = current()
        if (k.isBlank()) return ""
        return if (k.length <= 10) "•".repeat(k.length)
        else k.take(6) + "…" + k.takeLast(3)
    }

    enum class KeySource { USER, BUILD_TIME, NONE }

    /** 面向用户的来源说明（设置页/数据源页展示用） */
    fun KeySource.displayLabel(): String = when (this) {
        KeySource.USER -> "本机设置"
        KeySource.BUILD_TIME -> "构建时注入"
        KeySource.NONE -> "未配置"
    }
}
