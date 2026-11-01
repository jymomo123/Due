package com.offline.due

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offline.due.data.BgStyle
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventStore
import com.offline.due.data.SettingsStore
import com.offline.due.notify.Notifications
import com.offline.due.notify.ReminderScheduler
import com.offline.due.ui.AboutScreen
import com.offline.due.ui.BackdropScreen
import com.offline.due.ui.CalendarScreen
import com.offline.due.ui.CardScreen
import com.offline.due.ui.DetailScreen
import com.offline.due.ui.EditScreen
import com.offline.due.ui.HomeLocate
import com.offline.due.ui.HomeScreen
import com.offline.due.ui.OnboardingScreen
import com.offline.due.ui.QuickHints
import com.offline.due.ui.SettingsScreen
import com.offline.due.ui.rememberToday
import com.offline.due.ui.components.AppBackground
import com.offline.due.ui.components.BetaDialog
import com.offline.due.ui.components.LocalSpark
import com.offline.due.ui.components.PredictiveBack
import com.offline.due.ui.components.SparkOverlay
import com.offline.due.ui.components.SparkState
import com.offline.due.ui.components.Watermark
import com.offline.due.ui.components.backdropBrush
import com.offline.due.ui.components.backdropVeilBrush
import com.offline.due.ui.components.drawBackdropBase
import com.offline.due.ui.components.grain
import com.offline.due.ui.components.pageBaseColor
import com.offline.due.ui.components.rememberBackdrop
import com.offline.due.ui.theme.CountdownTheme
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Radius
import com.offline.due.widget.TodayWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class NavRequest(val eventId: String, val seq: Long)

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }

    private var navRequest by mutableStateOf<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enableEdgeToEdge()
        }
        run {
            val s = SettingsStore.state.value
            val night = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val dark = when (s.themeMode) {
                com.offline.due.data.ThemeMode.SYSTEM -> night
                com.offline.due.data.ThemeMode.LIGHT -> false
                com.offline.due.data.ThemeMode.DARK -> true
            }
            val g = com.offline.due.ui.theme.glassFor(dark, s.bgTone)
            val launch = if (s.bgStyle == com.offline.due.data.BgStyle.SOLID) {
                s.bgColor.toInt()
            } else {
                g.gradient.firstOrNull()?.toArgb() ?: g.scrim.toArgb()
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(launch))
        }
        navRequest = intent?.getStringExtra(EXTRA_EVENT_ID)?.let { NavRequest(it, 0L) }
        setContent {
            val settings by SettingsStore.state.collectAsStateWithLifecycle()
            CountdownTheme(
                mode = settings.themeMode,
                tone = settings.bgTone,
                textScale = settings.textScale,
            ) {
                SystemBarIcons()
                val grainAlpha = LocalGlass.current.grainAlpha
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .grain(grainAlpha),
                    color = Color.Transparent,
                ) {
                    val backdropPhoto = rememberBackdrop(settings.bgImage)
                    AppBackground(
                        style = settings.bgStyle,
                        solidColor = Color(settings.bgColor),
                        imageName = settings.bgImage,
                        scrim = settings.bgScrim,
                        photo = backdropPhoto,
                    ) {
                        var justOnboarded by rememberSaveable { mutableStateOf(false) }
                        if (!settings.onboarded) {
                            OnboardingScreen(
                                settings = settings,
                                onDone = {
                                    SettingsStore.setOnboarded()
                                    justOnboarded = true
                                },
                            )
                        } else {
                            var fullBleed by rememberSaveable { mutableStateOf(false) }
                            AppRoot(
                                navRequest = navRequest,
                                suppressBeta = justOnboarded,
                                onFullBleed = { fullBleed = it },
                                backdropPhoto = backdropPhoto,
                            )
                            if (!fullBleed) Watermark(Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_EVENT_ID)?.let { id ->
            navRequest = NavRequest(id, (navRequest?.seq ?: 0L) + 1L)
        }
    }
}

@Composable
private fun SystemBarIcons() {
    val view = LocalView.current
    val dark = LocalGlass.current.isDark
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppRoot(
    navRequest: NavRequest?,
    suppressBeta: Boolean = false,

    onFullBleed: (Boolean) -> Unit = {},

    backdropPhoto: ImageBitmap? = null,
) {
    val context = LocalContext.current
// 玻璃令牌。手势期间那层"页面自带的底"要用它的渐变，和根上那份**同一个**。
    val g = LocalGlass.current
    val events by EventStore.events.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()

// 已经用过的分类。编辑页拿它补全分类弹窗，用户自建的分类才会一直出现。
    val categories = remember(events) { events.map { it.category }.distinct() }

    val today = rememberToday()

    var showBeta by remember(suppressBeta) { mutableStateOf(!suppressBeta) }

    var route by rememberSaveable { mutableStateOf("home") }
    var argId by rememberSaveable { mutableStateOf<String?>(null) }
    var isNew by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(route) { onFullBleed(route == "customize" || route == "about") }

    var cardOpen by rememberSaveable { mutableStateOf(false) }

    var subBack by remember { mutableStateOf(false) }

    var rowAnchor by remember { mutableStateOf(HomeLocate(null, 0, 0)) }

    var cardSeen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(cardOpen) {
        if (cardOpen) {
            cardSeen = true
        } else if (cardSeen) {
            cardSeen = false
            QuickHints.next()
        }
    }

    val cardOrigin = remember { arrayOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero) }

    var rowOrigin by remember { mutableStateOf<Rect?>(null) }

    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }

    var detailFrom by rememberSaveable { mutableStateOf("home") }

// 通知权限（Android 13+）
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(navRequest) {
        val req = navRequest ?: return@LaunchedEffect
        if (events.any { it.id == req.eventId }) {
            argId = req.eventId
// 从通知点进来时 App 可能根本没停在首页上，没有"行"可言。
            rowOrigin = null
            detailFrom = "home"
            isNew = false
            route = "detail"
        }
    }

    LaunchedEffect(events, settings.ongoingNotification, settings.reminderAlert, settings.themeMode) {
        withContext(Dispatchers.IO) {
            ReminderScheduler.rescheduleAll(context, events)
            TodayWidgetProvider.refreshAll(context)
        }
        if (settings.ongoingNotification) {
            Notifications.updateOngoing(context, events)
        } else {
            Notifications.cancelOngoing(context)
        }
    }

    val goHome = {
        route = "home"
        cardOpen = false
    }

// 离开详情页：回到它来的那一屏。日历来的回日历，其余回首页。
    val leaveDetail = {
        route = if (detailFrom == "calendar") "calendar" else "home"
        cardOpen = false
    }

    var backLiftP by remember { mutableFloatStateOf(0f) }

    var previewA by remember { mutableFloatStateOf(0f) }

    var backLiftOn by remember { mutableStateOf(false) }
    val backScope = rememberCoroutineScope()

    var backdropA by remember { mutableFloatStateOf(0f) }

    var backDir by remember { mutableFloatStateOf(1f) }

    var previewLock by remember { mutableStateOf<String?>(null) }

    var liftPage by remember { mutableStateOf(true) }

    var previewSamePage by remember { mutableStateOf(false) }

    var dragActive by remember { mutableStateOf(false) }

    var settleJob by remember { mutableStateOf<Job?>(null) }

    var settleGen by remember { mutableIntStateOf(0) }

    val lastDragAt = remember { LongArray(1) }

    val pageDrag: (Float) -> Unit = { p ->
        lastDragAt[0] = android.os.SystemClock.uptimeMillis()
        if (!dragActive) {
            dragActive = true
// 作废上一场收尾：它就算之后跑进 `finally` 也不许再改任何量。
            settleGen++
            settleJob?.cancel()
            settleJob = null
// 手势开始 = 快照锁定。之后 `route` 再怎么变，垫的都是同一屏。
            previewLock = backTarget(route, detailFrom, isNew)
            liftPage = route != "detail" || rowOrigin == null
// 详情页那一路垫的是目标页本身，路由换人之后要让位（见 [previewSamePage]）。
            previewSamePage = !liftPage
            backLiftOn = true
            previewA = 0f
// 底从第一帧就全实：手势刚开始页面还没让开，底下什么都不该露。
            backdropA = 1f
        }
        backLiftP = p
        if (liftPage) previewA = ((p - 0.04f) / 0.24f).coerceIn(0f, 1f)
    }

    val pageDragEnd: (Boolean) -> Unit = { committed ->
        dragActive = false
        if (backLiftOn && settleJob?.isActive != true) {
            val gen = ++settleGen
            settleJob = backScope.launch {
                try {
                    coroutineScope {
                        launch {
                            animate(
                                initialValue = backLiftP,
                                targetValue = 0f,
                                animationSpec = tween(Motion.Settle, easing = Motion.handover),
                            ) { v, _ -> backLiftP = v }
                        }
                        launch {
                            animate(
                                initialValue = backdropA,
                                targetValue = 0f,
                                animationSpec = tween(Motion.Settle, easing = Motion.handover),
                            ) { v, _ -> backdropA = v }
                        }
                        if (liftPage) {
                            launch {
                                animate(
                                    initialValue = previewA,
                                    targetValue = if (committed) 1f else 0f,
                                    animationSpec = tween(Motion.Settle, easing = Motion.handover),
                                ) { v, _ -> previewA = v }
                            }
                        }
                    }
                } finally {
                    if (gen == settleGen) {
                        backLiftOn = false
                        liftPage = true
                        settleJob = null
                    }
                }
            }
        }
    }

    LaunchedEffect(backLiftOn) {
        if (backLiftOn || previewLock == null) return@LaunchedEffect
        withFrameNanos { }
        previewLock = null
        previewSamePage = false
        previewA = 0f
    }

    LaunchedEffect(route, cardOpen) { pageDragEnd(true) }

    val backBusy = backLiftOn && (dragActive || settleJob?.isActive == true)

    val healStaleGesture by rememberUpdatedState(newValue = {
        val stale = dragActive || (backLiftOn && settleJob == null)
        if (stale && android.os.SystemClock.uptimeMillis() - lastDragAt[0] > 400L) {
            dragActive = false
// 作废在跑的收尾：它之后进 `finally` 也不许再改任何量。
            settleGen++
            settleJob?.cancel()
            settleJob = null
            backLiftOn = false
            backLiftP = 0f
            previewA = 0f
            backdropA = 0f
            previewLock = null
            previewSamePage = false
            liftPage = true
        }
    })
    val healStaleWatch = Modifier.pointerInput(Unit) {
// Initial 阶段**只读不消费**：这一层是观察者，不许抢任何人的手势。
        awaitPointerEventScope {
            var wasDown = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val isDown = ev.changes.any { it.pressed }
                if (isDown && !wasDown) healStaleGesture()
                wasDown = isDown
            }
        }
    }

    if (showBeta && !settings.betaAck) {
        BetaDialog(
            onDismiss = { dontShowAgain ->
                showBeta = false
                if (dontShowAgain) SettingsStore.setBetaAck()
            }
        )
    }

    if (route != "home") {
        PredictiveBack(
            enabled = !cardOpen && !subBack,
            onProgress = pageDrag,
            onEdge = { edge ->
                backDir = if (edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
            },
            onCancel = { pageDragEnd(false) },
            onBack = {
                val target = backTarget(route, detailFrom, isNew)
                if (route == "edit" && isNew) draftJson = null
                if (target == null || target == "home") {
                    goHome()
                } else {
                    route = target
                    cardOpen = false
                }
                pageDragEnd(true)
            },
        )
    }

    val spark = remember { SparkState() }

    val routeStates = rememberSaveableStateHolder()

    val previewStates = rememberSaveableStateHolder()

    val cardBlur = remember { Animatable(0f) }

    var cardDragP by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(cardOpen) {
        if (cardOpen) cardDragP = 0f
        cardBlur.animateTo(
            targetValue = if (cardOpen) 30f else 0f,
            animationSpec = tween(Motion.Enter, easing = Motion.enterEasing),
        )
        if (!cardOpen) cardDragP = 0f
    }

    val cardBlurLayer = Modifier.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val r = (cardBlur.value * (1f - cardDragP)).dp.toPx()
            renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
        }
    }

    val liftBase = backdropBrush(settings.bgStyle, Color(settings.bgColor))
    val liftPhoto = if (settings.bgStyle == BgStyle.IMAGE) backdropPhoto else null
    val liftVeil = if (liftPhoto != null) backdropVeilBrush(settings.bgScrim) else null

    val backLift: Modifier =
        if (backLiftOn && liftPage) {
            Modifier.graphicsLayer {
                val p = backLiftP
                if (p <= 0.001f) {
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    alpha = 1f
                    clip = false
                    shape = RectangleShape
                    shadowElevation = 0f
                } else {
                    val e = PageLift.eased(p)
                    val s = PageLift.scale(e)
                    scaleX = s
                    scaleY = s
                    translationX = PageLift.offsetX(e, size.width) * backDir
                    shape = RoundedCornerShape(PageLift.corner(e))
                    clip = true
                    shadowElevation = PageLift.shadow.toPx() * e
                    alpha = 1f - 0.04f * Motion.handover.transform(p)
                }
            }
                .drawBehind { drawBackdropBase(liftBase, liftPhoto, liftVeil, backdropA) }
        } else {
            Modifier
        }

    val previewRoute: String? = previewLock

    var cardReady by remember { mutableStateOf(false) }
    LaunchedEffect(route) { if (route != "detail") cardReady = false }

    val hiddenRowId = if (route == "detail" && detailFrom == "home" && cardReady) argId else null

    val renderHome: @Composable (SaveableStateHolder, Boolean) -> Unit = { holder, preview ->
        val key = if (preview) {
            "preview-${rowAnchor.category ?: "-"}-${rowAnchor.search}-${rowAnchor.query}"
        } else {
            "home"
        }
        holder.SaveableStateProvider(key) {
            HomeScreen(
                events = events,
                headerTitle = settings.headerTitle,
                headerCountdown = settings.headerCountdown,
                headerAlmanac = settings.headerAlmanac,
                headerSearch = settings.headerSearch,
                yearHeader = settings.yearHeader,
                listCardStyle = settings.listCardStyle,
                quickAdd = settings.quickAdd,
                quickAddHint = settings.quickAddHint,
                rowDensity = settings.rowDensity,
                locate = if (preview) rowAnchor else null,
// 那一行正作为详情页那张卡在飞：位置留着，什么都不画。
                hiddenRowId = hiddenRowId,
                onOpenEvent = { id, origin, anchor ->
                    if (!backBusy) {
                        argId = id
// 记下这一行在屏幕上的位置，详情页的卡片从那儿长出来。
                        rowOrigin = origin
                        rowAnchor = anchor
                        detailFrom = "home"
                        isNew = false
                        route = "detail"
                    }
                },
                onQuickAdd = { event ->
// 解析出了日期：直接落库，不打断用户。
                    EventStore.upsert(event)
                    ReminderScheduler.scheduleOne(context, event)
                },
                onDraft = { event ->
                    if (!backBusy) {
                        draftJson = event.toJson().toString()
                        isNew = true
                        argId = null
                        route = "edit"
                    }
                },
                onNewEvent = {
                    if (!backBusy) {
                        draftJson = null
                        isNew = true
                        argId = null
                        route = "edit"
                    }
                },
                onOpenCalendar = { if (!backBusy) route = "calendar" },
                onOpenSettings = { if (!backBusy) route = "settings" },
            )
        }
    }

    val renderCalendar: @Composable (SaveableStateHolder) -> Unit = { holder ->
        holder.SaveableStateProvider("calendar") {
            CalendarScreen(
                events = events,
                pageBase = pageBaseColor(settings.bgStyle, Color(settings.bgColor)),
                onOpenEvent = { id ->
                    argId = id
                    rowOrigin = null
                    detailFrom = "calendar"
                    isNew = false
                    route = "detail"
                },
                onBack = goHome,
            )
        }
    }

    val renderSettings: @Composable (SaveableStateHolder) -> Unit = { holder ->
        holder.SaveableStateProvider("settings") {
            SettingsScreen(
                events = events,
                settings = settings,
                onBack = goHome,
                onDataChanged = { },
                onOpenCustomize = { if (!backBusy) route = "customize" },
                onOpenAbout = { if (!backBusy) route = "about" },
                onOpenBackdrop = { if (!backBusy) route = "backdrop" },
            )
        }
    }

    val renderBackdrop: @Composable (SaveableStateHolder) -> Unit = { holder ->
        holder.SaveableStateProvider("backdrop") {
            BackdropScreen(
                settings = settings,
                onBack = { route = "settings" },
                onSubBack = { subBack = it },
            )
        }
    }

    val renderDetail: @Composable (
        holder: SaveableStateHolder,
        event: CountdownEvent,
        interactive: Boolean,
    ) -> Unit = { holder, event, interactive ->
        holder.SaveableStateProvider("detail-${event.id}") {
            DetailScreen(
                event = event,
                origin = if (interactive) rowOrigin else null,
                backEnabled = interactive,
                listCardStyle = settings.listCardStyle,
                rowDensity = settings.rowDensity,
                onCardReady = { if (interactive) cardReady = true },
                onBack = { if (interactive) leaveDetail() },
                onEdit = {
                    if (interactive && !backBusy) {
                        isNew = false
                        rowOrigin = null
                        route = "edit"
                    }
                },
                onOpenCard = { if (interactive && !backBusy) cardOpen = true },
// 把这张卡的矩形记下来：卡片页从这儿滑出去，退回来时再缩到这一块。
                onCardPositioned = { card, title, number, date ->
                    if (interactive) {
                        cardOrigin[0] = card
                        cardOrigin[1] = title
                        cardOrigin[2] = number
                        cardOrigin[3] = date
                    }
                },
                onDragProgress = { p -> if (interactive) pageDrag(p) },
                onDragEnd = { if (interactive) pageDragEnd(true) },
                onCover = { c ->
                    if (interactive) previewA = ((1f - c) / 0.30f).coerceIn(0f, 1f)
                },
            )
        }
    }

    CompositionLocalProvider(LocalSpark provides spark) {
        Box(
            Modifier
                .fillMaxSize()
// 残留手势的兜底观察者（只读、不消费）。见 `healStaleGesture`。
                .then(healStaleWatch)
        ) {
            if (previewRoute != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (liftPage) {
                                Modifier.drawWithContent {
// `backLiftP` 在**绘制阶段**读（见 `liftOutsidePath` 的说明）
                                    val outside = liftOutsidePath(backLiftP, size, backDir)
                                        ?: return@drawWithContent
                                    clipPath(outside) { this@drawWithContent.drawContent() }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .swallowTouches()
                        .graphicsLayer {
                            alpha = if (previewSamePage && previewRoute == route) 0f else previewA
                            scaleX = 1f
                            scaleY = 1f
                        },
                ) {
                    when (previewRoute) {
                        "home" -> renderHome(previewStates, true)
                        "calendar" -> renderCalendar(previewStates)
                        "settings" -> renderSettings(previewStates)
                        "detail" -> {
                            val ev = events.firstOrNull { it.id == argId }
                            if (ev != null) renderDetail(previewStates, ev, false)
                        }
                    }
                }
            }

            AnimatedContent(
                modifier = Modifier
                    .then(cardBlurLayer)
                    .then(backLift),
                targetState = route,
                transitionSpec = {
                    val forward = routeDepth(targetState) > routeDepth(initialState)
                    if (rowOrigin != null &&
                        (initialState == "detail" || targetState == "detail") &&
                        (initialState == "home" || targetState == "home")
                    ) {
                        val handOff = previewSamePage && previewRoute == targetState
                        if (handOff) {
                            EnterTransition.None
                                .togetherWith(fadeOut(tween(Motion.Exit, easing = Motion.exitEasing)))
                        } else {
                            fadeIn(tween(Motion.Enter, easing = Motion.enterEasing))
                                .togetherWith(fadeOut(tween(Motion.Exit, easing = Motion.exitEasing)))
                        }
                    } else if (!forward && backLiftOn) {
                        fadeIn(tween(Motion.Settle, easing = Motion.handover))
                            .togetherWith(fadeOut(tween(Motion.Settle, easing = Motion.handover)))
                    } else {
                    val slide = Motion.glide<IntOffset>()
                    if (forward) {
                        (
                            fadeIn(tween(Motion.Page, easing = Motion.enterEasing)) +
                                slideInVertically(slide) { h -> h / 18 } +
                                scaleIn(
                                    tween(Motion.Page, easing = Motion.enterEasing),
                                    initialScale = 0.984f,
                                )
                            ).togetherWith(
                            fadeOut(tween(Motion.Exit, easing = Motion.exitEasing)) +
                                scaleOut(
                                    tween(Motion.Exit, easing = Motion.exitEasing),
                                    targetScale = 0.992f,
                                )
                        )
                    } else {
                        (
                            fadeIn(tween(Motion.Enter, easing = Motion.enterEasing)) +
                                slideInVertically(slide) { h -> -h / 26 } +
                                scaleIn(
                                    tween(Motion.Enter, easing = Motion.enterEasing),
                                    initialScale = 0.992f,
                                )
                            ).togetherWith(
                            fadeOut(tween(Motion.Exit, easing = Motion.exitEasing)) +
                                slideOutVertically(slide) { h -> h / 14 } +
                                scaleOut(
                                    tween(Motion.Exit, easing = Motion.exitEasing),
                                    targetScale = 0.972f,
                                )
                        )
                    }
                    }
                },
                label = "route",
            ) { current ->
        when (current) {
        "detail" -> {
            val event = events.firstOrNull { it.id == argId }
            if (event == null) {
                LaunchedEffect(argId) { leaveDetail() }
            } else {
                renderDetail(routeStates, event, true)
            }
        }

        "edit" -> {
// 先把草稿解出来。draftJson 为空（比如直接新建一个空事件）就是 null。
            val draftEvent = remember(draftJson) {
                draftJson?.let { raw ->
                    runCatching { CountdownEvent.fromJson(JSONObject(raw)) }.getOrNull()
                }
            }
            val original = remember(isNew, argId, draftEvent) {
                fun blank() = CountdownEvent(title = "", anchor = today)
                events.firstOrNull { it.id == argId }
                    ?: draftEvent.takeIf { isNew }
                    ?: blank()
            }
            EditScreen(
                original = original,
                isNew = isNew,
                categories = categories,
                onSubBack = { subBack = it },
                onSave = { saved ->
                    EventStore.upsert(saved)
                    ReminderScheduler.scheduleOne(context, saved)
                    argId = saved.id
                    isNew = false
                    route = "detail"
                },
                onDelete = { id ->
                    EventStore.delete(id)
                    ReminderScheduler.cancel(context, id)
                    goHome()
                },
                onBack = {
                    if (isNew) {
                        draftJson = null
                        goHome()
                    } else {
                        route = "detail"
                    }
                },
            )
        }

        "calendar" -> renderCalendar(routeStates)

        "settings" -> renderSettings(routeStates)

        "backdrop" -> renderBackdrop(routeStates)

        "about" -> AboutScreen(onBack = { route = "settings" })

        "customize" -> OnboardingScreen(
            settings = settings,
            firstRun = false,
            onDone = { route = "settings" },
        )

        else -> renderHome(routeStates, false)
        }
            }

            val cardEvent = events.firstOrNull { it.id == argId }
            AnimatedVisibility(
                visible = cardOpen && cardEvent != null,
                enter = fadeIn(tween(Motion.Press)),
                exit = fadeOut(tween(Motion.Exit, easing = Motion.exitEasing)),
            ) {
                if (cardEvent != null) {
                    CardScreen(
                        event = cardEvent,
                        onBack = { cardOpen = false },
                        hintSeen = settings.cardHintAck,
                        onHintSeen = { SettingsStore.setCardHintAck() },
                        origin = cardOrigin[0].takeIf { it.width > 0f },
                        partTitle = cardOrigin[1].takeIf { it.width > 0f },
                        partNumber = cardOrigin[2].takeIf { it.width > 0f },
                        partDate = cardOrigin[3].takeIf { it.width > 0f },
                        defaultNumberMode = settings.defaultCardMode,
                        onDragProgress = { cardDragP = it },
                    )
                }
            }

            SparkOverlay(spark)
        }
    }
}

