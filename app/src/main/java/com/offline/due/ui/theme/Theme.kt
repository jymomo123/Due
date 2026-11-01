package com.offline.due.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.offline.due.R
import com.offline.due.data.BgTone
import com.offline.due.data.TextScale
import com.offline.due.data.ThemeMode

@Immutable
data class GlassTokens(

    val gradient: List<Color>,

    val surfaceTop: Color,
    val surfaceBottom: Color,

    val frostTop: Color,
    val frostBottom: Color,

    val edgeLight: Color,
    val edgeDark: Color,

    val hairline: Color,

    val stroke: Color,

    val ink: Color,
    val inkMid: Color,
    val inkSoft: Color,

    val accent: Color,
    val accentSoft: Color,

    val contrast: Color,

    val onContrast: Color,

    val aurora: List<Color>,

    val auroraOpacity: Float,

    val grainAlpha: Float,

    val inverse: Color,
    val onInverse: Color,

    val veil: Color,
    val isDark: Boolean,
) {

    val scrim: Color
        get() = gradient.lastOrNull() ?: if (isDark) Color.Black else Color.White
}

private val LightGlass = GlassTokens(
    gradient = listOf(Color(0xFFF7FBFF), Color(0xFFE8F1FC), Color(0xFFD8E7F9)),
    surfaceTop = Color(0xFFFFFFFF).copy(alpha = 0.68f),
    surfaceBottom = Color(0xFFFFFFFF).copy(alpha = 0.44f),
    frostTop = Color(0xFFFFFFFF).copy(alpha = 0.90f),
    frostBottom = Color(0xFFFFFFFF).copy(alpha = 0.78f),
    edgeLight = Color(0xFFFFFFFF).copy(alpha = 0.95f),
    edgeDark = Color(0xFF7FA8D6).copy(alpha = 0.22f),
    hairline = Color(0xFFFFFFFF).copy(alpha = 0.72f),
    stroke = Color(0xFF7FA8D6).copy(alpha = 0.26f),
    ink = Color(0xFF14263C),
    inkMid = Color(0xFF33506E),
    inkSoft = Color(0xFF4B6785),
    accent = Color(0xFF2F76F0),
    accentSoft = Color(0xFFBBD5F7),
    contrast = Color(0xFFC7385E),
    onContrast = Color(0xFFFFFFFF),
    aurora = listOf(
        Color(0xFFA9CCF5),
        Color(0xFFBECBF0),
        Color(0xFFA8D8DE),
    ),
    auroraOpacity = 1f,
    grainAlpha = 0.040f,
    inverse = Color(0xFF14263C),
    onInverse = Color(0xFFF2F8FF),
    veil = Color(0xFF122A45).copy(alpha = 0.24f),
    isDark = false,
)

private val DarkGlass = GlassTokens(
    gradient = listOf(Color(0xFF0B1622), Color(0xFF101E2C), Color(0xFF09121C)),
    surfaceTop = Color(0xFFFFFFFF).copy(alpha = 0.15f),
    surfaceBottom = Color(0xFFFFFFFF).copy(alpha = 0.095f),
    frostTop = Color(0xFF2A3A4D).copy(alpha = 0.92f),
    frostBottom = Color(0xFF22303F).copy(alpha = 0.86f),
    edgeLight = Color(0xFFFFFFFF).copy(alpha = 0.18f),
    edgeDark = Color(0xFF6E9BC8).copy(alpha = 0.10f),
    hairline = Color(0xFFFFFFFF).copy(alpha = 0.11f),
    stroke = Color(0xFFFFFFFF).copy(alpha = 0.20f),
    ink = Color(0xFFE8F1FA),
    inkMid = Color(0xFF93AAC4),
    inkSoft = Color(0xFF6E88A5),
    accent = Color(0xFF6BA6FF),
    accentSoft = Color(0xFF2A4A70),
    contrast = Color(0xFFFF6B92),
    onContrast = Color(0xFF0B1622),
    aurora = listOf(
        Color(0xFF2E5C8A),
        Color(0xFF34456F),
        Color(0xFF22545C),
    ),
    auroraOpacity = 1f,
    grainAlpha = 0.055f,
    inverse = Color(0xFFE8F1FA),
    onInverse = Color(0xFF0B1622),
    veil = Color(0xFF03080E).copy(alpha = 0.46f),
    isDark = true,
)

