package com.offline.due.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offline.due.data.BgTone
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventMode
import com.offline.due.data.RepeatRule
import com.offline.due.data.RowDensity
import com.offline.due.data.Settings
import com.offline.due.data.SettingsStore
import com.offline.due.data.TextScale
import com.offline.due.data.ThemeMode
import com.offline.due.domain.CountdownInfo
import com.offline.due.domain.CountdownMath
import com.offline.due.ui.components.EventRow
import com.offline.due.ui.components.GlassInset
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.InsetLine
import com.offline.due.ui.components.RowInset
import com.offline.due.ui.components.SegmentedChoice
import com.offline.due.ui.components.TextAction
import com.offline.due.ui.components.ToggleRow
import com.offline.due.ui.components.pageBaseColor
import com.offline.due.ui.components.pressable
import com.offline.due.ui.components.rememberSubPageBack
import com.offline.due.ui.components.subPageSlide
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.glassFor
import java.time.LocalDate

@Composable
fun OnboardingScreen(
    settings: Settings,

    firstRun: Boolean = true,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = STEP_COUNT - 1
    val today = rememberToday()
    val samples = remember(today) { sampleRows(today) }

    val stepBack = rememberSubPageBack(
        enabled = firstRun && step > 0,
        onClose = { step-- },
    )

    val stepTransition: AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
        if (stepBack.driven) {
            fadeIn(tween(Motion.Settle, easing = Motion.handover))
                .togetherWith(fadeOut(tween(Motion.Settle, easing = Motion.handover)))
        } else {
            val dir = if (targetState > initialState) 1 else -1
            (
                slideInHorizontally(tween(Motion.Page, easing = Motion.enterEasing)) { it / 5 * dir } +
                    fadeIn(tween(Motion.Enter, easing = Motion.enterEasing))
                ).togetherWith(
                slideOutHorizontally(tween(Motion.Exit, easing = Motion.exitEasing)) { -it / 5 * dir } +
                    fadeOut(tween(Motion.Exit, easing = Motion.exitEasing))
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Gutter),
    ) {
        StepBar(
            step = step,
            last = last,
            exitLabel = if (firstRun) "跳过" else "退出",
            onExit = onDone,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "预览",
            style = MaterialTheme.typography.labelSmall,
            color = LocalGlass.current.inkSoft,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )

        HomePreview(
            settings = settings,
            rows = samples,
            modifier = Modifier
                .weight(PREVIEW_WEIGHT)
                .fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(STEP_WEIGHT)
                .fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                transitionSpec = stepTransition,
                label = "onboardStep",
            ) { s ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .subPageSlide(stepBack, fraction = 0.2f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (s) {
                        0 -> ColorStep(settings)
                        1 -> TypeStep(settings)
                        2 -> HomeStep(settings)
                        else -> DoneStep(settings, firstRun)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        StepFooter(
            step = step,
            last = last,
            doneLabel = if (firstRun) "开始使用" else "完成",
            onBack = { if (step > 0) step-- },
            onNext = { if (step >= last) onDone() else step++ },
        )
    }
}

private const val STEP_COUNT = 4

private const val PREVIEW_WEIGHT = 1f
private const val STEP_WEIGHT = 1.25f

@Composable
private fun StepBar(step: Int, last: Int, exitLabel: String, onExit: () -> Unit) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(STEP_COUNT) { i ->
            val active = i == step
            val w by animateDpAsState(
                targetValue = if (active) 22.dp else 7.dp,
                animationSpec = Motion.press(),
                label = "dotWidth",
            )
            val c by animateColorAsState(
                targetValue = if (active) g.accent else g.ink.copy(alpha = 0.14f),
                animationSpec = tween(Motion.Enter, easing = Motion.enterEasing),
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .height(6.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(c),
            )
        }
        Spacer(Modifier.weight(1f))
        if (step < last) {
            TextAction(
                text = exitLabel,
                onClick = onExit,
                color = g.inkSoft,
            )
        } else {
            Spacer(Modifier.height(44.dp))
        }
    }
}

@Composable
private fun StepFooter(
    step: Int,
    last: Int,
    doneLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 0) {
            TextAction(text = "上一步", onClick = onBack, color = g.inkSoft)
        }
        Spacer(Modifier.weight(1f))
        GlassPanel(
            modifier = Modifier
                .height(50.dp)
                .pressable(onNext, scale = 0.96f),
            shape = RoundedCornerShape(Radius.panel),
            weight = 1f,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (step >= last) doneLabel else "下一步",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = g.accent,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalGlass.current.inkMid,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 8.dp),
    )
}

