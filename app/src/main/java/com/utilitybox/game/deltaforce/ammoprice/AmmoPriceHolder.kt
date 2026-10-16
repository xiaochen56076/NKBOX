package com.utilitybox.game.deltaforce.ammoprice

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.utilitybox.core.cache.LocalCache
import com.utilitybox.core.cache.PrefsLocalCache
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpConfig
import com.utilitybox.core.network.UrlConnectionHttpClient
import com.utilitybox.domain.datasource.DataResult
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.AmmoPrice
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 弹药物价的状态持有者。
 *
 * UI 只读 [state]，不关心网络与缓存细节。
 */
class AmmoPriceHolder(
    private val repo: AmmoPriceRepository,
    private val scope: CoroutineScope,
) {
    var state: UiState<List<AmmoPrice>> by mutableStateOf(UiState.Loading)
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (isLoading) return          // 防重入：连点"刷新"不该打多个请求
        isLoading = true
        scope.launch {
            try {
                when (val r = repo.load()) {
                    is DataResult.Ok ->
                        state = if (r.value.isEmpty()) UiState.Empty else UiState.Success(r.value)
                    is DataResult.Err -> state = UiState.Failed(r.error)
                    is DataResult.Unavailable -> state = UiState.Failed(AppError.SourceUnavailable)
                }
            } finally {
                isLoading = false
            }
        }
    }
}

/** 依赖装配：网络与缓存单例；Key 在**请求时**读取（设置页改完立即生效） */
object AmmoPriceGraph {

    @Volatile
    private var cacheSingleton: LocalCache? = null

    @Volatile
    private var httpSingleton: HttpClient? = null

    private fun cache(context: Context): LocalCache =
        cacheSingleton ?: synchronized(this) {
            cacheSingleton ?: PrefsLocalCache(context.applicationContext).also { cacheSingleton = it }
        }

    private fun http(): HttpClient =
        httpSingleton ?: synchronized(this) {
            httpSingleton ?: UrlConnectionHttpClient(
                HttpConfig(baseUrl = DeltaForceApi.baseUrl, defaultHeaders = emptyMap()),
            ).also { httpSingleton = it }
        }

    fun repository(context: Context): AmmoPriceRepository = AmmoPriceRepository(
        http = http(),
        cache = cache(context),
        isKeyConfigured = { DeltaForceApi.isConfigured },
        authHeaders = { DeltaForceApi.authHeaders() },
    )
}

@Composable
fun rememberAmmoPriceHolder(): AmmoPriceHolder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember(context) { AmmoPriceHolder(AmmoPriceGraph.repository(context), scope) }

    // ★ 自动刷新：与今日密码同一套机制（观察全局 tick）。
    //   `tick > 0` 是为了跳过初值 0 —— Holder.init 已经加载过一次，
    //   不判断的话开局会连发两次请求（数据源是积分制的）。
    val tick = com.utilitybox.core.settings.AutoRefreshState.tick
    androidx.compose.runtime.LaunchedEffect(tick) {
        if (tick > 0L) holder.refresh()
    }
    return holder
}
