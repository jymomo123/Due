package com.offline.due.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DaySeam(

    progress: () -> Float,

    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val p = progress()
        if (p <= 0f || p >= 1f) return@Canvas

        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val band = w * BAND_FRACTION
// 左边缘：从 -band（整条在左侧之外）走到 w（整条在右侧之外）
        val left = -band + (w + band) * p

        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.26f to color.copy(alpha = GLOW_ALPHA * 0.38f),
                0.50f to color.copy(alpha = GLOW_ALPHA),
                0.74f to color.copy(alpha = GLOW_ALPHA * 0.38f),
                1f to Color.Transparent,
                startX = left,
                endX = left + band,
            ),
            topLeft = Offset(left, 0f),
            size = Size(band, h),
        )

        val coreHalf = CORE_HALF.toPx()
        val cx = left + band * 0.5f
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.09f to Color.White.copy(alpha = CORE_ALPHA * 0.65f),
                0.50f to Color.White.copy(alpha = CORE_ALPHA),
                0.91f to Color.White.copy(alpha = CORE_ALPHA * 0.65f),
                1f to Color.Transparent,
                startY = 0f,
                endY = h,
            ),
            topLeft = Offset(cx - coreHalf, 0f),
            size = Size(coreHalf * 2f, h),
        )
    }
}

private const val BAND_FRACTION = 0.34f

private const val GLOW_ALPHA = 0.13f

private val CORE_HALF = 0.8.dp

private const val CORE_ALPHA = 0.40f

