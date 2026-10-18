package com.utilitybox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.utilitybox.core.design.*

/**
 * 设置入口页（P11 重构）。
 *
 * ★ 用户要求：设置从「我的」右上角齿轮进入，并且**按类别拆成若干入口**，
 *   「工具管理合并成一个选项」「外观也是一个选项」「数据也是」。
 *
 * 因此本页**没有**任何开关或列表，只有入口行 ——
 * 每一项的实际内容在各自的子页里，这样设置页不会越来越长。
 *
 * ★★ P11 补 3：用户指出「工具管理 / 外观 / 数据」这三项**在「我的」页已经有一份**，
 *   要求从本页圈掉。于是分工定下来：
 *
 *   - 「我的」页 = 各类设置的**分发入口**（工具管理 / 外观 / 数据 / 设置）
 *   - 本页 = 只放**不属于上面三类**的全局信息（目前只有「关于与开源许可」）
 *
 *   本页因此只剩一张卡。页面薄没关系 ——
 *   比"同一目标有两个入口、用户还得猜哪个才正式"要好。
 *
 * ★★ P11 补：用户指出「设置界面太靠上了，应该和图片左上角一样显示一个设置的字样，
 *   然后上面留空」。
 *
 *   原因：本页是**根级页面**（从「我的」直接进入），不是 [com.utilitybox.feature.common.UbToolPage]
 *   那样的二级页 —— 二级页有返回键与标题栏外壳，而根级页没有外壳，
 *   于是标题就被漏掉了，内容直接顶到状态栏。
 *
 *   现在补上与「我的」一致的标题行：**大标题 + 上方留白**。
 *   ★ 不放假齿轮：这一页本身就是设置，再放一个设置入口是循环引用。
 *
 * ★★ P11 补 2：用户要求「把我的右上角的设置去掉直接变成选项放到数据下面去一起排着，
 *   把关于放到设置中去」。
 *
 *   于是本页新增「关于与开源许可」入口（原来在「我的」页）。
 *   ★ 它单独占一张卡：它是**信息类**入口，和"改设置"不是一类，
 *     混在同一张卡里会让人以为是又一个设置分组。
 */
@Composable
fun SettingsHubScreen(
    onOpenAbout: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenData: () -> Unit,
    onOpenInteraction: () -> Unit,
    /** 账号管理（P11 补 39）—— 用户点名要这个入口 */
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        // ★★ P11 补 23：这里原本有一段「大标题 + 上方留白」的页头，**已删除**。
        //
        //   它来自 SETTINGS 还是**底部根标签页**的时代 —— 根页没有外壳，
        //   所以标题得自己画。但 P11 把「设置」挪进「我的」之后，
        //   本页已经是二级页，页头由外层的 `SubPage(title = "设置")` 提供
        //   （带返回键 + 标题栏）。
        //
        //   结果就是：全 App 只有「设置」这一页没有返回键 ——
        //   因为兄弟页（工具管理 / 外观 / 数据 / 关于）都套了 SubPage，
        //   只有它自己还画着根页时代的标题。
        //
        //   ★ 删掉自绘标题后**不要**再补一个 —— 会变成两个「设置」叠在一起。
        //
        // ★★ P11 补 3：这里原本还有一张卡，重复列着「工具管理 / 外观 / 数据」——
        //   而那三项**已经在「我的」页的入口卡里**了（同一个 App 里同一目标两个入口，
        //   用户还得猜哪个才是"正式"的）。
        //   用户要求去掉，于是本页只剩下面这些卡片。
        //
        //   分工：
        //   - 「我的」= 各类设置的分发入口（工具管理 / 外观 / 数据 / 设置）
        //   - 「设置」= 交互偏好 + 不属于上面三类的全局信息
        // ---- 交互：**已移走**（P11 补 24）----
        //
        // ★ 这里原本内嵌了一个「交互」分组 + 开关。用户指出这是错的形式：
        //   「那个交互做成和这些一样的那种选项，可以点进去的」——
        //   即与「工具管理 / 外观 / 数据」并列的**入口行** + 独立二级页。
        //
        //   已改为：入口行放在「我的」页的入口卡里，
        //   内容在新页 `InteractionSettingsScreen`（路由 SETTINGS_INTERACTION）。
        //
        // ★ 为什么这个形式更好（不只是好看）：**开关会长大**。
        //   现在一项，以后会有"长按预览""震动反馈"之类。
        //   摊在本页里，这一页会越来越长；做成子页则天然有地方放。
        //
        // ★ 本页因此回到 P11 补 3 定下的分工：
        //   - 「我的」= 各类设置的分发入口（工具管理 / 外观 / 数据 / 交互 / 设置）
        //   - 「设置」= 只放不适合放进「数据」的全局信息（关于与开源许可）

        // ---- 设置分组入口（P11 补 25 迁入）----
        //
        // ★ 用户把「工具管理 / 数据 / 交互」圈出来，要求从「我的」页**移到本页**。
        //   这三项本来就是"设置"的子类，放在本页比摊在「我的」页更合理。
        //
        // ★ 注意这**推翻**了 P11 补 3 的决定（当时把这三项从本页删掉、
        //   理由是与「我的」页重复）。现在重复没了 ——
        //   它们只在**这一处**，所以"同一目标两个入口"的问题不存在了。
        //
        // ★ 「外观」与「设置」仍在「我的」页 —— 那是用户圈选的范围，未擅自扩大。
        SectionCard(title = "设置项") {
            Column {
                // ★ P11 补 84：用户要求「把账号管理移动到工具管理上面」。
                //   账号管理排第一 —— 它是"用户身份/数据"这一层的东西，
                //   放在最前面比夹在功能开关中间更符合心智顺序。
                UbNavRow(title = "账号管理", icon = UbIcon.User, onClick = onOpenAccount)
                UbDivider()
                UbNavRow(title = "工具管理", icon = UbIcon.Grid, onClick = onOpenTools)
                UbDivider()
                UbNavRow(title = "数据", icon = UbIcon.Database, onClick = onOpenData)
                UbDivider()
                UbNavRow(title = "交互", icon = UbIcon.Sliders, onClick = onOpenInteraction)
            }
        }

        SectionCard {
            Column {
                UbNavRow(
                    title = "关于与开源许可",
                    icon = UbIcon.Info,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
