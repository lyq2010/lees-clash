package io.github.leenban.clash.util

import android.content.Context
import android.content.Intent
import android.net.VpnService
import io.github.leenban.clash.common.compat.startForegroundServiceCompat
import io.github.leenban.clash.common.util.intent
import io.github.leenban.clash.design.store.UiStore
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.service.ClashService
import io.github.leenban.clash.service.TunService

fun Context.startClashService(): Intent? {
    val startTun = UiStore(this).enableVpn

    if (startTun) {
        val vpnRequest = VpnService.prepare(this)
        if (vpnRequest != null) return vpnRequest

        StatusClient(this).prepareStart()
        startForegroundServiceCompat(TunService::class.intent)
    } else {
        StatusClient(this).prepareStart()
        startForegroundServiceCompat(ClashService::class.intent)
    }

    return null
}

fun Context.stopClashService() {
    StatusClient(this).requestStop()
}
