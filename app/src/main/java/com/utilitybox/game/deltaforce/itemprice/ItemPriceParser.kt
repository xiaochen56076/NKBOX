package com.utilitybox.game.deltaforce.itemprice

import com.utilitybox.core.json.Json
import com.utilitybox.domain.model.ItemSummary
import com.utilitybox.domain.model.PricePoint
import com.utilitybox.domain.model.PriceTrend
import java.time.LocalDate

/**
 * 物品物价的响应解析。
 *
 * 实测字段（P01 用真实请求确认）：
 *  - 搜索 `/object/search`：`{limit,page,total,list:[{objectID,objectName,shortName,grade,
 *    avgPrice,pic,prePic,primaryClass,secondClass,secondClassCN,updatedAt,...}]}`
 *  - 当前价 `/object/price/ams/latest`：`{dataMap:{"<id>":价}, items:[{objectID,avgPrice}], source}`
 *  - 历史 `/object/price/ams/history/v1`：`{granularity, history:[{date,avgPrice,count}], source}`
 *    —— `date` 形如 `"2026-10-08"`，`count` 是**当日真实样本数**
 *
 * ★ 红线：`avgPrice` 为 0 或缺失表示**无成交数据**，不是"价格是 0"。
 *   解析层返回 `null` 而非 `0`，从源头杜绝把"没有数据"显示成"0 元"。
 *
 * 本对象**不依赖 Android 框架**，可直接单测。
 */
object ItemPriceParser {

    /** 解析搜索结果的 `list`；结构不符返回 null */
    fun parseSearch(data: Json): List<ItemSummary>? {
        val list = data.arr("list") ?: return null
        val out = ArrayList<ItemSummary>(list.items.size)
        for (item in list.items) {
            if (item !is Json.Obj) continue
            val id = item.str("objectID").orEmpty()
            val name = item.str("objectName").orEmpty().trim()
            if (id.isEmpty() && name.isEmpty()) continue
            out += ItemSummary(
                objectId = id,
                name = name,
                shortName = item.str("shortName")?.takeIf { it.isNotBlank() },
                // ★ grade > 0：接口用 0 表示"无稀有度"，那不是"★0"
                grade = item.int("grade")?.takeIf { it > 0 },
                category = item.str("secondClassCN")?.takeIf { it.isNotBlank() }
                    ?: item.str("secondClass")?.takeIf { it.isNotBlank() },
                // positiveLong：0 / 负数 / 缺失 一律 -> null
                avgPrice = item.positiveLong("avgPrice"),
                // ★ 图片来自腾讯官方图床；列表用 prePic（缩略图）省流量
                iconUrl = item.str("prePic")?.takeIf { it.isNotBlank() }
                    ?: item.str("pic")?.takeIf { it.isNotBlank() },
                primaryClass = item.str("primaryClass")?.takeIf { it.isNotBlank() },
                categoryCn = item.str("secondClassCN")?.takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    /** 解析当前价；取不到返回 null（**不返回 0**） */
    fun parseLatestPrice(data: Json, objectId: String): Long? {
        // 优先用 dataMap（实测结构），缺失时回退到 items 数组
        data.obj("dataMap")?.let { map ->
            map.fields[objectId]?.let { node ->
                val v = when (node) {
                    is Json.Num -> node.value.toLong()
                    is Json.Str -> node.value.trim().toLongOrNull()
                    else -> null
                }
                return if (v != null && v > 0) v else null
            }
        }
        val items = data.arr("items") ?: return null
        for (item in items.items) {
            if (item !is Json.Obj) continue
            if (item.str("objectID") == objectId) return item.positiveLong("avgPrice")
        }
        return null
    }

    /** 数据源标识（实测响应里有 `source: "ams"`） */
    fun parseSource(data: Json): String? = data.str("source")?.takeIf { it.isNotBlank() }

    /**
     * 解析历史价格。
     *
     * ★ 缺失点必须保留为 `avgPrice = null`，图表据此**断开**而不是连过去。
     *   插值/平滑成假曲线是明确禁止的（PROJECT_RULES.md 第 3.7 节）。
     */
    fun parseHistory(data: Json): List<PricePoint>? {
        val arr = data.arr("history") ?: return null
        val out = ArrayList<PricePoint>(arr.items.size)
        for (item in arr.items) {
            if (item !is Json.Obj) continue
            val date = parseDate(item.str("date").orEmpty()) ?: continue
            val count = item.longOrZero("count").coerceAtLeast(0L).toInt()
            // count == 0 表示当日无成交；此时即便接口给了价格也不可信，一律视为无数据
            val price = if (count == 0) null else item.positiveLong("avgPrice")
            out += PricePoint(date = date, avgPrice = price, sampleCount = count)
        }
        return out
    }

    /** 解析 `yyyy-MM-dd`；失败返回 null（该点直接跳过，不猜测） */
    fun parseDate(s: String): LocalDate? = try {
        if (s.length >= 10) LocalDate.parse(s.substring(0, 10)) else null
    } catch (e: Exception) {
        null
    }

    /**
     * 由历史点计算趋势。**只在有效点足够时才给结论**，否则 [PriceTrend.UNKNOWN]。
     * 不允许把"数据不足"伪装成"持平"。
     */
    fun trendOf(points: List<PricePoint>): PriceTrend {
        val valid = points.mapNotNull { it.avgPrice }
        if (valid.size < 2) return PriceTrend.UNKNOWN
        val first = valid.first()
        val last = valid.last()
        return when {
            last > first -> PriceTrend.UP
            last < first -> PriceTrend.DOWN
            else -> PriceTrend.FLAT
        }
    }
}
