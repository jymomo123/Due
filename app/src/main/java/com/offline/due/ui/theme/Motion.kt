package com.offline.due.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

object Motion {

    const val Press = 120

    const val Exit = 180

    const val Enter = 260

    const val Page = 280

    const val Settle = 190

    val enterEasing: Easing = LinearOutSlowInEasing

    val exitEasing: Easing = FastOutLinearInEasing

    val handover: Easing = Easing { t ->
        val k = ((t - 1f / 3f) / (2f / 3f)).coerceIn(0f, 1f)
        k * k * (3f - 2f * k)
    }

    val emphasized: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> press(): SpringSpec<T> =
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

    fun <T> settle(): SpringSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    fun <T> glide(): SpringSpec<T> =
        spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)

    fun <T> bounce(): SpringSpec<T> =
        spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMedium)
}

