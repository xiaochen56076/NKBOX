# PROJECT_RULES — UtilityBox 不可随意改变的项目规则

> 本文件是项目的**最高约束**。任何修改必须先在此登记，再改代码。
> 建立时间：2026-10-14（P00 阶段）
> 依据：设计稿《设计稿ubox.docx》第七节「项目总开发指令」

---

## 一、产品身份

| 项 | 值 | 可变性 |
|---|---|---|
| 项目名称 | UtilityBox | 冻结 |
| 产品定位 | 可持续扩展的 Android 游戏工具箱 | 冻结 |
| 首期游戏 | 中国大陆版《三角洲行动》 | 冻结 |
| 目标平台 | Android | 冻结 |
| 开发语言 | Kotlin | 冻结 |
| UI 框架 | Jetpack Compose | 冻结 |
| 架构 | MVVM + 分层架构 + 合理模块化 | 冻结 |
| UI 风格 | Apple 原生系统风格 | 冻结 |
| 视觉效果 | Liquid Glass 液态玻璃，**必须支持降级** | 效果可变，降级能力不可删 |
| 主题 | 浅色 / 深色 / 跟随系统 | 冻结 |
| 应用包名 | `com.utilitybox` | 冻结（改了会导致无法覆盖安装） |

> ⚠️ **AI 开发助手（DeepSeek）不是应用的运行依赖。**
> 最终 APK 必须能独立安装和运行，不得在手机上依赖任何 AI 服务。

---

## 二、红线规则（违反即视为交付失败）

1. **禁止编造数据源。** 不得虚构游戏 API、密码、价格、历史趋势、好友状态。
2. **禁止把模拟数据当真实功能交付。** 开发期可用 mock，但必须在代码与 UI 上显式标记 `MOCK`（见规则 8）。
3. **禁止把编译成功等同于功能完成。** 声称完成前必须有可核验的运行证据（构建日志、安装结果、界面 dump、测试输出）。
4. **禁止用空按钮冒充已完成交互。** 没有实现的按钮要么不显示，要么点击后给出明确说明。
5. **禁止跳过 P00～P11 阶段**，禁止在前一阶段存在重大未解决问题时盲目推进。
6. **禁止在未获用户批准时跨阶段推进。** 每阶段结束必须停下等待批准。
7. **禁止在项目文档里写"未执行"却表述为"已完成"。** 未执行的构建/测试/真机验证必须显式标注「未执行」。
8. **禁止模拟数据与真实数据混淆。** 所有 mock 数据源类名必须以 `Mock` 开头，UI 必须显示醒目标记。
9. **禁止业务模块互相依赖。** 详见 `ARCHITECTURE.md` 依赖规则。
10. **禁止把《三角洲行动》的业务规则写进公共层（`core/`）。**
11. **禁止擅自升级版本基线。** 见第四节。
12. **禁止提交 `local.properties`、API Key、Token、Cookie 到任何仓库或截图。**
13. **禁止在未获用户明确授权时引入账号登录。** 见下方「关于账号凭据的唯一例外」。
14. **禁止在模拟器上"验证"模拟器测不出来的东西。** 见下方「测试目标」。
    触摸手感、动画流畅度、拉伸回弹、深色观感、玻璃效果、冷启动耗时 ——
    这些在模拟器上跑出来的结果只能写成**「未验证」**，写成"通过"等同于造假。

### 测试目标（用户于 P11 补 19 定下）

> **后续测试优先使用虚拟机（模拟器）。**

| 优先级 | 目标 |
|---|---|
| 1 | `dsh_pixel10` 模拟器（`emulator-5556`，**必须 `-port 5556`**） |
| 2 | 用户真机 `10AE6F2JP4004QB` —— 只用于模拟器测不出来的项 |

**理由**：真机在本项目里反复掉线（`adb devices` 只剩模拟器、
`kill-server` + `start-server` 也无效，只有重新插拔才好）。
P11 补 16–18 一轮里真机安装失败 4 次、截图失败 3 次，时间花在重试而不是验证。
模拟器同期**一次都没掉过**。

**工具**：`scripts/emu.ps1`（`install` / `flow` / `ui` / `shot` / `tap` / `key` / `log` / `reset`）。
完整策略、坐标表、存储位置、验证下限见 **`TESTING.md`**。

