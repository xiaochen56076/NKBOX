package com.utilitybox.game.wutheringwaves.kuro

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 唤取记录**历史快照**存储（P11 补 97）。
 *
 * ### 为什么这组测试必要
 *
 * 快照靠**文件名**编码「时间_条数_账号_版本」。文件名一旦解析错，
 * 表现是「列表里少了几条」或「点进去是空的」—— 看起来像丢数据，
 * 但在真机上极难分辨是"文件没写"还是"解析失败"。
 *
 * 所以这里直接钉住：写进去的能列出来、能读回来、能删掉、能去重、有上限。
 */
class GachaHistoryStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("gacha-history-test").toFile()
        GachaHistoryStore.installForTest(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun rec(name: String, time: String, rarity: Int = 5) = GachaRecord(
        poolType = 1,
        name = name,
        rarity = rarity,
        resourceType = "0",
        count = 1,
        time = time,
    )

    private fun sample(n: Int) = (1..n).map { rec("角色$it", "2026-10-0${(it % 9) + 1} 12:00:00") }

    @Test
    fun `保存后能列出 并且条数与账号正确`() {
        val records = sample(6)
        val meta = GachaHistoryStore.save(records, "109430216")
        assertNotNull("保存应返回元信息", meta)
        assertEquals(6, meta!!.recordCount)
        assertEquals("109430216", meta.accountId)

        val list = GachaHistoryStore.list()
        assertEquals("应恰好有一份快照", 1, list.size)
        assertEquals(6, list[0].recordCount)
        assertEquals("109430216", list[0].accountId)
    }

    @Test
    fun `按 id 能读回完整记录`() {
        val records = sample(5)
        val meta = GachaHistoryStore.save(records, "123")!!
        val back = GachaHistoryStore.load(meta.id)

        assertEquals("读回的条数必须和存进去一致", records.size, back.size)
        // ★ 关键字段逐个核对：只对条数的话，字段错位也能通过
        assertEquals(records[1].name, back[1].name)
        assertEquals(records[1].time, back[1].time)
        assertEquals(records[1].rarity, back[1].rarity)
        assertEquals(records[1].poolType, back[1].poolType)
    }

    @Test
    fun `同账号同条数视为同一次查询 不会堆出两份`() {
        GachaHistoryStore.save(sample(4), "999")
        GachaHistoryStore.save(sample(4), "999")

        assertEquals(
            "重查一次不该在历史里出现两条一模一样的",
            1,
            GachaHistoryStore.list().size,
        )
    }

    @Test
    fun `不同账号或不同条数会各自存档`() {
        GachaHistoryStore.save(sample(4), "111")
        GachaHistoryStore.save(sample(4), "222")
        GachaHistoryStore.save(sample(7), "111")

        assertEquals("三份不同的快照都应保留", 3, GachaHistoryStore.list().size)
    }

    @Test
    fun `列表按时间倒序 最新的在最前`() {
        // 逐个保存并手动错开时间：同一毫秒内保存会导致时间相同，排序无法验证
        val a = GachaHistoryStore.save(sample(1), "1")!!
        Thread.sleep(5)
        val b = GachaHistoryStore.save(sample(2), "2")!!
        Thread.sleep(5)
        val c = GachaHistoryStore.save(sample(3), "3")!!

        val list = GachaHistoryStore.list()
        assertEquals(3, list.size)
        assertEquals("最新的应排第一", c.id, list[0].id)
        assertEquals("中间的排第二", b.id, list[1].id)
        assertEquals("最旧的排最后", a.id, list[2].id)
    }

    @Test
    fun `删除后不再出现在列表里`() {
        val meta = GachaHistoryStore.save(sample(3), "777")!!
        GachaHistoryStore.delete(meta)
        assertTrue("删除后列表应为空", GachaHistoryStore.list().isEmpty())
        assertTrue("删除后也读不到记录", GachaHistoryStore.load(meta.id).isEmpty())
    }

    @Test
    fun `空记录不存档`() {
        assertEquals("空列表没有存档价值", null, GachaHistoryStore.save(emptyList(), "123"))
        assertTrue(GachaHistoryStore.list().isEmpty())
    }

    @Test
    fun `损坏或格式不认识的文件被忽略 而不是当成有效快照`() {
        File(dir, "这不是快照.json").writeText("{}")
        File(dir, "123_v1.json").writeText("{}")            // 段数不对
        File(dir, "123_5_888_v99.json").writeText("{}")     // 版本不认识

        assertTrue(
            "无法解析的文件必须被忽略，否则列表里会出现点进去是空的条目",
            GachaHistoryStore.list().isEmpty(),
        )
    }

    @Test
    fun `非数字账号会被兜底 不会让文件名解析失败`() {
        val meta = GachaHistoryStore.save(sample(2), "abc-!@#")!!
        assertEquals("unknown", meta.accountId)
        assertEquals("兜底后的文件名仍应能列出来", 1, GachaHistoryStore.list().size)
    }
}
