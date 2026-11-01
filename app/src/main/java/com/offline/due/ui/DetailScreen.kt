package com.offline.due.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventMode
import com.offline.due.data.RepeatRule
import com.offline.due.data.RowDensity
import com.offline.due.domain.CardNumber
import com.offline.due.domain.CardNumberMode
import com.offline.due.domain.CountdownInfo
import com.offline.due.domain.CountdownMath
import com.offline.due.ui.components.EventRow
import com.offline.due.ui.components.GlassInset
import com.offline.due.ui.components.PredictiveBack
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.InsetLine
import com.offline.due.ui.components.KeyBar
import com.offline.due.ui.components.KeyCap
import com.offline.due.ui.components.RowInset
import com.offline.due.ui.components.RollingText
import com.offline.due.ui.components.SectionLabel
import com.offline.due.ui.components.SettingRow
import com.offline.due.ui.components.cardScrimBrush
import com.offline.due.ui.components.pressable
import com.offline.due.ui.components.rememberCardPhoto
import com.offline.due.ui.theme.ColorSlot
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Palette
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import com.offline.due.ui.theme.displayNumberHeight
import com.offline.due.ui.theme.displaySp
import com.offline.due.ui.theme.rememberWideLayout
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Composable
fun DetailScreen(
    event: CountdownEvent,
    onBack: () -> Unit,
    onEdit: () -> Unit,

    onOpenCard: () -> Unit,

    onCardPositioned: (card: Rect, title: Rect, number: Rect, date: Rect) -> Unit =
        { _, _, _, _ -> },

    origin: Rect? = null,

    backEnabled: Boolean = true,

    onDragProgress: (Float) -> Unit = {},

    onDragEnd: () -> Unit = {},

    onCover: (Float) -> Unit = {},

    listCardStyle: Boolean = true,

    rowDensity: RowDensity = RowDensity.STANDARD,

    onCardReady: () -> Unit = {},
) {
    val today = rememberToday()
    val nowState = rememberNowState(event.showSeconds)
    val info = remember(event, today) { CountdownMath.occurrence(event, today) }
    val accent = Color(event.color)
    val locked = event.capsule && info.isFuture
    val wide = rememberWideLayout()

    val grow = remember(event.id) { Animatable(if (origin == null) 1f else 0f) }
    var cardRect by remember(event.id) { mutableStateOf(Rect.Zero) }

    val settled = remember(event.id) { mutableStateOf(origin == null) }

    LaunchedEffect(event.id, origin, cardRect) {
        if (cardRect == Rect.Zero) return@LaunchedEffect
        if (grow.value >= 1f) {
            settled.value = true
            return@LaunchedEffect
        }
        if (origin == null) {
            grow.snapTo(1f)
            settled.value = true
            return@LaunchedEffect
        }
        grow.animateTo(1f, Motion.glide())
        settled.value = true
    }

    var exiting by remember(event.id) { mutableStateOf(false) }
    val exitGen = remember(event.id) { intArrayOf(0) }
    val scope = rememberCoroutineScope()

    var drag by remember(event.id) { mutableFloatStateOf(0f) }

    var dragging by remember(event.id) { mutableStateOf(false) }

    val leaving = exiting || dragging || drag > 0f

    val cardProgress: () -> Float = { grow.value * (1f - drag) }

    LaunchedEffect(dragging, exiting) {
        if (dragging || exiting) return@LaunchedEffect
        if (drag > 0f) {
            animate(
                initialValue = drag,
                targetValue = 0f,
                animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
            ) { v, _ ->
                drag = v
                onDragProgress(v)
// 取消 = 卡片又长回满屏，底下那一层要跟着退回去。
                onCover(grow.value * (1f - v))
            }
        }
        onDragEnd()
    }

    val openCard = { if (!leaving) onOpenCard() }

    val requestExit = {
        if (!exiting) {
            exiting = true
            val o = origin
            if (o == null) {
                onDragEnd()
                onBack()
            } else {
                scope.launch {
                    if (drag > 0f) {
                        grow.snapTo(grow.value * (1f - drag))
                        drag = 0f
                    }
                    val gen = ++exitGen[0]
                    var landed = false
                    try {
                        grow.animateTo(
                            targetValue = 0f,
                            animationSpec = Motion.bounce(),
                        ) {
                            onCover(value)
                        }
                        onDragEnd()
                        onBack()
                        landed = true
                    } finally {
                        if (!landed && gen == exitGen[0]) {
                            exiting = false
                            grow.snapTo(1f)
                            drag = 0f
                            dragging = false
                        }
                    }
                }
            }
        }
    }

    PredictiveBack(
        enabled = backEnabled,
        onProgress = { p ->
            if (!dragging) dragging = true
            drag = p
            onDragProgress(p)
            onCover(grow.value * (1f - p))
        },
        onCancel = { dragging = false },
        onBack = requestExit,
    )

    CompositionLocalProvider(LocalNow provides nowState) { IceBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val chromeAlpha: () -> Float = {
                val p = cardProgress()
                if (leaving) exitChromeAlpha(p) else cardChromeAlpha(p)
            }
            val fadeWithCard =
                if (origin != null && (!settled.value || exiting || dragging)) {
                    Modifier.graphicsLayer { alpha = chromeAlpha() }
                } else {
                    Modifier
                }

            KeyBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(fadeWithCard)
                    .padding(
                        start = Gutter,
                        end = Gutter,
// 横屏只剩 360dp 高，顶栏每多 1dp 都是卡片少 1dp
                        top = if (wide) 6.dp else 10.dp,
                        bottom = if (wide) 8.dp else 12.dp,
                    ),
            ) {
                KeyCap(text = "返回", onClick = requestExit)
                Spacer(Modifier.weight(1f))
                KeyCap(text = "编辑", onClick = onEdit, emphasis = true)
            }

            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = Gutter, end = 8.dp, bottom = Gutter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HeroCard(
                            event = event,
                            info = info,
                            accent = accent,
                            numberHeight = displayNumberHeight(0.26f),
                            grow = cardProgress,
                            origin = origin,
                            target = cardRect,
                            leaving = leaving,
                            listCardStyle = listCardStyle,
                            rowDensity = rowDensity,
                            onMeasured = { r ->
                                if (cardRect == Rect.Zero) {
                                    cardRect = r
                                    onCardReady()
                                }
                            },
                            onOpenCard = openCard,
                            onCardPositioned = onCardPositioned,
                            modifier = Modifier.widthIn(max = 460.dp),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 8.dp, end = Gutter),
                    ) {
                        Column(fadeWithCard) {
                            InfoGroups(event = event, info = info, locked = locked)
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .contentWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Gutter),
                ) {
                    HeroCard(
                        event = event,
                        info = info,
                        accent = accent,
                        numberHeight = displayNumberHeight(0.17f),
                        grow = cardProgress,
                        origin = origin,
                        target = cardRect,
                        leaving = leaving,
                        listCardStyle = listCardStyle,
                        rowDensity = rowDensity,
                        onMeasured = { r ->
                            if (cardRect == Rect.Zero) {
                                cardRect = r
                                onCardReady()
                            }
                        },
                        onOpenCard = openCard,
                        onCardPositioned = onCardPositioned,
                    )

                    Column(fadeWithCard) {
                        InfoGroups(event = event, info = info, locked = locked)
                    }

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    } }
}

