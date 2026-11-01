package com.offline.due.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.offline.due.data.CountdownEvent
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import kotlinx.coroutines.launch

val Gutter = 20.dp

val RowInset = 16.dp

val CardInset = 24.dp

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    GlassDivider(modifier)
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,

    hint: String? = null,
) {
    val g = LocalGlass.current
    Column(
        modifier.padding(start = RowInset, top = 24.dp, bottom = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.let { base ->
                base.copy(
                    fontSize = base.fontSize * (12f / 11.5f),
                    letterSpacing = 1.1.sp,
                )
            },
            color = g.inkMid,
            fontWeight = FontWeight.Medium,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun InsetLine(modifier: Modifier = Modifier, inset: Dp = RowInset) {
    Box(
        modifier
            .padding(horizontal = inset)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalGlass.current.hairline)
    )
}

@Composable
fun SettingRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    showChevron: Boolean = false,
    hint: String? = null,
) {
    val g = LocalGlass.current
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else g.ink
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.pressable(onClick, scale = 1f, specular = false)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = RowInset, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = g.inkMid,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "›",
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    color = g.inkSoft,
                )
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun rememberStretchPulse(key: Any?, durationMillis: Int = 380): State<Float> {
    val pulse = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
// 首次组合不抖：一进页面 thumb 就鼓一下会很吵
        if (first) {
            first = false
            return@LaunchedEffect
        }
        pulse.snapTo(1f)
        pulse.animateTo(0f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return pulse.asState()
}

@Composable
fun SegmentedChoice(
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val target = index.toFloat()
    val anim by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.press(),
        label = "segment",
    )
    val stretch by rememberStretchPulse(index, durationMillis = 420)

    val trackColor = g.ink.copy(alpha = if (g.isDark) 0.14f else 0.035f)
    val thumbBrush = remember(g.isDark, g.surfaceTop, g.surfaceBottom) {
        val topA = if (g.isDark) (g.surfaceTop.alpha * 1.125f).coerceAtMost(1f) else 0.95f
        val botA = if (g.isDark) (g.surfaceBottom.alpha * 1.125f).coerceAtMost(1f) else 0.72f
        Brush.verticalGradient(
            listOf(
                g.surfaceTop.copy(alpha = topA),
                g.surfaceBottom.copy(alpha = botA),
            )
        )
    }
    val thumbEdge = g.edgeLight

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.chip))
            .background(trackColor)
            .padding(3.dp),
    ) {
        val density = LocalDensity.current
        val gap = 3.dp
        val gapPx = with(density) { gap.toPx() }
        val totalPx = with(density) { maxWidth.toPx() }.let { if (it.isFinite()) it else 0f }
        val n = options.size.coerceAtLeast(1)
        val cellPx = ((totalPx - gapPx * (n - 1)) / n).coerceAtLeast(1f)
        val spanPx = cellPx * n + gapPx * (n - 1)
        val radiusPx = with(density) { 11.dp.toPx() }
        val strokePx = with(density) { 1.dp.toPx() }

        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val stretchPx = cellPx * 0.62f * stretch
                    val w = cellPx + stretchPx
                    val x = (anim * (cellPx + gapPx) - stretchPx / 2f)
                        .coerceIn(0f, (spanPx - w).coerceAtLeast(0f))
                    val corner = CornerRadius(radiusPx, radiusPx)
                    drawRoundRect(
                        brush = thumbBrush,
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = thumbEdge,
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = corner,
                        style = Stroke(strokePx),
                    )
                }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            options.forEachIndexed { i, option ->
                val on = i == index
                val labelColor by animateColorAsState(
                    targetValue = if (on) g.ink else g.inkMid,
                    animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
                    label = "segmentLabel",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.chip))
                        .pressable({ onPick(option) }, scale = 0.97f)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = labelColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun SquishSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    val target = if (on) 1f else 0f
    val anim by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.press(),
        label = "squish",
    )
    val trackColor by animateColorAsState(
        targetValue = if (on) {
            g.accent.copy(alpha = if (g.isDark) 0.85f else 0.92f)
        } else {
            g.ink.copy(alpha = if (g.isDark) 0.22f else 0.10f)
        },
        animationSpec = tween(Motion.Enter, easing = Motion.enterEasing),
        label = "squishTrack",
    )

    val trackW = 44.dp
    val trackH = 26.dp
    val inset = 3.dp
    val thumbSize = trackH - inset * 2
    val travel = trackW - inset * 2 - thumbSize
