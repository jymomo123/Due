package com.offline.due.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import com.offline.due.ui.theme.Motion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offline.due.data.Backdrop
import com.offline.due.data.BgStyle
import com.offline.due.data.BgTone
import com.offline.due.data.CountdownEvent
import com.offline.due.data.Settings
import com.offline.due.data.SettingsStore
import com.offline.due.ui.components.BackdropFill
import com.offline.due.ui.components.CardInset
import com.offline.due.ui.components.ChoiceDialog
import com.offline.due.ui.components.ColorBar
import com.offline.due.ui.components.ColorReadout
import com.offline.due.ui.components.GlassInset
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.RowInset
import com.offline.due.ui.components.HsvPanel
import com.offline.due.ui.components.HueBar
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.InsetLine
import com.offline.due.ui.components.KeyBar
import com.offline.due.ui.components.KeyCap
import com.offline.due.ui.components.PresetGrid
import com.offline.due.ui.components.SectionLabel
import com.offline.due.ui.components.SegmentedChoice
import com.offline.due.ui.components.SettingRow
import com.offline.due.ui.components.ShadeBar
import com.offline.due.ui.components.SliderRow
import com.offline.due.ui.components.argbToHsv
import com.offline.due.ui.components.backdropValueBand
import com.offline.due.ui.components.hsvToArgb
import com.offline.due.ui.components.rememberSubPageBack
import com.offline.due.ui.components.subPageSlide
import com.offline.due.ui.components.tameBackdropColor
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import com.offline.due.ui.theme.displaySp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackdropScreen(
    settings: Settings,
    onBack: () -> Unit,

    onSubBack: (Boolean) -> Unit = {},
) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingColor by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var pickTone by remember { mutableStateOf(false) }

    val syncToneToBackdrop = {
        val cur = SettingsStore.state.value
        if (cur.bgToneAuto && cur.bgStyle == BgStyle.SOLID) {
            val want = BgTone.fromColor(cur.bgColor)
            if (want != cur.bgTone) SettingsStore.setBgTone(want, manual = false)
        }
    }

    val subP = rememberSubPageBack(
        enabled = editingColor,
        onClose = {
            editingColor = false
// 和取色页右上角那个「返回」走同一段：离开取色页时把整套色相跟上。
            syncToneToBackdrop()
        },
    )
    LaunchedEffect(editingColor) { onSubBack(editingColor) }

    DisposableEffect(Unit) { onDispose { onSubBack(false) } }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            if (SettingsStore.state.value.bgImage == null) {
                SettingsStore.setBgStyle(BgStyle.ICE)
            }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importing = true
            failed = false
            val name = withContext(Dispatchers.IO) { Backdrop.import(context, uri) }
            importing = false
            if (name != null) {
                SettingsStore.setBgImage(name)
                SettingsStore.setBgStyle(BgStyle.IMAGE)
            } else {
                failed = true
            }
        }
    }

    val launchPicker = {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    AnimatedContent(
        targetState = editingColor,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        transitionSpec = {
            if (targetState) {
                (
                    slideInHorizontally(
                        tween(Motion.Page, easing = Motion.enterEasing)
                    ) { it } + fadeIn(tween(Motion.Enter, easing = Motion.enterEasing))
                    ).togetherWith(
                    fadeOut(tween(Motion.Exit, easing = Motion.exitEasing))
                )
            } else if (subP.driven) {
                fadeIn(tween(Motion.Settle, easing = Motion.handover))
                    .togetherWith(fadeOut(tween(Motion.Settle, easing = Motion.handover)))
            } else {
                fadeIn(tween(Motion.Enter, easing = Motion.enterEasing))
                    .togetherWith(
                        slideOutHorizontally(
                            tween(Motion.Page, easing = Motion.exitEasing)
                        ) { it } + fadeOut(tween(Motion.Exit, easing = Motion.exitEasing))
                    )
            }
        },
        label = "backdropPage",
    ) { editing ->
        if (editing) {
            Box(Modifier.fillMaxSize().subPageSlide(subP)) {
                BackdropColorPage(
                    initial = settings.bgColor,
                    dark = g.isDark,
                    onPick = { SettingsStore.setBgColor(it) },
                    onBack = {
                        editingColor = false
                        syncToneToBackdrop()
                    },
                )
            }
        } else {
            IceBackground {
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
                            text = "背景",
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
                        BackdropPreview(settings)

                        Spacer(Modifier.height(16.dp))

                        SegmentedChoice(
                            options = BgStyle.entries.map { it.label },
                            selected = settings.bgStyle.label,
                            onPick = { label ->
                                BgStyle.entries.firstOrNull { it.label == label }?.let { style ->
                                    SettingsStore.setBgStyle(style)
                                    if (style == BgStyle.IMAGE && settings.bgImage == null) {
                                        launchPicker()
                                    }
                                }
                            },
                        )

                        when (settings.bgStyle) {
                            BgStyle.ICE -> Text(
                                text = "原来那层冰：极淡的渐变，上面一层很慢的极光。",
                                style = MaterialTheme.typography.bodySmall,
                                color = g.inkSoft,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            )

                            BgStyle.SOLID -> {
                                SectionLabel("底色")
                                GlassInset(modifier = Modifier.fillMaxWidth()) {
                                    SettingRow(
                                        label = "颜色",
                                        value = backdropColorName(settings.bgColor),
                                        onClick = { editingColor = true },
                                        showChevron = true,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                PresetGrid(
                                    presets = backdropPresets(g.isDark),
                                    selected = settings.bgColor,
                                    onPick = {
                                        SettingsStore.setBgColor(tameBackdropColor(it, g.isDark))
                                        syncToneToBackdrop()
                                    },
                                )
                                Text(
                                    text = "亮度会自动收进可读范围——太深的底色会让整屏的字看不见。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = g.inkSoft,
                                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                                )
                            }

                            BgStyle.IMAGE -> {
                                SectionLabel("图片")
                                GlassInset(modifier = Modifier.fillMaxWidth()) {
                                    SettingRow(
                                        label = if (settings.bgImage == null) "选择图片" else "换一张",
                                        value = if (importing) "处理中…" else null,
                                        onClick = { if (!importing) launchPicker() },
                                        showChevron = true,
                                        hint = "会复制一份到 App 自己的目录，之后删掉相册原图也不影响",
                                    )
                                    if (settings.bgImage != null) {
                                        InsetLine()
                                        SettingRow(
                                            label = "移除图片",
                                            onClick = { Backdrop.remove(context) },
                                            destructive = true,
                                        )
                                    }
                                }
                                if (failed) {
                                    Text(
                                        text = "这张图读不出来，换一张试试",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                                    )
                                }
                            }
                        }

                        if (settings.bgStyle == BgStyle.IMAGE) {
                            SectionLabel("遮罩")
                            GlassInset(modifier = Modifier.fillMaxWidth()) {
                                SliderRow(
                                    label = "浓度",
                                    value = settings.bgScrim,
                                    onChange = { SettingsStore.setBgScrim(it) },
                                )
                            }
                            Text(
                                text = "往右拖，背景退得更远、字更清楚；往左拖，照片更明显。",
                                style = MaterialTheme.typography.bodySmall,
                                color = g.inkSoft,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            )
                        }

                        SectionLabel("配色", hint = "整屏的色相，玻璃、墨色、阴影都跟着它")
                        GlassInset(modifier = Modifier.fillMaxWidth()) {
                            SettingRow(
                                label = "色相",
                                value = settings.bgTone.label,
                                onClick = { pickTone = true },
                                showChevron = true,
                                hint = if (settings.bgToneAuto) {
                                    "现在会自动跟着底色走；你手动选过之后，就只听你的"
                                } else {
                                    "你手动选过，不再跟着底色变。想恢复自动，重新选一次底色"
                                },
                            )
                        }

                        Spacer(Modifier.height(56.dp))
                    }
                }
            }
        }
    }

    if (pickTone) {
        ChoiceDialog(
            title = "色相",
            options = BgTone.entries.map { it.label },
            selected = settings.bgTone.label,
            onPick = { label ->
                BgTone.entries.firstOrNull { it.label == label }?.let {
                    SettingsStore.setBgTone(it)
                }
            },
            onDismiss = { pickTone = false },
        )
    }
}

