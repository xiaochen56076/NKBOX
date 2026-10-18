package com.utilitybox.game.wutheringwaves

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.utilitybox.core.design.*
import com.utilitybox.game.wutheringwaves.kuro.GachaHistoryStore
import com.utilitybox.game.wutheringwaves.kuro.GachaResult
import com.utilitybox.game.wutheringwaves.kuro.GachaSnapshotMeta
import com.utilitybox.game.wutheringwaves.kuro.GachaStandardRoster
import com.utilitybox.game.wutheringwaves.kuro.GachaStats
import com.utilitybox.game.wutheringwaves.kuro.WuwaGachaApi
import com.utilitybox.game.wutheringwaves.kuro.WuwaGachaStore
import kotlinx.coroutines.launch

/**
 * 唤取记录（抽卡记录）。
 *
 * ### 用户要做什么
 *
 * 在游戏里打开「唤取记录」，复制那个链接，粘到这里。
 * 应用解析链接里的 4 个参数去查记录。
 *
 * ### 为什么不能让应用自己去拿那个链接
 *
 * `record_id` 在游戏 WebView 里，Android 上第三方应用既读不到游戏的私有目录
 * （分区存储），也扫不了别的进程内存（需要 root）。**只能由用户粘贴。**
 *
 * ### 链接约 1 小时过期
 *
 * 所以页面明确写出这一点，并且**只缓存记录、不缓存链接** ——
 * 链接过期后统计仍然看得到，要更新就再粘一次新链接。
 */