internal class RowMorph(

    val progress: () -> Float,

    val shell: FloatArray,

    val origin: Rect,
    val target: Rect,

    val rowLeft: Float,
    val rowTop: Float,
    val rowWidth: Float,
    val rowHeight: Float,

    val rowPadH: Float,
    val rowPadV: Float,

    val rowTitlePx: Float,
    val rowNumberPx: Float,
    val rowSubtitlePx: Float,

    val rowBarGap: Float,

    val destTitle: Rect? = null,
    val destNumber: Rect? = null,
    val destSubtitle: Rect? = null,
)

internal fun shellOf(p: Float, origin: Rect, target: Rect, out: FloatArray) {
    out[0] = origin.left + (target.left - origin.left) * p
    out[1] = origin.top + (target.top - origin.top) * p
    out[2] = if (target.width > 0f) {
        (origin.width + (target.width - origin.width) * p) / target.width
    } else 1f
    out[3] = if (target.height > 0f) {
        (origin.height + (target.height - origin.height) * p) / target.height
    } else 1f
}

private fun GraphicsLayerScope.noMorph() {
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = 0f
    translationY = 0f
    scaleX = 1f
    scaleY = 1f
}

private fun GraphicsLayerScope.morphTo(
    m: RowMorph,
    self: Rect,
    targetLeft: Float,
    targetTop: Float,
    k: Float,
) {
    if (self.width <= 0f || m.target.width <= 0f || m.target.height <= 0f) {
        noMorph()
        return
    }
    val p = m.progress()
    shellOf(p, m.origin, m.target, m.shell)
    val sx = m.shell[2]
    val sy = m.shell[3]
    if (sx <= 0f || sy <= 0f) {
        noMorph()
        return
    }
// 目标位置：从"卡片里的位置"插值到"行里的位置"。
    val t = 1f - p
    val destLeft = self.left + (targetLeft - self.left) * t
    val destTop = self.top + (targetTop - self.top) * t
    val kw = 1f + (k - 1f) * t
// 内层还没动的时候，这个元素会落在哪儿（外层变换之后）。
    val onLeft = m.shell[0] + (self.left - m.target.left) * sx
    val onTop = m.shell[1] + (self.top - m.target.top) * sy
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = (destLeft - onLeft) / sx
    translationY = (destTop - onTop) / sy
    scaleX = kw / sx
    scaleY = kw / sy
}