★ **模拟器没有 API Key 会导致三角洲四个工具无法测试** —— 这是"模拟器优先"最大的洞。
已用 `emu.ps1 key`（`run-as` 写明文，利用 `decrypt ?: stored` 的回退）解决，
但**该动作仅限模拟器，且 key 不得进入任何文件、日志或提交**。


### 关于账号凭据的唯一例外（P11 补 16，用户明确裁决）

**背景**：本项目自 P00 起禁止 Cookie / 账号密码登录。P11 用户要求给鸣潮标签页接入
库街区（kurobbs）数据，而库街区的**全部玩家数据接口都要求用户登录令牌**
（`token` 请求头 + `Cookie: user_token=…`，token 只能由手机号 + 短信验证码登录取得）。

**用户在被告知下列全部事实后，明确选择接入**：

1. token 等于其库街区账号的访问权（可读绑定角色、可签到、可领库洛币）
2. **库街区 APP 端同一时间只允许一个登录** —— 在此应用登录会让官方 App 下线，
   反之亦然。这会导致用户需要反复重登
3. 库洛的短信接口可能要求极验人机验证（`data.geeTest == true`），届时短信不会发出

**因此本规则修订为**：

> 允许**唯一一处**使用账号凭据的功能：`game/wutheringwaves/kuro/`（库街区接入）。
> 它必须始终满足以下**全部**约束，缺一即视为交付失败：

| # | 约束 |
|---|---|
| 1 | 凭据**只存**应用私有目录（`MODE_PRIVATE`），不导出、不备份 |
| 2 | 凭据与短信验证码**绝不进入日志、异常信息、界面文本** |
| 3 | 凭据**不写入 `UbSettings`** 等会被整体序列化/dump 的通用对象 |
| 4 | 只做**读**操作；不实现签到、领取奖励等任何写操作 |
| 5 | 提供显式的**退出登录**入口，退出即从本机删除凭据 |
| 6 | 登录页必须**明示**上述第 1–3 条事实，不得淡化 |
| 7 | 除该目录外，**任何其它模块不得引入账号登录** |

**其它游戏/数据源若也想走账号登录，必须重新取得用户授权，不得援引本例外。**

### 关于 `KuroCredentialStore` 未使用加密存储

`androidx.security:security-crypto` 是新增依赖，与本项目「零新增依赖」冲突。
私有目录本身受 Android 沙箱保护（非 root 设备上其它应用不可读）。
**这是一个被记录的取舍，不是遗漏** —— 见该文件顶部注释。

---

## 三、数据真实性规则（设计稿第四节，逐条落地）

1. 好友状态排序固定为：**游戏中 → 在线 → 离线 → 未知**。
2. **只有数据源能可靠提供"游戏中"状态时才显示该状态。** 否则该状态不出现，不得猜测。
3. **未知状态绝不能自动转换成离线。** 未知就是未知。
4. 今日密码必须区分四种状态：`当天有效` / `过期缓存` / `暂无数据` / `请求失败`。
5. **没有当天有效数据时，不得拿昨天的密码冒充今日密码。** 过期缓存必须显示原始抓取日期。
6. 物品价格必须显示**来源**和**更新时间**。
7. 历史趋势图**只能使用真实获取或真实记录的价格数据**，不得插值伪造、不得平滑成假曲线。
8. 数据源状态分级：`已验证` / `待验证` / `受限` / `不可用`，并记录在 `DATA_SOURCES.md`。

---

## 四、版本基线（★ P01 实测后更新，照抄，不得随意升级）

已在本机实测通过（来源：`AGENT_GUIDE.md` 第 5.2 节 + 本项目 P01 实测）。

```toml
agp        = "9.2.1"
kotlin     = "2.4.21"   # ★ 由 2.2.10 提升（用户于 P01 裁决，见变更 1）
composeBom = "2026.09.00"   # ★ 由 2026.02.01 提升（用户于 P04 裁决，见变更 2）
                        #   实测映射：ui/foundation/runtime/animation-core = 1.12.1，material3 = 1.4.0
haze       = "2.0.1"    # ★ 液态玻璃库（用户于 P04 裁决，见变更 2）
gradle     = "9.4.1"
compileSdk = 37.0   # ★ 由 36.1 提升（见变更 1 后的附注）
minSdk     = 24
targetSdk  = 36
```

