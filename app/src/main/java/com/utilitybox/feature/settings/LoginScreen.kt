package com.utilitybox.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.utilitybox.core.account.AccountState
import com.utilitybox.core.account.QqLoginResult
import com.utilitybox.core.design.*

/**
 * 登录页（P11 新增）。
 *
 * ## ★★ QQ 登录：**没有伪造**
 *
 * 用户要求「优先显示 QQ 登录，登录后即可显示一些关于 QQ 的信息」。
 *
 * QQ 登录需要接入**腾讯开放平台 SDK**，而本项目自 P00 起维持**零新增依赖**。
 * 因此本页的处理方式是：
 *
 * - QQ 按钮放在**第一位**（符合"优先"的要求），样式与其它登录方式一致；
 * - 但它**如实标注「待接入 SDK」**，点击后弹出一条说明，
 *   **不会假装登录成功**（把假交互当成品是红线）；
 * - 同时提供「本地昵称」，让登录后的界面状态现在就能验证。
 *
 * 接入 SDK 后，只要在 `AccountStore.loginWithQq` 里换成真实实现，
 * 本页**一行都不用改** —— 两种方式共用同一套状态流。
 */
@Composable
fun LoginScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current

    var nickname by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        // ---- QQ 登录（优先）----
        LoginOption(
            title = "QQ 登录",
            subtitle = "待接入 SDK · 暂不可用",
            badge = "优先",
            enabled = false,
            onClick = {
                // 走真实的状态机，由它返回**具体原因** —— 而不是在这里写死一句话。
                // 这样接入 SDK 后本页逻辑自然就通了。
                notice = when (val result = AccountState.loginWithQq()) {
                    is QqLoginResult.SdkNotIntegrated ->
                        "QQ 登录需要接入腾讯开放平台 SDK，目前尚未接入。\n接入后会直接在这里生效，无需你重新登录。"
                    is QqLoginResult.Failed -> result.message
                    is QqLoginResult.Success -> null
                }
            },
        )

        // ---- 本地昵称（现在即可用）----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(r.card))
                .background(c.bgElevated)
                .padding(s.cardPad),
            verticalArrangement = Arrangement.spacedBy(s.s),
        ) {
            Text(text = "本地昵称", style = t.titleMedium, color = c.textPrimary)
            // ★ 如实说明它与 QQ 登录的区别，避免用户以为已经绑定了 QQ
            Text(
                text = "只保存在本机，不会上传，也不代表绑定 QQ。",
                style = t.caption,
                color = c.textTertiary,
            )
            androidx.compose.material3.OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("给自己起个名字", style = t.bodyMedium, color = c.textTertiary) },
            )
            UbPrimaryButton(
                label = "使用这个昵称",
                enabled = nickname.isNotBlank(),
                onClick = {
                    AccountState.loginLocal(nickname)
                    onDone()
                },
            )
        }

        if (AccountState.profile.isLoggedIn) {
            UbSecondaryButton(
                label = "退出登录",
                onClick = {
                    AccountState.logout()
                    notice = null
                },
            )
        }

        // ---- 说明 ----
        Text(
            text = "本应用不索取游戏账号密码，也不实现 Cookie 登录。",
            style = t.caption,
            color = c.textTertiary,
            modifier = Modifier.padding(top = s.s),
        )

        notice?.let {
            WarningNote(text = it, tone = WarningTone.Info)
        }
    }
}

/** 登录方式条目：统一"优先/待接入"的呈现方式 */
@Composable
private fun LoginOption(
    title: String,
    subtitle: String,
    badge: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.card))
            .background(if (enabled) c.bgElevated else c.bgSubtle)
            // ★ 不可用时**仍然可点击** —— 点了会如实说明原因。
            //   禁用点击会让用户以为按钮坏了（P02 实测踩到过）。
            .ubClickable(onClick = onClick)
            .padding(s.cardPad),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(s.xxs)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.s),
            ) {
                Text(text = title, style = t.bodyLarge, color = c.textPrimary)
                if (badge != null) {
                    UbChip(label = badge, selected = true)
                }
            }
            Text(text = subtitle, style = t.caption, color = c.textTertiary)
        }
    }
}

/**
 * 右下角预留页（P11 新增）。
 *
 * 用户明确要求：「点击右下角的选项不用显示任何三角洲和鸣潮的工具，
 * 而是你先预留好界面，我会后面和你说需求的」。
 *
 * ★ 所以这里**刻意不做任何猜测性实现**，只如实说明它还是空的。
 *   凭空填一些功能进去，反而会在需求明确后全部推翻。
 */
@Composable
fun ExplorePlaceholderScreen(modifier: Modifier = Modifier) {
    val s = LocalUbSpacing.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.xxl),
        contentAlignment = Alignment.TopCenter,
    ) {
        UbEmpty(
            title = "这里还没做",
            description = "这个入口先预留好了，等你想好要放什么再填。",
        )
    }
}
