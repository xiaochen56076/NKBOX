// UtilityBox · app/build.gradle.kts
// 依赖边界说明：首期单 Gradle 模块，包结构表达边界（见 docs/architecture.md §2.3）。

import java.util.Properties

// ---------------------------------------------------------------------------
// API Key 注入（★ 绝不落盘进版本库）
//
// 优先级：环境变量 UBOX_API_KEY > local.properties 的 ubox.apiKey > 空串
//
// 为什么这样做：
//  - 红线要求 **API Key 不得写入任何文件**（源码、文档、日志都不行）
//  - `local.properties` 已被 .gitignore 排除，是 Android 生态的惯例位置
//  - 环境变量方式便于 CI 与临时验证
//
// 未提供 Key 时构建**不会失败**，只会注入空串 —— 应用照常可跑，
// 数据源状态会如实显示「未配置 API Key」。这样"没配 Key"不会是构建错误，
// 而是一个**运行时可解释的状态**。
// ---------------------------------------------------------------------------
val uboxApiKey: String = run {
    val fromEnv = System.getenv("UBOX_API_KEY")
    if (!fromEnv.isNullOrBlank()) return@run fromEnv

    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties()
        localProps.inputStream().use { props.load(it) }
        val v = props.getProperty("ubox.apiKey")
        if (!v.isNullOrBlank()) return@run v
    }
    ""
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.utilitybox"
    compileSdk {
        // ★ 37.0 —— P01 实测：官方导航与全部第三方玻璃库均通过 AAR metadata 强制要求 >= 37
        //   已安装 platforms/android-37.0。不影响 targetSdk(36) 与 minSdk(24)。
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        applicationId = "com.utilitybox"
        minSdk = 24
        targetSdk = 36
        /**
         * 版本号。
         *
         * ★ versionName 里的 `pNN` 表示**当前走到哪个阶段**，每次阶段推进都要更新。
         *   它同时会显示在「我的 → 关于与开源许可」里 ——
         *   停在 `p09` 会让用户（和排查问题的我）误以为装的是旧包。
         *   这类"界面显示的版本与实际不符"虽不致命，但会直接误导排查方向。
         */
        versionCode = 1
        versionName = "0.1.0-p11"

        // 注入 API Key（值为空串表示未配置，UI 会如实提示）
        buildConfigField("String", "UBOX_API_KEY", "\"$uboxApiKey\"")
        // 数据源服务地址（可整体替换是设计稿要求，故不写死在业务代码里）
        buildConfigField(
            "String",
            "UBOX_BASE_URL",
            "\"https://delta-test-api.shallow.ink\"",
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // P11 再处理签名与混淆
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- 液态玻璃（用户于 P04 裁决采用）----
    // 为什么用现成库而不是自研：自研只能做「整体均质模糊」，做不出**边缘折射**。
    // 实测并排对比见 docs/liquid-glass-library-eval.md 与 screenshots/haze-vs-handmade.png。
    // 为什么是 2.0.1 而不是 1.7.2：只有 2.x 有 haze-glass 的折射光学（Regular/Clear）。
    // 代价：Compose 基线随之升到 1.12.1（已同步把 BOM 升到 2026.09.00 与之匹配）。
    implementation(libs.haze)
    implementation(libs.haze.glass)
    implementation(libs.haze.glass.material3)

    // 刻意不引入：navigation（用自研导航，P01 已验证）、
    //            material-icons（图标全部手绘）、OkHttp/Gson（用平台内置）

    // ---- 价格历史图表（P11 补 50，用户裁决引入）----
    //
    // ★ 起因（我犯的错）：我曾断言「构建是 --offline 的，无法解析新依赖」。
    //   **这是错的** —— `--offline` 只是我敲命令行时带的参数，gradle.properties 里
    //   并没有它；实测 Maven Central 与 google() 都可达、构件能真下载。
    //
    // ★ Vico 替换的是手绘 Canvas 图表，理由有两条实证：
    //   1. 手绘版坐标轴要自己排版，**数字过长会挤成竖排**
    //      （真机截图：`31,947,82832,568,813…` 下面跟着竖排的 `4 8 , 9 1 0 / 1 5`）
    //   2. 没有"点击某点看数值"的交互 —— 而这正是同类产品的标配
    //
    // ★ 兼容性已实测（不是"仓库能下载"就算数，是**编译过 + 370 例全绿**）
    implementation(libs.vico.compose.m3)

    // ---- 单元测试：纯逻辑，不依赖 Android 框架 ----
    // 解析、错误映射、缓存新鲜度、四态判定这些必须能在无设备的情况下验证
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
