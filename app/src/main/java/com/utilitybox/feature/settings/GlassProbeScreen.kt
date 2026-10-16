package com.utilitybox.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.utilitybox.core.design.*

/**
 * 玻璃模糊的**受控验证页**（诊断用，不是产品页面）。
 *
 * ★ 为什么需要它：在正常页面上"玻璃看起来没模糊"这句话无法证伪 ——
 *   因为正常页面的内容被 `bottomBarReserved` 挡在栏外，**栏后面本来就是空的**，
 *   没东西可模糊，测出来的对比度必然是 0。
 *
 *   本页故意在底部铺**高低对比的黑白条纹并让它们穿过导航栏所在区域**，
 *   于是有三种可区分的结果：
 *
 *   | 观察结果 | 含义 |
 *   |---|---|
 *   | 栏内条纹**变糊但仍有起伏** | 玻璃模糊**正常工作** |
 *   | 栏内条纹**消失成纯色** | 玻璃被不透明材质盖住（色调过重） |
 *   | 栏内条纹**和外面一样清晰** | 玻璃**完全没在工作**（Haze 未生效） |
 *
 *   删除本页时请连同路由一起删（`UbRoutes.GLASS_PROBE`）。
 */
@Composable
fun GlassProbeScreen(modifier: Modifier = Modifier) {
    val t = LocalUbTypography.current
    val c = LocalUbColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "玻璃验证页",
                style = t.displayLarge,
                color = c.textPrimary,
            )
            Text(
                text = "下方条纹会穿过悬浮导航栏。若栏内条纹变糊，说明高斯模糊在工作。",
                style = t.bodyLarge,
                color = c.textSecondary,
            )
        }
        // 60 条高对比横条纹，铺满并继续向下延伸
        repeat(60) { i ->
            Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                // 每行左右再分块，形成网格状高频图案
                repeat(6) { j ->
                    val dark = (i + j) % 2 == 0
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(if (dark) Color(0xFF101010) else Color(0xFFF0F0F0)),
                    )
                }
            }
        }
    }
}
