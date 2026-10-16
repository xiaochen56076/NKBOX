package com.utilitybox.domain.datasource

import com.utilitybox.domain.model.DataSourceRef
import com.utilitybox.domain.model.Friend
import com.utilitybox.domain.model.SourceState

/**
 * 好友状态数据源契约（接口占位）。
 *
 * ★ 现状：**没有任何可用数据源**，本功能暂停开发，仅保留接口。
 *
 * 依据 docs/data-sources.md 第 2.3 节，P01 已用真实请求实测排除全部候选：
 *  - `person/friendinfo`        -> 402 付费（1 积分/次），且只返回昵称/头像，**无状态字段**
 *  - `gamesafe/online`          -> 400 需 X-Framework-Token，且只能查**自己**，无「游戏中」
 *  - `person/friends` / `friend` / `friendlist` -> **404 不存在**
 *  - `df/friend/status` / `social/friends`      -> **404 不存在**
 *  - 周报 `reportDm.wbn.friends` -> 属统计字段，非实时，且只是"当周一起玩过的人"，**不是好友列表**
 *
 * 结论：`好友列表` 与 `游戏中` 两个关键要素在现有数据源中完全缺失，
 * **即使付费也解决不了**（缺的是"好友列表"这一环）。
 *
 * 因此本模块当前只做两件事：
 *  1. 把契约定义好，未来数据源就绪时替换实现即可（不改页面与分析逻辑）；
 *  2. 如实告知用户"功能暂不可用"，**绝不用模拟数据填充**（红线）。
 *
 * 未来接入时的实现要求（写在此处以免遗忘）：
 *  - 只有数据源能**可靠提供**「游戏中」状态时才可出现 `FriendStatus.IN_GAME`；
 *  - `UNKNOWN` **绝不**自动降级为 `OFFLINE`；
 *  - 排序固定为 游戏中 -> 在线 -> 离线 -> 未知；
 *  - 昵称取不到时显示脱敏 ID 尾号，不编造。
 */
interface FriendStatusDataSource {

    /** 数据源标识（用于 UI 如实展示来源与验证状态） */
    val ref: DataSourceRef

    /**
     * 拉取好友列表。
     *
     * 返回值语义（重要，避免"空列表"与"拿不到"混淆）：
     *  - `null`  -> 本数据源**无法提供**该能力（当前即为这种情况）
     *  - `emptyList()` -> 能提供，但用户确实没有好友
     *  - 非空列表 -> 正常结果
     *
     * 用可空类型而非空列表，是为了从类型层面防止把"没有数据源"伪装成"没有好友"。
     */
    suspend fun fetchFriends(): List<Friend>?
}

/**
 * 当前使用的实现：**明确不可用**。
 *
 * 声明为不可用而不是返回空列表，是为了让 UI 能如实说明原因，
 * 而不是显示一个会让人误解的"你没有好友"。
 */
object UnavailableFriendStatusSource : FriendStatusDataSource {

    override val ref = DataSourceRef(
        id = "friendstatus.unavailable",
        displayName = "无可用数据源",
        verified = SourceState.UNAVAILABLE,
    )

    override suspend fun fetchFriends(): List<Friend>? = null
}
