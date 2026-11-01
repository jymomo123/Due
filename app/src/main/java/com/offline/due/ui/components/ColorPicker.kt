package com.offline.due.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offline.due.data.CountdownEvent
import com.offline.due.ui.theme.ColorSlot
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Palette
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import com.offline.due.ui.theme.displaySp

@Composable
fun ColorPickerPage(
    event: CountdownEvent,
    slot: ColorSlot,
    onChange: (CountdownEvent) -> Unit,
    onBack: () -> Unit,
) {
    val g = LocalGlass.current
    val current = slot.read(event)
    val seed = if (current == CountdownEvent.NO_COLOR) event.color else current
    val initial = remember(slot) { argbToHsv(seed) }

    var hue by remember(slot) { mutableFloatStateOf(initial[0]) }
    var sat by remember(slot) { mutableFloatStateOf(initial[1]) }
    var valo by remember(slot) { mutableFloatStateOf(initial[2]) }

    val onPanel = rememberUpdatedState<(Float, Float) -> Unit> { x, y ->
        sat = x
        valo = 1f - y
        onChange(slot.write(event, hsvToArgb(hue, sat, valo)))
    }
    val onHue = rememberUpdatedState<(Float) -> Unit> { x ->
        hue = x * 360f
        onChange(slot.write(event, hsvToArgb(hue, sat, valo)))
    }
    val onShade = rememberUpdatedState<(Float) -> Unit> { t ->
        valo = t
        onChange(slot.write(event, hsvToArgb(hue, sat, valo)))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        KeyBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 12.dp),
        ) {
            KeyCap(text = "返回", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                text = slot.label,
                style = MaterialTheme.typography.titleMedium,
                color = g.inkMid,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .contentWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            SlotPreview(event = event, slot = slot)

            Spacer(Modifier.height(6.dp))
            Text(
                text = slot.hint,
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            HsvPanel(hue = hue, sat = sat, valo = valo) { x, y -> onPanel.value(x, y) }

            Spacer(Modifier.height(14.dp))

            ColorBar(label = "色相") {
                HueBar(hue = hue) { x -> onHue.value(x) }
            }

            Spacer(Modifier.height(10.dp))

            ColorBar(label = "深浅") {
                ShadeBar(hue = hue, sat = sat, valo = valo) { t -> onShade.value(t) }
            }

            Spacer(Modifier.height(14.dp))

            ColorReadout(color = current)

            if (slot.allowNone) {
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    label = "不着色",
                    value = if (current == CountdownEvent.NO_COLOR) "当前" else null,
                    onClick = { onChange(slot.write(event, CountdownEvent.NO_COLOR)) },
                    hint = "回到纯玻璃，不加任何染色",
                )
            }

// ── 预设 ──────────────────────────────────────────
            Spacer(Modifier.height(22.dp))
            SectionLabel(text = "预设")
            Spacer(Modifier.height(2.dp))
            PresetGrid(
                presets = CountdownEvent.COLOR_PRESETS,
                selected = current,
                onPick = { picked ->
                    val hsv = argbToHsv(picked)
                    hue = hsv[0]
                    sat = hsv[1]
                    valo = hsv[2]
                    onChange(slot.write(event, picked))
                },
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
internal fun ColorBar(label: String, bar: @Composable () -> Unit) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = g.inkSoft,
            modifier = Modifier.widthIn(min = 30.dp),
            maxLines = 1,
        )
        Box(modifier = Modifier.weight(1f)) { bar() }
    }
}

@Composable
fun ColorRow(
    label: String,
    color: Long,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
// 整行不缩放，只上底纹（0.992 那版按下去是什么都没有的）
            .pressable(onClick, scale = 1f, specular = false)
            .padding(horizontal = RowInset, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = g.ink,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(if (color == CountdownEvent.NO_COLOR) Color.Transparent else Color(color))
                .border(
                    1.dp,
                    if (color == CountdownEvent.NO_COLOR) g.stroke.copy(alpha = 0.8f)
                    else Color.Black.copy(alpha = 0.12f),
                    CircleShape,
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = CountdownEvent.colorName(color),
            style = MaterialTheme.typography.titleMedium,
            color = g.inkMid,
        )
        Spacer(Modifier.width(6.dp))
// 同上：只取字号，不换整个 `TextStyle`（那会带上行高，撑高这一行）。
        Text(
            text = "›",
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            color = g.inkSoft,
        )
    }
}

@Composable
private fun SlotPreview(event: CountdownEvent, slot: ColorSlot) {
    val g = LocalGlass.current
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.panel),
        weight = 0.7f,
        tint = Palette.card(event),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = RowInset, vertical = 15.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color(event.color), RoundedCornerShape(Radius.hair))
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "18",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = displaySp(52.sp),
                    lineHeight = displaySp(56.sp),
                ),
                color = Palette.number(event, g),
            )
            Text(
                text = "天",
                style = MaterialTheme.typography.titleMedium,
                color = g.inkMid,
                modifier = Modifier.padding(start = 5.dp, bottom = 7.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = slot.label,
                style = MaterialTheme.typography.labelSmall,
                color = g.inkSoft,
            )
        }
    }
}

