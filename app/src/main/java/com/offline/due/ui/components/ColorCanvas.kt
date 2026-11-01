package com.offline.due.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.offline.due.data.CountdownEvent
import com.offline.due.domain.ColorMath
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Radius
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val HUE_STOPS = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

@Composable
fun HsvPanel(
    hue: Float,
    sat: Float,
    valo: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 196.dp,
    onPick: (x: Float, y: Float) -> Unit,
) {
    val g = LocalGlass.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.panel))
            .border(1.dp, g.stroke, RoundedCornerShape(Radius.panel))
            .pickXY(onPick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
// 底色 = 当前色相的纯色，再叠白（降饱和）和黑（降明度）
            drawRect(Color(hsvToArgb(hue, 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

// 取色点：白圈套一圈半透明黑，浅色底和深色底上都看得见
            val cx = sat * size.width
            val cy = (1f - valo) * size.height
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = 9.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(2.5.dp.toPx()),
            )
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

@Composable
fun HueBar(
    hue: Float,
    modifier: Modifier = Modifier,
    onPick: (x: Float) -> Unit,
) {
    val g = LocalGlass.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(Radius.panel))
            .border(1.dp, g.stroke, RoundedCornerShape(Radius.panel))
            .pickXY { x, _ -> onPick(x) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(HUE_STOPS))
            val cx = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = size.height / 2f - 4.dp.toPx(),
                center = Offset(cx.coerceIn(0f, size.width), size.height / 2f),
                style = Stroke(2.5.dp.toPx()),
            )
        }
    }
}

@Composable
fun ShadeBar(
    hue: Float,
    sat: Float,
    valo: Float,
    modifier: Modifier = Modifier,
    vRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onPick: (Float) -> Unit,
) {
    val g = LocalGlass.current
    val lo = vRange.start
// 区间退化成一个点的话，除零会让指针跑到无穷远。给一个下限。
    val span = (vRange.endInclusive - lo).takeIf { it > 1e-4f } ?: 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(Radius.panel))
            .border(1.dp, g.stroke, RoundedCornerShape(Radius.panel))
            .pickXY { x, _ -> onPick(lo + x * span) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color(hsvToArgb(hue, sat, lo)),
                        Color(hsvToArgb(hue, sat, lo + span)),
                    )
                )
            )
            val cx = ((valo - lo) / span).coerceIn(0f, 1f) * size.width
            drawCircle(
                color = Color.White,
                radius = size.height / 2f - 4.dp.toPx(),
                center = Offset(cx, size.height / 2f),
                style = Stroke(2.5.dp.toPx()),
            )
        }
    }
}

@Composable
fun ColorReadout(
    color: Long,
    modifier: Modifier = Modifier,
    name: String = CountdownEvent.colorName(color),
    hex: String = if (color == CountdownEvent.NO_COLOR) "—" else CountdownEvent.hexOf(color),
) {
    val g = LocalGlass.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (color == CountdownEvent.NO_COLOR) Color.Transparent else Color(color)
                )
                .border(
                    1.dp,
                    g.stroke,
                    CircleShape,
                )
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = g.ink,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = hex,
            style = MaterialTheme.typography.labelMedium,
            color = g.inkSoft,
        )
    }
}

@Composable
fun PresetGrid(
    presets: List<Long>,
    selected: Long,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    val g = LocalGlass.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { value ->
                    val on = value == selected
                    val ink = if (ColorMath.luminance(value.toInt()) > 0.55f) {
                        Color.Black.copy(alpha = 0.55f)
                    } else {
                        Color.White
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.5f)
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(Color(value))
                            .border(
                                width = if (on) 2.5.dp else 1.dp,
                                color = if (on) g.edgeLight else Color.Black.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(Radius.chip),
                            )
                            .pressable({ onPick(value) }, scale = 0.94f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) {
                            Canvas(modifier = Modifier.size(20.dp)) {
                                val w = size.width
                                val h = size.height
                                val tick = Path().apply {
                                    moveTo(w * 0.16f, h * 0.54f)
                                    lineTo(w * 0.40f, h * 0.78f)
                                    lineTo(w * 0.86f, h * 0.24f)
                                }
                                drawPath(
                                    path = tick,
                                    color = ink,
                                    style = Stroke(
                                        width = 2.2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                            }
                        }
                    }
                }
// 最后一行不满时补空位，否则剩下的色块会被 weight 拉宽
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

internal fun Modifier.pickXY(onPick: (x: Float, y: Float) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            onPick((down.position.x / w).coerceIn(0f, 1f), (down.position.y / h).coerceIn(0f, 1f))
            down.consume()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.pressed } ?: break
                onPick(
                    (change.position.x / w).coerceIn(0f, 1f),
                    (change.position.y / h).coerceIn(0f, 1f),
                )
                change.consume()
            }
        }
    }

internal fun argbToHsv(argb: Long): FloatArray {
    val r = ((argb shr 16) and 0xFF).toFloat() / 255f
    val gr = ((argb shr 8) and 0xFF).toFloat() / 255f
    val b = (argb and 0xFF).toFloat() / 255f
    val hi = max(r, max(gr, b))
    val lo = min(r, min(gr, b))
    val d = hi - lo
    val h = when {
        d == 0f -> 0f
        hi == r -> 60f * (((gr - b) / d) % 6f)
        hi == gr -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - gr) / d) + 4f)
    }
    return floatArrayOf(
        ((h % 360f) + 360f) % 360f,
        if (hi == 0f) 0f else d / hi,
        hi,
    )
}

internal fun hsvToArgb(h: Float, s: Float, v: Float): Long {
    val hh = (((h % 360f) + 360f) % 360f) / 60f
    val c = v * s
    val x = c * (1f - abs(hh % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when (hh.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(f: Float): Long = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
    return (0xFFL shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

fun backdropValueBand(dark: Boolean): ClosedFloatingPointRange<Float> =
    if (dark) 0.06f..0.18f else 0.84f..1f

fun tameBackdropColor(argb: Long, dark: Boolean): Long {
    val hsv = argbToHsv(argb)
    val s = hsv[1].coerceAtMost(if (dark) 0.42f else 0.30f)
    val band = backdropValueBand(dark)
    val v = hsv[2].coerceIn(band.start, band.endInclusive)
    return hsvToArgb(hsv[0], s, v)
}

