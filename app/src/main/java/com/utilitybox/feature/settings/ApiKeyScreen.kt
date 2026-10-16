package com.utilitybox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.core.secret.ApiKeyProvider

/**
 * API Key 设置页。
 *
 * ★ 设计取向（依据用户裁决「数据方面的 API 就采用设置里面可以输入那种」）：
 *   Key 由**用户自己填写**，不依赖构建期注入。任何人拿到 APK 都能填自己的 Key 使用。
 *
 * ★ 诚实性要求（本项目红线的一贯取向）：
 *   - 展示时**只显示打码形式**（`sk-dfa4…97d`），绝不回显完整 Key
 *   - **如实说明加密状态**：Keystore 可用时说"已加密"，降级时说"未加密"，不假称安全
 *   - 说明 Key 的用途与获取方式，避免用户不知道这是什么
 */
@Composable
fun ApiKeyScreen(modifier: Modifier = Modifier) {
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val c = LocalUbColors.current

    // 初始输入框留空：不回显完整 Key（用户要改就整体重填）
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    // ★ 这些必须是 state 而不能是普通 val —— P06 实测踩到：
    //   用普通 val 读取时，保存后**不会触发重组**，"当前状态"仍然显示「未配置」，
    //   用户会以为没保存成功。用 mutableStateOf 包起来才能即时刷新。
    var configured by remember { mutableStateOf(ApiKeyProvider.isConfigured()) }
    var masked by remember { mutableStateOf(ApiKeyProvider.masked()) }
    var encrypted by remember { mutableStateOf(ApiKeyProvider.isEncryptedAtRest()) }
    var source by remember { mutableStateOf(ApiKeyProvider.source()) }
    var saved by remember { mutableStateOf(false) }

    /** 保存/清除后统一刷新展示值 */
    fun refresh() {
        configured = ApiKeyProvider.isConfigured()
        masked = ApiKeyProvider.masked()
        encrypted = ApiKeyProvider.isEncryptedAtRest()
        source = ApiKeyProvider.source()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        // ---------- 当前状态 ----------
        SectionCard(title = "当前状态") {
            Column(modifier = Modifier.padding(horizontal = s.screenH, vertical = s.m)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    UbStatusDot(
                        color = if (configured) c.success else c.warning,
                    )
                    Text(
                        text = if (configured) "已配置" else "未配置",
                        style = t.bodyLarge,
                        color = if (configured) c.success else c.warning,
                    )
                }
                if (configured) {
                    Text(
                        text = "当前 Key：$masked",
                        style = t.caption,
                        color = c.textTertiary,
                        modifier = Modifier.padding(top = s.xs),
                    )
                    Text(
                        text = when (source) {
                            ApiKeyProvider.KeySource.USER -> "来源：本机设置（可随时修改）"
                            ApiKeyProvider.KeySource.BUILD_TIME -> "来源：构建期注入（开发用）"
                            ApiKeyProvider.KeySource.NONE -> ""
                        },
                        style = t.caption,
                        color = c.textTertiary,
                    )
                    // ★ 如实说明加密状态，不假称安全
                    Text(
                        text = if (encrypted) {
                            "存储方式：已用系统密钥库（Android Keystore）加密"
                        } else {
                            "存储方式：未加密（本机 Keystore 不可用，已降级为明文保存）"
                        },
                        style = t.caption,
                        color = if (encrypted) c.textTertiary else c.warning,
                    )
                }
            }
        }

        // ---------- 输入 ----------
        SectionCard(title = "设置 API Key") {
            Column(
                modifier = Modifier.padding(horizontal = s.screenH, vertical = s.m),
                verticalArrangement = Arrangement.spacedBy(s.s),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        saved = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    placeholder = { Text("粘贴你的 API Key") },
                    singleLine = true,
                    // 默认遮蔽，避免旁人看到
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = c.bgElevated,
                        unfocusedContainerColor = c.bgElevated,
                    ),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(s.s)) {
                    UbTextButton(
                        label = if (visible) "隐藏" else "显示",
                        onClick = { visible = !visible },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(s.s)) {
                    UbPrimaryButton(
                        label = if (saved) "已保存" else "保存",
                        enabled = input.isNotBlank(),
                        onClick = {
                            ApiKeyProvider.save(input)
                            input = ""
                            saved = true
                            refresh()
                        },
                    )
                    if (configured) {
                        UbSecondaryButton(
                            label = "清除",
                            onClick = {
                                ApiKeyProvider.clear()
                                input = ""
                                saved = false
                                refresh()
                            },
                        )
                    }
                }

                if (saved) {
                    Text(
                        text = "已保存。今日密码与物品物价会立即使用新的 Key，无需重启应用。",
                        style = t.caption,
                        color = c.success,
                    )
                }
            }
        }

        // ---------- 说明 ----------
        SectionCard(title = "说明") {
            Column(
                modifier = Modifier.padding(horizontal = s.screenH, vertical = s.m),
                verticalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                Text(
                    text = "为什么需要 API Key",
                    style = t.labelMedium,
                    color = c.textSecondary,
                )
                Text(
                    text = "本应用的物价与密码数据来自第三方社区接口，" +
                        "该接口按 API Key 计量调用。请自行申请后填入。",
                    style = t.caption,
                    color = c.textTertiary,
                )
                Text(
                    text = "Key 存在哪里",
                    style = t.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = s.xs),
                )
                Text(
                    text = "只存在本机应用私有目录，其他应用无法读取；" +
                        "不会上传，也不会写进应用的安装包。",
                    style = t.caption,
                    color = c.textTertiary,
                )
                Text(
                    text = "申请地址",
                    style = t.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = s.xs),
                )
                Text(
                    text = "delta-test.shallow.ink/developer/api-keys（需登录）",
                    style = t.caption,
                    color = c.accent,
                )
            }
        }
    }
}
