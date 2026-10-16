# TEST_REPORT — 真实执行过的测试

> 最后更新：2026-10-14（P00）
> **原则：本文件只记录"实际执行过"的测试。未执行的必须显式标注「未执行」。**
> 设计稿要求：「对于没有实际执行的构建、测试或设备验证，必须明确标记为未执行。」

---

## 测试环境

| 项 | 值 |
|---|---|
| 主机 | Windows（本机） |
| Android SDK | `D:\CODE_Nskdfh_VS\android SDK` |
| JDK | 21.0.7 LTS（`D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21`） |
| 模拟器 | AVD `dsh_pixel10` / Android 16 (API 36) / x86_64 / **软件渲染** |
| 设备名 | `emulator-5556` |
| 辅助脚本 | `D:\CODE_Nskdfh_VS\android_Projects\devtools.ps1` |

---

## P00 · 实际执行过的验证

### T-P00-01 环境自检 ✅ 已执行

**执行方式**
```powershell
& powershell -NoProfile -ExecutionPolicy Bypass -File `
  'D:\CODE_Nskdfh_VS\android_Projects\devtools.ps1' status
```

**实际输出**
```
===== 环境自检 =====
  SDK         : D:\CODE_Nskdfh_VS\android SDK  OK
  adb         : OK
  emulator    : OK
  JDK 21      : OK
  AVD         : dsh_pixel10
  设备        : 无（执行 devtools.ps1 emu 启动）
  注意：JAVA_HOME 未持久化，构建时本脚本会自动设置
```

**结论**：✅ SDK / adb / emulator / JDK21 / AVD 全部就绪。模拟器**当前未启动**（符合预期，P00 不需要）。

---

### T-P00-02 现有工程勘察 ✅ 已执行

**执行方式**
```powershell
Get-ChildItem 'D:\CODE_Nskdfh_VS\android_Projects' -Force
Test-Path 'D:\CODE_Nskdfh_VS\android_Projects\UtilityBox'
```

**实际结果**
- `UtilityBox` 目录**不存在** → 全新项目，**无覆盖既有工程风险**
- 参考工程 `GameMate` 存在（`HANDOVER.md` / `app/` / `gradle/` / `settings.gradle.kts` 等）
- `_refs/` 下有 `astrbot-df`、`yunzai-df` 两个参考仓库

---

### T-P00-03 数据源核验 ✅ 已执行

**执行方式**
```powershell
# 1. 接口文档全文检索
Select-String -Path '...\_refs\yunzai-df\Delta-Force-API-DOCS.md' -Pattern '好友|friend|social'
# 2. 参考仓库全文检索
Get-ChildItem '...\_refs' -Recurse -File | Select-String -Pattern 'friendinfo|好友|gamesafe'
# 3. 人工阅读关键代码段
#    - astrbot-df/core/client.py L385-445, L560-640
#    - astrbot-df/main.py L8968-9032（确认 labels 只是标签映射）
#    - yunzai-df/apps/report/Weekly.js L428-482
#    - yunzai-df/Delta-Force-API-DOCS.md L1854-1893, L3705-3764, L3790-3841, L1398-1477
```

**实际结论**
| 功能 | 结论 | 关键证据 |
|---|---|---|
| 今日密码 | ✅ 端点存在，字段 `list[]{mapID,mapName,secret}`，**无日期字段** | 文档 L1856-1859；GameMate 实测 |
| 物品物价 | ✅ 6 个端点，字段完整 | 文档 + GameMate 实测表 |
| 好友列表 | 🔴 **无任何端点** | grep 无结果 |
| 好友在线状态 | 🔴 **无任何端点** | 5 个候选逐一排除（见 `DATA_SOURCES.md` §3） |

**附带发现（教训）**：`main.py` 的 `_gamesafe_record_lines` 是**标签映射表**，不是响应 schema。
一度据此误判存在好友状态接口，已在 `KNOWN_ISSUES.md` ISSUE-007 记录。

---

## P00 · 未执行的验证（如实标注）

| # | 项目 | 状态 | 原因 |
|---|---|---|---|
| 1 | Gradle 构建（`assembleDebug`） | ❌ **未执行** | P00 阶段按要求不生成应用代码，无工程可构建 |
| 2 | APK 安装 | ❌ **未执行** | 无 APK |
| 3 | App 启动 / 崩溃检查 | ❌ **未执行** | 无 APK；模拟器未启动 |
| 4 | 单元测试 | ❌ **未执行** | 无代码 |
| 5 | UI 测试 / 集成测试 | ❌ **未执行** | 无代码 |
| 6 | 界面文字 dump 验证 | ❌ **未执行** | 无页面可验证 |
| 7 | `liquid-glass:0.2.3` 实际编译 | ❌ **未执行** | 仅基于 `mv=[2,3,0]` 规则判定可用，**未构建验证** |
| 8 | Kotlin 2.4.x 路线验证 | ❌ **未执行** | 未构建；且未确认 AGP 9.2.1 内置 KGP 能否配合 2.4.x |
| 9 | 付费端点 `friendinfo` 响应 schema | ❌ **未执行** | 账号 0 积分，返回 402 |
| 10 | Liquid Glass 渲染/性能对比 | ❌ **未执行** | 属 P01 范围 |

> ⚠️ **上述未执行项不得在任何文档或汇报中被表述为"已完成"或"已验证"。**

---

## P10 · 全面测试与验收 ✅ 已执行（158 个用例全部通过）

**执行命令**
```powershell
$env:JAVA_HOME = 'D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21'
Set-Location 'D:\CODE_Nskdfh_VS\android_Projects\UtilityBox'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

