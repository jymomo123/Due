package com.offline.due.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.offline.due.data.CountdownEvent
import com.offline.due.data.RowDensity
import com.offline.due.domain.Almanac
import com.offline.due.domain.CountdownInfo
import com.offline.due.domain.CountdownMath
import com.offline.due.domain.NaturalDateParser
import com.offline.due.ui.components.ChoiceDialog
import com.offline.due.ui.components.EmptyState
import com.offline.due.ui.components.EventRow
import com.offline.due.ui.components.FilterChips
import com.offline.due.ui.components.GlassDivider
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.RowInset
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.TextAction
import com.offline.due.ui.components.pressable
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import com.offline.due.ui.theme.rememberWideLayout
import kotlin.math.abs
import kotlinx.coroutines.delay

data class HomeLocate(

    val category: String?,

    val index: Int,

    val offset: Int,

    val search: Boolean = false,

    val query: String = "",
)

@Composable
fun HomeScreen(
    events: List<CountdownEvent>,

    headerTitle: String,

    headerCountdown: Boolean,

    headerAlmanac: Boolean,

    headerSearch: Boolean,

    yearHeader: Boolean,

    listCardStyle: Boolean,

    quickAdd: Boolean,

    quickAddHint: Boolean,

    rowDensity: RowDensity,

    onOpenEvent: (String, Rect?, HomeLocate) -> Unit,

    onQuickAdd: (CountdownEvent) -> Unit,

    onDraft: (CountdownEvent) -> Unit,

    onNewEvent: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,

    locate: HomeLocate? = null,

    hiddenRowId: String? = null,
) {
    val today = rememberToday()
    val wide = rememberWideLayout()
    var menuOpen by remember { mutableStateOf(false) }
    var searchMode by rememberSaveable { mutableStateOf(locate?.search ?: false) }
    var query by rememberSaveable { mutableStateOf(locate?.query ?: "") }
    var categoryFilter by rememberSaveable { mutableStateOf(locate?.category) }

    val focusManager = LocalFocusManager.current

    var lastAdded by remember { mutableStateOf<String?>(null) }

    val openSearch = {
        categoryFilter = null
        searchMode = true
    }
    val closeSearch = {
        searchMode = false
        query = ""
        focusManager.clearFocus()
    }

    LaunchedEffect(headerSearch) {
        if (!headerSearch && searchMode) closeSearch()
    }

    val anySeconds = remember(events) { events.any { it.showSeconds } }
    val nowState = rememberNowState(anySeconds)

    val almanacText = remember(today, headerAlmanac, headerCountdown) {
        if (!headerAlmanac) return@remember null
        val a = Almanac.todayInfo(today)
        val mark = a.mark
        val next = a.next
        val detailed = !headerCountdown
        when {
            mark != null && next != null ->
                if (detailed) {
                    "今天 $mark · 距${next.name} ${a.daysToNext} 天"
                } else {
                    "今天 $mark"
                }
            mark != null -> "今天 $mark"
            next != null -> "距${next.name} ${a.daysToNext} 天"
            else -> null
        }
    }

    val categoryCounts = remember(events) {
        val counts = events.groupingBy { it.category }.eachCount()
        counts.entries
            .sortedWith(
                compareBy(
                    { entry ->
                        val i = CountdownEvent.CATEGORY_PRESETS.indexOf(entry.key)
                        if (i < 0) Int.MAX_VALUE else i
                    },
                    { it.key },
                )
            )
            .map { it.key to it.value }
    }

    val activeCategory = categoryFilter?.takeIf { name -> categoryCounts.any { it.first == name } }

    val pages = remember(categoryCounts) {
        if (categoryCounts.size > 1) {
            listOf<String?>(null) + categoryCounts.map { it.first }
        } else {
            listOf<String?>(null)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(locate?.category ?: activeCategory).coerceAtLeast(0),
    ) { pages.size }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val want = pages.getOrNull(page)
            if (want != categoryFilter) categoryFilter = want
        }
    }

