# UtilityBox 数据源说明与风险清单

> 阶段：P00 ｜ 日期：2026-10-14 ｜ 状态：**实测已完成**
> 本文件是 P00 交付物第 9 项「数据源风险清单」
> **所有结论均基于本机实测证据或参考仓库源码，未经证实的部分已显式标注。**

---

## 0. ★ 真实调用实测记录（2026-10-14，使用用户提供的 API Key）

以下结论**不是文档推断，是真实 HTTP 调用结果**。API Key 仅通过环境变量传入，未写入任何文件。

| # | 端点 | HTTP | code | 结论 |
|---|---|---|---|---|
| 1 | `/api/v1/df/tools/dailykeyword` | 200 | 0 | ✅ **可用**，返回 6 张地图密码 |
| 2 | `/api/v1/df/object/search?objectName=钛` | 200 | 0 | ✅ **可用**，`total=4`；`钢` 返回 28 条 |
| 3 | `/api/v1/df/object/price/ams/latest?id=13020000524` | 200 | 0 | ✅ **可用**，`avgPrice=49444` |
| 4 | `/api/v1/df/object/price/ams/history/v1?id=&days=7` | 200 | 0 | ✅ **可用**，7 天完整数据，见下 |
| 5 | `/api/v1/df/place/material/price` | 200 | 0 | ✅ **可用**，`materials[]` 含 `priceSource: "ams"` 与 `timestamp` |
| 6 | `/api/v1/df/object/ammo?days=7` | 200 | 0 | ✅ **可用**，113 项弹药，**含 `hasValidPriceData: false` 真实样本** |
| 7 | `/api/v1/df/person/friendinfo` | **402** | 402 | 🔴 **端点存在但收费**：「余额不足，本次请求需要 1 积分」 |
| 8 | `/api/v1/df/gamesafe/online` | 400 | 400 | 🟠 **需 `X-Framework-Token`**：「缺少 X-Framework-Token 请求头」 |
| 9 | `/api/v1/df/person/friends` / `friend` / `friendlist` | **404** | — | 🔴 **不存在**（`404 page not found`） |
| 10 | `/api/v1/df/friend/status` / `/api/v1/df/social/friends` | **404** | — | 🔴 **不存在** |
| 11 | `/api/v1/user/{info,statistics,points,quota,balance,credits}` | **404** | — | 🔴 全部不存在 → **没有余额查询接口** |
| 12 | `/api/v1/df/user/statistics` | **404** | — | 🔴 不存在 |

### 0.1 最重要的新发现：本 API 是**积分制收费**

- **免费**：今日密码、物品搜索、当前价、价格历史、材料价格、弹药价格
- **付费（1 积分/次）**：`/df/person/friendinfo`
- **本账号当前积分为 0**，因此 `friendinfo` **无法实测**
- 服务端**不提供余额查询接口**

**影响**：
1. 三个核心功能中的**今日密码与物品物价完全免费**，可放心用于首期交付。
2. **好友状态若要做，是持续付费功能**，且本账号积分不足 → 与用户裁决的「Q1 方案 A」完全一致。
3. 未来若接入任何个人数据类端点，必须在架构中体现"积分成本"，并提供明确的降级与提示。

### 0.2 实测到的真实字段（修正文档记录）

**今日密码**（★ 确认**无日期字段**）
```json
{"code":0,"message":"成功","data":{"list":[
  {"mapID":1,"mapName":"零号大坝","secret":"0144"},
  {"mapID":2,"mapName":"长弓溪谷","secret":"8288"},
  {"mapID":3,"mapName":"巴克什","secret":"4400"},
  {"mapID":4,"mapName":"航天基地","secret":"0201"},
  {"mapID":5,"mapName":"潮汐监狱","secret":"2643"},
  {"mapID":6,"mapName":"AZ3","secret":"2520"}
]}}
```
> 6 张图，字段仅 `mapID` / `mapName` / `secret`。**没有任何日期、时间戳或有效期字段。**
> → **ISSUE-002 坐实**：无法从接口得知密码属于哪一天。

