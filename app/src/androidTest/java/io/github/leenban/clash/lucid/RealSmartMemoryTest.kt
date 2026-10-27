package io.github.leenban.clash.lucid

import android.os.Debug
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.util.startClashService
import io.github.leenban.clash.util.withClash
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

// Opt-in: exercises the existing subscription without printing/exporting it.
// The harness must explicitly pass -e realSmartMemory true and grant VPN access.
class RealSmartMemoryTest {
    @Test fun compareUsingExistingSubscription(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("realSmartMemory") == "true")
        val context = instrumentation.targetContext
        val repo = ClientRepository(context)
        val mode = arguments.getString("smartMemoryMode") ?: "config"
        require(mode in listOf("config", "smart"))
        val seconds = arguments.getString("smartMemorySeconds")?.toIntOrNull()?.coerceIn(30,600) ?: 90
        val pressure = arguments.getString("smartMemoryPressure") == "true"
        val originalModes = linkedMapOf<String,String>()
        val demand = repo.service.onDemandEnabled
        val samples = mutableListOf<JsonObject>()
        val requestTimes = mutableListOf<Long>()
        var successes = 0
        var attempts = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[ClientViewModel::class.java].openRoute("home") }
            val active = checkNotNull(repo.active()) { "Real profile must already be selected" }
            try {
                repo.restoreSelection(context, active)
                repo.service.onDemandEnabled = false
                val groups = withClash { queryProxyGroupNames(false) }
                var nodeCount = 0
                for (name in groups.filter { it != "GLOBAL" }) {
                    val group = repo.group(name)
                    val eligible = group.proxies.count { !it.isGroup && it.type.lowercase() !in listOf("direct","reject","rejectdrop","dns","pass","compatible") }
                    if (eligible > 0 && repo.groupSmart(name).supported) {
                        nodeCount = maxOf(nodeCount,eligible)
                        originalModes[name] = repo.smartMode(name)
                        repo.smartMode(name, mode)
                    }
                }
                assertTrue("Existing configuration must contain real proxy nodes", nodeCount > 0)
                assertNull("Harness must grant VPN access", context.startClashService())
                awaitRunningProfile(context)
                delay(15000)
                suspend fun sample(phase: String) {
                    val runtime = DiagnosticJson.format.parseToJsonElement(repo.diagnostic("runtime")).jsonObject
                    val ui = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    val value = JsonObject(runtime + mapOf("phase" to JsonPrimitive(phase), "uiPssKiB" to JsonPrimitive(ui.totalPss),
                        "uiJavaKiB" to JsonPrimitive(ui.getMemoryStat("summary.java-heap").toLongOrNull() ?: 0),
                        "uiGraphicsKiB" to JsonPrimitive(ui.getMemoryStat("summary.graphics").toLongOrNull() ?: 0),
                        "uiCodeKiB" to JsonPrimitive(ui.getMemoryStat("summary.code").toLongOrNull() ?: 0),
                        "uiOtherKiB" to JsonPrimitive(ui.getMemoryStat("summary.private-other").toLongOrNull() ?: 0),
                        "uiCpuMs" to JsonPrimitive(android.os.Process.getElapsedCpuTime()),
                        "atMs" to JsonPrimitive(android.os.SystemClock.elapsedRealtime())))
                    samples += value
                    println("SMART_MEMORY $mode $value")
                }
                repeat(3) { sample("idle-before"); delay(5000) }
                val urls = listOf("https://www.gstatic.com/generate_204", "https://cp.cloudflare.com/generate_204", "https://www.google.com/generate_204")
                repeat(24) { index ->
                    attempts++
                    val ok = runCatching {
                        val response = withClash { queryDiagnostics("networkCheck", buildJsonObject { put("url",urls[index%urls.size]) }.toString()) }
                        requestTimes += DiagnosticJson.format.parseToJsonElement(response).jsonObject.getValue("elapsedMs").jsonPrimitive.long
                    }.isSuccess
                    if (ok) successes++
                    if (index%6 == 5) sample("traffic")
                }
                repeat(seconds/5) { delay(5000); sample("idle-after") }
                if (pressure) {
                    println("SMART_MEMORY_PRESSURE_READY $mode")
                    // Measurement checkpoint only; production has no periodic GC.
                    Runtime.getRuntime().gc()
                    delay(15000) // Harness delivers Android RUNNING_LOW to the service.
                    repeat(6) { sample("after-pressure"); delay(5000) }
                    attempts++
                    withClash { queryDiagnostics("networkCheck", buildJsonObject { put("url",urls[0]) }.toString()) }
                    successes++
                }
                val report = buildJsonObject {
                    put("mode",mode); put("groups",originalModes.size); put("maxNodesInGroup",nodeCount)
                    put("requests",attempts); put("successfulRequests",successes)
                    put("pressureCheckpoint",pressure)
                    put("requestElapsedMs", JsonArray(requestTimes.map { JsonPrimitive(it) }))
                    put("samples",JsonArray(samples))
                }
                File(context.cacheDir,"smart-memory-$mode.json").writeText(report.toString())
                assertTrue("Real routed requests must succeed", successes > 0)
                if (mode == "smart") assertTrue("Traffic must actually exercise Smart", samples.any {
                    (it["smart"]?.jsonObject?.get("nodeRecords")?.jsonPrimitive?.int ?: 0) > 0 &&
                    (it["smart"]?.jsonObject?.get("siteRecords")?.jsonPrimitive?.int ?: 0) > 0
                })
            } finally {
                repo.restoreSelection(context, active)
                for ((name,value) in originalModes) repo.smartMode(name,value)
                repo.service.onDemandEnabled = demand
            }
        }
    }
}