### ★ 变更 2：`composeBom` 由 `2026.02.01` → `2026.09.00` + 引入 Haze 2.0.1（用户裁决，2026-10-14）

**原因**：用户要求「用现成的液态玻璃组件库」，并在看过并排对比截图后裁决
**「显然那两种好，自己写的不好，按照那两种的来」**。

自研玻璃（`Modifier.blur` + 半透明覆盖）只能做**整体均质模糊**，做不出**边缘折射**；
且实测自研版本的胶囊边缘会溢出底色。Haze 2.0.1 的 `haze-glass` 提供
折射、菲涅尔边缘提亮、色散、光源位置、材质随尺寸自适应，以及无障碍挂钩。

**为什么 BOM 必须一起升**：`haze-glass:2.0.1` 依赖 `androidx.compose.ui 1.12.1`。
实测「保 BOM 1.10.4 + 用 Haze 2.x」**做不到**：

```
> Component is the target of multiple version constraints with conflicting requirements:
> Could not resolve androidx.compose.runtime:runtime:{strictly 1.10.4}.
BUILD FAILED
```

**因此选择与库匹配的 BOM，而不是让库单方面顶版本**：`2026.09.00` 精确钉住
`ui/foundation/runtime/animation-core = 1.12.1`、`material3 = 1.4.0`，
与 Haze 2.0.1 的要求**完全吻合**。实测验证：

```
+--- androidx.compose:compose-bom:2026.09.00
|    +--- androidx.compose.foundation:foundation:1.12.1 (c)
|    +--- androidx.compose.material3:material3:1.4.0 (c)
|    +--- androidx.compose.runtime:runtime:1.12.1 (c)
|    +--- androidx.compose.ui:ui:1.12.1 (c)
```

**代价与风险**：Compose 从 1.10.4 跳到 1.12.1，影响面覆盖全部 8 类页面。
已做的缓解：BOM 与库版本对齐（不是被顶）；全部页面在设备上跑过（零崩溃）。
**未做的**：性能测量、API < 31 降级验证（见 `KNOWN_ISSUES.md` ISSUE-006/015）。

**完整依据**：`docs/liquid-glass-library-eval.md`（8 个坐标的 Maven 实测 + 真实构建 + 截图对比）。

> **新的依赖红线（补充到第 5 节第 7 条）**：
> 引入任何依赖前，必须实测它是否会把 `androidx.compose.*` 抬高于 BOM 锁定值。
> 若会抬升，**必须同时把 BOM 换到与该库匹配的版本**，不可放任版本被顶高 ——
> 否则 BOM 名不副实，且运行时用的是一套从未验证过的组合。

### ★ 变更 1：`kotlin` 由 `2.2.10` → `2.4.21`（用户裁决，2026-10-14）

**原因**：Kotlin 编译器只能读取「自身版本 **+1 个 minor**」的 metadata。
Kotlin 2.2.10 上限为 `@Metadata mv ≤ 2.3.x`，因此**无法编译 `mv=[2,4,0]` 的库**：

| 库 | 实测 mv | Kotlin 2.2.10 | Kotlin 2.4.21 |
|---|---|---|---|
| `dev.chrisbanes.haze:haze:2.0.1` + `haze-glass:2.0.1` | `[2,4,0]` | ❌ 编译失败 | ✅ **产出 12,141,445 B APK** |
| `io.github.kyant0:backdrop:2.0.1` | `[2,4,0]` | ❌ | 🟡 接入中 |

**实测依据**：同一份源码、同一套依赖，仅 Kotlin 版本不同，两工程对照构建
（`ubox-verify` vs `ubox-verify-b`），结果见 `TEST_REPORT.md` T-P01-01。

**影响范围**：解锁折射式液态玻璃（`haze-glass`）。
**已验证不受影响**：其余 5 个变体（官方导航 / 自研导航 / 平台模糊 / liquid-glass / haze 1.7.3）
在 **2.2.10 与 2.4.21 下均构建成功**，故本次升级**不引入回归风险**。

> ⚠️ **配套要求**：Compose 编译器插件版本必须**等于** Kotlin 版本
> （`org.jetbrains.kotlin.plugin.compose` = `2.4.21`）。

### ★ 变更 2：`compileSdk` 由 `36.1` → `37.0`（用户裁决，2026-10-14）

