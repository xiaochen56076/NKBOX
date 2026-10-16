package com.utilitybox.game.deltaforce.friendstatus.data

import com.utilitybox.domain.datasource.FriendStatusDataSource
import com.utilitybox.domain.model.DataSourceRef
import com.utilitybox.domain.model.Friend
import com.utilitybox.domain.model.FriendStatus
import com.utilitybox.domain.model.SourceState

/**
 * 好友状态 · 模拟数据源（**仅用于开发期视觉验证**）。
 *
 * 依据 PROJECT_RULES.md 第 8 节，mock 必须满足全部三条：
 *  1. 类名以 `Mock` 开头          -> 本类
 *  2. 模型携带 `isMock = true`     -> 见下方 sourceRef 的 displayName 与页面 UbMockBadge
 *  3. UI 上有可见标记              -> FriendStatusScreen 显示 `UbMockBadge`
 *
 * ★ 真实运行时**不得使用本类**：当前绑定的是 `UnavailableFriendStatusSource`。
 *   本类只在开发期开关打开时用于验证"数据源就绪"后的四态分组排序视觉。
 *
 * 交付前请全局搜索 `isMock` 与 `Mock` 定位残留。
 */
class MockFriendStatusDataSource : FriendStatusDataSource {

    override val ref = DataSourceRef(
        id = "friendstatus.mock",
        displayName = "模拟数据（非真实）",
        verified = SourceState.PENDING,
    )

    /**
     * 供组合期直接取用的样本（非 suspend）。
     * 真实的 [FriendStatusDataSource.fetchFriends] 是 suspend 的；
     * 这里额外提供一个同步方法，只为开发期预览，避免在 composable 里启动协程。
     */
    fun friendsForPreview(): List<Friend> = listOf(
        Friend("a1", "阿澈", FriendStatus.IN_GAME, ref, "刚刚", null),
        Friend("a2", "老陈", FriendStatus.ONLINE, ref, "2 分钟前", null),
        Friend("a3", "小鱼", FriendStatus.ONLINE, ref, "5 分钟前", null),
        Friend("a4", "KK", FriendStatus.OFFLINE, ref, "昨天", "2 小时前"),
        Friend("a5", "老王", FriendStatus.OFFLINE, ref, "今天 09:12", "3 小时前"),
        // 昵称取不到时显示脱敏 ID 尾号 —— 真实数据也会遇到这种形态
        Friend("a6", null, FriendStatus.UNKNOWN, ref, null, null),
    )

    override suspend fun fetchFriends(): List<Friend> = friendsForPreview()
}
