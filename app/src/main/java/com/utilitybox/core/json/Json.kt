package com.utilitybox.core.json

/**
 * 极小的 JSON 解析器（**零依赖、可单元测试**）。
 *
 * ★ 为什么不用 `org.json`：
 *   它在 Android 运行时可用，但在**单元测试里是空壳 stub** —— 方法一调用就抛
 *   `RuntimeException: Method ... not mocked`。这会导致所有涉及 JSON 的逻辑
 *   （解析正确性、错误映射、"0 不是价格"等红线）**无法在无设备环境下验证**。
 *
 *   备选方案是给测试加一个第三方 `org.json` 实现依赖，但那会引入"测试用的 JSON 实现
 *   与运行时用的 org.json 是两套代码"的隐患 —— 测过的和跑的不是同一个东西。
 *
 *   自写一个最小实现则同时满足：零生产依赖、测试与运行时**同一份代码**、完全可控。
 *
 * 支持 JSON 的全部必需子集：对象、数组、字符串（含转义与 \uXXXX）、数字、
 * true/false/null。不支持流式与注释（接口也不会返回这些）。
 */
sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val value: Double) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json

    // ---- 取值辅助：缺失/类型不符一律返回 null，绝不用默认值掩盖缺失 ----

    fun obj(key: String): Obj? = (this as? Obj)?.fields?.get(key) as? Obj

    fun arr(key: String): Arr? = (this as? Obj)?.fields?.get(key) as? Arr

    fun str(key: String): String? = (this as? Obj)?.fields?.get(key)?.let {
        when (it) {
            is Str -> it.value
            // 数字型 ID 在 JSON 里常见（如 mapID:1），允许转字符串
            is Num -> formatNumber(it.value)
            else -> null
        }
    }

    fun int(key: String): Int? = (this as? Obj)?.fields?.get(key)?.let {
        when (it) {
            is Num -> it.value.toInt()
            is Str -> it.value.trim().toIntOrNull()
            else -> null
        }
    }

    /** 读取价格类字段：缺失、非数字、**以及 0 与负数**一律返回 null（=无数据） */
    fun positiveLong(key: String): Long? = (this as? Obj)?.fields?.get(key)?.let {
        val v: Long = when (it) {
            is Num -> it.value.toLong()
            is Str -> it.value.trim().toLongOrNull() ?: return null
            else -> return null
        }
        if (v > 0) v else null
    }

    /** 读取数字，允许 0（例如历史样本数 count） */
    fun longOrZero(key: String): Long = (this as? Obj)?.fields?.get(key)?.let {
        when (it) {
            is Num -> it.value.toLong()
            is Str -> it.value.trim().toLongOrNull() ?: 0L
            else -> 0L
        }
    } ?: 0L

    fun bool(key: String, default: Boolean = false): Boolean =
        ((this as? Obj)?.fields?.get(key) as? Bool)?.value ?: default

    /**
     * 读取布尔字段，**缺失返回 null**（而不是 false）。
     *
     * ★ 与 [bool] 的区别很重要：有些接口字段缺失时"默认 false"是错的 ——
     *   例如 `hasValidPriceData` 缺失时，不能断言"没有有效价格"，
     *   那会凭空宣称"暂无成交数据"。用 null 让调用方自己决定如何兜底。
     */
    fun boolOrNull(key: String): Boolean? = ((this as? Obj)?.fields?.get(key) as? Bool)?.value

    /**
     * 读取浮点字段，缺失/非数字返回 null。
     *
     * 涨跌幅这类字段可能是整数也可能是小数，统一按 Double 读。
     */
    fun doubleOrNull(key: String): Double? = (this as? Obj)?.fields?.get(key)?.let {
        when (it) {
            is Num -> it.value
            is Str -> it.value.trim().toDoubleOrNull()
            else -> null
        }
    }

    /** 把数组节点转成 List（配合 [arr] 使用） */
    fun Arr.items(): List<Json> = this.items

    fun isNull(key: String): Boolean = (this as? Obj)?.fields?.get(key) is Null

    companion object {
        /** 解析失败返回 null（由调用方归为格式错误），不抛异常 */
        fun parse(text: String): Json? = try {
            Parser(text).run {
                val v = parseValue()
                skipWs()
                // 尾部还有非空白内容说明不是合法 JSON
                if (pos < text.length) null else v
            }
        } catch (e: Exception) {
            null
        }

        internal fun formatNumber(d: Double): String =
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }
}

private class Parser(private val s: String) {
    var pos = 0

    fun skipWs() {
        while (pos < s.length && s[pos].isWhitespace()) pos++
    }

    private fun peek(): Char {
        if (pos >= s.length) throw IllegalStateException("意外结束")
        return s[pos]
    }

    private fun expect(c: Char) {
        if (peek() != c) throw IllegalStateException("期望 '$c' 但得到 '${peek()}' at $pos")
        pos++
    }

    fun parseValue(): Json {
        skipWs()
        return when (val c = peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> Json.Str(parseString())
            't' -> { expectWord("true"); Json.Bool(true) }
            'f' -> { expectWord("false"); Json.Bool(false) }
            'n' -> { expectWord("null"); Json.Null }
            else -> if (c == '-' || c.isDigit()) parseNumber() else
                throw IllegalStateException("非法字符 '$c' at $pos")
        }
    }

    private fun expectWord(w: String) {
        if (!s.startsWith(w, pos)) throw IllegalStateException("期望 $w at $pos")
        pos += w.length
    }

    private fun parseObject(): Json.Obj {
        expect('{')
        val map = LinkedHashMap<String, Json>()
        skipWs()
        if (peek() == '}') { pos++; return Json.Obj(map) }
        while (true) {
            skipWs()
            val key = parseString()
            skipWs()
            expect(':')
            map[key] = parseValue()
            skipWs()
            when (peek()) {
                ',' -> pos++
                '}' -> { pos++; return Json.Obj(map) }
                else -> throw IllegalStateException("对象内期望 ',' 或 '}' at $pos")
            }
        }
    }

    private fun parseArray(): Json.Arr {
        expect('[')
        val list = ArrayList<Json>()
        skipWs()
        if (peek() == ']') { pos++; return Json.Arr(list) }
        while (true) {
            list.add(parseValue())
            skipWs()
            when (peek()) {
                ',' -> pos++
                ']' -> { pos++; return Json.Arr(list) }
                else -> throw IllegalStateException("数组内期望 ',' 或 ']' at $pos")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            val c = peek()
            when (c) {
                '"' -> { pos++; return sb.toString() }
                '\\' -> {
                    pos++
                    when (val e = peek()) {
                        '"' -> { sb.append('"'); pos++ }
                        '\\' -> { sb.append('\\'); pos++ }
                        '/' -> { sb.append('/'); pos++ }
                        'b' -> { sb.append('\b'); pos++ }
                        'f' -> { sb.append('\u000C'); pos++ }
                        'n' -> { sb.append('\n'); pos++ }
                        'r' -> { sb.append('\r'); pos++ }
                        't' -> { sb.append('\t'); pos++ }
                        'u' -> {
                            pos++
                            val hex = s.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw IllegalStateException("非法转义 \\$e at $pos")
                    }
                }
                else -> { sb.append(c); pos++ }
            }
        }
    }

    private fun parseNumber(): Json.Num {
        val start = pos
        if (peek() == '-') pos++
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' ||
                s[pos] == 'e' || s[pos] == 'E' || s[pos] == '+' || s[pos] == '-')
        ) pos++
        val raw = s.substring(start, pos)
        return Json.Num(raw.toDoubleOrNull() ?: throw IllegalStateException("非法数字 '$raw'"))
    }
}
