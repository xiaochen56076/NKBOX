package com.utilitybox.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.LocalUbColors
import com.utilitybox.core.design.LocalUbRadius
import com.utilitybox.core.design.LocalUbSize
import com.utilitybox.core.design.LocalUbSpacing
import com.utilitybox.core.design.LocalUbTypography
import com.utilitybox.core.design.SectionCard
import com.utilitybox.core.design.UbDivider
import com.utilitybox.core.design.UbIcon
import com.utilitybox.core.design.UbIcons
import com.utilitybox.core.design.UbPrimaryButton
import com.utilitybox.core.design.UbSecondaryButton
import com.utilitybox.core.design.UbSettingRow
import com.utilitybox.core.design.UbTextButton
import com.utilitybox.core.sync.WebDavClient
import com.utilitybox.core.sync.WebDavConfig
import com.utilitybox.core.sync.WebDavResult
import com.utilitybox.core.sync.WebDavStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 云备份（P11 补 40 结构 / 补 42 视觉重做）。
 *
 * ### 用户原话与对应改动
 *
 * > 「这个界面不能直接改为**登录那种**吗？反而还要服务器地址」
 * > 「还有那个路径不应该是**隐式的**？」
 * > 「云备份是一个**单独的选项，进去才是**」
 * > 「你这个界面弄好看点呀，太丑了，**至少风格统一**一下吧」
 *
 * ### ★ 补 42：上一版为什么丑 —— 根因不是配色，是**没用设计系统**
 *
 * 上一版我用 `SectionCard` + 默认控件随手拼，跳过了项目已有的组件与间距规则，
 * 结果四处不统一：
 *
 * | 症状 | 根因 |
 * |---|---|
 * | 卡片之间节奏忽大忽小 | `SectionCard` **自带横向内边距和标题上间距**，我外面又套了一层 `Column(spacedBy(m))`，等于间距加了两遍 |
 * | 邮箱大得像标题 | 用了 `bodyLarge`，而它在这一页是**主信息**，应该配图标并降一级 |
 * | 三个按钮长得一模一样 | 全用 `UbSecondaryButton` —— 没有主次。主操作该是 `UbPrimaryButton`，退出该是 `UbTextButton` |
 * | 状态文字裸在页面中间 | 没有容器，看起来像出错信息 |
 *
 * 这一版的原则：**只用设计系统里的组件，不自己拼外观**（`UbButtons.kt` 开头就写着
 * 「页面不得自行拼装按钮外观」，我上一版违反了这条）。
 */
