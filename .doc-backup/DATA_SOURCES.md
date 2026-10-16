# DATA_SOURCES — 数据源、验证结果与限制

> 最后更新：2026-10-14（P00）
> **本文件是数据源的唯一权威记录。任何数据源结论必须先更新本文件，再写代码。**
> 完整分析见 [`docs/data-sources.md`](docs/data-sources.md)

---

## 状态分级定义

| 等级 | 含义 | 允许的用法 |
|---|---|---|
| ✅ **已验证** | 本机实际调用成功并看到真实数据 | 可用于正式功能 |
| 🟡 **待验证** | 有文档或源码依据，但未实际调用确认 | 仅可用于开发，UI 必须标注 |
| 🟠 **受限** | 存在但能力不足/条件苛刻，无法满足需求 | 只可作为辅助，不得作为主数据源 |
| 🔴 **不可用** | 不存在或确认无法使用 | **禁止**用于任何功能 |

---

## 一、当前数据源总表

| 功能 | 数据源 | 端点 | 等级 | 证据 |
|---|---|---|---|---|
| 今日密码 | 社区接口 | `GET /api/v1/df/tools/dailykeyword` | ✅ 已验证 | GameMate 实测 |
| 物品搜索 | 社区接口 | `GET /api/v1/df/object/search?objectName=` | ✅ 已验证 | GameMate 实测 |
| 当前价格 | 社区接口 | `GET /api/v1/df/object/price/ams/latest?id=` | ✅ 已验证 | GameMate 实测 |
| 价格历史 | 社区接口 | `GET /api/v1/df/object/price/ams/history/v1?id=&days=` | ✅ 已验证 | GameMate 实测 |
| 材料价格 | 社区接口 | `GET /api/v1/df/place/material/price` | ✅ 已验证 | GameMate 实测 |
| 弹药价格 | 社区接口 | `GET /api/v1/df/object/ammo?days=7` | ✅ 已验证 | GameMate 实测 |
| 制造利润 | 社区接口 | `GET /api/v1/df/place/profit/rank` | ✅ 已验证 | GameMate 实测 |
| **好友列表** | — | **无** | 🔴 **不可用** | 全文档 + 全参考仓库 grep 无结果 |
| **好友在线状态** | — | **无** | 🔴 **不可用** | 见下方排除表 |
| 自己账号在线（微信） | 社区接口 | `GET /api/v1/df/gamesafe/online` | 🟠 受限 | 只能查自己，需微信 token，无"游戏中" |
| 好友昵称/头像 | 社区接口 | `GET /api/v1/df/person/friendinfo?openid=` | 🟠 受限 | 只返回资料，**无状态** |

---

## 二、服务基本信息

| 项 | 值 |
|---|---|
| 基地址 | `https://delta-test-api.shallow.ink` |
| 鉴权 | 请求头 `X-API-Key`；个人数据类另需 `X-Framework-Token` |
| 响应信封 | `{"code":0,"message":"成功","data":{...}}`，`code == 0` 成功 |
| Key 申请 | `https://delta-test.shallow.ink/developer/api-keys`（需登录） |
| 开发者文档 | `https://delta-force.apifox.cn` |
| 运营者 | 文档署名「浅巷墨黎、Admilk、mapleleaf」 |
| 接口文档版本 | v2.4.0 |
| 求助渠道 | QQ 群 932459332 |
| **官方性** | ❌ **非腾讯官方**，社区第三方 |

---

## 三、好友在线状态：逐条排除记录（ISSUE-001）

**这是本文件最重要的内容。若未来有人质疑"为什么不做好友状态"，看这里。**

