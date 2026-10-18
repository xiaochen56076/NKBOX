package com.utilitybox.game.wutheringwaves

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*
import com.utilitybox.game.wutheringwaves.kuro.KuroError
import com.utilitybox.game.wutheringwaves.kuro.KuroLoginController
import com.utilitybox.game.wutheringwaves.kuro.KuroLoginState
import com.utilitybox.game.wutheringwaves.kuro.KuroMeter
import com.utilitybox.game.wutheringwaves.kuro.KuroResult
import com.utilitybox.game.wutheringwaves.kuro.KuroRole
import com.utilitybox.game.wutheringwaves.kuro.KuroSession
import com.utilitybox.game.wutheringwaves.kuro.KuroWidgetData
import com.utilitybox.game.wutheringwaves.kuro.KuroCredentialStore
import com.utilitybox.game.wutheringwaves.kuro.KuroBbsApi
import kotlinx.coroutines.launch

/**
 * 鸣潮标签页。
 *
 * ### 数据来源
 *
 * 库街区（kurobbs）官方接口，**需要用户用自己的库街区账号登录**。
 * 这是本项目里唯一一处使用账号凭据的功能 —— 依据是用户在 P11 补 16 的明确裁决
 * （见 `PROJECT_RULES.md` 的对应条目与 `CHANGELOG.md`）。
 *
 * ### 未登录 / 已登录两种形态
 *
 * 未登录时给出**登录入口 + 风险说明**，而不是一个空页面 —— 用户需要知道
 * "这里能做什么"、"我要付出什么"。风险说明不是免责声明，是必要信息：
 * 其中"会让官方 App 下线"这一条会实际影响他怎么用。
 */
@Composable
fun WuwaScreen(
    onOpenGacha: () -> Unit = {},
    onOpenWiki: () -> Unit = {},
    /** 要切换到的游戏 id；null = 不显示切换入口（设置里关掉 / 只有一款游戏） */
    switchToGameId: String? = null,
    onSwitchGame: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val creds = KuroSession.credentials
    val nextName = switchToGameId
        ?.let { id -> com.utilitybox.domain.tool.UbToolRegistry.games.firstOrNull { it.id == id }?.name }
    val onSwitch: (() -> Unit)? = switchToGameId?.let { id -> { onSwitchGame(id) } }

    if (creds == null) {
        WuwaLogin(onOpenGacha, onOpenWiki, nextName, onSwitch, modifier)
    } else {
        WuwaDashboard(onOpenGacha, onOpenWiki, nextName, onSwitch, modifier)
    }
}

/**
 * 鸣潮页的身份卡（与三角洲页**同一个组件**）。
 *
 * ★ 用户 P11 补 22：「要做到对称，得给鸣潮页也换成身份卡」。
 *   对称不只是"好看"——两页的顶部结构不同，用户在两个 tab 之间切换时
 *   会觉得是两个拼起来的 App。
 */
@Composable
private fun WuwaHero(
    meta: String,
    nextName: String?,
    onSwitch: (() -> Unit)?,
) {
    val s = LocalUbSpacing.current
    Box(modifier = Modifier.padding(start = s.screenH, end = s.screenH, top = s.l)) {
        UbGameHeroCard(
            brand = UbStrings.appName,
            title = "鸣潮",
            meta = meta,
            watermark = UbIcon.Wave,
            switchLabel = nextName,
            onSwitch = onSwitch,
        )
    }
}

// ---------------------------------------------------------------------------
// 登录
// ---------------------------------------------------------------------------

