package com.utilitybox.core.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.utilitybox.R

/**
 * 品牌文案的**唯一来源**。
 *
 * ### 为什么要有这个文件
 *
 * 应用名原本散落在三处硬编码字符串里（首页标题、启动页、设置页的「名称」行），
 * 而 `strings.xml` 里的 `app_name` 又是第四处 —— 于是「桌面图标叫什么」
 * 和「应用里显示什么」是**两份可以互相矛盾的真相**。
 * 这次改名时正是这个问题暴露出来：如果只改 `strings.xml`，
 * 桌面会变成「N盒」而首页仍然写着「UtilityBox」。
 *
 * 所以：**清单（桌面/系统界面）与界面读同一个 `@string/app_name`**，
 * 界面统一经由下面两个入口取用。
 *
 * ★ 为什么不干脆定义成 Kotlin 常量：
 *   那样又会变成两份真相（`strings.xml` 一份、Kotlin 一份）。
 *   资源文件才是 Android 里"应用名"的正规位置 —— 系统界面直接读它。
 */
object UbStrings {

    /** 应用名。与桌面图标、系统「应用信息」页显示的是**同一个字符串**。 */
    val appName: String
        @Composable get() = stringResource(R.string.app_name)

    /** 启动页副标题 */
    val appTagline: String
        @Composable get() = stringResource(R.string.app_tagline)
}