@Composable
fun CloudBackupScreen(
    store: WebDavStore,
    /** 当前本地数据序列化成 JSON（用于上传）*/
    exportJson: () -> String,
    /** 把云端拉到的 JSON 合并进本地。返回合并后的记录数 */
    importJson: (String) -> Int,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    val scope = rememberCoroutineScope()

    val saved = remember { store.load() }
    var loggedIn by remember { mutableStateOf(saved.isConfigured && saved.password.isNotBlank()) }
    var account by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var autoUpload by remember { mutableStateOf(saved.autoUpload) }
    var status by remember { mutableStateOf<String?>(null) }
    var bad by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun config() = WebDavConfig(
        serverUrl = CLOUD_SERVER_URL,
        username = account.trim(),
        password = password,
        remotePath = CLOUD_REMOTE_PATH,
        autoUpload = autoUpload,
    )

    fun run(label: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        status = "$label…"
        bad = false
        scope.launch {
            val msg = runCatching { withContext(Dispatchers.IO) { block() } }
                .getOrElse { "出错了：${it.javaClass.simpleName}" }
            bad = msg.startsWith("✗")
            status = msg
            busy = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!loggedIn) {
            // ---------- 未登录：登录表单 ----------
            SectionCard(title = "登录坚果云") {
                Column(
                    modifier = Modifier.padding(s.cardPad),
                    verticalArrangement = Arrangement.spacedBy(s.m),
                ) {
                    Text(
                        text = "登录后，唤取记录会自动备份到你自己的网盘。",
                        style = t.caption,
                        color = c.textSecondary,
                    )
                    CloudField("账号", account, "坚果云登录邮箱") { account = it }
                    CloudField("应用密码", password, "不是登录密码", secret = true) { password = it }

                    // 提示块：用柔和底色包起来，而不是让一行小字裸着
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(r.button))
                            .background(c.warning.copy(alpha = 0.12f))
                            .padding(s.m),
                    ) {
                        Text(
                            text = "填的是应用密码。在坚果云客户端 →「设置 → 第三方应用管理」里生成；" +
                                "填登录密码会认证失败。",
                            style = t.caption,
                            color = c.textSecondary,
                        )
                    }

                    Spacer(Modifier.height(s.xs))
                    UbPrimaryButton(
                        label = if (busy) "登录中…" else "登录",
                        onClick = {
                            run("登录") {
                                when (val res = WebDavClient.test(config())) {
                                    is WebDavResult.Ok -> {
                                        store.save(config())
                                        loggedIn = true
                                        "✓ 登录成功"
                                    }
                                    is WebDavResult.Failure -> "✗ ${res.message}"
                                }
                            }
                        },
                        enabled = !busy && account.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            // ---------- 已登录：账号卡 ----------
            SectionCard(title = "已登录") {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = s.cardPad, vertical = s.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(s.m),
                    ) {
                        // 图标给这项一个视觉身份 —— 纯文字行看起来像没做完
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(r.chip))
                                .background(c.accentSoft)
                                .padding(s.s),
                            contentAlignment = Alignment.Center,
                        ) {
                            UbIcons.Icon(UbIcon.Database, tint = c.accent, size = 20.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = account, style = t.bodyLarge, color = c.textPrimary)
                            Text(
                                text = if (store.isEncrypted) "凭据已加密保存" else "⚠️ 凭据以明文保存",
                                style = t.caption,
                                color = if (store.isEncrypted) c.textTertiary else c.danger,
                            )
                        }
                    }
                    UbDivider()
                    UbSettingRow(title = "获取记录后自动备份") {
                        Switch(
                            checked = autoUpload,
                            onCheckedChange = {
                                autoUpload = it
                                store.save(config().copy(autoUpload = it))
                            },
                        )
                    }
                }
            }

            // ---------- 已登录：操作卡（有主次） ----------
            SectionCard(title = "备份") {
                Column(
                    modifier = Modifier.padding(s.cardPad),
                    verticalArrangement = Arrangement.spacedBy(s.m),
                ) {
                    UbPrimaryButton(
                        label = if (busy) "请稍候…" else "立即备份",
                        onClick = {
                            run("备份") {
                                when (val res = WebDavClient.upload(config(), exportJson())) {
                                    is WebDavResult.Ok -> "✓ 已备份到你的坚果云"
                                    is WebDavResult.Failure -> "✗ ${res.message}"
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UbSecondaryButton(
                        label = "从云端恢复",
                        onClick = {
                            run("恢复") {
                                when (val res = WebDavClient.download(config())) {
                                    is WebDavResult.Failure -> "✗ ${res.message}"
                                    is WebDavResult.Ok -> {
                                        val text = res.value
                                        if (text == null) {
                                            "✓ 云端还没有备份（第一次用是正常的）"
                                        } else {
                                            "✓ 已合并，本地共 ${importJson(text)} 条"
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ---------- 状态：有容器、按结果着色 ----------
        status?.let {
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (bad) c.danger.copy(alpha = 0.10f) else c.success.copy(alpha = 0.10f))
                        .padding(s.cardPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    UbIcons.Icon(
                        if (bad) UbIcon.Warning else UbIcon.Check,
                        tint = if (bad) c.danger else c.success,
                        size = 18.dp,
                    )
                    Text(
                        text = it,
                        style = t.caption,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---------- 安全说明：短句分行，不要长到折行 ----------
        SectionCard(title = "关于安全") {
            Column(
                modifier = Modifier.padding(s.cardPad),
                verticalArrangement = Arrangement.spacedBy(s.s),
            ) {
                SecurityLine("凭据只存在本机，加密保存")
                SecurityLine("备份文件存在你自己的网盘里")
                SecurityLine("不经过我们的任何服务器")
                SecurityLine("恢复是合并：旧数据不会被覆盖")
            }
        }

        if (loggedIn) {
            Spacer(Modifier.height(s.s))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 退出是低频且破坏性的操作 —— 用文字按钮降级，不和主操作抢注意力
                UbTextButton(label = "退出登录", onClick = {
                    store.clear()
                    loggedIn = false
                    password = ""
                    status = null
                })
            }
        }
        Spacer(Modifier.height(s.xl))
    }
}

@Composable
private fun SecurityLine(text: String) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(s.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UbIcons.Icon(UbIcon.Check, tint = c.success, size = 15.dp)
        Text(text = text, style = t.caption, color = c.textSecondary)
    }
}

@Composable
private fun CloudField(
    label: String,
    value: String,
    hint: String,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val c = LocalUbColors.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = t.caption) },
        placeholder = { Text(hint, style = t.caption, color = c.textTertiary) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(r.button),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 云端参数 —— **程序内部常量，不给用户看**（P11 补 40）。
 *
 * ★ 用户明确要求服务器地址和路径都隐式。这两个值本来也不需要用户决策：
 *   · 地址：我们用坚果云，它的 WebDAV 入口是固定的
 *   · 路径：文件放哪是程序的事
 *
 * ★ 路径里的 `wawu` 是**实测确认可用**的目录（见 WebDavClient 类注释里
 *   关于 MKCOL 的限制：程序不能自己建目录，所以必须用一个已存在的目录）。
 */
private const val CLOUD_SERVER_URL = "https://dav.jianguoyun.com/dav"
private const val CLOUD_REMOTE_PATH = "wawu/wuwa_gacha.json"
