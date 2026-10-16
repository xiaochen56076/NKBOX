# LICENSES — 第三方开源许可证清单

> 最后更新：2026-10-14（P00）
> ⚠️ **本文件在 P00 阶段尚未完成正式核验。** 标注「待 P01 核验」的条目必须在 P01 阶段逐个查证后填写。
> 依据 `PROJECT_RULES.md` §5：任何新增依赖必须先完成 **版本 / License / 兼容性 / 维护状态** 四项检查。

---

## 一、本项目自身

| 项 | 值 |
|---|---|
| 项目名 | UtilityBox |
| 许可证 | ⚠️ **待用户确定**（建议在 P01 决定，如 MIT / Apache-2.0 / 私有） |

---

## 二、直接依赖（应用打包内含）

> 以下版本来自本机实测通过的版本基线（`AGENT_GUIDE.md` §5.2）。

| 依赖 | 版本 | 许可证 | 状态 |
|---|---|---|---|
| AndroidX Core KTX (`androidx.core:core-ktx`) | 1.10.1 | Apache-2.0 | 已知 |
| AndroidX Lifecycle Runtime KTX | 2.6.1 | Apache-2.0 | 已知 |
| AndroidX Activity Compose | 1.8.0 | Apache-2.0 | 已知 |
| Compose BOM (`androidx.compose:compose-bom`) | 2026.02.01 | Apache-2.0 | 已知 |
| Compose UI / ui-graphics / ui-tooling | （由 BOM 管理） | Apache-2.0 | 已知 |
| Compose Material3 | （由 BOM 管理） | Apache-2.0 | 已知 |
| Kotlin Stdlib | 2.2.10 | Apache-2.0 | 已知 |
| Kotlinx Coroutines | （随 Kotlin/Compose 引入） | Apache-2.0 | 待 P01 核验确切版本 |
| JUnit 4 | 4.13.2 | EPL-1.0 | ⚠️ 仅测试，不打包 |
| AndroidX Test JUnit (`androidx.test.ext:junit`) | 1.1.5 | Apache-2.0 | ⚠️ 仅测试 |
| Espresso Core | 3.5.1 | Apache-2.0 | ⚠️ 仅测试 |

**构建期工具**（不打包进 APK）

| 工具 | 版本 | 许可证 |
|---|---|---|
| Android Gradle Plugin | 9.2.1 | Apache-2.0 |
| Gradle | 9.4.1 | Apache-2.0 |
| Kotlin Compose 编译器插件 | 2.2.10 | Apache-2.0 |
| foojay-resolver-convention | 1.0.0 | Apache-2.0 |

---

## 三、P01 候选依赖（★ 已实测核验，2026-10-14）

**核验方法**：下载 AAR/JAR → 取 `classes.jar` → **JDK 21 `javap -v`** 读 `@Metadata(mv=)`；及直接解析各库 POM。
判定基准：**Kotlin 2.2.10 只能读 `mv ≤ 2.3.x`**（详见 `docs/risks.md` R-00）。

### 3.1 ✅ 可在 Kotlin 2.2.10 上使用

| 坐标 | 版本 | 实测 mv | License | minSdk | 备注 |
|---|---|---|---|---|---|
| `androidx.activity:activity-compose` | **1.13.0** | [2,1,0] | Apache-2.0 | — | **不拉高 Compose**（声明 runtime 1.7.0） |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | **2.10.0** | — | Apache-2.0 | — | ⚠️ **刻意不用 2.11.0**（后者拉 runtime 到 1.11.0） |
| `androidx.navigation:navigation-compose` | **2.10.2** | [2,1,0] | Apache-2.0 | — | 会把 ui/runtime 抬到 **1.10.5** |
| `androidx.navigation3:navigation3-runtime` + `-ui` | **1.2.0** | [2,1,0] | Apache-2.0 | — | **对 BOM 最友好**（runtime 1.9.5） |
| `androidx.compose.material:material-icons-core` | **1.7.8**（走 BOM） | [1,8,0] | Apache-2.0 | — | ✅ BOM **仍管理**它（已实测） |
| `kotlinx-serialization-json` | 1.11.0 | [2,3,0] | Apache-2.0 | — | 本项目暂不需要（用 `org.json`） |
| `androidx.room:room-runtime` / `room3:room3-runtime` | 2.8.5 / 3.0.3 | [2,1,0] | Apache-2.0 | — | 本项目暂不需要（用 SharedPreferences） |
| `androidx.datastore:datastore-preferences` | 1.2.1 | [2,0,0] | Apache-2.0 | — | 本项目暂不需要 |
| **`io.github.nadeemiqbal:liquid-glass`** | **0.2.3** | **[2,3,0]** | **Apache-2.0** | **24** | ⭐ **Kotlin 2.2.10 上唯一可用的液态玻璃** |
| `dev.chrisbanes.haze:haze` | **1.7.3** / **1.7.2** | [2,3,0] / [2,2,0] | Apache-2.0 | 23 | ⚠️ **仅纯模糊，无折射**（`haze-glass` 只在 2.x） |

