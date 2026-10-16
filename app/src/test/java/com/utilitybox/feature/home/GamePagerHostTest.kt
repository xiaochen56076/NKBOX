package com.utilitybox.feature.home

import com.utilitybox.core.navigation.UbRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可滑动游戏标签的判定（P11 用户要求「左右滑动切换三角洲和鸣潮」）。
 *
 * ★ 为什么这个判定要单独测：
 *   它是"哪些路由进分页容器"的**唯一依据**。判定过宽会把二级页面也塞进 pager
 *   （横向手势与返回手势打架）；判定过窄则滑动失效。
 *   两种错误都只能在真机上"感觉怪"，不会有崩溃 —— 正是最该被测试钉住的那类。
 */
class GamePagerHostTest {

    @Test
    fun `三角洲判定为可滑动`() {
        assertTrue("三角洲必须可滑动", isGameTabRoute(UbRoutes.GAME_DELTA))
    }

    @Test
    fun `鸣潮也是可滑动的游戏标签`() {
        assertTrue("鸣潮必须可滑动", isGameTabRoute(UbRoutes.GAME_WUWA))
    }

    @Test
    fun `二级页面不可滑动 —— 否则横向手势会和返回冲突`() {
        val subPages = listOf(
            UbRoutes.SETTINGS,
            UbRoutes.SETTINGS_TOOLS,
            UbRoutes.SETTINGS_APPEARANCE,
            UbRoutes.SETTINGS_DATA,
            UbRoutes.DATA_SOURCES,
            UbRoutes.API_KEY,
            UbRoutes.ABOUT,
            UbRoutes.LOGIN,
        )
        subPages.forEach { route ->
            assertFalse(
                "$route 不该进分页容器：那里的横向手势会和返回手势、横向筛选条打架",
                isGameTabRoute(route),
            )
        }
    }

    @Test
    fun `我的与预留页也不可滑动`() {
        // ★ 用户只要求"三角洲和鸣潮之间"可滑。
        //   把「我的」也塞进 pager 会让它变成第三个分页，
        //   与"游戏标签"的语义不符 —— 它不是游戏。
        assertFalse(isGameTabRoute(UbRoutes.MINE))
        assertFalse(isGameTabRoute(UbRoutes.EXPLORE))
    }

    @Test
    fun `未注册的路由不可滑动`() {
        assertFalse(isGameTabRoute("no/such/route"))
        assertFalse(isGameTabRoute(""))
    }

    // -----------------------------------------------------------------------
    // ★★ 滑块位移公式（P11 用户反馈「tab 和页面应该是同步的，
    //    而不是工具页面滑动再 tab 动的」）
    //
    //    这是**回归钉子**。旧实现用
    //    「`offset > 0.15` → `base + 1`；`offset < -0.15` → `base - 1`」，
    //    真机录屏逐帧实测（120fps + 模板匹配测页面真实位移）抓到两个现象：
    //      · 页面已滑到 73% 时药丸仍停在源标签
    //      · 中途闪跳「鸣潮 → 三角洲 → 鸣潮」
    //    两种现象都只能"肉眼觉得怪"，不会崩溃 —— 正是最该被测试钉住的那类。
    // -----------------------------------------------------------------------

    @Test
    fun `两个端点必须精确落在两个标签上`() {
        // ⚠️ 带上显式 delta：否则 Kotlin 会把 0f/1f 装箱成 Object 去比 equals，
        //    一旦将来返回值改成 Double 就会静默走错重载。
        assertEquals("第 0 页吸附时必须正好在第 1 个标签", 0f, pagerSlideFraction(0, 0f, 1), 1e-6f)
        assertEquals("第 1 页吸附时必须正好在第 2 个标签", 1f, pagerSlideFraction(1, 0f, 1), 1e-6f)
    }

    @Test
    fun `过半处不得跳回源标签 —— 复现旧 bug 的回归钉子`() {
        // pager 连续位置 0.6 的真实表达：currentPage 已进位到 1，offset 为 −0.4。
        // 旧实现走 `offset < -0.15` 分支 → `base - 1` = 0，即**跳回三角洲**。
        val f = pagerSlideFraction(currentPage = 1, currentPageOffsetFraction = -0.4f, lastIndex = 1)

        assertEquals("过半后必须已经偏向目标标签", 0.6f, f, 1e-6f)
        assertTrue("过半后位置必须大于中点 —— 旧实现在这里跳回了源标签", f > 0.5f)
    }

    @Test
    fun `翻页前后位置连续 —— 不得在进位处出现台阶`() {
        // 同一次滑动，Compose 在过半时会重新表达成"下一页 + 负偏移"。
        // 下面两组相邻采样跨越了那次进位，位置必须平滑接上。
        val beforeCarry = pagerSlideFraction(currentPage = 0, currentPageOffsetFraction = 0.49f, lastIndex = 1)
        val afterCarry = pagerSlideFraction(currentPage = 1, currentPageOffsetFraction = -0.49f, lastIndex = 1)

        assertEquals(
            "进位前后的位置必须几乎相同（这才是「连续」的含义）",
            beforeCarry,
            afterCarry,
            0.02f,
        )
    }

    @Test
    fun `位置随位移单调前进 —— 不会倒退`() {
        // 一次左滑的采样序列（跨越了 currentPage 进位）
        val samples = listOf(
            pagerSlideFraction(0, 0.1f, 1),
            pagerSlideFraction(0, 0.3f, 1),
            pagerSlideFraction(0, 0.49f, 1),
            pagerSlideFraction(1, -0.49f, 1),
            pagerSlideFraction(1, -0.2f, 1),
            pagerSlideFraction(1, 0f, 1),
        )
        samples.zipWithNext { a, b ->
            assertTrue("滑块位置必须单调前进，实测旧实现会在过半处倒退：$samples", b >= a)
        }
    }

    @Test
    fun `两端回弹被夹在范围内`() {
        assertEquals("第 1 页往右拖不得变成负位置", 0f, pagerSlideFraction(0, -0.3f, 1), 1e-6f)
        assertEquals("最后一页往左拖不得越过末位", 1f, pagerSlideFraction(1, 0.3f, 1), 1e-6f)
    }

    @Test
    fun `只有一个标签时恒为 0 —— 不得越界`() {
        assertEquals(0f, pagerSlideFraction(0, 0.4f, 0), 1e-6f)
        assertEquals(0f, pagerSlideFraction(0, 0f, -1), 1e-6f)
    }
}
