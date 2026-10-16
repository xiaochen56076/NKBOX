# UtilityBox 模拟器测试台
#
# 用法（在项目根目录）：
#   .\scripts\emu.ps1 build                # 只构建
#   .\scripts\emu.ps1 install              # 构建 + 安装 + 启动
#   .\scripts\emu.ps1 shot 03_detail       # 截图并保存到探针目录
#   .\scripts\emu.ps1 tap 258 452          # 点击
#   .\scripts\emu.ps1 ui                   # dump 界面层级（找坐标用这个，不要靠肉眼猜）
#   .\scripts\emu.ps1 key <APIKEY>         # 注入 API Key（模拟器专用，见下）
#   .\scripts\emu.ps1 clearkey             # 清除 API Key
#   .\scripts\emu.ps1 log                  # 崩溃日志 + 应用日志
#   .\scripts\emu.ps1 reset                # 清空应用数据
#   .\scripts\emu.ps1 flow                 # 一条常见冒烟路径
#
# ## 为什么要有这个脚本
#
# 用户于 P11 补 19 定下「后续测试**优先使用虚拟机**」。之前每轮测试都是手敲一长串
# adb 命令，散落在会话里，既不可复用也没法保证一致。把它固化下来：
#   · 设备选择、探针目录、截图命名都有默认值，不再每次拼路径
#   · `key` 动作解决了模拟器**没有 API Key** 这个大洞（见下）
#
# ## 关于 API Key（★ 重要）
#
# 模拟器上三角洲那几个工具一直是「还没填 API Key」的空态，等于**四个工具在模拟器上
# 无法测试** —— 这会让「模拟器优先」出现一个覆盖不到的大洞。
#
# 做法：`KeystoreSecretStore.getApiKey()` 是 `decrypt(stored) ?: stored` ——
# **解密失败会回退把内容当明文读**。所以直接用 `run-as` 往 SharedPreferences 里
# 写一个明文值就能生效，不需要 UI 自动化去点输入框。
#
# ⚠️ 仅限模拟器：模拟器是 debug 构建 + 可 `run-as`。真机不可用也不该用。
# ⚠️ 不要把这个 key 写进任何文件、日志或提交。
#
# ## 探针目录
#
# 截图/日志落在 `$ProbeRoot\<Name>\`，与之前手敲时的目录保持一致，便于对照历史截图。

[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [string]$Action,

    [Parameter(Position = 1)]
    [string]$A1,

    [Parameter(Position = 2)]
    [string]$A2,

    # 探针输出根目录
    [string]$ProbeRoot = "D:\APPDATA\PCD\DSH\ubox-probe",

    [string]$Name = "emu",

    # 模拟器 AVD 名
    [string]$Avd = "dsh_pixel10",

    # 端口：★ 这台机器的 AVD 必须用 5556，用默认 5554 会连不上
    [int]$Port = 5556
)

# ★ 不用 "Stop"：adb 会把进度信息写进 **stderr**（例如 "1 file pulled"），
#   而 PowerShell 5.1 在 ErrorActionPreference='Stop' 下会把 native stderr
#   当成终止错误并中断脚本。本脚本一律靠**显式检查返回值**判断成败，不靠异常。
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Sdk    = "D:\CODE_Nskdfh_VS\android SDK"
$Adb    = Join-Path $Sdk "platform-tools\adb.exe"
$Emu    = Join-Path $Sdk "emulator\emulator.exe"
$Serial = "emulator-$Port"
$Root   = Split-Path -Parent $PSScriptRoot
$Apk    = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
$Pkg    = "com.utilitybox"

# 应用内部的存储位置（取自源码常量，改源码时要同步改这里）
$PrefsSecrets = "utilitybox_secrets"
$KeyApi       = "api_key"

$OutDir = Join-Path $ProbeRoot $Name
function Ensure-Out { if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null } }

function Get-State {
    $s = (& $Adb -s $Serial get-state 2>&1) -join ""
    return $s.Trim()
}

