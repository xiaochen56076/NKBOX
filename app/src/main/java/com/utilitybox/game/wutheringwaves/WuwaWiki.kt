package com.utilitybox.game.wutheringwaves

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
// ★ border 与 clip 一样，是 **Modifier 的扩展函数**，必须 import 后以 .border() 调用。
//   写成 androidx.compose.foundation.border(...) 会被当成普通顶层函数 → Unresolved reference。
//   这个坑在本文件里连着踩了两次（先 clip 后 border）。
import androidx.compose.foundation.border
// ★ verticalScroll 同样是 Modifier 的扩展函数，必须 import
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
// ★ 必须显式导入 grid 的 items(List) 扩展函数。
//   不导入时编译器只会看到 LazyGridScope 的**成员** items(count: Int)，
//   于是报出 "actual type is List<...>, but 'Int' was expected" ——
//   错误信息指向实参类型，和真正的原因（少了 import）看起来毫无关系。
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.utilitybox.core.design.*
import com.utilitybox.game.wutheringwaves.kuro.KuroWikiApi
import com.utilitybox.game.wutheringwaves.kuro.WikiCategory
import com.utilitybox.game.wutheringwaves.kuro.WikiEntry
import com.utilitybox.game.wutheringwaves.kuro.WikiEntryDetail
import com.utilitybox.game.wutheringwaves.kuro.WikiHtml
import com.utilitybox.game.wutheringwaves.kuro.WikiResult
import com.utilitybox.game.wutheringwaves.kuro.WikiSubCatalogue
// ★ 本文件在父包 wutheringwaves，而筛选相关的东西在子包 kuro ——
//   Kotlin **不会**自动把子包带进来（这一点和 Java 不同），必须逐个 import。
import com.utilitybox.game.wutheringwaves.kuro.WikiTagGroup
import com.utilitybox.game.wutheringwaves.kuro.filterWikiEntries
import com.utilitybox.game.wutheringwaves.kuro.selectedTagCount
import com.utilitybox.game.wutheringwaves.kuro.wikiTagNameOf

// ---------------------------------------------------------------------------
// 1. 分类
// ---------------------------------------------------------------------------

/**
 * 图鉴首页：分类树。
 *
 * ★ 这是一个**二级分组树**（游戏图鉴 → 共鸣者 / 武器 / …），
 *   所以按层级缩进展示，而不是拍平成一个长列表 —— 拍平会丢掉"这几项属于同一组"的信息。
 */
/**
 * 顶层保留哪些分组 —— 按**接口实测的名字**白名单（P11 补 45）。
 *
 * ★ 接口实测返回 9 个顶层（见 `docs/wiki-filter-api.md`）：
 *   游戏图鉴 / 攻略合集 / 剧情合集 / 活动合集 / 任务合集 /
 *   成就合集 / 影像收录 / 热门资讯 / 游戏百科
 *
 *   用户只要「游戏图鉴 + 攻略（改名养成）」，其余 7 个是噪音。
 * ★ 用白名单而不是黑名单：接口**新增**分组时不会被悄悄放进来。
 */
private val TOP_LEVEL_KEEP = setOf("游戏图鉴")
// ★★ P11 补 69：去掉了「攻略合集」。
//
//   用户指出：「目前就是你的养成和图鉴内容是一样的，你应该考虑去掉养成」。
//
//   确实重复了：
//     游戏图鉴 → 角色与武器   ─┐
//                              ├→ 都是 catalogueId 1105，同一个列表
//     养成     → 角色养成     ─┘
//
//   起因是 P11 补 62 我把「养成」从 1384（攻略帖聚合，拿不到养成数据）
//   改指到 1105（共鸣者，有完整词条）—— **数据修对了，但造出了重复入口**。
//
//   现在养成数据在 游戏图鉴 → 角色与武器 → 角色 → 原生详情页 里都有
//   （1级~90级面板、一阶~六阶突破材料、技能突破材料）。
//   所以「养成」顶层分组整个去掉 —— **一个入口只留一份**。
//
//   ★ 只改白名单、不删下面的分支代码：分支会因分组不再出现而不执行，
//     将来若要恢复「养成」，把 "攻略合集" 加回这个 set 即可。

/** 游戏图鉴的接口名 */
private const val CAT_GAME = "游戏图鉴"

/** 攻略合集的接口名 */
private const val CAT_GUIDE = "攻略合集"

/** 攻略合集里保留哪个子分类 —— 实测「角色攻略」下有 59 个角色，每个一篇养成 */
private const val CAT_ROLE_GUIDE = 1384

