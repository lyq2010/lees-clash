package io.github.leenban.clash.lucid

import android.os.Parcel
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.leenban.clash.core.model.ProxyGroup
import io.github.leenban.clash.core.model.ConnectionInfo
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.service.ConnectionAppResolver
import io.github.leenban.clash.design.lucid.LucidTheme
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PolicyIconVerificationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteSvgAndFailureFallbackAndParcel() {
        val server = ServerSocket(0)
        val failedRequests = AtomicInteger()
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val path = input.readLine().orEmpty()
                    while (!input.readLine().isNullOrEmpty()) {}
                    val ok = path.contains("/icon.svg")
                    if (!ok) failedRequests.incrementAndGet()
                    val body = if (ok) """<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#00cc00"/></svg>""" else "missing"
                    socket.getOutputStream().write((
                        "HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\n" +
                            "Content-Type: image/svg+xml\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body
                    ).toByteArray())
                }
            }
        }
        try {
            var icon by mutableStateOf("")
            compose.setContent { LucidTheme(false) {
                GroupsScreen(ClientState(groups = listOf(PolicyGroup("图标测试", ProxyGroup("select", emptyList(), "DIRECT", icon)))), {}, {})
            } }
            compose.onNodeWithTag("policy-icon-default", useUnmergedTree = true).assertExists()
            val url = "http://127.0.0.1:${server.localPort}/icon.svg"
            compose.runOnIdle { icon = url }
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("policy-icon-default", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
            val pixels = compose.onNodeWithTag("policy-icon-remote", useUnmergedTree = true).captureToImage().toPixelMap()
            assertTrue(pixels[pixels.width / 2, pixels.height / 2].green > .7f)
            val screenshot = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "policy-icon-success.png")
            screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnIdle { icon = "http://127.0.0.1:${server.localPort}/missing" }
            compose.waitUntil(10000) { failedRequests.get() > 0 }
            compose.onNodeWithTag("policy-icon-default", useUnmergedTree = true).assertExists()
            compose.runOnIdle { icon = "file:///private/icon.png" }
            compose.onNodeWithTag("policy-icon-remote", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("policy-icon-default", useUnmergedTree = true).assertExists()
            val parcel = Parcel.obtain()
            try {
                val group = ProxyGroup("select", emptyList(), "DIRECT", url)
                group.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                assertEquals(group, ProxyGroup.CREATOR.createFromParcel(parcel))
            } finally { parcel.recycle() }
        } finally {
            server.close()
            worker.join(1000)
        }
    }

    @Test fun installedApplicationLabelResolvesFromPackageOrUid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = ConnectionAppResolver(context)
        val json = DiagnosticJson.format
        val expected = context.applicationInfo.loadLabel(context.packageManager).toString()
        for (info in listOf(
            ConnectionInfo("1", process = context.packageName),
            ConnectionInfo("2", uid = android.os.Process.myUid().toLong()),
        )) {
            val result = resolver.decorate("connection", json.encodeToString(ConnectionInfo.serializer(), info))
            val decoded = json.decodeFromString(ConnectionInfo.serializer(), result)
            assertEquals(expected, decoded.appName)
            assertEquals(expected, decoded.applicationTitle())
        }
    }
}
