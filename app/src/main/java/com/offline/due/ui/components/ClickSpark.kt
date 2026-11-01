package com.offline.due.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val SPARK_COUNT = 6

private const val SPARK_NANOS = 340_000_000L

private val SPARK_TRAVEL = 21.dp

private val SPARK_LENGTH = 9.dp

private val SPARK_WIDTH = 1.4.dp

internal class SparkBurst(
    val bornAt: Long,
    val center: Offset,
    val angles: FloatArray,
    val scales: FloatArray,
    val color: Color,
)

class SparkState {

    internal val bursts = mutableStateListOf<SparkBurst>()

    private var counter = 0

    fun burst(at: Offset, color: Color) {
        val rnd = Random(counter * 7919 + 13)
        val angles = FloatArray(SPARK_COUNT)
        val scales = FloatArray(SPARK_COUNT)
// 每一轮整体转一点：连点同一个按钮，火花不会每次都长得一模一样
        val spin = (counter * 41 % 360) * PI.toFloat() / 180f
        for (i in 0 until SPARK_COUNT) {
            angles[i] = spin + i * (2f * PI.toFloat() / SPARK_COUNT) +
                rnd.nextDouble(-0.18, 0.18).toFloat()
            scales[i] = (0.72f + rnd.nextDouble(0.56)).toFloat()
        }
        bursts.add(SparkBurst(System.nanoTime(), at, angles, scales, color))
        counter++
    }
}

val LocalSpark = staticCompositionLocalOf<SparkState?> { null }

@Composable
fun SparkOverlay(state: SparkState, modifier: Modifier = Modifier) {
    var tick by remember { mutableLongStateOf(System.nanoTime()) }
    val idle = state.bursts.isEmpty()

    LaunchedEffect(idle) {
        if (idle) return@LaunchedEffect
        while (state.bursts.isNotEmpty()) {
            withFrameNanos { now ->
                tick = now
                state.bursts.removeAll { now - it.bornAt > SPARK_NANOS }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val now = tick
        val travel = SPARK_TRAVEL.toPx()
        val maxLen = SPARK_LENGTH.toPx()
        val width = SPARK_WIDTH.toPx()

        state.bursts.forEach { b ->
            val p = ((now - b.bornAt).toFloat() / SPARK_NANOS).coerceIn(0f, 1f)
// 三次方缓出：出手快、收尾慢。线性的话火花像被匀速"推"出去。
            val ease = 1f - (1f - p) * (1f - p) * (1f - p)
            val alpha = (1f - p) * (1f - p) * 0.62f
            if (alpha <= 0.01f) return@forEach

            val d = travel * ease
// 线段自身也在缩短，读起来像"被拉长之后耗散掉"
            val len = maxLen * (1f - 0.45f * p)

            for (i in b.angles.indices) {
                val a = b.angles[i]
                val l = len * b.scales[i]
                val dir = Offset(cos(a), sin(a))
                drawLine(
                    color = b.color.copy(alpha = alpha),
                    start = b.center + dir * (d - l * 0.5f),
                    end = b.center + dir * (d + l * 0.5f),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