**原因**：以下依赖通过 **AAR metadata** 强制要求 `compileSdk ≥ 37`
（实测报错原文：`Dependency 'X' requires ... compile against version 37 or later ... :app is currently compiled against android-36.1`）：

| 依赖 | 它拉入的版本 | 要求 |
|---|---|---|
| `navigation-compose 2.10.2` | 经 `navigation-common` → `lifecycle 2.11.0` | compileSdk 37 |
| `haze 1.7.3` | Compose **1.12.0** | compileSdk 37 |
| `haze 2.0.1` / `haze-glass 2.0.1` | Compose **1.12.1** | compileSdk 37 |
| `backdrop 2.0.1` | Compose **1.12.0** + `shapes 1.2.1` | compileSdk 37 |

**前置条件（已在本机执行）**：安装 `platforms;android-37.0`
```powershell
# ★ 必须用新版 android CLI，且用 "/" 作包分隔符
#   （sdkmanager.bat 会被分号拆参数，报 "Package platforms not found"）
& '<SDK>\cmdline-tools\latest\bin\android.exe' --sdk='<SDK>' sdk install 'platforms/android-37.0'
```

**影响范围**：仅决定**编译期可用的 API 集合**。
**不影响** `targetSdk`（仍 36）与 `minSdk`（仍 24）→ **运行时行为与可安装设备范围不变**。

---

强制配套写法：

```kotlin
// AGP 9 新 DSL —— 不是 compileSdk = 37
compileSdk { version = release(37) { minorApiLevel = 0 } }
```

强制配套写法：

