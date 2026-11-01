package com.offline.due.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Radius

@Composable
fun BetaDialog(

    onDismiss: (dontShowAgain: Boolean) -> Unit,
) {
    val g = LocalGlass.current
    var dontShow by remember { mutableStateOf(false) }
    val version = rememberAppVersion()

    GlassDialog(onDismiss = { onDismiss(dontShow) }) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(horizontal = CardInset, vertical = 20.dp)) {
// 版本号用小字，像标签而不是标题——它不是主角
                Text(
                    text = "beta $version",
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkSoft,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    text = "此软件目前还在测试阶段",
                    style = MaterialTheme.typography.headlineSmall,
                    color = g.ink,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "功能还会变动，数据请自己留一份备份。发现问题欢迎告诉我。",
                    style = MaterialTheme.typography.bodySmall,
                    color = g.inkMid,
                )

                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(g.hairline)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.chip))
                            .pressable({ dontShow = !dontShow }, scale = 0.97f)
                            .padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckMark(on = dontShow)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "不再显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = g.inkMid,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextAction(
                        text = "知道了",
                        onClick = { close { onDismiss(dontShow) } },
                        emphasis = true,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.substringBefore('-')?.takeIf { it.isNotBlank() } ?: "?"
    }
}

@Composable
private fun CheckMark(on: Boolean) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(Radius.dot)
    Box(
        modifier = Modifier
            .size(17.dp)
            .clip(shape)
            .background(if (on) g.accent else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else g.stroke, shape)
            .drawBehind {
                if (!on) return@drawBehind
                val w = size.width
                val h = size.height
                val tick = Path().apply {
                    moveTo(w * 0.24f, h * 0.52f)
                    lineTo(w * 0.44f, h * 0.72f)
                    lineTo(w * 0.78f, h * 0.29f)
                }
                drawPath(
                    path = tick,
                    color = g.onInverse,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
    )
}

@Composable
fun Watermark(modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    Text(
        text = "机翼",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
        color = g.inkSoft.copy(alpha = 0.5f),
        modifier = modifier.padding(end = 14.dp, bottom = 3.dp),
    )
}

