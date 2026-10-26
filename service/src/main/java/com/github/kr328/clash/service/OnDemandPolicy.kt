package io.github.leenban.clash.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import io.github.leenban.clash.service.store.ServiceStore

class OnDemandPolicy(private val context: Context) {
    private val store = ServiceStore(context)

    fun shouldBypass(): Boolean {
        if (!store.onDemandEnabled) return false
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities =
            connectivity.allNetworks.mapNotNull(connectivity::getNetworkCapabilities).filter {
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        val network =
            capabilities.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
                ?: capabilities.firstOrNull {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                }
                ?: return false
        val ssid =
            if (store.onDemandSsids.isEmpty()) null
            else
                runCatching {
                        @Suppress("DEPRECATION")
                        context.applicationContext
                            .getSystemService(WifiManager::class.java)
                            ?.connectionInfo
                            ?.ssid
                    }
                    .getOrNull()
        val transport = if (network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) NetworkCapabilities.TRANSPORT_WIFI else NetworkCapabilities.TRANSPORT_CELLULAR
        return matches(transport, ssid, store.onDemandSsids, store.onDemandCellular)
    }

    companion object {
        fun matches(
            transport: Int,
            ssid: String?,
            excluded: Set<String>,
            cellular: Boolean,
        ): Boolean {
            if (transport == NetworkCapabilities.TRANSPORT_WIFI) {
                val name =
                    ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID }
                return name != null && name in excluded
            }
            return cellular && transport == NetworkCapabilities.TRANSPORT_CELLULAR
        }
    }
}