@Composable
private fun WuwaLogin(
    onOpenGacha: () -> Unit,
    onOpenWiki: () -> Unit,
    nextName: String?,
    onSwitch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(KuroLoginState()) }
    val controller = remember {
        KuroLoginController(scope) { state = it }
    }
    // 每秒走一格，驱动"XX 秒后可重发"倒计时
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.resendAtMillis) {
        while (state.resendAtMillis > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
        now = System.currentTimeMillis()
    }
    val resendLeft = ((state.resendAtMillis - now) / 1000).coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
    ) {
        // ★ P11 补 22：原来的「鸣潮」文字标题换成身份卡，与三角洲页对称
        WuwaHero(
            meta = "图鉴与唤取记录 · 绑定账号后可看角色数据",
            nextName = nextName,
            onSwitch = onSwitch,
        )
        Spacer(Modifier.height(s.l))

        // ★ P11 补 77：登录区块可隐藏（设置 → 工具管理 → 登录入口）。
        //   原因：库街区登录受极验拦截，这个表单填了也不会成功；
        //   让用户能藏起来，比摆一个永远失败的表单好。
        if (WuwaUiSettings.showLoginEntry) {
            SectionCard {
                Column(modifier = Modifier.padding(s.cardPad)) {
                    Text(text = "手机号登录", style = t.bodyLarge, color = c.textPrimary)
                Text(
                    text = "验证码由库街区发送，登录后官方 App 会被挤下线",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = s.xxs),
                )
                Spacer(Modifier.height(s.m))

                KuroField(
                    value = state.mobile,
                    onValueChange = controller::onMobileChange,
                    placeholder = "手机号",
                    keyboardType = KeyboardType.Phone,
                    enabled = !state.sending && !state.loggingIn,
                )
                Spacer(Modifier.height(s.s))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        KuroField(
                            value = state.smsCode,
                            onValueChange = controller::onCodeChange,
                            placeholder = "短信验证码",
                            keyboardType = KeyboardType.NumberPassword,
                            enabled = !state.loggingIn,
                        )
                    }
                    UbSecondaryButton(
                        label = when {
                            state.sending -> "发送中…"
                            resendLeft > 0 -> "${resendLeft}s"
                            state.codeSent -> "重新获取"
                            else -> "获取验证码"
                        },
                        onClick = controller::sendCode,
                        enabled = !state.sending && resendLeft == 0L && state.mobile.length == 11,
                    )
                }

                state.error?.let { msg ->
                    Spacer(Modifier.height(s.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UbIcons.Icon(icon = UbIcon.Warning, tint = c.danger, size = 16.dp)
                        Spacer(Modifier.width(s.s))
                        Text(text = msg, style = t.caption, color = c.danger)
                    }
                }

                Spacer(Modifier.height(s.m))
                UbPrimaryButton(
                    label = if (state.loggingIn) "登录中…" else "登录",
                    onClick = controller::login,
                    enabled = !state.loggingIn && state.mobile.length == 11 && state.smsCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                }
            }
        }

        Spacer(Modifier.height(s.s))

        // ★★ P11 补 78：图鉴 / 唤取记录改成**块状卡片**（用户要求
        //   「这个唤取记录和图鉴可以做成和三角洲那里一样的块状」）。
        //
        //   原来是一行一条的列表项（`UbNavRow` + `UbDivider`），
        //   和三角洲页的工具卡（`UbToolCard` 两列网格）视觉上完全是两套语言：
        //   那边是**可点的大色块**，这边是**列表行**，切 tab 时观感割裂。
        //
        //   现在用同一个组件 `UbToolCard`，并排两列 —— 和三角洲工具区一致。
        //   ★ 不用 ListView 的 `LazyVerticalGrid`：这一页顶层是 `Column + verticalScroll`，
        //     嵌套一个可滚动网格会冲突。只有两张卡，`Row + weight` 就够，
        //     而且两张卡等宽、间距和三角洲的列间距（`s.m`）一致。
        //
        // ★ 仍然放在登录**之前**：两者都不需要库街区账号，
        //   藏在登录后面会让没登录的用户以为用不了。
        UbSectionHeader(title = "功能")
        Spacer(Modifier.height(s.s))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(s.m),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                UbToolCard(
                    title = "鸣潮图鉴",
                    subtitle = "角色与武器",
                    icon = UbIcon.Database,
                    statusColor = c.textTertiary,
                    enabled = true,
                    onClick = onOpenWiki,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                UbToolCard(
                    title = "唤取记录",
                    subtitle = "抽卡历史",
                    icon = UbIcon.Clock,
                    statusColor = c.textTertiary,
                    enabled = true,
                    onClick = onOpenGacha,
                )
            }
        }

        Spacer(Modifier.height(s.s))

        // ★ 风险说明。不是走过场 —— 用户需要知道自己的凭据被怎么对待。
        SectionCard {
            Column(modifier = Modifier.padding(s.cardPad)) {
                Text(text = "关于账号安全", style = t.bodyMedium, color = c.textPrimary)
                Spacer(Modifier.height(s.xs))
                listOf(
                    "凭据只存在本机应用私有目录，不写日志、不上传任何第三方",
                    "应用只读角色数据（体力、深塔、周本），不做签到等写操作",
                    "库街区同一时间只允许一个 APP 端登录，所以这里登录后官方 App 会下线",
                    "随时可在下方退出登录，退出即从本机删除凭据",
                ).forEach { line ->
                    Row(modifier = Modifier.padding(vertical = s.xxs)) {
                        Text(text = "·", style = t.caption, color = c.textTertiary)
                        Spacer(Modifier.width(s.xs))
                        Text(text = line, style = t.caption, color = c.textSecondary)
                    }
                }
            }
        }

        UbSourceFooter(source = "库街区（kurobbs）官方接口", updatedAt = "实时")
    }
}

