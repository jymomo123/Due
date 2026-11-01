package com.offline.due.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.offline.due.R
import com.offline.due.ui.components.CardInset
import com.offline.due.ui.components.GlassDialog
import com.offline.due.ui.components.GlassInset
import com.offline.due.ui.components.GlassPanel
import com.offline.due.ui.components.Gutter
import com.offline.due.ui.components.IceBackground
import com.offline.due.ui.components.InsetLine
import com.offline.due.ui.components.KeyBar
import com.offline.due.ui.components.KeyCap
import com.offline.due.ui.components.SegmentedChoice
import com.offline.due.ui.components.SettingRow
import com.offline.due.ui.components.pressable
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.Radius
import com.offline.due.ui.theme.contentWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GITHUB_URL = "https://github.com/jymomo123"
private const val GITHUB_NAME = "jymomo123"
private const val SITE_URL = "https://jymomo123.github.io/"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val versionName = rememberVersionName()

    var donate by remember { mutableStateOf(false) }
    var linkFailed by remember { mutableStateOf(false) }

    val openUrl: (String) -> Unit = { url ->
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        linkFailed = !ok
    }

    IceBackground {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        text = "关于如期",
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
                    IdentityCard(versionName = versionName)

                    Spacer(Modifier.height(16.dp))

                    GlassInset(modifier = Modifier.fillMaxWidth()) {
                        SettingRow(
                            label = "作者",
                            value = "机翼",
                            onClick = { openUrl(SITE_URL) },
                            showChevron = true,
                        )
                        InsetLine()
                        SettingRow(
                            label = "GitHub",
                            value = GITHUB_NAME,
                            onClick = { openUrl(GITHUB_URL) },
                            showChevron = true,
                        )
                    }

                    if (linkFailed) {
                        Text(
                            text = "这台设备上没有能打开链接的应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = g.inkSoft,
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                        )
                    }

                    Text(
                        text = "本软件为免费开源软件，不含广告、不做内购、不收集任何数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = g.inkSoft,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
                    )

                    Spacer(Modifier.height(96.dp))
                }
            }

            DonateKey(
                text = "捐赠",
                onClick = { donate = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = Gutter, bottom = 20.dp),
            )
        }
    }

    if (donate) {
        DonateDialog(onDismiss = { donate = false })
    }
}

@Composable
private fun DonateKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(Radius.panel)
    Box(
        modifier = modifier
            .clip(shape)
            .background(g.contrast)
            .border(1.dp, g.edgeLight.copy(alpha = 0.38f), shape)
            .pressable(onClick, scale = 0.94f, veil = g.onContrast)
            .padding(horizontal = Gutter, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = g.onContrast,
            maxLines = 1,
        )
    }
}

@Composable
private fun IdentityCard(versionName: String) {
    val g = LocalGlass.current
    val icon = rememberLauncherIcon()

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        weight = 0.55f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp, horizontal = CardInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LauncherIcon(icon, size = 72.dp)

            Spacer(Modifier.height(14.dp))

            Text(
                text = "如期",
                style = MaterialTheme.typography.titleLarge,
                color = g.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = versionName,
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
            )
        }
    }
}

@Composable
private fun LauncherIcon(icon: ImageBitmap?, size: Dp) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(Radius.panel)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .border(1.dp, g.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = "如期",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(size * 1.45f),
            )
        }
    }
}

@Composable
private fun rememberLauncherIcon(): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val px = (72 * context.resources.displayMetrics.density * 1.45f).toInt()
                    .coerceIn(96, 384)
                ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                    ?.toBitmap(width = px, height = px)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }.value
}

@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    val g = LocalGlass.current
    var index by remember { mutableIntStateOf(0) }
    val options = listOf("支付宝", "微信")
    val qr = rememberQr(if (index == 0) R.drawable.donate_alipay else R.drawable.donate_wechat)

    GlassDialog(onDismiss = onDismiss, fullWidth = true) { close ->
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 340.dp),
            shape = RoundedCornerShape(Radius.card),
            weight = 1f,
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = CardInset)) {
                Text(
                    text = "捐赠",
                    style = MaterialTheme.typography.labelSmall,
                    color = g.inkSoft,
                )

                Spacer(Modifier.height(14.dp))

                SegmentedChoice(
                    options = options,
                    selected = options[index],
                    onPick = { label ->
                        options.indexOf(label).takeIf { it >= 0 }?.let { index = it }
                    },
                )

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = options[index] + "收款码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(Radius.panel)),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogKey(text = "关闭", onClick = { close(onDismiss) })
                }
            }
        }
    }
}

@Composable
private fun DialogKey(text: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(Radius.chip)
    Box(
        modifier = Modifier
            .clip(shape)
            .pressable(onClick, scale = 0.92f),
    ) {
        GlassInset(shape = shape) {
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .padding(horizontal = CardInset),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = g.ink,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun rememberQr(@DrawableRes id: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    return produceState<ImageBitmap?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeResource(resources, id, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }.value
}

@Suppress("DEPRECATION")
@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.substringBefore('-')?.takeIf { it.isNotBlank() } ?: "?"
    }
}