### 单元测试：158 个用例 / 0 失败

| 测试类 | 用例数 | 失败 | P10 新增 |
|---|---|---|---|
| `core.cache.CachePolicyTest` | 8 | 0 | |
| `core.design.ExitConfirmStateTest` | 8 | 0 | ★ 双击退出状态机 |
| `core.json.JsonTest` | 14 | 0 | |
| `core.navigation.UbNavigatorTest` | 7 | 0 | ★ 返回键回归钉子 |
| `core.network.ApiEnvelopeTest` | 9 | 0 | |
| `core.settings.SettingsStoreTest` | 10 | 0 | |
| `domain.tool.UbToolRegistryTest` | 15 | 0 | 白名单已按新增工具更新 |
| `game.deltaforce.ammoprice.AmmoPriceParserTest` | **11** | 0 | ★ 全新（弹药解析红线） |
| `game.deltaforce.dailycode.DailyCodeParserTest` | 11 | 0 | |
| `game.deltaforce.dailycode.DailyCodeRepositoryTest` | 21 | 0 | |
| `game.deltaforce.dailycode.ExceptionPathMatrixTest` | **8** | 0 | ★ 全新（异常路径矩阵） |
| `game.deltaforce.itemprice.ItemPriceParserTest` | 17 | 0 | |
| `game.deltaforce.itemprice.ItemPriceRepositoryTest` | 19 | 0 | |
| **合计** | **158** | **0** | |

### ★ 关键验收：新增工具后已有模块测试仍全绿

**方法**：先记录 10 个公共层文件的 SHA256 → 新增一个完整工具（`弹药物价`）→ 再比对并跑全部测试。

**结果**：

| 检查项 | 结果 |
|---|---|
| 10 个公共层文件 SHA256 | **全部未改动（10/10）** |
| 已有模块测试 | **全部通过**（147 → 158，只增不减） |

**新增工具的改动量**：新建 `game/deltaforce/ammoprice/` 包（5 个文件）
+ `DeltaForceRoutes` 加 1 行 + `DeltaForceModule.providers` 加 1 行。

**设备验证**：「弹药物价」自动出现在首页；点开正常渲染；
未配 Key 时正确显示「还没填 API Key」并指向设置页。

**另外三个按需扩展的共享文件**（如实列出，不全算"零改动"）：

| 文件 | 改动 | 必要性 |
|---|---|---|
| `core/json/Json.kt` | 新增 `boolOrNull` / `doubleOrNull` | 弹药接口的字段形态此前没用到 |
| `domain/datasource/DeltaForceApi.kt` | 新增 `PATH_OBJECT_AMMO` 常量 | 端点契约本来就集中在此文件 |
| `domain/model/Models.kt` | 新增 `AmmoPrice` 数据类 | 跨工具共享的模型层 |

> 这三处都是**按需增长**而非返工：没有被改动的是**导航/首页/注册表/设计系统**，
> 也就是"新增工具要不要改公共逻辑"这个问题的答案仍然是「不用」。

### 异常路径覆盖（设计稿要求的 7 类 + 2 类）

`ExceptionPathMatrixTest` 用**矩阵遍历**覆盖，新增错误类型时会自动失败：

| 错误类型 | 覆盖 | 断言的语义 |
|---|---|---|
| 无网 `NoNetwork` | ✅ | 归为 Failed，保留原错误 |
| 超时 `Timeout` | ✅ | 与无网**不混淆** |
| 5xx `Server(500/502/503)` | ✅ | 归为 Failed，不伪装成"暂无数据" |
| 鉴权失败 `ApiKeyMissing` / `Unauthorized` | ✅ | **两者必须区分**（P06 缺陷的回归钉子） |
| JSON 损坏 | ✅ | 8 种损坏形态逐一验证（截断/空/类型错/缺字段…） |
| 业务错误 `Business` | ✅ | 保留数据源原文（code + message） |
| 数据源不可用 `SourceUnavailable` | ✅ | **不得**被当成"暂无数据"（此前零覆盖） |
| 未知错误 `Unknown` | ✅ | 保留原始信息（此前零覆盖） |

**另有两条横切红线断言**：
- 任何错误下，**无缓存时绝不产生 `Today`**（会显示错误的密码）
- 任何错误下，有昨天的缓存都回退到 `Stale` 且**携带原始日期**

