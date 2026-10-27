package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.leenban.clash.design.lucid.*

@Composable
internal fun CompactIconAction(label: String, symbol: LucidSymbol, enabled: Boolean = true,
    busy: Boolean = false, tint: Color = LocalLucidColors.current.blue, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).semantics { contentDescription = label }
        .lucidClickable(enabled = enabled && !busy, scale = true, onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = tint, strokeWidth = 2.dp)
        else LucidIcon(symbol, tint.copy(alpha = if (enabled) 1f else .35f), Modifier.size(21.dp))
    }
}
