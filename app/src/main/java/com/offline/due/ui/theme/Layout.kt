package com.offline.due.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ContentMaxWidth: Dp = 560.dp

@Composable
fun Modifier.contentWidth(): Modifier = this
    .widthIn(max = ContentMaxWidth)
    .fillMaxWidth()

@Composable
fun displaySp(base: TextUnit): TextUnit {
    if (base.isUnspecified) return base
    val fontScale = LocalDensity.current.fontScale
    val scaled = base.value * LocalTextScale.current
// 1.02 是留一点余量：字体设置里的"默认"在某些 ROM 上不是精确的 1.0
    return if (fontScale <= 1.02f) scaled.sp else (scaled / fontScale).sp
}

@Composable
fun rememberWideLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp > config.screenHeightDp
}

val WideContentMaxWidth: Dp = 880.dp

@Composable
fun displayNumberHeight(fraction: Float): Dp {
    val config = LocalConfiguration.current
    return (config.screenHeightDp * fraction).dp
}

