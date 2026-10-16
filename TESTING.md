# 测试策略

> 本文记录**怎么测**。`PROJECT_RULES.md` 记录**不许做什么**，`TEST_REPORT.md` 记录**测过什么**。
> 三份各管一段，不要互相抄。

---

## 一、测试目标优先级（P11 补 19 起）

| 优先级 | 目标 | 说明 |
|---|---|---|
| **1** | **`dsh_pixel10` 模拟器** | 默认测试目标。可控、可脚本化、不会掉线 |
| 2 | 用户真机 `10AE6F2JP4004QB` | 只在**需要真机特有结论**时才用 |

### 为什么改成模拟器优先

真机在本项目里**反复掉线**：`adb devices` 只剩模拟器、`reconnect offline` 无效、
`kill-server` + `start-server` 也无效，只有重新插拔才好。一整轮 P11 补 16–18 里，
真机安装失败了 4 次、截图失败 3 次，大量时间花在重试而不是验证。

模拟器是 `emulator-5556` 上常驻的 AVD，从会话开始到现在**一次都没掉过**。

### 什么时候仍然必须上真机

模拟器**测不出来的**东西，不要在模拟器上"验证"然后声称通过：

| 事项 | 为什么模拟器不算数 |
|---|---|
| 拉伸回弹（stretch overscroll） | 模拟器用软件渲染，**只有位移没有形变**；真机才有 |
| 动画流畅度 / 掉帧 | 模拟器 12–17 fps，任何滑动都不跟手 |
| 深色主题观感、玻璃（Haze）效果 | 模拟器渲染不出真实的模糊合成 |
| 冷启动耗时 | 模拟器 I/O 与真机差异极大 |
| 触摸手感（跟手程度、惯性） | 同上 |

**这些项在模拟器上跑出来的结果，只能写成"未验证"**，不能写成"通过"。

---

## 二、工具：`scripts/emu.ps1`

把 adb 操作固化成一条命令，避免每轮手敲长串 adb（既不可复用，也无法保证前后一致）。

```powershell
.\scripts\emu.ps1 install              # 构建 + 安装 + 启动
.\scripts\emu.ps1 flow                 # 冒烟：鸣潮 → 图鉴 → 共鸣者 → 首个词条，逐级截图
.\scripts\emu.ps1 ui                   # dump 界面层级，列出「文字 → 中心坐标」
.\scripts\emu.ps1 shot 05_xxx          # 截图到探针目录
.\scripts\emu.ps1 tap 452 1180         # 点击
.\scripts\emu.ps1 key <APIKEY>         # 注入 API Key（模拟器专用）
.\scripts\emu.ps1 log                  # 崩溃 + 应用日志
.\scripts\emu.ps1 reset                # 清空应用数据
```

★ **找坐标用 `ui`，不要靠肉眼从截图估。** 截图是 1080×2424，预览会被缩放到 836 宽，
按预览估坐标要心算一次缩放，很容易差几十像素 —— 点空了还以为是"界面没响应"。

---

## 三、★ 模拟器的 API Key 缺口（已解决）

模拟器**没有 API Key**，所以三角洲的四个工具一直是「还没填 API Key」的空态 ——
等于**四个工具在默认测试目标上根本无法测试**。这是"模拟器优先"最大的一个洞。

**解决办法**：`KeystoreSecretStore.getApiKey()` 的实现是

```kotlin
val stored = prefs.getString(KEY_API, null) ?: return ""
return decrypt(stored) ?: stored     // ← 解密失败会**回退当成明文**
```

所以直接用 `run-as` 往 SharedPreferences 写一个明文值就能生效，
**不需要 UI 自动化去点输入框、也不需要绕过 Keystore**。

```powershell
.\scripts\emu.ps1 key <APIKEY>
```

### 约束（必须遵守）

1. **仅限模拟器**。模拟器是 debug 构建且可 `run-as`；真机不可用也不该用。
2. **key 不写进任何文件、日志、提交**。脚本刻意不回显它。
3. 该动作只做"把用户自己的 key 放进测试设备"，**不产生、不获取、不传输 key**。

---

## 四、模拟器环境事实（照抄，不要重新试错）

| 项 | 值 |
|---|---|
| AVD 名 | `dsh_pixel10` |
| **端口** | **必须 `-port 5556`** —— 用默认 5554 连不上 |
| 设备号 | `emulator-5556` |
| 分辨率 / 密度 | 1080 × 2424 @ 420dpi |
| 渲染 | 软件渲染（swiftshader），约 12–17 fps |
| emulator.exe | `D:\CODE_Nskdfh_VS\android SDK\emulator\emulator.exe` |
| API 级别 | 36 |

### 常用坐标（1080×2424）

| 目标 | 坐标 |
|---|---|
| 底栏 三角洲 / 鸣潮 / 我的 | (251, 2268) / (633, 2268) / (936, 2268) |
| 鸣潮页「鸣潮图鉴」 | (452, 1180) |
| 图鉴首页「共鸣者」 | (114, 518) |
| 共鸣者列表首个词条 | (258, 452) |

★ 坐标会随布局调整失效。**用 `emu.ps1 ui` 重新确认**，不要盲信这张表。

---

## 五、应用私有存储位置（脚本与调试用）

| 用途 | 路径 |
|---|---|
| 设置 | `shared_prefs/utilitybox_settings.xml` |
| **密钥（API Key / 库街区 token）** | `shared_prefs/utilitybox_secrets.xml`、`shared_prefs/kuro_auth.xml` |
| 数据缓存 | `shared_prefs/utilitybox_cache.xml` |
| 图鉴缓存 | `files/wuwa_wiki/` |
| 唤取记录 | `files/wuwa_gacha/` |
| 启动页背景 | `files/splash/bg.jpg` |
| 图片磁盘缓存 | `cache/ub_img/` |

