package io.github.leenban.clash.lucid

import android.content.Context
import android.net.NetworkCapabilities
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.service.OnDemandPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkFeatureVerificationTest {
    @Test
    fun networkProbeAndBoundedPrivateHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val preferences = context.getSharedPreferences("network-probes", Context.MODE_PRIVATE)
            val previous = preferences.getString("history", "[]")
            try {
                val result =
                    repo.networkProbe("http://127.0.0.1:18080/ip-info?secret=not-saved", true, true)
                assertEquals("203.0.113.5", result.getValue("ip").jsonPrimitive.content)
                assertEquals(
                    0,
                    result.getValue("risk").jsonObject.getValue("risk_score").jsonPrimitive.int,
                )
                assertFalse(result.toString().contains("not-saved"))
                repeat(24) { repo.saveProbe(result) }
                assertEquals(20, repo.probeHistory().size)
                assertFalse(repo.probeHistory().toString().contains("secret"))
                assertTrue(
                    runCatching { repo.networkProbe("file:///invalid", true, false) }.isFailure
                )
            } finally {
                preferences.edit().putString("history", previous).commit()
            }
        }
    }

    @Test
    fun onDemandNetworkMatchingPreservesVpnForUnknownWifi() {
        val wifi = NetworkCapabilities.TRANSPORT_WIFI
        val mobile = NetworkCapabilities.TRANSPORT_CELLULAR
        val vpn = NetworkCapabilities.TRANSPORT_VPN
        assertTrue(OnDemandPolicy.matches(wifi, "\"Trusted WiFi\"", setOf("Trusted WiFi"), false))
        assertFalse(OnDemandPolicy.matches(wifi, "<unknown ssid>", setOf("Trusted WiFi"), true))
        assertFalse(OnDemandPolicy.matches(wifi, "Other", setOf("Trusted WiFi"), true))
        assertTrue(OnDemandPolicy.matches(mobile, null, emptySet(), true))
        assertFalse(OnDemandPolicy.matches(mobile, null, emptySet(), false))
        assertFalse(OnDemandPolicy.matches(vpn, "Trusted WiFi", setOf("Trusted WiFi"), true))
    }
}
