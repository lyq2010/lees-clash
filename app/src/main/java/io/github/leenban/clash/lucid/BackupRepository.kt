package io.github.leenban.clash.lucid

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.design.store.UiStore.Companion.mainActivityAlias
import io.github.leenban.clash.service.backup.*
import io.github.leenban.clash.util.withProfile
import kotlinx.serialization.json.*

suspend fun ClientRepository.exportBackup(uri: Uri) {
    val snapshot = buildJsonObject {
        put(
            "preferences",
            PreferenceCodec.encode(
                context.getSharedPreferences("ui", Context.MODE_PRIVATE),
                PreferenceCodec.uiKeys,
            ),
        )
        put(
            "restartReceiver",
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, "io.github.leenban.clash.RestartReceiver")
            ),
        )
    }
    withProfile { exportBackup(uri.toString(), snapshot.toString()) }
}

suspend fun ClientRepository.previewBackup(uri: Uri): BackupPreview = withProfile {
    DiagnosticJson.format.decodeFromString(
        BackupPreview.serializer(),
        previewBackup(uri.toString()),
    )
}

suspend fun ClientRepository.restoreBackup(uri: Uri): BackupRestoreResult {
    val result = withProfile {
        DiagnosticJson.format.decodeFromString(
            BackupRestoreResult.serializer(),
            restoreBackup(uri.toString()),
        )
    }
    val preferences = result.ui["preferences"]?.jsonObject ?: buildJsonObject {}
    PreferenceCodec.restore(
        context.getSharedPreferences("ui", Context.MODE_PRIVATE),
        preferences,
        PreferenceCodec.uiKeys,
    )
    result.ui["restartReceiver"]?.jsonPrimitive?.int?.let { state ->
        require(state in 0..2)
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, "io.github.leenban.clash.RestartReceiver"),
            state,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
    }
    context.packageManager.setComponentEnabledSetting(
        context.mainActivityAlias,
        if (ui.hideAppIcon) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        android.content.pm.PackageManager.DONT_KILL_APP,
    )
    context.getSystemService(android.app.ActivityManager::class.java).appTasks.forEach {
        it.setExcludeFromRecents(ui.hideFromRecents)
    }
    return result
}