| # | 候选端点 | 实际返回 | 排除原因 |
|---|---|---|---|
| 1 | `GET /df/person/friendinfo?openid=` | `charac_name`、`picurl` | 只有昵称与头像，**无状态字段** |
| 2 | `GET /api/v1/df/gamesafe/online` | 单账号在线/离线 | ① 只能查**自己**；② 需 **gamesafe（微信）** token；③ 只有布尔，**无"游戏中"** |
| 3 | `GET /df/person/weeklyRecord?...&showExtra=true` | `reportDm.wbn.friends[].Friendopenid` | 周报**统计**字段，非实时；需登录；响应慢 |
| 4 | `weeklyRecord` 的 `friends` 字段 | 队友战绩数据 | 是"队友"非"好友"，且非状态 |
| 5 | `GET /api/v1/df/gamesafe/devices` | 登录设备列表 | 与好友无关 |

**核验方法**（可复现）
```powershell
# 全文档检索
Select-String -Path 'D:\CODE_Nskdfh_VS\android_Projects\_refs\yunzai-df\Delta-Force-API-DOCS.md' -Pattern '好友|friend'
# 全参考仓库检索
Get-ChildItem 'D:\CODE_Nskdfh_VS\android_Projects\_refs' -Recurse -File |
  Select-String -Pattern 'friendinfo|friendInfo|好友'
```

---

## 四、⚠️ 证据判定教训（务必遵守）

> **不要把展示层标签当成接口证据。**

`astrbot-df/main.py` 的 `_gamesafe_record_lines()` 里有：
```python
labels = { "online": "在线", "nickname": "昵称", "status": "状态", ... }
```
这**只是**一个 `字段名 → 中文标签` 的映射表，用于把**任意**响应字段渲染成可读文本。
它**不能**证明：
- 存在提供"在线状态"的接口
- 响应里真的有 `online` 字段
- 该字段的含义是"游戏中/在线"

**判定数据源的合法依据只有三种**：
1. 本机实际调用并看到真实响应
2. 接口文档明确记录的响应字段
3. 客户端 SDK / 参考实现的**取值代码**（如 `data.charac_name`）

---

## 五、数据真实性红线（不得违反）

1. 具体数据源不可用时，**不得编造任何数据**填充界面。
2. `UNKNOWN` 状态**绝不**自动转换为 `OFFLINE`。
3. 无成交价格（`hasValidPriceData: false` / `avgPrice: 0`）必须显示「暂无成交数据」，**不得显示 0 为真实价格**。
4. 历史趋势图**只用真实数据点**，缺失点留空，不插值、不平滑成假曲线。
5. 没有当天有效密码时，**不得**拿昨天的密码冒充今日密码。
6. 每个价格与密码必须显示**来源**与**更新时间**。
7. 开发期 mock 必须满足三条：类名以 `Mock` 开头、模型带 `isMock = true`、UI 显示 `UbMockBadge`。

---

## 六、P01 必须确认清单

| # | 待确认 | 方法 | 关联 |
|---|---|---|---|
| 1 | `dailykeyword` 是否含日期字段 | 真实 Key 调用，打印完整响应 | ISSUE-002 |
| 2 | API Key 申请门槛与配额 | 访问申请页 | Q4 |
| 3 | 服务条款与数据来源 | 访问文档站 / Apifox | 合规 |
| 4 | 好友状态是否有未文档化端点 | Apifox 全文 + 咨询开发者 | ISSUE-001 |
| 5 | 限流与超时表现 | 观察响应头 / 压测 | 刷新策略 |
| 6 | 价格历史真实数据密度 | 抽查若干物品 7/30 天 | 图表设计 |

---

## 七、合规约束

| 约束 | 内容 |
|---|---|
| 数据版权 | 归腾讯有限公司所有 |
| 用途 | 仅供技术学习与个人使用；不得商用 |
| **禁止** | 自动化游戏操作：不签到、不代打、不改内存、**不抓包** |
| **禁止** | Cookie 登录（需交出完整 QQ 会话凭据） |
| 凭据 | 首期三个核心功能**均不需要登录** |
| 声明 | 应用内必须如实声明数据来源与免责 |

---

## 八、变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14 | 首次建立（P00）。核验 7 个端点已验证；确认好友状态无可用数据源；记录展示层标签误判教训 |
