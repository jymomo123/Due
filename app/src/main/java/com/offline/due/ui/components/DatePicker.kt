package com.offline.due.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import java.time.LocalDate
import java.time.YearMonth

private val CellH = 44.dp

private val WeekRowH = 22.dp

private val MonthTitleH = 44.dp

private const val GridRows = 6

private val BodyH = MonthTitleH + WeekRowH + CellH * GridRows

private const val YearCols = 3

private const val MonthCols = 3

private const val YearFirst = 1900
private const val YearLast = 2100

private enum class PickerTab(val label: String) {
    YEAR("年"),
    MONTH("月"),
    DAY("日"),
}

private val PickerTabLabels: List<String> = PickerTab.entries.map { it.label }

@Composable
fun DatePickDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,

    onClear: (() -> Unit)? = null,

    minDate: LocalDate? = null,
) {
    val g = LocalGlass.current
    val today = LocalDate.now()

    var picked by remember { mutableStateOf(initial) }
    var shown by remember { mutableStateOf(YearMonth.from(initial)) }
    var tab by remember { mutableStateOf(PickerTab.DAY) }
// 每次进入年视图 +1，用来重建年份列表的位置（见下）。
    var yearOpen by remember { mutableStateOf(0) }

    val goMonth: (YearMonth) -> Unit = { m ->
        shown = m
        var d = m.atDay(picked.dayOfMonth.coerceAtMost(m.lengthOfMonth()))
        if (minDate != null && d.isBefore(minDate)) d = minDate
        picked = d
    }

    val yearState = key(yearOpen) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = ((shown.year - YearFirst) / YearCols - 2)
                .coerceAtLeast(0)
        )
    }

    GlassDialog(onDismiss = onDismiss, fullWidth = true) { close ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RowInset),
            contentAlignment = Alignment.Center,
        ) {
            GlassPanel(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                weight = 1f,
            ) {
                Column(modifier = Modifier.padding(horizontal = RowInset)) {
                    Text(
                        text = "选择日期",
                        style = MaterialTheme.typography.labelSmall,
                        color = g.inkSoft,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp),
                    )

                    SegmentedChoice(
                        options = PickerTabLabels,
                        selected = tab.label,
                        onPick = { label ->
                            val next = PickerTab.entries.first { it.label == label }
                            tab = next
// 每次进年视图都重建列表位置：选完年再回来，要落在新年份上。
                            if (next == PickerTab.YEAR) yearOpen++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )

                    Box(modifier = Modifier.height(BodyH)) {
                        AnimatedContent(
                            targetState = tab,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                fadeIn(
                                    animationSpec = tween(
                                        durationMillis = Motion.Enter,
                                        delayMillis = Motion.Exit,
                                        easing = Motion.enterEasing,
                                    )
                                ) togetherWith fadeOut(
                                    animationSpec = tween(
                                        durationMillis = Motion.Exit,
                                        easing = Motion.exitEasing,
                                    )
                                )
                            },
                            label = "date-body",
                        ) { which ->
                            when (which) {
                                PickerTab.YEAR -> YearGrid(
                                    shownYear = shown.year,
                                    today = today,
                                    minDate = minDate,
                                    state = yearState,
                                    onPickYear = {
                                        goMonth(shown.withYear(it))
                                        tab = PickerTab.MONTH
                                    },
                                )

                                PickerTab.MONTH -> MonthPickerGrid(
                                    shown = shown,
                                    today = today,
                                    minDate = minDate,
                                    onPickMonth = {
                                        goMonth(shown.withMonth(it))
                                        tab = PickerTab.DAY
                                    },
                                    onStepYear = { delta ->
                                        goMonth(shown.plusYears(delta.toLong()))
                                    },
                                )

                                PickerTab.DAY -> DayBody(
                                    shown = shown,
                                    picked = picked,
                                    today = today,
                                    minDate = minDate,
                                    onStepMonth = goMonth,
                                    onPickDay = { picked = it },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onClear != null) {
                            TextAction(
                                text = "清除",
                                onClick = {
                                    close {
                                        onClear()
                                        onDismiss()
                                    }
                                },
                                color = g.inkMid,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextAction(text = "取消", onClick = { close(onDismiss) })
                        TextAction(
                            text = "确定",
                            onClick = {
                                close {
                                    onPick(picked)
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
}

@Composable
private fun MonthPickerGrid(
    shown: YearMonth,
    today: LocalDate,
    minDate: LocalDate?,
    onPickMonth: (Int) -> Unit,
    onStepYear: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .swipePage(
                onPrev = { onStepYear(-1) },
                onNext = { onStepYear(1) },
            ),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        (1..12).chunked(MonthCols).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { m ->
                    val first = shown.withMonth(m).atDay(1)
                    val last = first.withDayOfMonth(first.lengthOfMonth())
                    LabelChip(
                        modifier = Modifier.weight(1f),
                        text = "${m}月",
                        selected = m == shown.monthValue,
                        accent = shown.year == today.year && m == today.monthValue,
// 整月都在下限之前 = 一个日子都点不动，整格置灰。
                        enabled = minDate == null || !last.isBefore(minDate),
                        onClick = { onPickMonth(m) },
                    )
                }
                repeat(MonthCols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayBody(
    shown: YearMonth,
    picked: LocalDate,
    today: LocalDate,
    minDate: LocalDate?,
    onStepMonth: (YearMonth) -> Unit,
    onPickDay: (LocalDate) -> Unit,
) {
    val g = LocalGlass.current
    val lead = shown.atDay(1).dayOfWeek.value - 1
    val total = shown.lengthOfMonth()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .swipePage(
                onPrev = { onStepMonth(shown.minusMonths(1)) },
                onNext = { onStepMonth(shown.plusMonths(1)) },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MonthTitleH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(
                text = "‹",
                onClick = { onStepMonth(shown.minusMonths(1)) },
                color = g.inkMid,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${shown.year} 年 ${shown.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                color = g.ink,
            )
            Spacer(Modifier.weight(1f))
            TextAction(
                text = "›",
                onClick = { onStepMonth(shown.plusMonths(1)) },
                color = g.inkMid,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(WeekRowH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = g.inkSoft,
                    )
                }
            }
        }

        repeat(GridRows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNumber = row * 7 + col - lead + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(CellH)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNumber in 1..total) {
                            val date = shown.atDay(dayNumber)
                            DayChip(
                                date = date,
                                selected = date == picked,
                                today = date == today,
                                enabled = minDate == null || !date.isBefore(minDate),
                                onClick = { onPickDay(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
    val shape = CircleShape
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .clip(shape)
            .then(
                when {
                    selected -> Modifier.background(g.inverse)
                    today -> Modifier.border(1.dp, g.accent, shape)
                    else -> Modifier
                }
            )
            .then(if (enabled) Modifier.pressable(onClick, scale = 0.90f) else Modifier)
            .alpha(if (enabled) 1f else 0.32f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> g.onInverse
                today -> g.accent
                else -> g.ink
            },
        )
    }
}

@Composable
private fun YearGrid(
    shownYear: Int,
    today: LocalDate,
    minDate: LocalDate?,
    state: LazyListState,
    onPickYear: (Int) -> Unit,
) {
    val rows = remember {
        (YearFirst..YearLast).chunked(YearCols)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(count = rows.size) { i ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rows[i].forEach { year ->
                    LabelChip(
                        modifier = Modifier.weight(1f),
                        text = year.toString(),
                        selected = year == shownYear,
                        accent = year == today.year,
// 整年都在下限之前 = 一年里的日子一个都点不动。
                        enabled = minDate == null || year >= minDate.year,
                        onClick = { onPickYear(year) },
                    )
                }
// 最后一行可能不满三列，补空位，否则那几个格子会被拉宽。
                repeat(YearCols - rows[i].size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LabelChip(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    accent: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
    Box(
        modifier = modifier
            .height(CellH)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Radius.chip))
                .then(if (selected) Modifier.background(g.inverse) else Modifier)
                .then(if (enabled) Modifier.pressable(onClick, scale = 0.94f) else Modifier)
                .alpha(if (enabled) 1f else 0.32f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected || accent) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> g.onInverse
                    accent -> g.accent
                    else -> g.ink
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(
    hour: Int,
    minute: Int,
    onPick: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true,
    )
// 和其它弹窗走同一个壳，否则只有这一个会"啪"地出现。
    GlassDialog(onDismiss = onDismiss) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { close(onDismiss) }) {
                        Text("取消", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = {
                        close {
                            onPick(state.hour, state.minute)
                            onDismiss()
                        }
                    }) {
                        Text("确定", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