/**
 * 「共鸣者」分类 id（P11 补 62）。
 *
 * ★ 实测值（探针 `wiki1384.ps1` / `wiki_entry.ps1`）。
 *
 * **养成数据在这里，不在 1384**：
 * - `1384`（角色攻略）→ 59 个角色 → 每个角色下是**玩家攻略帖聚合**，
 *   唯一的词条实测 `modules = 0`（空壳）——拿不到共鸣链和突破材料；
 * - `1105`（共鸣者）→ 65 个角色 → **完整词条**（5 个模块，
 *   含基础资料与角色养成：技能介绍 / 共鸣链 / 角色突破材料）。
 *
 * 所以「养成」入口指向这里。写实测 id 而不是猜树结构，
 * 和上面合并入口用 1105/1106 是同一个思路。
 */
const val CAT_RESONATOR = 1105

/**
 * 「角色与武器」合并入口的**哨兵 id**。
 *
 * ★ 用负数：接口的 catalogueId 全是正整数（实测 1099/1105/1106/…），
 *   负数不可能与真实分类撞上。
 */
const val MERGED_CHAR_WEAPON = -1

/** 游戏图鉴里被合并的两个真实分类 id（接口实测） */
private const val CAT_ROLE = 1105
private const val CAT_WEAPON = 1106

