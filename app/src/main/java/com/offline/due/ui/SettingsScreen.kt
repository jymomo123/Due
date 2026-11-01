package com.offline.due.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offline.due.data.Backup
import com.offline.due.data.BgTone
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventCodec
import com.offline.due.data.EventStore
import com.offline.due.data.RowDensity
import com.offline.due.data.Settings
import com.offline.due.data.SettingsStore
import com.offline.due.data.TextScale
import com.offline.due.data.ThemeMode
import com.offline.due.domain.CardNumberMode
import com.offline.due.net.TimeSync
import com.offline.due.ui.components.CardInset
import com.offline.due.ui.components.ChoiceDialog
import com.offline.due.ui.components.GlassDialog
import com.offline.due.ui.components.GlassInset
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.InsetLine
import com.offline.due.ui.components.KeyBar
import com.offline.due.ui.components.KeyCap
import com.offline.due.ui.components.SectionLabel
import com.offline.due.ui.components.SettingRow
import com.offline.due.ui.components.TextPromptDialog
import com.offline.due.ui.components.ToggleRow
import com.offline.due.ui.components.pressable
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import com.offline.due.widget.TodayWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    events: List<CountdownEvent>,
    settings: Settings,
    onBack: () -> Unit,
    onDataChanged: () -> Unit,

    onOpenCustomize: () -> Unit,

    onOpenAbout: () -> Unit,

    onOpenBackdrop: () -> Unit,
) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(status) {
        if (status == null) return@LaunchedEffect
        delay(3200)
        status = null
    }
    var pickTheme by remember { mutableStateOf(false) }
    var pickTone by remember { mutableStateOf(false) }
    var pickTitle by remember { mutableStateOf(false) }
    var pickWidgetEvent by remember { mutableStateOf(false) }
    var pickScale by remember { mutableStateOf(false) }
    var pickDensity by remember { mutableStateOf(false) }
    var pickCardMode by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var skew by remember { mutableStateOf<String?>(null) }

