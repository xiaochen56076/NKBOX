package com.utilitybox.core.cache

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 基于 `SharedPreferences` 的缓存实现（**零新依赖**）。
 *
 * 选择理由：首期缓存内容简单（今日密码、价格、设置），
 * 且 [LocalCache] 已抽象，未来要换 DataStore / Room 只替换本类。
 *
 * ⚠️ 本类含 Android 依赖，**不参与单元测试**；
 *    缓存的新鲜度/版本判定逻辑放在 [CacheEntry] 与 [CachePolicy] 里，那部分可纯 JVM 测试。
 */
class PrefsLocalCache(context: Context) : LocalCache {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): CacheEntry? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val o = JSONObject(raw)
            CacheEntry(
                schemaVersion = o.getInt(FIELD_SCHEMA),
                fetchedAtMillis = o.getLong(FIELD_FETCHED_AT),
                sourceId = o.optString(FIELD_SOURCE, ""),
                isMock = o.optBoolean(FIELD_IS_MOCK, false),
                payload = o.getString(FIELD_PAYLOAD),
            )
        } catch (e: Exception) {
            // 缓存损坏 → 丢弃，而不是让整个应用崩在启动路径上
            Log.w(TAG, "缓存条目损坏，已丢弃: $key", e)
            prefs.edit().remove(key).apply()
            null
        }
    }

    override fun write(key: String, entry: CacheEntry) {
        val o = JSONObject().apply {
            put(FIELD_SCHEMA, entry.schemaVersion)
            put(FIELD_FETCHED_AT, entry.fetchedAtMillis)
            put(FIELD_SOURCE, entry.sourceId)
            put(FIELD_IS_MOCK, entry.isMock)
            put(FIELD_PAYLOAD, entry.payload)
        }
        prefs.edit().putString(key, o.toString()).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun keys(): Set<String> =
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.toSet()

    /**
     * 实际占用字节数。
     *
     * ★ 按**真实的键值尺寸**统计，而不是编一个好看的数：
     *   设置页要如实告诉用户"清空能释放多少"，编数字就是在骗人。
     *   统计的是 SharedPreferences 中 `cache:` 前缀条目的 key+value 字节长度。
     */
    override fun sizeBytes(): Long {
        var total = 0L
        prefs.all.forEach { (k, v) ->
            if (k.startsWith(KEY_PREFIX)) {
                total += k.toByteArray(Charsets.UTF_8).size.toLong()
                total += (v?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0).toLong()
            }
        }
        return total
    }

    override fun entryCount(): Int =
        prefs.all.keys.count { it.startsWith(KEY_PREFIX) }

    private companion object {
        const val PREFS_NAME = "utilitybox_cache"
        const val TAG = "UbCache"

        /** 与 [LocalCache.key] 的前缀保持一致 */
        const val KEY_PREFIX = "cache:"

        const val FIELD_SCHEMA = "v"
        const val FIELD_FETCHED_AT = "ts"
        const val FIELD_SOURCE = "src"
        const val FIELD_IS_MOCK = "mock"
        const val FIELD_PAYLOAD = "data"
    }
}