★ `utilitybox_secrets.xml` 与 `kuro_auth.xml` 里是**用户凭据**。
调试时可以看文件是否存在、大小多少 —— **不要把内容打印出来**。

---

## 六、每轮改动的验证下限

改完代码，声称"完成"之前至少要：

1. `:app:testDebugUnitTest` **全绿**（当前 289 例）
2. `:app:assembleDebug` 成功
3. `.\scripts\emu.ps1 install` 装到模拟器并**启动不崩**
4. 改到了界面 → `.\scripts\emu.ps1 flow`（或手动走到那个页面）**截图确认**
5. `.\scripts\emu.ps1 log` 确认 `-b crash` 为空

**涉及真机特有项**（见第一节表格）→ 在文档里写**「未验证」**，不要写成通过。

---

## 八、踩过的坑（写下来，避免重复踩）

### 8.1 `crash` buffer 里有内容 ≠ 我们的应用崩了

跑完 `emu.ps1 ui` 后 `logcat -b crash` 里会出现：

```
E AndroidRuntime: FATAL EXCEPTION: UiAutomation
E AndroidRuntime: java.lang.RuntimeException: Bad file descriptor
```

**这是 `uiautomator` 工具进程自己崩的，不是应用。** 区分方法：

```powershell
adb -s emulator-5556 shell "ps -A | grep utilitybox"      # 应用还活着吗
adb -s emulator-5556 logcat -d -b crash | Select-String utilitybox   # 0 行 = 不是我们
```

★ **先查清崩的是谁再下结论。** 看到 crash buffer 非空就写"应用崩溃"，
和看到空就说"无崩溃"一样，都是没验证的结论。

### 8.2 PowerShell 5.1 的相关陷阱（`scripts/emu.ps1` 上真实踩过）

| 坑 | 表现 | 处理 |
|---|---|---|
| **`.ps1` 必须带 UTF-8 BOM** | 5.1 按 GBK 解码中文 → 乱码字节冲掉字符串引号 → 报 ``"<"运算符是为将来使用而保留的`` 之类的怪错误 | 写完脚本**必须确认 BOM 还在**（`edit` 工具会把它去掉） |
| `$ErrorActionPreference='Stop'` + native 命令 | adb 把进度写 stderr（`1 file pulled`），被当成终止错误中断脚本 | 用 `'Continue'`，靠**显式检查返回值**判断成败 |
| **管道输出 + `Select-Object -First N`** | 提前终止管道抛 `PipelineStoppedException`，**静默杀掉整个脚本**（退出码 0、零输出） | 脚本用 `Write-Host`（宿主流，不受管道截断影响） |
| `.NET` 静态方法用相对路径 | `[IO.File]::ReadAllBytes(".\x")` 按**进程当前目录**解析，`Set-Location` 不影响它 | 用绝对路径，或改用 PowerShell cmdlet |

★ 写 PowerShell 脚本用 `write`/`edit` 工具（不要用 shell 重定向生成），
写完立即复查 BOM 与语法。

### 8.3 ★ 只验证"有输出" ≠ 验证"输出对了"（同一模式犯了两次）

**第一次**：`emu.ps1 ui` 只打印了 `找到 N 个有文字的节点：`，
坐标因为 bounds 解析 bug **一个都没输出**，而我看第一行正常就报"实测可用"。

**第二次**：装完 APK 后

```powershell
& ".\scripts\emu.ps1" install | Select-String "Success|失败" | Select-Object -First 1
```

`Select-String` **默认大小写不敏感**，所以 gradle 的 `BUILD SUCCESSFUL` 也匹配 `Success`；
`-First 1` 把真正的 `Performing Streamed Install Success` 挤掉了。
**我见"有一行匹配"就当成装上了** —— 结果后面截的图全是**旧 APK**，
还据此得出"修复没生效"的错误结论，白查了一轮 `UbToolPage`。

#### 规矩

| 场景 | 必须这样验 |
|---|---|
| 安装 APK | 显式 `Select-String "Performing Streamed Install"`，或先滤掉 gradle 行再取第一条 |
| 跑脚本看输出 | **看完整个输出**，不能只看第一行 |
| 看截图判断 | 先确认设备上跑的**就是**刚构建的版本（比对 APK 时间戳/大小） |
| 界面元素是否存在 | 用 `uiautomator` 查节点与 bounds（比读截图可靠，能给出确切坐标） |

★ 一句话：**"命令没报错"和"结果正确"是两件事。**
判定"通过"必须指向**具体的那一条证据**，而不是"输出里出现了某个关键词"。

---

## 七、单元测试的取舍

现有 289 例集中在**会静默出错**的纯函数上，这是有意的：

| 优先测 | 理由 |
|---|---|
| 数值换算、解析、去重、边界 | 错了界面看着正常，没人会发现 |
| 安全相关（转义、凭据不落日志） | 错了的代价不可逆 |
| 状态机语义（如导航栈、入场闸门） | 表现为"偶发"，手工测极难稳定复现 |

**不测**：Compose 布局细节（改了外观就要改测试，收益极低）。
本项目目前**没有** UI 测试（`androidx.compose.ui.test`）、没有覆盖率工具、没有 Macrobenchmark ——
这些是已知缺口，不是遗漏。
