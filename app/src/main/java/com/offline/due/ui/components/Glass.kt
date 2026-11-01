package com.offline.due.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.offline.due.data.Backdrop
import com.offline.due.data.BgStyle
import com.offline.due.data.CardImage
import com.offline.due.ui.theme.GlassTokens
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Radius
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    style: BgStyle = BgStyle.ICE,
    solidColor: Color = Color.Unspecified,
    imageName: String? = null,
    scrim: Float = 0.34f,

    photo: ImageBitmap? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        BackdropFill(
            style = style,
            solidColor = solidColor,
            imageName = imageName,
            scrim = scrim,
            modifier = Modifier.fillMaxSize(),
            photo = photo,
        )
        content()
    }
}

@Composable
fun backdropBrush(style: BgStyle, solidColor: Color): Brush {
    val g = LocalGlass.current
    return remember(style, solidColor, g.isDark, g.gradient) {
        if (style == BgStyle.SOLID) {
            SolidColor(tamedSolid(solidColor, g))
        } else {
            Brush.verticalGradient(g.gradient)
        }
    }
}

private fun tamedSolid(solidColor: Color, g: GlassTokens): Color {
    val src = if (solidColor == Color.Unspecified) g.scrim else solidColor
    return Color(tameBackdropColor(src.toArgb().toLong() and 0xFFFFFFFFL, g.isDark))
}

@Composable
fun pageBaseColor(style: BgStyle, solidColor: Color): Color {
    val g = LocalGlass.current
    return remember(style, solidColor, g.isDark, g.gradient, g.scrim) {
        when (style) {
            BgStyle.SOLID -> tamedSolid(solidColor, g)
            BgStyle.IMAGE -> g.scrim
            BgStyle.ICE -> g.gradient.getOrNull(1) ?: g.scrim
        }
    }
}

@Composable
fun backdropVeilBrush(scrim: Float): Brush {
    val g = LocalGlass.current
    return remember(scrim, g.scrim) { scrimBrush(scrim, g.scrim) }
}

fun DrawScope.drawBackdropBase(
    base: Brush,
    photo: ImageBitmap?,
    veil: Brush?,
    alpha: Float,
) {
    if (alpha <= 0f) return

    if (photo == null || photo.width <= 0 || photo.height <= 0) {
        drawRect(brush = base, alpha = alpha)
        return
    }

    val scale = max(size.width / photo.width.toFloat(), size.height / photo.height.toFloat())
    val w = photo.width * scale
    val h = photo.height * scale
    drawImage(
        image = photo,
        dstOffset = IntOffset(
            ((size.width - w) / 2f).roundToInt(),
            ((size.height - h) / 2f).roundToInt(),
        ),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
    if (veil != null) drawRect(brush = veil, alpha = alpha)
}

@Composable
fun BackdropFill(
    style: BgStyle,
    solidColor: Color,
    imageName: String?,
    scrim: Float,
    modifier: Modifier = Modifier,

    photo: ImageBitmap? = null,
) {
    val g = LocalGlass.current
    val loaded = rememberBackdrop(imageName, skip = photo != null)
    val image = photo ?: loaded
    val hasPhoto = style == BgStyle.IMAGE && image != null

    val base = backdropBrush(style, solidColor)
    val veil = remember(scrim, g.scrim) { scrimBrush(scrim, g.scrim) }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawBackdropBase(
                        base = base,
                        photo = if (hasPhoto) image else null,
                        veil = if (hasPhoto) veil else null,
                        alpha = 1f,
                    )
                }
        )

        if (!hasPhoto && style != BgStyle.SOLID) {
            Aurora(
                colors = g.aurora,
                modifier = Modifier.fillMaxSize(),
// 帘幕要盖住上半屏。太矮的话它整片藏在最不透明的玻璃后面，等于没画。
                heightFraction = 0.78f,
                speed = 1f,
// 波长放大一点，帘幕更宽、更平缓，不会像一团彩色的雾
                scale = 1.25f,
                layers = 3,
                opacity = g.auroraOpacity,
            )
        }
    }
}

private fun scrimBrush(scrim: Float, veil: Color): Brush {
    val base = scrim.coerceIn(0f, 1f)
// 下限 0.10：遮罩拖到最左边也不是"没有"，否则深色照片上的墨字会彻底消失
    return Brush.verticalGradient(
        0f to veil.copy(alpha = (base * 0.92f + 0.12f).coerceAtMost(0.97f)),
        0.44f to veil.copy(alpha = (base * 0.58f + 0.04f).coerceAtMost(0.95f)),
        1f to veil.copy(alpha = (base * 0.84f + 0.08f).coerceAtMost(0.96f)),
    )
}

@Composable
fun rememberBackdrop(name: String?, skip: Boolean = false): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val target = with(density) { screenWidthDp.dp.roundToPx() }

    val state = produceState<ImageBitmap?>(null, name, target, skip) {
        value = if (skip || name.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { Backdrop.load(context, name, target) }
        }
    }
    return state.value
}

@Composable
fun rememberCardPhoto(name: String?, maxWidthPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(null, name, maxWidthPx) {
        value = if (name.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { CardImage.load(context, name, maxWidthPx) }
        }
    }
    return state.value
}

@Composable
fun cardScrimBrush(): Brush {
    val g = LocalGlass.current
    val veil = g.scrim
    return remember(veil) {
        Brush.verticalGradient(
            0f to veil.copy(alpha = 0.66f),
            0.42f to veil.copy(alpha = 0.36f),
            1f to veil.copy(alpha = 0.58f),
        )
    }
}