@Composable
private fun HeroCard(
    event: CountdownEvent,
    info: CountdownInfo,
    accent: Color,
    numberHeight: Dp,

    grow: () -> Float,

    origin: Rect?,

    target: Rect,
    onMeasured: (Rect) -> Unit,

    onOpenCard: () -> Unit,

    onCardPositioned: (card: Rect, title: Rect, number: Rect, date: Rect) -> Unit,

    leaving: Boolean = false,

    listCardStyle: Boolean = true,
    rowDensity: RowDensity = RowDensity.STANDARD,
    modifier: Modifier = Modifier,
) {
    val measured = target.width > 0f && target.height > 0f
    val liveRect = remember { arrayOf(Rect.Zero) }

    val partRects = remember { arrayOf(Rect.Zero, Rect.Zero, Rect.Zero) }
    val rowWidth = with(LocalDensity.current) { (origin?.width ?: 0f).toDp() }
    val seen = remember(event.id) { floatArrayOf(0f) }
    val rowFits = origin == null || target.width <= 0f ||
        origin.width <= target.width * 1.10f
    val rowNumSp = displaySp(rowDensity.numberSp.sp)
    val shell = remember(event.id) { FloatArray(4) }
    val morph: RowMorph? = if (origin != null && leaving && measured && rowFits) {
        val o = origin
        val d = LocalDensity.current
        val typo = MaterialTheme.typography
        RowMorph(
            progress = grow,
            shell = shell,
            origin = o,
            target = target,
            rowLeft = o.left,
            rowTop = o.top,
            rowWidth = o.width,
            rowHeight = o.height,
            rowPadH = with(d) { RowInset.toPx() },
            rowPadV = with(d) { rowDensity.verticalPaddingDp.dp.toPx() },
            rowTitlePx = with(d) {
                (typo.headlineSmall.fontSize * rowDensity.titleScale).toPx()
            },
            rowNumberPx = with(d) { rowNumSp.toPx() },
            rowSubtitlePx = with(d) { typo.bodySmall.fontSize.toPx() },
// `EventRow` 里竖线占 3dp，后面还有 13dp 间距。
            rowBarGap = with(d) { (3.dp + 13.dp).toPx() },
        )
    } else null
    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            liveRect[0] = r
            onMeasured(r)
        },
    ) {
        EventCard(
            event = event,
            info = info,
            accent = accent,
            numberHeight = numberHeight,
            onClick = if (leaving) {
                null
            } else {
                {
                    onCardPositioned(
                        liveRect[0],
                        partRects[0],
                        partRects[1],
                        partRects[2],
                    )
                    onOpenCard()
                }
            },
            contentAlpha = {
                val c = cardContentAlpha(grow())
                if (c > seen[0]) seen[0] = c
                seen[0]
            },
            chromeAlpha = {
                if (leaving) ((grow() - 0.18f) / 0.22f).coerceIn(0f, 1f) else 1f
            },
// 标题 / 大数字 / 日期搬去行里。见 `RowMorph`。
            morph = morph,
// 三样元素平时（未变形）的位置，收着等"点开全屏卡片页"那一刻一起报出去。
            onParts = { i, r -> partRects[i] = r },
            modifier = Modifier.graphicsLayer {
                if (origin == null) {
// 没有起点：不参与这段动画，也不许被"未量到"挡住。
                    noMorph()
                    alpha = 1f
                } else if (!measured) {
                    noMorph()
                    alpha = 0f
                } else {
                    val p = grow()
                    shellOf(p, origin, target, shell)
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = shell[0] - target.left
                    translationY = shell[1] - target.top
                    scaleX = shell[2]
                    scaleY = shell[3]
                    alpha = if (leaving) pageExitAlpha(p) else 1f
                }
            },
        )

        if (origin != null && leaving && measured && rowFits) {
            val o = origin
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .requiredWidth(rowWidth)
                        .graphicsLayer {
                            val p = grow()
                            shellOf(p, o, target, shell)
                            val l = shell[0]
                            val t = shell[1]
                            val w = shell[2] * target.width
                            val h = shell[3] * target.height
                            val s = w / o.width
                            val k = s * rowSettleScale(p)
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = l - target.left
                            translationY = (t + (h - o.height * k) * 0.5f) - target.top
                            scaleX = k
                            scaleY = k
                            alpha = rowBodyAlpha(p)
                        },
                ) {
                    EventRow(
                        event = event,
                        info = info,
// 飞在半路上的那一行不该被点：它盖着的是一块空位。
                        onClick = {},
                        modifier = Modifier.clearAndSetSemantics { },
                        showCardLook = listCardStyle,
                        rowDensity = rowDensity,
                    )
                }
            }
        }
    }
}

