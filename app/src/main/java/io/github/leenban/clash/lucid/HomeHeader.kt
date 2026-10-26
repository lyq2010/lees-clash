package io.github.leenban.clash.lucid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*

@Composable
fun HomeHeader(
    profileName: String?,
    running: Boolean,
    starting: Boolean,
    busy: Boolean,
    selectProfile: () -> Unit,
    toggle: () -> Unit,
) {
    val c = LocalLucidColors.current
    val name = profileName ?: "选择配置"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.weight(1f)
                .heightIn(min = 52.dp)
                .semantics(mergeDescendants = true) { contentDescription = "选择配置，当前：$name" }
                .testTag("home-profile-selector")
                .lucidClickable(onClick = selectProfile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(c.blue),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(io.github.leenban.clash.common.R.drawable.ic_lees_logo),
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                )
            }
            Text(
                name,
                Modifier.weight(1f).padding(start = 10.dp, end = 6.dp).testTag("home-profile-name"),
                color = c.text,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LucidIcon(
                LucidSymbol.Down,
                c.secondary,
                Modifier.size(14.dp).testTag("profile-disclosure"),
            )
        }
        Box(Modifier.testTag("home-service-control")) {
            LucidPill(
                if (starting) "连接中" else if (running) "停止" else "启动",
                enabled = !busy && !starting,
                onClick = toggle,
            )
        }
    }
}
