package io.github.leenban.clash.lucid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.core.model.TunnelState
import io.github.leenban.clash.design.lucid.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeGeneralCards(state: ClientState, navigate: (String) -> Unit,
    setMode: (TunnelState.Mode) -> Unit, updateProfile: () -> Unit) {
    HomeModeCard(state.mode, state.busy, navigate, setMode)
    HomeSubscriptionCard(state.active, state.busy, state.active?.uuid in state.updatingProfiles, navigate, updateProfile)
    HomeRuntimeCard(state.running, state.traffic, navigate)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeModeCard(selectedMode: TunnelState.Mode, busy: Boolean, navigate: (String) -> Unit,
    setMode: (TunnelState.Mode) -> Unit) {
    LucidCard("出站模式", LucidSymbol.Mode, listOf(Color(0xFF5450D3), Color(0xFF4298EB)), Modifier.testTag("home-mode-card")) {
        Spacer(Modifier.weight(1f))
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(TunnelState.Mode.Direct, TunnelState.Mode.Global, TunnelState.Mode.Rule).forEach { mode ->
                val active = selectedMode == mode
                val background by animateColorAsState(if (active) Color.White.copy(alpha = .24f)
                    else Color.Black.copy(alpha = .08f), tween(160), label = "mode-selection")
                Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                    .background(background).semantics { selected = active }
                    .lucidClickable(enabled = !busy, role = Role.RadioButton, scale = true) { setMode(mode) }
                    .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(modeTitle(mode), color = Color.White, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            CardButton("代理策略") { navigate("groups") }
            CardButton("代理规则") { navigate("rules") }
        }
    }
}