private const val EXIT_FADE_FROM = 0.67f

private const val EXIT_FADE_TO = 0.06f

private const val EXIT_CHROME_TO = 0.70f

private fun exitChromeAlpha(p: Float): Float =
    ((p - EXIT_CHROME_TO) / (1f - EXIT_CHROME_TO)).coerceIn(0f, 1f)

private fun pageExitAlpha(p: Float): Float {
    if (p >= EXIT_FADE_FROM) return 1f
    if (p <= EXIT_FADE_TO) return 0f
    val t = (EXIT_FADE_FROM - p) / (EXIT_FADE_FROM - EXIT_FADE_TO)
    return 1f - t * t * t
}

private fun rowBodyAlpha(p: Float): Float =
    ((0.10f - p) / 0.08f).coerceIn(0f, 1f)

private fun rowSettleScale(p: Float): Float =
    1f + 0.12f * (p / 0.30f).coerceIn(-0.5f, 1f)

private fun cardContentAlpha(grow: Float): Float =
    ((grow - 0.45f) / 0.55f).coerceIn(0f, 1f)

private fun cardChromeAlpha(grow: Float): Float =
    ((grow - 0.62f) / 0.38f).coerceIn(0f, 1f)

@Composable
private fun ColumnScope.InfoGroups(
    event: CountdownEvent,
    info: CountdownInfo,
    locked: Boolean,
) {
    val g = LocalGlass.current

// ── 信息 ────────────────────────────────────────
    SectionLabel(text = "信息")
    GlassInset(modifier = Modifier.fillMaxWidth()) {
        SettingRow(label = "分类", value = event.category)
        InsetLine()
        SettingRow(label = "模式", value = event.mode.label)
        InsetLine()
        SettingRow(
            label = if (event.mode == EventMode.ELAPSED) "起始日" else "目标日",
            value = dateText(event.anchor),
        )
        if (event.mode == EventMode.ELAPSED) {
            InsetLine()
            SettingRow(
                label = "结束日",
                value = event.endDate?.let { dateText(it) } ?: "不限",
            )
        }
        if (event.mode == EventMode.COUNTDOWN) {
            InsetLine()
            SettingRow(label = "重复", value = event.repeat.label)
        }
        if (event.repeat != RepeatRule.NONE && event.mode == EventMode.COUNTDOWN) {
            InsetLine()
// 重复事件最容易被问"下一次是哪天"，所以单列一行
            SettingRow(label = "下一次", value = dateText(info.target))
        }
    }

    SectionLabel(text = "显示")
    GlassInset(modifier = Modifier.fillMaxWidth()) {
        SettingRow(label = "含起始日", value = onOff(event.includeStartDay))
        InsetLine()
        SettingRow(label = "显示秒", value = onOff(event.showSeconds))
        InsetLine()
        SettingRow(label = "置顶", value = onOff(event.pinned))
        InsetLine()
        ReadonlyColors(event = event)
    }

// ── 提醒 ────────────────────────────────────────
    SectionLabel(text = "提醒")
    GlassInset(modifier = Modifier.fillMaxWidth()) {
        SettingRow(label = "提醒", value = onOff(event.remind))
        if (event.remind) {
            InsetLine()
            SettingRow(
                label = "提醒时间",
                value = timeText(event.remindHour, event.remindMinute),
            )
            InsetLine()
            SettingRow(label = "频率", value = event.remindRule.effective(event.mode).label)
        }
    }

    SectionLabel(text = "时间胶囊")
    GlassInset(modifier = Modifier.fillMaxWidth()) {
        SettingRow(label = "开启", value = onOff(event.capsule))
        if (event.capsule) {
            InsetLine()
            Text(
                text = if (locked) {
                    "会在 ${info.target.monthValue} 月 ${info.target.dayOfMonth} 日当天展开。"
                } else {
                    event.capsuleText.ifBlank { "（没有留下内容）" }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (locked) g.inkMid else g.ink,
                modifier = Modifier.padding(horizontal = RowInset, vertical = 15.dp),
            )
        }
    }

// ── 备注 ────────────────────────────────────────
    if (event.note.isNotBlank() && !locked) {
        SectionLabel(text = "备注")
        GlassInset(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = event.note,
                style = MaterialTheme.typography.bodyMedium,
                color = g.ink,
                modifier = Modifier.padding(horizontal = RowInset, vertical = 15.dp),
            )
        }
    }

    Spacer(Modifier.height(22.dp))
    Text(
        text = "记录于 ${dateText(createdDate(event.createdAt))}",
        style = MaterialTheme.typography.labelSmall,
        color = g.inkSoft.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    )
}

