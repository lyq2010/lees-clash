package io.github.leenban.clash.lucid

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*

@Composable
fun ClientFeedback(state: ClientState, dismiss: () -> Unit) {
    val c = LocalLucidColors.current
    val progress =
        if (state.importing) state.importStatus
        else state.operation ?: if (state.pageLoading) "正在加载" else null
    val text = state.error ?: progress ?: state.notice?.text
    val tint = if (state.error != null) c.red else if (progress != null) c.blue else c.green
    AnimatedVisibility(
        text != null,
        enter = fadeIn(tween(120)) + expandVertically(tween(160)),
        exit = fadeOut(tween(100)) + shrinkVertically(tween(160)),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(tint.copy(alpha = .1f))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.orEmpty(), Modifier.weight(1f), color = tint, fontSize = 14.sp)
            if (state.error != null || progress == null)
                Text(
                    "关闭",
                    Modifier.heightIn(min = 40.dp)
                        .wrapContentHeight()
                        .lucidClickable(onClick = dismiss)
                        .padding(start = 12.dp),
                    color = tint,
                    fontSize = 13.sp,
                )
        }
    }
}
