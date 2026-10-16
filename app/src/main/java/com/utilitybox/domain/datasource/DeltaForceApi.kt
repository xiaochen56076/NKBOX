package com.utilitybox.domain.datasource

import com.utilitybox.domain.model.DataSourceRef
import com.utilitybox.domain.model.SourceState

/**
 * 数据源配置。
 *
 * ★ 依据设计稿「数据源可整体替换」：`baseUrl` 与鉴权头**全配置化**，
 *   不写死在业务代码里。社区服务随时可能停服（见 docs/data-sources.md §2）。
 *
 * ★ 已实测确认（P01）：
 *   - 端点：`GET /api/v1/df/tools/dailykeyword`
 *   - 鉴权：请求头 `X-API-Key`（个人数据类另需 `X-Framework-Token`，本期不用）
 *   - 响应信封：`{"code":0,"message":"成功","data":{...}}`
 */
object DeltaForceApi {

    /** 服务根地址。由 BuildConfig 注入，便于换源而无需改代码。 */
    val baseUrl: String
        get() = com.utilitybox.BuildConfig.UBOX_BASE_URL

    /**
     * API Key。
     *
     * ★ 优先级（P06 用户裁决后）：用户在设置页输入的值 > 构建期注入值 > 空串。
     *   因此这里委托给 [ApiKeyProvider]，而**不是**直接读 BuildConfig ——
     *   否则用户在设置里填完 Key 不会生效。
     *
     * ★ 红线：Key 不进入版本库与交付产物。
     *   构建期注入走环境变量 / local.properties（已被 .gitignore 排除）；
     *   用户输入的值存在应用私有存储（Android Keystore 加密）。
     */
    val apiKey: String
        get() = com.utilitybox.core.secret.ApiKeyProvider.current()

    /** 是否已配置 Key。未配置时 UI 应如实提示，而不是报"请求失败"。 */
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** 鉴权头。每次调用重新求值，保证设置页改完立即生效。 */
    fun authHeaders(): Map<String, String> {
        val k = apiKey
        // 未配置时不发送该头（避免发送空值造成难以解释的 401）
        return if (k.isNotBlank()) mapOf("X-API-Key" to k) else emptyMap()
    }

    // ---- 端点常量（集中管理，避免散落在各处） ----
    const val PATH_DAILY_KEYWORD = "/api/v1/df/tools/dailykeyword"
    const val PATH_OBJECT_SEARCH = "/api/v1/df/object/search"
    const val PATH_PRICE_LATEST = "/api/v1/df/object/price/ams/latest"
    const val PATH_PRICE_HISTORY = "/api/v1/df/object/price/ams/history/v1"

    /**
     * 弹药列表（P01 实测：113 项，**含 `hasValidPriceData:false` 真实样本**）。
     *
     * ★ 参数名是 `days`（与价格端点的 `id` 不同）—— 照抄实测记录。
     */
    const val PATH_OBJECT_AMMO = "/api/v1/df/object/ammo"

    /** 数据源元信息（用于「来源」展示与数据源状态页） */
    val ref = DataSourceRef(
        id = "deltaforce.community.v1",
        displayName = "社区接口",
        verified = SourceState.VERIFIED,
    )
}
