package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.service.model.Profile
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun remainingQuota(total: Long, upload: Long, download: Long): Long? {
    if (total <= 0) return null
    val afterUpload = total - upload.coerceIn(0L, total)
    return afterUpload - download.coerceIn(0L, afterUpload)
}

fun formatRunTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000
    val time = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60, seconds % 60)
    return if (seconds >= 86400) "${seconds / 86400} 天 $time" else time
}

internal fun profileDate(value: Long): String = if (value > 0)
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value)) else "暂无记录"

@Composable
internal fun HomeProfileSummary(profile: Profile?) {
    val text = when {
        profile == null -> "尚未选择配置"
        profile.type != Profile.Type.Url -> "${profile.name} · ${if (profile.type == Profile.Type.File) "本地配置" else "外部配置"}"
        else -> profile.name
    }
    Text(text, Modifier.padding(horizontal = 15.dp), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (profile != null) {
        if (profile.type == Profile.Type.Url) {
            val remaining = remainingQuota(profile.total, profile.upload, profile.download)
            val quota = remaining?.let { "剩余 ${formatBytes(it)} / ${formatBytes(profile.total)}" }
                ?: "订阅未提供总额度"
            Text(quota, Modifier.padding(horizontal = 15.dp).padding(top = 3.dp), color = Color.White, fontSize = 14.sp)
            Text(if (profile.expire > 0) "${if (profile.expire <= System.currentTimeMillis()) "已到期" else "到期"} ${profileDate(profile.expire)}"
                else "到期时间未知", Modifier.padding(horizontal = 15.dp).padding(top = 3.dp), color = Color.White, fontSize = 13.sp)
        }
        Text("上次更新 ${profileDate(profile.updatedAt)}", Modifier.padding(horizontal = 15.dp).padding(top = 3.dp),
            color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
    }
}

@Composable
fun SubscriptionScreen(state: ClientState, vm: ClientViewModel, navigate: (String) -> Unit, back: () -> Unit) {
    val profile = state.active
    Column(Modifier.fillMaxSize()) {
        LucidHeader("当前配置详情", back,
            if (profile?.type == Profile.Type.Url) "更新" else null,
            actionEnabled = !state.busy && profile != null && profile.uuid !in state.updatingProfiles) {
            profile?.let(vm::updateProfile)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (profile == null) {
                LucidEmpty("尚未选择配置")
                LucidSection { LucidRow("导入配置", onClick = { navigate("import") }) }
            } else {
                LucidSection("配置") {
                    LucidRow("名称", value = profile.name)
                    LucidRow("类型", value = when (profile.type) { Profile.Type.Url -> "订阅配置"; Profile.Type.File -> "本地配置"; else -> "外部配置" })
                    LucidRow("上次更新", value = profileDate(profile.updatedAt))
                    LucidRow("编辑配置", onClick = { navigate("profile/${profile.uuid}") })
                    LucidRow("资源管理", onClick = { navigate("providers") })
                }
                if (profile.type == Profile.Type.Url) SubscriptionDetails(profile)
            }
        }
    }
}
