package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*

@Composable
internal fun SmartGroupToggle(checked: Boolean, enabled: Boolean, toggle: () -> Unit) {
    val c = LocalLucidColors.current
    Row(Modifier.heightIn(min = 48.dp)
        .semantics { contentDescription = "Smart 独立开关" }
        .toggleable(checked, enabled = enabled, role = Role.Switch) { toggle() }
        .padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Smart", color = (if (checked) c.blue else c.text).copy(alpha = if (enabled) 1f else .4f),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.blue, checkedThumbColor = Color.White,
                uncheckedTrackColor = c.field, uncheckedThumbColor = c.secondary, uncheckedBorderColor = c.separator))
    }
}

@Composable
internal fun SmartToolsSection(state: ClientState, toggle: (Boolean) -> Unit) {
    val summary = state.smartSummary
    LucidSection("Smart") {
        LucidToggle("所有 URLTest 组", summary?.allEnabled == true,
            symbol = LucidSymbol.Mode,
            enabled = !state.busy && summary != null && summary.total > 0,
            subtitle = when {
                state.active == null -> "请先选择配置"
                summary == null -> "正在读取"
                summary.total == 0 -> "当前配置没有 URLTest 组"
                else -> "当前配置 · ${summary.enabled}/${summary.total} 组已启用"
            }, onToggle = toggle)
    }
}