**物品搜索**（字段比 `GameMate/HANDOVER.md` 记录**更丰富**）
```json
{"limit":100,"page":1,"total":4,"list":[{
  "objectID":13020000524,"objectName":"M250钛金长枪管","shortName":"...",
  "grade":..., "avgPrice":49258, "updatedAt":"2026-09-19T15:54:40.384Z",
  "pic":"...","prePic":"...","primaryClass":"...","secondClass":"...","secondClassCN":"...",
  "gameName":"...","id":...,"length":...,"width":...,"weight":...,"accDetail":{...}
}]}
```
> 新增发现：**`updatedAt`**（价格更新时间，ISO8601）、**`pic`/`prePic`**（图片 URL）、
> `weight`/`width`/`length`（尺寸）、`primaryClass`/`secondClass`/`secondClassCN`（分类）。
> 另有 **`limit` / `page` / `total`** 分页字段（文档未记录 `limit`/`page`）。

**价格历史**（真实数据密度实测，7 天 7 个点，无缺失）
```json
{"objectID":13020000524,"granularity":"day","source":"ams","method":"dfm/object.price.recent",
 "history":[
  {"avgPrice":49268,"count":371,"date":"2026-10-08"},
  {"avgPrice":49268,"count":356,"date":"2026-10-09"},
  {"avgPrice":49322,"count":359,"date":"2026-10-10"},
  {"avgPrice":53894,"count":357,"date":"2026-10-11"},
  {"avgPrice":49248,"count":359,"date":"2026-10-12"},
  {"avgPrice":49270,"count":339,"date":"2026-10-13"},
  {"avgPrice":49288,"count":130,"date":"2026-10-14"}
]}
```
> ✅ **`count` 是每日真实样本数**（300~370 量级），可用来判断数据可信度；
> 当日（10-14）`count=130` 明显偏低，说明**当天数据尚在累积** —— 这是一个重要的 UI 提示机会。
> 也说明缺失点确实可能出现，折线**必须**留空断线。

**当前价**
```json
{"objectIDs":[13020000524],"dataMap":{"13020000524":49444},
 "items":[{"objectID":13020000524,"avgPrice":49444}],
 "source":"ams","amsSerial":"AMS-DFM-1014125503-HoaWCb-661959-316969"}
```
> 与搜索返回的 `avgPrice=49258`（`updatedAt: 2026-09-19`）**不一致** → 证明
> **搜索接口的价格是快照、可能过期**，实时价必须走 `latest`。UI 应显示各自的时间。

**弹药价格**（★ 恰好抓到 `hasValidPriceData: false` 的真实样本）
```json
{"ammoDetail":{"armorHarmLevel":"高","harmRatio":100,"penetrationLevel":6},
 "avgPrice":0,"grade":6,"hasValidPriceData":false,"objectID":37150600001,
 "objectName":"6.8x51mm AP",
 "priceStats":{"avgPrice":0,"dataPoints":0,"latestPrice":0,"maxPrice":0,"minPrice":0,
               "priceChange":0,"priceChangePercent":0,"queryDays":7},
 "pic":"https://playerhub.df.qq.com/playerhub/60004/object/gun/ammo/6.8x51.png"}
```
> ⚠️ **这是 `PROJECT_RULES.md` 红线 §3 的活生生案例**：
> `avgPrice: 0` 且 `hasValidPriceData: false` —— **0 不是价格，是"没有数据"**。
> UI 必须显示「暂无成交数据」，**绝不能显示 0**。
> 另外 `priceStats.dataPoints` 是判断数据是否存在的**最直接字段**。
> 图片来自腾讯官方 CDN `playerhub.df.qq.com`。

