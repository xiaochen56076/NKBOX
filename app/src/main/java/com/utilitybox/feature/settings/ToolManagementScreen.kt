package com.utilitybox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.*
import com.utilitybox.domain.tool.UbToolRegistry

/**
 * 工具管理（P11 从原设置页抽出）。
 *
 * ★ 用户要求「把工具管理合并成为一个选项，点击进入来调节」——
 *   所以这里只做工具开关，不含任何其它设置。
 *
 * ★ **按游戏分组**：现在有两个游戏 tab，把三角洲和鸣潮的工具混在一起
 *   会让人分不清哪个开关属于哪个游戏。
 *   鸣潮目前没有工具，所以不会出现一个空的分组标题。
 */
@Composable
fun ToolManagementScreen(modifier: Modifier = Modifier) {
    val c = LocalUbColors.current
    val s = LocalUbSpacing.current
    val t = LocalUbTypography.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalUbSize.current.bottomBarReserved),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        val gamesWithTools = UbToolRegistry.gamesWithTools()

        if (gamesWithTools.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = s.xxl)) {
                UbEmpty(
                    title = "还没有可管理的工具",
                    description = "接入工具后，这里会出现按游戏分组的开关。",
                )
            }
            return@Column
        }

        gamesWithTools.forEach { game ->
            val tools = UbToolRegistry.allToolsOf(game.id)
            SectionCard(title = game.name) {
                Column {
                    tools.forEachIndexed { index, tool ->
                        val disabled = UbToolRegistry.isDisabled(tool.id)
                        UbSettingRow(
                            title = tool.name,
                            // ★ 用户要求「小字就不用了」：只有**确实需要说明**时才给副标题。
                            //   这里保留是因为"开发中/实验性"直接决定用户该不该指望它，
                            //   去掉会让人以为工具坏了。
                            subtitle = tool.statusLabel,
                            trailing = {
                                Switch(
                                    checked = !disabled,
                                    onCheckedChange = { UbToolRegistry.setDisabled(tool.id, !it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                                )
                            },
                        )
                        if (index != tools.lastIndex) UbDivider()
                    }
                }
            }
        }

        // ★★ P11 补 77：**界面开关**（不是工具）。
        //
        //   用户要求「先在设置里面加一个开关来隐藏登录那个板块，放到工具管理里面去，叫登录入口」。
        //
        //   为什么它不是工具：工具是"一个可进入的功能页"，而这是"要不要显示某块 UI"。
        //   放进工具注册表会污染 `UbToolRegistry` 的语义（那里还要校验路由一致性）。
        //   所以单独一张卡放在这里 —— 位置按用户要求，语义保持干净。
        //
        //   为什么需要它：库街区登录被极验挡住（`geeTestData` 拿不到 → 短信不发），
        //   那个登录表单**填了也不会成功**。能让用户藏起来比摆着更好。
        SectionCard(title = "鸣潮") {
            Column {
                UbSettingRow(
                    title = "登录入口",
                    subtitle = "登录受极验拦截，暂不可用；关闭可隐藏登录区块",
                    trailing = {
                        Switch(
                            checked = com.utilitybox.game.wutheringwaves.WuwaUiSettings.showLoginEntry,
                            onCheckedChange = {
                                com.utilitybox.game.wutheringwaves.WuwaUiSettings.updateShowLoginEntry(it)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                        )
                    },
                )
            }
        }

        Text(
            text = "关掉的工具会从对应游戏页隐藏。新增工具默认开启。",
            style = t.caption,
            color = c.textTertiary,
            modifier = Modifier.padding(horizontal = s.screenH),
        )
    }
}
