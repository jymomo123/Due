package com.offline.due.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offline.due.data.CardImage
import com.offline.due.data.CountdownEvent
import com.offline.due.domain.CountdownMath
import com.offline.due.ui.EventCard
import com.offline.due.ui.rememberToday
import com.offline.due.ui.theme.LocalGlass
import com.offline.due.ui.theme.contentWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CardBackdropPage(
    event: CountdownEvent,
    onChange: (CountdownEvent) -> Unit,
    onBack: () -> Unit,
) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            failed = false
            val name = withContext(Dispatchers.IO) {
                CardImage.import(context, event.id, uri)
            }
            importing = false
            if (name != null) onChange(event.copy(cardImage = name)) else failed = true
        }
    }

    val launchPicker = {
        if (!importing) {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val today = rememberToday()
    val info = remember(event, today) { CountdownMath.occurrence(event, today) }
    val accent = Color(event.color)

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
                text = "卡片背景",
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
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
            ) {
                EventCard(
                    event = event,
                    info = info,
                    accent = accent,
                    numberHeight = 52.dp,
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionLabel("背景图")
            GlassInset(modifier = Modifier.fillMaxWidth()) {
                SettingRow(
                    label = if (event.cardImage == null) "从相册选择" else "换一张",
                    value = if (importing) "处理中…" else null,
                    onClick = launchPicker,
                    showChevron = true,
                    hint = "会复制一份到 App 自己的目录，之后删掉相册原图也不影响",
                )
                if (event.cardImage != null) {
                    InsetLine()
                    SettingRow(
                        label = "移除背景图",
                        destructive = true,
                        onClick = {
                            if (!importing) onChange(event.copy(cardImage = null))
                        },
                    )
                }
            }

            if (failed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "这张图读不出来，换一张试试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "图片会裁切铺满整张卡片，字下面自动加一层薄雾，" +
                    "换深色的图也不会看不清数字。",
                style = MaterialTheme.typography.bodySmall,
                color = g.inkSoft,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

