package com.offline.due.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventMode
import com.offline.due.data.RepeatRule
import com.offline.due.data.RowDensity
import com.offline.due.domain.CountdownInfo
import com.offline.due.domain.CountdownMath
import com.offline.due.ui.LocalNow
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.displaySp
import com.offline.due.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun EventRow(
    event: CountdownEvent,
    info: CountdownInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = RowInset,

    showCardLook: Boolean = true,

    rowDensity: RowDensity = RowDensity.STANDARD,
) {
    val g = LocalGlass.current
    val inverted = info.isToday
    val accent = Color(event.color)
    val typo = MaterialTheme.typography

    val titleStyle = remember(typo, rowDensity) {
        if (rowDensity.titleScale == 1f) {
            typo.headlineSmall
        } else {
            typo.headlineSmall.copy(
                fontSize = typo.headlineSmall.fontSize * rowDensity.titleScale,
                lineHeight = typo.headlineSmall.lineHeight * rowDensity.titleScale,
            )
        }
    }

    val density = LocalDensity.current
    val photoPx = with(density) { 240.dp.roundToPx() }

    val stateGap = with(density) { (titleStyle.fontSize * 0.227f).toDp() }

    val unitWidth = with(density) {
        val ls = typo.labelMedium.letterSpacing
        val track = if (ls.type == TextUnitType.Unspecified) 0f else ls.value
        (typo.labelMedium.fontSize.value + track).dp
    }
    val photoName = if (showCardLook && !inverted) event.cardImage else null
    val photo = rememberCardPhoto(photoName, photoPx)
    val tint = if (showCardLook && !inverted && photo == null) Palette.card(event) else null

    val veil = remember(g.scrim) {
        Brush.verticalGradient(
            0f to g.scrim.copy(alpha = 0.52f),
            0.45f to g.scrim.copy(alpha = 0.26f),
            1f to g.scrim.copy(alpha = 0.46f),
        )
    }

    val titleColor = if (inverted) g.onInverse else g.ink
    val dimColor = if (inverted) g.onInverse.copy(alpha = 0.62f) else g.inkMid
    val numberColor = if (inverted) g.onInverse else Palette.number(event, g)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val dx = 5.dp.toPx()
                val dy = 2.dp.toPx()
                val box = Size(size.width - dx * 2f, size.height - dy * 2f)
                if (inverted) {
                    drawRoundRect(
                        color = g.inverse,
                        topLeft = Offset(dx, dy),
                        size = box,
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                    return@drawBehind
                }
                val radius = CornerRadius(14.dp.toPx())
                val p = photo
                if (p != null) {
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(dx, dy, dx + box.width, dy + box.height),
                                cornerRadius = radius,
                            )
                        )
                    }
                    clipPath(path) {
                        val scale = maxOf(size.width / p.width, size.height / p.height)
                        val dw = p.width * scale
                        val dh = p.height * scale
                        drawImage(
                            image = p,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(p.width, p.height),
                            dstOffset = IntOffset(
                                ((size.width - dw) / 2f).roundToInt(),
                                ((size.height - dh) / 2f).roundToInt(),
                            ),
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                        drawRect(brush = veil)
                    }
                } else {
                    val t = tint
                    if (t != null) {
                        drawRoundRect(
                            color = t.copy(alpha = 0.10f),
                            topLeft = Offset(dx, dy),
                            size = box,
                            cornerRadius = radius,
                        )
                    }
                }
            }
            .pressable(
                onClick,
                scale = 1f,
                specular = false,
                veil = if (inverted) g.onInverse else null,
            )
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = rowDensity.verticalPaddingDp.dp,
                bottom = rowDensity.verticalPaddingDp.dp,
            )
            .drawBehind {
                drawRoundRect(
                    color = if (inverted) g.onInverse.copy(alpha = 0.85f) else accent,
                    size = Size(3.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
// 竖线本身已经画好了，这里只占住它那 3dp 的位置。
        Spacer(Modifier.width(3.dp))
        Spacer(Modifier.width(13.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.Top),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.pinned) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                if (inverted) g.onInverse else g.accent,
                                CircleShape,
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = event.title,
                    style = titleStyle,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val state = CountdownMath.stateWord(info)
                if (state != null) {
                    Spacer(Modifier.width(stateGap))
                    Text(
                        text = state,
                        style = titleStyle,
                        color = dimColor,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = subtitle(event, info),
                style = MaterialTheme.typography.bodySmall,
                color = dimColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.align(Alignment.Bottom),
            horizontalAlignment = Alignment.End,
        ) {
            val numSize = displaySp(rowDensity.numberSp.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(
                    text = CountdownMath.headline(info),
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = numSize,
                    color = numberColor,
                    durationMillis = 320,
                )
                Spacer(Modifier.width(3.dp))
                val unit = CountdownMath.suffix(info)
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = dimColor,
                        modifier = Modifier.padding(
                            bottom = with(LocalDensity.current) { (numSize * 0.11f).toDp() },
                        ),
                    )
                } else {
// 空的占位：只占宽度，没有高度，不影响这一行的高度。
                    Spacer(Modifier.width(unitWidth))
                }
            }
            if (event.showSeconds) {
                ClockLine(event = event, numSize = numSize, color = dimColor)
            }
            Text(
                text = event.category,
                style = MaterialTheme.typography.labelSmall,
                color = if (inverted) g.onInverse.copy(alpha = 0.55f) else g.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .widthIn(max = 78.dp),
            )
        }
    }
}

@Composable
private fun ClockLine(event: CountdownEvent, numSize: TextUnit, color: Color) {
    val now by LocalNow.current
// 抬进数字行盒底部那段死区（descent = 0.195em，见 tools/build_fonts.py）。
    val lift = with(LocalDensity.current) { (numSize * 0.195f).toDp() }
    val clockSize = (numSize.value * 0.30f)
        .coerceAtMost(MaterialTheme.typography.labelLarge.fontSize.value)
    RollingText(
        text = CountdownMath.precise(event, now).clock(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontSize = clockSize.sp,
        durationMillis = 200,
        modifier = Modifier.offset(y = -lift),
    )
}

private fun subtitle(event: CountdownEvent, info: CountdownInfo): String {
    if (event.mode == EventMode.ELAPSED) {
        val start = info.cycleStart
        return if (info.hasEnd) {
            val end = info.target
            val whenText = if (end.year == info.today.year) {
                "${end.monthValue}月${end.dayOfMonth}日"
            } else {
                "${end.year}年${end.monthValue}月${end.dayOfMonth}日"
            }
            "走到 $whenText · 共 ${info.totalDays} 天"
        } else {
            "从 ${start.year}年${start.monthValue}月${start.dayOfMonth}日 起"
        }
    }

    if (info.isToday) {
        return if (event.repeat == RepeatRule.NONE) "就是今天" else "就是今天 · ${event.repeat.label}"
    }

    val target = info.target
    val date = if (event.repeat == RepeatRule.NONE) {
        "${target.year}年${target.monthValue}月${target.dayOfMonth}日"
    } else {
        "${target.monthValue}月${target.dayOfMonth}日"
    }
    return if (event.repeat == RepeatRule.NONE) date else "$date · ${event.repeat.label}"
}

