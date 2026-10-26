package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leenban.clash.MainActivity
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
        lateinit var vm: ClientViewModel
        compose.activityRule.scenario.onActivity {
            vm = ViewModelProvider(it)[ClientViewModel::class.java]
        }
        val sockets = mutableListOf<Socket>()
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
            withContext(Dispatchers.IO) {
                repeat(65) {
                    val socket = Socket("127.0.0.1", 17890)
                    sockets += socket
                    socket.soTimeout = 5000
                    socket
                        .getOutputStream()
                        .write(
                            "GET http://lees-clash.test:18080/hold HTTP/1.1\r\nHost: lees-clash.test:18080\r\nConnection: close\r\n\r\n"
                                .toByteArray()
                        )
                    // The first response byte confirms this connection has reached the fixture.
                    assertEquals('H'.code, socket.getInputStream().read())
                }
            }
            compose.onNodeWithText("工具").performClick()
            compose.onNodeWithText("实时连接").performClick()
            await("first page") {
                vm.state.value.connections.total == 65 &&
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

            compose.onAllNodesWithText("lees-clash.test")[0].performClick()
            await("connection details") { vm.state.value.connection != null }
            val lastKnown = vm.state.value.connection!!
            repository.diagnostic("close", id = lastKnown.id)
            await("ended connection") { vm.state.value.connectionEnded }
            assertEquals(lastKnown.id, vm.state.value.connection?.id)
            compose.onNodeWithText("已结束").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            await("back to expanded list") { vm.state.value.connections.items.size == 64 }

            compose.onNode(hasSetTextAction()).performTextInput("no-such-local-host")
            await("filtered empty list") {
                vm.state.value.connectionQuery == "no-such-local-host" &&
                    vm.state.value.connections.total == 0
            }
            compose.onNode(hasSetTextAction()).performTextClearance()
            await("search reset") { vm.state.value.connections.items.size == 50 }
            loadMore()
            await("expanded filtered list") { vm.state.value.connections.items.size == 64 }

            compose.onNodeWithText("全部关闭").performClick()
            compose.onNodeWithText("确认").performClick()
            await("all connections closed") {
                vm.state.value.connections.total == 0 && !vm.state.value.busy
            }
            assertTrue(vm.state.value.connections.items.isEmpty())
        } finally {
            sockets.forEach { runCatching { it.close() } }
            context.stopClashService()
        }
    }
}