// ---------------------------------------------------------------------------
// 数据面板
// ---------------------------------------------------------------------------

@Composable
private fun WuwaDashboard(
    onOpenGacha: () -> Unit,
    onOpenWiki: () -> Unit,
    nextName: String?,
    onSwitch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val scope = rememberCoroutineScope()
    val creds = KuroSession.credentials ?: return

    var roles by remember { mutableStateOf<List<KuroRole>>(emptyList()) }
    var data by remember { mutableStateOf<KuroWidgetData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf("") }
    /** 凭据失效 —— 与普通错误分开，因为它需要的是"重新登录"而不是"重试" */
    var expired by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        val distinct = KuroCredentialStore.distinctId()

        // 角色列表只取一次（换账号时会由 KuroSession 清掉选择）
        if (roles.isEmpty()) {
            when (val r = KuroBbsApi.findWuwaRoles(distinct, creds.token)) {
                is KuroResult.Ok -> {
                    roles = r.value
                    if (KuroSession.selectedRole == null) {
                        KuroSession.selectedRole = r.value.firstOrNull { it.isDefault } ?: r.value.firstOrNull()
                    }
                }
                is KuroResult.Failure -> {
                    if (r.error is KuroError.TokenExpired) { expired = true; loading = false; return }
                    // 角色列表失败不阻断取数据：不传 roleId 也能拿到默认角色的数据
                    roles = emptyList()
                }
            }
        }

        when (val r = KuroBbsApi.wuwaWidgetData(distinct, creds.token, KuroSession.selectedRole)) {
            is KuroResult.Ok -> {
                data = r.value
                updatedAt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    .format(java.util.Date())
            }
            is KuroResult.Failure -> {
                if (r.error is KuroError.TokenExpired) expired = true else error = r.message
            }
        }
        loading = false
    }

    LaunchedEffect(creds.token) { reload() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
    ) {
        // ★ P11 补 22：与三角洲页对称的身份卡
        WuwaHero(
            meta = buildString {
                append(creds.userName.ifBlank { "已登录" })
                data?.let {
                    if (it.roleName.isNotBlank()) append(" · ${it.roleName}")
                    if (it.serverName.isNotBlank()) append(" · ${it.serverName}")
                }
            },
            nextName = nextName,
            onSwitch = onSwitch,
        )
        Spacer(Modifier.height(s.l))

        if (expired) {
            SectionCard {
                UbError(
                    title = "登录已失效",
                    description = "库街区的登录凭据已过期，或被其它设备登录顶掉了。" +
                        "库街区同一时间只允许一个 APP 端登录 —— 在官方 App 里登录过就会这样。",
                    onRetry = { KuroSession.logout() },
                    retryLabel = "重新登录",
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            return@Column
        }

        // 多角色时给切换入口
        if (roles.size > 1) {
            SectionCard {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(s.cardPad)) {
                        Text(text = "选择角色", style = t.bodyLarge, color = c.textPrimary)
                    }
                    Row(
                        modifier = Modifier.padding(start = s.cardPad, end = s.cardPad, bottom = s.m),
                        horizontalArrangement = Arrangement.spacedBy(s.s),
                    ) {
                        roles.forEach { role ->
                            UbChip(
                                label = role.roleName.ifBlank { role.roleId },
                                selected = KuroSession.selectedRole?.roleId == role.roleId,
                                onClick = {
                                    KuroSession.selectedRole = role
                                    scope.launch { reload() }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(s.s))
        }

        when {
            loading && data == null -> Box(modifier = Modifier.padding(top = s.m)) { UbLoading() }
            error != null && data == null -> SectionCard {
                UbError(
                    title = "获取失败",
                    description = error ?: "",
                    onRetry = { scope.launch { reload() } },
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            data == null -> SectionCard {
                UbEmpty(
                    title = "没有数据",
                    description = "库街区没有返回该角色的数据。请确认账号已绑定鸣潮角色。",
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
            else -> {
                val d = data!!
                // 体力放最前且单独一卡 —— 它是这个页面最高频的查看目标
                d.energy?.let { e ->
                    SectionCard {
                        MeterCard(
                            meter = e,
                            accent = true,
                            serverTime = d.serverTime,
                            signedIn = d.hasSignIn,
                            signInTxt = d.signInTxt,
                        )
                    }
                    Spacer(Modifier.height(s.s))
                }

                val rest = listOfNotNull(
                    d.liveness, d.storeEnergy, d.tower, d.slashTower,
                    d.weekly, d.weeklyRouge,
                ) + d.battlePass
                if (rest.isNotEmpty()) {
                    SectionCard {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().padding(s.cardPad)) {
                                Text(text = "其他进度", style = t.bodyLarge, color = c.textPrimary)
                            }
                            rest.forEachIndexed { i, m ->
                                if (i > 0) UbDivider()
                                MeterRow(meter = m, serverTime = d.serverTime)
                            }
                        }
                    }
                    Spacer(Modifier.height(s.s))
                }

                SectionCard {
                    Column {
                        // 图鉴与唤取记录都不需要库街区账号
                        UbNavRow(
                            title = "鸣潮图鉴",
                            icon = UbIcon.Database,
                            onClick = onOpenWiki,
                            trailingText = "",
                        )
                        UbDivider()
                        UbNavRow(
                            title = "唤取记录",
                            icon = UbIcon.Clock,
                            onClick = onOpenGacha,
                            trailingText = "",
                        )
                        UbDivider()
                        UbNavRow(
                            title = "刷新数据",
                            icon = UbIcon.Refresh,
                            onClick = { scope.launch { reload() } },
                            trailingText = if (updatedAt.isBlank()) null else updatedAt,
                        )
                        UbDivider()
                        UbNavRow(
                            title = "退出登录",
                            icon = UbIcon.User,
                            onClick = { KuroSession.logout() },
                            trailingText = "删除本机凭据",
                        )
                    }
                }
                UbSourceFooter(
                    source = "库街区（kurobbs）官方接口",
                    updatedAt = updatedAt.ifBlank { "刚刚" },
                )
            }
        }
    }
}

/** 体力的重点展示：大数字 + 进度条 + 回满时间 */
@Composable
private fun MeterCard(
    meter: KuroMeter,
    serverTime: Long,
    accent: Boolean,
    signedIn: Boolean,
    signInTxt: String,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(modifier = Modifier.padding(s.cardPad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = meter.name, style = t.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            if (signedIn) {
                Text(text = signInTxt.ifBlank { "已签到" }, style = t.caption, color = c.accent)
            }
        }
        Spacer(Modifier.height(s.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = meter.cur.toString(),
                style = t.numericLarge,
                color = if (accent) c.accent else c.textPrimary,
            )
            Text(
                text = " / ${meter.total}",
                style = t.bodyMedium,
                color = c.textTertiary,
                modifier = Modifier.padding(bottom = s.xxs),
            )
        }
        Spacer(Modifier.height(s.s))
        MeterBar(meter)
        remainingText(meter, serverTime)?.let {
            Spacer(Modifier.height(s.xs))
            Text(text = it, style = t.caption, color = c.textTertiary)
        }
    }
}

/** 普通进度行 */
@Composable
private fun MeterRow(meter: KuroMeter, serverTime: Long) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = s.cardPad, vertical = s.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = meter.name,
                style = t.bodyMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // ★ total == 0 时显示 valueText 或"—"，**绝不显示 0**
            //   （响应里没有数据时是 cur=0,total=0，按比例算会显示成 0%，
            //    那是把"没有数据"画成了"进度为零"）
            Text(
                text = if (meter.total > 0) "${meter.cur} / ${meter.total}"
                else (meter.valueText ?: "—"),
                style = t.caption,
                color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(s.xs))
        MeterBar(meter)
        remainingText(meter, serverTime)?.let {
            Spacer(Modifier.height(s.xxs))
            Text(text = it, style = t.caption, color = c.textTertiary)
        }
    }
}

@Composable
private fun MeterBar(meter: KuroMeter) {
    val c = LocalUbColors.current
    val r = LocalUbRadius.current
    val fraction = meter.fraction
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(r.chip))
            .background(c.bgSubtle),
    ) {
        if (fraction != null && fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(r.chip))
                    .background(c.accent),
            )
        }
        // fraction == null ⇒ 不画填充，条子保持空 —— 表示"没有数据"而不是"0%"
    }
}

