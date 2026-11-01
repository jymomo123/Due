package com.offline.due.ui.components

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val FRAME_INTERVAL_NANOS = 33_000_000L

@Composable
fun Aurora(
    colors: List<Color>,
    modifier: Modifier = Modifier,

    amplitude: Float = 1f,

    heightFraction: Float = 0.5f,

    speed: Float = 1f,

    scale: Float = 1f,

    blend: Float = 0.55f,

    layers: Int = 3,
    opacity: Float = 1f,
) {
    if (colors.isEmpty() || layers <= 0) return

// 一个周期 70 秒：慢到看不出在循环，但肉眼能感觉到"在呼吸"
    val periodMillis = (70_000f / speed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(8_000)

    val phase by produceState(0f, periodMillis) {
        val periodNanos = periodMillis * 1_000_000L
        var lastEmit = 0L
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame ->
                if (frame - lastEmit >= FRAME_INTERVAL_NANOS) {
                    lastEmit = frame
                    value = ((frame - start) % periodNanos) / periodNanos.toFloat()
                }
            }
        }
    }

// 进场淡入：对应原版的 intro
    var intro by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(1600, easing = FastOutSlowInEasing),
        ) { value, _ -> intro = value }
    }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val curtain = h * heightFraction.coerceIn(0.1f, 1f)
        val step = w / 28f
        val baseAmp = amplitude * 26.dp.toPx()

        for (i in 0 until layers) {
// 越靠后越淡越矮，形成纵深
            val depth = 1f - i * 0.26f
            val color = colors[i % colors.size]
            val alpha = opacity * intro * depth * 0.50f
            if (alpha <= 0.001f) continue

            val p = phase * 2f * PI.toFloat() + i * 1.7f
            val amp = baseAmp * depth
            val freq = 1.35f / scale.coerceAtLeast(0.2f) * (1f + i * 0.32f)
            val bottom = curtain * depth

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                var x = w
                while (x >= -step) {
                    val u = x / w
                    val y = bottom +
                        sin(u * 2f * PI.toFloat() * freq + p) * amp +
                        sin(u * 2f * PI.toFloat() * freq * 2.4f - p * 0.7f) * amp * 0.38f
                    lineTo(x, y)
                    x -= step
                }
                close()
            }

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    0f to color.copy(alpha = alpha),
                    blend.coerceIn(0.05f, 0.95f) to color.copy(alpha = alpha * 0.42f),
                    1f to color.copy(alpha = 0f),
                    startY = 0f,
                    endY = bottom,
                ),
            )
        }
    }
}

@Composable
fun Modifier.grain(alpha: Float = 0.038f, overlay: Boolean = true): Modifier {
    if (alpha <= 0f) return this
    val brush = rememberGrainBrush()
    return if (overlay) {
        this.drawWithContent {
            drawContent()
            drawRect(brush = brush, alpha = alpha)
        }
    } else {
        this.drawWithCache {
            onDrawBehind { drawRect(brush = brush, alpha = alpha) }
        }
    }
}

@Composable
private fun rememberGrainBrush(): ShaderBrush = remember {
    val size = 96
// 固定种子：每次启动的颗粒图案一致，不会闪
    val rnd = Random(0x5EED)
    val pixels = IntArray(size * size) { i ->
        val a = rnd.nextInt(34)
// 一半偏白一半偏黑，混起来才是中性颗粒，而不是"撒了一层白粉"
        if (i and 1 == 0) (a shl 24) or 0x00FFFFFF else (a shl 24)
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
    ShaderBrush(
        ImageShader(
            bitmap.asImageBitmap(),
            TileMode.Repeated,
            TileMode.Repeated,
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,

    scale: Float = 0.985f,

    specular: Boolean = true,

    veil: Color? = null,

    onLongClick: (() -> Unit)? = null,

    longClickFeedback: Boolean = true,

    role: Role? = Role.Button,
): Modifier {
    val g = LocalGlass.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val veilColor = veil ?: if (g.isDark) Color.White else g.ink
    val veilMax = if (g.isDark) 0.10f else 0.075f
    val specularMax = if (g.isDark) 0.20f else 0.11f

    val pressPoint = remember { mutableStateOf(Offset.Unspecified) }
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = if (pressed) {
            tween(Motion.Press)
        } else {
            Motion.settle()
        },
        label = "press",
    )
    val glow = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) Motion.Press else Motion.Enter),
        label = "specular",
    )

    var m = this
    m = m.pointerInput(Unit) {
// Initial 阶段只读不消费，不会抢掉 clickable 的手势
        awaitPointerEventScope {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial)
                    .changes.firstOrNull() ?: continue
                if (change.pressed) pressPoint.value = change.position
            }
        }
    }

    val spark = LocalSpark.current
    val accent = LocalGlass.current.accent
    var rootAt by remember { mutableStateOf(Offset.Zero) }
    if (spark != null) {
        m = m.onGloballyPositioned { rootAt = it.positionInRoot() }
    }

    val fire: () -> Unit = {
        val local = pressPoint.value
        if (spark != null && local != Offset.Unspecified) {
            spark.burst(rootAt + local, accent)
        }
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onClick()
    }

    return m
        .graphicsLayer {
// 在绘制阶段读，按下时不触发重组
            scaleX = pressScale.value
            scaleY = pressScale.value
        }
        .drawWithContent {
            drawContent()

            val a = glow.value
            if (a <= 0.002f) return@drawWithContent

            drawRect(veilColor.copy(alpha = a * veilMax))

// 镜面高光：按下处一团光，位置跟着手指走
            val p = pressPoint.value
            if (specular && p != Offset.Unspecified) {
// 半径跟着元素大小走：小圆点是整颗亮，整行是按下点周围一圈
                val r = size.minDimension * 0.92f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = specularMax * a),
                        0.55f to Color.White.copy(alpha = specularMax * 0.35f * a),
                        1f to Color.Transparent,
                        center = p,
                        radius = r,
                    ),
                    radius = r,
                    center = p,
                )
            }
        }
        .then(
            if (onLongClick == null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = role,
                    onClick = fire,
                )
            } else {
                Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = role,
                    onLongClick = {
                        if (longClickFeedback) {
                            if (spark != null && pressPoint.value != Offset.Unspecified) {
                                spark.burst(rootAt + pressPoint.value, accent)
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        onLongClick()
                    },
                    onClick = fire,
                )
            }
        )
}

@Composable
fun rememberGlare(key: Any?, durationMillis: Int = 650): State<Float> {
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
    }
    return progress.asState()
}

fun DrawScope.drawGlare(progress: Float, color: Color, alpha: Float) {
    if (progress <= 0f || progress >= 1f) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val diag = sqrt(w * w + h * h)
    val band = diag * 0.15f // 光带半宽：整条占对角线的 30%
    val dirX = w / diag
    val dirY = h / diag
// 中心从"完全在左上之外"走到"完全在右下之外"
    val c = -band + (diag + band * 2f) * progress
    val cx = dirX * c
    val cy = dirY * c

    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to color.copy(alpha = alpha),
            1f to Color.Transparent,
            start = Offset(cx - dirX * band, cy - dirY * band),
            end = Offset(cx + dirX * band, cy + dirY * band),
        )
    )
}