### 3.2 ❌ 在 Kotlin 2.2.10 上**编译失败**（`mv=[2,4,0]`）

| 坐标 | 版本 | 实测 mv | License | 说明 |
|---|---|---|---|---|
| `dev.chrisbanes.haze:haze` / `:haze-glass` | 2.0.1 | **[2,4,0]** | Apache-2.0 | 真·折射式液态玻璃**只在 2.x** → 本基线完全不可用 |
| `io.github.kyant0:backdrop` | 2.0.1 | **[2,4,0]** | Apache-2.0 | 社区最热门（4.2k★）也用不了 |
| `com.github.skydoves:cloudy` | 0.7.1 / 1.0.0-alpha01 | **[2,4,0]** | Apache-2.0 | 两个版本都不可用 |
| `Mortd3kay/liquid-glass-android` | — | — | **未确认** | 无 Maven 坐标 + License 未确认 → **合规无法闭环，不用** |

> 若用户裁决**路线 B（升 Kotlin 2.4.x）**，3.2 表中的库即可解锁。
> `kotlin-gradle-plugin` 与 `kotlin-compose-compiler-plugin-embeddable` 的
> **2.4.10 / 2.4.21 均已实测为稳定版**。

### 3.3 许可证合规结论

✅ **本次全部候选库均为 Apache-2.0，未发现任何 GPL / AGPL / 商用受限库。**

需留意的**非许可证类风险**：
1. **Apache-2.0 的 NOTICE 义务**：上游若提供 `NOTICE` 文件，分发时需保留。
2. **外观设计（trade dress）**：Apache-2.0 覆盖**代码**，但不覆盖"在宣传中声称与 iOS 外观一致"的设计权利风险。本项目应在文案上**避免声称与 Apple 官方一致**（Liquid Glass 库作者本人也明确不承诺与 iOS 像素级一致）。

---

## 四、参考仓库（**不打包进 App**，仅开发期参考）

本机 `D:\CODE_Nskdfh_VS\android_Projects\_refs\` 下的参考仓库**不进入 APK**，仅用于接口与算法核验。

| 仓库 | 用途 | 许可证 | 使用方式 |
|---|---|---|---|
| `yunzai-df` | 接口文档、端点清单参考 | ⚠️ **待 P01 核验** | 仅阅读，不复制代码进 App |
| `astrbot-df` | 端点调用代码参考 | ⚠️ **待 P01 核验** | 仅阅读，不复制代码进 App |
| `GameMate` | 同机参考工程（接口实测记录） | ⚠️ 见该工程 `LICENSE` | 仅阅读参考 |

> ⚠️ **注意**：`GameMate/HANDOVER.md` 记载其 `assets/delta/` 下的数据来自**社区开源项目（AGPL-3.0）**，
> 并已做 AGPL 合规处理（`LICENSE` / `NOTICE.md` / 随包分发许可证）。
> **UtilityBox 若复用任何 AGPL 代码或数据，必须做同等合规处理；否则不得复用。**
> P00 阶段**未**复用任何此类资产。

---

## 五、数据来源声明（非软件许可，但必须声明）

| 项 | 内容 |
|---|---|
| 游戏数据版权 | **腾讯有限公司** |
| 接口提供方 | 社区第三方（`delta-test-api.shallow.ink`），**非腾讯官方** |
| 使用性质 | 仅供技术学习与个人使用，**不得商用** |
| 免责 | 应用内必须如实声明数据来源，并注明"以游戏内数据为准" |

---

## 六、P01 待完成事项

- [ ] 逐个核验上表所有「待核验」条目的确切版本与 License 文本
- [ ] 核验 `_refs/` 两个参考仓库的 License，确认未复制受限代码
- [ ] 确认是否引入任何第三方库（含 Liquid Glass、Navigation、图表）
- [ ] 确定 UtilityBox 自身 License
- [ ] 生成 APK 后核对最终打包的依赖清单
- [ ] 在应用内「关于 → 开源许可」页面展示本清单

---

## 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14 | 首次建立（P00）。记录版本基线的已知 Apache-2.0 依赖；Liquid Glass 与 Navigation 候选**故意留空**，待 P01 验证后填写 |
