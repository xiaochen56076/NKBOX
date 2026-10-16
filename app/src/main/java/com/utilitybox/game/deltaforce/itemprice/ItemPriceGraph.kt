package com.utilitybox.game.deltaforce.itemprice

import android.content.Context
import com.utilitybox.core.cache.LocalCache
import com.utilitybox.core.cache.PrefsLocalCache
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpConfig
import com.utilitybox.core.network.UrlConnectionHttpClient
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.game.deltaforce.itemprice.data.RemoteItemPriceSource

/**
 * 物品物价的依赖装配。
 *
 * 与今日密码同一模式：网络与缓存单例，Key 在**请求时**读取（保证设置页改完立即生效）。
 * 测试直接构造 `ItemPriceRepository(假源, 假缓存)`，完全不碰这里。
 */
object ItemPriceGraph {

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
                HttpConfig(baseUrl = DeltaForceApi.baseUrl, defaultHeaders = emptyMap()),
            ).also { httpSingleton = it }
        }

    fun repository(context: Context): ItemPriceRepository = ItemPriceRepository(
        remote = RemoteItemPriceSource(
            http = http(),
            isKeyConfigured = { DeltaForceApi.isConfigured },
            authHeaders = { DeltaForceApi.authHeaders() },
        ),
        cache = cache(context),
    )
}