@Composable
private fun ReadonlyColors(event: CountdownEvent) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "颜色",
            style = MaterialTheme.typography.titleMedium,
            color = g.ink,
            modifier = Modifier.weight(1f),
        )
        ColorSlot.entries.forEachIndexed { index, slot ->
            if (index > 0) Spacer(Modifier.width(12.dp))
            val value = slot.read(event)
            Text(
                text = slot.label,
                style = MaterialTheme.typography.labelSmall,
                color = g.inkSoft,
            )
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .background(
                        if (value == CountdownEvent.NO_COLOR) Color.Transparent else Color(value),
                        CircleShape,
                    )
                    .border(
                        1.dp,
                        if (value == CountdownEvent.NO_COLOR) g.stroke.copy(alpha = 0.9f)
                        else Color.Black.copy(alpha = 0.14f),
                        CircleShape,
                    )
            )
        }
    }
}

private const val UNIT_RATIO = 0.20f

private const val LEAD_RATIO = 0.26f

private val SEGMENT_GAP = 8.dp

private const val CLOCK_ROW_RATIO = 0.26f

private val CLOCK_SP_RANGE = 18f..64f

private const val GLYPH_BOX = 1.15f

@Composable
private fun rememberFittedNumberSp(

    segments: List<Pair<String, Float>>,
    maxWidth: Dp,
    maxHeight: Dp,
    style: TextStyle,
): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(segments, maxWidth, maxHeight, style, density) {
        val probe = style.copy(
            fontSize = 100.sp,
            lineHeight = TextUnit.Unspecified,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum",
        )

        fun width100(s: String): Float =
            if (s.isEmpty()) 0f else measurer.measure(s, probe).size.width.toFloat()

        val gapsPx = with(density) {
            (SEGMENT_GAP * (segments.size - 1).coerceAtLeast(0)).toPx()
        }

// 各段按自己的比例折算到"100sp 基准"上，才能和主数字放在同一个分母里
        var denom = 0f
        for ((text, ratio) in segments) denom += width100(text) * ratio

        val byWidth = if (denom <= 0.5f) {
            0f
        } else {
            (with(density) { maxWidth.toPx() } - gapsPx) * 100f / denom
        }

        val byHeight = with(density) {
            (maxHeight.toPx() / GLYPH_BOX) / 1.sp.toPx()
        }

        min(byWidth, byHeight).coerceIn(26f, 300f).sp
    }
}

private fun numberSegments(num: CardNumber): List<Pair<String, Float>> = buildList {
    if (num.lead.isNotEmpty()) add(num.lead to LEAD_RATIO)
    num.parts.forEach { part ->
        add(part.value to 1f)
        if (part.unit.isNotEmpty()) add(part.unit to UNIT_RATIO)
    }
}

