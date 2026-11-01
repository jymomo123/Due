package com.offline.due.ui.components

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.offline.due.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PredictiveBack(
    enabled: Boolean = true,
    onProgress: (Float) -> Unit = {},
    onCancel: () -> Unit = {},

    onEdge: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }

    val gen = remember { intArrayOf(0) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PredictiveBackHandler(enabled = enabled) { events ->
            if (busy) return@PredictiveBackHandler
            val my = ++gen[0]
            try {
                var last = 0f
// 只报一次：同一场手势里 `swipeEdge` 不会变。
                var edgeReported = false
                events.collect {
                    if (!edgeReported) {
                        edgeReported = true
                        onEdge(it.swipeEdge)
                    }
                    last = it.progress.coerceIn(0f, 1f)
                    onProgress(last)
                }
                busy = true
                finishLift(last, onProgress)
                onBack()
            } catch (c: CancellationException) {
                if (my == gen[0]) onCancel()
                throw c
            } finally {
                if (my == gen[0]) busy = false
            }
        }
    } else {
        BackHandler(enabled = enabled) {
            if (busy) return@BackHandler
            busy = true
            val my = ++gen[0]
            scope.launch {
                try {
                    finishLift(0f, onProgress)
                    onBack()
                } finally {
                    if (my == gen[0]) busy = false
                }
            }
        }
    }
}

private suspend fun finishLift(from: Float, onProgress: (Float) -> Unit) {
    if (from > 0f) return
    animate(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = tween(Motion.Exit, easing = Motion.enterEasing),
    ) { v, _ -> onProgress(v) }
}

@Stable
class SubPageBack internal constructor(
    private val p: MutableFloatState,
    private val moved: MutableState<Boolean>,
    private val edgeDir: MutableFloatState,
) {

    val progress: Float get() = p.floatValue

    val driven: Boolean get() = moved.value

    val dir: Float get() = edgeDir.floatValue
}

@Composable
fun rememberSubPageBack(
    enabled: Boolean,
    onClose: () -> Unit,
): SubPageBack {
    val p = remember { mutableFloatStateOf(0f) }
    val moved = remember { mutableStateOf(false) }
// 往哪边退。默认往右（和整页那一层一致），`onEdge` 进来才改。
    val edgeDir = remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(enabled) { if (enabled) moved.value = false }

    PredictiveBack(
        enabled = enabled,
        onProgress = {
            p.floatValue = it
            if (it > 0f) moved.value = true
        },
// 从右边划进来就往左退。方向跟着手指，手才认得出"是我推的"。
        onEdge = { edge ->
            edgeDir.floatValue = if (edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        },
// 中途反悔：自己倒回 0。系统不会再报进度，也不会替我们收尾。
        onCancel = {
            moved.value = false
            scope.launch {
                animate(
                    initialValue = p.floatValue,
                    targetValue = 0f,
                    animationSpec = tween(Motion.Exit, easing = Motion.exitEasing),
                ) { v, _ -> p.floatValue = v }
            }
        },
        onBack = {
            onClose()
            scope.launch {
                animate(
                    initialValue = p.floatValue,
                    targetValue = 0f,
                    animationSpec = tween(Motion.Settle, easing = Motion.enterEasing),
                ) { v, _ -> p.floatValue = v }
            }
        },
    )
    return remember(p, moved, edgeDir) { SubPageBack(p, moved, edgeDir) }
}

fun Modifier.subPageSlide(p: SubPageBack, fraction: Float = 1f): Modifier =
    this.graphicsLayer {
        translationX = p.progress * size.width * fraction * p.dir
    }