**材料价格**
```json
{"materials":[{"objectID":15010010004,"objectName":"含氟牙膏","price":2263,
               "priceSource":"ams","timestamp":1791932400}]}
```
> `timestamp` 是 Unix 秒（`1791932400` = 2026-10-14 前后），**可直接用于"更新时间"展示**。
> `priceSource: "ams"` 应作为**数据来源标识**显示给用户。

---

## 1. 核心数据源

| 项 | 值 |
|---|---|
## 鉴权与 API Key 的存放方式（★ P06 用户裁决后更新）

| 项 | 内容 |
|---|---|
| 服务地址 | `https://delta-test-api.shallow.ink` |
| 鉴权 | 请求头 `X-API-Key`；个人数据类另需 `X-Framework-Token`（本期不用） |
| API Key 申请 | `https://delta-test.shallow.ink/developer/api-keys`（需登录） |

### Key 从哪来（优先级）

1. **用户在设置页输入**（`设置` → `API Key`）—— 主要方式
2. 构建期注入：环境变量 `UBOX_API_KEY` 或 `local.properties` 的 `ubox.apiKey` —— 开发/CI 便利
3. 都没有 = 未配置状态，功能页如实提示并给出行动指引

**用户裁决原话**：「关于数据方面的 api 就采用设置里面可以输入那种」。
这样任何人拿到 APK 都能填自己的 Key 使用，不必重新构建。

### Key 存到哪里

- **应用私有的 SharedPreferences + Android Keystore (AES/GCM) 加密**
- 实测确认：设备上存储形态为 `Base64(iv):Base64(cipher)`，**不含明文**
- Keystore 不可用时**降级为明文但如实告知**（设置页显示"未加密"），不静默降级
- 存在本机，不上传，不打进 APK

### 红线口径的澄清（重要）

原红线写的是「API Key 不得写入任何文件」。
经用户裁决引入"设置页输入"后，口径明确为：

- ❌ **禁止**：写进源码、文档、日志、打进 APK、提交到版本库
- ✅ **允许**：由用户输入后存进**应用私有存储**（等同登录态，其他应用读不到）

`.gitignore` 已排除 `local.properties`，且已核查项目内无 Key 字面值。

### ★ 实测踩到的一处设计缺陷（已修复）

最初「本机未配置 Key」与「服务端拒绝了 Key」**共用 `AppError.Unauthorized`**，
导致界面自相矛盾：设置页显示"已配置 · sk-FAK…234"，功能页却说"未配置 API Key"。

**修复**：拆成两个类型，因为用户要采取的行动不同 ——
- `AppError.ApiKeyMissing` → 去填一个 Key
- `AppError.Unauthorized` → Key 被拒，需要检查/更换

**教训**：**错误类型的粒度必须匹配用户可采取的行动**。把两种不同的用户处境
挤进一个类型里，界面就必然会在某一侧说谎。


| 类型 | **社区第三方接口，非腾讯官方** |
| 鉴权 | 请求头 `X-API-Key`；个人数据类另需 `X-Framework-Token` |
| 响应信封 | `{"code":0,"message":"成功","data":{...}}`，`code == 0` 为成功 |
| API Key 申请 | `https://delta-test.shallow.ink/developer/api-keys`（需登录） |
| 开发者文档 | `https://delta-force.apifox.cn` |
| 运营者 | 文档署名「浅巷墨黎、Admilk、mapleleaf」 |
| 服务条款 | ⚠️ **未找到公开 ToS**，P01 需确认 |
| 数据来源 | ⚠️ **未确认**（疑似社区上报 / OCR 识别 / 官方助手数据） |

### 1.1 证据来源（本机实测）

| 证据 | 位置 | 可信度 |
|---|---|---|
| 接口清单 + 真实响应字段 | `GameMate/HANDOVER.md` 第四节（标注「全部实测过」） | 高（同一台机器、同一开发者实测） |
| 完整接口文档 v2.4.0 | `_refs/yunzai-df/Delta-Force-API-DOCS.md`（203 KB，7834 行） | 高（文档） |
| 端点调用代码 | `_refs/astrbot-df/core/client.py`（1190 行） | 高（源码） |
| 端点调用代码 | `_refs/yunzai-df/components/Code.js` | 高（源码） |

