package com.offline.due.domain

import kotlin.math.pow

object ColorMath {

    fun luminance(argb: Int): Float {
        fun lin(c: Int): Float {
            val s = c / 255f
            return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
        }
        val r = lin((argb shr 16) and 0xFF)
        val g = lin((argb shr 8) and 0xFF)
        val b = lin(argb and 0xFF)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun readable(
        argb: Int,
        dark: Boolean,

        floor: Float = 0.42f,

        ceiling: Float = 0.28f,
    ): Int {
        val l = luminance(argb)

        if (dark) {
            if (l >= floor) return argb
// 走完剩余亮度差的全部路程，而不是固定混 30%
            val t = ((floor - l) / (1f - l)).coerceIn(0f, 1f)
            fun up(c: Int) = (c + (255 - c) * t).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or
                (up((argb shr 16) and 0xFF) shl 16) or
                (up((argb shr 8) and 0xFF) shl 8) or
                up(argb and 0xFF)
        }

// l <= 0 是纯黑，本来就够暗；也避免下面除零
        if (l <= ceiling || l <= 0f) return argb

        fun scaled(k: Float): Int {
            fun f(c: Int) = (c * k).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or
                (f((argb shr 16) and 0xFF) shl 16) or
                (f((argb shr 8) and 0xFF) shl 8) or
                f(argb and 0xFF)
        }

        var lo = 0f
        var hi = 1f
        repeat(8) {
            val mid = (lo + hi) * 0.5f
            if (luminance(scaled(mid)) > ceiling) hi = mid else lo = mid
        }
        return scaled(lo)
    }
}