private const val PREVIEW_PANEL_WEIGHT = 0.55f

@Composable
private fun HomePreview(
    settings: Settings,
    rows: List<Pair<CountdownEvent, CountdownInfo>>,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .clearAndSetSemantics { },
    ) {
        GlassPanel(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(Radius.card),
            weight = PREVIEW_PANEL_WEIGHT,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PreviewHeader(settings)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = if (settings.quickAdd) 0.dp else 60.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                    ) {
                        rows.forEach { (event, info) ->
                            EventRow(
                                event = event,
                                info = info,
                                onClick = {},
                                horizontalPadding = RowInset,
                                showCardLook = settings.listCardStyle,
                                rowDensity = settings.rowDensity,
                            )
                        }
                    }
                    val pageBase = pageBaseColor(settings.bgStyle, Color(settings.bgColor))
                    val fillAlpha = (
                        g.surfaceBottom.alpha * (0.6f + PREVIEW_PANEL_WEIGHT * 0.5f)
                        ).coerceIn(0f, 1f)
                    val fade = g.surfaceBottom.copy(alpha = fillAlpha).compositeOver(pageBase)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to fade.copy(alpha = 0f),
                                    0.5f to fade.copy(alpha = 0.5f),
                                    1f to fade,
                                )
                            ),
                    )
                }
                if (settings.quickAdd) {
                    PreviewQuickAdd(showHint = settings.quickAddHint)
                }
            }
        }
        if (!settings.quickAdd) {
            AddBall(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(RowInset),
                ballSize = 40.dp,
            )
        }
    }
}

@Composable
private fun PreviewHeader(settings: Settings) {
    val g = LocalGlass.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(
                start = RowInset,
                end = RowInset,
                top = 14.dp,
                bottom = if (settings.headerCountdown || settings.headerAlmanac) 4.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = settings.headerTitle,
                style = MaterialTheme.typography.titleLarge,
                color = g.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (settings.headerSearch) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.labelMedium,
                    color = g.inkMid,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                )
            }
            Text(
                text = "⋯",
                style = MaterialTheme.typography.labelMedium,
                color = g.inkMid,
            )
        }
        if (settings.headerCountdown || settings.headerAlmanac) {
            val parts = buildList {
                if (settings.headerCountdown) add("今年还剩 66 天")
                if (settings.headerAlmanac) add("今天霜降")
            }
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = g.inkMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = RowInset, end = RowInset, bottom = 12.dp),
            )
        }
        if (settings.yearHeader) {
// 年度进度线：2dp，走多少算多少。这里画到 82%，比"满格"更像真的。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(g.accent.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .fillMaxHeight()
                        .background(g.accent.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

@Composable
private fun PreviewQuickAdd(showHint: Boolean) {
    val g = LocalGlass.current
    GlassInset(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset, vertical = 10.dp),
        shape = RoundedCornerShape(Radius.panel),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showHint) QuickHints.text else "",
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "＋",
                style = MaterialTheme.typography.titleMedium,
                color = g.accent,
            )
        }
    }
}