> **教训记录（写入 `KNOWN_ISSUES.md`）**：
> `astrbot-df/main.py` 的 `_gamesafe_record_lines()` 含有 `"online": "在线"`、`"nickname": "昵称"` 等映射，
> **这只是通用字段渲染用的标签表，不是实测响应 schema**。
> 曾一度据此误判"存在好友在线状态接口"。**不要把展示层标签当成接口证据。**

---

## 2. 逐功能可用性结论

### 2.1 今日密码 —— ✅ 已验证可用

| 项 | 内容 |
|---|---|
| 端点 | `GET /api/v1/df/tools/dailykeyword` |
| 响应 | `data.list[]{ mapID, mapName, secret }` |
| 鉴权 | API Key（**不需要登录**） |
| 状态 | **已验证可用**（GameMate 已实测出数据） |
| 限制 | ⚠️ **响应中未记录任何日期字段** |

**风险**
| 风险 | 等级 | 说明与对策 |
|---|---|---|
| 无日期字段 | **高** | 设计稿要求区分「当天有效 / 过期缓存」。若接口确实不给日期，则无法严格判定。**对策**：P01 实测确认；若无日期字段，改为「抓取日期 + 如实说明判定方式」，绝不用猜测的日期冒充 |
| 数据源非官方 | 中 | 密码错误会导致玩家损失。**对策**：UI 标注来源，提示以游戏内为准 |
| 服务停服 | 中 | **对策**：数据源适配器可整体替换；缓存可离线查看 |

### 2.2 物品物价 —— ✅ 已验证可用（数据能力最完整）

| 用途 | 端点 | 关键字段 | 状态 |
|---|---|---|---|
| 物品搜索 | `GET /api/v1/df/object/search?objectName=` | `list[]{objectID, objectName, shortName, grade, avgPrice, desc, secondClassCN}`, `total` | 已验证 |
| 当前价格 | `GET /api/v1/df/object/price/ams/latest?id=` | `dataMap{"<objectID>":价格}`, `items[]{objectID, avgPrice}` | 已验证 |
| 价格历史 | `GET /api/v1/df/object/price/ams/history/v1?id=&days=7` | `granularity`, `history[]{date, avgPrice, count}` | 已验证 |
| 材料价格 | `GET /api/v1/df/place/material/price` | `materials[]{objectName, price, priceSource, timestamp}` | 已验证 |
| 弹药价格 | `GET /api/v1/df/object/ammo?days=7` | `bullets[]{..., priceStats{latestPrice, avgPrice, priceChange, priceChangePercent}, hasValidPriceData}` | 已验证 |
| 制造利润 | `GET /api/v1/df/place/profit/rank` | `items[]{rank, objectName, costPrice, salePrice, fee, hourProfit, ...}` | 已验证 |

**风险**
| 风险 | 等级 | 说明与对策 |
|---|---|---|
| 无成交数据 | 中 | `hasValidPriceData: false`、`avgPrice: 0` 是正常情况。**对策**：UI 必须显示「暂无成交数据」，**不得把 0 显示成真实价格** |
| 历史点稀疏 | 中 | `count` 为 0 的日期是真实存在的。**对策**：折线图留空，不插值 |
| 价格延迟 | 低 | 社区上报价格有延迟。**对策**：显示 `timestamp` 与来源 |
| 数据源非官方 | 中 | **对策**：标注来源，声明仅供参考 |

### 2.3 好友在线状态 —— ❌ 无可用数据源（**阻塞级，已实测确认**）

这是 P00 最重要的发现。设计稿 P05 要求「好友列表 + 游戏中/在线/离线/未知」四态排序，
但经**真实调用探测**（2026-10-14，见 §0）+ 全文档 grep + 全参考仓库 grep，**没有任何端点能支撑该需求**。