@Composable
fun WuwaGachaScreen(
    /** 打开独立的统计页（P11 补 44）—— 统计不再内嵌在本页 */
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val scope = rememberCoroutineScope()
    // ★ 必须在**组合期**读出来存好（P11 补 41）。
    //   LocalContext.current 是 @Composable 读取，放进 scope.launch{} 里会编译报错 ——
    //   launch 体是运行时执行的，那时已经不在组合上下文里了。
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val webDavStore = remember(appCtx) { com.utilitybox.core.sync.WebDavStore(
        com.utilitybox.core.secret.KeystoreSecretStore(appCtx)
    ) }

    var link by remember { mutableStateOf("") }
    var records by remember { mutableStateOf(WuwaGachaStore.load()) }
    var fetching by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    // ★ P11 补 97：历史快照列表（每次导入存一份，可点进去看往期）
    var history by remember { mutableStateOf(GachaHistoryStore.list()) }
    // ★ P11 补 100：常驻五星名单的编辑弹窗
    var rosterOpen by remember { mutableStateOf(false) }
    var rosterText by remember { mutableStateOf(GachaStandardRoster.names.joinToString("\n")) }

    // ★ 常驻名单编辑弹窗。
    //   放在"查看往期"的早退之前 —— 这个弹窗只在主页面用得到，
    //   往期视图里不需要，跟着早退一起关掉正好。
    if (rosterOpen) {
        RosterDialog(
            initial = rosterText,
            onDismiss = { rosterOpen = false },
            onSave = { text ->
                GachaStandardRoster.save(text)
                rosterText = GachaStandardRoster.names.joinToString("\n")
                rosterOpen = false
                notice = "常驻名单已保存（${GachaStandardRoster.names.size} 个）"
            },
        )
    }

    // 正在查看的往期快照；null 表示看当前记录
    var viewing by remember { mutableStateOf<GachaSnapshotMeta?>(null) }

    // ★ 查看往期快照时**整屏接管**（只读视图）：
    //   不嵌进下面那个 verticalScroll —— 两层滚动容器会互相抢手势，
    //   而且这里要显示的是"那一期的完整统计"，和当前记录无关。
    viewing?.let { meta ->
        SnapshotViewer(meta = meta, onBack = { viewing = null })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
    ) {
        Spacer(Modifier.height(s.m))

        SectionCard {
            Column(modifier = Modifier.padding(s.cardPad)) {
                Text(text = "粘贴唤取记录链接", style = t.bodyLarge, color = c.textPrimary)
                Text(
                    text = "游戏内打开「唤取记录」→ 复制链接 → 粘到下面。链接约 1 小时后失效。",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = s.xxs),
                )
                Spacer(Modifier.height(s.m))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(LocalUbRadius.current.field))
                        .background(c.bgSubtle)
                        .padding(s.m),
                ) {
                    if (link.isEmpty()) {
                        Text(
                            text = "https://aki-gm-resources.aki-game.com/aki/gacha/index.html#/record?…",
                            style = t.caption,
                            color = c.textTertiary,
                        )
                    }
                    BasicTextField(
                        value = link,
                        onValueChange = { link = it; error = null; notice = null },
                        textStyle = t.caption.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                error?.let {
                    Spacer(Modifier.height(s.s))
                    Text(text = it, style = t.caption, color = c.danger)
                }
                notice?.let {
                    Spacer(Modifier.height(s.s))
                    Text(text = it, style = t.caption, color = c.accent)
                }

                Spacer(Modifier.height(s.m))
                UbPrimaryButton(
                    label = when {
                        fetching -> "获取中 ${progress.first}/${progress.second}…"
                        else -> "解析并获取"
                    },
                    enabled = !fetching && link.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val parsed = WuwaGachaApi.parseLink(link)
                        if (parsed == null) {
                            // ★ 解析失败要说清缺什么，而不是笼统的"链接无效" ——
                            //   用户贴错内容时，"缺哪个字段"才是能行动的信息
                            error = "解析失败：链接里找不到 svr_id / player_id / record_id / resources_id。" +
                                "请确认复制的是「唤取记录」页面的完整链接。"
                            return@UbPrimaryButton
                        }
                        scope.launch {
                            fetching = true
                            error = null
                            notice = null
                            var fetched = 0
                            // ★ 逐个卡池查询：接口一次只返回一个池。
                            //   全部试一遍，空池自然被跳过 —— 比让用户先猜自己在哪个池抽过更省事。
                            val all = WuwaGachaApi.POOL_TYPES
                            progress = 0 to all.size
                            all.forEachIndexed { i, (type, _) ->
                                progress = (i + 1) to all.size
                                when (val r = WuwaGachaApi.query(parsed, type)) {
                                    is GachaResult.Ok -> {
                                        if (r.value.isNotEmpty()) {
                                            records = WuwaGachaStore.save(records, r.value)
                                            fetched += r.value.size
                                        }
                                    }
                                    is GachaResult.Failure -> {
                                        // 一个池失败不阻断其它池；把原因记下来
                                        if (error == null) error = r.message
                                    }
                                }
                            }
                            fetching = false
                            if (fetched == 0) {
                                error = "没有取到任何记录。链接可能已过期（约 1 小时），" +
                                    "请回游戏重新打开「唤取记录」再复制一次。"
                            } else {
                                notice = "已获取 $fetched 条记录"

                                // ★ P11 补 97：这次获取**存档成一份历史快照**。
                                //   用户要「历史记录…可以点进去查看往期」。
                                //   ★ 存的是**合并后的全量**（records），不是本次增量 ——
                                //     往期视图应当看到"那天查的时候完整长什么样"。
                                //   ★ 失败不打断主流程：当前记录已经存好了，
                                //     历史存档失败不该让用户以为这次获取白干了。
                                runCatching {
                                    GachaHistoryStore.save(records, parsed.playerId)
                                }
                                history = GachaHistoryStore.list()

                                // ★★ 自动云备份（P11 补 41）—— 用户要的
                                //    「每次链接获取数据完后，会自动存储到百度网盘」。
                                //
                                // ★ 刻意的设计：**云备份失败不打断主流程**。
                                //   记录这时已经存进本地了，如果因为网盘连不上就报错，
                                //   用户会以为"这次获取失败了"，然后重贴链接 —— 白折腾。
                                //   所以失败只**追加**一句说明，不去设 error。
                                //
                                // ★ 用 withContext(IO)：WebDavClient 是阻塞式 HTTP，
                                //   直接放在 launch 的默认调度器上会卡主线程。
                                val dav = webDavStore.load()
                                if (dav.autoUpload && dav.isConfigured && dav.password.isNotBlank()) {
                                    val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.utilitybox.core.sync.WebDavClient.upload(
                                            dav,
                                            WuwaGachaStore.exportJson(),
                                        )
                                    }
                                    notice = when (res) {
                                        is com.utilitybox.core.sync.WebDavResult.Ok ->
                                            "已获取 $fetched 条记录 · 已备份到云端"
                                        is com.utilitybox.core.sync.WebDavResult.Failure ->
                                            "已获取 $fetched 条记录 · 云备份失败：${res.message}"
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }

        if (records.isNotEmpty()) {
            Spacer(Modifier.height(s.s))
            // ★ P11 补 44：统计**不再内嵌在本页**。
            //
            //   用户原话：「统计好后应该是一个**新的界面**，而不是仍然显示在那里下面」。
            //   内嵌有两个问题：一是这一页要同时渲染「统计 5 张卡 + 848 条记录」，
            //   二是统计想看细节时必须一路往下滚。
            //
            //   现在这里只留一个入口，点进去是独立的统计页。
            SectionCard {
                UbNavRow(
                    title = "唤取统计",
                    icon = UbIcon.Sliders,
                    onClick = onOpenStats,
                    trailingText = "${records.size} 条",
                )
            }

            Spacer(Modifier.height(s.s))
            SectionCard {
                Column {
                    UbNavRow(
                        title = "清除本地记录",
                        icon = UbIcon.Warning,
                        onClick = {
                            WuwaGachaStore.clear()
                            records = emptyList()
                            notice = "已清除"
                        },
                        trailingText = "只删本机缓存",
                    )
                }
            }
        } else if (!fetching) {
            Spacer(Modifier.height(s.s))
            SectionCard {
                UbEmpty(
                    title = "还没有记录",
                    description = "粘贴一次唤取记录链接即可。记录会缓存到本机，" +
                        "之后链接过期也还能看到已获取的部分。",
                    modifier = Modifier.padding(vertical = s.m),
                )
            }
        }

        // ---- 常驻五星名单（P11 补 100）----
        //
        // 为什么让用户填：判定「UP 还是歪」必须先知道哪些五星是常驻，
        // 而这个数据拿不到（wiki 没有卡池分类、游戏接口没有 UP 字段）。
        // 常驻池里抽到过的会自动计入，这里补上还没抽到过的。
        Spacer(Modifier.height(s.s))
        SectionCard {
            Column {
                UbNavRow(
                    title = "常驻五星名单",
                    icon = UbIcon.Check,
                    onClick = { rosterOpen = true },
                    trailingText = if (GachaStandardRoster.names.isEmpty()) {
                        "未填"
                    } else {
                        "${GachaStandardRoster.names.size} 个"
                    },
                )
                Text(
                    text = "填了才能算「歪」。常驻池抽到过的会自动计入，不用重复填。",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(horizontal = s.screenH, vertical = s.xs),
                )
            }
        }

        // ---- 历史记录（P11 补 97）----
        //
        // 用户要的形态（参考图）：一行一份存档，显示「账号 · 日期」，点进去看往期。
        //
        // ★ 放在 if/else **外面**：即使当前记录被清空了，历史存档仍然要能看到 ——
        //   两份数据故意互不影响（见 GachaHistoryStore 的说明）。
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(s.s))
            SectionCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(s.cardPad),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "历史记录", style = t.bodyLarge, color = c.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "共 ${history.size} 份",
                            style = t.caption,
                            color = c.textTertiary,
                        )
                    }
                    history.forEachIndexed { i, m ->
                        if (i > 0) UbDivider()
                        UbNavRow(
                            // ★ 账号未知时只显示日期：编一个账号号是假数据（红线）。
                            //   "unknown" 是迁移时 unavoidable 的结果（历史记录里
                            //   从来就没存过 player_id），不是猜的。
                            title = if (m.accountId == "unknown") {
                                m.dateLabel
                            } else {
                                "${m.accountId} · ${m.dateLabel}"
                            },
                            icon = UbIcon.Clock,
                            trailingText = "${m.recordCount} 条 · ${m.timeLabel}",
                            onClick = { viewing = m },
                        )
                    }
                }
            }
        }

        SectionCard {
            Column(modifier = Modifier.padding(s.cardPad)) {
                Text(text = "说明", style = t.bodyMedium, color = c.textPrimary)
                Spacer(Modifier.height(s.xs))
                listOf(
                    "记录缓存在本机应用私有目录，不上传任何服务器",
                    "链接里的 record_id 是短时凭据，应用只在本机使用、不保存",
                    "统计全部由记录直接算出，没有估算或补全",
                    "同一卡池重复获取会按「池 + 名称 + 时间」去重，不会重复计数",
                ).forEach {
                    Row(modifier = Modifier.padding(vertical = s.xxs)) {
                        Text(text = "·", style = t.caption, color = c.textTertiary)
                        Spacer(Modifier.width(s.xs))
                        Text(text = it, style = t.caption, color = c.textSecondary)
                    }
                }
            }
        }

        UbSourceFooter(source = "鸣潮唤取记录接口（aki-game2）", updatedAt = "实时查询")
    }
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color) {
    val t = LocalUbTypography.current
    val c = LocalUbColors.current
    Column {
        Text(text = value, style = t.numericMedium, color = valueColor)
        Text(text = label, style = t.caption, color = c.textTertiary)
    }
}