/**
 * 回满 / 重置倒计时。
 *
 * ★ 用 `refreshTimeStamp - serverTime` 而不是 `refreshTimeStamp - 本机时间`：
 *   文档明确「库洛的服务器有时候返回的时间可能不对」，并建议**按 serverTime 推算**。
 *   两个都来自服务端时，即使服务端时钟整体偏移，**差值仍然是对的**；
 *   混用本机时间则会把偏移量直接算进剩余时间里。
 */
private fun remainingText(meter: KuroMeter, serverTime: Long): String? {
    val ts = meter.refreshTimeStamp ?: return null
    if (serverTime <= 0L) return null
    val left = ts - serverTime
    if (left <= 0L) return null
    val h = left / 3600
    val m = (left % 3600) / 60
    return when {
        h > 0 -> "约 ${h} 小时 ${m} 分后可恢复"
        m > 0 -> "约 ${m} 分钟后可恢复"
        else -> "即将恢复"
    }
}

// ---------------------------------------------------------------------------
// 输入框
// ---------------------------------------------------------------------------

/**
 * 与设计系统一致的输入框。
 *
 * ★ 为什么不用 Material 的 `TextField`：它的默认外观（下划线 / 填充色 / 高度）
 *   与本项目的卡片式风格不一致，要改到一致得覆盖十几个参数。
 *   这里用 `BasicTextField` + 设计令牌，和物品物价的搜索框是同一套做法。
 */
@Composable
private fun KuroField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.field))
            .background(c.bgSubtle)
            .padding(horizontal = s.m, vertical = s.m),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = t.bodyLarge, color = c.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = t.bodyLarge.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