逐一排除（**已实测**）：

| 候选 | 实测结果 | 为什么不行 |
|---|---|---|
| `GET /df/person/friendinfo` | **HTTP 402「余额不足，本次请求需要 1 积分」** | 端点**存在**，但①**收费**（1 积分/次，本账号积分为 0）；② 按文档与参考实现，它只返回 `charac_name`（昵称）与 `picurl`（头像），**无在线状态字段**；③ 需先知道好友 `openid` |
| `GET /df/person/friends` | **404 page not found** | **不存在** |
| `GET /df/person/friend` | **404** | 不存在 |
| `GET /df/person/friendlist` | **404** | 不存在 |
| `GET /df/friend/status` | **404** | 不存在 |
| `GET /df/social/friends` | **404** | 不存在 |
| `GET /api/v1/df/gamesafe/online` | **HTTP 400「缺少 X-Framework-Token 请求头」** | ① 需 **gamesafe（微信）** token；② 只能查**自己账号**，非好友列表；③ 只有布尔值，**无"游戏中"** |
| 周报 `reportDm.wbn.friends[].Friendopenid` | 未实测（属统计字段） | 属**周报统计**，非实时；需 `showExtra=true`（响应慢）；需登录 token |
| `weeklyRecord` 的 `friends` 字段 | 未实测 | 是"队友"非"好友"，且是战绩数据非状态 |
| `GET /df/gamesafe/devices` | 未实测 | 与好友无关 |

**结论**：`好友列表` 与 `游戏中` 两个关键要素**在现有数据源中完全缺失**，**且已通过 6 个 404 探测排除了未文档化端点的可能性**。

> ⚠️ 即使付费解锁 `friendinfo`，**仍然缺少"好友列表"这一环**——
> 不知道好友的 `openid`，就无法批量查询状态。周报 `wbn.friends` 只覆盖当周一起玩过的人，
> **不是好友列表**，且不是实时状态。因此**付费也解决不了这个需求**。

**处置方案**（用户已裁决）：✅ **采用方案 A（降级实现）** —— 保留模块、四态模型与排序逻辑（可单测），
页面如实显示「当前无可用数据源」。数据源就绪后即可点亮。

> ⚠️ **无论如何处置，都不得编造好友状态。** 这是红线第 1 条。
> `UNKNOWN` 状态绝不能自动降级为 `OFFLINE`。

---

## 3. 合规与法律风险

> ★ 本节在 P01 前置调研后**大幅加强**。以下结论来自对 shallow.ink 前端 chunk 的逆向还原、
> 社区 SDK 源码核验、以及对官方文档的查证。**未验证的部分已显式标注。**

### 3.1 🔴 服务运营方：匿名、不可追责、环境带 `test`

| 项 | 实测发现 |
|---|---|
| 运营主体 | **匿名**。`/about/team` 页面原文写着「核心开发团队（**占位勿怪**）」，无名字、无实体 |
| 可追责性 | ❌ **无 GitHub 组织、无开源代码、无可追责对象**（`q=shallow.ink` 在 GitHub 上零仓库） |
| 环境标识 | 域名带 **`test`**（web 与 api 主机均为 `delta-test*`） |
| 公开文档 | ❌ **无**。`/docs`、`/openapi.json`、`/redoc`、`/swagger`、`/developer/docs` **全部 404** |
| 通知渠道 | ❌ 无。我们的端点清单**完全来自自行逆向**，对方改版**没有任何通知渠道** |
| 站内自称 | Delta Nexus / 三角洲工具站 / 三角洲行动数据平台 |

### 3.2 🔴 开发者协议：只允许「非商业」用途 —— **商业化前必须书面授权**

站内存在真实路由 `/developer/api-keys`、`/developer/permissions`、`/developer/subscription`。
《开发者调用协议》原文要点：

