package com.offline.due.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,

    fontSize: TextUnit? = null,
    fontWeight: FontWeight? = null,

    durationMillis: Int = 280,
    tabular: Boolean = true,
) {
    val density = LocalDensity.current

// 字距：从样式里取出来，换算成 dp 交给容器；字本身清零。
    val tracking = remember(style, density) {
        val ls = style.letterSpacing
        if (ls.type == TextUnitType.Unspecified) {
            0.dp
        } else {
            with(density) { ls.toDp() }
        }
    }
    val baseStyle = remember(style, tabular, fontSize) {
        style.copy(
            letterSpacing = 0.sp,
            lineHeight = if (fontSize == null) style.lineHeight else TextUnit.Unspecified,
            fontFeatureSettings = if (tabular) "tnum" else style.fontFeatureSettings,
        )
    }

    val prev = remember { PrevText(text) }
    val rollingUp = remember(text) { rollsUp(prev.value, text) }
    SideEffect { prev.value = text }

    Row(
        modifier = modifier.clipToBounds(),
        horizontalArrangement = if (tracking == 0.dp) {
            Arrangement.Start
        } else {
            Arrangement.spacedBy(tracking)
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        text.forEachIndexed { index, ch ->
            key(index) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val slide = tween<IntOffset>(
                            durationMillis = durationMillis,
                            easing = FastOutSlowInEasing,
                        )
                        val fade = tween<Float>(durationMillis / 2)
                        (
                            slideInVertically(slide) { h -> if (rollingUp) h else -h } +
                                fadeIn(fade)
                            ).togetherWith(
                            slideOutVertically(slide) { h -> if (rollingUp) -h else h } +
                                fadeOut(fade)
                        )
                    },
                    label = "roll",
                ) { c ->
                    Text(
                        text = c.toString(),
                        style = baseStyle,
                        color = color,
                        fontSize = fontSize ?: TextUnit.Unspecified,
                        fontWeight = fontWeight,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private class PrevText(var value: String)

private fun rollsUp(prev: String, next: String): Boolean {
    val a = prev.toLongOrNull()
    val b = next.toLongOrNull()
    return if (a != null && b != null) b > a else next >= prev
}

