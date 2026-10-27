package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Keys in androidTest/assets are disposable local fixtures, never user credentials. */
@RunWith(AndroidJUnit4::class)
class EncryptedProfileVerificationTest {
    @Test(timeout = 180000)
    fun bothAgeFormatsDecryptAndWrongKeyPreservesProfiles() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtures = DiagnosticJson.format.parseToJsonElement(
            instrumentation.context.assets.open("encrypted-profiles.json").bufferedReader().use { it.readText() }
        ).jsonArray
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val running = StatusClient(context).currentProfile() != null
            try {
                repo.restoreSelection(context, original)
                for ((index, value) in fixtures.withIndex()) {
                    val item = value.jsonObject
                    val kind = item.getValue("kind").jsonPrimitive.content
                    val key = item.getValue("secretKey").jsonPrimitive.content
                    val url = "http://127.0.0.1:18080/encrypted-config/$kind"
                    val name = "Verification age $kind"
                    val profile = repo.profiles().firstOrNull { it.name == name }
                        ?: repo.profile(repo.importUrl(name, url, key).toString())!!
                    repo.select(profile)
                    assertTrue(repo.readDocument("${profile.uuid}/config.yaml").startsWith("-----BEGIN AGE ENCRYPTED FILE-----"))
                    assertTrue(repo.groupSummaries().any { it.name == "Local-Test" })
                    assertTrue(repo.diagnostic("rules", "lees-clash.test").contains("lees-clash.test"))
                    assertNull(context.startClashService())
                    awaitRunningProfile(context)
                    assertTrue(repo.groups().any { it.name == "Local-Test" })
                    repo.restoreSelection(context, original)

                    val before = repo.profiles().map { it.uuid }.toSet()
                    val wrong = fixtures[(index + 1) % fixtures.size].jsonObject.getValue("secretKey").jsonPrimitive.content
                    assertTrue(runCatching { repo.importUrl("Verification wrong age key", url, wrong) }.isFailure)
                    assertEquals(before, repo.profiles().map { it.uuid }.toSet())
                    assertEquals(original?.uuid, repo.active()?.uuid)
                }
            } finally {
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    if (running) { context.startClashService(); awaitRunningProfile(context) }
                }
            }
        }
    }
}
