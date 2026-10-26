package io.github.leenban.clash.lucid

import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.util.startClashService
import io.github.leenban.clash.util.stopClashService
import io.github.leenban.clash.util.withClash
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android's real lifecycle and clock, without Compose's foreground test synchronization. */
@RunWith(AndroidJUnit4::class)
class DiagnosticLifecycleTest {
    @Test(timeout = 60000)
    fun samplingStopsInBackgroundAndResumesWithFreshData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ClientRepository(context)
        val sockets = mutableListOf<Socket>()
        lateinit var vm: ClientViewModel
        suspend fun await(check: () -> Boolean) {
            withTimeout(15000) { while (!check()) delay(100) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    vm = ViewModelProvider(activity)[ClientViewModel::class.java]
                    activity.setContent {
                        ClientApp(
                            vm,
                            initialRoute = "connections",
                            toggle = {},
                            importFile = {},
                            scan = {},
                            external = {},
                            export = { _, _ -> },
                            importGeo = {},
                            finish = {},
                        )
                    }
                }
                repository.select(
                    repository.profiles().first { it.name == "Local integration test" }
                )
                assertNull(context.startClashService())
                await { vm.state.value.running }
                withContext(Dispatchers.IO) {
                    repeat(2) {
                        val socket = Socket("127.0.0.1", 17890)
                        sockets += socket
                        socket.soTimeout = 5000
                        socket
                            .getOutputStream()
                            .write(
                                "GET http://lees-clash.test:18080/hold HTTP/1.1\r\nHost: lees-clash.test:18080\r\nConnection: close\r\n\r\n"
                                    .toByteArray()
                            )
                        assertEquals('H'.code, socket.getInputStream().read())
                    }
                }
                await { vm.state.value.connections.total == 2 }
                // The UI intentionally unbinds its service when hidden. Retain the existing
                // Binder for test control; do not rebind the background UI just to inspect it.
                val diagnostic = withClash { this }
                scenario.moveToState(Lifecycle.State.CREATED)
                val sampled = vm.state.value.connections
                val closed = sampled.items.first().id
                val actualJson =
                    withContext(Dispatchers.IO) {
                        DiagnosticJson.checked(
                            diagnostic.queryDiagnostics(
                                "close",
                                buildJsonObject { put("id", closed) }.toString(),
                            )
                        )
                        DiagnosticJson.checked(diagnostic.queryDiagnostics("connections", "{}"))
                    }
                val actual =
                    DiagnosticJson.format.decodeFromString(
                        DiagnosticPage.serializer(ConnectionInfo.serializer()),
                        actualJson,
                    )
                assertEquals(1, actual.total)
                delay(2200)
                assertEquals("A stopped page must not sample", sampled, vm.state.value.connections)
                scenario.moveToState(Lifecycle.State.RESUMED)
                await { vm.state.value.connections.total == 1 }
                assertFalse(
                    vm.state.value.connections.items.any { it.id == sampled.items.first().id }
                )
            }
        } finally {
            sockets.forEach { runCatching { it.close() } }
            context.stopClashService()
        }
    }
}
