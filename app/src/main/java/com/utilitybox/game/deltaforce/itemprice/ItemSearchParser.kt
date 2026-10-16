package com.utilitybox.game.deltaforce.itemprice

import com.utilitybox.core.json.Json
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.ItemSummary

/**
 * 物品搜索解析。
 *
 * ★ 实测响应字段（P10 从原始响应抄录，**不是猜的**）：
 * ```json
 * {"list":[{
 *   "objectID":13120000332, "objectName":"M250 75发弹链", "shortName":"M250 75发弹链",
 *   "grade":2, "avgPrice":49505,
 *   "pic":"https://playerhub.df.qq.com/playerhub/60004/object/13120000332.png",
 *   "prePic":"…/p_13120000332.png",
 *   "primaryClass":"acc", "secondClass":"accMagazine", "secondClassCN":"弹匣",
 *   "updatedAt":"2026-09-19T15:54:40.384Z"
 * }]}
 * ```
 *
 * ★ 图片来自**腾讯官方图床** `playerhub.df.qq.com`，不是第三方站自己的服务器。
 *   `prePic` 是小图（前缀 `p_`），列表用 `prePic` 更省流量。
 */
object ItemSearchParser {

    fun parse(data: Json?): List<ItemSummary>? {
        // `list` 是 data 对象里的数组字段
        val list = data?.arr("list")?.items ?: return null
        return list.mapNotNull { item ->
            val id = item.str("objectID")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = item.str("objectName")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            ItemSummary(
                objectId = id,
                name = name,
                shortName = item.str("shortName")?.takeIf { it.isNotBlank() },
                grade = item.int("grade"),
                category = item.str("secondClassCN")?.takeIf { it.isNotBlank() },
                // ★ avgPrice 为 0 / 负数 / 缺失一律 null（红线：不得显示 0 为真实价格）
                avgPrice = item.positiveLong("avgPrice"),
                // 列表优先用缩略图，省流量
                iconUrl = item.str("prePic")?.takeIf { it.isNotBlank() }
                    ?: item.str("pic")?.takeIf { it.isNotBlank() },
                primaryClass = item.str("primaryClass")?.takeIf { it.isNotBlank() },
                categoryCn = item.str("secondClassCN")?.takeIf { it.isNotBlank() },
            )
        }
    }

    /** 数据源元信息（供 UI 展示来源） */
    val ref = DeltaForceApi.ref
}