### 缓存过期与 schema 变更

| 场景 | 覆盖 |
|---|---|
| 有效期内为 FRESH / 超期为 STALE / 边界值 | ✅ `CachePolicyTest` |
| schema 版本不匹配 → `SCHEMA_MISMATCH` | ✅ |
| 用户改系统时间（时间倒退） | ✅ 按 STALE 处理 |
| **schema 不匹配的缓存被真正删除**（不只是不读） | ✅ 仓库层断言 `assertNull` |
| 今日密码的特殊策略（不走固定 TTL） | ✅ |

### 崩溃检查

| 场景 | 结果 |
|---|---|
| 模拟器逐页导航（今日密码 / 物品物价 / 设置 / 新工具） | ✅ 零崩溃 |
| 真机 vivo V2307A / Android 16 | ✅ 用户确认「整机测试下来，正常」 |
| logcat FATAL / ANR 扫描 | ✅ 无 |

### 安全验收

| 检查 | 结果 |
|---|---|
| 源码硬编码密钥 | ✅ 无（3 处正则命中均为注释/路由常量/保密声明） |
| 日志打印 Key | ✅ 无（5 处日志：缓存键、URL+响应体、Keystore 异常；**Key 在请求头，不参与日志**） |
| `.gitignore` 排除 `local.properties` / keystore | ✅ |
| `local.properties` 的 `ubox.apiKey` | ✅ 为空 |
| **APK 内是否含真实密钥** | ✅ 解包扫描 `.dex`/资源，**无 `sk-` 形态密钥**；`BuildConfig.UBOX_API_KEY` 为空串 |

### 未执行事项（如实标注）
- ❌ **UI 测试 / 自动化交互测试** —— 未接入 `androidx.compose.ui.test`；
  返回键这类交互目前只有状态机单测 + 真人操作覆盖
- ❌ **覆盖率统计** —— 未接入覆盖率工具（JaCoCo 等）
- ❌ **性能测量** —— 未做 Macrobenchmark（需真机 + release-like 构建）

---

## P09 · 单元测试与设备实测 ✅ 已执行（124 个用例全部通过）

### 单元测试

| 测试类 | 用例数 | 失败 |
|---|---|---|
| `core.cache.CachePolicyTest` | 8 | 0 |
| `core.json.JsonTest` | 14 | 0 |
| `core.network.ApiEnvelopeTest` | 9 | 0 |
| **`core.settings.SettingsStoreTest`** | **10** | 0 |
| `domain.tool.UbToolRegistryTest` | 15 | 0 |
| `game.deltaforce.dailycode.DailyCodeParserTest` | 11 | 0 |
| `game.deltaforce.dailycode.DailyCodeRepositoryTest` | 21 | 0 |
| `game.deltaforce.itemprice.ItemPriceParserTest` | 17 | 0 |
| `game.deltaforce.itemprice.ItemPriceRepositoryTest` | 19 | 0 |
| **合计** | **124** | **0** |

P09 新增用例锁定的关键点：
- `保存后重新读取能拿回同样的值 —— 模拟重启`（"重启"= 同一 store 再 `load()`）
- `工具默认是启用的 —— 新工具不需要登记`（存储设计选择的核心收益）
- `工具启停会递增版本号 以便界面重组`（"开关点了没反应"的回归钉子）

### 设备实测（P09 验收标准逐条对应）

**执行方式**：`adb install -r` → 改设置 → `am force-stop` → `am start`（真实冷启动）→ 读磁盘 + dump 界面

| 验收标准 | 实测结果 |
|---|---|
| 每项设置生效且持久化 | ✅ 磁盘 `theme=Dark`、`auto_refresh_min=60`、`disabled_tools={deltaforce.friendstatus}`；冷启动后界面与磁盘一致 |
| 工具启停即时反映到首页 | ✅ 关「好友状态」→ 首页 4 项变 3 项；**重启后仍 3 项** |
| 数据源状态页真实反映验证结果 | ✅ 自动统计「2 个可用 · 1 个实验性 · 1 个开发中」（原为写死的"2 可用 1 开发中"） |
| 清除缓存后页面正确回落到空态 | ✅ 磁盘缓存条目 5 → 0；界面显示「已清空 · 页面会重新获取数据」 |
| 逐页回归 | ✅ 今日密码 24 · 物品物价 5 · 设置 27，**零崩溃** |

**未执行**
- ❌ **自动刷新的实际生效** —— 设置已持久化，但调度未接入（属 P10）
- ❌ **UI 测试** —— 属 P10

---

## P06/P07 · 单元测试 ✅ 已执行（114 个用例全部通过）

**执行方式**
```powershell
$env:JAVA_HOME = 'D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21'
Set-Location 'D:\CODE_Nskdfh_VS\android_Projects\UtilityBox'
.\gradlew.bat :app:testDebugUnitTest
```

**实际结果**