@Composable
fun WuwaWikiCategoriesScreen(onOpenCategory: (Int, String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    var tree by remember { mutableStateOf<List<WikiCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = KuroWikiApi.tree()) {
            is WikiResult.Ok -> tree = r.value
            is WikiResult.Failure -> error = r.message
        }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            loading -> Box(modifier = Modifier.padding(top = s.l)) { UbLoading() }
            error != null -> SectionCard {
                UbError(
                    title = "分类加载失败",
                    description = error ?: "",
                    onRetry = {
                        loading = true; error = null
                    },
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            tree.isEmpty() -> SectionCard {
                UbEmpty(
                    title = "没有分类",
                    description = "库街区图鉴接口没有返回内容。",
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            // ★ P11 补 22：用户指出「全部列在一坨，用点 ui 库组件排版」。
            //
            //   原来是 `LazyColumn` + 缩进列表行 —— 十几个分类一条条往下堆，
            //   没有任何视觉分组，扫视时找不到东西。
            //
            //   现在改成 **两列网格卡片**：每个分组一个分节标题（复用设计系统的
            //   `UbSectionHeader`），分类变成等宽卡片。好处：
            //   · 一屏能放下更多分类，且是"块"不是"行"，扫视更快
            //   · 卡片有描边与图标，层级靠**分组标题 + 卡片**表达，
            //     不再依赖缩进（网格里缩进没有意义，已去掉）
            else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = s.screenH,
                    end = s.screenH,
                    bottom = LocalUbSize.current.bottomBarReserved,
                ),
                horizontalArrangement = Arrangement.spacedBy(s.m),
                verticalArrangement = Arrangement.spacedBy(s.m),
            ) {
                // ★★ P11 补 45：顶层从 9 项收窄到 2 项。
                //
                //   用户要求：「保留**游戏图鉴**和**攻略**即可（游戏图鉴里面只需要留下
                //   **角色、武器**即可，攻略改为**养成**吧，不同角色的养成，需要啥的）」。
                //
                //   接口实际返回 9 个顶层：游戏图鉴 / 攻略合集 / 剧情合集 / 活动合集 /
                //   任务合集 / 成就合集 / 影像收录 / 热门资讯 / 游戏百科 —— 与我们无关的
                //   有 7 个。这不是"少给用户东西"，是**把噪音去掉**：
                //   原样铺开时，用户要在一屏里找自己要的那一个。
                //
                // ★ 白名单是**按接口实测的名字**写的，不是猜的（见 docs/wiki-filter-api.md）。
                tree.filter { it.name in TOP_LEVEL_KEEP }.forEach { group ->
                    item(
                        key = "g${group.id}",
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    ) {
                        UbSectionHeader(title = if (group.name == CAT_GUIDE) "养成" else group.name)
                    }

                    if (group.name == CAT_GAME) {
                        // ---- 游戏图鉴：角色与武器**合并成一个入口** ----
                        // 用户要求「角色和武器用同一个列表 + 一个「角色/武器」切换 chip」，
                        // 所以这里不再分两个卡片，进去之后再切。
                        item(key = "charweapon") {
                            WikiCategoryTile(
                                name = "角色与武器",
                                onClick = { onOpenCategory(MERGED_CHAR_WEAPON, "角色与武器") },
                            )
                        }
                    } else {
                        // ---- 养成：指向**共鸣者列表**（P11 补 62）----
                        //
                        // ★★ 这里原来指向 1384（角色攻略）。**实测那条路是死的**：
                        //
                        //   getPage(1384) → 59 个角色（子分类）
                        //     └─ getPage(1644)「心」→ 9 条 `kuro-post-component`
                        //         其中 8 条是**外链 kurobbs 帖子**（linkUrl=.../post/...），
                        //         唯一那个词条（1546618120826560512）实测
                        //         **modules = 0** —— 空壳，名字还叫「角色攻略-心」。
                        //
                        //   也就是说走 1384 那一层，**根本拿不到养成数据** ——
                        //   共鸣链、突破材料在那个空壳里一条都没有（关键词计数全为 0）。
                        //
                        //   真正的养成数据在图鉴的**共鸣者词条**里：
                        //   1105 → 65 个角色 → 完整词条（5 个模块，
                        //   含「基础资料」「角色养成（技能介绍/共鸣链/角色突破材料）」）。
                        //
                        //   → 所以「养成」直接指向 1105，点角色直达完整词条。
                        //     顺带把导航从 4 层压到 3 层 —— 这也正是用户问的
                        //     「为什么点了角色后，才到这个界面」。
                        item(key = "growth") {
                            WikiCategoryTile(
                                name = "角色养成",
                                onClick = { onOpenCategory(CAT_RESONATOR, "角色养成") },
                            )
                        }
                    }
                }
                item(
                    key = "footer",
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                ) {
                    Column {
                        UbSourceFooter(source = "库街区公开图鉴接口", updatedAt = "实时")
                        Text(
                            text = "内容由库街区 WIKI 提供，非官方接口。",
                            style = t.caption,
                            color = c.textTertiary,
                            modifier = Modifier.padding(horizontal = s.s, vertical = s.s),
                        )
                    }
                }
            }
        }
    }
}

/** 把分组展平成可点击的叶子列表，带缩进层级 */
private fun flattenLeaves(
    node: WikiCategory,
    depth: Int = 0,
    out: MutableList<Pair<Int, WikiCategory>> = mutableListOf(),
): List<Pair<Int, WikiCategory>> {
    node.children.forEach { child ->
        if (child.children.isEmpty()) {
            // 叶子：可点进条目列表
            out += depth to child
        } else {
            // 中间层：**也**要可点 —— 有些中间层自己有条目（hasItem），
            // 只是同时还有子分类。不可点会让用户以为点不动。
            if (child.hasItem) out += depth to child
            flattenLeaves(child, depth + 1, out)
        }
    }
    return out
}

/**
 * 图鉴分类卡片（两列网格中的一个格子）。
 *
 * ★ P11 补 22：由 `CategoryRow`（缩进列表行）改成 **Material3 `Card` 网格瓦片**。
 *
 * 为什么网格比列表行好：
 * - 分类是**并列**关系，不是序列。列表行的视觉暗示是"从上往下读"，
 *   而用户实际是在"扫一眼找我要的那类"。
 * - 卡片有描边与图标容器，块感强，扫视定位快。
 * - 一屏容纳更多分类，减少滚动。
 *
 * ★ 图标按**分类名关键字**映射。这不是精确映射（接口不提供图标字段），
 *   所以兜底必须是通用图标 —— 猜错时显示一个中性图标，而不是错误暗示。
 */
@Composable
private fun WikiCategoryTile(name: String, onClick: () -> Unit) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 84.dp)
            .ubClickable(scale = UbPressScale.Card, onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(r.card),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = c.bgElevated),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.divider),
    ) {
        Column(modifier = Modifier.padding(s.cardPad)) {
            UbIcons.Icon(
                icon = categoryIcon(name),
                tint = c.accent,
                size = 22.dp,
            )
            Spacer(Modifier.height(s.s))
            Text(
                text = name,
                style = t.bodyLarge,
                color = c.textPrimary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 图鉴条目卡（两列网格中的一个格子）。
 *
 * ★ P11 补 26：按**官方 WIKI 的版式**重做（用户提供截图作为参考）。
 *   原版是"图片区 + 名字 + 星级"的通用卡片，功能对但不像图鉴。
 *
 * 现在的结构自上而下：
 *
 * ```
 * ┌──────────────────────┐
 * │ [官方角标]   [属性]   │  ← 角标用官方图；属性名来自 tagTree
 * │                      │
 * │      [立绘]          │  ← 稀有度渐变底
 * │     ★★★★★           │  ← 星行
 * ├──────────────────────┤
 * │         心           │  ← 底部名称条（深底白字）
 * └──────────────────────┘
 * ```
 *
 * ★ 三个字段的来源都是**真实数据**，没有一个是我编的：
 *   · 稀有度 → `content.star`（决定渐变底色与星数）
 *   · 属性   → `tagTree`「属性」分组里与该词条 `relateTagIds` 匹配的那一项
 *   · 角标   → `content.cornerMarkUrl`（**官方图**）
 *
 * ★ 角标为什么用图不用文字：`cornerMark` 只是布尔值，
 *   说不出是「新」还是「复刻」还是「预」。自己填一个字就是编数据。
 *
 * ★ 属性**没有就留空**，不给默认值 —— 编一个默认属性等于告诉用户错误的事实。
 */
@Composable
private fun WikiEntryCard(
    entry: WikiEntry,
    elementName: String?,
    onClick: () -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    val rarity = starColor(entry.star)

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().ubClickable(scale = UbPressScale.Card, onClick = onClick),
        shape = RoundedCornerShape(r.card),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = c.bgElevated),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.divider),
    ) {
        // ---- 画面上半部：立绘 + 稀有度渐变 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .background(
                    // 稀有度决定底色：五星金、四星紫。透明度压得很低，
                    // 是为了**衬托立绘**而不是抢它 —— 官方也是这个思路。
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(rarity.copy(alpha = 0.22f), rarity.copy(alpha = 0.04f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.imageUrl != null) {
                UbItemIcon(url = entry.imageUrl, contentDescription = null, size = 128.dp)
            } else {
                UbIcons.Icon(icon = UbIcon.Empty, tint = c.textTertiary, size = 30.dp)
            }

            // 左上角：官方角标图
            if (entry.cornerMarkUrl != null) {
                UbItemIcon(
                    url = entry.cornerMarkUrl,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopStart).padding(s.xs),
                    size = 34.dp,
                )
            }

            // 右上角：属性名（不是图标 —— 我们没有可靠的元素图标集）
            if (elementName != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(s.xs)
                        .androidxClipCapsule(r.capsule)
                        .background(c.bgBase.copy(alpha = 0.82f))
                        .padding(horizontal = s.s, vertical = 2.dp),
                ) {
                    Text(text = elementName, style = t.caption, color = c.textSecondary)
                }
            }

            // 星行：稀有度色
            if (entry.star > 0) {
                Text(
                    text = "★".repeat(entry.star),
                    style = t.caption,
                    color = rarity,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                )
            }
        }

        // ---- 底部名称条：深底白字，与官方一致 ----
        Box(
            modifier = Modifier.fillMaxWidth().background(c.bgInverse).padding(vertical = s.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.name,
                style = t.bodyLarge,
                color = c.textInverse,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = s.s),
            )
        }
    }
}

