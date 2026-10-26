package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.TunnelState
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in comparison. Reports outcomes without subscription URLs, keys or node names. */
@RunWith(AndroidJUnit4::class)
class RealSubscriptionRecoveryTest {
    @Test(timeout = 420000)
    fun compareStoppedAndRunningDirectSubscriptionUpdate() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realSubscription") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = checkNotNull(repo.active())
            require(!original.source.startsWith("http://127.0.0.1"))
            val running = StatusClient(context).currentProfile() != null
            val outcomes = mutableListOf<JsonObject>()
            var previousMode: TunnelState.Mode? = null
            suspend fun attempt(label: String) {
                val start = System.currentTimeMillis()
                val result = runCatching { withTimeout(150000) { repo.reload(original) } }
                outcomes += buildJsonObject {
                    put("name", label)
                    put("passed", result.isSuccess)
                    put("durationMs", System.currentTimeMillis() - start)
                    result.exceptionOrNull()?.let { e ->
                        put("errorType", e.javaClass.simpleName)
                        put(
                            "reason",
                            e.message
                                .orEmpty()
                                .replace(Regex("https?://[^\\s]+"), "[URL]")
                                .take(200),
                        )
                    }
                }
            }
            try {
                repo.restoreSelection(context, original)
                attempt("subscription update while stopped")
                assertNull(context.startClashService())
                awaitRunningProfile(context)
                previousMode = repo.mode()
                repo.setMode(TunnelState.Mode.Direct)
                attempt("subscription update with running direct mode")
            } finally {
                withContext(NonCancellable) {
                    previousMode?.let { mode -> repo.setMode(mode) }
                    repo.restoreSelection(context, original)
                    if (running) {
                        context.startClashService()
                        awaitRunningProfile(context)
                    }
                    File(context.getExternalFilesDir(null), "verification/subscription-paths.json")
                        .apply {
                            parentFile!!.mkdirs()
                            writeText(JsonArray(outcomes).toString())
                        }
                }
            }
            assertTrue(
                "See sanitized subscription-paths.json",
                outcomes.all { p -> p.getValue("passed").jsonPrimitive.boolean },
            )
        }
    }
}