// 状态 → 页。点筛选条走这条；滑页走上面那条，这里会看到两边已经一致，什么也不做。
    LaunchedEffect(activeCategory, pages) {
        if (locate != null) return@LaunchedEffect
        val target = pages.indexOf(activeCategory).coerceAtLeast(0)
        val from = pagerState.currentPage
        if (from == target) return@LaunchedEffect
        if (abs(target - from) > 1) {
            pagerState.scrollToPage(target)
        } else {
            pagerState.animateScrollToPage(target)
        }
    }

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    CompositionLocalProvider(LocalNow provides nowState) { IceBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            Header(
                title = headerTitle,
                daysLeft = if (headerCountdown) CountdownMath.daysLeftInYear(today) else null,
                progress = CountdownMath.yearProgress(today),
                almanacText = almanacText,
                showProgress = yearHeader,
                searchMode = searchMode,
                showSearch = headerSearch,
                compact = wide,
                onMenu = { menuOpen = true },
                onToggleSearch = {
                    if (searchMode) closeSearch() else openSearch()
                },
            )

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (wide && categoryCounts.size > 1) {
                    Column(
                        modifier = Modifier
                            .width(132.dp)
                            .fillMaxHeight()
                            .padding(start = Gutter, top = 4.dp, bottom = 4.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        FilterChips(
                            options = categoryCounts,
                            selected = activeCategory,
                            onPick = { categoryFilter = it },
                            vertical = true,
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .contentWidth()
                            .fillMaxHeight()
                            .padding(
                                start = if (wide && categoryCounts.size > 1) 12.dp else Gutter,
                                end = Gutter,
                                bottom = if (imeBottom > 0) 16.dp else 0.dp,
                            ),
                    ) {
                        AnimatedVisibility(
                            visible = searchMode,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = tween(Motion.Enter, easing = Motion.enterEasing),
                            ) + fadeIn(tween(Motion.Enter, easing = Motion.enterEasing)),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
                            ) + fadeOut(tween(Motion.Exit, easing = Motion.exitEasing)),
                        ) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                SearchField(query) { query = it }
                            }
                        }

                        if (!wide && categoryCounts.size > 1) {
                            Spacer(Modifier.height(14.dp))
                            FilterChips(
                                options = categoryCounts,
                                selected = activeCategory,
                                onPick = { categoryFilter = it },
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            GlassPanel(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(Radius.card),
                                weight = 0.45f,
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    pageSpacing = 0.dp,
                                ) { page ->
                                    val pageCat = pages.getOrNull(page)
                                    val rows = remember(events, query, today, pageCat) {
                                        val inCat = if (pageCat == null) {
                                            events
                                        } else {
                                            events.filter { it.category == pageCat }
                                        }
                                        val matched = if (query.isBlank()) {
                                            inCat
                                        } else {
                                            inCat.filter {
                                                it.title.contains(query, ignoreCase = true) ||
                                                    it.note.contains(query, ignoreCase = true) ||
                                                    it.category.contains(query, ignoreCase = true)
                                            }
                                        }
// 排序时顺手把 CountdownInfo 算出来带走，渲染时不用再算
                                        CountdownMath.displayList(matched, today)
                                    }

                                    CategoryPage(
                                        rows = rows,
                                        lastAdded = lastAdded,
                                        isCurrent = page == pagerState.settledPage,
                                        onAddedShown = { lastAdded = null },
                                        listCardStyle = listCardStyle,
                                        rowDensity = rowDensity,
                                        locate = if (locate != null && pageCat == locate.category) locate else null,
                                        freshState = locate != null,
                                        hiddenRowId = hiddenRowId,
                                        bottomInset = if (quickAdd) 0.dp else 84.dp,
                                        emptyText = when {
                                            events.isEmpty() -> "还没有任何事在等着"
                                            query.isNotBlank() -> "没找到"
                                            pageCat != null -> "「$pageCat」里还没有事"
                                            else -> "没找到"
                                        },
                                        emptyHint = when {
                                            events.isEmpty() -> "写下第一件想记住的事，比如「下周五 交房租」"
                                            query.isNotBlank() -> "换个词试试，分类名也算"
                                            pageCat != null -> "左右滑一下看看别的分类"
                                            else -> "换个词试试"
                                        },
                                        onOpenEvent = { id, rect, index, offset ->
                                            onOpenEvent(
                                                id,
                                                rect,
                                                HomeLocate(
                                                    category = pageCat,
                                                    index = index,
                                                    offset = offset,
                                                    search = searchMode,
                                                    query = query,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }

                            if (!quickAdd) {
                                AddBall(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(18.dp),
                                    onClick = onNewEvent,
                                )
                            }
                        }

                        if (quickAdd) {
                            Spacer(Modifier.height(12.dp))
                            QuickAddBar(
                                showHint = quickAddHint,
                                onQuickAdd = { event ->
                                    onQuickAdd(event)
                                    lastAdded = event.id
                                },
                                onDraft = onDraft,
                            )
                        }
                    }
                }
            }
        }
    } }

    if (menuOpen) {
        val options = if (headerSearch) {
            listOf("搜索", "日历", "设置")
        } else {
            listOf("日历", "设置")
        }
        ChoiceDialog(
            title = "如期",
            options = options,
            selected = null,
            onPick = { option ->
                when (option) {
                    "搜索" -> openSearch()
                    "日历" -> onOpenCalendar()
                    "设置" -> onOpenSettings()
                }
            },
            onDismiss = { menuOpen = false },
        )
    }
}

