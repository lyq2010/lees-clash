package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import io.github.leenban.clash.core.model.DiagnosticJson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryRegressionTest {
    @Test
    fun versionAndKeyGenerationDoNotLoadASecondKernelInUiProcess() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(InstrumentationRegistry.getInstrumentation().targetContext)
            assertTrue(repo.coreVersion().contains("3f27ef3"))
            assertTrue(repo.generateKey(false).publicKey.isNotEmpty())
            assertTrue(repo.generateKey(true).publicKey.isNotEmpty())
            val runtime = DiagnosticJson.format.parseToJsonElement(repo.diagnostic("runtime")).jsonObject
            assertTrue(runtime.containsKey("servicePssKiB"))
            assertTrue("Go memory advice must be embedded at build time",
                runtime.getValue("immediatePageRelease").jsonPrimitive.boolean)
            assertEquals("Lee's Clash", contextLabel())
            assertTrue(repo.diagnostic("lan").contains("mixedPort"))
            assertFalse(
                "UI process must not map a second Go kernel",
                File("/proc/self/maps").readText().contains("libclash.so"),
            )
        }
    }

    private fun contextLabel(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.applicationInfo.loadLabel(context.packageManager).toString()
    }
}