private val BlossomLight = GlassTokens(
    gradient = listOf(Color(0xFFFFF7FA), Color(0xFFFCE8EE), Color(0xFFF9D8E3)),
    surfaceTop = Color(0xFFFFFFFF).copy(alpha = 0.68f),
    surfaceBottom = Color(0xFFFFFFFF).copy(alpha = 0.44f),
    frostTop = Color(0xFFFFFFFF).copy(alpha = 0.90f),
    frostBottom = Color(0xFFFFFFFF).copy(alpha = 0.78f),
    edgeLight = Color(0xFFFFFFFF).copy(alpha = 0.95f),
    edgeDark = Color(0xFFD67F9C).copy(alpha = 0.22f),
    hairline = Color(0xFFFFFFFF).copy(alpha = 0.72f),
    stroke = Color(0xFFD67F9C).copy(alpha = 0.26f),
    ink = Color(0xFF3C1421),
    inkMid = Color(0xFF6E3348),
    inkSoft = Color(0xFF854B5F),
    accent = Color(0xFFD14A75),
    accentSoft = Color(0xFFF7BBCD),
    contrast = Color(0xFF266BD9),
    onContrast = Color(0xFFFFFFFF),
    aurora = listOf(
        Color(0xFFF5A9C2),
        Color(0xFFF0BEC4),
        Color(0xFFDEA8D1),
    ),
    auroraOpacity = 1f,
    grainAlpha = 0.040f,
    inverse = Color(0xFF3C1421),
    onInverse = Color(0xFFFFF2F6),
    veil = Color(0xFF451223).copy(alpha = 0.24f),
    isDark = false,
)

private val BlossomDark = GlassTokens(
    gradient = listOf(Color(0xFF220B13), Color(0xFF2C101A), Color(0xFF1C090F)),
    surfaceTop = Color(0xFFFFFFFF).copy(alpha = 0.15f),
    surfaceBottom = Color(0xFFFFFFFF).copy(alpha = 0.095f),
// 深色主题里"浮起来"是"更亮"，所以这两个是比背景亮的深粉，不是白色加浓
    frostTop = Color(0xFF4D2A35).copy(alpha = 0.92f),
    frostBottom = Color(0xFF3F222C).copy(alpha = 0.86f),
    edgeLight = Color(0xFFFFFFFF).copy(alpha = 0.18f),
    edgeDark = Color(0xFFC86E8F).copy(alpha = 0.10f),
    hairline = Color(0xFFFFFFFF).copy(alpha = 0.11f),
    stroke = Color(0xFFFFFFFF).copy(alpha = 0.20f),
    ink = Color(0xFFFAE8EF),
    inkMid = Color(0xFFC493A3),
// 旋转给出 #A56E81，在 #2C101A 上只有 4.30——低于 AA，这里提一档到 5.02
    inkSoft = Color(0xFFB07A8C),
    accent = Color(0xFFFF6B92),
    accentSoft = Color(0xFF702A41),
// 淡粉深色底上的反差色：亮宝蓝，字用最深的墨（7.40:1）。
    contrast = Color(0xFF6BA6FF),
    onContrast = Color(0xFF0B1622),
    aurora = listOf(
        Color(0xFF8A2E50),
        Color(0xFF6F343D),
        Color(0xFF5C224C),
    ),
    auroraOpacity = 1f,
    grainAlpha = 0.055f,
    inverse = Color(0xFFFAE8EF),
    onInverse = Color(0xFF220B13),
    veil = Color(0xFF0E0307).copy(alpha = 0.46f),
    isDark = true,
)

fun glassFor(dark: Boolean, tone: BgTone): GlassTokens = when {
    dark && tone == BgTone.BLOSSOM -> BlossomDark
    dark -> DarkGlass
    tone == BgTone.BLOSSOM -> BlossomLight
    else -> LightGlass
}

val LocalGlass = compositionLocalOf { LightGlass }

val LocalTextScale = compositionLocalOf { 1f }

private fun scaleUnit(value: TextUnit, k: Float): TextUnit =
    if (value.isUnspecified) value else value * k

