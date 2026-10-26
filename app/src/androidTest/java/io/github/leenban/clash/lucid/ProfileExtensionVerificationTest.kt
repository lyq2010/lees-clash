package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileExtensionVerificationTest {
    @Test(timeout = 180000)
    fun extensionSurvivesUpdateAndInvalidScriptsPreserveLastValidData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val running = StatusClient(context).currentProfile() != null
            val fixture = repo.profiles().first { p -> p.name == "Local integration test" }
            val old = repo.extension(fixture.uuid.toString())
            val override = repo.overrideText()
            try {
                repo.restoreSelection(context, original)
                repo.select(fixture)
                val extension =
                    """{
                    "rules":["DOMAIN,extension.test,Verification-Group"],
                    "proxies":[{"name":"Verification-Node","type":"socks5","server":"127.0.0.1","port":18081}],
                    "proxy-groups":[{"name":"Verification-Group","type":"select","proxies":["Verification-Node"]}],
                    "script":"function main(config) { config.rules.unshift('DOMAIN,script.test,DIRECT'); return config; }",
                    "override":{"ntp":{"enable":false,"server":"127.0.0.1","port":12345,"interval":30}}
                }"""
                        .trimIndent()
                repo.saveExtension(fixture.uuid.toString(), extension)
                assertTrue(repo.groups().any { p -> p.name == "Verification-Group" })
                assertTrue(repo.groupSummaries().all { p -> p.group.proxies.isEmpty() })
                assertTrue(repo.diagnostic("rules", "script.test").contains("script.test"))
                assertTrue(
                    repo.diagnostic("rules", "extension.test").contains("Verification-Group")
                )
                repo.reload(fixture)
                assertEquals(extension, repo.extension(fixture.uuid.toString()))
                assertEquals(1893456000000L, repo.profile(fixture.uuid.toString())!!.expire)
                assertNull(context.startClashService())
                awaitRunningProfile(context)
                assertEquals("Verification-Node", repo.group("Verification-Group").now)
                assertTrue(repo.diagnostic("rules", "script.test").contains("script.test"))
                repo.restoreSelection(context, fixture)
                for (invalid in
                    listOf(
                        """{"rules":["DOMAIN,x.test,missing-proxy"]}""",
                        """{"script":"function main(c) { while(true){} }"}""",
                        """{"script":"function main(c) { throw new Error('invalid'); }"}""",
                    )) {
                    assertTrue(
                        runCatching { repo.saveExtension(fixture.uuid.toString(), invalid) }
                            .isFailure
                    )
                    assertEquals(extension, repo.extension(fixture.uuid.toString()))
                }
                val settings =
                    repo.configurationJson
                        .decodeFromString(ConfigurationOverride.serializer(), override)
                        .copy(
                            ntp =
                                ConfigurationOverride.Ntp(
                                    false,
                                    "127.0.0.1",
                                    12345,
                                    30,
                                    "DIRECT",
                                    false,
                                ),
                            globalUserAgent = "Lees-Verification",
                        )
                repo.saveOverride(
                    repo.configurationJson.encodeToString(
                        ConfigurationOverride.serializer(),
                        settings,
                    )
                )
                assertEquals(
                    settings,
                    repo.configurationJson.decodeFromString(
                        ConfigurationOverride.serializer(),
                        repo.overrideText(),
                    ),
                )
                val failure =
                    runCatching {
                            repo.importUrl(
                                "HTTP error verification",
                                "http://127.0.0.1:18080/config-error",
                                null,
                            )
                        }
                        .exceptionOrNull()
                assertNotNull(failure)
                assertTrue(failure!!.message.orEmpty().contains("HTTP 500"))
            } finally {
                withContext(NonCancellable) {
                    repo.restoreSelection(context, original)
                    repo.saveExtension(fixture.uuid.toString(), old)
                    repo.saveOverride(override)
                    if (running) {
                        context.startClashService()
                        awaitRunningProfile(context)
                    }
                }
            }
        }
    }
}