| 测试类 | 用例数 | 失败 |
|---|---|---|
| `core.cache.CachePolicyTest` | 8 | 0 |
| `core.json.JsonTest` | 14 | 0 |
| `core.network.ApiEnvelopeTest` | 9 | 0 |
| `domain.tool.UbToolRegistryTest` | 15 | 0 |
| `game.deltaforce.dailycode.DailyCodeParserTest` | 11 | 0 |
| `game.deltaforce.dailycode.DailyCodeRepositoryTest` | **21** | 0 |
| `game.deltaforce.itemprice.ItemPriceParserTest` | 17 | 0 |
| `game.deltaforce.itemprice.ItemPriceRepositoryTest` | **19** | 0 |
| **合计** | **114** | **0** |

### ★ P06/P07 新增的关键红线断言

| 红线 | 断言 |
|---|---|
| 过期密码不冒充今日 | `Stale 状态的日期与 Today 不同`（并断言 `fetchedDate != today`） |
| **无成交数据不是 0 元** | `当前价为 0 时同样判定为 NoPriceData`（解析层 + 仓库层双重） |
| **缺失点必须保留** | `历史中的无成交日保留为 null 且不被丢弃`；**且缓存往返后仍是 null** |
| 缺失点不影响趋势 | `缺失点不影响趋势计算（只用有效点）` |
| **「没有数据」≠「服务故障」** | `服务端回应缺少 dataMap 时必须归为无成交数据` |
| **反向保护** | `真正的服务端错误不会被吞成无数据`（5xx / 限流仍如实报错） |
| **请求参数名契约** | `三个端点的请求参数名必须是实测值`（`objectName`/`id`/`id`） |
| 「没配 Key」≠「Key 被拒」 | `服务端拒绝 Key 与未配 Key 是两种不同的错误` |

### 设备实测（真实 API Key，非模拟）

| 场景 | 结果 |
|---|---|
| 今日密码 | 6 张地图密码全部返回，来源与更新时间正常 |
| 物品搜索 `M250` | 9 条真实结果；**3 条有价格、5 条无成交** |
| 有价格物品 | `49,498` / `25,201` / `220,603`（**未把无成交项显示成 0**） |
| 无成交物品详情 | 显示「暂无成交数据」+「这不代表价格是 0」 |
| 详情页（有数据） | 当前价 `234,020`、7 天真实历史、趋势 `-0.5%`，图表形态与数值吻合 |
| 逐页回归 | 今日密码 24 · 物品物价 5 · 好友状态 14 · 设置 26，**零崩溃** |

**未执行**
- ❌ **UI 测试 / 集成测试** —— P10 范围
- ❌ **覆盖率统计** —— 未接入覆盖率工具
- ❌ **中文关键字搜索的设备端验证** —— `adb shell input text` 不支持非 ASCII

---

## P03/P04 · 单元测试（59 → 74 个用例）

**执行方式**
```powershell
$env:JAVA_HOME = 'D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21'
Set-Location 'D:\CODE_Nskdfh_VS\android_Projects\UtilityBox'
.\gradlew.bat :app:testDebugUnitTest
```

**实际结果**

| 测试类 | 用例数 | 失败 | 覆盖内容 |
|---|---|---|---|
| `core.json.JsonTest` | **14** | 0 | 自写 JSON 解析器（对象/数组/转义/Unicode/非法输入/数字读取） |
| `core.network.ApiEnvelopeTest` | **9** | 0 | 响应信封、错误映射、非 JSON 响应、缺字段 |
| `domain.tool.UbToolRegistryTest` | **15** | 0 | 注册表完整性、路由唯一、**扩展机制派生一致性**、状态诚实标注 |
| `game.deltaforce.dailycode.DailyCodeParserTest` | **11** | 0 | 今日密码解析 + **四态判定 + 过期不冒充今日** |
| `game.deltaforce.itemprice.ItemPriceParserTest` | **17** | 0 | 物品解析 + **0 不等于价格** + 缺失点保留 + 趋势判定 |
| `core.cache.CachePolicyTest` | **8** | 0 | 缓存新鲜度、schema 版本、时间倒退、键命名空间 |
| **合计** | **74** | **0** | |

**★ 已被测试锁定的红线**

| 红线 | 对应断言 |
|---|---|
| 过期密码不冒充今日 | `Stale 状态的日期与 Today 不同 —— 这是红线断言` |
| 无成交数据不是 0 元 | `价格为 0 视为无成交数据而不是 0 元 —— 红线`（在 `positiveLong` 与解析层双重断言） |
| `count=0` 的日子价格不可信 | `样本数为 0 的日期视为无成交，即使接口给了价格 —— 红线` |
| 数据不足不伪装持平 | `有效点不足两个时趋势为 UNKNOWN 而不是持平` |
| 缺失点必须保留（图表断开） | `趋势由首尾有效点计算，中间缺失点不影响`（同时断言列表仍有 3 个点） |
| 结构变更丢弃旧缓存 | `schema 版本不匹配时判定为 SCHEMA_MISMATCH` |
| **未完成的工具必须如实标注** | `所有非 ENABLED 状态的工具都必须带状态说明`（遍历所有工具，不止单个） |
| **注册表不由第二份列表写死** | `注册表工具列表与 AppContainer providers 派生结果完全同组`（逐项相等 + 反向 `assertSame`） |
| 入口不隐藏 | `好友状态工具仍为待数据源状态且入口未隐藏`（断言仍出现在 `homeTools()`） |
| 二级页不会被误当工具 | `未知路由不返回 provider`（含 `df/itemprice/detail` 必须为 null） |