/**
 * 常驻五星名单编辑弹窗（P11 补 100）。
 *
 * ★ 用**真正的 Dialog**（平台自带遮罩），和刷新间隔选择器一致。
 *
 * ★ 分隔符不做限制：用户多半是从公告里整段粘过来，
 *   换行 / 逗号 / 顿号 / 空格都能切（见 `GachaStandardRoster.parse`）。
 */
@Composable
private fun RosterDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current
    val r = LocalUbRadius.current
    var text by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(r.cardLarge))
                .background(c.bgElevated)
                .padding(s.cardPad),
            verticalArrangement = Arrangement.spacedBy(s.s),
        ) {
            Text(text = "常驻五星名单", style = t.titleMedium, color = c.textPrimary)
            Text(
                text = "写下常驻池里的五星角色名，一行一个或用逗号隔开。" +
                    "这些角色在活动池出现时会被判成「歪」。",
                style = t.caption,
                color = c.textTertiary,
            )
            UbTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                placeholder = "维里奈\n卡卡罗\n鉴心\n安可",
                singleLine = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(s.s, Alignment.End),
            ) {
                UbTextButton(label = "取消", onClick = onDismiss)
                UbPrimaryButton(
                    label = "保存",
                    enabled = GachaStandardRoster.parse(text).isNotEmpty(),
                    onClick = { onSave(text) },
                )
            }
        }
    }
}

