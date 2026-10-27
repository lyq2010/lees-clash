package io.github.leenban.clash.lucid

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.leenban.clash.design.lucid.*

@Composable
fun NotificationScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var traffic by remember(state.settingsRevision) { mutableStateOf(vm.repository.service.dynamicNotification) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("通知", back)
        LucidSection {
            LucidRow("系统通知", value = if (allowed) "已开启" else "未开启", symbol = LucidSymbol.Bell,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
        }
        LucidSection("通知内容", footer = if (state.running) "停止服务后可修改。" else null) {
            LucidToggle("通知显示实时流量", traffic, !state.running) {
                vm.repository.service.dynamicNotification = it
                traffic = it
            }
        }
    }
}