@Composable
internal fun EventCard(
    event: CountdownEvent,
    info: CountdownInfo,
    accent: Color,

    modifier: Modifier = Modifier,

    numberHeight: Dp = 120.dp,

    fillHeight: Boolean = false,
    onClick: (() -> Unit)? = null,

    numberMode: CardNumberMode = CardNumberMode.DAY,

    onNumberTap: (() -> Unit)? = null,

    onNumberLongPress: (() -> Unit)? = null,

    secondsUnderNumber: Boolean = false,

    showSeconds: Boolean = event.showSeconds,

    contentAlpha: () -> Float = { 1f },

    morph: RowMorph? = null,

    chromeAlpha: () -> Float = { 1f },

    onParts: ((index: Int, rect: Rect) -> Unit)? = null,
) {
    val g = LocalGlass.current
    val numColor = Palette.number(event, g)
    val shape = RoundedCornerShape(Radius.card)

    val density = LocalDensity.current
    val maxPx = with(density) { 460.dp.roundToPx() }
    val photo = rememberCardPhoto(event.cardImage, maxPx)
    val hasPhoto = photo != null

    BoxWithConstraints(
        modifier = modifier
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .clip(shape)
            .then(
                if (onClick == null) Modifier
                else Modifier.pressable(onClick, scale = 0.985f)
            ),
    ) {
// 数字真正能用的宽度：左右内边距（全屏时宽一档）+ 竖线 3dp + 竖线后间距 16dp
        val hPad = if (fillHeight) 26.dp else 20.dp
        val innerWidth = (maxWidth - hPad * 2 - 19.dp).coerceAtLeast(96.dp)

        if (hasPhoto) {
            Image(
                bitmap = photo,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().background(cardScrimBrush()))
        }

        GlassPanel(
            modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            shape = shape,
            weight = if (hasPhoto) 0.30f else 0.85f,
            elevation = if (fillHeight) 0.dp else null,
// 卡片底色。没选色时是 null，还是原来那块纯玻璃
            tint = Palette.card(event),
            glareKey = Triple(event.cardColor, event.numberColor, event.cardImage ?: event.mode.name),
        ) {
            Column(
                modifier = Modifier
                    .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
// 只淡内容，不动玻璃。见 `contentAlpha` 的说明。
                    .graphicsLayer { alpha = contentAlpha() }
                    .padding(
                        start = if (fillHeight) 26.dp else 20.dp,
                        end = if (fillHeight) 26.dp else 20.dp,
                        top = 22.dp,
                        bottom = 20.dp,
                    ),
                verticalArrangement = if (fillHeight) Arrangement.Center else Arrangement.Top,
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(accent, RoundedCornerShape(Radius.hair))
                            .graphicsLayer { alpha = chromeAlpha() }
                    )
                    Spacer(Modifier.width(16.dp))

                    Column {
                        val titleStyle = if (fillHeight) {
                            val base = MaterialTheme.typography.headlineMedium
                            base.copy(
                                fontSize = base.fontSize * 1.08f,
                                lineHeight = base.lineHeight * 1.08f,
                            )
                        } else {
                            MaterialTheme.typography.headlineMedium
                        }
                        val titleText = remember(event.title, info, fillHeight, g.ink, g.inkMid) {
                            if (fillHeight) {
                                AnnotatedString(event.title)
                            } else {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = g.ink)) { append(event.title) }
                                    val state = CountdownMath.stateWord(info)
                                    if (state != null) {
                                        append(" ")
                                        withStyle(SpanStyle(color = g.inkMid)) { append(state) }
                                    }
                                }
                            }
                        }
                        var titleRect by remember { mutableStateOf(Rect.Zero) }
                        Row(
                            modifier = Modifier.padding(bottom = if (fillHeight) 10.dp else 6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = titleText,
                                style = titleStyle,
                                color = g.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .onGloballyPositioned {
                                        val m0 = morph
                                        if (m0 == null || m0.progress() >= 1f) {
                                            titleRect = it.boundsInWindow()
                                            onParts?.invoke(0, titleRect)
                                        }
                                    }
                                    .graphicsLayer {
                                        val m = morph
                                        val card = titleStyle.fontSize.toPx()
                                        if (m == null || card <= 0f || titleRect.width <= 0f) {
                                            noMorph()
                                            return@graphicsLayer
                                        }
                                        val d = m.destTitle
                                        morphTo(
                                            m = m,
                                            self = titleRect,
                                            targetLeft = d?.left
                                                ?: (m.rowLeft + m.rowPadH + m.rowBarGap),
                                            targetTop = d?.top
                                                ?: (m.rowTop + m.rowPadV),
                                            k = if (d != null) {
                                                if (titleRect.height > 0f) {
                                                    d.height / titleRect.height
                                                } else 1f
                                            } else {
                                                m.rowTitlePx / card
                                            },
                                        )
                                    },
                            )
                            if (fillHeight) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = CountdownMath.stateWord(info) ?: "已经",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = accent,
                                    maxLines = 1,
                                )
                            }
                        }
                        val num = remember(info, numberMode) {
                            CountdownMath.cardNumber(info, numberMode)
                        }
                        val todayOnly = info.isToday
                        val clockVisible = secondsUnderNumber &&
                            numberMode == CardNumberMode.DAY &&
                            showSeconds

                        val segments = remember(num, todayOnly) {
                            if (todayOnly) listOf("今天" to 1f) else numberSegments(num)
                        }
                        val fittedSp = rememberFittedNumberSp(
                            segments = segments,
                            maxWidth = innerWidth,
                            maxHeight = numberHeight,
                            style = MaterialTheme.typography.displayMedium,
                        )
                        val spAnim = remember(event.id) { Animatable(fittedSp.value) }
                        LaunchedEffect(fittedSp) {
                            spAnim.animateTo(fittedSp.value, Motion.settle())
                        }
                        val numSp = spAnim.value.coerceAtLeast(1f).sp
                        val baseGap = (numSp.value * 0.16f).dp

                        val typo = MaterialTheme.typography
                        val unitStyle = remember(numSp, typo) {
                            typo.titleMedium.copy(
                                fontSize = numSp * UNIT_RATIO,
                                lineHeight = TextUnit.Unspecified,
                            )
                        }
                        val leadStyle = remember(numSp, typo) {
                            typo.titleMedium.copy(
                                fontSize = numSp * LEAD_RATIO,
                                lineHeight = TextUnit.Unspecified,
                            )
                        }
                        val todayStyle = remember(numSp, typo) {
                            typo.displayMedium.copy(
                                fontSize = numSp,
                                lineHeight = TextUnit.Unspecified,
                            )
                        }

                        var numRect by remember { mutableStateOf(Rect.Zero) }
                        Box(
                            modifier = (Modifier
                                .onGloballyPositioned {
                                    val m0 = morph
                                    if (m0 == null || m0.progress() >= 1f) {
                                        numRect = it.boundsInWindow()
                                        onParts?.invoke(1, numRect)
                                    }
                                }
                                .graphicsLayer {
                                    val m = morph
                                    val card = numSp.toPx()
                                    if (m == null || card <= 0f || numRect.width <= 0f) {
                                        noMorph()
                                        return@graphicsLayer
                                    }
                                    val d = m.destNumber
                                    val k = if (d != null) {
                                        if (numRect.height > 0f) d.height / numRect.height else 1f
                                    } else {
                                        m.rowNumberPx / card
                                    }
                                    morphTo(
                                        m = m,
                                        self = numRect,
                                        targetLeft = d?.left
                                            ?: (m.rowLeft + m.rowWidth - m.rowPadH -
                                                numRect.width * k),
// 垂直居中。
                                        targetTop = d?.top
                                            ?: (m.rowTop +
                                                (m.rowHeight - numRect.height * k) * 0.5f),
                                        k = k,
                                    )
                                }
                                .then(
                                    if (onNumberTap == null) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .clip(RoundedCornerShape(Radius.chip))
                                            .pressable(
                                        onClick = onNumberTap,
                                        scale = 0.97f,
                                        onLongClick = onNumberLongPress ?: {},
                                        longClickFeedback = onNumberLongPress != null,
                                    )
                                    .padding(horizontal = 2.dp)
                                    }
                                )
                            )
                        ) {
                            if (todayOnly) {
                                Text(
                                    text = "今天",
                                    style = todayStyle,
                                    color = numColor,
                                )
                            } else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    if (num.lead.isNotEmpty()) {
                                        Text(
                                            text = num.lead,
                                            style = leadStyle,
                                            color = numColor,
                                            modifier = Modifier.padding(
                                                end = SEGMENT_GAP,
                                                bottom = baseGap,
                                            ),
                                        )
                                    }
                                    num.parts.forEachIndexed { index, part ->
                                        if (index > 0) Spacer(Modifier.width(SEGMENT_GAP))
                                        RollingText(
                                            text = part.value,
                                            style = MaterialTheme.typography.displayMedium,
                                            color = numColor,
                                            fontSize = numSp,
                                            durationMillis = 340,
                                        )
                                        if (part.unit.isNotEmpty()) {
                                            Text(
                                                text = part.unit,
                                                style = unitStyle,
                                                color = numColor,
                                                modifier = Modifier.padding(
                                                    start = SEGMENT_GAP,
                                                    bottom = baseGap,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = clockVisible,
// 行里没有秒表，落地之前要淡掉。见 `chromeAlpha`。
                            modifier = Modifier.graphicsLayer { alpha = chromeAlpha() },
                            enter = expandVertically(
                                animationSpec = tween(Motion.Enter, easing = Motion.enterEasing),
                                expandFrom = Alignment.Top,
                            ) + fadeIn(tween(Motion.Enter)),
                            exit = shrinkVertically(
                                animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
                                shrinkTowards = Alignment.Top,
                            ) + fadeOut(tween(Motion.Exit)),
                            label = "clockRow",
                        ) {
                            ClockRow(
                                event = event,
                                size = (numSp.value * CLOCK_ROW_RATIO)
                                    .coerceIn(CLOCK_SP_RANGE.start, CLOCK_SP_RANGE.endInclusive)
                                    .sp,
                                color = numColor,
                            )
                        }

                        var dateRect by remember { mutableStateOf(Rect.Zero) }
                        val dateStyle = if (fillHeight) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        }
                        Text(
                            text = headlineMeta(
                                event = event,
                                info = info,
                                showCaption = numberMode != CardNumberMode.DAY,
                            ),
                            style = dateStyle,
                            color = g.inkMid,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = if (fillHeight) 8.dp else 4.dp)
                                .onGloballyPositioned {
                                    val m0 = morph
                                    if (m0 == null || m0.progress() >= 1f) {
                                        dateRect = it.boundsInWindow()
                                        onParts?.invoke(2, dateRect)
                                    }
                                }
                                .graphicsLayer {
                                    val m = morph
                                    val card = dateStyle.fontSize.toPx()
                                    if (m == null || card <= 0f || dateRect.width <= 0f) {
                                        noMorph()
                                        return@graphicsLayer
                                    }
                                    val d = m.destSubtitle
                                    val k = if (d != null) {
                                        if (dateRect.height > 0f) d.height / dateRect.height else 1f
                                    } else {
                                        m.rowSubtitlePx / card
                                    }
                                    val ts = titleStyle.fontSize.toPx()
                                        .let { if (it <= 0f) 1f else m.rowTitlePx / it }
                                    morphTo(
                                        m = m,
                                        self = dateRect,
                                        targetLeft = d?.left
                                            ?: (m.rowLeft + m.rowPadH + m.rowBarGap),
                                        targetTop = d?.top
                                            ?: (m.rowTop + m.rowPadV +
                                                titleRect.height * ts + 2.dp.toPx()),
                                        k = k,
                                    )
                                },
                        )
                    }
                }

            if (event.showSeconds && !secondsUnderNumber) {
                    Column(Modifier.graphicsLayer { alpha = chromeAlpha() }) {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(g.hairline)
                        )
                        Spacer(Modifier.height(12.dp))
                        PreciseRow(event = event)
                    }
                }