- 鉴权 = `api_key` + `client_id`；**"请严格基于你当前订阅阶层的频率限制 (QPS) 进行调用"**
- 禁止：恶意并发 / 接口轰炸、代理池规避风控、**直接倒卖基础数据**、外挂用途
- 允许用例仅列出：**社群机器人 / 二次开发 / 非商业研究**

> ⚠️ **最不利的一条**：协议**没有**为"面向公众分发的 Android App"背书。
> "禁止倒卖基础数据"能否覆盖一个**免费工具 App**，**条款未澄清**。
> 具体 QPS 与价格档位需登录才能看，**我们尚未核实**。

### 3.3 🔴 数据采集手段超出"公开 Web 接口"范畴

`/about/data` 页面原文：数据"均基于公开可获取的游戏接口或**多节点爬取**…同步延迟通常在 **5 至 15 分钟**"。

但从其管理端路由常量还原出三个**硬证据**：

| 路由常量 | 含义 |
|---|---|
| `ADMIN_UNPACKED_DATA` | **解包**游戏资源 |
| `ADMIN_OCR_TASKS` | **OCR** 识别 |
| `ADMIN_TAPTAP_SESSIONS` | TapTap 会话（第三方平台账号登录态） |

> ⚠️ 另：个人数据靠"**扫码或账密绑定**游戏角色"。
> **"账密绑定" = 用户把游戏账号密码交给匿名第三方** —— 这是本项目**必须避免**的交互模式。

### 3.4 ✅ 一条利好：今日密码很可能代理的是腾讯官方 AMS 接口

**血缘推断（⚠️ 强关联推断，未端到端实测）**：
- `df-api.apifox.cn` 的"后端控制台"**直接链接 `df.shallow.ink`**
- 其"每日密码 新版"文档列出的 server 包含 **`https://comm.ams.game.qq.com/ide/`（腾讯官方 AMS 网关）** 与 `https://ams.shallow.ink/ide/`
- 其示例响应 `jData.data.data.list[]{mapID,mapName,secret}` 与我们**实测结果逐字一致**

→ **结论：今日密码功能的合规性优于物价与个人数据**，因为它代理的是官方 Web 活动接口，而非游戏客户端协议。

**且机制上更可靠**：今日密码官方**每天 0:05 更新后全天一致** → 应按日失效、每天只请求 1 次。

### 3.5 🔴 断供风险有明确先例

| 先例 | 证据 |
|---|---|
| `orzice/DeltaForcePrice`（90★） | README 原文：**"因业务调整（哈夫克的重拳），将于 2026年1月11日 停止数据更新"** —— 生命周期约 **4 个月** |
| 同上 | README 另称"**gitee 被封**" |
| `l1n6yun/DeltaForceAPI` | README 载明官方侧 `iChartId=319386` 持续返回"服务不可用~" |

→ **数据源生命周期可能只有数月。可整体替换是硬需求，不是架构洁癖。**

### 3.6 🔴 许可证红旗（**必须遵守**）

| 项目 | License | 对本项目的结论 |
|---|---|---|
| `coolxi-tech/deltaforce`（256★） | **CC BY-NC-SA 4.0（禁商用）** | ⛔ **商业 App 直接排除**，禁止复制其任何代码或数据 |
| `l1n6yun/DeltaForceAPI` | **MIT** | ✅ 可用（但仍需自建登录流程） |
| `HonkerBit/DeltaForcekey-Server` | 自建 Express；README 明写"供 iOS/Android 小组件或 app 使用" | ✅ **最适合照抄的架构模板** |
| 数据帝 / 小涛查（orzice） | **无 License** | ⚠️ 无授权即默认保留全部权利，**不可直接使用** |
| `coolxi-tech/deltaforce_map_api` | README 原文自称"**挂狗地图** API"，WebSocket 读局内玩家位置/血量 | ⛔⛔ **外挂基础设施，必须拉黑，绝不接触** |

