package com.utilitybox

import android.app.Application
import com.utilitybox.core.secret.ApiKeyProvider
import com.utilitybox.domain.tool.UbToolRegistry

/**
 * 应用入口。
 *
 * 在这里做**一次性初始化**（而不是散落在 Activity 或 Composition 里）：
 *  1. 注册表自检 —— 配置写错时立刻暴露，而不是等用户点到那个工具才发现空白页
 *  2. API Key 提供器 —— 注入本机存储与构建期兜底值
 *  3. 设置持久化 —— 主题 / 玻璃 / 减少动效重启后保持
 *  4. 图标磁盘缓存 —— 让列表滑动不必每次冷启动重新下载
 *
 * ★ 为什么 Key 要在这里注入而不是在数据源里直接读全局静态：
 *   数据源需要能在单元测试中替换实现（见 `RemoteDailyCodeSource` 的注释）。
 *   把"从哪读"集中到本类，数据源只依赖抽象。
 */
class UbApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 注册表自检：工具 id 唯一、路由唯一且非空、gameId 已注册
        UbToolRegistry.verifyIntegrity()

        // API Key：用户在设置页输入的值优先，构建期注入值兜底
        ApiKeyProvider.init(
            context = this,
            buildTimeKey = BuildConfig.UBOX_API_KEY,
        )

        // 设置：从持久化存储加载（P09）—— 主题/玻璃/减少动效等重启后保持
        com.utilitybox.core.settings.UbSettingsState.init(this)

        // 图标磁盘缓存（P11 补 13）。
        // ★ 放这里而不是 Activity：磁盘目录只要挂一次，进程内有效；
        //   放 Activity 会在每次重建时重复初始化，而且容易顺手传入 Activity 的 Context。
        com.utilitybox.core.design.UbImageLoader.install(this)

        // 库街区凭据存储（P11 补 16）。
        // ★ 必须在任何界面读登录态之前挂好，否则首帧会误判为"未登录"，
        //   已登录用户会看到一瞬间的登录页。
        com.utilitybox.game.wutheringwaves.kuro.KuroCredentialStore.init(this)
        com.utilitybox.game.wutheringwaves.kuro.KuroSession.restore()

        // 唤取记录缓存（P11 补 17）。只存记录本身，不存链接 ——
        // 链接里的 record_id 是短时凭据，见 WuwaGachaApi 的说明。
        com.utilitybox.game.wutheringwaves.kuro.WuwaGachaStore.install(this)

        // 图鉴（库街区公开 WIKI）的磁盘缓存（P11 补 18）。
        // 分类树 24h / 条目列表 6h —— 接口是逆向来的，原作者自己也在做缓存。
        com.utilitybox.game.wutheringwaves.kuro.KuroWikiApi.install(this)

        // 价格搜索历史（P11 补 53）。进程启动读一次盘，之后界面订阅它的快照状态。
        // ★ 不用 Keystore：搜索历史不是密钥，加密既没必要，
        //   也会把"清除历史"变成一个涉及密钥的操作。
        com.utilitybox.game.deltaforce.itemprice.ItemSearchHistory.install(this)
    }
}
