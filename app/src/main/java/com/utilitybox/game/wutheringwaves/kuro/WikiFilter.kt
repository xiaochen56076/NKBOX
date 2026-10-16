package com.utilitybox.game.wutheringwaves.kuro

/**
 * 图鉴筛选的纯逻辑。
 *
 * ★ 单独成文件、**不依赖任何 Android/Compose 类型** —— 因为它是本项目里
 *   最典型的"错了界面看着正常、没人会发现"的一类代码，必须能用单元测试钉住。
 *
 * ### 语义（照官方 WIKI 的行为）
 *
 * | 范围 | 关系 |
 * |---|---|
 * | **同一分组内**多选 | **或** —— 选「五星」+「四星」= 五星 ∪ 四星 |
 * | **不同分组**之间 | **与** —— 选「五星」+「导电」= 五星 ∩ 导电 |
 *
 * ★ 这是本文件存在的主要理由：如果只用**一个扁平的选中集合**去 `any { it in tags }`，
 *   拿到的是"全或"语义 —— 选「五星 + 导电」会返回**所有**五星**加上**所有导电角色，
 *   而不是"五星的导电角色"。界面看起来完全正常，结果却是错的。
 */

/**
 * 按分组筛选条件过滤词条。
 *
 * @param selected 分组 id → 该分组下选中的标签 id 集合。
 *                 空集合表示该分组未筛选（不参与判断）。
 */
fun filterWikiEntries(
    entries: List<WikiEntry>,
    selected: Map<Int, Set<String>>,
): List<WikiEntry> {
    // 只保留"真的有选中项"的分组
    val active = selected.filterValues { it.isNotEmpty() }
    if (active.isEmpty()) return entries
    return entries.filter { entry ->
        // 每个有选中的分组都必须被满足（与）
        active.values.all { groupSelection ->
            // 该分组内任选其一即可（或）
            groupSelection.any { tagId -> tagId in entry.relateTagIds }
        }
    }
}

/**
 * 取词条在某个分组下的标签名 —— 用于卡片右上角的属性图标等。
 *
 * 例：分组「属性」→ 返回「导电」。
 *
 * ★ 返回 null 表示**该词条在这个分组下没有标签**。
 *   调用方必须**当作"没有"处理，不要给默认值** —— 编一个默认属性
 *   等于告诉用户一个错误的事实。
 */
fun wikiTagNameOf(entry: WikiEntry, group: WikiTagGroup): String? =
    group.tags.firstOrNull { it.id in entry.relateTagIds }?.name

/**
 * 按分组 id 取出对应的标签名（多选时返回全部，保持分组内的原始顺序）。
 *
 * 用于详情页展示"这个角色是 五星 / 导电 / 长刃"这类信息。
 */
fun wikiTagNamesOf(entry: WikiEntry, groups: List<WikiTagGroup>): List<Pair<String, String>> =
    groups.mapNotNull { g -> wikiTagNameOf(entry, g)?.let { g.name to it } }

/** 已选中的标签总数（用于筛选按钮上的角标） */
fun selectedTagCount(selected: Map<Int, Set<String>>): Int =
    selected.values.sumOf { it.size }
