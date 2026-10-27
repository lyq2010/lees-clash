package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.ConnectionInfo
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.core.model.DiagnosticPage
import io.github.leenban.clash.util.startClashService
import io.github.leenban.clash.util.stopClashService
import java.net.Socket
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticPagingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 90000)
    fun livePagesSearchAndEndedDetails() = runBlocking {
        val context = compose.activity.applicationContext
        val repository = ClientRepository(context)
        val original = repository.active()
        lateinit var vm: ClientViewModel
        compose.activityRule.scenario.onActivity {
            vm = ViewModelProvider(it)[ClientViewModel::class.java]
        }
        val sockets = mutableListOf<Socket>()
        val fixtureHost = "paging.lees-clash.test"
        suspend fun await(label: String = "state update", check: () -> Boolean) {
            try {
                withTimeout(15000) {
                    while (true) {
                        compose.mainClock.advanceTimeBy(1000)
                        compose.waitForIdle()
                        if (check()) break
                        delay(100)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "Waiting for $label: running=${vm.state.value.running}, items=${vm.state.value.connections.items.size}, total=${vm.state.value.connections.total}, error=${vm.state.value.error}",
                    e,
                )
            }
        }
        fun loadMore() {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("加载更多"))
            compose.onNodeWithText("加载更多").performClick()
        }
        try {
            val profile = repository.profiles().first { it.name == "Local integration test" }
            repository.select(profile)
            assertNull("VPN permission is required", context.startClashService())
            await("VPN running") { vm.state.value.running }
            awaitRunningProfile(context)
            withContext(Dispatchers.IO) {
                // Keep one unrelated fixture connection open to reproduce a noisy device.
                for (host in listOf("lees-clash.test") + List(65) { fixtureHost }) {
                    val socket = Socket("127.0.0.1", 17890)
                    sockets += socket
                    socket.soTimeout = 5000
                    socket
                        .getOutputStream()
                        .write(
                            "GET http://$host:18080/hold HTTP/1.1\r\nHost: $host:18080\r\nConnection: close\r\n\r\n"
                                .toByteArray()
                        )
                    // The first response byte confirms this connection has reached the fixture.
                    assertEquals('H'.code, socket.getInputStream().read())
                }
            }
            compose.onNodeWithText("工具").performClick()
            compose.onNodeWithText("实时连接").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(fixtureHost)
            await("first page") {
                vm.state.value.connectionQuery == fixtureHost && vm.state.value.connections.total == 65 &&
                    vm.state.value.connections.items.size == 50
            }
            loadMore()
            await("second page") { vm.state.value.connections.items.size == 65 }
            compose.mainClock.advanceTimeBy(2200)
            delay(2200)
            assertEquals(
                "Periodic refresh must preserve the expanded window",
                65,
                vm.state.value.connections.items.size,
            )

            compose.onAllNodes(hasText(fixtureHost) and hasClickAction() and !hasSetTextAction())[0].performClick()
            await("connection details") { vm.state.value.connection != null }
            val lastKnown = vm.state.value.connection!!
            repository.diagnostic("close", id = lastKnown.id)
            await("ended connection") { vm.state.value.connectionEnded }
            assertEquals(lastKnown.id, vm.state.value.connection?.id)
            compose.onNodeWithText("已结束").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            await("back to expanded list") { vm.state.value.connections.items.size == 64 }

            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput("no-such-local-host")
            await("filtered empty list") {
                vm.state.value.connectionQuery == "no-such-local-host" &&
                    vm.state.value.connections.total == 0
            }
            compose.onNode(hasSetTextAction()).performTextClearance()
            await("search reset with unrelated connection") {
                vm.state.value.connectionQuery.isEmpty() && vm.state.value.connections.total >= 65 &&
                    vm.state.value.connections.items.size == 50
            }
            compose.onNode(hasSetTextAction()).performTextInput(fixtureHost)
            await("fixture search restored") {
                vm.state.value.connectionQuery == fixtureHost && vm.state.value.connections.total == 64 &&
                    vm.state.value.connections.items.size == 50
            }
            loadMore()
            await("expanded filtered list") { vm.state.value.connections.items.size == 64 }

            compose.onNodeWithText("全部关闭").performClick()
            compose.onNodeWithText("确认").performClick()
            await("all connections closed") {
                vm.state.value.connections.total == 0 && !vm.state.value.busy
            }
            assertTrue(vm.state.value.connections.items.isEmpty())
            val remaining = DiagnosticJson.format.decodeFromString(
                DiagnosticPage.serializer(ConnectionInfo.serializer()),
                repository.diagnostic("connections", "lees-clash.test"),
            )
            assertEquals("Close all must also close the unrelated fixture connection", 0, remaining.total)
        } finally {
            sockets.forEach { runCatching { it.close() } }
            context.stopClashService()
            withContext(NonCancellable) { repository.restoreSelection(context, original) }
        }
    }
}
