package com.utilitybox.game.deltaforce.dailycode

import android.content.Context
import com.utilitybox.core.cache.LocalCache
import com.utilitybox.core.cache.PrefsLocalCache
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpConfig
import com.utilitybox.core.network.UrlConnectionHttpClient
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.game.deltaforce.dailycode.data.RemoteDailyCodeSource

/**
 * 今日密码的依赖装配。
 *
 * ★ 为什么要有这个对象：让 [DailyCodeRepository] 的构造依赖（网络、缓存）
 *   在**一处**决定，页面只调 `repository(context)`。
 *   测试则直接 `new DailyCodeRepository(假源, 假缓存)`，完全不碰这里。
 *
 * ★ 为什么用 `applicationContext`：避免持有 Activity 造成泄漏。
 *
 * ★ 为什么 HttpClient 也做成单例：`HttpURLConnection` 每次新建没有连接池收益，
 *   而保留一个实例可复用超时配置，且不需要引入 OkHttp。
 */
object DailyCodeGraph {

    @Volatile
    private var cacheSingleton: LocalCache? = null

    @Volatile
    private var httpSingleton: HttpClient? = null

    private fun cache(context: Context): LocalCache =
        cacheSingleton ?: synchronized(this) {
            cacheSingleton ?: PrefsLocalCache(context.applicationContext).also {
                cacheSingleton = it
            }
        }

    private fun http(): HttpClient =
        httpSingleton ?: synchronized(this) {
            httpSingleton ?: UrlConnectionHttpClient(
                HttpConfig(
                    baseUrl = DeltaForceApi.baseUrl,
                    // ★ 这里**不**放鉴权头：Key 由 RemoteDailyCodeSource 每次请求时求值，
                    //   这样用户在设置页填完 Key 后立即生效，不必重启应用。
                    defaultHeaders = emptyMap(),
                ),
            ).also { httpSingleton = it }
        }

    fun repository(context: Context): DailyCodeRepository = DailyCodeRepository(
        remote = RemoteDailyCodeSource(
            http = http(),
            // 传函数而非值：每次 fetch 都重新读取，保证设置页改完立刻生效
            isKeyConfigured = { DeltaForceApi.isConfigured },
            authHeaders = { DeltaForceApi.authHeaders() },
        ),
        cache = cache(context),
    )

    /** 供测试/预览注入替身（不经过网络与磁盘） */
    fun repositoryFor(remote: RemoteDailyCodeSource, cache: LocalCache): DailyCodeRepository =
        DailyCodeRepository(remote = remote, cache = cache)
}
