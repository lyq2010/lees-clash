package io.github.leenban.clash.lucid

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.R
import io.github.leenban.clash.core.model.ProxySort
import io.github.leenban.clash.design.lucid.LocalLucidColors
import io.github.leenban.clash.design.lucid.lucidClickable

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PolicyActionBar(mode: String, sort: ProxySort, showMode: Boolean,
    modeEnabled: Boolean, exitEnabled: Boolean, progress: String?,
    onMode: () -> Unit, onSort: () -> Unit, onExit: () -> Unit) {
    val sortTitle = when (sort) {
        ProxySort.Default -> "配置顺序"
        ProxySort.Title -> "按名称"
        ProxySort.Delay -> "按延迟"
    }
    val sortIcon = when (sort) {
        ProxySort.Default -> R.drawable.ic_lucide_list_ordered
        ProxySort.Title -> R.drawable.ic_lucide_arrow_down_a_z
        ProxySort.Delay -> R.drawable.ic_lucide_gauge
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showMode) SmartGroupToggle(mode == "smart", modeEnabled, onMode)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PolicyIconAction("节点排序", sortIcon, state = sortTitle, caption = when (sort) {
                    ProxySort.Default -> "顺序"
                    ProxySort.Title -> "名称"
                    ProxySort.Delay -> "延迟"
                }, onClick = onSort)
                PolicyIconAction(if (progress == null) "检测落地信息" else "取消落地检测",
                    if (progress == null) R.drawable.ic_lucide_map_pin else R.drawable.ic_lucide_square,
                    state = progress ?: "IPQuery", caption = if (progress == null) "落地" else "停止",
                    enabled = exitEnabled, active = progress != null, onClick = onExit)
            }
        }
        if (progress != null) Text(progress, Modifier.align(Alignment.End), color = LocalLucidColors.current.secondary, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicyIconAction(label: String, @DrawableRes icon: Int, state: String, caption: String,
    enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val c = LocalLucidColors.current
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("$label · $state") } }, state = rememberTooltipState()) {
        Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
                .background(if (active) c.blue.copy(alpha = .14f) else c.surface)
                .semantics { stateDescription = state }
                .lucidClickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(20.dp),
                    tint = c.blue.copy(alpha = if (enabled) 1f else .35f))
                Text(caption, color = c.secondary.copy(alpha = if (enabled) 1f else .4f), fontSize = 12.sp, maxLines = 1)
        }
    }
}
