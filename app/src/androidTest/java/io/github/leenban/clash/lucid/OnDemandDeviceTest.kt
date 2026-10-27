package io.github.leenban.clash.lucid

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDemandDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private suspend fun awaitBypass(repo: ClientRepository, bypass: Boolean) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        withTimeout(45000) {
            while (true) {
                val state = DiagnosticJson.format.parseToJsonElement(repo.diagnostic("traffic")).jsonObject
                val vpn = connectivity.allNetworks.any {
                    connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
                if (state.getValue("networkBypassed").jsonPrimitive.boolean == bypass && vpn != bypass) break
                delay(250)
            }
        }
        assertEquals("Local integration test", StatusClient(context).currentProfile())
    }

    private fun verify(granted: Boolean) = runBlocking {
        assertEquals(granted, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val running = StatusClient(context).currentProfile() != null
            val vpn = repo.ui.enableVpn
            val enabled = repo.service.onDemandEnabled
            val ssids = repo.service.onDemandSsids
            val cellular = repo.service.onDemandCellular
            val wifi = context.getSystemService(android.net.wifi.WifiManager::class.java).isWifiEnabled
            try {
                repo.restoreSelection(context, original)
                repo.select(repo.profiles().first { it.name == "Local integration test" })
                repo.ui.enableVpn = true
                repo.service.onDemandEnabled = true
                // Official emulator fixture SSID; no real network names enter the test output.
                repo.service.onDemandSsids = setOf("AndroidWifi")
                repo.service.onDemandCellular = false
                device.executeShellCommand("svc wifi enable")
                delay(3000)
                assertNull(context.startClashService())
                awaitRunningProfile(context)
                awaitBypass(repo, granted)
                if (granted) {
                    device.executeShellCommand("svc wifi disable")
                    awaitBypass(repo, false)
                    device.executeShellCommand("svc wifi enable")
                    awaitBypass(repo, true)
                }
            } finally {
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    repo.ui.enableVpn = vpn
                    repo.service.onDemandEnabled = enabled
                    repo.service.onDemandSsids = ssids
                    repo.service.onDemandCellular = cellular
                    device.executeShellCommand("svc wifi ${if (wifi) "enable" else "disable"}")
                    if (running) { context.startClashService(); awaitRunningProfile(context) }
                }
            }
        }
    }

    @Test(timeout = 180000)
    fun deniedLocationKeepsVpnActive() = verify(false)

    @Test(timeout = 180000)
    fun emulatorWifiDisconnectAndReconnectRestoresAndPausesVpn() = verify(true)
}
