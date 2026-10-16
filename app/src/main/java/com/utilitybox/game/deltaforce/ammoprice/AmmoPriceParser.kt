package com.utilitybox.game.deltaforce.ammoprice

import com.utilitybox.core.json.Json
import com.utilitybox.domain.datasource.DeltaForceApi
import com.utilitybox.domain.model.AmmoPrice

/**
 * 弹药接口解析（`/api/v1/df/object/ammo`）。
 *
 * ★★ 字段结构来自 P10 **实测原始响应** —— 此前凭 `priceStats` 猜错过一次：
 * ```json
 * {"bullets":[{
 *   "objectID":37150600001, "objectName":"6.8x51mm AP", "shortName":"6.8x51",
 *   "shortNameSec":"AP",
 *   "grade":6,                       // 稀有度 ★1–★6
 *   "avgPrice":0,                    // ★ 均价在**顶层**，不在 priceStats 里
 *   "hasValidPriceData":false,
 *   "pic":"https://playerhub.df.qq.com/playerhub/60004/object/gun/ammo/6.8x51.png",
 *   "prePic":"…/p_6.8x51.png",
 *   "secondClassCN":"6.8x51mm",      // 口径分组
 *   "primaryClass":"ammo",
 *   "ammoDetail":{"penetrationLevel":6,"armorHarmLevel":"高","harmRatio":100},
 *   "priceStats":{"avgPrice":0,"dataPoints":0,"latestPrice":0,"priceChangePercent":0},
 *   "updatedAt":"2026-09-19T15:54:40.384Z"
 * }]}
 * ```
 *
 * ### 一次真实误判的记录
 *
 * 最初把价格读成 `priceStats.latestPrice`。实测发现该接口的价格在**顶层 `avgPrice`**，
 * 而 `priceStats` 里全是 0 —— 两者**同时为 0 时看不出差别**，
 * 直到发现"全部条目都是暂无成交数据"这个反常现象才回头查原始响应。
 *
 * **教训：字段路径必须照抄实测响应，不能从"看起来更专业的嵌套结构"去推断。**
 *
 * ### 红线
 *
 * `0` / 负数 / 缺失一律读成 `null`（无数据），绝不产生"价格是 0"。
 */
object AmmoPriceParser {

    fun parse(data: Json?): List<AmmoPrice>? {
        val bullets = data?.arr("bullets")?.items ?: return null
        return bullets.mapNotNull { item ->
            val id = item.str("objectID")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = item.str("objectName")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val detail = item.obj("ammoDetail")
            val stats = item.obj("priceStats")
            val dataPoints = stats?.int("dataPoints")

            // ★ 价格优先取**顶层 avgPrice**（实测位置）；
            //   priceStats 作为兜底，以防对方将来把字段挪进嵌套结构。
            val price = item.positiveLong("avgPrice")
                ?: stats?.positiveLong("latestPrice")
                ?: stats?.positiveLong("avgPrice")

            // ★★ 涨跌幅只在**有样本**时才计算。
            //
            //   接口在无成交时返回 `priceChangePercent: 0`，但那不是"价格没变"，
            //   而是"无从比较"。原先直接取这个 0，界面就显示成「↑ 0.0%」——
            //   等于替数据源编了一个"持平"的结论。这是数据展示上的红线问题。
            val change = if (price == null || (dataPoints ?: 0) <= 0) null else {
                item.doubleOrNull("priceChangePercent")
                    ?: stats?.doubleOrNull("priceChangePercent")
            }?.takeIf { it != 0.0 || (dataPoints ?: 0) > 0 }

            // 顶层 avgPrice 为 0 但样本数 > 0 ⇒ 数据源把价格管道停了
            // （实测：updatedAt 停在 2026-09-19，全部 dataPoints=0）
            val staleBySource = (dataPoints ?: 0) <= 0

            AmmoPrice(
                objectId = id,
                name = name,
                caliber = item.str("secondClassCN")?.takeIf { it.isNotBlank() },
                // grade > 0：接口用 0 表示"无稀有度"，那不是 ★0
                grade = item.int("grade")?.takeIf { it > 0 },
                latestPrice = price,
                changePercent = change,
                // ★ 字段缺失时用"有没有价格"兜底，而不是默认 false ——
                //   默认 false 会凭空宣称"暂无成交数据"
                hasValidPriceData = item.boolOrNull("hasValidPriceData") ?: (price != null),
                source = DeltaForceApi.ref,
                iconUrl = item.str("prePic")?.takeIf { it.isNotBlank() }
                    ?: item.str("pic")?.takeIf { it.isNotBlank() },
                // ★ 用户问的"一级还是二级"= **穿透等级**，不是稀有度
                penetrationLevel = detail?.int("penetrationLevel"),
                armorHarmLevel = detail?.str("armorHarmLevel")?.takeIf { it.isNotBlank() },
                caliberGroup = item.str("secondClassCN")?.takeIf { it.isNotBlank() },
                dataPoints = dataPoints,
            )
        }
    }
}
