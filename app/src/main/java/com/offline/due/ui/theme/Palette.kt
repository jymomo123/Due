package com.offline.due.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.offline.due.data.CountdownEvent
import com.offline.due.domain.ColorMath

object Palette {

    fun card(event: CountdownEvent): Color? =
        if (event.cardColor == CountdownEvent.NO_COLOR) null else Color(event.cardColor)

    fun number(event: CountdownEvent, g: GlassTokens): Color {
        val raw = if (event.numberColor == CountdownEvent.NO_COLOR) g.ink else Color(event.numberColor)
        return Color(ColorMath.readable(raw.toArgb(), g.isDark))
    }
}

enum class ColorSlot(
    val label: String,

    val hint: String,

    val allowNone: Boolean,
) {
    BAR("竖条", "列表左边那条线，和详情页的进度条", false),
    CARD("卡片", "详情页那张主角卡片的底色", true),
    NUMBER("数字", "大数字本身的颜色", true);

    fun read(e: CountdownEvent): Long = when (this) {
        BAR -> e.color
        CARD -> e.cardColor
        NUMBER -> e.numberColor
    }

    fun write(e: CountdownEvent, value: Long): CountdownEvent = when (this) {
        BAR -> e.copy(color = value)
        CARD -> e.copy(cardColor = value)
        NUMBER -> e.copy(numberColor = value)
    }
}

