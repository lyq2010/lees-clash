package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DnsFailureVerificationTest {
    @Test(timeout = 90000)
    fun localResolverReturnsRecordsAndBoundsFailuresWithoutInventingAnswers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val running = StatusClient(context).currentProfile() != null
            val previous = repo.overrideText()
            try {
                repo.restoreSelection(context, original)
                repo.saveOverride("{}")
                val profile = repo.profiles().first { it.name == "Local integration test" }
                require(profile.source == "http://127.0.0.1:18080/config")
                repo.select(profile)
                assertNull(context.startClashService()); awaitRunningProfile(context)
                for ((kind, expected) in listOf("A" to "127.0.0.1", "AAAA" to "::1", "TXT" to "local-fixture")) {
                    val value = Json.parseToJsonElement(repo.diagnostic("dns", domain = "records-verification.test", recordType = kind)).jsonObject
                    assertEquals("NOERROR", value.getValue("status").jsonPrimitive.content)
                    val answer = value.getValue("answers").jsonArray.single().jsonObject
                    assertTrue(answer.getValue("data").jsonPrimitive.content.contains(expected))
                    assertEquals(60, answer.getValue("ttl").jsonPrimitive.int)
                }
                val missing = Json.parseToJsonElement(repo.diagnostic("dns", domain = "nxdomain-verification.test")).jsonObject
                assertEquals("NXDOMAIN", missing.getValue("status").jsonPrimitive.content)
                assertTrue(missing.getValue("answers").jsonArray.isEmpty())
                for ((domain, kind) in listOf("servfail-verification.test" to "A", "timeout-verification.test" to "A", ".." to "A", "records-verification.test" to "NOT-A-TYPE")) {
                    val started = android.os.SystemClock.elapsedRealtime()
                    val failure = runCatching { repo.diagnostic("dns", domain = domain, recordType = kind) }.exceptionOrNull()
                    assertNotNull("Must reject $domain/$kind", failure)
                    assertTrue(failure!!.message.orEmpty().isNotBlank())
                    assertTrue(android.os.SystemClock.elapsedRealtime() - started < 15000)
                }
                assertNotNull(StatusClient(context).currentProfile())
            } finally {
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    repo.saveOverride(previous)
                    if (running) { context.startClashService(); awaitRunningProfile(context) }
                }
            }
        }
    }
}