private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

private object PageLift {

    fun eased(p: Float): Float = p * (2f - p)

    fun scale(e: Float): Float = 1f - 0.055f * e

    fun offsetX(e: Float, width: Float): Float = width * 0.20f * e

    fun corner(e: Float): Dp = Radius.card * e

    val shadow: Dp = 18.dp
}

private fun Density.liftOutsidePath(p: Float, size: Size, dir: Float): Path? {
    val e = PageLift.eased(p.coerceIn(0f, 1f))
    if (e <= 0.001f) return null

    val s = PageLift.scale(e)
    val w = size.width * s
    val h = size.height * s
    val left = (size.width - w) / 2f + PageLift.offsetX(e, size.width) * dir
    val top = (size.height - h) / 2f
    val r = PageLift.corner(e).toPx()

    return Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(0f, 0f, size.width, size.height))
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + w,
                bottom = top + h,
                cornerRadius = CornerRadius(r, r),
            )
        )
    }
}

private fun backTarget(
    route: String,
    detailFrom: String,
    isNew: Boolean,
): String? = when (route) {
    "home" -> null
// 日历来的回日历，其余回首页。`detailFrom` 是进详情页那一刻记下的。
    "detail" -> if (detailFrom == "calendar") "calendar" else "home"
    "calendar", "settings" -> "home"
// 自定义 / 关于 / 整屏背景只可能从设置页进来。不特判就会一路送回首页。
    "about", "customize", "backdrop" -> "settings"
    "edit" -> if (isNew) "home" else "detail"
    else -> "home"
}

private fun routeDepth(route: String): Int = when (route) {
    "home" -> 0
    "detail", "calendar", "settings" -> 1
    "edit" -> 2
    "customize", "about", "backdrop" -> 2
    else -> 0
}

