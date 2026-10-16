package com.utilitybox.core.sync

import com.utilitybox.core.secret.InMemorySecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDAV 的纯逻辑测试（P11 补 39）。
 *
 * ★ 为什么这些必须有：
 *   网络部分出错会**报错**（看得见），但这三处出错是**静默的**：
 *   · URL 拼错 → 404 → 被当成"云端还没有备份" → 用户以为备份成功
 *   · Base64 编错 → 401 → 用户以为密码错，反复重填
 *   · 配置读回错 → 每次打开设置页都被重置
 */
class WebDavClientTest {

    // ---- URL 拼接 ----

    @Test
    fun `拼接去掉多余的斜杠`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/NBox/a.json",
            WebDavClient.joinUrl("https://dav.jianguoyun.com/dav/", "/NBox/a.json"),
        )
    }

    @Test
    fun `两边都没有斜杠也能拼对`() {
        assertEquals(
            "https://x.com/dav/NBox/a.json",
            WebDavClient.joinUrl("https://x.com/dav", "NBox/a.json"),
        )
    }

    @Test
    fun `地址末尾多个斜杠也只会加一个`() {
        assertEquals(
            "https://x.com/dav/NBox/a.json",
            WebDavClient.joinUrl("https://x.com/dav///", "NBox/a.json"),
        )
    }

    @Test
    fun `路径为空时只返回服务器地址`() {
        assertEquals("https://x.com/dav", WebDavClient.joinUrl("https://x.com/dav/", ""))
    }

    @Test
    fun `地址两边的空格会被去掉`() {
        // 用户从别处复制粘贴，很容易带空格 —— 不去掉就是 404
        assertEquals(
            "https://x.com/dav/a.json",
            WebDavClient.joinUrl("  https://x.com/dav  ", "  a.json  "),
        )
    }

    // ---- Base64 ----

    @Test
    fun `Base64 标准向量`() {
        // RFC 4648 的官方测试向量，不是我自己编的
        assertEquals("", WebDavClient.base64Encode("".toByteArray()))
        assertEquals("Zg==", WebDavClient.base64Encode("f".toByteArray()))
        assertEquals("Zm8=", WebDavClient.base64Encode("fo".toByteArray()))
        assertEquals("Zm9v", WebDavClient.base64Encode("foo".toByteArray()))
        assertEquals("Zm9vYg==", WebDavClient.base64Encode("foob".toByteArray()))
        assertEquals("Zm9vYmE=", WebDavClient.base64Encode("fooba".toByteArray()))
        assertEquals("Zm9vYmFy", WebDavClient.base64Encode("foobar".toByteArray()))
    }

    @Test
    fun `Base64 处理非 ASCII 字节`() {
        // 密码里出现中文/特殊字符时走的是 UTF-8 字节
        assertEquals("5L2g5aW9", WebDavClient.base64Encode("你好".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `Basic 认证头格式`() {
        // "user:pass" 的标准编码
        assertEquals("dXNlcjpwYXNz", WebDavClient.basicAuth("user", "pass"))
    }

    @Test
    fun `密码为空也要保留冒号`() {
        // "user:" —— 少一个冒号认证一定失败
        assertEquals("dXNlcjo=", WebDavClient.basicAuth("user", ""))
    }

    @Test
    fun `密码里带冒号不会被截断`() {
        // Basic 是「用户名:密码」整体编码，密码内的冒号是允许的
        assertEquals("dXNlcjphOmI=", WebDavClient.basicAuth("user", "a:b"))
    }

    // ---- 配置存取 ----

    @Test
    fun `配置存了能原样读回来`() {
        val store = WebDavStore(InMemorySecretStore())
        val cfg = WebDavConfig(
            serverUrl = "https://dav.jianguoyun.com/dav",
            username = "me@example.com",
            password = "p@ss word",
            remotePath = "NBox/wuwa.json",
            autoUpload = false,
        )
        store.save(cfg)
        assertEquals(cfg, store.load())
    }

    @Test
    fun `没配过时给出默认值而不是空`() {
        val cfg = WebDavStore(InMemorySecretStore()).load()
        assertTrue(cfg.serverUrl.isEmpty())
        assertEquals("默认路径要有值，否则 joinUrl 会拼出个目录地址", "wawu/wuwa_gacha.json", cfg.remotePath)
        assertTrue("默认开启自动上传", cfg.autoUpload)
    }

    @Test
    fun `路径留空会回落到默认值`() {
        val store = WebDavStore(InMemorySecretStore())
        store.save(WebDavConfig(serverUrl = "https://x.com/dav", username = "u", remotePath = "   "))
        assertEquals("wawu/wuwa_gacha.json", store.load().remotePath)
    }

    @Test
    fun `isConfigured 的判定`() {
        assertFalse(WebDavConfig().isConfigured)
        assertFalse(WebDavConfig(serverUrl = "https://x.com/dav").isConfigured)
        assertTrue(
            WebDavConfig(serverUrl = "https://x.com/dav", username = "u").isConfigured,
        )
    }

    @Test
    fun `clear 之后回到未配置状态`() {
        val store = WebDavStore(InMemorySecretStore())
        store.save(WebDavConfig(serverUrl = "https://x.com/dav", username = "u", password = "p"))
        store.clear()
        assertFalse(store.load().isConfigured)
        assertTrue(store.load().password.isEmpty())
    }

    @Test
    fun `内存实现不加密_界面据实展示`() {
        // ★ Keystore 不可用时接口会返回 false，界面必须如实显示"未加密"。
        //   静默降级是安全功能里最不该做的事。
        assertFalse(WebDavStore(InMemorySecretStore()).isEncrypted)
    }
}