private class RectRef(var value: Rect? = null)

@Composable
private fun CategoryPage(
    rows: List<Pair<CountdownEvent, CountdownInfo>>,

    lastAdded: String?,

    isCurrent: Boolean,
    onAddedShown: () -> Unit,
    emptyText: String,
    emptyHint: String,
    listCardStyle: Boolean,

    rowDensity: RowDensity = RowDensity.STANDARD,

    bottomInset: Dp = 0.dp,

    locate: HomeLocate? = null,

    freshState: Boolean = false,

    hiddenRowId: String? = null,
    onOpenEvent: (id: String, origin: Rect?, index: Int, offset: Int) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyState(text = emptyText, hint = emptyHint)
        return
    }

    val listState = if (freshState) {
        remember(locate) {
            LazyListState(
                firstVisibleItemIndex = locate?.index ?: 0,
                firstVisibleItemScrollOffset = locate?.offset ?: 0,
            )
        }
    } else {
        rememberLazyListState()
    }

    LaunchedEffect(lastAdded, rows, isCurrent) {
        val id = lastAdded ?: return@LaunchedEffect
        if (!isCurrent) return@LaunchedEffect
        val index = rows.indexOfFirst { it.first.id == id }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.animateScrollToItem(index)
        }
        onAddedShown()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp + bottomInset),
    ) {
        itemsIndexed(rows, key = { _, item -> item.first.id }) { index, item ->
            val (event, info) = item
// 这一行正作为详情页那张卡在飞：位置留着，什么都不画。
            val inFlight = event.id == hiddenRowId
            val origin = remember { RectRef() }
            EventRow(
                event = event,
                info = info,
                modifier = Modifier
                    .onGloballyPositioned { origin.value = it.boundsInWindow() }
                    .alpha(if (inFlight) 0f else 1f),
                onClick = {
                    if (!inFlight) {
                        onOpenEvent(
                            event.id,
                            origin.value,
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                        )
                    }
                },
                showCardLook = listCardStyle,
                rowDensity = rowDensity,
            )
            if (index != rows.lastIndex) {
                GlassDivider(Modifier.padding(horizontal = RowInset))
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    daysLeft: Long?,
    progress: Float,
    almanacText: String?,
    showProgress: Boolean,
    searchMode: Boolean,

    showSearch: Boolean,

    compact: Boolean,
    onMenu: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    val g = LocalGlass.current

    val meta = buildAnnotatedString {
        if (daysLeft != null) {
            append("今年还剩 ")
            withStyle(SpanStyle(color = g.accent, fontWeight = FontWeight.SemiBold)) {
                append(daysLeft.toString())
            }
            append(" 天")
        }
        if (almanacText != null) {
            if (daysLeft != null) append("  ·  ")
            append(almanacText)
        }
    }

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = Radius.card, bottomEnd = 24.dp),
        weight = 0.8f,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
// 标题从 20dp 起：22sp 的字贴到 18dp 会显得往外顶
                        start = Gutter,
                        end = 6.dp,
                        top = if (compact) 6.dp else 10.dp,
                        bottom = if (compact) 8.dp else 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = if (compact) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = g.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = if (compact) {
                                MaterialTheme.typography.labelSmall
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                            color = g.inkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = if (compact) 2.dp else 4.dp),
                        )
                    }
                }
                if (showSearch) {
                    TextAction(
                        text = if (searchMode) "收起" else "搜索",
                        onClick = onToggleSearch,
                        color = g.inkMid,
                    )
                }
                TextAction(text = "⋯", onClick = onMenu, emphasis = true)
            }

            if (showProgress) {
                YearLine(progress)
            }
        }
    }
}