private fun scaleStyle(style: TextStyle, k: Float): TextStyle = style.copy(
    fontSize = scaleUnit(style.fontSize, k),
    lineHeight = scaleUnit(style.lineHeight, k),
// 字距是绝对值（sp），也要跟着走：字放大了字距不动，中文会显得更挤。
    letterSpacing = scaleUnit(style.letterSpacing, k),
)

private fun scaledTypography(base: Typography, k: Float): Typography = Typography(
    displayLarge = scaleStyle(base.displayLarge, k),
    displayMedium = scaleStyle(base.displayMedium, k),
    displaySmall = scaleStyle(base.displaySmall, k),
    headlineLarge = scaleStyle(base.headlineLarge, k),
    headlineMedium = scaleStyle(base.headlineMedium, k),
    headlineSmall = scaleStyle(base.headlineSmall, k),
    titleLarge = scaleStyle(base.titleLarge, k),
    titleMedium = scaleStyle(base.titleMedium, k),
    titleSmall = scaleStyle(base.titleSmall, k),
    bodyLarge = scaleStyle(base.bodyLarge, k),
    bodyMedium = scaleStyle(base.bodyMedium, k),
    bodySmall = scaleStyle(base.bodySmall, k),
    labelLarge = scaleStyle(base.labelLarge, k),
    labelMedium = scaleStyle(base.labelMedium, k),
    labelSmall = scaleStyle(base.labelSmall, k),
)

private fun lightScheme(g: GlassTokens) = lightColorScheme(
    primary = g.accent,
    onPrimary = Color.White,
    secondary = g.inkMid,
    onSecondary = Color.White,
    background = g.gradient[1],
    onBackground = g.ink,
    surface = g.surfaceTop,
    onSurface = g.ink,
    surfaceVariant = g.inkSoft,
    onSurfaceVariant = g.inkMid,
    outline = g.stroke,
    outlineVariant = g.hairline,
    error = Color(0xFFC64A3E),
    onError = Color.White,
)

private fun darkScheme(g: GlassTokens) = darkColorScheme(
    primary = g.accent,
    onPrimary = Color(0xFF06101A),
    secondary = g.inkMid,
    onSecondary = Color(0xFF06101A),
    background = g.gradient[1],
    onBackground = g.ink,
    surface = g.surfaceTop,
    onSurface = g.ink,
    surfaceVariant = g.inkSoft,
    onSurfaceVariant = g.inkMid,
    outline = g.stroke,
    outlineVariant = g.hairline,
    error = Color(0xFFFF7B6E),
    onError = Color(0xFF2A0906),
)

private val DueSans = FontFamily(
    Font(R.font.noto_sc_400, FontWeight.Normal),
    Font(R.font.noto_sc_500, FontWeight.Medium),
    Font(R.font.noto_sc_700, FontWeight.Bold),
    Font(R.font.noto_sc_900, FontWeight.Black),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DueSans,
        fontSize = 46.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DueSans,
        fontSize = 88.sp,
        lineHeight = 92.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-4.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = DueSans,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.2).sp,
    ),
// ── 标题 ──────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = DueSans,
        fontSize = 32.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DueSans,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DueSans,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
// ── 正文 ──────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = DueSans,
        fontSize = 21.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(
        fontFamily = DueSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = DueSans,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = DueSans,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = DueSans,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = DueSans,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
// ── 标签 ──────────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = DueSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = DueSans,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
// 字号涨了，字距就得收一点：1.8sp 在 10.5sp 上刚好，在 11.5sp 上会散架
        letterSpacing = 1.6.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = DueSans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
// 等宽数字：秒数每秒变化时，整行不会左右抖动
        fontFeatureSettings = "tnum",
    ),
)

@Composable
fun CountdownTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,

    tone: BgTone = BgTone.ICE,

    textScale: TextScale = TextScale.NORMAL,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val glass = glassFor(dark, tone)
    val typography = remember(textScale) {
        if (textScale.factor == 1f) AppTypography
        else scaledTypography(AppTypography, textScale.factor)
    }
    CompositionLocalProvider(
        LocalGlass provides glass,
        LocalTextScale provides textScale.factor,
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(glass) else lightScheme(glass),
            typography = typography,
            content = content,
        )
    }
}

