package com.utilitybox.game.wutheringwaves.kuro

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 唤取记录合并的单元测试（P11 补 36）。
 *
 * ★ 为什么这个必须有：
 *   合并错了的表现是**用户的抽卡数翻倍或者记录丢失** ——
 *   界面上看不出任何异常，只是数字不对。而这恰恰是修
 *   `cardPoolType` 解析时**自己引出来的新 bug**：
 *
 *   · 旧缓存里解析失败的记录 → `poolType = 0`
 *   · 重新拉取的同一条记录   → `poolType = 1`
 *
 *   旧键是「poolType|name|time」，两者键不同 → 同一条存两份。
 */
class GachaMergeTest {

    private fun rec(
        poolType: Int,
        name: String,
        time: String,
        rarity: Int = 3,
    ) = GachaRecord(
        poolType = poolType,
        name = name,
        rarity = rarity,
        resourceType = "武器",
        count = 1,
        time = time,
    )

    @Test
    fun `同一条记录_旧的是0新的有池类型_只保留一条且用新的`() {
        // 这就是"修复后重新拉一次"的真实场景
        val old = listOf(rec(0, "远行者长刃·辟路", "2026-10-15 11:31:13"))
        val new = listOf(rec(1, "远行者长刃·辟路", "2026-10-15 11:31:13"))

        val r = mergeRecords(old, new)

        assertEquals("不能变成两条 —— 那会让抽卡数翻倍", 1, r.size)
        assertEquals("要保留带池类型的那条", 1, r[0].poolType)
    }

    @Test
    fun `顺序反过来也要保留有池类型的那条`() {
        // 新拉的先到、旧缓存后到（合并顺序不该影响结果）
        val r = mergeRecords(
            listOf(rec(1, "远行者长刃·辟路", "2026-10-15 11:31:13")),
            listOf(rec(0, "远行者长刃·辟路", "2026-10-15 11:31:13")),
        )

        assertEquals(1, r.size)
        assertEquals("不能因为新的这条是 0 就把已知的池类型丢掉", 1, r[0].poolType)
    }

    @Test
    fun `两边都没有池类型时也要去重`() {
        val r = mergeRecords(
            listOf(rec(0, "远行者长刃·辟路", "2026-10-15 11:31:13")),
            listOf(rec(0, "远行者长刃·辟路", "2026-10-15 11:31:13")),
        )
        assertEquals(1, r.size)
    }

    @Test
    fun `十连里三个同名同秒的物品必须保留三条`() {
        // ★★ 本文件最重要的一条（P11 补 37）。
        //
        //   上一版用 `name|time` 做键，这种记录会被合并成 1 条 ——
        //   实测代价：826 条被压成 787 条，**39 条记录凭空消失**。
        //   而界面上完全看不出异常，只是总数少了。
        //
        //   一次十连出多个同名物品是**常态**（三星武器池就那么几把）。
        val ten = listOf(
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
        )
        assertEquals("同秒同名是三次不同的抽卡，不能合并", 3, mergeRecords(emptyList(), ten).size)
    }

    @Test
    fun `十连重复项在重新抓取后不会翻倍`() {
        // 自愈验证：旧缓存里丢了第 2、3 条，重新抓取要能补回来，
        // 且**不能**变成 3 + 3 = 6 条。
        val full = listOf(
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
        )
        val broken = full.take(1) // 模拟被旧键吃剩下的那一条

        val r = mergeRecords(broken, full)

        assertEquals("补回丢失的，且不产生重复", 3, r.size)
    }

    @Test
    fun `旧缓存0与新数据1在十连重复项上仍能一一对应`() {
        // 旧缓存（poolType 全 0，且被吃掉一条） + 新抓取（poolType 1，完整）
        val old = listOf(rec(0, "远行者佩枪·洞察", "2026-09-30 18:40:07"))
        val new = listOf(
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
            rec(1, "远行者佩枪·洞察", "2026-09-30 18:40:07"),
        )

        val r = mergeRecords(old, new)

        assertEquals(2, r.size)
        assertEquals("两条都要带上池类型", listOf(1, 1), r.map { it.poolType })
    }

    @Test
    fun `同名但时间不同是两条_不能误合并`() {
        // ★ 反过来错就是**丢记录**：抽了两把同名武器只算一把
        val r = mergeRecords(
            listOf(rec(1, "远行者长刃·辟路", "2026-10-15 11:31:13")),
            listOf(rec(1, "远行者长刃·辟路", "2026-10-15 11:31:20")),
        )
        assertEquals("同一秒才可能是同一条，差 7 秒是两次抽卡", 2, r.size)
    }

    @Test
    fun `跨批次同秒同名视为同一次抽卡`() {
        // ★ 这条我改过两次，记录一下为什么最终是这个断言（P11 补 37）。
        //
        //   一开始断言 1（用 name|time 做键），后来改成断言 2，
        //   最后又改回 1 —— 因为**实测数据证明前提是错的**：
        //
        //   各卡池的返回是**互不重叠**的：
        //   池1=580、池2=116、池3=116、池4=4、池6=10，加起来正好 826 = 实测总记录数。
        //   所以同一条记录**不可能**同时出现在两个池的响应里。
        //
        //   于是"跨批次、同秒、同名"只可能是**同一次抽卡的两次抓取** ——
        //   必须合并成一条，否则用户每粘一次链接记录就翻一倍。
        //
        //   ★ 关键区别在"批次内"还是"批次间"：
        //     · 同一批里同秒同名 → 十连的重复物品 → **要分开**（见上面的十连测试）
        //     · 不同批次里同秒同名 → 同一次抽卡被拉了两次 → **要合并**
        //   序号只在各自批次内计数，正好实现这个区别。
        val r = mergeRecords(
            listOf(rec(1, "远行者长刃·辟路", "2026-10-15 11:31:13")),
            listOf(rec(3, "远行者长刃·辟路", "2026-10-15 11:31:13")),
        )
        assertEquals("跨批次的同秒同名是同一次抽卡", 1, r.size)
    }

    @Test
    fun `多次合并是幂等的`() {
        // 用户反复粘链接不该让记录膨胀
        val once = listOf(rec(1, "心", "2026-10-15 11:31:13", rarity = 5))
        var acc = mergeRecords(emptyList(), once)
        repeat(5) { acc = mergeRecords(acc, once) }
        assertEquals(1, acc.size)
    }

    @Test
    fun `不同名字各自保留`() {
        val r = mergeRecords(
            listOf(rec(1, "心", "2026-10-15 11:31:13", rarity = 5)),
            listOf(rec(1, "千咲", "2026-10-15 11:31:14", rarity = 5)),
        )
        assertEquals(2, r.size)
    }
}
