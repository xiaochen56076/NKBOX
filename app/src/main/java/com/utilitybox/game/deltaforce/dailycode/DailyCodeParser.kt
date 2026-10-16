package com.utilitybox.game.deltaforce.dailycode

import com.utilitybox.core.json.Json
import com.utilitybox.domain.model.AppError
import com.utilitybox.domain.model.DailyCode
import com.utilitybox.domain.model.DailyCodeState
import com.utilitybox.domain.model.DataSourceRef
import java.time.LocalDate

/**
 * 今日密码的响应解析与状态判定。
 *
 * ★★ 这是本项目红线最集中的一处，务必看清：
 *
 * 1. **接口不提供日期字段**（P01 用真实请求实测坐实）。
 *    实测响应：`{"list":[{"mapID":1,"mapName":"零号大坝","secret":"0144"}, ...]}`
 *    —— 字段仅 `mapID` / `mapName` / `secret`，**没有任何日期、时间戳或有效期**。
 *
 * 2. 因此「当天有效 / 过期缓存」**无法由接口判定**，只能由**本机抓取日期**推断。
 *    UI 必须如实写明这一判定依据。
 *
 * 3. **绝不允许**把昨日的密码当作今日密码展示，也**绝不允许**编造一个有效日期。
 *
 * 本对象**不依赖 Android 框架**，可直接单测。
 */
object DailyCodeParser {

    /** 解析 `data` 里的 `list`。结构不符返回 null（由调用方归为格式错误）。 */
    fun parse(data: Json): List<DailyCode>? {
        val list = data.arr("list") ?: return null
        val out = ArrayList<DailyCode>(list.items.size)
        for (item in list.items) {
            if (item !is Json.Obj) continue
            val mapId = item.str("mapID").orEmpty()
            val mapName = item.str("mapName").orEmpty().trim()
            // 实测字段名是 secret；同时兼容 password 以防对方改版
            val code = (item.str("secret").orEmpty().ifBlank { item.str("password").orEmpty() }).trim()
            if (mapName.isEmpty() && mapId.isEmpty()) continue
            out += DailyCode(mapId = mapId, mapName = mapName, code = code)
        }
        return out
    }

    /**
     * 由「抓取日期」与「今天」判定状态。**纯函数，可单测。**
     *
     * 判定规则（必须与 UI 文案一致）：
     *  - 抓取日 == 今天  -> [DailyCodeState.Today]
     *  - 抓取日 != 今天  -> [DailyCodeState.Stale]（携带**原始抓取日期**）
     *
     * @param codes     已解析出的密码列表；null/空表示本次没有可用数据
     * @param fetchedAt 本次抓取的本机日期
     * @param today     今天（传入而非内部取，便于单测）
     */
    fun classify(
        codes: List<DailyCode>?,
        fetchedAt: LocalDate,
        today: LocalDate,
        source: DataSourceRef,
        updatedAtLabel: String,
        isMock: Boolean = false,
    ): DailyCodeState {
        if (codes == null || codes.isEmpty()) return DailyCodeState.NoDataForToday

        return if (fetchedAt == today) {
            DailyCodeState.Today(
                codes = codes,
                source = source,
                updatedAtLabel = updatedAtLabel,
                isMock = isMock,
            )
        } else {
            DailyCodeState.Stale(
                codes = codes,
                fetchedDate = fetchedAt,
                source = source,
                isMock = isMock,
            )
        }
    }

    /** 请求失败时统一走这里，避免各处自行 new */
    fun failed(error: AppError): DailyCodeState = DailyCodeState.Failed(error)
}
