package com.utilitybox.core.network

import com.utilitybox.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 响应信封解析测试。
 *
 * 用例基于 P01 用真实请求实测到的响应形态（见 DATA_SOURCES.md 第 0 节），
 * 不是凭空假设的结构。
 */
class ApiEnvelopeTest {

    private fun errorOf(body: String): AppError {
        val r = ApiEnvelope.parse(body)
        assertTrue("期望失败，实际: $r", r is ApiEnvelope.EnvelopeResult.Err)
        return (r as ApiEnvelope.EnvelopeResult.Err).error
    }

    private fun dataOf(body: String) =
        (ApiEnvelope.parse(body) as ApiEnvelope.EnvelopeResult.Ok).data

    @Test
    fun `成功信封返回 data 对象`() {
        val data = dataOf("""{"code":0,"message":"成功","data":{"list":[{"mapID":1}]}}""")
        assertEquals(1, data.arr("list")!!.items.size)
    }

    @Test
    fun `code 非 0 归为业务错误并保留原文`() {
        val e = errorOf("""{"code":402,"message":"余额不足，本次请求需要 1 积分"}""")
        assertTrue(e is AppError.Business)
        assertEquals(402, (e as AppError.Business).code)
        assertTrue("必须保留数据源原文以便用户理解", e.message.contains("积分"))
    }

    @Test
    fun `401 与 403 归为鉴权失败`() {
        for (code in listOf(401, 403)) {
            assertEquals(AppError.Unauthorized, errorOf("""{"code":$code,"message":"x"}"""))
        }
    }

    @Test
    fun `非 JSON 响应归为格式错误而不是未知错误`() {
        // 真实场景：网关返回 HTML 错误页
        assertEquals(AppError.Malformed, errorOf("<html>502 Bad Gateway</html>"))
    }

    @Test
    fun `空响应体归为格式错误`() {
        assertEquals(AppError.Malformed, errorOf(""))
    }

    @Test
    fun `缺少 code 字段视为不认识的信封`() {
        assertEquals(AppError.Malformed, errorOf("""{"message":"ok","data":{}}"""))
    }

    @Test
    fun `code 为 0 但缺少 data 视为格式错误`() {
        assertEquals(AppError.Malformed, errorOf("""{"code":0,"message":"成功"}"""))
    }

    @Test
    fun `兼容 msg 字段名`() {
        val e = errorOf("""{"code":500,"msg":"服务器忙"}""") as AppError.Business
        assertEquals("服务器忙", e.message)
    }

    @Test
    fun `业务错误没有 message 时不填空字符串以外的猜测`() {
        val e = errorOf("""{"code":999}""") as AppError.Business
        assertEquals(999, e.code)
        assertEquals("", e.message)
    }
}