@Composable
private fun ColorStep(settings: Settings) {
    val g = LocalGlass.current
    val dark = g.isDark

    FieldLabel("配色")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BgTone.entries.forEach { tone ->
            val picked = settings.bgTone == tone
            val toneGlass = glassFor(dark, tone)
            val shape = RoundedCornerShape(Radius.panel)
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .then(
                            if (dark) Modifier else Modifier.shadow(
                                elevation = 10.dp,
                                shape = shape,
                                clip = false,
                                ambientColor = toneGlass.ink.copy(alpha = 0.10f),
                                spotColor = toneGlass.ink.copy(alpha = 0.20f),
                            )
                        )
                        .clip(shape)
                        .background(Brush.verticalGradient(toneGlass.gradient))
                        .border(
                            width = if (picked) 2.dp else 1.dp,
                            color = if (picked) g.accent else toneGlass.edgeLight,
                            shape = shape,
                        )
                        .pressable({ SettingsStore.setBgTone(tone) }, scale = 0.96f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.56f)
                            .height(34.dp)
// 同上：深色主题下这圈投影会变成白色光晕。
                            .then(
                                if (dark) Modifier else Modifier.shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(Radius.chip),
                                    clip = false,
                                    ambientColor = toneGlass.ink.copy(alpha = 0.12f),
                                    spotColor = toneGlass.ink.copy(alpha = 0.22f),
                                )
                            )
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(
                                Brush.verticalGradient(
                                    listOf(toneGlass.surfaceTop, toneGlass.surfaceBottom),
                                )
                            )
                            .border(1.dp, toneGlass.edgeLight, RoundedCornerShape(Radius.chip)),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 9.dp, start = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tone.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (picked) g.ink else g.inkMid,
                    )
                    if (picked) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(g.accent),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(22.dp))

    FieldLabel("深浅")
    SegmentedChoice(
        options = ThemeMode.entries.map { it.label },
        selected = settings.themeMode.label,
        onPick = { label ->
            ThemeMode.entries.firstOrNull { it.label == label }?.let {
                SettingsStore.setTheme(it)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeStep(settings: Settings) {
    FieldLabel("字号")
    SegmentedChoice(
        options = TextScale.entries.map { it.label },
        selected = settings.textScale.label,
        onPick = { label ->
            TextScale.entries.firstOrNull { it.label == label }?.let {
                SettingsStore.setTextScale(it)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(22.dp))

    FieldLabel("列表密度")
    SegmentedChoice(
        options = RowDensity.entries.map { it.label },
        selected = settings.rowDensity.label,
        onPick = { label ->
            RowDensity.entries.firstOrNull { it.label == label }?.let {
                SettingsStore.setRowDensity(it)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeStep(settings: Settings) {
    FieldLabel("首页上有什么")

    GlassInset(modifier = Modifier.fillMaxWidth()) {
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
        InsetLine()
        ToggleRow(
            label = "列表卡片",
            on = settings.listCardStyle,
            onChange = { SettingsStore.setListCardStyle(it) },
        )
    }
}

@Composable
private fun DoneStep(settings: Settings, firstRun: Boolean) {
    val g = LocalGlass.current

    val onCount = listOf(
        settings.headerCountdown,
        settings.headerAlmanac,
        settings.yearHeader,
        settings.headerSearch,
        settings.quickAdd,
        settings.listCardStyle,
    ).count { it }

    FieldLabel("你的选择")
    GlassInset(modifier = Modifier.fillMaxWidth()) {
        SummaryLine("配色", settings.bgTone.label)
        InsetLine()
        SummaryLine("深浅", settings.themeMode.label)
        InsetLine()
        SummaryLine("字号", settings.textScale.label)
        InsetLine()
        SummaryLine("列表密度", settings.rowDensity.label)
        InsetLine()
        SummaryLine("首页显示", "已开 $onCount 项")
    }

    Text(
        text = if (firstRun) {
            "写下第一条，比如「下周五 交房租」"
        } else {
            "每一步都是立刻生效的，退出也不会还原"
        },
        style = MaterialTheme.typography.bodySmall,
        color = g.inkMid,
        modifier = Modifier.padding(top = 16.dp, start = 2.dp, end = 2.dp),
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = g.inkMid,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = g.ink,
        )
    }
}

private fun sampleRows(today: LocalDate): List<Pair<CountdownEvent, CountdownInfo>> {
    val specs = listOf(
        Triple("妈妈生日", 18L, "家人"),
        Triple("项目上线", 37L, "工作"),
        Triple("考研初试", 416L, "学习"),
        Triple("还书", 8L, "生活"),
    )
    val colors = CountdownEvent.COLOR_PRESETS
    return specs.mapIndexed { i, (title, days, category) ->
        val event = CountdownEvent(
            title = title,
            anchor = today.plusDays(days),
            mode = EventMode.COUNTDOWN,
            repeat = if (i == 0) RepeatRule.YEARLY else RepeatRule.NONE,
            category = category,
            color = colors[(i * 3) % colors.size],
        )
        event to CountdownMath.occurrence(event, today)
    }
}

