package com.offline.due.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.offline.due.data.CountdownEvent
import com.offline.due.domain.CountdownMath
import com.offline.due.ui.components.CardInset
import com.offline.due.ui.components.EventRow
import com.offline.due.ui.components.GlassDialog
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.KeyBar
import com.offline.due.ui.components.KeyCap
import com.offline.due.ui.components.SlidingSegments
import com.offline.due.ui.components.TextAction
import com.offline.due.ui.components.pressable
import com.offline.due.ui.components.swipePage
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import com.offline.due.ui.theme.rememberWideLayout
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(
    events: List<CountdownEvent>,
    onOpenEvent: (String) -> Unit,
    onBack: () -> Unit,

    pageBase: Color,
) {
    val today = rememberToday()
    val wide = rememberWideLayout()

    var selectedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val month = YearMonth.from(selected)

// 0 = 月历，1 = 年历。存 Int 而不是枚举，同样是为了 saveable。
    var mode by rememberSaveable { mutableStateOf(0) }

    val goMonth: (YearMonth) -> Unit = { target ->
        selectedEpoch = CountdownMath
            .clampToMonth(target.year, target.monthValue, selected.dayOfMonth)
            .toEpochDay()
    }

// 翻年：同一个月的同一天，换一年。和翻月是同一个动作，只是步长不同。
    val goYear: (Int) -> Unit = { delta ->
        selectedEpoch = CountdownMath
            .clampToMonth(selected.year + delta, selected.monthValue, selected.dayOfMonth)
            .toEpochDay()
    }

    IceBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            KeyBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 10.dp),
            ) {
                KeyCap(text = "返回", onClick = onBack)
                Spacer(Modifier.weight(1f))
                KeyCap(
                    text = "今天",
                    onClick = { selectedEpoch = today.toEpochDay() },
                    emphasis = true,
                )
            }

            SlidingSegments(
                options = listOf("月历", "年历"),
                selected = mode,
                onSelect = { mode = it },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )

            AnimatedContent(
                targetState = mode,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (
                        slideInHorizontally(
                            tween(Motion.Enter, easing = Motion.enterEasing)
                        ) { it / 3 * dir } +
                            fadeIn(tween(Motion.Enter, easing = Motion.enterEasing))
                        ) togetherWith (
                        slideOutHorizontally(
                            tween(Motion.Exit, easing = Motion.exitEasing)
                        ) { -it / 3 * dir } +
                            fadeOut(tween(Motion.Exit, easing = Motion.exitEasing))
                        )
                },
                label = "calendar-mode",
            ) { m ->
                if (m == 0) {
                    MonthView(
                        events = events,
                        today = today,
                        selected = selected,
                        month = month,
                        wide = wide,
                        onGoMonth = goMonth,
                        onSelect = { selectedEpoch = it.toEpochDay() },
                        onOpenEvent = onOpenEvent,
                    )
                } else {
                    YearView(
                        events = events,
                        today = today,
                        year = selected.year,
                        pageBase = pageBase,
                        onGoYear = goYear,
                        onPickDay = { date ->
                            selectedEpoch = date.toEpochDay()
                            mode = 0
                        },
                        onOpenEvent = onOpenEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthView(
    events: List<CountdownEvent>,
    today: LocalDate,
    selected: LocalDate,
    month: YearMonth,
    wide: Boolean,
    onGoMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val dayEvents = remember(events, selected) {
        CountdownMath.eventsOn(events, selected)
    }
    val marks = remember(events, month, today) {
        val map = HashMap<LocalDate, Int>(32)
        for (d in 1..month.lengthOfMonth()) {
            val date = month.atDay(d)
            val hit = CountdownMath.eventsOn(events, date).size
            if (hit > 0) map[date] = hit
        }
        map
    }

    if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Gutter, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.widthIn(max = 440.dp)) {
                    MonthPanel(
                        month = month,
                        today = today,
                        selected = selected,
                        marks = marks,
                        onGoMonth = onGoMonth,
                        onSelect = onSelect,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

// 右：选中的那一天。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = Gutter),
            ) {
                DayHeading(selected = selected, count = dayEvents.size)
                DayList(
                    events = dayEvents,
                    today = today,
                    onOpenEvent = onOpenEvent,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
// 大屏上限宽 560dp 后居中；小屏上就是原来的全宽
                modifier = Modifier
                    .contentWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Gutter),
            ) {
                MonthPanel(
                    month = month,
                    today = today,
                    selected = selected,
                    marks = marks,
                    onGoMonth = onGoMonth,
                    onSelect = onSelect,
                )

                Spacer(Modifier.height(12.dp))

                DayHeading(selected = selected, count = dayEvents.size)

                DayList(
                    events = dayEvents,
                    today = today,
                    onOpenEvent = onOpenEvent,
                )

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private class YearMarks(val days: Set<LocalDate>) {
    val total: Int get() = days.size
}

private const val YEAR_MAX_ZOOM = 5f

private const val YEAR_TAP_ZOOM = 2.5f

@Composable
private fun YearView(
    events: List<CountdownEvent>,
    today: LocalDate,
    year: Int,
    pageBase: Color,
    onGoYear: (Int) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val g = LocalGlass.current

// 整年扫一遍建索引，而不是让 372 个格子各自遍历事件
    val marks = remember(events, year, today) {
        val set = HashSet<LocalDate>(400)
        var d = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year + 1, 1, 1)
        while (d.isBefore(end)) {
            if (CountdownMath.eventsOn(events, d).isNotEmpty()) set.add(d)
            d = d.plusDays(1)
        }
        YearMarks(set)
    }

    var zoom by remember(year) { mutableFloatStateOf(1f) }
    var tx by remember(year) { mutableFloatStateOf(0f) }
    var ty by remember(year) { mutableFloatStateOf(0f) }

// 按下的是哪一格（row * 31 + col）。只用来画按压高亮。
    var pressed by remember { mutableIntStateOf(-1) }
    var openDay by remember(year) { mutableStateOf<LocalDate?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$year",
                style = MaterialTheme.typography.headlineSmall,
                color = g.ink,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "全年 ${marks.total} 天有事件",
                style = MaterialTheme.typography.bodySmall,
                color = g.inkMid,
            )
            Spacer(Modifier.weight(1f))
            TextAction(text = "‹", onClick = { onGoYear(-1) }, color = g.inkMid)
            TextAction(text = "›", onClick = { onGoYear(1) }, color = g.inkMid)
        }

// ── 网格 ─────────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds(),
        ) {
            val density = LocalDensity.current
            val labelW = 26.dp
            val gap = 2.dp
            val edge = 6.dp

            val labelPx = with(density) { labelW.toPx() }
            val gapPx = with(density) { gap.toPx() }
            val edgePx = with(density) { edge.toPx() }
            val viewW = with(density) { maxWidth.toPx() }
            val viewH = with(density) { maxHeight.toPx() }

            val availW = (viewW - labelPx - edgePx).coerceAtLeast(1f)
            val availH = viewH.coerceAtLeast(1f)

            val cellDp = with(density) {
                min(
                    (availW - gapPx * 30f) / 31f,
                    (availH - gapPx * 11f) / 12f,
                ).coerceAtLeast(1f).toDp()
            }
            val cellPx = with(density) { cellDp.toPx() }
            val pitchPx = cellPx + gapPx
            val gridW = cellPx * 31f + gapPx * 30f
            val gridH = cellPx * 12f + gapPx * 11f

            fun clampTx(raw: Float, z: Float): Float {
                val w = gridW * z
                return if (w <= availW) labelPx + (availW - w) / 2f
                else raw.coerceIn(labelPx + availW - w, labelPx)
            }

            fun clampTy(raw: Float, z: Float): Float {
                val h = gridH * z
                return if (h <= availH) (availH - h) / 2f
                else raw.coerceIn(availH - h, 0f)
            }

// 视口坐标 → 格子下标。落在缝里或越界一律 -1。
            fun cellAt(x: Float, y: Float): Int {
                val lx = (x - clampTx(tx, zoom)) / zoom
                val ly = (y - clampTy(ty, zoom)) / zoom
                if (lx < 0f || ly < 0f) return -1
                val col = floor(lx / pitchPx).toInt()
                val row = floor(ly / pitchPx).toInt()
                if (col !in 0..30 || row !in 0..11) return -1
// 缝只有 2dp，手指按在缝上不该点亮旁边那一格。
                if (lx - col * pitchPx > cellPx || ly - row * pitchPx > cellPx) return -1
// 2 月没有 30 号：那一格根本不存在。
                if (col + 1 > YearMonth.of(year, row + 1).lengthOfMonth()) return -1
                return row * 31 + col
            }

            fun dateOf(cell: Int): LocalDate =
                LocalDate.of(year, cell / 31 + 1, cell % 31 + 1)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cellPx, pitchPx, gridW, gridH, availW, availH, labelPx, year) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val next = (zoom * gestureZoom).coerceIn(1f, YEAR_MAX_ZOOM)
                            val k = next / zoom
                            val curX = clampTx(tx, zoom)
                            val curY = clampTy(ty, zoom)
                            tx = clampTx(centroid.x - (centroid.x - curX) * k + pan.x, next)
                            ty = clampTy(centroid.y - (centroid.y - curY) * k + pan.y, next)
                            zoom = next
                        }
                    }
                    .pointerInput(cellPx, pitchPx, gridW, gridH, availW, availH, labelPx, year) {
                        detectTapGestures(
                            onPress = { pos ->
                                val cell = cellAt(pos.x, pos.y)
                                pressed = if (cell >= 0 && marks.days.contains(dateOf(cell))) {
                                    cell
                                } else {
                                    -1
                                }
                                tryAwaitRelease()
                                pressed = -1
                            },
                            onTap = { pos ->
                                val cell = cellAt(pos.x, pos.y)
                                if (cell >= 0 && marks.days.contains(dateOf(cell))) {
                                    openDay = dateOf(cell)
                                }
                            },
                            onDoubleTap = { pos ->
                                val next = if (zoom > 1.05f) 1f else YEAR_TAP_ZOOM
                                val k = next / zoom
                                val curX = clampTx(tx, zoom)
                                val curY = clampTy(ty, zoom)
                                tx = clampTx(pos.x - (pos.x - curX) * k, next)
                                ty = clampTy(pos.y - (pos.y - curY) * k, next)
                                zoom = next
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        val z = zoom
                        scaleX = z
                        scaleY = z
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = clampTx(tx, z)
                        translationY = clampTy(ty, z)
                    },
                ) {
                    Column {
                        for (m in 1..12) {
                            val ym = YearMonth.of(year, m)
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (d in 1..31) {
                                    val valid = d <= ym.lengthOfMonth()
                                    val date = if (valid) ym.atDay(d) else null
                                    val marked = date != null && marks.days.contains(date)
                                    val isToday = date == today
                                    val past = date != null && date.isBefore(today)
                                    Box(
                                        modifier = Modifier
                                            .size(cellDp)
                                            .clip(RoundedCornerShape(Radius.hair))
                                            .then(
                                                when {
                                                    isToday && marked -> Modifier
                                                        .background(g.accent)
                                                        .border(
                                                            1.dp,
                                                            g.edgeLight,
                                                            RoundedCornerShape(Radius.hair),
                                                        )
                                                    isToday -> Modifier.border(
                                                        1.dp,
                                                        g.accent,
                                                        RoundedCornerShape(Radius.hair),
                                                    )
                                                    marked -> Modifier.background(
                                                        g.accent.copy(alpha = if (past) 0.40f else 1f),
                                                    )
                                                    valid -> Modifier.background(
                                                        g.ink.copy(alpha = if (past) 0.035f else 0.07f),
                                                    )
                                                    else -> Modifier
                                                }
                                            )
                                    )
                                }
                            }
                            if (m != 12) Spacer(Modifier.height(gap))
                        }
                    }

                    Canvas(modifier = Modifier.matchParentSize()) {
                        val i = pressed
                        if (i < 0) return@Canvas
                        drawRoundRect(
                            color = g.ink.copy(alpha = 0.18f),
                            topLeft = Offset((i % 31) * pitchPx, (i / 31) * pitchPx),
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }

                val pageBg = pageBase
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight()
                        .width(labelW + 12.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(pageBg, pageBg, pageBg.copy(alpha = 0f)),
                                startX = 0f,
                                endX = with(density) { (labelW + 12.dp).toPx() },
                            )
                        ),
                )
                for (m in 1..12) {
                    Text(
                        text = "%02d".format(m),
                        style = MaterialTheme.typography.labelSmall,
                        color = g.inkMid,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                val z = zoom
                                val top = clampTy(ty, z) + (m - 1) * pitchPx * z
                                IntOffset(0, (top + (pitchPx * z - 16.dp.toPx()) / 2f).roundToInt())
                            }
                            .width(labelW),
                    )
                }
            }
        }

        Text(
            text = if (year == today.year) {
                "实心＝有事件　描边＝今天　双击放大"
            } else {
                "实心＝有事件　双击放大"
            },
            style = MaterialTheme.typography.bodySmall,
            color = g.inkSoft,
            modifier = Modifier.padding(start = Gutter, top = 8.dp, bottom = 10.dp),
        )
    }

    val day = openDay
    if (day != null) {
        YearDayDialog(
            date = day,
            events = remember(events, day) { CountdownMath.eventsOn(events, day) },
            today = today,
            onOpenEvent = onOpenEvent,
            onPickDay = onPickDay,
            onDismiss = { openDay = null },
        )
    }
}