@Composable
fun IceBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), content = content)
}

val LocalFrosted = staticCompositionLocalOf { false }

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.card),

    weight: Float = 0.6f,

    elevation: Dp? = null,

    tint: Color? = null,

    glareKey: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlass.current
    val glare = rememberGlare(glareKey)

    val frosted = LocalFrosted.current
    val top = if (frosted) {
        g.frostTop
    } else {
        g.surfaceTop.copy(alpha = (g.surfaceTop.alpha * (0.7f + weight * 0.5f)).coerceAtMost(1f))
    }
    val bottom = if (frosted) {
        g.frostBottom
    } else {
        g.surfaceBottom.copy(alpha = (g.surfaceBottom.alpha * (0.6f + weight * 0.5f)).coerceAtMost(1f))
    }

// 深色底上的染色要更重才看得出来；浅色底上则要克制，否则玻璃变塑料
    val tintAlpha = if (g.isDark) 0.32f else 0.26f
    val edgeLight = if (tint == null) g.edgeLight else lerp(g.edgeLight, tint, 0.28f)

    val fill = remember(top, bottom) { Brush.verticalGradient(listOf(top, bottom)) }
    val tintFill = remember(tint, tintAlpha) {
        tint?.let {
            Brush.verticalGradient(
                listOf(it.copy(alpha = tintAlpha), it.copy(alpha = tintAlpha * 0.40f))
            )
        }
    }
    val edge = remember(edgeLight, g.edgeDark) {
        Brush.linearGradient(
            0f to edgeLight.copy(alpha = edgeLight.alpha * 0.62f),
            0.4f to edgeLight.copy(alpha = edgeLight.alpha * 0.19f),
            1f to g.edgeDark,
        )
    }
    val cornerGlow = when {
        frosted && g.isDark -> 0.10f
        frosted -> 0.13f
        g.isDark -> 0.065f
        else -> 0.10f
    }

    val lift = if (g.isDark) 0.dp else (elevation ?: (weight * weight * 7f).dp)

    Box(
        modifier
            .then(
                if (lift > 0.dp) {
                    Modifier.shadow(
                        elevation = lift,
                        shape = shape,
// clip = false：阴影要画到面板外面去，裁掉就没了
                        clip = false,
                        ambientColor = g.ink.copy(alpha = 0.10f),
                        spotColor = g.ink.copy(alpha = 0.18f),
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(fill)
            .then(if (tintFill == null) Modifier else Modifier.background(tintFill))
            .drawWithCache {
                val radius = when (val o = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
                    else -> 0f
                }
                val highlight = Brush.linearGradient(
                    0f to g.edgeLight.copy(
                        alpha = g.edgeLight.alpha * if (frosted) 0.45f else 0.15f,
                    ),
                    0.74f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.9f, size.height * 1.15f),
                )
                val glowRadius = (radius * 3.2f).coerceAtLeast(1f)
                fun glowAt(x: Float, y: Float) = Brush.radialGradient(
                    0f to Color.White.copy(alpha = cornerGlow),
                    1f to Color.Transparent,
                    center = Offset(x, y),
                    radius = glowRadius,
                )
                val tl = glowAt(radius, radius)
                val tr = glowAt(size.width - radius, radius)
                val bl = glowAt(radius, size.height - radius)
                val br = glowAt(size.width - radius, size.height - radius)

                val footH = 5.dp.toPx()
                val foot = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to g.ink.copy(alpha = if (g.isDark) 0.055f else 0.05f),
                    startY = size.height - footH,
                    endY = size.height,
                )

                onDrawBehind {
                    drawRect(highlight)
                    drawCircle(tl, radius = glowRadius, center = Offset(radius, radius))
                    drawCircle(tr, radius = glowRadius, center = Offset(size.width - radius, radius))
                    drawCircle(bl, radius = glowRadius, center = Offset(radius, size.height - radius))
                    drawCircle(br, radius = glowRadius, center = Offset(size.width - radius, size.height - radius))
                    drawRect(foot)
                }
            }
// 扫光画在内容之上、描边之下。在绘制阶段读 glare.value，只重绘不重组。
            .drawWithContent {
                drawContent()
                drawGlare(glare.value, Color.White, if (g.isDark) 0.10f else 0.34f)
            }
            .border(width = 1.dp, brush = edge, shape = shape),
        content = content,
    )
}

@Composable
fun GlassInset(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.panel),
    content: @Composable ColumnScope.() -> Unit,
) {
    val g = LocalGlass.current
    val topAlpha = if (g.isDark) 0.14f else 0.028f
    val bottomAlpha = if (g.isDark) 0.07f else 0.010f
    val shadeAlpha = if (g.isDark) 0.22f else 0.050f

    val fill = remember(g.ink, topAlpha, bottomAlpha) {
        Brush.verticalGradient(
            listOf(g.ink.copy(alpha = topAlpha), g.ink.copy(alpha = bottomAlpha))
        )
    }

    Column(
        modifier
            .clip(shape)
            .background(fill)
            .drawWithCache {
                val shade = Brush.verticalGradient(
                    0f to g.ink.copy(alpha = shadeAlpha),
                    1f to Color.Transparent,
                    endY = 9.dp.toPx(),
                )
                onDrawBehind { drawRect(shade) }
            }
            .border(1.dp, g.stroke.copy(alpha = g.stroke.alpha * 0.55f), shape),
        content = content,
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier, strong: Boolean = false) {
    val g = LocalGlass.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (strong) g.stroke else g.hairline)
    )
}

