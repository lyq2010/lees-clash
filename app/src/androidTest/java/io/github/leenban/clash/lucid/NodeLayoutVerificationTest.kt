package io.github.leenban.clash.lucid

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.leenban.clash.design.lucid.LocalLucidColors
import android.graphics.Bitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.core.model.Proxy
import io.github.leenban.clash.core.model.ProxyGroup
import io.github.leenban.clash.design.lucid.LucidTheme
import io.github.leenban.clash.design.model.PolicyLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class NodeLayoutVerificationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nodeLayoutsAreIndependentAndKeepSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = ClientViewModel(context.applicationContext as Application)
        val previous = vm.repository.ui.nodeLayout
        val groupLayout = vm.repository.ui.groupLayout
        val previousSort = vm.repository.ui.proxySort
        var supported by mutableStateOf(true)
        val proxies = (1..8).map { Proxy("Node $it", "Node $it", "Socks5", "Socks5", 24, false) }
        compose.setContent { LucidTheme(false) {
            Box(Modifier.fillMaxSize().background(LocalLucidColors.current.background)) {
                NodesScreen("Fixture", ClientState(running = true, selectedGroup = ProxyGroup("url-test", proxies, "Node 1"), smartSupported = supported), vm, {})
            }
        } }
        try {
            compose.onNodeWithContentDescription("Smart 独立开关").assertHasClickAction()
            compose.onNodeWithContentDescription("检测落地信息").assertHasClickAction()
            repeat(3) { compose.onNodeWithContentDescription("节点排序").performClick() }
            assertEquals(previousSort, vm.repository.ui.proxySort)
            for (layout in PolicyLayout.values()) {
                compose.onNodeWithText(layout.title()).performClick()
                compose.onNodeWithText("Node 1").assertIsDisplayed()
                compose.onNodeWithText("测速").assertIsDisplayed()
                assertEquals(layout, vm.repository.ui.nodeLayout)
                assertEquals(groupLayout, vm.repository.ui.groupLayout)
                if (layout == PolicyLayout.List) {
                    compose.mainClock.advanceTimeBy(350)
                    Thread.sleep(450) // Allow the platform ripple renderer to finish before capture.
                    val output = File(context.getExternalFilesDir(null), "ui-refinement/nodes-lucide.png")
                    output.parentFile!!.mkdirs()
                    output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
                }
            }
            compose.runOnIdle { supported = false }
            compose.onNodeWithContentDescription("Smart 独立开关").assertDoesNotExist()
            compose.onNodeWithContentDescription("节点排序").assertHasClickAction()
        } finally { vm.repository.ui.nodeLayout = previous; vm.repository.ui.proxySort = previousSort }
    }
}
