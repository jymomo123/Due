package com.offline.due.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.offline.due.data.CountdownEvent
import com.offline.due.data.GallerySaver
import com.offline.due.domain.CardNumberMode
import com.offline.due.domain.CountdownMath
import com.offline.due.ui.components.RowInset
import com.offline.due.ui.components.CardInset
import com.offline.due.ui.components.DaySeam
import com.offline.due.ui.components.GlassDialog
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.PredictiveBack
import com.offline.due.ui.components.TextAction
import com.offline.due.ui.components.pressable
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Motion
import com.offline.due.ui.theme.Palette
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.displayNumberHeight
import com.offline.due.ui.theme.rememberWideLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

private fun safeFileBase(title: String): String {
    val cleaned = title.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
    return cleaned.take(24).ifBlank { "卡片" }
}

private const val DAY_SEAM = 900

private const val DAY_LIFT = 240

private val LIFT_DISTANCE = 6.dp

private class LastDay(var value: LocalDate)

@Composable
fun CardScreen(
    event: CountdownEvent,
    onBack: () -> Unit,

    hintSeen: Boolean = true,

    onHintSeen: () -> Unit = {},

    origin: Rect? = null,

    partTitle: Rect? = null,
    partNumber: Rect? = null,
    partDate: Rect? = null,

    defaultNumberMode: CardNumberMode = CardNumberMode.DAY,

    onDragProgress: (Float) -> Unit = {},
) {
    val g = LocalGlass.current
    val today = rememberToday()

    var peek by remember(event.id) { mutableStateOf<Boolean?>(null) }
    val showSeconds = peek ?: event.showSeconds

    val nowState = rememberNowState(showSeconds)
    val info = remember(event, today) { CountdownMath.occurrence(event, today) }
    val accent = Color(event.color)
    val seamColor = remember(event.numberColor, g.isDark) { Palette.number(event, g) }
    val wide = rememberWideLayout()

    var modeOrdinal by rememberSaveable(event.id) {
        mutableIntStateOf(defaultNumberMode.ordinal)
    }
    val numberMode = CardNumberMode.entries[
        modeOrdinal.coerceIn(0, CardNumberMode.entries.lastIndex)
    ]

    val enter = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        enter.animateTo(1f, tween(Motion.Enter, easing = Motion.enterEasing))
    }

    var targetTop by remember(event.id) { mutableFloatStateOf(Float.NaN) }
    var selfRect by remember(event.id) { mutableStateOf(Rect.Zero) }
    val originTop = origin?.top ?: 0f
    val shift = remember(event.id) { Animatable(0f) }
    LaunchedEffect(targetTop, originTop) {
        if (targetTop.isNaN()) return@LaunchedEffect
        val from = if (originTop > 0f) originTop - targetTop else 0f
        if (from == 0f) {
            shift.snapTo(0f)
        } else {
            shift.snapTo(from)
            shift.animateTo(
                targetValue = 0f,
                animationSpec = Motion.glide(),
            )
        }
    }

    val lastDay = remember { LastDay(today) }
    val seam = remember { Animatable(1f) } // 1 = 光已经走完，不画
    val lift = remember { Animatable(0f) } // 0 → -1 → 0，卡片被光顶起来一下
    var dayTurn by remember { mutableIntStateOf(0) }
    var dateAtResume by remember { mutableStateOf(LocalDate.now()) }

    LifecycleResumeEffect(Unit) {
        dateAtResume = LocalDate.now()
        onPauseOrDispose { }
    }

    LaunchedEffect(today) {
        if (today == lastDay.value) return@LaunchedEffect
        lastDay.value = today
        if (today == dateAtResume) return@LaunchedEffect
        dayTurn++
    }

    LaunchedEffect(dayTurn) {
        if (dayTurn == 0) return@LaunchedEffect
        seam.snapTo(0f)
        seam.animateTo(1f, tween(DAY_SEAM, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(dayTurn) {
        if (dayTurn == 0) return@LaunchedEffect
        lift.animateTo(-1f, tween(DAY_LIFT, easing = Motion.enterEasing))
        lift.animateTo(0f, Motion.settle())
    }

// 保存为图片
    val context = LocalContext.current
    val capture = rememberGraphicsLayer()
    var saving by remember { mutableStateOf(false) }
    var saveHint by remember { mutableStateOf<String?>(null) }

    val needsPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED
    var saveRequest by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveRequest++ else saveHint = "没有存储权限，存不进相册"
    }

    val doSave = {
        if (needsPerm) {
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveRequest++
        }
    }

    LaunchedEffect(saveRequest) {
        if (saveRequest == 0) return@LaunchedEffect
        saving = true
        val bitmap = runCatching { capture.toImageBitmap().asAndroidBitmap() }.getOrNull()
        val ok = bitmap != null && GallerySaver.save(
            context = context,
            bitmap = bitmap,
            name = "如期-" + safeFileBase(event.title) + "-" +
                LocalDateTime.now().format(STAMP) + ".png",
        )
        saving = false
        saveHint = if (ok) "已保存到相册" else "没存成，稍后再试"
    }

    LaunchedEffect(saveHint) {
        if (saveHint == null) return@LaunchedEffect
        delay(2200)
        saveHint = null
    }

    var backDrag by remember(event.id) { mutableFloatStateOf(0f) }
    var closing by remember(event.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shell = remember(event.id) { FloatArray(4) }
// 还没有起点（不是从详情页点进来的）时没有"该缩回哪儿"，就只淡出。
    val canMorph = origin != null && !targetTop.isNaN() &&
        selfRect.width > 0f && selfRect.height > 0f

    val requestClose = {
        if (!closing) {
            closing = true
            scope.launch {
                if (canMorph) {
                    animate(
                        initialValue = backDrag,
                        targetValue = 1f,
                        animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
                    ) { v, _ ->
                        backDrag = v
                        onDragProgress(v)
                    }
                }
                onBack()
            }
        }
    }

    PredictiveBack(
        onProgress = { p ->
            backDrag = p
            onDragProgress(p)
        },
        onCancel = {
            scope.launch {
                animate(
                    initialValue = backDrag,
                    targetValue = 0f,
                    animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
                ) { v, _ ->
                    backDrag = v
                    onDragProgress(v)
                }
            }
        },
        onBack = requestClose,
    )

    CompositionLocalProvider(LocalNow provides nowState) { IceBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = requestClose,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(g.veil),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = if (wide) 14.dp else 10.dp,
                        vertical = 10.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
// 只负责报最终矩形，**不带任何变换**（理由见上面 targetTop 的注释）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            val r = it.boundsInWindow()
                            if (targetTop.isNaN()) targetTop = r.top
                            if (selfRect != r) selfRect = r
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = if (targetTop.isNaN()) 0f else enter.value
                                transformOrigin = TransformOrigin(0f, 0f)
                                translationY = shift.value +
                                    lift.value * LIFT_DISTANCE.toPx()
                                val p = backDrag
                                val o = origin
                                if (o != null && selfRect.width > 0f && selfRect.height > 0f) {
                                    shellOf(1f - p, o, selfRect, shell)
                                    translationX = shell[0] - selfRect.left
                                    translationY += shell[1] - selfRect.top
                                    scaleX = shell[2]
                                    scaleY = shell[3]
                                } else {
                                    translationX = 0f
                                    scaleX = 1f
                                    scaleY = 1f
                                }
                            },
                    ) {
                        val morph = if (origin != null &&
                            partTitle != null && partNumber != null && partDate != null &&
                            selfRect.width > 0f && selfRect.height > 0f
                        ) {
                            RowMorph(
                                progress = { 1f - backDrag },
                                shell = shell,
                                origin = origin,
                                target = selfRect,
// 这几个只在 `destXxx` 为空时用，这里全给了值。
                                rowLeft = 0f,
                                rowTop = 0f,
                                rowWidth = 0f,
                                rowHeight = 0f,
                                rowPadH = 0f,
                                rowPadV = 0f,
                                rowTitlePx = 0f,
                                rowNumberPx = 0f,
                                rowSubtitlePx = 0f,
                                rowBarGap = 0f,
                                destTitle = partTitle,
                                destNumber = partNumber,
                                destSubtitle = partDate,
                            )
                        } else null
                        EventCard(
                            event = event,
                            info = info,
                            accent = accent,
                            modifier = Modifier.drawWithContent {
                                capture.record { this@drawWithContent.drawContent() }
                                drawLayer(capture)
                            },
                            numberHeight = displayNumberHeight(if (wide) 0.42f else 0.30f),
                            fillHeight = false,
                            numberMode = numberMode,
                            onNumberTap = {
                                modeOrdinal = numberMode.next().ordinal
                            },
                            onNumberLongPress = if (numberMode == CardNumberMode.DAY) {
                                { peek = !showSeconds }
                            } else {
                                null
                            },
                            secondsUnderNumber = true,
                            showSeconds = showSeconds,
// 三样各自搬回详情卡（见上面 `morph` 的说明）。
                            morph = morph,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = Gutter, bottom = 30.dp),
            ) {
                GlassPanel(
                    modifier = Modifier.pressable(
                        onClick = { if (!saving) doSave() },
                        scale = 0.96f,
                    ),
                    shape = RoundedCornerShape(Radius.panel),
// 压在模糊背景上，比卡片薄一档就够——它不是主角
                    weight = 0.62f,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = RowInset, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (saving) "正在保存…" else "保存为图片",
                            style = MaterialTheme.typography.labelMedium,
                            color = g.ink,
                        )
                    }
                }
            }