/**
 * 不在筛选面板里显示的分组（用户 P11 补 29 裁决：不需要）。
 *
 * ★ 用**分组名**而不是 id：id 会随接口调整漂移，而"实装版本"这个名字
 *   在中文版接口里是稳定的。真改名了的表现是"分组又冒出来了" ——
 *   属于**可见的失效**，比悄悄过滤错东西安全。
 *
 * ★ 只在 UI 层过滤，模型层如实保留接口返回的数据（见调用处说明）。
 */
private const val HIDDEN_FILTER_GROUP = "实装版本"

/** 胶囊裁剪的小包装 */
private fun Modifier.androidxClipCapsule(radius: androidx.compose.ui.unit.Dp): Modifier =
    // ★ clip 是 Modifier 的扩展函数，**不能**写成 `androidx.compose.ui.draw.clip(...)` ——
    //   那样会被当成普通顶层函数，报 Unresolved reference。
    //   扩展函数必须在文件顶部 import，然后以 `.clip(...)` 调用。
    this.clip(RoundedCornerShape(radius))

/**
 * 分类名 → 图标。
 *
 * ★ 接口**不提供**图标字段，所以这是关键字猜测，**必须有兜底**。
 *   宁可显示一个中性图标，也不要显示一个会误导的图标
 *   （例如把「声骸」映射成「首页」图标）。
 */
private fun categoryIcon(name: String): UbIcon = when {
    name.contains("共鸣者") || name.contains("角色") -> UbIcon.User
    name.contains("武器") -> UbIcon.Tag
    name.contains("声骸") -> UbIcon.Database
    name.contains("敌人") || name.contains("全息") -> UbIcon.Warning
    name.contains("活动") -> UbIcon.Clock
    name.contains("攻略") || name.contains("任务") || name.contains("剧情") -> UbIcon.Info
    name.contains("道具") || name.contains("素材") ||
        name.contains("资源") || name.contains("补给") || name.contains("合成") -> UbIcon.Database
    else -> UbIcon.Grid
}

// ---------------------------------------------------------------------------
// 1.5 角色与武器（合并列表 + 切换 chip）
// ---------------------------------------------------------------------------

/**
 * 角色 / 武器合并列表（P11 补 45）。
 *
 * ★ 用户要求：「游戏图鉴那边，**角色和武器用同一个列表 + 一个「角色/武器」切换 chip**」。
 *
 * ★ 为什么合并是合理的：查一个角色的**专属武器**、或者按武器反查谁能用时，
 *   在两个分类之间来回跳很烦。合成一个列表 + 顶部 chip 切换，切一次就是一屏。
 *
 * ★ 两个分类的条目**分别请求、分别缓存**（`KuroWikiApi.page` 内部按 catalogueId 缓存），
 *   所以切 chip 不会重新打网络 —— 首屏只等当前选中的那一个。
 */