**⚠️ 过程中发现并解决的问题**

**问题 1：`org.json` 在单元测试中是 Android 空壳 stub**（首次运行 29/40 失败）
根因不是业务代码：`org.json` 只有方法签名，一调用即抛
`RuntimeException: Method ... not mocked`，导致所有涉及 JSON 的逻辑无法在无设备环境下验证。

**处置**：自写最小 JSON 解析器 `core/json/Json.kt`，使**测试与运行时共用同一份代码**。
备选方案（给测试加第三方 `org.json` 依赖）被否决 —— 那会造成
"测过的实现与运行时用的实现是两套代码"的隐患。

**问题 2（由 P04 测试发现）：`verifyIntegrity()` 写了却没有任何调用点**
`UbToolRegistry.verifyIntegrity()` 的 KDoc 写明"在应用启动时调用一次"，
但全仓 grep 显示**只有定义处与测试引用它** —— 真机上启动自检从不执行。
后果：注册表配错时不会立刻暴露，用户点到该工具只会看到空白页
（`providerByRoute` 静默返回 null），排查成本高得多。

**处置**：在 `MainActivity.onCreate` 开头调用一次（现 `MainActivity.kt:51`）。
另外修正了 `MainActivity` 上一处与实现不符的过时注释
（原写"新增工具只需在 `when(route)` 加分支"——那正是被本轮改掉的旧机制）。

> **这条缺陷值得单独记一笔**：它证明"写了自检"和"自检真的会跑"是两件事，
> 而单元测试能发现后者。修复后 74 个用例仍全绿。

**同批验证**
- ✅ `assembleDebug` 成功
- ✅ 安装到 `emulator-5556`、启动正常、**无崩溃**（启动自检通过）
- ✅ 首页 4 个工具全部渲染

**未执行**
- ❌ **UI 测试（compose-ui-test）** —— P10 范围
- ❌ **集成测试** —— P10 范围
- ❌ **覆盖率统计** —— 未接入覆盖率工具

---

## P01 · 实际执行过的验证（P00 收尾 + P01 前置，全部真机/真构建）

> 环境：`emulator-5556` / Android 16 (API 36) / `sdk_gphone64_x86_64` / x86_64 / **`hw.gpu.enabled=no`（软件渲染，`ro.hardware.egl=emulation`）**
> 验证工程：`D:\APPDATA\PCD\DSH\ubox-verify`（路线A Kotlin 2.2.10）与 `ubox-verify-b`（路线B Kotlin 2.4.21）

### T-P01-01 完整构建矩阵：7 变体 × 2 路线 ✅ 已执行

**方式**：同一份源码，唯一差异为 Kotlin 版本；`compileSdk 37.0` / `minSdk 24` / `targetSdk 36` /
Compose BOM 2026.02.01 / AGP 9.2.1 / Gradle 9.4.1。用 product flavor 隔离 7 个测试目标，一次 `assemble*Debug` 全量构建。

**实际输出（APK 字节数，构建产物为证）**

| 变体 | 路线A Kotlin 2.2.10 | 路线B Kotlin 2.4.21 |
|---|---|---|
| Baseline（`navigation-compose 2.10.2`） | ✅ 11,649,913 B | ✅ 11,715,821 B |
| Selfnav（零导航依赖） | ✅ 11,764,971 B | ✅ 11,847,263 B |
| Platformblur（零玻璃依赖，`Modifier.blur`） | ✅ 11,764,975 B | ✅ 11,847,267 B |
| Liquidglass（`liquid-glass 0.2.3`） | ✅ 11,847,383 B | ✅ 11,896,610 B |
| Haze1（`haze 1.7.3` 纯模糊） | ✅ 11,734,790 B | ✅ 11,784,017 B |
| **Haze2（`haze 2.0.1` + `haze-glass 2.0.1` 折射）** | ❌ **metadata 2.4.0 不兼容** | ✅ **12,141,445 B** |
| Backdrop（`backdrop 2.0.1`） | ❌ API 用法 | ❌ API 用法 |

**结论**
- ✅ `compileSdk 37` 是解锁关键：提到 37 后官方导航 / liquid-glass / haze1 **全部由失败转为通过**。
- ✅ **`haze2` 是唯一被 Kotlin 版本硬卡住的项**：2.2.10 失败、2.4.21 成功 → 对「metadata +1 minor 天花板」的**构建级实证**。
- ✅ 两条路线都能跑通核心栈（Baseline/Selfnav/Platformblur/Liquidglass/Haze1 在 2.2.10 即可用）。
- ⚠️ `backdrop` 两次均为 **API 用法**失败（`Unresolved reference` / `No value passed`），**尚未与兼容性分离**；
  它是 API 最复杂的候选（`drawBackdrop` 13 个参数），**只证明接入成本高，不证明不可行**。