/**
 * 往期快照的只读视图（P11 补 97）。
 *
 * 顶部一条返回条 + 该期的时间与条数，下面是**那一期的完整统计**
 * （直接复用 [GachaStatsScreen]，保证往期和当前的统计口径完全一致 ——
 * 另写一套统计必然和当前页对不上，用户会以为数据错了）。
 *
 * ★ 只读：这里不提供导入/清除，往期存档是"当时那一刻的样子"，
 *   能被就地修改就不再是存档了。
 */
@Composable
private fun SnapshotViewer(
    meta: GachaSnapshotMeta,
    onBack: () -> Unit,
) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    // 按 id 缓存：来回切换不同快照时才重新读盘
    val records = remember(meta.id) { GachaHistoryStore.load(meta.id) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = s.screenH, end = s.screenH, top = s.s, bottom = s.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UbTextButton(label = "返回当前", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${meta.dateLabel} ${meta.timeLabel} · ${meta.recordCount} 条",
                style = t.caption,
                color = c.textTertiary,
            )
        }

        if (records.isEmpty()) {
            // 文件损坏/被外部清理 —— 如实说明，不假装有数据
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "这份历史记录已损坏或不存在",
                    style = t.bodyMedium,
                    color = c.textTertiary,
                )
            }
        } else {
            GachaStatsScreen(records = records, modifier = Modifier.weight(1f))
        }
    }
}