```kotlin
// AGP 9 新 DSL —— 不是 compileSdk = 36
compileSdk { version = release(36) { minorApiLevel = 1 } }
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official
android.overridePathCheck=true
```

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
```

```properties
# local.properties —— 不提交到 git
sdk.dir=D\:\\CODE_Nskdfh_VS\\android SDK
```

`settings.gradle.kts` **必须**包含 foojay 插件解析器，否则 toolchain 下载失败：

```kotlin
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
```

> 任何版本变更必须：① 在本文件登记理由；② 在 `CHANGELOG.md` 记录；③ 实际构建验证通过。

---

## 五、依赖规则

1. **零新依赖优先。** 能用平台内置 API 就不引第三方库。
2. 已知可用替代（P00 决策）：
   - 网络 → `HttpURLConnection`（不引 OkHttp / Retrofit）
   - JSON → 内置 `org.json`（不引 Gson / Moshi）
   - 持久化 → `SharedPreferences` + JSON 字符串（不引 Room / DataStore）
   - 图标 → Compose `Canvas` / `Path` 手绘
3. **任何新增依赖必须在此之前完成四项检查**：当前版本、License、与版本基线的兼容性、维护状态。结论写入 `ARCHITECTURE.md` 与 `LICENSES.md`。
4. **禁止引入 GPL / AGPL / 商用受限库**（除非用户明确同意并完成合规处理）。
5. **Kotlin metadata 天花板（硬约束）**：Kotlin 编译器只能读取「自身版本 **+1 个 minor**」的 metadata。
   Kotlin 2.2.10 → 上限 **`@Metadata mv ≤ 2.3.x`**。
   **任何依赖入库前，必须实测其 `mv` 值**（方法见 `docs/risks.md` R-00）。
   > ⚠️ **不要用 POM 里声明的 `kotlin-stdlib` 版本推断**——那不等于编译该库所用的 Kotlin 版本。
6. **图标（已修正）**：Compose BOM 的 240 条中**仍然管理** `androidx.compose.material:material-icons-core = 1.7.8`
   与 `material-icons-extended = 1.7.8`。官方变化是把它**从 material3 的传递依赖中移除**，因此
   *间接*引用会断，**显式声明即可恢复 `Icons.Default.*`**。
   - 本项目**首选仍为 Compose `Canvas` / `Path` 手绘**（零依赖优先、视觉可控）。
   - 若确实需要图标库，显式加 `material-icons-core`（**不给版本号**，走 BOM）；`material-icons-extended` 官方指出会显著拖慢构建，**不采用**。
7. **禁止引入会把 Compose 版本拉高于 BOM 锁定值的依赖**（BOM 只管理 `androidx.compose.*` 六个 group）。
   已知陷阱与正确版本见 `docs/risks.md` R-02。
   > **★ P04 补充（实测教训）**：若某个必需依赖**确实**要求更高的 Compose，
   > **正确做法是把 BOM 一起换到与该库匹配的版本**，而不是放任版本被单方面顶高。
   > 判定步骤：① `gradle dependencies` 看实际解析版本；
   > ② 在 Google Maven 的 `compose-bom/maven-metadata.xml` 里找钉住该版本的那一版 BOM；
   > ③ 换 BOM 后再看解析结果，确认是「BOM 满足库」而不是「库顶高版本」。
   > 实测案例见 `docs/liquid-glass-library-eval.md`（`haze-glass:2.0.1` → BOM `2026.09.00`）。

---

## 六、模块边界铁律

1. 依赖方向**只能**是：`feature → game/* → domain ← core`。
2. `core/` **不得** import 任何 `game.deltaforce.*` 或 `feature.*`。
3. `game/deltaforce/*` 三个功能模块**互不依赖**。
   - 好友模块不得调用今日密码的 ViewModel。
   - 物价模块不得直接操作好友模块的缓存。
4. 跨模块通信只能经由 `domain/` 中的接口，或通过工具注册表解耦。
5. 删除任一功能模块，其余模块必须仍能编译。
6. **不引入动态代码加载 / 外部插件机制。** 设计稿明确首期不做（安全风险）。
   工具注册只负责**描述、注册、启用状态与导航关联**，全部在编译期完成。
   > 说明：设计稿目录示例中的 `core/plugin/` 在本项目的落地位置是
   > `core/tool/`（注册机制）+ `domain/tool/`（契约）。**不建立 `core/plugin/` 包**，
   > 避免"plugin"一词暗示动态加载能力。

---

## 七、开发环境铁律（本机特有）

1. **`JAVA_HOME` 未持久化**，每个新进程必须显式设置：
   ```powershell
   $env:JAVA_HOME = 'D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21'
   ```
2. 本机**没有可用的 Android Studio GUI**，一切靠命令行 + adb。
3. 模拟器：AVD `dsh_pixel10`，端口 **5556**（`emulator-5556`），冷启动约 12 秒。
4. 构建/安装/界面验证统一走 `D:\CODE_Nskdfh_VS\android_Projects\devtools.ps1`。
5. **`JAVA_HOME` 不设就构建 = 必然失败**，不要浪费一轮排查。
6. 读 uiautomator XML **必须**显式 UTF-8，否则中文乱码：
   ```powershell
   [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
   ```
7. 界面验证**一律按文字定位**（算 bounds 中心），**禁止硬编码坐标**。
8. 启动后第一次点击会被动画吃掉，**等 6–8 秒再点**。
9. 修改 `devtools.ps1` 必须存为**带 BOM 的 UTF-8**。

---

## 八、Mock 数据标记规范

开发期在数据源未就绪时允许 mock，但必须满足全部三条：

1. 类名以 `Mock` 开头（如 `MockFriendStatusDataSource`）。
2. 数据模型携带 `isMock: Boolean = true`。
3. UI 上有可见标记 —— 统一组件 `MockBadge`，文案「模拟数据」。

> 交付前必须能通过全局搜索 `isMock = true` 定位所有残留 mock。

---

## 九、文档即长期记忆

**项目文件才是长期开发依据，聊天记录只是辅助。**

每阶段结束必须更新：

| 文件 | 作用 |
|---|---|
| `PROJECT_RULES.md` | 本文件，不可随意改变的项目规则 |
| `ROADMAP.md` | 开发阶段与计划 |
| `ARCHITECTURE.md` | 模块边界、依赖方向、设计决策 |
| `TASKS.md` | 待办与问题 |
| `CHANGELOG.md` | 每次修改记录 |
| `DATA_SOURCES.md` | 数据源、验证结果与限制 |
| `TEST_REPORT.md` | **真实执行过**的测试 |
| `KNOWN_ISSUES.md` | 已知问题及进度 |

---

## 十、规则变更流程

1. 在 `CHANGELOG.md` 写明：改哪条、为什么改、影响范围。
2. 在本文件对应位置直接修订，并保留变更日期。
3. 涉及依赖或版本基线的变更，必须附**实际构建验证结果**。
