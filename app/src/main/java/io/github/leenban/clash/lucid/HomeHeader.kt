package io.github.leenban.clash.lucid

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.LucidIcon
import io.github.leenban.clash.design.lucid.LucidSymbol
import io.github.leenban.clash.design.lucid.lucidClickable

@Composable
fun HomeHeader(
    profileName: String?,
    running: Boolean,
    starting: Boolean,
    busy: Boolean,
    selectProfile: () -> Unit,
    toggle: () -> Unit,
) {
    val palette = rememberHomeBentoPalette()
    val name = profileName ?: "选择配置"

    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { contentDescription = "选择配置，当前：$name" }
                .testTag("home-profile-selector")
                .lucidClickable(onClick = selectProfile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.blueAccent),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(io.github.leenban.clash.common.R.drawable.ic_lees_logo),
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                name,
                Modifier.weight(1f, fill = false)
                    .padding(end = 5.dp)
                    .testTag("home-profile-name"),
                color = palette.primaryText,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LucidIcon(
                LucidSymbol.Down,
                palette.mutedText,
                Modifier.size(14.dp).testTag("profile-disclosure"),
            )
        }
        Box(Modifier.testTag("home-service-control")) {
            HomeServiceActionPill(
                running = running,
                starting = starting,
                enabled = !busy && !starting,
                palette = palette,
                onClick = toggle,
            )
        }
    }
}

@Composable
private fun HomeServiceActionPill(
    running: Boolean,
    starting: Boolean,
    enabled: Boolean,
    palette: HomeBentoPalette,
    onClick: () -> Unit,
) {
    val label = if (starting) "连接中" else if (running) "停止" else "启动"
    val containerColor by
        animateColorAsState(
            targetValue =
                when {
                    running -> palette.cardBg
                    else -> palette.accentOrbBg.copy(alpha = if (enabled) 1f else 0.45f)
                },
            animationSpec = tween(180),
            label = "service-pill-bg",
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                when {
                    running -> palette.primaryText.copy(alpha = if (enabled) 1f else 0.5f)
                    else -> palette.accentOrbFg
                },
            animationSpec = tween(180),
            label = "service-pill-fg",
        )
    val borderColor by
        animateColorAsState(
            targetValue =
                if (running) palette.readyDot.copy(alpha = 0.52f)
                else Color.Transparent,
            animationSpec = tween(180),
            label = "service-pill-border",
        )
    val shape = RoundedCornerShape(22.dp)

    Box(
        Modifier.heightIn(min = 48.dp)
            .lucidClickable(enabled = enabled, scale = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = label,
            modifier =
                Modifier.clip(shape)
                    .background(containerColor)
                    .border(1.dp, borderColor, shape)
                    .animateContentSize(tween(160))
                    .padding(horizontal = 15.dp, vertical = 8.dp),
            animationSpec = tween(120),
            label = "service-pill-text",
        ) { currentLabel ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (currentLabel) {
                    "停止" ->
                        Box(
                            Modifier.size(7.dp)
                                .clip(CircleShape)
                                .background(palette.readyDot)
                        )
                    "启动" ->
                        HomeBentoIcon(
                            HomeBentoSymbol.Play,
                            contentColor,
                            Modifier.size(11.dp),
                        )
                }
                Text(
                    currentLabel,
                    color = contentColor,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