// 版本号不再在这一页读：它跟着「关于如期」搬到 [AboutScreen] 了。

    val widgetEventLabel = remember(settings.widgetEventId, events) {
        settings.widgetEventId
            ?.let { id -> events.firstOrNull { it.id == id }?.title }
            ?: "自动"
    }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Backup.exportJson(events).toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                }.isSuccess
            }
            status = if (ok) "已导出 ${events.size} 条" else "导出失败"
        }
    }

    val exportIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Backup.exportIcs(events).toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                }.isSuccess
            }
            status = if (ok) "已导出 .ics" else "导出失败"
        }
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: ""
                    val parsed = EventCodec.decodeStrict(text) ?: return@runCatching null
                    EventStore.mergeIncoming(parsed) to parsed.size
                }.getOrNull()
            }
            if (result == null) {
                status = "文件无法解析"
            } else {
                onDataChanged()
                status = "新增 ${result.first} 条（文件共 ${result.second} 条）"
            }
        }
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
                    .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 12.dp),
            ) {
                KeyCap(text = "返回", onClick = onBack)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalGlass.current.inkMid,
                )
            }

            Column(
// 大屏上限宽 560dp 后居中；小屏上就是原来的全宽
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .contentWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Gutter),
            ) {
                Spacer(Modifier.height(12.dp))
                GlassInset(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        label = "可视化自定义",
                        onClick = onOpenCustomize,
                        showChevron = true,
                    )
                }

                SectionLabel("外观")
                GlassInset(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        label = "主题",
                        value = settings.themeMode.label,
                        onClick = { pickTheme = true },
                        showChevron = true,
                    )
                    InsetLine()
                    SettingRow(
                        label = "配色",
                        value = settings.bgTone.label,
                        onClick = { pickTone = true },
                        showChevron = true,
                    )
                    InsetLine()
                    SettingRow(
                        label = "整屏背景",
                        value = settings.bgStyle.label,
                        onClick = onOpenBackdrop,
                        showChevron = true,
                    )
                    InsetLine()
                    ToggleRow(
                        label = "列表卡片",
                        on = settings.listCardStyle,
                        onChange = { SettingsStore.setListCardStyle(it) },
                    )
                    InsetLine()
                    SettingRow(
                        label = "列表密度",
                        value = settings.rowDensity.label,
                        onClick = { pickDensity = true },
                        showChevron = true,
                    )
                    InsetLine()
                    SettingRow(
                        label = "字号",
                        value = settings.textScale.label,
                        onClick = { pickScale = true },
                        showChevron = true,
                    )
                }

                SectionLabel("首页")
                GlassInset(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        label = "标题",
                        value = settings.headerTitle,
                        onClick = { pickTitle = true },
                        showChevron = true,
                    )
                    InsetLine()
                    ToggleRow(
                        label = "今年还剩",
                        on = settings.headerCountdown,
                        onChange = { SettingsStore.setHeaderCountdown(it) },
                    )
                    InsetLine()
                    ToggleRow(
                        label = "节气与节日",
                        on = settings.headerAlmanac,
                        onChange = { SettingsStore.setHeaderAlmanac(it) },
                    )
                    InsetLine()
                    ToggleRow(
                        label = "年度进度线",
                        on = settings.yearHeader,
                        onChange = { SettingsStore.setYearHeader(it) },
                    )
                    InsetLine()
                    ToggleRow(
                        label = "搜索",
                        on = settings.headerSearch,
                        onChange = { SettingsStore.setHeaderSearch(it) },
                    )
                    InsetLine()
                    ToggleRow(
                        label = "快速添加",
                        on = settings.quickAdd,
                        onChange = { SettingsStore.setQuickAdd(it) },
                    )
                    RevealRow(visible = settings.quickAdd, key = "quickAddHintRow") {
                        InsetLine()
                        ToggleRow(
                            label = "示例文案",
                            on = settings.quickAddHint,
                            onChange = { SettingsStore.setQuickAddHint(it) },
                        )
                    }
                    InsetLine()
                    SettingRow(
                        label = "默认视图",
                        value = settings.defaultCardMode.label,
                        onClick = { pickCardMode = true },
                        showChevron = true,
                    )
                }

                SectionLabel("桌面小组件")
                GlassInset(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        label = "显示的事件",
                        value = widgetEventLabel,
                        onClick = { pickWidgetEvent = true },
                        showChevron = true,
                    )
                }

                SectionLabel("通知")
                GlassInset(modifier = Modifier.fillMaxWidth()) {
                    ToggleRow(
                        label = "事件提醒",
                        on = settings.reminderAlert,
                        onChange = { SettingsStore.setReminderAlert(it) },
                    )
                    RevealRow(visible = settings.reminderAlert, key = "ongoingRow") {
                        InsetLine()
                        ToggleRow(
                            label = "常驻倒数",
                            on = settings.ongoingNotification,
                            onChange = { SettingsStore.setOngoing(it) },
                        )
                    }
                }

                SectionLabel("数据")
                GlassInset(modifier = Modifier.fillMaxWidth()) {
                    SettingRow("导出备份", "JSON", onClick = {
                        exportJson.launch(Backup.suggestedFileName("due"))
                    }, showChevron = true)
                    InsetLine()
                    SettingRow("导入备份", onClick = {
                        importJson.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }, showChevron = true)
                    InsetLine()
                    SettingRow("导出日历", "ICS", onClick = {
                        exportIcs.launch(Backup.suggestedFileName("due"))
                    }, showChevron = true)
                }

                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = g.accent,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                    )
                }

                SectionLabel("关于")
                GlassInset(modifier = Modifier.fillMaxWidth()) {
// 全 App 唯一会联网的地方。刻意做得只读、只提示。
                    SettingRow(
                        label = "网络校时",
                        value = when {
                            probing -> "检测中…"
                            skew != null -> skew!!
                            else -> "未检测"
                        },
                        onClick = {
                            if (!probing) {
                                scope.launch {
                                    probing = true
                                    val probe = TimeSync.probe()
                                    probing = false
                                    skew = probe
                                        ?.let { TimeSync.describe(it.skewMillis) }
                                        ?: "无法连接（不影响使用）"
                                }
                            }
                        },
                        showChevron = true,
                    )
                    InsetLine()
                    SettingRow(
                        label = "关于如期",
                        onClick = onOpenAbout,
                        showChevron = true,
                    )
                }

                SectionLabel("重置")
                GlassInset(modifier = Modifier.fillMaxWidth()) {
                    SettingRow(
                        label = "清空所有数据",
                        onClick = { confirmClear = true },
                        destructive = true,
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (pickTheme) {
        ChoiceDialog(
            title = "主题",
            options = ThemeMode.entries.map { it.label },
            selected = settings.themeMode.label,
            onPick = { label ->
                ThemeMode.entries.firstOrNull { it.label == label }?.let {
                    SettingsStore.setTheme(it)
                }
            },
            onDismiss = { pickTheme = false },
        )
    }

    if (pickTone) {
        ChoiceDialog(
            title = "配色",
            options = BgTone.entries.map { it.label },
            selected = settings.bgTone.label,
            onPick = { label ->
                BgTone.entries.firstOrNull { it.label == label }?.let {
                    SettingsStore.setBgTone(it)
                }
            },
            onDismiss = { pickTone = false },
        )
    }

    if (pickDensity) {
        ChoiceDialog(
            title = "列表密度",
            options = RowDensity.entries.map { it.label },
            selected = settings.rowDensity.label,
            onPick = { label ->
                RowDensity.entries.firstOrNull { it.label == label }?.let {
                    SettingsStore.setRowDensity(it)
                }
            },
            onDismiss = { pickDensity = false },
        )
    }

    if (pickScale) {
        ChoiceDialog(
            title = "字号",
            options = TextScale.entries.map { it.label },
            selected = settings.textScale.label,
            onPick = { label ->
                TextScale.entries.firstOrNull { it.label == label }?.let {
                    SettingsStore.setTextScale(it)
                }
            },
            onDismiss = { pickScale = false },
        )
    }

    if (pickCardMode) {
        ChoiceDialog(
            title = "默认视图",
            options = CardNumberMode.entries.map { it.label },
            selected = settings.defaultCardMode.label,
            subtitle = "进去之后点数字照样能切，这里只是每次进来先显示的那一档",
            onPick = { label ->
                CardNumberMode.entries.firstOrNull { it.label == label }?.let {
                    SettingsStore.setDefaultCardMode(it)
                }
            },
            onDismiss = { pickCardMode = false },
        )
    }

    if (confirmClear) {
        ChoiceDialog(
            title = "所有事件将被永久删除",
            options = listOf("清空"),
            selected = null,
            onPick = {
                EventStore.clearAll()
                onDataChanged()
                status = "已清空"
            },
            onDismiss = { confirmClear = false },
        )
    }

    if (pickWidgetEvent) {
        WidgetEventDialog(
            events = events,
            selectedId = settings.widgetEventId,
            onPick = { id ->
                SettingsStore.setWidgetEvent(id)
                TodayWidgetProvider.refreshAll(context)
            },
            onDismiss = { pickWidgetEvent = false },
        )
    }

    if (pickTitle) {
        TextPromptDialog(
            title = "顶栏标题",
            initial = settings.headerTitle,
            placeholder = "如期",
            onConfirm = {
                SettingsStore.setHeaderTitle(it)
                pickTitle = false
            },
            onDismiss = { pickTitle = false },
// 8 个字：顶栏第一行只有 22sp，再长就要吃掉右边的「搜索」「⋯」
            limit = 8,
        )
    }
}

@Composable
private fun RevealRow(
    visible: Boolean,
    key: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(Motion.Enter, easing = Motion.enterEasing),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(Motion.Enter)),
        exit = shrinkVertically(
            animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(tween(Motion.Exit)),
        label = key,
    ) {
        Column(content = content)
    }
}

@Composable
private fun WidgetEventDialog(
    events: List<CountdownEvent>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
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
                    text = "小组件显示的事件",
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkSoft,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    WidgetEventRow(
                        label = "自动",
                        on = selectedId == null,
                    ) {
                        close { onPick(null); onDismiss() }
                    }
                    events.forEach { event ->
                        WidgetEventRow(
                            label = event.title,
                            on = event.id == selectedId,
                        ) {
                            close { onPick(event.id); onDismiss() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetEventRow(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.chip))
            .pressable(onClick, scale = 1f, specular = false)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(15.dp)
                .background(
                    if (on) g.accent else Color.Transparent,
                    RoundedCornerShape(Radius.hair),
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (on) g.ink else g.inkMid,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

