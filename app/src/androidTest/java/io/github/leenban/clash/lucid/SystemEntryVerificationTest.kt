package io.github.leenban.clash.lucid

import android.app.Notification
import android.app.NotificationManager
import android.content.pm.LauncherApps
import android.content.pm.ShortcutManager
import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemEntryVerificationTest {
    @Test(timeout = 180000)
    fun systemTileAndAllLauncherShortcutsControlService() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val component = "io.github.leenban.clash/.TileService"
        // Normal APKs cannot read this hidden setting on Android17. Inspect through
        // the instrumentation shell rather than depending on testOnly privileges.
        val hadTile = device.executeShellCommand("settings get secure sysui_qs_tiles").contains("TileService")
        suspend fun awaitService(running: Boolean) = withTimeout(30000) {
            while ((StatusClient(context).currentProfile() != null) != running) delay(100)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val repo = ClientRepository(context)
            val original = repo.active()
            val running = StatusClient(context).currentProfile() != null
            try {
                repo.restoreSelection(context, original)
                repo.select(repo.profiles().first { it.name == "Local integration test" })
                val shortcuts = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts
                assertEquals(setOf("service_0", "service_1", "service_2"), shortcuts.map { it.id }.toSet())
                assertNull("The emulator harness must grant VPN consent", android.net.VpnService.prepare(context))
                val launcher = context.getSystemService(LauncherApps::class.java)
                for ((id, expected) in listOf("service_1" to true, "service_2" to false, "service_0" to true, "service_0" to false)) {
                    // Drop the launcher-only test identity before the Activity can execute
                    // VpnService.prepare; running both on the main looper prevents a race.
                    scenario.onActivity {
                        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.ACCESS_SHORTCUTS")
                        try { launcher.startShortcut(context.packageName, id, null, null, Process.myUserHandle()) }
                        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
                    }
                    awaitService(expected)
                    if (expected) {
                        assertEquals("Local integration test", StatusClient(context).currentProfile())
                        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any {
                            it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0
                        })
                    }
                }
                instrumentation.uiAutomation.dropShellPermissionIdentity()
                device.executeShellCommand("cmd statusbar add-tile $component")
                device.openQuickSettings()
                device.waitForIdle()
                device.executeShellCommand("cmd statusbar click-tile $component")
                awaitService(true)
                awaitRunningProfile(context)
                device.executeShellCommand("cmd statusbar click-tile $component")
                awaitService(false)
            } finally {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
                device.executeShellCommand("cmd statusbar collapse")
                if (!hadTile) device.executeShellCommand("cmd statusbar remove-tile $component")
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    if (running) { context.startClashService(); awaitRunningProfile(context) }
                }
            }
        }
    }
}