// 进度：计时有终点时是"走到哪"，倒数有重复时是"本周期走到哪"
                val showProgress = (event.mode == EventMode.ELAPSED && info.hasProgress) ||
                    (event.mode == EventMode.COUNTDOWN && event.repeat != RepeatRule.NONE && info.isFuture)
                if (showProgress) {
                    Column(Modifier.graphicsLayer { alpha = chromeAlpha() }) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = progressText(event, info),
                            style = MaterialTheme.typography.bodySmall,
                            color = g.inkMid,
                        )
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(ratio = info.progress, color = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockRow(event: CountdownEvent, size: TextUnit, color: Color) {
    RollingText(
        text = CountdownMath.precise(event, LocalNow.current.value).clock(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontSize = size,
        durationMillis = 200,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp),
    )
}

@Composable
private fun PreciseRow(event: CountdownEvent) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (event.mode == EventMode.ELAPSED) "精确走过" else "精确剩余",
            style = MaterialTheme.typography.labelSmall,
            color = g.inkSoft,
        )
        Spacer(Modifier.weight(1f))
// 秒表读数：每滚一位就动一位，"时"和"分"大部分时间是不动的。
        RollingText(
            text = CountdownMath.precise(event, LocalNow.current.value).text(),
            style = MaterialTheme.typography.labelMedium,
            color = g.ink,
            durationMillis = 200,
        )
    }
}