@Composable
private fun YearDayDialog(
    date: LocalDate,
    events: List<CountdownEvent>,
    today: LocalDate,
    onOpenEvent: (String) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val g = LocalGlass.current
    GlassDialog(onDismiss = onDismiss) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(vertical = 18.dp)) {
                Text(
                    text = "${date.monthValue} 月 ${date.dayOfMonth} 日 · " +
                        "${weekdayText(date)} · ${events.size} 个事件",
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkSoft,
                    modifier = Modifier.padding(horizontal = CardInset),
                )
                Spacer(Modifier.height(8.dp))

                events.forEachIndexed { index, event ->
                    EventRow(
                        event = event,
                        info = CountdownMath.occurrence(event, today),
                        onClick = {
                            close {
                                onOpenEvent(event.id)
                                onDismiss()
                            }
                        },
                        horizontalPadding = 14.dp,
                        showCardLook = false,
                    )
                    if (index != events.lastIndex) {
                        Box(
                            Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(g.hairline)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.chip))
                        .pressable(
                            {
                                close {
                                    onPickDay(date)
                                    onDismiss()
                                }
                            },
                            scale = 1f,
                            specular = false,
                        )
                        .padding(horizontal = CardInset, vertical = 12.dp),
                ) {
                    Text(
                        text = "在月历里看这天",
                        style = MaterialTheme.typography.titleMedium,
                        color = g.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPanel(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    marks: Map<LocalDate, Int>,
    onGoMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val g = LocalGlass.current
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        weight = 0.7f,
    ) {
        Column(
            modifier = Modifier
                .swipePage(
                    onPrev = { onGoMonth(month.minusMonths(1)) },
                    onNext = { onGoMonth(month.plusMonths(1)) },
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${month.year} 年 ${month.monthValue} 月",
                    style = MaterialTheme.typography.headlineSmall,
                    color = g.ink,
                    modifier = Modifier.weight(1f),
                )
                TextAction(
                    text = "‹",
                    onClick = { onGoMonth(month.minusMonths(1)) },
                    color = g.inkMid,
                )
                TextAction(
                    text = "›",
                    onClick = { onGoMonth(month.plusMonths(1)) },
                    color = g.inkMid,
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = g.inkSoft,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            MonthGrid(
                month = month,
                today = today,
                selected = selected,
                marks = marks,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun DayHeading(selected: LocalDate, count: Int) {
    val g = LocalGlass.current
    Text(
        text = "${selected.monthValue} 月 ${selected.dayOfMonth} 日 · " +
            "${weekdayText(selected)} · $count 个事件",
        style = MaterialTheme.typography.bodySmall,
        color = g.inkMid,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 10.dp),
    )
}

@Composable
private fun DayList(
    events: List<CountdownEvent>,
    today: LocalDate,
    onOpenEvent: (String) -> Unit,
) {
    val g = LocalGlass.current
    if (events.isEmpty()) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.panel),
            weight = 0.4f,
        ) {
            Text(
                text = "这一天没有安排",
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
                modifier = Modifier.padding(18.dp),
            )
        }
        return
    }
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.panel),
        weight = 0.5f,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            events.forEachIndexed { index, event ->
                EventRow(
                    event = event,
                    info = CountdownMath.occurrence(event, today),
                    onClick = { onOpenEvent(event.id) },
                    horizontalPadding = 14.dp,
                )
                if (index != events.lastIndex) {
                    Box(
                        Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(g.hairline)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    marks: Map<LocalDate, Int>,
    onSelect: (LocalDate) -> Unit,
) {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val total = month.lengthOfMonth()
    val rows = (lead + total + 6) / 7

    Column {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val dayNumber = index - lead + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNumber in 1..total) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selected,
                                marked = marks.containsKey(date),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
    val fg = if (isSelected) g.onInverse else g.ink
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(g.inverse)
                    isToday -> Modifier.border(1.dp, g.accent, CircleShape)
                    else -> Modifier
                }
            )
            .pressable(onClick, scale = 0.90f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday && !isSelected) g.accent else fg,
        )
        if (marked) {
            Spacer(Modifier.size(3.dp))
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .background(
                        if (isSelected) g.onInverse.copy(alpha = 0.8f) else g.accent,
                        CircleShape,
                    )
            )
        }
    }
}

private fun weekdayText(date: LocalDate): String {
    val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return names[date.dayOfWeek.value - 1]
}