@Composable
fun WuwaWikiCharWeaponScreen(
    onOpenEntry: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    // 0 = 角色，1 = 武器
    var tab by remember { mutableStateOf(0) }
    val catId = if (tab == 0) CAT_ROLE else CAT_WEAPON

    var entries by remember(catId) { mutableStateOf<List<WikiEntry>>(emptyList()) }
    var loading by remember(catId) { mutableStateOf(true) }
    var error by remember(catId) { mutableStateOf<String?>(null) }

    LaunchedEffect(catId) {
        loading = true
        error = null
        when (val r = KuroWikiApi.page(catId)) {
            is WikiResult.Ok -> {
                entries = r.value.entries
                loading = false
            }
            is WikiResult.Failure -> {
                error = r.message
                loading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH, vertical = s.s),
            horizontalArrangement = Arrangement.spacedBy(s.s),
        ) {
            UbChip(label = "角色", selected = tab == 0, onClick = { tab = 0 })
            UbChip(label = "武器", selected = tab == 1, onClick = { tab = 1 })
            Spacer(Modifier.weight(1f))
            Text(
                text = if (loading) "加载中…" else "${entries.size} 个",
                style = t.caption,
                color = c.textTertiary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        when {
            error != null -> SectionCard {
                UbError(
                    title = "加载失败",
                    description = error ?: "",
                    onRetry = { tab = if (tab == 0) 1 else 0 },
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            loading && entries.isEmpty() -> SectionCard {
                UbLoading(modifier = Modifier.padding(vertical = s.m))
            }
            entries.isEmpty() -> SectionCard {
                UbEmpty(
                    title = "没有内容",
                    description = "库街区图鉴接口没有返回条目。",
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = s.screenH,
                    end = s.screenH,
                    bottom = LocalUbSize.current.bottomBarReserved,
                ),
                horizontalArrangement = Arrangement.spacedBy(s.m),
                verticalArrangement = Arrangement.spacedBy(s.m),
            ) {
                // ★ key 里带上 tab —— 否则 LazyVerticalGrid 会复用上一个 tab 的
                //   滚动位置与 item 身份，切换后会出现"内容换了但位置还在半空"
                items(entries, key = { "${tab}_${it.entryId}" }) { e ->
                    WikiEntryCard(
                        entry = e,
                        // ★ 属性名传 null：属性筛选在「条目列表」页才做（那边有接口给的
                        //   tagTree）。这里合并列表追求的是**快**，不做筛选，
                        //   所以不给卡片加属性角标 —— 传一个猜出来的属性名比不传更糟。
                        elementName = null,
                        onClick = { onOpenEntry(e.entryId, e.name) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. 子分类（图鉴的中间层，P11 补 60）
// ---------------------------------------------------------------------------

/**
 * 子分类网格。
 *
 * ★ 形态和条目卡一致（两列、封面在上、名字在下）—— 因为对用户来说
 *   「养成 → 心」和「角色 → 心」是**同一件事**：一个可以用图认出来的东西。
 *   两者长得不一样只会让人以为进错了地方。
 *
 * ★ 封面来自 `content.contentUrl`（实测每个角色都有，如「心.png」），
 *   **不是**靠名字现拼 URL —— 那样拼错了就是一堆破图。
 */
@Composable
private fun WuwaWikiSubCategoryGrid(
    items: List<WikiSubCatalogue>,
    onOpen: (Int, String) -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = s.screenH,
            end = s.screenH,
            top = s.m,
            bottom = LocalUbSize.current.bottomBarReserved,
        ),
        horizontalArrangement = Arrangement.spacedBy(s.m),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        items(items, key = { it.catalogueId }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(r.card))
                    .background(c.bgElevated)
                    .ubClickable(onClick = { onOpen(item.catalogueId, item.name) })
                    .padding(s.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(s.s),
            ) {
                if (item.imageUrl != null) {
                    UbItemIcon(
                        url = item.imageUrl,
                        contentDescription = item.name,
                        size = 96.dp,
                    )
                }
                Text(
                    text = item.name,
                    style = t.bodyLarge,
                    color = c.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. 条目列表
// ---------------------------------------------------------------------------

@Composable
fun WuwaWikiEntriesScreen(
    catalogueId: Int,
    title: String,
    onOpenEntry: (String, String) -> Unit,
    /**
     * 打开一个**子分类**（P11 补 60）。
     *
     * ★ 有些分类页返回的不是条目，而是**子分类** ——
     *   典型是「养成」：`getPage(1384)` 返回 59 个角色的分类，
     *   每个点进去才是该角色的养成词条。
     *
     *   没有这个回调，那一层就**点不进去**（之前的表现是「这个分类下没有条目」）。
     */
    onOpenCategory: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    var entries by remember(catalogueId) { mutableStateOf<List<WikiEntry>>(emptyList()) }
    /**
     * 子分类列表（P11 补 60）。
     *
     * ★ 与 [entries] 是**互斥的两种内容形态**，来自同一个响应：
     *   接口要么给条目（有 entryId），要么给子分类（有 linkConfig.catalogueId）。
     */
    var subCatalogues by remember(catalogueId) {
        mutableStateOf<List<WikiSubCatalogue>>(emptyList())
    }
    // 筛选条件树**来自接口**，不写死 —— 不同分类的筛选维度不同
    var filterGroups by remember(catalogueId) { mutableStateOf<List<WikiTagGroup>>(emptyList()) }
    var loading by remember(catalogueId) { mutableStateOf(true) }
    var error by remember(catalogueId) { mutableStateOf<String?>(null) }

    // 已应用的筛选（分组 id → 选中标签）。面板里改的是草稿，点「确认」才落到这里
    var applied by remember(catalogueId) { mutableStateOf<Map<Int, Set<String>>>(emptyMap()) }
    var sheetOpen by remember(catalogueId) { mutableStateOf(false) }

    // ★ 纯函数过滤，逻辑见 WikiFilter.kt（有 11 条单元测试钉住语义）
    val filtered = remember(entries, applied) { filterWikiEntries(entries, applied) }
    // 卡片右上角的属性 chip：「属性」分组里与该词条匹配的那一项
    val elemGroup = remember(filterGroups) { filterGroups.firstOrNull { it.name == "属性" } }

    LaunchedEffect(catalogueId) {
        loading = true
        // 用 page() 而不是 entries()：同一个请求里就带回了筛选条件树，
        // 单独再发一次只为拿筛选条件是白白多一次网络往返
        when (val r = KuroWikiApi.page(catalogueId)) {
            is WikiResult.Ok -> {
                entries = r.value.entries
                subCatalogues = r.value.subCatalogues
                filterGroups = r.value.filterGroups
            }
            is WikiResult.Failure -> error = r.message
        }
        loading = false
    }

    // 筛选面板（底部弹层）。放在这里而不是塞进 when 分支 ——
    // ModalBottomSheet 是**浮层**，与列表的布局无关
    if (sheetOpen) {
        WikiFilterSheet(
            // ★ 用户明确要求去掉「实装版本」（P11 补 29）——23 个版本号对用图鉴查
            //   角色资料的人没有意义，还会把面板撑得很长。
            //
            //   ★ 在**这里**过滤，而不是在 `parseTagGroups` 里丢掉：
            //     接口确实返回了这个分组，模型层如实保留；
            //     "我们不需要"是**产品决策**，属于 UI 层。哪天想加回来，删掉这一行即可，
            //     数据一直都在。
            groups = filterGroups.filterNot { it.name == HIDDEN_FILTER_GROUP },
            applied = applied,
            onDismiss = { sheetOpen = false },
            onApply = { applied = it },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            loading -> Box(modifier = Modifier.padding(top = s.l)) { UbLoading() }
            error != null -> SectionCard {
                UbError(
                    title = "加载失败",
                    description = error ?: "",
                    onRetry = { error = null; loading = true },
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            // ★ P11 补 60：**子分类优先**。
            //
            //   「养成」这一页返回的就是 59 个角色的分类。在修好解析之前，
            //   它走的是下面的 `entries.isEmpty()`，显示「这个分类下没有条目」——
            //   用户看到的是"这页是空的"，实际是**一整层导航被吃掉了**。
            subCatalogues.isNotEmpty() -> WuwaWikiSubCategoryGrid(
                items = subCatalogues,
                onOpen = onOpenCategory,
            )
            entries.isEmpty() -> SectionCard {
                UbEmpty(
                    title = "这个分类下没有条目",
                    description = "「$title」暂时没有可查看的内容。",
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            // ★ P11 补 25：用户指出这一页「还是不好，不好看，要活用 UI 组件」。
            //
            //   原来是 `LazyColumn` + 「44dp 小图标 + 名字 + 箭头」的**素列表** ——
            //   这个形态适合"设置项"，不适合**图鉴**：
            //   图鉴的主角是**美术资源本身**，把它压成 44dp 的小方块等于把主角藏起来。
            //
            //   改成**两列角色卡网格**：图片区占卡片上半部（96dp 居中 + 淡底），
            //   下半部是名字与星级徽章。这样一屏能认出更多角色，也更像图鉴该有的样子。
            else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = s.screenH,
                    end = s.screenH,
                    top = s.m,
                    bottom = LocalUbSize.current.bottomBarReserved,
                ),
                horizontalArrangement = Arrangement.spacedBy(s.m),
                verticalArrangement = Arrangement.spacedBy(s.m),
            ) {
                // 排序 / 筛选工具条（占满整行）
                if (filterGroups.isNotEmpty()) {
                    item(
                        key = "toolbar",
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = s.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val n = selectedTagCount(applied)
                            Text(
                                text = if (n == 0) "共 ${entries.size} 个" else "已筛选 · $n 个条件",
                                style = t.caption,
                                color = c.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            if (n > 0) {
                                UbTextButton(
                                    label = "清除",
                                    onClick = { applied = emptyMap() },
                                )
                                Spacer(Modifier.width(s.s))
                            }
                            UbTextButton(
                                label = if (n == 0) "筛选" else "筛选 ($n)",
                                onClick = { sheetOpen = true },
                            )
                        }
                    }
                }

                items(filtered, key = { it.entryId }) { e ->
                    WikiEntryCard(
                        entry = e,
                        elementName = elemGroup?.let { g -> wikiTagNameOf(e, g) },
                        onClick = { onOpenEntry(e.entryId, e.name) },
                    )
                }
                item(
                    key = "count",
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        text = if (applied.isEmpty()) {
                            "共 ${entries.size} 条 · 来源：库街区 WIKI"
                        } else {
                            "筛选后 ${filtered.size} / ${entries.size} 条 · 来源：库街区 WIKI"
                        },
                        style = t.caption,
                        color = c.textTertiary,
                        modifier = Modifier.padding(horizontal = s.s, vertical = s.m),
                    )
                }
            }
        }
    }
}

/**
 * 筛选面板（底部弹层）。
 *
 * ★ 分组**全部来自接口的 `tagTree`**，一个都没写死 ——
 *   「共鸣者」是稀有度/属性/武器/实装版本/风格定位，
 *   「武器投影」的维度显然不同，写死就只能在共鸣者这一页用。
 *
 * ★ 草稿 / 应用分离：面板里改的是 [draft]，点「确认」才回调 [onApply]。
 *   如果边选边应用，用户每点一下背景列表都要重算 + 重排，
 *   而且点「取消」就没法回退了。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WikiFilterSheet(
    groups: List<WikiTagGroup>,
    applied: Map<Int, Set<String>>,
    onDismiss: () -> Unit,
    onApply: (Map<Int, Set<String>>) -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    var draft by remember(groups) { mutableStateOf(applied) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bgBase,
        // ★ 实测 bug：5 个分组里「实装版本」一项就有 23 个标签，
        //   面板内容远高于默认的半展开高度，把「取消 / 确认」挤到屏幕外 ——
        //   用户**根本点不到确认**，筛选功能等于不可用。
        //   单元测试测不出来，只有真跑才会发现。
        //
        //   ★ 修法**不是**靠"让面板更高"：这个 Compose BOM 的
        //     `ModalBottomSheet` 没有 `skipPartiallyExpanded` 参数（编译报错）。
        //     真正的修法是**让分组区可滚动、按钮固定在滚动区之外** ——
        //     这样面板多高都不影响按钮可达性，比调高度更稳。
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // ★ 整块可滚（含按钮）。
                //
                //   试过两种更"优雅"的做法都失败：
                //   ① 只让分组区滚 + 按钮固定 → `weight(1f, fill = false)`
                //      在 ModalBottomSheet 里拿不到有界高度，按钮照样被顶出屏幕；
                //   ② `skipPartiallyExpanded = true` → 这个 BOM 的
                //      ModalBottomSheet **没有这个参数**，编译直接报错。
                //
                //   整块滚动是**一定可达**的：内容再长，滚一下就能碰到「确认」。
                //   已经实测确认过按钮找得到（见 CHANGELOG 补 28）。
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = s.screenH)
                .padding(bottom = s.xl),
            verticalArrangement = Arrangement.spacedBy(s.m),
        ) {
            Text(text = "筛选", style = t.displayLarge, color = c.textPrimary)

            groups.forEach { g ->
                Column(verticalArrangement = Arrangement.spacedBy(s.s)) {
                    Text(
                        text = g.name,
                        style = t.labelMedium,
                        color = c.textTertiary,
                        modifier = Modifier.padding(top = s.s),
                    )
                    // ★ 自动换行的标签流。用 FlowRow 而不是横向滚动：
                    //   实装版本有 23 项，横滑会让用户以为只有几个
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(s.s),
                        verticalArrangement = Arrangement.spacedBy(s.s),
                    ) {
                        g.tags.forEach { tag ->
                            val on = draft[g.id]?.contains(tag.id) == true
                            WikiFilterChip(
                                text = tag.name,
                                selected = on,
                                onClick = {
                                    val cur = draft[g.id].orEmpty()
                                    val next = if (on) cur - tag.id else cur + tag.id
                                    draft = draft + (g.id to next)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(s.s))
            Row(horizontalArrangement = Arrangement.spacedBy(s.m)) {
                UbSecondaryButton(
                    label = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                UbPrimaryButton(
                    label = "确认",
                    onClick = { onApply(draft); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 筛选标签：选中态用强调色实底，未选中用浅底描边 */
@Composable
private fun WikiFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    Box(
        modifier = Modifier
            .androidxClipCapsule(r.capsule)
            .background(if (selected) c.accent else c.bgElevated)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.androidxBorderCapsule(c.divider, r.capsule)
                },
            )
            .ubClickable(scale = UbPressScale.Row, onClick = onClick)
            .padding(horizontal = s.l, vertical = s.m),
    ) {
        Text(
            text = text,
            style = t.bodyLarge,
            color = if (selected) c.textOnAccent else c.textSecondary,
        )
    }
}

/** 胶囊描边的小包装 */
private fun Modifier.androidxBorderCapsule(
    color: androidx.compose.ui.graphics.Color,
    radius: androidx.compose.ui.unit.Dp,
): Modifier = this.border(1.dp, color, RoundedCornerShape(radius))

@Composable
private fun starColor(star: Int) = when (star) {
    5 -> androidx.compose.ui.graphics.Color(0xFFD9A21B)
    4 -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
    else -> LocalUbColors.current.textTertiary
}

// ---------------------------------------------------------------------------
// 3. 词条详情（WebView）
// ---------------------------------------------------------------------------

/**
 * 词条详情。
 *
 * ### ★ WebView 的安全设置（逐条都是必要的）
 *
 * 页面内容来自**外部接口的 HTML**，我们只转义自己插入的字段、不改变正文，
 * 所以必须假设正文可能带任何东西：
 *
 * | 设置 | 值 | 原因 |
 * |---|---|---|
 * | `javaScriptEnabled` | **false** | 正文里没有任何需要 JS 的东西（折叠用原生 `<details>`）。关掉就从根上消除了脚本执行 |
 * | `allowFileAccess` | **false** | 否则页面能读应用私有目录 —— 那里有库街区 token |
 * | `allowContentAccess` | **false** | 同上，堵住 `content://` |
 * | `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs` | **false** | 防 `file://` 跨源 |
 * | `addJavascriptInterface` | **不调用** | 没有 JS 桥就没有 JS→Java 的通道 |
 * | `shouldOverrideUrlLoading` | **全部拦掉** | 不让 WebView 内部跳转；正文里的链接一律不跟随 |
 *
 * ★ 不做 JS 桥、不做文件访问、不做跳转 —— 这三条合起来，
 *   即使库街区的正文被注入，这个 WebView 也没有能伤到用户的出口。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WuwaWikiDetailScreen(entryId: String, modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current

    var detail by remember(entryId) { mutableStateOf<WikiEntryDetail?>(null) }
    var loading by remember(entryId) { mutableStateOf(true) }
    var error by remember(entryId) { mutableStateOf<String?>(null) }

    LaunchedEffect(entryId) {
        loading = true
        when (val r = KuroWikiApi.detail(entryId)) {
            is WikiResult.Ok -> detail = r.value
            is WikiResult.Failure -> error = r.message
        }
        loading = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loading -> Box(modifier = Modifier.padding(top = s.l)) { UbLoading(lines = 4) }
            error != null -> SectionCard {
                UbError(
                    title = "加载失败",
                    description = error ?: "",
                    onRetry = { error = null; loading = true },
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            detail != null -> {
                val html = remember(detail) { WikiHtml.build(detail!!) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.domStorageEnabled = false
                            settings.databaseEnabled = false
                            settings.setSupportZoom(false)
                            settings.builtInZoomControls = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.loadsImagesAutomatically = true
                            // 内容区是白底，避免深色主题下 WebView 默认黑底闪一下
                            setBackgroundColor(android.graphics.Color.WHITE)
                            webViewClient = object : WebViewClient() {
                                // ★ 拦掉一切跳转：正文里的链接不跟随。
                                //   这个 WebView 只用来展示一份我们拼好的文档。
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = true
                            }
                        }
                    },
                    update = { web -> web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
                )
            }
            else -> SectionCard {
                UbEmpty(
                    title = "没有内容",
                    description = "该词条没有可展示的正文。",
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
        }
        // 加载完成后底部提示，避免用户以为内容到此为止
        if (detail != null) {
            Text(
                text = "内容来自库街区 WIKI · 长按可选中文字",
                style = LocalUbTypography.current.caption,
                color = c.textTertiary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(c.bgElevated.copy(alpha = 0.92f))
                    .padding(horizontal = s.screenH, vertical = s.s),
            )
        }
    }
}