---

### T-P01-02 实机安装 + 启动 + 崩溃检查 + 界面文本 dump ✅ 已执行

**方式**
```powershell
& powershell -NoProfile -ExecutionPolicy Bypass -File 'D:\APPDATA\PCD\DSH\ubox-probe\verify_on_device.ps1'
# 每变体：adb install -r → am start -W → pidof → logcat 查 FATAL/AndroidRuntime → uiautomator dump → screencap
```
> 脚本先 `am force-stop com.example.gamemate` 并回桌面，**避免把上一个 App 的窗口误当成自己的界面**（首轮踩过这个坑）。

**实际结果**

| 变体 | 安装 | 启动 | 进程存活 | 崩溃 | dump 文字数 | 中文标记 `P01` |
|---|---|---|---|---|---|---|
| baseline | ✅ | ≥ 2063 ms | ✅ | **CLEAN** | 8 | ✅ 找到 |
| selfnav | ✅ | ≥ 1892 ms | ✅ | **CLEAN** | 9 | ✅ 找到 |
| platformblur | ✅ | ≥ 2033 ms | ✅ | **CLEAN** | 11 | ✅ 找到 |
| liquidglass（平台档） | ✅ | ≥ 2983 ms | ❌ **死** | **SIGSEGV** | — | 否（dump 到桌面） |
| haze1 | ✅ | ≥ 2011 ms | ✅ | **CLEAN** | 8 | ✅ 找到 |
| haze2（路线B） | ✅ | ≥ 2112 ms | ✅ | **CLEAN** | 8 | ✅ 找到 |

**★ 关键结论 1：无 GUI 环境下的界面验证方法可行。**
`uiautomator dump` **能正确读取 Compose 渲染的中文文字**，例如实际 dump 到：
```
P01 验证
标记：F2-基线-自研导航
设备=sdk_gphone64_x86_64 厂商=Google API=36 硬件=ranchu 硬件加速=true
回退栈深度：1
```
→ 证明「按文字定位控件」的既有方法论对 Compose 有效（`AGENT_GUIDE.md` §4 的方法可直接用于本项目）。

**★ 关键结论 2：`liquid-glass 0.2.3` 在软件渲染下会让 RenderThread 崩溃。**
```
F libc : Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0x7e1f38b00ff0
         in tid 15558 (RenderThread), pid 15543 (ify.liquidglass)
F DEBUG: Cmdline: com.utilitybox.verify.liquidglass   pid: 15543, tid: 15558, name: RenderThread
```
**但强制其自带的降级档后正常**（见 T-P01-03）。

---

### T-P01-03 `liquid-glass` 降级档验证 ✅ 已执行

**方式**：把该库的档位由平台自动档改为强制 `LiquidGlassQuality.Fallback`，重新构建、安装、启动、dump。

**实际输出**
```
BUILD SUCCESSFUL in 5s
install: Success
Status: ok    TotalTime: 2074
进程存活 pid=15601  -> Fallback 档未崩溃
无 SIGSEGV / FATAL EXCEPTION
dump: P01 验证 / 标记：F3-液态玻璃-Fallback档 / 强制档位：Fallback / 液态玻璃卡片（Fallback 档）
```

**结论**：✅ 该库**自带可用的降级路径**（`Full` / `Medium` / `Fallback` 三档 + `rememberPlatformLiquidGlassQuality()` 平台分级）。
风险不在于"库不可用"，而在于**默认档位选择策略**——设计稿要求玻璃效果必须可独立降级，本库满足该要求，
但接入时**必须自行决定档位探测策略**，不能盲信平台自动档。

---

### T-P01-04 模拟器渲染后端确认 ✅ 已执行

```powershell
adb -s emulator-5556 shell getprop ro.hardware.egl      # emulation
adb -s emulator-5556 shell getprop debug.hwui.renderer  # skiagl
# AVD config.ini: hw.gpu.enabled=no   hw.gpu.mode=auto
```

**结论**：模拟器走**软件渲染路径**（`ro.hardware.egl=emulation`）。因此：
- 所有**性能**结论在本机**不具代表性**，玻璃效果性能必须真机复核。
- 玻璃效果在软件渲染下的**崩溃**（如 T-P01-02）可能只反映软件渲染的限制，**不能直接推断真机也崩**——
  但也**不能假定真机不崩**，需真机验证。

---

### T-P01-05 依赖 metadata / BOM / 传递依赖实测 ✅ 已执行

