package com.utilitybox.game.deltaforce.friendstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.utilitybox.core.design.*
import com.utilitybox.domain.datasource.UnavailableFriendStatusSource
import com.utilitybox.domain.mock.FriendStatusCopy
import com.utilitybox.domain.model.Friend
import com.utilitybox.domain.model.FriendStatus
import com.utilitybox.domain.model.SourceState
import com.utilitybox.domain.model.groupAndSortFriends
import com.utilitybox.game.deltaforce.friendstatus.data.MockFriendStatusDataSource

/**
 * 好友状态页。
 *
 * 现状（用户已裁决：本功能先放放，保留接口占位）：
 *   公开数据源中不存在任何能获取"好友列表"或"好友在线状态"的接口。
 *   P01 已用真实请求逐一排除全部候选（详见 docs/data-sources.md 第 2.3 节）。
 *
 * 因此本页默认走 [PendingState] 分支：不产生任何 Friend 实例，
 * 明确告诉用户"功能开发中、原因是什么"，而不是用空列表或伪造数据糊过去。
 *
 * 开发期开关 DEV_SHOW_READY_FORM 仅用于验证"数据源就绪后"的四态分组排序视觉，
 * 交付前必须置为 false 或整体移除（届时全局搜索 isMock 即可定位）。
 */
private const val DEV_SHOW_READY_FORM = true

@Composable
fun FriendStatusScreen(modifier: Modifier = Modifier) {
    val s = LocalUbSpacing.current
    var showReadyForm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (DEV_SHOW_READY_FORM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.screenH, vertical = s.xs),
                horizontalArrangement = Arrangement.spacedBy(s.s),
            ) {
                UbChip(
                    label = "当前状态（开发中）",
                    selected = !showReadyForm,
                    onClick = { showReadyForm = false },
                )
                UbChip(
                    label = "数据源就绪后（示意）",
                    selected = showReadyForm,
                    onClick = { showReadyForm = true },
                )
            }
        }

        if (showReadyForm) {
            ReadyFormPreview(modifier = Modifier.weight(1f))
        } else {
            PendingState(modifier = Modifier.weight(1f))
        }
    }
}

/** 真实形态：功能保留、数据源待接入，如实说明并展示已核验结论 */
@Composable
private fun PendingState(modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        UbEmpty(
            title = FriendStatusCopy.UNAVAILABLE_TITLE,
            description = FriendStatusCopy.UNAVAILABLE_DESC,
            actionLabel = "查看数据源状态",
            onAction = {},
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenH)
                .clip(RoundedCornerShape(LocalUbRadius.current.card))
                .background(c.bgElevated)
                .padding(s.cardPad),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    Text(
                        text = "已核验的候选接口",
                        style = t.labelMedium,
                        color = c.textSecondary,
                    )
                    UbStatusDot(color = c.warning)
                    Text(text = "本功能开发中", style = t.caption, color = c.warning)
                }
                FriendStatusCopy.VERIFIED_CANDIDATES.forEach {
                    Text(text = "· $it", style = t.caption, color = c.textTertiary)
                }
                Text(
                    text = "数据源就绪后本功能会自动点亮，接口已按可替换的方式预留。",
                    style = t.caption,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = s.xs),
                )
            }
        }
    }
}

/** 示意形态：数据源就绪后的分组排序（四态，未知单独成组且排最后） */
@Composable
private fun ReadyFormPreview(modifier: Modifier = Modifier) {
    val s = LocalUbSpacing.current
    val source = remember { MockFriendStatusDataSource() }
    // 用同步预览方法，避免在 composable 里调用 suspend 函数
    val groups = remember { groupAndSortFriends(source.friendsForPreview()) }
    // ★ 闸门声明在 LazyColumn **之外**：LazyListScope 的 lambda 不是 @Composable 上下文
    val entrance = rememberUbListEntrance()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = s.screenH,
            end = s.screenH,
            bottom = LocalUbSize.current.bottomBarReserved,
        ),
        verticalArrangement = Arrangement.spacedBy(s.listGap),
    ) {
        item(key = "mock-badge") {
            Box(modifier = Modifier.padding(top = s.s)) { UbMockBadge() }
        }
        groups.forEach { group ->
            item(key = "h-${group.status.name}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = s.m, bottom = s.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    UbStatusDot(color = statusColor(group.status))
                    Text(
                        text = "${group.status.label}（${group.friends.size}）",
                        style = LocalUbTypography.current.labelMedium,
                        color = LocalUbColors.current.textTertiary,
                    )
                }
            }
            itemsIndexed(group.friends, key = { _, it -> it.id }) { index, f ->
                // ★ P11 补 14：入场闸门 —— 数据到达时那一屏播入场，滚动进入的直接显示
                FriendRow(
                    friend = f,
                    modifier = Modifier.ubListEnter(f.id, index, entrance),
                )
            }
            // 注：`entrance` 整页共用一份 —— 好友页按在线状态分组，
            // 但它们是**同一个滚动容器**里的连续内容，共用一屏名额才对。
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val r = LocalUbRadius.current
    val t = LocalUbTypography.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(r.card))
            .background(c.bgElevated)
            .padding(s.cardPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        // 头像位占位（真实数据源提供 picurl）
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(c.bgSubtle)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                // 昵称取不到时显示脱敏 ID 尾号，而不是留空或编造
                text = friend.displayName ?: "…${friend.id.takeLast(6)}",
                style = t.bodyLarge,
                color = if (friend.displayName == null) c.textTertiary else c.textPrimary,
            )
            val sub = when (friend.status) {
                FriendStatus.OFFLINE -> friend.lastSeenLabel?.let { "最后在线 $it" }
                FriendStatus.UNKNOWN -> "状态未知（数据源未提供该好友状态）"
                else -> friend.statusKnownAtLabel
            }
            if (sub != null) {
                Text(text = sub, style = t.caption, color = c.textTertiary)
            }
        }
        // 状态必须同时有颜色与文字，不可仅靠颜色区分（无障碍要求）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            UbStatusDot(color = statusColor(friend.status))
            Text(text = friend.status.label, style = t.labelMedium, color = c.textSecondary)
        }
    }
}

@Composable
private fun statusColor(status: FriendStatus) = when (status) {
    FriendStatus.IN_GAME -> LocalUbColors.current.statusInGame
    FriendStatus.ONLINE -> LocalUbColors.current.statusOnline
    FriendStatus.OFFLINE -> LocalUbColors.current.statusOffline
    FriendStatus.UNKNOWN -> LocalUbColors.current.statusUnknown
}

/** 供设置页 / 数据源状态页复用的能力结论（取自真实的占位数据源） */
val FRIEND_STATUS_SOURCE_STATE: SourceState = UnavailableFriendStatusSource.ref.verified
