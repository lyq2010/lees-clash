package io.github.leenban.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.leenban.clash.service.StatusProvider
import io.github.leenban.clash.util.startClashService

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (StatusProvider.shouldStartClashOnBoot)
                    context.startClashService()
            }
        }
    }
}