见 T-P00-04 / T-P00-05 / T-P00-06（下节）。已确认：
- `@Metadata mv`：haze 2.0.1 = `[2,4,0]`、backdrop 2.0.1 = `[2,4,0]`、haze 1.7.3 = `[2,3,0]`、liquid-glass 0.2.3 = `[2,3,0]`
- BOM 2026.02.01 = 240 条，只覆盖 7 个 `androidx.compose.*` group，**仍含 `material-icons-core/-extended = 1.7.8`**
- 传递依赖：`navigation-common:2.10.2` → `lifecycle 2.11.0` → 要求 `compileSdk 37`

---

### P01 · 未执行的验证（如实标注）

| # | 项目 | 状态 | 原因 |
|---|---|---|---|
| 1 | **API < 31 时 `Modifier.blur` 被静默忽略的行为** | ❌ **未执行** | 本机**只有 `system-images;android-36`**，无 API 24–30 镜像，无法创建低 API 模拟器 |
| 2 | **玻璃效果的渲染表现与性能对比** | ❌ **未执行** | 已截图但**未做人眼比对**；且模拟器为软件渲染，性能数据不可信 |
| 3 | **真机验证** | ❌ **未执行** | 无真机设备连接 |
| 4 | **`backdrop` 完整 API 接入** | ❌ **未执行** | 两次均为 API 用法失败，未完成接入 |
| 5 | **`haze 2.0.1` 的 `hazeGlass()` 折射效果渲染** | ❌ **未执行** | 仅验证到"依赖可编译可加载"，未接入 Glass API |
| 6 | **Macrobenchmark 性能测量** | ❌ **未执行** | 需真机 + release-like 构建 |
| 7 | **API 36 模拟器上的实际 GPU 加速对比** | ❌ **未执行** | AVD 配置为 `hw.gpu.enabled=no`，未改动 |

> ⚠️ 截图已产出（`D:\APPDATA\PCD\DSH\screenshots\p01-*.png`），但**视觉判断必须由人做**（设计稿 §七.1 已明确这是 AI 代理做不了的事）。

---

## P00 补充 · 真实调用与依赖实测 ✅ 已执行

### T-P00-04 真实 API 调用实测 ✅ 已执行

**目的**：把数据源结论从"文档推断"升级为"真实调用验证"（P00 原先缺的一环）。

**执行方式**
```powershell
# Key 仅通过环境变量传入，不写入任何文件
$env:UBOX_API_KEY = '<用户提供的 Key>'
& <python> 'D:\APPDATA\PCD\DSH\ubox-probe\probe_api.py'
& <python> 'D:\APPDATA\PCD\DSH\ubox-probe\probe_quota.py'
& <python> 'D:\APPDATA\PCD\DSH\ubox-probe\probe_paid.py'
& <python> 'D:\APPDATA\PCD\DSH\ubox-probe\probe_price.py'
Remove-Item Env:\UBOX_API_KEY
```

**实际结果（HTTP / code）**

| 端点 | HTTP | code | 结论 |
|---|---|---|---|
| `/api/v1/df/tools/dailykeyword` | 200 | 0 | ✅ 6 张地图密码，**无日期字段** |
| `/api/v1/df/object/search?objectName=钛` | 200 | 0 | ✅ `total=4`（`钢`→28） |
| `/api/v1/df/object/price/ams/latest?id=13020000524` | 200 | 0 | ✅ `avgPrice=49444` |
| `/api/v1/df/object/price/ams/history/v1?id=&days=7` | 200 | 0 | ✅ 7 天 7 点，含 `count` 样本数 |
| `/api/v1/df/place/material/price` | 200 | 0 | ✅ 含 `priceSource` + Unix `timestamp` |
| `/api/v1/df/object/ammo?days=7` | 200 | 0 | ✅ 113 项，含 `hasValidPriceData:false` 样本 |
| `/api/v1/df/person/friendinfo` | **402** | 402 | 🔴 **付费端点**（1 积分/次），账号 0 积分 |
| `/api/v1/df/gamesafe/online` | 400 | 400 | 🟠 需 `X-Framework-Token` |
| `/api/v1/df/person/friends` 等 5 个 | **404** | — | 🔴 **不存在** |
| `/api/v1/user/{info,statistics,points,quota,balance,credits}` 等 8 个 | **404** | — | 🔴 **无余额查询接口** |

**结论**：免费端点已覆盖**今日密码与物品物价的全部需求**；**好友状态即使付费也仍缺"好友列表"** →
用户裁决的 **Q1 方案 A（降级实现）成立**。

**副作用说明**：本测试为**只读 GET 探测**，未修改任何远端数据；付费端点因余额不足**未产生消费**。

---

### T-P00-05 依赖 Kotlin metadata 兼容性实测 ✅ 已执行

**目的**：判定候选液态玻璃库能否被 Kotlin 2.2.10 编译。

**判据**：Kotlin 编译器只能读取「自身版本 +1 minor」的 metadata → 2.2.10 上限 `mv ≤ 2.3.x`。

**执行方式**
```powershell
# 下载 AAR → 取 classes.jar → javap -v 读 @Metadata 的 mv=
& 'D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21\bin\javap.exe' -v -p <class 文件> | Select-String 'mv='
# 脚本：D:\APPDATA\PCD\DSH\ubox-probe\probe_mv_javap.py
```

