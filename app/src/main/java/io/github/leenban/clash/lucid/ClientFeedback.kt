package io.github.leenban.clash.lucid

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.leenban.clash.design.lucid.*
import kotlinx.coroutines.delay

/** Feedback overlays never resize the page. Success is transient; errors require dismissal. */
@Composable
fun ClientFeedback(state: ClientState, bottomTabs: Boolean = false, dismiss: () -> Unit) {
    val c = LocalLucidColors.current
    val progress = if (state.importing) state.importStatus else state.operation
    val pending = progress ?: if (state.pageLoading) "正在加载" else null
    var delayedProgress by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) {
        delayedProgress = null
        if (pending != null) {
            delay(300)
            delayedProgress = pending
        }
    }
    var details by remember { mutableStateOf<String?>(null) }
    val error = state.error
    val banner = error ?: delayedProgress
    var truncated by remember(banner) { mutableStateOf(false) }
    val tint = if (error != null) c.red else c.blue
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            banner != null,
            Modifier.align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .padding(top = 64.dp, bottom = 8.dp)
                .widthIn(max = 560.dp),
            enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { -it / 3 },
            exit = fadeOut(tween(100)),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("feedback-alert")
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (error == null)
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        color = tint,
                        strokeWidth = 2.dp,
                    )
                else Text("!", color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    Text(
                        banner.orEmpty(),
                        color = c.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { truncated = it.hasVisualOverflow },
                    )
                    if (error != null && truncated)
                        Text(
                            "查看详情",
                            Modifier.heightIn(min = 48.dp)
                                .lucidClickable { details = error }
                                .wrapContentHeight(),
                            color = c.blue,
                            fontSize = 13.sp,
                        )
                }
                if (error != null)
                    Text(
                        "关闭",
                        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .lucidClickable(onClick = dismiss)
                            .wrapContentSize(),
                        color = c.secondary,
                        fontSize = 13.sp,
                    )
            }
        }
        AnimatedVisibility(
            state.notice != null && error == null && pending == null,
            Modifier.align(Alignment.BottomCenter)
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = if (bottomTabs && maxWidth < 600.dp) 86.dp else 20.dp)
                .widthIn(max = 440.dp),
            enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { it / 3 },
            exit = fadeOut(tween(100)),
        ) {
            Text(
                state.notice?.text.orEmpty(),
                Modifier.shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.text.copy(alpha = .94f))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("feedback-toast"),
                color = c.surface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
    details?.let { message ->
        Dialog(onDismissRequest = { details = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)) {
                Text(
                    "操作未完成",
                    Modifier.padding(20.dp),
                    color = c.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    message,
                    Modifier.heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    color = c.text,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                LucidRow("知道了", onClick = { details = null })
            }
        }
    }
}