> ⚠️ **同时提醒**：参考工程 `GameMate` 的 `assets/delta/` 数据来自**社区开源项目（AGPL-3.0）**。
> **UtilityBox 若复用任何 AGPL 代码或数据，必须做同等合规处理；否则不得复用。**
> P00 阶段**未**复用任何此类资产，**后续也不应复用**。

### 3.7 替代数据源（可整体替换，这是架构要求）

| 候选 | License | 备注 |
|---|---|---|
| `https://orzice.com/work/`（数据帝） | ⚠️ **无 License** | 有完整 OpenAPI：`GET /v1/sjz_api/map_pwd?token=`；响应结构 `data:{a:['0639','2025-12-26'],...}` **与 shallow.ink 完全不同** |
| `l1n6yun/DeltaForceAPI` | **MIT** | 需自建 QQ/微信扫码登录，拿用户自持 openid + access_token |
| `HonkerBit/DeltaForcekey-Server` | 自建 | 架构模板 |
| `shushu.fan` | 未核验 | — |

> ⚠️ **重要架构含义**：数据帝的今日密码响应是 `['密码', '日期']` 形式，
> **而 shallow.ink 是 `{mapID, mapName, secret}` 且无日期** ——
> **结构不同，绝不能假设可以统一解析。** 这再次证明 `domain/datasource` 抽象层的必要性。

### 3.8 官方开放能力：❌ 没有（请停止投入调研）

| 证据 | 内容 |
|---|---|
| 最强反证 | 那份自称整理"**官方接口**"的社区文档，原文要求"**Openid 和 Accesstoken 请自行抓包获取**"、"**不提供登陆的接口和流程**"。官方若开放平台，开发者就不必自己抓包 |
| 官方小程序 | 功能仅：制造提醒 / 百科 / 战绩净收益 / 价格走势 / 每日密码 —— **没有好友** |
| 腾讯关系链能力 | QQ 关系链 / 微信小游戏状态服务 / WeGame rail SDK **全部要求"应用跑在腾讯平台内"**，**不适用于三角洲行动这类原生游戏** |
| `gamesafe.qq.com` | 只服务账号本人（防盗号功能，配套"强制下线"） |

### 3.9 其他被排除的"看似可用物"（避免误用）

| 物 | 为什么不是好友在线状态 |
|---|---|
| 周报「好友昵称与头像 + TOP10 + 最佳/最差队友」 | 由**对局记录回溯**出的静态快照，**无在线状态字段** |
| `qq.getFriendCloudStorage()` | **QQ 小游戏**专用，只返回"也玩该小游戏"的好友 |
| `GameServerManager.getFriendsStateData` | **微信小游戏**专用 |
| WeGame rail SDK friends | 上架 WeGame 的**开发者**专用（文档页仅返回 JS 空壳，**未读到内容**，仅为 URL 级线索） |
| NGA 帖《现在有什么方法不上线查看好友在线状态？》 | ⚠️ 返回 403，**未能读到** —— 但这说明**玩家自己也在找方法**，侧面印证无公开途径 |

### 3.10 客户端落地要求（由以上风险推导，已并入架构）

| # | 要求 |
|---|---|
| 1 | **绝不硬编码共享 API Key**（会被反编译提取并打爆 QPS 连累封禁）→ 走 **BYOK（Bring Your Own Key）+ Android Keystore** |
| 2 | **不要在服务端代持用户 access_token** |
| 3 | 客户端做**去重 + 指数退避 + single-flight** 以遵守对方 QPS |
| 4 | **禁止 Cookie / 账密登录**（`/api/v1/login/qq/ck` 需交出完整 QQ 会话凭据） |
| 5 | 降级链：主源 → 备源 → 本地缓存 → **明确告知不可用**。**任何一环都不允许伪造数据** |
| 6 | 每个数据都带 `providerName` / `providerUrl` / `fetchedAt` 一路带到 UI |
| 7 | 信封差异（`{code,message,data}` vs `?token=` vs `{ret,iRet,sMsg,jData}`）全部封在各自 DataSource 内 |
| 8 | 今日密码按日失效、每天只请求 1 次；物价明示"延迟 5–15 分钟，以游戏内为准" |
| 9 | **商业化前必须解决 ToS 的"非商业"措辞（取得书面授权）**，并确保 CC BY-NC-SA 代码不进商业包 |