@Composable
private fun ProgressBar(ratio: Float, color: Color) {
    val g = LocalGlass.current
    val r = ratio.coerceIn(0.004f, 0.996f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        Box(
            Modifier
                .weight(r)
                .fillMaxSize()
                .background(color, RoundedCornerShape(Radius.hair))
        )
        Box(
            Modifier
                .weight(1f - r)
                .fillMaxSize()
                .background(g.ink.copy(alpha = 0.09f), RoundedCornerShape(Radius.hair))
        )
    }
}

private fun headlineMeta(
    event: CountdownEvent,
    info: CountdownInfo,
    showCaption: Boolean,
): String {
    val parts = mutableListOf<String>()
    if (showCaption) parts.add(CountdownMath.caption(info))

    if (event.mode == EventMode.ELAPSED) {
        val start = info.cycleStart
        parts.add("从 ${start.year}年${start.monthValue}月${start.dayOfMonth}日 起")
        if (info.hasEnd) parts.add("共 ${info.totalDays} 天")
    } else {
        val t = info.target
        parts.add("${t.year}年${t.monthValue}月${t.dayOfMonth}日")
        parts.add(weekdayText(t))
        if (event.repeat != RepeatRule.NONE) parts.add(event.repeat.label)
    }
    return parts.joinToString(" · ")
}

private fun progressText(event: CountdownEvent, info: CountdownInfo): String =
    if (event.mode == EventMode.ELAPSED) {
        "已走过 ${info.days} 天，共 ${info.totalDays} 天"
    } else {
        val done = ChronoUnit.DAYS.between(info.cycleStart, info.today)
        val total = ChronoUnit.DAYS.between(info.cycleStart, info.target)
        "本周期已过 $done 天，共 $total 天"
    }

private fun onOff(value: Boolean) = if (value) "开" else "关"

private fun timeText(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun dateText(d: LocalDate): String = "${d.year}年${d.monthValue}月${d.dayOfMonth}日"

private fun createdDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun weekdayText(date: LocalDate): String {
    val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return names[date.dayOfWeek.value - 1]
}