// 和分段选择共用同一个脉冲：拨动时 thumb 被推着走，于是被拉长、同时变窄
    val stretch by rememberStretchPulse(on, durationMillis = 340)

    Box(
        modifier = modifier
            .size(trackW, trackH)
            .clip(CircleShape)
            .background(trackColor)
            .pressable({ onChange(!on) }, scale = 0.94f, specular = false, role = Role.Switch)
            .semantics { stateDescription = if (on) "已开启" else "已关闭" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = inset)
                .size(thumbSize)
                .graphicsLayer {
// 两个值都在绘制阶段读，滑动过程中不触发重组
                    translationX = anim * travel.toPx()
                    val sx = 1f + 0.62f * stretch
                    scaleX = sx
                    scaleY = 1f / sx
                }
                .background(Color.White, CircleShape)
                .border(0.5.dp, g.stroke.copy(alpha = 0.45f), CircleShape)
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    val g = LocalGlass.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressable({ onChange(!on) }, scale = 1f, specular = false, role = Role.Switch)
            .semantics { stateDescription = if (on) "已开启" else "已关闭" }
            .padding(horizontal = RowInset, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = g.ink,
                modifier = Modifier.weight(1f),
            )
            SquishSwitch(on = on, onChange = onChange)
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
// 右边留出开关的宽度，说明文字才不会绕到开关底下
                modifier = Modifier.padding(top = 3.dp, end = 52.dp),
            )
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueText: String = "${(value * 100).toInt()}%",
) {
    val g = LocalGlass.current
    val v = value.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = g.ink,
        )
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .weight(1f)
// 触摸区做到 36dp 高，轨道只有 4dp——手指按不准一条 4dp 的线
                .height(36.dp)
                .pickXY { x, _ -> onChange(x) }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(v, 0f..1f)
                    stateDescription = valueText
                    setProgress { target ->
                        onChange(target.coerceIn(0f, 1f))
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                val cy = size.height / 2f
                val trackH = 4.dp.toPx()
                val r = CornerRadius(trackH / 2f, trackH / 2f)
                drawRoundRect(
                    color = g.ink.copy(alpha = if (g.isDark) 0.22f else 0.09f),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(size.width, trackH),
                    cornerRadius = r,
                )
                drawRoundRect(
                    color = g.accent.copy(alpha = 0.85f),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(size.width * v, trackH),
                    cornerRadius = r,
                )
                val cx = (size.width * v).coerceIn(0f, size.width)
                val thumbR = 8.dp.toPx()
                drawCircle(color = Color.White, radius = thumbR, center = Offset(cx, cy))
                drawCircle(
                    color = g.stroke.copy(alpha = 0.9f),
                    radius = thumbR,
                    center = Offset(cx, cy),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = g.inkMid,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun ColorDots(
    colors: List<Long>,
    selected: Long,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
    dotSize: Dp = 26.dp,
) {
    val g = LocalGlass.current
    val gap = if (dotSize >= 26.dp) 10.dp else 4.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (allowNone) {
            val on = selected == CountdownEvent.NO_COLOR
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .pressable({ onPick(CountdownEvent.NO_COLOR) }, scale = 0.86f)
                    .clip(CircleShape)
                    .border(
                        width = if (on) 2.dp else 1.dp,
                        color = if (on) g.edgeLight else g.stroke.copy(alpha = 0.7f),
                        shape = CircleShape,
                    )
// 斜杠画在空圈里：这是"无"的通用记号，比写一个"无"字省地方
                    .drawBehind {
                        drawLine(
                            color = g.inkMid,
                            start = Offset(size.width * 0.30f, size.height * 0.70f),
                            end = Offset(size.width * 0.70f, size.height * 0.30f),
                            strokeWidth = 1.4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
            )
        }
        colors.forEach { value ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .pressable({ onPick(value) }, scale = 0.86f)
                    .clip(CircleShape)
                    .then(
                        if (on) {
                            Modifier.border(2.dp, g.edgeLight, CircleShape)
                        } else {
                            Modifier.border(1.dp, g.stroke.copy(alpha = 0.5f), CircleShape)
                        }
                    )
                    .padding(if (on) 4.dp else 3.dp)
                    .background(Color(value), CircleShape)
            )
        }
    }
}

@Composable
fun ColorSlotRow(
    label: String,
    colors: List<Long>,
    selected: Long,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
) {
    val g = LocalGlass.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = g.inkSoft,
            modifier = Modifier.width(28.dp),
        )
        if (!allowNone) Spacer(Modifier.width(24.dp))
        ColorDots(
            colors = colors,
            selected = selected,
            onPick = onPick,
            allowNone = allowNone,
            dotSize = 20.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun EmptyState(text: String, hint: String) {
    val g = LocalGlass.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 88.dp, start = Gutter, end = Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = g.ink,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = g.inkMid,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun GlassDialog(
    onDismiss: () -> Unit,

    fullWidth: Boolean = false,
    content: @Composable (dismiss: (after: () -> Unit) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    var gone by remember { mutableStateOf(false) }

    val close: (() -> Unit) -> Unit = { after ->
// 连点两下、或者返回键和点选项同时来的时候，只能演一次。
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(Motion.Exit, easing = Motion.exitEasing))
                try {
                    after()
                } finally {
                    gone = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
// 进度条长满 = 中位移落定，用全局的 settle 档。
            animationSpec = Motion.settle(),
        )
    }

// `gone` 之后整块 `Dialog` 从组合里移除（见上面 close 的注释）。
    if (!gone) Dialog(
        onDismissRequest = { close(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = !fullWidth),
    ) {
        BackHandler(enabled = !closing) { close(onDismiss) }
        Box(
            modifier = Modifier.graphicsLayer {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                val s = 0.86f + 0.14f * p
                scaleX = s
                scaleY = s
// 向上抬 20dp：玻璃是浮起来的，落下来的方向该是向上。
                translationY = (1f - p) * 20.dp.toPx()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = if (p < 0.995f) {
                        val r = (1f - p) * 14f
                        BlurEffect(r, r, TileMode.Clamp)
                    } else {
                        null
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalFrosted provides true) {
                content(close)
            }
        }
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,

    subtitle: String? = null,
) {
    val g = LocalGlass.current
    GlassDialog(onDismiss = onDismiss) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = CardInset)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkSoft,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = g.inkMid,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        val isSelected = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.chip))
                                .pressable(
                                    {
                                        close {
                                            onPick(option)
                                            onDismiss()
                                        }
                                    },
                                    scale = 1f,
                                    specular = false,
                                )
                                .padding(vertical = 12.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(15.dp)
                                    .background(
                                        if (isSelected) g.accent else Color.Transparent,
                                        RoundedCornerShape(Radius.hair),
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) g.ink else g.inkMid,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NumberGridDialog(
    title: String,
    values: List<Int>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    columns: Int = 7,
) {
    val g = LocalGlass.current
    GlassDialog(onDismiss = onDismiss) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = CardInset)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkSoft,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = g.inkMid,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                values.chunked(columns).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { value ->
                            val isSelected = value == selected
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(Radius.chip))
                                    .then(
                                        if (isSelected) Modifier.background(g.inverse) else Modifier
                                    )
                                    .pressable(
                                        {
                                            close {
                                                onPick(value)
                                                onDismiss()
                                            }
                                        },
                                        scale = 0.94f,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = value.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) g.onInverse else g.ink,
                                )
                            }
                        }
// 最后一行可能不满，补空位，否则那几个格子会被拉宽。
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextAction(text = "取消", onClick = { close(onDismiss) })
                }
            }
        }
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    color: Color? = null,
    padding: Dp = 8.dp,
) {
    val g = LocalGlass.current
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .pressable(onClick, scale = 0.92f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
            color = color ?: if (emphasis) g.ink else g.inkMid,
            modifier = Modifier.padding(horizontal = padding),
        )
    }
}