@Composable
private fun YearLine(progress: Float) {
    val g = LocalGlass.current
    val done = progress.coerceIn(0.002f, 0.998f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        Box(
            Modifier
                .weight(done)
                .fillMaxSize()
                .background(g.accent)
        )
        Box(
            Modifier
                .weight(1f - done)
                .fillMaxSize()
                .background(g.ink.copy(alpha = 0.07f))
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val g = LocalGlass.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { focus.requestFocus() }
    }

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.panel),
        weight = 0.3f,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = g.ink),
            cursorBrush = SolidColor(g.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RowInset, vertical = 13.dp)
                .focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索标题或备注",
                            style = MaterialTheme.typography.titleMedium,
                            color = g.inkSoft,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun AddBall(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ballSize: Dp = 54.dp,
) {
    val g = LocalGlass.current
    GlassPanel(
        modifier = modifier
            .size(ballSize)
            .clip(CircleShape)
            .pressable(onClick, scale = 0.92f)
            .semantics { contentDescription = "新建倒数日" },
        shape = CircleShape,
        weight = 0.95f,
        elevation = 6.dp,
    ) {
// 十字的留白按尺寸等比缩：17 / 54 ≈ 0.315，换尺寸时不用另算。
        Canvas(modifier = Modifier.fillMaxSize().padding(ballSize * 0.315f)) {
            val stroke = 2.dp.toPx()
            drawLine(
                color = g.accent,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = g.accent,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun QuickAddBar(

    showHint: Boolean,
    onQuickAdd: (CountdownEvent) -> Unit,
    onDraft: (CountdownEvent) -> Unit,
) {
    val g = LocalGlass.current
    val today = rememberToday()
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val parsed = remember(text) {
        if (text.isBlank()) null else NaturalDateParser.parse(text)
    }
// 解析成功、但只说了日期没说是什么事（比如只打了「明天」）。
    val dateOnly = parsed != null && parsed.title.isBlank()

    val submit: () -> Unit = submit@{
        if (text.isBlank()) return@submit
        val p = parsed
        when {
// 一句完整的话都没听出日期：原样当标题带走，让人自己挑日子。
            p == null -> onDraft(CountdownEvent(title = text.trim(), anchor = today))
            p.title.isBlank() -> onDraft(
                CountdownEvent(title = "", anchor = p.date, repeat = p.repeat)
            )
            else -> onQuickAdd(
                CountdownEvent(title = p.title, anchor = p.date, repeat = p.repeat)
            )
        }
        text = ""
    }

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.panel),
        weight = 0.9f,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = RowInset, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = g.ink),
                    cursorBrush = SolidColor(g.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty() && showHint) {
                                Text(
                                    text = QuickHints.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = g.inkSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                TextAction(
                    text = when {
                        parsed == null && text.isBlank() -> "＋"
                        parsed == null -> "选日期"
                        dateOnly -> "写标题"
                        else -> "添加"
                    },
                    onClick = {
                        if (text.isBlank()) {
                            runCatching { focus.requestFocus() }
                        } else {
                            submit()
                        }
                    },
                    emphasis = parsed != null && !dateOnly,
                    color = if (parsed != null && !dateOnly) g.accent else g.inkMid,
                    padding = 10.dp,
                )
            }

// 解析预览：让用户在建之前就确认"我有没有听懂"
            if (parsed != null) {
                Text(
                    text = if (dateOnly) {
                        parsed.explanation
                    } else {
                        "${parsed.title} · ${parsed.explanation}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = g.inkMid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = RowInset, end = RowInset, bottom = 12.dp),
                )
            }
        }
    }
}