function Wait-Device([int]$TimeoutSec = 90) {
    for ($i = 0; $i -lt ($TimeoutSec / 3); $i++) {
        if ((Get-State) -eq "device") { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Ensure-Device {
    if ((Get-State) -eq "device") { return }
    Write-Host "模拟器未就绪，尝试启动 AVD '$Avd'（port $Port）..."
    # ★ 用后台任务启动：emulator 是前台程序，直接调用会把脚本挂住
    Start-Process -FilePath $Emu -ArgumentList @("-avd", $Avd, "-port", "$Port", "-gpu", "swiftshader_indirect", "-no-snapshot-load") -WindowStyle Minimized
    if (-not (Wait-Device 180)) { throw "模拟器在 180 秒内没有就绪。手动启动：`n  & '$Emu' -avd $Avd -port $Port" }
    Start-Sleep -Seconds 5
}

function Invoke-Build {
    $env:JAVA_HOME = "D:\CODE_Nskdfh_VS\Java Dev\Java\jdk-21"
    $env:ANDROID_HOME = $Sdk
    Push-Location $Root
    try {
        & ".\gradlew.bat" --offline :app:assembleDebug 2>&1 |
            Select-String -Pattern "^e: |FAILED|BUILD " | Select-Object -First 30
    } finally { Pop-Location }
}

function Invoke-Shot([string]$FileName) {
    Ensure-Out
    $remote = "/sdcard/ub_shot.png"
    & $Adb -s $Serial shell screencap -p $remote 2>&1 | Out-Null
    Start-Sleep -Milliseconds 700
    $local = Join-Path $OutDir "$FileName.png"
    & $Adb -s $Serial pull $remote $local 2>&1 | Out-Null
    $f = Get-Item $local -ErrorAction SilentlyContinue
    if ($f) { Write-Host "截图: $local  ($($f.Length) B)" } else { Write-Host "截图失败: $local" }
}

switch ($Action.ToLower()) {

    "build" { Invoke-Build }

    "install" {
        Ensure-Device
        Invoke-Build
        if (-not (Test-Path $Apk)) { throw "APK 不存在：$Apk" }
        $r = (& $Adb -s $Serial install -r -d $Apk 2>&1) -join "`n"
        Write-Host ($r -split "`n" | Select-Object -First 3)
        if ($r -notmatch "Success") { throw "安装失败" }
        & $Adb -s $Serial shell am force-stop $Pkg 2>&1 | Out-Null
        Start-Sleep -Milliseconds 800
        & $Adb -s $Serial logcat -c 2>&1 | Out-Null
        & $Adb -s $Serial shell am start -n "$Pkg/.MainActivity" 2>&1 | Out-Null
        Start-Sleep -Seconds 5
        Write-Host "已安装并启动。"
    }

    "start" {
        Ensure-Device
        & $Adb -s $Serial shell am start -n "$Pkg/.MainActivity" 2>&1 | Out-Null
        Start-Sleep -Seconds 4
    }

    "shot" {
        Ensure-Device
        if (-not $A1) { throw "用法: emu.ps1 shot <名字>" }
        Invoke-Shot $A1
    }

    "tap" {
        Ensure-Device
        if (-not $A1 -or -not $A2) { throw "用法: emu.ps1 tap <x> <y>" }
        & $Adb -s $Serial shell input tap $A1 $A2 2>&1 | Out-Null
        Start-Sleep -Milliseconds 800
        Write-Host "已点击 ($A1, $A2)"
    }

    "swipe" {
        Ensure-Device
        # 用法: emu.ps1 swipe x1 y1 x2 y2 —— 但参数位不够，这里固定用常见滚动
        & $Adb -s $Serial shell input swipe 540 1800 540 700 250 2>&1 | Out-Null
        Start-Sleep -Seconds 1
        Write-Host "已上滑一屏"
    }

    "back" {
        Ensure-Device
        & $Adb -s $Serial shell input keyevent KEYCODE_BACK 2>&1 | Out-Null
        Start-Sleep -Milliseconds 800
    }

    # ★ 找坐标用这个，不要靠肉眼从截图估
    "ui" {
        Ensure-Device
        Ensure-Out
        & $Adb -s $Serial shell uiautomator dump /sdcard/ub_ui.xml 2>&1 | Out-Null
        $local = Join-Path $OutDir "ui.xml"
        & $Adb -s $Serial pull /sdcard/ub_ui.xml $local 2>&1 | Out-Null
        if (-not (Test-Path $local)) {
            Write-Host "dump 失败：$local 不存在"
        } else {
            $x = [xml](Get-Content $local -Raw -Encoding UTF8)
            $nodes = $x.SelectNodes("//node[@text!='' or @content-desc!='']")
            Write-Host "找到 $($nodes.Count) 个有文字的节点：`n"
            foreach ($n in $nodes) {
                # ★ bounds 形如 `[148,548][311,593]` —— 去掉括号后切分只有 **2** 段
                #   （左上、右下），不是 4 段。
                #   早先按 4 段解析并守卫 `$p.Count -ge 4`，于是**一个坐标都没打印过**，
                #   却始终输出"找到 N 个节点"，看起来像成功了。
                #   ★ 教训：只打印了计数就当成"功能可用"，等于没验证。
                if ($n.bounds -match '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
                    $x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]
                    $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]
                    $label = if ($n.text) { $n.text } else { $n.'content-desc' }
                    Write-Host ("  {0,-30} ({1},{2})" -f $label, [int](($x1 + $x2) / 2), [int](($y1 + $y2) / 2))
                }
            }
        }
    }

    # ★ 模拟器专用：往应用私有目录写明文 key（解密失败会回退当明文读）
    "key" {
        Ensure-Device
        if (-not $A1) { throw "用法: emu.ps1 key <APIKEY>`n(仅限模拟器；不要把 key 写进任何文件)" }
        & $Adb -s $Serial shell am force-stop $Pkg 2>&1 | Out-Null
        Start-Sleep -Milliseconds 600

        $xml = "<?xml version=`"1.0`" encoding=`"utf-8`" standalone=`"yes`" ?>`n" +
               "<map>`n    <string name=`"$KeyApi`">$A1</string>`n</map>`n"

        # 先把旧的（含 .bak）删掉 —— SharedPreferences 会优先读 .bak 复原，
        # 留着旧文件会让新写入的值看起来"没生效"
        & $Adb -s $Serial shell "run-as $Pkg rm -f shared_prefs/$PrefsSecrets.xml shared_prefs/$PrefsSecrets.xml.bak" 2>&1 | Out-Null

        # 用 stdin 管道写：/data/local/tmp 对 others 不可读，cp 会被拒；
        # 而 shell 侧的 stdin 重定向不受这个限制
        $xml | & $Adb -s $Serial shell "run-as $Pkg sh -c 'cat > shared_prefs/$PrefsSecrets.xml'"
        Start-Sleep -Milliseconds 600

        $size = (& $Adb -s $Serial shell "run-as $Pkg ls -l shared_prefs/$PrefsSecrets.xml" 2>&1) -join ""
        Write-Host "已写入: $size"
        Write-Host "（key 未回显 —— 不要把它带进日志或提交）"

        & $Adb -s $Serial shell am start -n "$Pkg/.MainActivity" 2>&1 | Out-Null
        Start-Sleep -Seconds 4
    }

    "clearkey" {
        Ensure-Device
        & $Adb -s $Serial shell am force-stop $Pkg 2>&1 | Out-Null
        & $Adb -s $Serial shell "run-as $Pkg rm -f shared_prefs/$PrefsSecrets.xml shared_prefs/$PrefsSecrets.xml.bak" 2>&1 | Out-Null
        Write-Host "已清除 API Key"
    }

    "log" {
        Ensure-Device
        Ensure-Out
        Write-Host "=== 崩溃 ==="
        & $Adb -s $Serial logcat -d -b crash 2>&1 | Select-Object -Last 40
        Write-Host "`n=== 应用相关（最后 60 行）==="
        & $Adb -s $Serial logcat -d 2>&1 | Select-String "utilitybox|AndroidRuntime|chromium" | Select-Object -Last 60
    }

    "reset" {
        Ensure-Device
        & $Adb -s $Serial shell pm clear $Pkg 2>&1 | Out-Null
        Write-Host "已清空应用数据（注意：这也会清掉 API Key 与图鉴缓存）"
    }

    "flow" {
        Ensure-Device
        Write-Host "冒烟路径：启动 → 鸣潮 → 图鉴 → 共鸣者 → 首个词条"
        & $Adb -s $Serial shell am force-stop $Pkg 2>&1 | Out-Null
        Start-Sleep -Milliseconds 800
        & $Adb -s $Serial shell am start -n "$Pkg/.MainActivity" 2>&1 | Out-Null
        Start-Sleep -Seconds 5
        & $Adb -s $Serial shell input tap 633 2268 2>&1 | Out-Null   # 鸣潮 tab
        Start-Sleep -Seconds 2
        Invoke-Shot "flow_1_wuwa"
        & $Adb -s $Serial shell input tap 452 1180 2>&1 | Out-Null   # 鸣潮图鉴
        Start-Sleep -Seconds 5
        Invoke-Shot "flow_2_categories"
        & $Adb -s $Serial shell input tap 114 518 2>&1 | Out-Null    # 共鸣者
        Start-Sleep -Seconds 6
        Invoke-Shot "flow_3_entries"
        & $Adb -s $Serial shell input tap 258 452 2>&1 | Out-Null    # 首个词条
        Start-Sleep -Seconds 9
        Invoke-Shot "flow_4_detail"
        Write-Host "`n=== 崩溃检查 ==="
        & $Adb -s $Serial logcat -d -b crash 2>&1 | Select-Object -Last 10
    }

    default {
        Write-Host "未知动作: $Action"
        Write-Host "可用: build | install | start | shot <名> | tap <x> <y> | swipe | back | ui | key <密钥> | clearkey | log | reset | flow"
    }
}