// 保存结果。停在按钮上方一行，两秒后自己消失。
            saveHint?.let { hint ->
                Text(
                    text = hint,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = Gutter, bottom = 82.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkMid,
                )
            }

            DaySeam(
                progress = { seam.value },
                color = seamColor,
            )
        }
    } }

    if (!hintSeen) {
        CardHintDialog(onDismiss = onHintSeen)
    }
}

@Composable
private fun CardHintDialog(onDismiss: () -> Unit) {
    val g = LocalGlass.current

    GlassDialog(onDismiss = onDismiss) { close ->
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(horizontal = CardInset, vertical = 20.dp)) {
                Text(
                    text = "怎么用",
                    style = MaterialTheme.typography.headlineSmall,
                    color = g.ink,
                )
                Spacer(Modifier.height(10.dp))

                HintLine("点数字", "换一种读法")
                HintLine("长按数字", "这一屏显示秒")
                HintLine("点别处", "返回")

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
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextAction(
                        text = "知道了",
                        onClick = { close { onDismiss() } },
                        emphasis = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun HintLine(gesture: String, effect: String) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = gesture,
            style = MaterialTheme.typography.bodyMedium,
            color = g.ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = effect,
            style = MaterialTheme.typography.bodyMedium,
            color = g.inkMid,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