---

## 4. 数据源抽象设计（应对"数据源可替换"要求）

设计稿第三层扩展要求：更换数据源时**只改适配器，不重写页面与分析逻辑**。

```kotlin
// domain/datasource —— 只定义契约，不含任何具体实现
interface DailyCodeDataSource {
    suspend fun fetch(): DataResult<List<DailyCode>>
}

interface ItemPriceDataSource {
    suspend fun search(keyword: String): DataResult<List<ItemSummary>>
    suspend fun currentPrice(objectId: String): DataResult<ItemPrice>
    suspend fun history(objectId: String, days: Int): DataResult<PriceHistory>
}

interface FriendStatusDataSource {
    suspend fun fetchFriends(): DataResult<List<Friend>>
}

// 状态感知：让 UI 能如实说明"这个功能当前没有数据源"
interface UnavailableDataSource : FriendStatusDataSource   // 返回 UNAVAILABLE 而非伪造数据
```

实现放在 `game/deltaforce/*/data/`，通过 `app/di` 组装注入。
**`feature/` 与页面层只依赖 `domain/datasource` 接口，不依赖任何 HTTP 细节。**

`DataResult<T>` 需能表达：成功 / 网络失败 / 鉴权失败 / 业务失败 / 数据源不可用。

---

## 5. 缓存策略

| 数据类型 | 缓存 | 有效期（建议） | 过期后行为 |
|---|---|---|---|
| 今日密码 | ✅ | 当天 | 显示为「过期缓存」并标注原始日期 |
| 物品列表 / 搜索 | ✅ | 24 小时 | 显示缓存 + 提示可刷新 |
| 当前价格 | ✅ | 10 分钟 | 显示缓存 + 标注时间 |
| 价格历史 | ✅ | 24 小时 | 显示缓存 + 标注时间 |
| 好友状态 | ✅（若可用） | 60 秒 | 显示为「状态可能已过期」 |
| 设置 | ✅ | 永久 | — |

缓存必须带 `fetchedAt`、`sourceId`、`schemaVersion`。
`schemaVersion` 变更时**安全丢弃**旧缓存，不做兼容猜测。

---

## 6. P01 必须实测确认的清单

| # | 待确认项 | 方法 | 若结论为否的影响 |
|---|---|---|---|
| 1 | `dailykeyword` 是否返回日期字段 | 用真实 API Key 调一次，完整打印响应 | 影响今日密码四态判定（Q3） |
| 2 | API Key 是否需要个人申请、有无配额 | 访问申请页 + 文档 | 影响 F-03/F-04 可用性 |
| 3 | 服务条款与数据来源 | 访问文档站、Apifox 文档 | 影响合规声明（Q2） |
| 4 | 好友状态是否存在未文档化端点 | 查阅 Apifox 文档全文 + 咨询开发者（QQ 群 932459332） | 决定 F-06 能否交付（Q1） |
| 5 | 接口限流与超时表现 | 压测 / 观察响应头 | 影响刷新策略 |
| 6 | 价格历史的真实数据密度 | 抽查若干物品的 7/30 天历史 | 影响折线图空态设计 |

---

## 7. 结论摘要

| 功能 | 数据源状态 | 能否交付 |
|---|---|---|
| 今日密码 | ✅ 已验证（1 项待确认） | **能**（含日期判定风险） |
| 物品物价 + 历史趋势 | ✅ 已验证（能力最完整） | **能** |
| 好友在线状态 | ❌ 无可用数据源 | **不能**（需用户裁决 Q1） |
