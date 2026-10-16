package com.utilitybox.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自写 JSON 解析器测试。
 *
 * ★ 这个解析器是**测试与运行时共用的同一份代码**，
 *   所以它自身的正确性必须被验证 —— 否则所有基于它的解析测试都失去了意义。
 */
class JsonTest {

    @Test
    fun `解析对象与取值`() {
        val j = Json.parse("""{"a":1,"b":"x","c":true,"d":null}""")!!
        assertEquals(1, j.int("a"))
        assertEquals("x", j.str("b"))
        assertTrue(j.bool("c"))
        assertTrue(j.isNull("d"))
    }

    @Test
    fun `解析嵌套对象与数组`() {
        val j = Json.parse("""{"data":{"list":[{"mapName":"零号大坝","secret":"0144"}]}}""")!!
        val list = j.obj("data")!!.arr("list")!!
        assertEquals(1, list.items.size)
        assertEquals("0144", (list.items[0] as Json.Obj).str("secret"))
    }

    @Test
    fun `缺失字段返回 null 而不是抛异常`() {
        val j = Json.parse("""{"a":1}""")!!
        assertNull(j.int("missing"))
        assertNull(j.str("missing"))
        assertNull(j.obj("missing"))
        assertNull(j.arr("missing"))
    }

    @Test
    fun `类型不符返回 null`() {
        val j = Json.parse("""{"a":"not-a-number","b":123}""")!!
        assertNull("字符串不是数字", j.int("a"))
        assertNull("数字不是字符串的字面意义", j.obj("b"))
    }

    @Test
    fun `数字型字段可读成字符串（接口里 ID 常是数字）`() {
        val j = Json.parse("""{"objectID":13020000524,"mapID":1}""")!!
        assertEquals("13020000524", j.str("objectID"))
        assertEquals("1", j.str("mapID"))
    }

    @Test
    fun `positiveLong 把 0 与负数视为无数据 —— 红线`() {
        val j = Json.parse("""{"a":0,"b":-1,"c":100,"d":"0","e":"50"}""")!!
        assertNull("0 表示无数据", j.positiveLong("a"))
        assertNull("负数表示无数据", j.positiveLong("b"))
        assertEquals(100L, j.positiveLong("c"))
        assertNull("字符串 0 同样表示无数据", j.positiveLong("d"))
        assertEquals(50L, j.positiveLong("e"))
    }

    @Test
    fun `longOrZero 允许 0（用于样本数这类字段）`() {
        val j = Json.parse("""{"count":0,"n":371}""")!!
        assertEquals(0L, j.longOrZero("count"))
        assertEquals(371L, j.longOrZero("n"))
        assertEquals(0L, j.longOrZero("missing"))
    }

    @Test
    fun `字符串转义被正确解码`() {
        val j = Json.parse("""{"a":"line\nbreak","b":"quote\"inside","c":"tab\there"}""")!!
        assertEquals("line\nbreak", j.str("a"))
        assertEquals("quote\"inside", j.str("b"))
        assertEquals("tab\there", j.str("c"))
    }

    @Test
    fun `unicode 转义被正确解码`() {
        val j = Json.parse("""{"a":"\u4e2d\u6587"}""")!!
        assertEquals("中文", j.str("a"))
    }

    @Test
    fun `中文原样保留`() {
        val j = Json.parse("""{"mapName":"零号大坝","secret":"0144"}""")!!
        assertEquals("零号大坝", j.str("mapName"))
    }

    @Test
    fun `非法 JSON 返回 null 而不抛异常`() {
        for (bad in listOf(
            "",
            "{",
            "{\"a\":}",
            "not json at all",
            "<html>502</html>",
            """{"a":1} trailing""",
            """{'a':1}""",      // 单引号不是合法 JSON
        )) {
            assertNull("应解析失败: $bad", Json.parse(bad))
        }
    }

    @Test
    fun `数组顶层也可解析`() {
        assertEquals(3, (Json.parse("[1,2,3]") as Json.Arr).items.size)
    }

    @Test
    fun `空对象与空数组`() {
        assertEquals(0, (Json.parse("{}") as Json.Obj).fields.size)
        assertEquals(0, (Json.parse("[]") as Json.Arr).items.size)
    }

    @Test
    fun `小数与科学计数法`() {
        assertEquals(1.5, (Json.parse("1.5") as Json.Num).value, 0.0001)
        assertEquals(1000.0, (Json.parse("1e3") as Json.Num).value, 0.0001)
    }
}
