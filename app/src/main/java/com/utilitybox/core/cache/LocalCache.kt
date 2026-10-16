package com.utilitybox.core.cache

/**
 * 本地缓存的读写契约。
 *
 * 抽象成接口的理由与网络层相同：业务只依赖契约，且单测可注入内存实现。
 *
 * ★ 缓存键必须带游戏命名空间（形如 `cache:<gameId>:<dataType>:<key>`），
 *   这是设计稿「多游戏数据隔离」要求的一部分（见 ARCHITECTURE.md 第 5.3 节）。
 */
interface LocalCache {
    fun read(key: String): CacheEntry?
    fun write(key: String, entry: CacheEntry)
    fun remove(key: String)
    fun clear()

    /** 全部缓存键（供统计与批量操作；实现应返回当前实际存在的键） */
    fun keys(): Set<String>

    /**
     * 当前缓存占用字节数（供设置页如实展示）。
     *
     * ★ 提供**默认实现**而不是强制实现：
     *   这两个统计方法可以从 [keys] + [read] 推导，不该逼每个实现（包括测试替身）
     *   都重复写一遍。"新增一个接口方法就编译不过一堆无关实现"是没必要的破坏。
     *   具体实现若能用更准的方式统计（如 PrefsLocalCache 直接量 key/value 尺寸），可覆写。
     */
    fun sizeBytes(): Long = keys().sumOf { k ->
        val e = read(k) ?: return@sumOf 0L
        (k.toByteArray(Charsets.UTF_8).size + e.payload.toByteArray(Charsets.UTF_8).size).toLong()
    }

    /** 缓存条目数（供设置页展示"清空后会怎样"） */
    fun entryCount(): Int = keys().size

    /** 缓存键构造：强制带游戏命名空间，避免不同游戏的数据串味 */
    fun key(gameId: String, dataType: String, id: String = ""): String =
        buildString {
            append("cache:")
            append(gameId)
            append(':')
            append(dataType)
            if (id.isNotEmpty()) {
                append(':')
                append(id)
            }
        }
}

/**
 * 缓存信封。
 *
 * ★ 为什么必须有 [schemaVersion]：解析结构一旦变更，旧缓存用新代码解析会得到
 *   似是而非的结果（字段错位、缺失当默认值）。**安全丢弃旧缓存，不做兼容猜测**
 *   —— 这也是红线「不得用默认值掩盖数据缺失」在缓存层的落地。
 *
 * ★ 为什么必须有 [fetchedAtMillis]：UI 需要区分"新鲜"与"陈旧"。
 *   今日密码尤其关键：过期数据绝不能以今日数据的样式呈现。
 */
data class CacheEntry(
    val schemaVersion: Int,
    val fetchedAtMillis: Long,
    val sourceId: String,
    /** mock 数据必须在缓存里也带上标记，否则重启后会"洗白"成真实数据 */
    val isMock: Boolean,
    val payload: String,
) {
    /**
     * 是否可作为当前 schema 使用。
     * 版本不匹配即视为不可用 —— 调用方应删掉它并重新请求。
     */
    fun isUsable(currentSchemaVersion: Int): Boolean = schemaVersion == currentSchemaVersion
}