**实际结果**

| 坐标 | 实测 `@Metadata mv` | Kotlin 2.2.10 |
|---|---|---|
| `dev.chrisbanes.haze:haze-android:2.0.1` | `[2, 4, 0]` | ❌ 不可用 |
| `io.github.kyant0:backdrop-android:2.0.1` | `[2, 4, 0]` | ❌ 不可用 |
| `dev.chrisbanes.haze:haze-android:1.7.3` | `[2, 3, 0]` | ✅ 可用（仅纯模糊） |
| `dev.chrisbanes.haze:haze-android:1.7.2` | `[2, 2, 0]` | ✅ 可用（仅纯模糊） |
| `io.github.nadeemiqbal:liquid-glass-android:0.2.3` | `[2, 3, 0]` | ✅ 可用 |
| `androidx.compose.ui:ui-android:1.10.4` | `[2, 0, 0]` | ✅ 可用 |

**方法学记录（含一次自我纠错）**
- ❌ 第一版尝试：解析 `.kotlin_module` 头部 → **方法错误**，那是 **JVM metadata 版本**，不是 `@Metadata mv`。
- ✅ 正确方法：**`javap -v`** 读 class 的 `@Metadata(mv=...)`。
- ⚠️ 不可用 POM 声明的 `kotlin-stdlib` 版本推断（POM 声明 ≠ 编译该库的 Kotlin 版本）。

---

### T-P00-06 Compose BOM 与传递依赖实测 ✅ 已执行

**目的**：核验 BOM 实际内容，并检查候选依赖是否会拉高 Compose 版本。

**执行方式**
```powershell
& <python> 'D:\APPDATA\PCD\DSH\ubox-probe\probe_bom.py'    # 解析 BOM POM
& <python> 'D:\APPDATA\PCD\DSH\ubox-probe\probe_poms.py'   # 解析各库 POM 直接依赖
```

**实际结果**

BOM `2026.02.01`：**240 条**，只覆盖 **7 个 groupId**（`androidx.compose.animation / foundation / material / material3 / material3.adaptive / runtime / ui`）。

| 关键条目 | 值 |
|---|---|
| `androidx.compose.ui:ui` | **1.10.4** |
| `androidx.compose.foundation:foundation` | 1.10.4 |
| `androidx.compose.runtime:runtime` | 1.10.4 |
| `androidx.compose.material3:material3` | **1.4.0** |
| `androidx.compose.material:material-icons-core` | ✅ **1.7.8（存在！）** |
| `androidx.compose.material:material-icons-extended` | ✅ **1.7.8（存在！）** |

> ⚠️ **本项实测推翻了一个既有错误前提**：`AGENT_GUIDE.md` 称
> "Compose BOM 2026.02.01 不再包含 `material-icons`"。**实测证明该说法错误**——
> BOM 仍然管理 `material-icons-core/-extended = 1.7.8`。官方变化是把它
> **从 material3 的传递依赖中移除**，因此*间接*引用会断，**显式声明即可恢复**。

**传递依赖实测（是否会拉高 BOM 的 Compose 1.10.4）**

| 依赖 | 版本 | 声明的 Compose 版本 | 判定 |
|---|---|---|---|
| `lifecycle-viewmodel-compose` | 2.11.0 | `runtime = **1.11.0**` | ❌ **高于 BOM，不用** |
| `lifecycle-viewmodel-compose` | **2.10.0** | `runtime = 1.9.0` | ✅ 推荐 |
| `lifecycle-runtime-compose` | 2.10.0 | `runtime = 1.9.0` | ✅ 推荐 |
| `navigation-compose` | 2.10.2 | `ui/runtime/animation = 1.10.5` | ⚠️ patch 上浮 |
| `navigation3-runtime` | 1.2.0 | `runtime = 1.9.5` | ✅ 最干净 |
| `activity-compose` | 1.13.0 | `runtime = 1.7.0` | ✅ 推荐 |

**结论**：BOM **不覆盖** lifecycle / navigation / activity / room / datastore →
必须显式写版本号，且**必须检查它们反向拉扯的 Compose 版本**。

---

## 后续阶段的测试记录规范

每个阶段记录格式：

```markdown
### T-P0X-NN 测试名称 ✅ 已执行 / ❌ 未执行

**目的**：
**执行方式**：（可直接复制的命令）
**实际输出**：（原始输出，不改写）
**结论**：
**未通过项**：（如有，附原因与后续动作）
```

**必须记录原始输出的项目**
- 构建结果（含 `BUILD SUCCESSFUL` / 错误行）
- 测试执行输出（通过/失败数）
- 安装结果（adb 输出）
- 崩溃检查输出
- 界面 dump 关键片段

---

## 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14 | 首次建立。记录 3 项实际执行的验证（环境自检、工程勘察、数据源核验）+ 8 项明确标注未执行的验证 |