@Composable
private fun BackdropPreview(settings: Settings) {
    val g = LocalGlass.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
            .clip(RoundedCornerShape(Radius.panel)),
    ) {
        BackdropFill(
            style = settings.bgStyle,
            solidColor = Color(settings.bgColor),
            imageName = settings.bgImage,
            scrim = settings.bgScrim,
            modifier = Modifier.fillMaxSize(),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = CardInset)
                .fillMaxWidth(),
            shape = RoundedCornerShape(Radius.panel),
            weight = 0.7f,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = RowInset, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "18",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = displaySp(38.sp),
                        lineHeight = displaySp(42.sp),
                    ),
                    color = g.ink,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "天后",
                    style = MaterialTheme.typography.titleMedium,
                    color = g.inkMid,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(Radius.dot))
                        .background(g.accent.copy(alpha = 0.85f))
                )
            }
        }
    }
}

@Composable
private fun BackdropColorPage(
    initial: Long,
    dark: Boolean,
    onPick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val g = LocalGlass.current
    val seed = remember { argbToHsv(initial) }
    var hue by remember { mutableFloatStateOf(seed[0]) }
    var sat by remember { mutableFloatStateOf(seed[1]) }
    var valo by remember { mutableFloatStateOf(seed[2]) }

    val raw = hsvToArgb(hue, sat, valo)
    val shown = tameBackdropColor(raw, dark)

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
                text = "底色",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(Radius.panel)),
            ) {
                BackdropFill(
                    style = BgStyle.SOLID,
                    solidColor = Color(raw),
                    imageName = null,
                    scrim = 0f,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(16.dp))
            HsvPanel(hue = hue, sat = sat, valo = valo) { x, y ->
                sat = x
                valo = 1f - y
                onPick(tameBackdropColor(hsvToArgb(hue, sat, valo), dark))
            }
            Spacer(Modifier.height(14.dp))
            ColorBar(label = "色相") {
                HueBar(hue = hue) { x ->
                    hue = x * 360f
                    onPick(tameBackdropColor(hsvToArgb(hue, sat, valo), dark))
                }
            }
            Spacer(Modifier.height(10.dp))
            ColorBar(label = "深浅") {
                ShadeBar(
                    hue = hue,
                    sat = sat,
                    valo = valo,
                    vRange = backdropValueBand(dark),
                ) { v ->
                    valo = v
                    onPick(tameBackdropColor(hsvToArgb(hue, sat, valo), dark))
                }
            }
            Spacer(Modifier.height(14.dp))
            ColorReadout(color = shown, name = backdropColorName(shown))
            Text(
                text = "底色要在上面写字，太深太浅都压不住，所以这条只管安全的那一段。" +
                    "上面那块面板照旧可以拖到任意位置，超出部分会自动收回边缘。",
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("预设")
            Spacer(Modifier.height(2.dp))
            PresetGrid(
                presets = backdropPresets(dark),
                selected = shown,
                onPick = { picked ->
                    val hsv = argbToHsv(picked)
                    hue = hsv[0]
                    sat = hsv[1]
                    valo = hsv[2]
                    onPick(picked)
                },
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun backdropPresets(dark: Boolean): List<Long> = if (dark) {
    listOf(
        0xFF0B1622, // 夜
        0xFF111F2E, // 深海
        0xFF0E1A26, // 墨蓝
        0xFF132029, // 松烟
        0xFF161B2A, // 暗紫
        0xFF0D1F22, // 苔
        0xFF1A1A22, // 石
        0xFF08121C, // 渊
    )
} else {
    listOf(
        0xFFF7FBFF, // 雪
        0xFFEFF5FD, // 云
        0xFFE4EEFA, // 冰
        0xFFDCE9F9, // 冰川
        0xFFE9EDF5, // 雾
        0xFFE6F1EC, // 薄荷
        0xFFF2EDF7, // 藕荷
        0xFFF7F1E9, // 米
    )
}

private val LIGHT_NAMES = listOf("雪", "云", "冰", "冰川", "雾", "薄荷", "藕荷", "米")
private val DARK_NAMES = listOf("夜", "深海", "墨蓝", "松烟", "暗紫", "苔", "石", "渊")

private fun backdropColorName(argb: Long): String {
    backdropPresets(dark = false).indexOf(argb).let { if (it >= 0) return LIGHT_NAMES[it] }
    backdropPresets(dark = true).indexOf(argb).let { if (it >= 0) return DARK_NAMES[it] }
    return CountdownEvent.hexOf(argb)
}

