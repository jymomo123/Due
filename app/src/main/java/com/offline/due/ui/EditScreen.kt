package com.offline.due.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventMode
import com.offline.due.data.RemindRule
import com.offline.due.data.RepeatRule
import com.offline.due.domain.CountdownMath
import com.offline.due.domain.NaturalDateParser
import com.offline.due.ui.components.RowInset
import com.offline.due.ui.components.CardBackdropPage
import com.offline.due.ui.components.ChoiceDialog
import com.offline.due.ui.components.ColorPickerPage
import com.offline.due.ui.components.ColorRow
import com.offline.due.ui.components.ConfirmDialog
import com.offline.due.ui.components.DatePickDialog
import com.offline.due.ui.components.GlassInset
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.InsetLine
import com.offline.due.ui.components.KeyBar
import com.offline.due.ui.components.KeyCap
import com.offline.due.ui.components.NumberGridDialog
import com.offline.due.ui.components.SectionLabel
import com.offline.due.ui.components.SegmentedChoice
import com.offline.due.ui.components.SettingRow
import com.offline.due.ui.components.TextPromptDialog
import com.offline.due.ui.components.TimePickDialog
import com.offline.due.ui.components.ToggleRow
import com.offline.due.ui.components.pressable
import com.offline.due.ui.components.rememberSubPageBack
import com.offline.due.ui.components.subPageSlide
import com.offline.due.ui.theme.ColorSlot
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import java.time.LocalDate
import org.json.JSONObject

private const val NEW_CATEGORY = "＋ 新建分类"

private val DraftSaver: Saver<CountdownEvent, String> = Saver(
    save = { it.toJson().toString() },
    restore = { raw ->
        runCatching { CountdownEvent.fromJson(JSONObject(raw)) }.getOrNull()
    },
)

private sealed interface EditPage {

    val depth: Int

    data object Main : EditPage {
        override val depth = 0
    }

    data object Backdrop : EditPage {
        override val depth = 1
    }

    data class Color(val slot: ColorSlot) : EditPage {
        override val depth = 1
    }
}

