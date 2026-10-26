package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StressVerificationTest {
    @Test(timeout = 180000)
    fun fiveThousandNodesAndRulesKeepGroupSummariesSmall() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val running = StatusClient(context).currentProfile() != null
            val file = File(context.cacheDir, "verification-stress.yaml")
            try {
                repo.restoreSelection(context, original)
                val text = buildString {
                    append("mixed-port: 17890\nmode: rule\ndns:\n  enable: true\n  enhanced-mode: redir-host\n  nameserver: [tcp://127.0.0.1:13053]\nproxies:\n")
                    repeat(5000) { append("  - {name: Stress-$it, type: socks5, server: 127.0.0.1, port: 18081}\n") }
                    append("proxy-groups:\n")
                    repeat(12) { index ->
                        append("  - name: Stress-Group-$index\n    type: select\n    proxies: [")
                        append((0 until 5000).joinToString(",") { "Stress-$it" })
                        append("]\n")
                    }
                    append("rules:\n")
                    repeat(5000) { append("  - DOMAIN,node-$it.test,DIRECT\n") }
                    append("  - MATCH,DIRECT\n")
                }
                val existing = repo.profiles().firstOrNull { it.name == "Verification stress 5000" }
                val profile = if (existing != null) existing else {
                    file.writeText(text)
                    repo.profile(repo.importFile("Verification stress 5000", Uri.fromFile(file)).toString())!!
                }
                repo.select(profile)
                val start = System.currentTimeMillis()
                val summaries = repo.groupSummaries()
                assertTrue(summaries.size >= 12)
                assertTrue(summaries.all { it.group.proxies.isEmpty() })
                assertEquals(5000, repo.group("Stress-Group-0").proxies.size)
                assertTrue(repo.diagnostic("rules", "node-4999.test").contains("node-4999.test"))
                assertTrue("Offline large-list query exceeded 15s", System.currentTimeMillis() - start < 15000)
                assertNull(context.startClashService())
                awaitRunningProfile(context)
                assertTrue(repo.groupSummaries().all { it.group.proxies.isEmpty() })
                assertEquals(5000, repo.group("Stress-Group-0").proxies.size)
                assertTrue(repo.diagnostic("rules", "node-4999.test").contains("node-4999.test"))
            } finally {
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    if (running) { context.startClashService(); awaitRunningProfile(context) }
                    file.delete()
                }
            }
        }
    }
}
