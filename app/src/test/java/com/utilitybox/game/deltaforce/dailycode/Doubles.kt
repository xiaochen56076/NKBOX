package com.utilitybox.game.deltaforce.dailycode

import com.utilitybox.core.cache.CacheEntry
import com.utilitybox.core.cache.LocalCache
import com.utilitybox.core.network.HttpClient
import com.utilitybox.core.network.HttpResult
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCodeState

/** 内存缓存替身，供单元测试使用（不碰 SharedPreferences） */
class FakeCache : LocalCache {
    private val map = mutableMapOf<String, CacheEntry>()
    var writeCount = 0
        private set
    var removeCount = 0
        private set

    override fun read(key: String): CacheEntry? = map[key]
    override fun write(key: String, entry: CacheEntry) {
        map[key] = entry
        writeCount++
    }

    override fun remove(key: String) {
        map.remove(key)
        removeCount++
    }

    override fun clear() = map.clear()

    override fun keys(): Set<String> = map.keys.toSet()

    /** 直接塞入一条缓存，用于构造"上次成功、本次失败"的过期场景 */
    fun seed(key: String, entry: CacheEntry) {
        map[key] = entry
    }

    fun isEmpty(): Boolean = map.isEmpty()
}

/**
 * 可编程的 HTTP 替身。
 *
 * ★ 这就是把 [HttpClient] 抽象成接口的回报：整条"网络 → 信封 → 解析 → 缓存 → 四态"
 *   链路可以在**无网络、无设备**的情况下被完整验证。
 */
class FakeHttpClient(
    private val responder: () -> HttpResult,
) : HttpClient {
    var lastPath: String? = null
        private set
    var lastHeaders: Map<String, String> = emptyMap()
        private set
    var callCount = 0
        private set

    override suspend fun get(
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
    ): HttpResult {
        callCount++
        lastPath = path
        lastHeaders = headers
        return responder()
    }

    companion object {
        /** P01 实测到的真实响应体 */
        const val REAL_BODY = """{"code":0,"message":"成功","data":{"list":[
            {"mapID":1,"mapName":"零号大坝","secret":"0144"},
            {"mapID":2,"mapName":"长弓溪谷","secret":"8288"},
            {"mapID":3,"mapName":"巴克什","secret":"4400"},
            {"mapID":4,"mapName":"航天基地","secret":"0201"},
            {"mapID":5,"mapName":"潮汐监狱","secret":"2643"},
            {"mapID":6,"mapName":"AZ3","secret":"2520"}
        ]}}"""

        fun ok(body: String = REAL_BODY) = FakeHttpClient { HttpResult.Success(body) }
        fun failing(error: AppError) = FakeHttpClient { HttpResult.Failure(error) }
        fun business(code: Int, message: String) = FakeHttpClient {
            HttpResult.Success("""{"code":$code,"message":"$message"}""")
        }
    }
}