@Composable
fun EditScreen(
    original: CountdownEvent,
    isNew: Boolean,

    categories: List<String>,
    onSave: (CountdownEvent) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,

    onSubBack: (Boolean) -> Unit = {},
) {
    val g = LocalGlass.current
    var draft by rememberSaveable(original.id, stateSaver = DraftSaver) {
        mutableStateOf(original)
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickRepeat by remember { mutableStateOf(false) }
    var pickRemindRule by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
// 非空表示正在调色盘里。用一个枚举而不是布尔，是因为调色盘要知道调的是哪个槽位。
    var colorSlot by remember { mutableStateOf<ColorSlot?>(null) }
// 卡片背景是页内的一层，和调色盘同一套路：走路由的话 draft 会丢。
    var cardBackdrop by remember { mutableStateOf(false) }
    var pickWeekday by remember { mutableStateOf(false) }
    var pickMonthDay by remember { mutableStateOf(false) }

    val remindFollowDay: LocalDate = run {
        val today = LocalDate.now()
        if (draft.mode == EventMode.ELAPSED) {
            if (today.isBefore(draft.anchor)) draft.anchor else today
        } else {
            CountdownMath.occurrence(draft, today).target
        }
    }
    val remindWeekday = draft.remindWeekday.takeIf { it in 1..7 } ?: remindFollowDay.dayOfWeek.value
    val remindMonthDay = draft.remindDayOfMonth.takeIf { it in 1..31 } ?: remindFollowDay.dayOfMonth

    val scroll = rememberScrollState()

    val dirty = draft != original

    val subPageOpen = cardBackdrop || colorSlot != null
    val subP = rememberSubPageBack(
        enabled = subPageOpen,
        onClose = {
            when {
                cardBackdrop -> cardBackdrop = false
                colorSlot != null -> colorSlot = null
            }
        },
    )
    BackHandler(enabled = dirty && !subPageOpen) { confirmDiscard = true }

    LaunchedEffect(subPageOpen, dirty) { onSubBack(subPageOpen || dirty) }

    DisposableEffect(Unit) { onDispose { onSubBack(false) } }

// 根据标题猜重复规则，只建议不擅自改；用户点过之后建议自动消失
    val suggestion = remember(draft.title, draft.repeat) {
        if (draft.repeat == RepeatRule.NONE) NaturalDateParser.inferRepeat(draft.title) else null
    }

    val slot = colorSlot
    val page: EditPage = when {
        slot != null -> EditPage.Color(slot)
        cardBackdrop -> EditPage.Backdrop
        else -> EditPage.Main
    }

    AnimatedContent(
        targetState = page,
        modifier = Modifier
            .fillMaxSize()
// 滑动的那一页会画到容器外面去，不裁的话右边缘会露出一条。
            .clipToBounds(),
        transitionSpec = {
            if (targetState.depth > initialState.depth) {
                (
                    slideInHorizontally(
                        tween(Motion.Page, easing = Motion.enterEasing)
                    ) { it } + fadeIn(tween(Motion.Enter, easing = Motion.enterEasing))
                    ).togetherWith(
                    fadeOut(tween(Motion.Exit, easing = Motion.exitEasing))
                )
            } else if (subP.driven) {
                fadeIn(tween(Motion.Settle, easing = Motion.handover))
                    .togetherWith(fadeOut(tween(Motion.Settle, easing = Motion.handover)))
            } else {
                fadeIn(tween(Motion.Enter, easing = Motion.enterEasing))
                    .togetherWith(
                        slideOutHorizontally(
                            tween(Motion.Page, easing = Motion.exitEasing)
                        ) { it } + fadeOut(tween(Motion.Exit, easing = Motion.exitEasing))
                    )
            }
        },
        label = "editPage",
    ) { p ->
        when (p) {
            is EditPage.Color -> Box(Modifier.fillMaxSize().subPageSlide(subP)) {
                ColorPickerPage(
                    event = draft,
                    slot = p.slot,
                    onChange = { draft = it },
                    onBack = { colorSlot = null },
                )
            }

            EditPage.Backdrop -> Box(Modifier.fillMaxSize().subPageSlide(subP)) {
                CardBackdropPage(
                    event = draft,
                    onChange = { draft = it },
                    onBack = { cardBackdrop = false },
                )
            }

            EditPage.Main -> IceBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.union(WindowInsets.ime)
                        ),
                ) {
                    KeyBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 12.dp),
                    ) {
                        KeyCap(
                            text = "返回",
// 有改动才拦一下。没改过还弹窗问"要不要放弃"，是纯打扰。
                            onClick = { if (dirty) confirmDiscard = true else onBack() },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (isNew) "新建" else "编辑",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalGlass.current.inkSoft,
                        )
                        Spacer(Modifier.weight(1f))
                        KeyCap(
                            text = "完成",
                            onClick = {
                                onSave(
                                    draft.copy(
                                        title = draft.title.ifBlank { "未命名" },
                                        category = draft.category.ifBlank { CountdownEvent.DEFAULT_CATEGORY },
                                    )
                                )
                            },
// 改过才高亮：一进来就亮着的"完成"没有信息量
                            emphasis = dirty || isNew,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .contentWidth()
                            .fillMaxHeight()
                            .verticalScroll(scroll)
                            .padding(horizontal = Gutter),
                    ) {
// ── 标题 ────────────────────────────────────
                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.panel),
                            weight = 0.7f,
                        ) {
                            Column(modifier = Modifier.padding(horizontal = RowInset, vertical = 6.dp)) {
                                BasicTextField(
                                    value = draft.title,
                                    onValueChange = { draft = draft.copy(title = it) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = g.ink),
                                    cursorBrush = SolidColor(g.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    decorationBox = { inner ->
                                        Box {
                                            if (draft.title.isEmpty()) {
                                                Text(
                                                    text = "标题",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = g.inkSoft,
                                                )
                                            }
                                            inner()
                                        }
                                    },
                                )
                                if (suggestion != null) {
                                    Text(
                                        text = "看起来像${suggestion.label}，点这里设为${suggestion.label} →",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = g.accent,
                                        modifier = Modifier
                                            .pressable({ draft = draft.copy(repeat = suggestion) }, scale = 0.96f)
                                            .padding(bottom = 12.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        SegmentedChoice(
                            options = EventMode.entries.map { it.label },
                            selected = draft.mode.label,
                            onPick = { label ->
                                val mode = EventMode.entries.firstOrNull { it.label == label } ?: return@SegmentedChoice
                                draft = draft.copy(
                                    mode = mode,
// 计时模式下"重复"没有意义，顺手清掉，免得留下看不见的状态
                                    repeat = if (mode == EventMode.ELAPSED) RepeatRule.NONE else draft.repeat,
                                )
                            },
                        )
                        Text(
                            text = draft.mode.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = g.inkSoft,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )

// ── 基本 ────────────────────────────────────
                        SectionLabel(text = "基本")
                        GlassInset(modifier = Modifier.fillMaxWidth()) {
                            SettingRow(
                                label = "分类",
                                value = draft.category,
                                onClick = { pickCategory = true },
                                showChevron = true,
                            )
                            InsetLine()
                            SettingRow(
                                label = if (draft.mode == EventMode.ELAPSED) "起始日" else "目标日",
                                value = dateText(draft.anchor),
                                onClick = { pickDate = true },
                                showChevron = true,
                            )
                            if (draft.mode == EventMode.ELAPSED) {
                                InsetLine()
                                SettingRow(
                                    label = "结束日",
                                    value = draft.endDate?.let { dateText(it) } ?: "不限",
                                    onClick = { pickEnd = true },
                                    showChevron = true,
                                    hint = "不设结束日就一直累计下去",
                                )
                            }
                            if (draft.mode == EventMode.COUNTDOWN) {
                                InsetLine()
                                SettingRow(
                                    label = "重复",
                                    value = draft.repeat.label,
                                    onClick = { pickRepeat = true },
                                    showChevron = true,
                                )
                            }
                        }

// ── 显示 ────────────────────────────────────
                        SectionLabel(text = "显示")
                        GlassInset(modifier = Modifier.fillMaxWidth()) {
                            ToggleRow(
                                label = "含起始日",
                                on = draft.includeStartDay,
                                onChange = { draft = draft.copy(includeStartDay = it) },
                                hint = "把第一天算进去。只影响已经过的天数",
                            )
                            InsetLine()
                            ToggleRow(
                                label = "显示秒",
                                on = draft.showSeconds,
                                onChange = { draft = draft.copy(showSeconds = it) },
                                hint = "在大数字下面显示 时:分:秒",
                            )
                            InsetLine()
                            ToggleRow(
                                label = "置顶",
                                on = draft.pinned,
                                onChange = { draft = draft.copy(pinned = it) },
                                hint = "置顶的事件永远排在列表最前面",
                            )
                        }

// ── 颜色 ────────────────────────────────────
                        SectionLabel(text = "颜色")
                        GlassInset(modifier = Modifier.fillMaxWidth()) {
                            ColorSlot.entries.forEachIndexed { index, s ->
                                if (index > 0) InsetLine()
                                ColorRow(
                                    label = s.label,
                                    color = s.read(draft),
                                    onClick = { colorSlot = s },
                                )
                            }
                        }

                        SectionLabel(text = "卡片背景")
                        GlassInset(modifier = Modifier.fillMaxWidth()) {
                            SettingRow(
                                label = "背景图",
                                value = if (draft.cardImage == null) "无" else "已设置",
                                onClick = { cardBackdrop = true },
                                showChevron = true,
                                hint = "只影响这一条事件的卡片，不是整个 App 的背景",
                            )
                        }

// ── 提醒 ────────────────────────────────────
                        SectionLabel(text = "提醒")
                        GlassInset(modifier = Modifier.fillMaxWidth()) {
                            ToggleRow(
                                label = "提醒",
                                on = draft.remind,
                                onChange = { draft = draft.copy(remind = it) },
                            )
                            if (draft.remind) {
                                InsetLine()
                                SettingRow(
                                    label = "提醒时间",
                                    value = timeText(draft.remindHour, draft.remindMinute),
                                    onClick = { pickTime = true },
                                    showChevron = true,
                                )
                                InsetLine()
                                SettingRow(
                                    label = "频率",
                                    value = draft.remindRule.effective(draft.mode).label,
                                    onClick = { pickRemindRule = true },
                                    showChevron = true,
                                    hint = remindHint(
                                        draft.mode,
                                        draft.remindRule.effective(draft.mode),
                                        remindWeekday,
                                        remindMonthDay,
                                    ),
                                )
                                when (draft.remindRule.effective(draft.mode)) {
                                    RemindRule.WEEKLY -> {
                                        InsetLine()
                                        SettingRow(
                                            label = "星期",
                                            value = CountdownEvent.weekdayName(remindWeekday).orEmpty(),
                                            onClick = { pickWeekday = true },
                                            showChevron = true,
                                            hint = "默认跟目标日同一天，点开可以改",
                                        )
                                    }
                                    RemindRule.MONTHLY -> {
                                        InsetLine()
                                        SettingRow(
                                            label = "日期",
                                            value = "${remindMonthDay} 号",
                                            onClick = { pickMonthDay = true },
                                            showChevron = true,
                                            hint = "选 31 号时，只有 30 天的月份落在月末",
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                        }

                        SectionLabel(text = "时间胶囊")
                        GlassInset(modifier = Modifier.fillMaxWidth()) {
                            ToggleRow(
                                label = "启用",
                                on = draft.capsule,
                                onChange = { draft = draft.copy(capsule = it) },
                                hint = "到日子之前，正文对你自己也隐藏",
                            )
                        }

                        if (draft.capsule) {
                            Spacer(Modifier.height(12.dp))
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Radius.panel),
                                weight = 0.5f,
                            ) {
                                BlockInput(
                                    value = draft.capsuleText,
                                    placeholder = "写给那一天的话",
                                    onChange = { draft = draft.copy(capsuleText = it) },
                                )
                            }
                        }

                        SectionLabel(text = "备注")
                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.panel),
                            weight = 0.5f,
                        ) {
                            BlockInput(
                                value = draft.note,
                                placeholder = "备注",
                                onChange = { draft = draft.copy(note = it) },
                            )
                        }

                        if (!isNew) {
                            Spacer(Modifier.height(20.dp))
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Radius.panel),
                                weight = 0.5f,
                            ) {
                                SettingRow(
                                    label = "删除这个事件",
                                    onClick = { confirmDelete = true },
                                    destructive = true,
                                )
                            }
                        }

                        Spacer(Modifier.height(56.dp))
                    }
                }
            }
        }
    }

    if (pickDate) {
        DatePickDialog(
            initial = draft.anchor,
            onPick = { picked ->
                draft = draft.copy(
                    anchor = picked,
                    endDate = draft.endDate?.let { if (it.isBefore(picked)) picked else it },
                )
            },
            onDismiss = { pickDate = false },
        )
    }
    if (pickEnd) {
        DatePickDialog(
            initial = draft.endDate ?: draft.anchor.plusDays(30),
            onPick = { draft = draft.copy(endDate = it) },
            onDismiss = { pickEnd = false },
            onClear = { draft = draft.copy(endDate = null) },
// 结束日不能早于起始日：那些日子直接在日历上点不动
            minDate = draft.anchor,
        )
    }
    if (pickTime) {
        TimePickDialog(
            hour = draft.remindHour,
            minute = draft.remindMinute,
            onPick = { h, m -> draft = draft.copy(remindHour = h, remindMinute = m) },
            onDismiss = { pickTime = false },
        )
    }
    if (pickRepeat) {
        ChoiceDialog(
            title = "重复",
            options = RepeatRule.entries.map { it.label },
            selected = draft.repeat.label,
            onPick = { label ->
                RepeatRule.entries.firstOrNull { it.label == label }?.let {
                    draft = draft.copy(repeat = it)
                }
            },
            onDismiss = { pickRepeat = false },
        )
    }
    if (pickRemindRule) {
// 选项随模式变：计时没有"当天"可言，给了也只会让用户以为坏了。
        val options = RemindRule.options(draft.mode)
        ChoiceDialog(
            title = "提醒频率",
            options = options.map { it.label },
            selected = draft.remindRule.effective(draft.mode).label,
            onPick = { label ->
                options.firstOrNull { it.label == label }?.let {
                    draft = draft.copy(remindRule = it)
                }
            },
            onDismiss = { pickRemindRule = false },
        )
    }
    if (pickWeekday) {
        ChoiceDialog(
            title = "每周几",
            options = CountdownEvent.WEEKDAY_NAMES,
            selected = CountdownEvent.weekdayName(remindWeekday),
            onPick = { label ->
                val i = CountdownEvent.WEEKDAY_NAMES.indexOf(label)
                if (i >= 0) draft = draft.copy(remindWeekday = i + 1)
            },
            onDismiss = { pickWeekday = false },
        )
    }
    if (pickMonthDay) {
        NumberGridDialog(
            title = "每月几号",
            values = (1..31).toList(),
            selected = remindMonthDay,
            onPick = { draft = draft.copy(remindDayOfMonth = it) },
            onDismiss = { pickMonthDay = false },
            subtitle = "只有 30 天的月份，31 号落在月末",
        )
    }
    if (pickCategory) {
        val options = (CountdownEvent.CATEGORY_PRESETS + categories).distinct()
        ChoiceDialog(
            title = "分类",
            options = options + NEW_CATEGORY,
            selected = draft.category,
            onPick = { picked ->
                if (picked == NEW_CATEGORY) newCategory = true
                else draft = draft.copy(category = picked)
            },
            onDismiss = { pickCategory = false },
        )
    }
    if (newCategory) {
        TextPromptDialog(
            title = "新建分类",
            initial = "",
            placeholder = "比如 健身",
            onConfirm = { name ->
                if (name.isNotBlank()) draft = draft.copy(category = name)
                newCategory = false
            },
            onDismiss = { newCategory = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "删除「${draft.title.ifBlank { "未命名" }}」？",
            message = "这条记录和它的提醒会一起消失，不能撤销。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete(draft.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "放弃这次的修改？",
            message = "改了的内容不会保存。",
            confirmText = "放弃",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun BlockInput(value: String, placeholder: String, onChange: (String) -> Unit) {
    val g = LocalGlass.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = g.ink),
        cursorBrush = SolidColor(g.accent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .padding(horizontal = RowInset, vertical = 16.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = g.inkSoft,
                    )
                }
                inner()
            }
        },
    )
}

private fun timeText(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun dateText(d: LocalDate): String = "${d.year}年${d.monthValue}月${d.dayOfMonth}日"

private fun remindHint(
    mode: EventMode,
    rule: RemindRule,
    weekday: Int,
    monthDay: Int,
): String? {
    val step = when (rule) {
        RemindRule.ONCE -> return null
        RemindRule.DAILY -> "每天"
        RemindRule.WEEKLY -> "每${CountdownEvent.weekdayName(weekday) ?: "周"}"
        RemindRule.MONTHLY -> "每月 ${monthDay} 号"
    }
    return if (mode == EventMode.ELAPSED) {
        "${step}这个时间提醒一次，从今天往后数"
    } else {
        "${step}这个时间提醒一次，目标日当天也会响"
    }
}

