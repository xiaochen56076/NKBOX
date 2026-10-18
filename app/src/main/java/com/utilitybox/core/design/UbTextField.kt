package com.utilitybox.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 通用文本输入框 —— **全项目唯一的输入框实现**。
 *
 * ### 为什么必须有这个组件
 *
 * 用户真机反馈（深色模式）：
 * 「输入框中的数字显示为和背景一致的颜色，导致无法认出」。
 *
 * 根因：项目里直接用的 Material3 `OutlinedTextField`，**没有设置 `colors`**。
 * Material3 的文字颜色取自 `MaterialTheme.colorScheme.onSurface`，
 * 而本项目**没有用 MaterialTheme 承载自己的调色板**（用的是 `LocalUbColors`），
 * 于是 Material3 一直用它的**默认浅色配色** —— 深色模式下就是
 * 「深色背景 + 近黑色的字」→ 看不见。
 *
 * 实测同一个缺陷存在于**全部 4 处**输入框（数据/API Key/云备份/昵称）。
 * 逐个补 `colors` 只是治标：下次有人新增输入框还会再犯一次。
 * 所以把它收成一个组件，**调色板在这里一次性对齐**。
 *
 * ### 规则
 *
 * ★ 页面里**不许**再直接使用 `OutlinedTextField` / `TextField`。
 *   需要新样式就往这里加参数，不要在页面里复制一份 colors。
 */
@Composable
fun UbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 悬浮标题（输入框上方的标签）。null 表示不要 */
    label: String? = null,
    /** 空内容时的占位文字。null 表示不要 */
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: Shape = RoundedCornerShape(LocalUbRadius.current.button),
) {
    val c = LocalUbColors.current
    val t = LocalUbTypography.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        shape = shape,
        label = label?.let { text -> { Text(text = text, style = t.caption, color = c.textTertiary) } },
        placeholder = placeholder?.let { text ->
            { Text(text = text, style = t.bodyMedium, color = c.textTertiary) }
        },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        // ★★ 这一块就是修 bug 的地方：**文字色必须显式给**，不能依赖 Material3 默认值
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            disabledTextColor = c.textTertiary,
            // 容器用应用自己的层级色，而不是 Material3 的 surface
            focusedContainerColor = c.bgElevated,
            unfocusedContainerColor = c.bgElevated,
            disabledContainerColor = c.bgSubtle,
            // 光标 / 边框 / 标签都用强调色，和其余控件一致
            cursorColor = c.accent,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.divider,
            disabledBorderColor = c.divider,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textTertiary,
            focusedPlaceholderColor = c.textTertiary,
            unfocusedPlaceholderColor = c.textTertiary,
        ),
    )
}
