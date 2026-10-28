package io.github.leenban.clash.lucid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.design.model.PolicyLayout

internal fun PolicyLayout.title() = when (this) {
    PolicyLayout.List -> "列表"
    PolicyLayout.Cards -> "卡片"
    PolicyLayout.Grid -> "网格"
}

@Composable
internal fun PolicyLayoutPicker(layout: PolicyLayout, choose: (PolicyLayout) -> Unit) {
    val c = LocalLucidColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(10.dp)).background(c.surface),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        PolicyLayout.values().forEach { item ->
            val background by animateColorAsState(if (layout == item) c.blue.copy(alpha = .13f)
                else c.surface, tween(160), label = "policy-layout")
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(9.dp))
                .background(background).semantics { selected = item == layout }
                .lucidClickable(role = Role.RadioButton, scale = true) { choose(item) }
                .padding(horizontal = 5.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(item.title(), color = if (layout == item) c.blue else c.secondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun <T> PolicyCollection(items: List<T>, layout: PolicyLayout, key: (T) -> String,
    modifier: Modifier = Modifier, render: @Composable (T, Boolean) -> Unit) {
    if (layout == PolicyLayout.List) LazyColumn(modifier, contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 16.dp)) {
        items(items, key = key) { item ->
            Box(Modifier.animateItem().fillMaxWidth().background(LocalLucidColors.current.surface)) { render(item, true) }
        }
    } else {
        BoxWithConstraints(modifier) {
            val cell = 148.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = if (layout == PolicyLayout.Grid) ((maxWidth - 30.dp) / (cell + 10.dp)).toInt().coerceAtLeast(1) else 1
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items((items.size + columns - 1) / columns, key = { row -> key(items[row * columns]) }) { row ->
                    Row(Modifier.animateItem().fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(columns) { column ->
                            val item = items.getOrNull(row * columns + column)
                            if (item == null) Spacer(Modifier.weight(1f))
                            else Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) { render(item, false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PolicyTile(title: String, subtitle: String, detail: String, icon: String? = null,
    selected: Boolean? = null, delay: Int = 0, onClick: (() -> Unit)?) {
    val c = LocalLucidColors.current
    val border by animateColorAsState(if (selected == true) c.blue.copy(alpha = .55f) else c.separator,
        tween(160), label = "policy-tile-border")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
        .border(.7.dp, border, RoundedCornerShape(14.dp))
        .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
        .then(if (onClick != null) Modifier.lucidClickable(role = if (selected == null) Role.Button else Role.RadioButton,
            scale = true, onClick = onClick) else Modifier)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { PolicyGroupIcon(icon); Spacer(Modifier.width(9.dp)) }
            Text(title, Modifier.weight(1f), color = c.text, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = if (icon != null) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (selected == true) LucidIcon(LucidSymbol.Check, c.blue, Modifier.size(18.dp))
        }
        if (subtitle.isNotBlank() || policyLatencyText(delay) != null) Row {
            Text(subtitle, Modifier.weight(1f).alignByBaseline(), color = c.secondary, fontSize = 12.sp, lineHeight = 17.sp)
            PolicyLatency(delay, Modifier.padding(start = 8.dp).alignByBaseline())
        }
        if (detail.isNotBlank()) Text(detail, color = c.blue, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PolicyGroupRow(name: String, type: String, now: String, icon: String, delay: Int, onClick: () -> Unit) {
    val c = LocalLucidColors.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).lucidClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PolicyGroupIcon(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, color = c.text, fontSize = 16.sp, lineHeight = 21.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type, color = c.secondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1)
                    if (now.isNotBlank()) {
                        Text(" · ", color = c.secondary, fontSize = 12.sp, lineHeight = 17.sp)
                        Text(now, Modifier.weight(1f, fill = false), color = c.blue, fontSize = 13.sp,
                            lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            PolicyLatency(delay, Modifier.padding(start = 8.dp))
            Spacer(Modifier.width(8.dp))
            LucidIcon(LucidSymbol.Chevron, c.secondary.copy(alpha = .6f), Modifier.size(15.dp))
        }
        Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(.5.dp).background(c.separator))
    }
}

internal fun policyLatencyText(delay: Int): String? = when {
    delay <= 0 -> null
    delay >= 65535 -> "超时"
    else -> "$delay ms"
}

@Composable
internal fun PolicyLatency(delay: Int, modifier: Modifier = Modifier) {
    val text = policyLatencyText(delay) ?: return
    val c = LocalLucidColors.current
    val color = when {
        delay < 300 -> c.green
        delay < 600 -> androidx.compose.ui.graphics.Color(0xFFD99A00)
        else -> c.red
    }
    Text(text, modifier, color = color, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1)
}
