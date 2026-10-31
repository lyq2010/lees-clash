package io.github.leenban.clash.lucid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.core.model.TunnelState
import io.github.leenban.clash.design.lucid.LucidIcon
import io.github.leenban.clash.design.lucid.LucidSymbol
import io.github.leenban.clash.design.lucid.lucidClickable

@Composable
internal fun HomeGeneralCards(
    state: ClientState,
    navigate: (String) -> Unit,
    setMode: (TunnelState.Mode) -> Unit,
    updateProfile: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeModeCard(
            selectedMode = state.mode,
            running = state.running,
            hasProfile = state.active != null,
            primaryPolicy = state.primaryPolicy,
            busy = state.busy,
            setMode = setMode,
            navigate = navigate,
        )
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeRuntimeCard(
                running = state.running,
                traffic = state.traffic,
                navigate = navigate,
                rateHistory = state.rateHistory,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            HomeConnectionsCard(
                running = state.running,
                traffic = state.traffic,
                navigate = navigate,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        HomeSubscriptionCard(
            profile = state.active,
            busy = state.busy,
            updating = state.active?.uuid in state.updatingProfiles,
            navigate = navigate,
            update = updateProfile,
        )
    }
}

private data class OutboundModeOption(
    val mode: TunnelState.Mode,
    val title: String,
    val subtitle: String,
    val symbol: HomeBentoSymbol,
)

private val OUTBOUND_MODE_OPTIONS =
    listOf(
        OutboundModeOption(TunnelState.Mode.Rule, "规则", "按规则分流", HomeBentoSymbol.ModeRule),
        OutboundModeOption(TunnelState.Mode.Global, "全局", "全部走代理", HomeBentoSymbol.ModeGlobal),
        OutboundModeOption(TunnelState.Mode.Direct, "直连", "本地直出", HomeBentoSymbol.ModeDirect),
    )

@Composable
internal fun HomeModeCard(
    selectedMode: TunnelState.Mode,
    running: Boolean,
    hasProfile: Boolean,
    primaryPolicy: PolicyGroup?,
    busy: Boolean,
    setMode: (TunnelState.Mode) -> Unit,
    navigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberHomeBentoPalette()

    HomeBentoCard(
        palette = palette,
        cornerRadius = 26.dp,
        glow = BentoGlow.BottomMagenta,
        modifier = modifier.fillMaxWidth().testTag("home-mode-card"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeBentoIcon(HomeBentoSymbol.SplitRoute, palette.primaryText, Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "出站模式",
                    Modifier.weight(1f),
                    color = palette.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val pillShape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.clip(pillShape)
                        .background(palette.pillBg)
                        .border(0.8.dp, palette.subBorder, pillShape)
                        .lucidClickable(scale = true) { navigate("rules") }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "代理规则",
                        color = palette.mutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    LucidIcon(LucidSymbol.Chevron, palette.mutedText, Modifier.size(11.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OUTBOUND_MODE_OPTIONS.forEach { option ->
                    val isSelected = selectedMode == option.mode
                    OutboundModeTile(
                        option = option,
                        isSelected = isSelected,
                        enabled = !busy,
                        palette = palette,
                        onClick = { setMode(option.mode) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }

            HomePolicyRouteBar(
                running = running,
                hasProfile = hasProfile,
                primaryPolicy = primaryPolicy,
                palette = palette,
                onClick = { navigate("groups") },
            )
        }
    }
}

@Composable
private fun OutboundModeTile(
    option: OutboundModeOption,
    isSelected: Boolean,
    enabled: Boolean,
    palette: HomeBentoPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(16.dp)
    val bgColor by
        animateColorAsState(
            targetValue = if (isSelected) palette.subSurfaceActive else palette.subSurface,
            animationSpec = tween(160),
            label = "mode-tile-bg-${option.mode}",
        )
    val borderColor by
        animateColorAsState(
            targetValue = if (isSelected) palette.subBorderActive else palette.subBorder,
            animationSpec = tween(160),
            label = "mode-tile-border-${option.mode}",
        )
    val titleColor =
        if (isSelected) {
            if (palette.dark) palette.primaryText else palette.blueAccent
        } else {
            palette.primaryText.copy(alpha = 0.86f)
        }

    Column(
        modifier
            .clip(tileShape)
            .background(bgColor)
            .border(1.dp, borderColor, tileShape)
            .semantics {
                selected = isSelected
                contentDescription = "出站模式：${option.title}"
            }
            .lucidClickable(enabled = enabled, role = Role.RadioButton, scale = true, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeBentoIcon(
                option.symbol,
                if (isSelected) titleColor else palette.mutedText,
                Modifier.size(15.dp),
            )
            if (isSelected) {
                Box(
                    Modifier.size(6.dp)
                        .clip(CircleShape)
                        .background(if (palette.dark) palette.readyDot else palette.blueAccent)
                )
            }
        }
        Text(
            option.title,
            color = titleColor,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            option.subtitle,
            color = palette.mutedText,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomePolicyRouteBar(
    running: Boolean,
    hasProfile: Boolean,
    primaryPolicy: PolicyGroup?,
    palette: HomeBentoPalette,
    onClick: () -> Unit,
) {
    val barShape = RoundedCornerShape(16.dp)
    val delay = if (running) primaryPolicy?.group?.delay ?: 0 else 0
    val routeSummary =
        when {
            running && primaryPolicy != null -> {
                val node = primaryPolicy.group.now.ifBlank { "自动选择" }
                "${primaryPolicy.name} · $node"
            }
            hasProfile -> "查看策略与节点"
            else -> "先导入配置"
        }

    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(barShape)
            .background(palette.subSurface)
            .border(0.8.dp, palette.subBorder, barShape)
            .testTag("home-policy-capsule")
            .lucidClickable(scale = true, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(palette.policyBadgeBg),
            contentAlignment = Alignment.Center,
        ) {
            HomeBentoIcon(HomeBentoSymbol.Layers, palette.policyBadgeFg, Modifier.size(15.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            "代理策略",
            color = palette.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            routeSummary,
            Modifier.weight(1f),
            color = palette.mutedText,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (delay in 1..65534) {
            Spacer(Modifier.width(6.dp))
            PolicyLatency(delay)
        }
        Spacer(Modifier.width(5.dp))
        LucidIcon(LucidSymbol.Chevron, palette.mutedText.copy(alpha = 0.7f), Modifier.size(13.dp))
    }
}