@Composable
fun KeyBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
fun KeyCap(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    color: Color? = null,
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(Radius.chip)
    GlassPanel(
        modifier = modifier
            .clip(shape)
            .pressable(onClick, scale = 0.93f),
        shape = shape,
        weight = 0.42f,
        elevation = 4.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = RowInset, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
                color = color ?: if (emphasis) g.ink else g.inkMid,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SlidingSegments(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 168.dp,
    height: Dp = 38.dp,
) {
    if (options.isEmpty()) return
    val g = LocalGlass.current
    val picked = selected.coerceIn(0, options.lastIndex)
    GlassInset(modifier = modifier.width(width), shape = RoundedCornerShape(Radius.panel)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height),
        ) {
            val seg = maxWidth / options.size.toFloat()
            val segPx = with(LocalDensity.current) { seg.toPx() }

            val x = remember(segPx) { Animatable(segPx * picked) }

            LaunchedEffect(segPx, picked) {
                x.animateTo(segPx * picked, Motion.glide())
            }

            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .width(seg)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .graphicsLayer { translationX = x.value },
                ) {
                    GlassPanel(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(Radius.chip),
                        weight = 0.85f,
                        elevation = 2.dp,
                    ) {}
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { i, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = height)
                            .pressable({ onSelect(i) }, scale = 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (i == picked) FontWeight.Bold else FontWeight.Normal,
// 一格就那么宽，换行会把两段字摞起来、顶出凹槽。
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (i == picked) g.ink else if (g.isDark) g.inkMid else g.inkSoft,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChips(
    options: List<Pair<String, Int>>,
    selected: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,

    vertical: Boolean = false,
) {
    val g = LocalGlass.current
    val total = options.sumOf { it.second }
    if (vertical) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilterChip(label = "全部", count = total, on = selected == null) { onPick(null) }
            options.forEach { (category, count) ->
                FilterChip(label = category, count = count, on = selected == category) {
                    onPick(category)
                }
            }
        }
        return
    }

    val picked = if (selected == null) {
        0
    } else {
        options.indexOfFirst { it.first == selected } + 1
    }

    val scroll = rememberScrollState()

    val lefts = remember { mutableStateMapOf<Int, Float>() }
    val widths = remember { mutableStateMapOf<Int, Float>() }

    val toLeft = lefts[picked]
    val toWidth = widths[picked]

    val record: (Int, LayoutCoordinates) -> Unit = remember(lefts, widths) {
        { index, coords ->
            val b = coords.boundsInParent()
            if (lefts[index] != b.left) lefts[index] = b.left
            if (widths[index] != b.width) widths[index] = b.width
        }
    }

    val slideX = remember { Animatable(0f) }
    val slideW = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }

// 可视区宽度（px）。用来判断选中项是不是跑到屏幕外去了。
    var viewport by remember { mutableStateOf(0) }

    LaunchedEffect(picked, viewport) {
        if (viewport <= 0) return@LaunchedEffect
        val l = lefts[picked] ?: return@LaunchedEffect
        val w = widths[picked] ?: return@LaunchedEffect
        val fullyVisible = l >= scroll.value && l + w <= scroll.value + viewport
        if (!fullyVisible) {
// 居中，但两头顶住时贴边——滚到头的动画如果还在往中间凑，会显得没落定。
            val target = (l - (viewport - w) / 2f).toInt().coerceIn(0, scroll.maxValue)
            scroll.animateScrollTo(target)
        }
    }

    val slide = Motion.glide<Float>()

    LaunchedEffect(toLeft, toWidth) {
        if (toLeft == null || toWidth == null) return@LaunchedEffect
        if (!placed) {
            slideX.snapTo(toLeft)
            slideW.snapTo(toWidth)
            placed = true
        } else {
            launch { slideX.animateTo(toLeft, slide) }
            launch { slideW.animateTo(toWidth, slide) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scroll),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                label = "全部",
                count = total,
                on = picked == 0,
// 横排的下划线由整条共享的那一条负责，这里不再各画各的。
                underline = false,
                modifier = Modifier.onGloballyPositioned { record(0, it) },
            ) { onPick(null) }
            options.forEachIndexed { i, (category, count) ->
                val index = i + 1
                FilterChip(
                    label = category,
                    count = count,
                    on = picked == index,
                    underline = false,
                    modifier = Modifier.onGloballyPositioned { record(index, it) },
                ) { onPick(category) }
            }
// 右侧留一点空，最后一个分类才不会顶着屏幕边
            Spacer(Modifier.width(6.dp))
        }

        if (placed) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val thickness = 2.dp.toPx()
                val r = thickness / 2f
                drawRoundRect(
                    color = g.accent,
                    topLeft = Offset(slideX.value, size.height - thickness),
                    size = Size(slideW.value, thickness),
                    cornerRadius = CornerRadius(r, r),
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int,
    on: Boolean,
    modifier: Modifier = Modifier,

    underline: Boolean = true,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
// 选中态的变色也跟着走一小段，不然指示条滑过去了、字还是"啪"地变深。
    val labelColor by animateColorAsState(
        targetValue = if (on) g.ink else g.inkMid,
        animationSpec = tween(Motion.Press),
        label = "chipLabel",
    )
    val countColor by animateColorAsState(
        targetValue = if (on) g.accent else g.inkSoft,
        animationSpec = tween(Motion.Press),
        label = "chipCount",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.dot))
            .pressable(onClick, scale = 0.94f)
            .drawBehind {
                if (!underline || !on) return@drawBehind
                val thickness = 2.dp.toPx()
                drawRect(
                    color = g.accent,
                    topLeft = Offset(0f, size.height - thickness),
                    size = Size(size.width, thickness),
                )
            }
            .padding(top = 4.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Transparent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = labelColor,
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = countColor,
        )
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    limit: Int = 12,
) {
    val g = LocalGlass.current
    var text by remember(initial) { mutableStateOf(initial) }
    GlassDialog(onDismiss = onDismiss) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(horizontal = CardInset, vertical = 20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkSoft,
                )
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= limit) text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = g.ink),
                    cursorBrush = SolidColor(g.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = g.inkSoft,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(g.hairline)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${text.length}/$limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = g.inkSoft,
                    )
                    Spacer(Modifier.weight(1f))
                    TextAction(text = "取消", onClick = { close(onDismiss) })
                    TextAction(
                        text = confirmText,
                        onClick = {
// 和 DatePickDialog 同一个坑：`close` 不替调用方关外层。
                            close {
                                onConfirm(text.trim())
                                onDismiss()
                            }
                        },
                        emphasis = true,
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
) {
    val g = LocalGlass.current
    GlassDialog(onDismiss = onDismiss) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(horizontal = CardInset, vertical = 20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = g.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = g.inkMid,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(g.hairline)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    TextAction(text = "取消", onClick = { close(onDismiss) })
                    TextAction(
                        text = confirmText,
                        onClick = { close(onConfirm) },
                        emphasis = true,
                        color = if (destructive) MaterialTheme.colorScheme.error else null,
                    )
                }
            }
        }
    }
}

@Composable
fun Modifier.swipePage(
    onPrev: () -> Unit,
    onNext: () -> Unit,
    enabled: Boolean = true,
): Modifier {
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return this.pointerInput(threshold, enabled) {
        if (!enabled) return@pointerInput
        var acc = 0f
        detectHorizontalDragGestures(
            onDragStart = { acc = 0f },
            onDragCancel = { acc = 0f },
            onDragEnd = {
                if (acc <= -threshold) next() else if (acc >= threshold) prev()
            },
        ) { change, dragAmount ->
            acc += dragAmount
            change.consume()
        }
    }
}

