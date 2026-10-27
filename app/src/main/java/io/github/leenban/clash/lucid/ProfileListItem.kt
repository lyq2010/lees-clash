package io.github.leenban.clash.lucid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.service.model.Profile

@Composable
internal fun ProfileListItem(profile: Profile, updating: Boolean, recent: Boolean,
    select: () -> Unit, update: () -> Unit, edit: () -> Unit) {
    val c = LocalLucidColors.current
    val tint by animateColorAsState(if (profile.active) c.blue else c.secondary.copy(alpha = .3f),
        tween(160), label = "profile-selection")
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 68.dp)
            .semantics { selected = profile.active }
            .lucidClickable(role = Role.RadioButton, onClick = select)
            .padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).background(tint.copy(alpha = if (profile.active) .13f else .08f), CircleShape),
                contentAlignment = Alignment.Center) {
                if (profile.active) LucidIcon(LucidSymbol.Check, tint, Modifier.size(17.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when {
                    !profile.imported -> "未完成导入"
                    updating -> "更新中"
                    recent && !profile.active -> "新添加"
                    profile.type == Profile.Type.Url -> "订阅配置"
                    profile.type == Profile.Type.File -> "本地配置"
                    else -> "外部配置"
                }, color = c.secondary, fontSize = 12.sp)
            }
        }
        if (profile.type != Profile.Type.File && profile.imported)
            CompactIconAction("更新${profile.name}", LucidSymbol.Refresh, busy = updating, onClick = update)
        CompactIconAction("编辑${profile.name}", LucidSymbol.Edit, onClick = edit)
    }
    Box(Modifier.padding(start = 49.dp).fillMaxWidth().height(.5.dp).background(c.separator))
}
