package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.service.model.AccessControlMode
import io.github.leenban.clash.util.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFeatureVerificationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 120000)
    fun serviceProcessDeathRebindAndRestart() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val wasRunning = StatusClient(context).currentProfile() != null
            try {
                repo.restoreSelection(context, original)
                val fixture = repo.profiles().first { p -> p.name == "Local integration test" }
                repo.select(fixture)
                assertNull(context.startClashService())
                awaitRunningProfile(context)
                val processes =
                    context
                        .getSystemService(android.app.ActivityManager::class.java)
                        .runningAppProcesses
                val pid =
                    processes
                        .first { p -> p.processName == "${context.packageName}:background" }
                        .pid
                android.os.Process.killProcess(pid)
                delay(1500)
                withTimeout(30000) { while (repo.active()?.uuid != fixture.uuid) delay(100) }
                if (StatusClient(context).currentProfile() == null) {
                    assertNull(context.startClashService())
                    awaitRunningProfile(context)
                }
                assertTrue(repo.groups().isNotEmpty())
            } finally {
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    if (wasRunning) {
                        context.startClashService()
                        awaitRunningProfile(context)
                    }
                }
            }
        }
    }

    @Test(timeout = 180000)
    fun fileImportExportEditingRollbackGeoRejectionAndAgeKeys() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val source = repo.profiles().first { p -> p.name == "Local integration test" }
            val workspace =
                repo.profiles().firstOrNull { p -> p.name == "Verification file workspace" }
                    ?: repo.cloneProfile(source).also { p ->
                        repo.saveProfile(p, "Verification file workspace", p.source, 0, null, null)
                    }
            val uuid = workspace.uuid
            val text = repo.readDocument("$uuid/config.yaml")
            try {
                repo.writeDocument("$uuid/providers/verification-export.yaml", text)
                assertEquals(text, repo.readDocument("$uuid/providers/verification-export.yaml"))
                val target = repo.files.buildDocumentUri("$uuid/providers/verification-copy.yaml")
                repo.files.copyDocument(target, "$uuid/config.yaml")
                assertEquals(text, repo.readDocument("$uuid/providers/verification-copy.yaml"))
                repo.files.renameDocument(
                    "$uuid/providers/verification-copy.yaml",
                    "verification-renamed.yaml",
                )
                assertEquals(text, repo.readDocument("$uuid/providers/verification-renamed.yaml"))
                assertTrue(
                    repo.files.list("$uuid/providers").any { file ->
                        file.name == "verification-renamed.yaml"
                    }
                )
                withProfile {
                    try {
                        commit(uuid)
                    } finally {
                        release(uuid)
                    }
                }
                val imported =
                    repo.profiles().firstOrNull { p -> p.name == "Verification imported file" }
                        ?: repo.profile(
                            repo
                                .importFile(
                                    "Verification imported file",
                                    repo.files.buildDocumentUri("$uuid/config.yaml"),
                                )
                                .toString()
                        )!!
                assertEquals(text, repo.readDocument("${imported.uuid}/config.yaml"))
                val current = repo.profile(uuid.toString())!!
                val invalid = runCatching {
                    repo.saveProfile(
                        current,
                        current.name,
                        current.source,
                        0,
                        null,
                        "proxies: [invalid: yaml",
                    )
                }
                assertTrue(invalid.isFailure)
                assertEquals(text, repo.readDocument("$uuid/config.yaml"))
                val geoBefore = repo.geoAssets()
                assertTrue(
                    runCatching {
                            repo.importGeo(
                                "mmdb",
                                repo.files.buildDocumentUri(
                                    "$uuid/providers/verification-export.yaml"
                                ),
                            )
                        }
                        .isFailure
                )
                assertEquals(geoBefore, repo.geoAssets())
                for (pair in listOf(Clash.genX25519KeyPair(), Clash.genHybridKeyPair())) {
                    assertTrue(Clash.veritySecretKeys(pair.secretKey))
                    assertTrue(Clash.verityPublicKeys(pair.publicKey))
                    assertTrue(Clash.toPublicKeys(pair.secretKey).isNotEmpty())
                }
                assertFalse(Clash.veritySecretKeys("invalid-test-key"))
            } finally {
                if (original != null) repo.select(original)
            }
        }
    }

    @Test(timeout = 180000)
    fun vpnStacksIpv6PerApplicationAndProxyOnlyRestoreSettings() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val wasRunning = StatusClient(context).currentProfile() != null
            val vpn = repo.ui.enableVpn
            val stack = repo.service.tunStackMode
            val ipv6 = repo.service.allowIpv6
            val access = repo.service.accessControlMode
            val packages = repo.service.accessControlPackages
            try {
                repo.restoreSelection(context, original)
                repo.select(repo.profiles().first { p -> p.name == "Local integration test" })
                repo.ui.enableVpn = true
                for (mode in listOf("system", "gvisor", "mixed", "mips")) {
                    android.util.Log.i("LeesVerification", "VPN stack start: $mode")
                    repo.service.tunStackMode = mode
                    repo.service.allowIpv6 = true
                    assertNull(context.startClashService())
                    awaitRunningProfile(context)
                    try {
                        repo.restoreSelection(context, null)
                    } catch (e: Exception) {
                        java.io
                            .File(
                                context.getExternalFilesDir(null),
                                "verification/tun-failure.json",
                            )
                            .writeText(repo.diagnostic("runtime", "stacks"))
                        java.io
                            .File(
                                context.getExternalFilesDir(null),
                                "verification/tun-java-failure.json",
                            )
                            .writeText(repo.diagnostic("runtime", "javaStacks"))
                        throw e
                    }
                    android.util.Log.i("LeesVerification", "VPN stack stopped: $mode")
                }
                assertTrue(repo.appChoices().isNotEmpty())
                for (mode in
                    listOf(AccessControlMode.AcceptSelected, AccessControlMode.DenySelected)) {
                    repo.service.accessControlMode = mode
                    repo.service.accessControlPackages = setOf("com.android.settings")
                    assertNull(context.startClashService())
                    awaitRunningProfile(context)
                    repo.restoreSelection(context, null)
                }
                repo.ui.enableVpn = false
                assertNull(context.startClashService())
                awaitRunningProfile(context)
                repo.restoreSelection(context, null)
            } finally {
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    repo.ui.enableVpn = vpn
                    repo.service.tunStackMode = stack
                    repo.service.allowIpv6 = ipv6
                    repo.service.accessControlMode = access
                    repo.service.accessControlPackages = packages
                    if (wasRunning) {
                        context.startClashService()
                        awaitRunningProfile(context)
                    }
                }
            }
        }
    }
}
