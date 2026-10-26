package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
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
            assertFalse(
                "UI process must not map a second Go kernel",
                File("/proc/self/maps").readText().contains("libclash.so"),
            )
        }
    }
}
