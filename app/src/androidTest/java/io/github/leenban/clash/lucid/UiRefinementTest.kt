package io.github.leenban.clash.lucid

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.design.model.PolicyLayout
import io.github.leenban.clash.design.store.UiStore
import io.github.leenban.clash.service.model.Profile
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UiRefinementTest {
    @get:Rule val compose = createComposeRule()
    private val profile = Profile(UUID(0, 1), "Wget 很长的订阅配置名称", Profile.Type.Url, "https://example.test/config",
        true, 0, 1024, 2048, 1024 * 1024, 1893456000000, 1, true, false)

    private fun save(name: String) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-refinement/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun overviewCardsAreCompactAndActionsRemainReachable() {
        val state = ClientState(active = profile, running = true, traffic = TrafficStats(uptimeMs = 45000))
        var allCards by mutableStateOf(false)
        compose.setContent { LucidTheme(false) {
            Column(Modifier.width(360.dp).background(LocalLucidColors.current.background).verticalScroll(rememberScrollState())) {
                if (allCards) HomeGeneralCards(state, {}, {}, {})
                else {
                    HomeSubscriptionCard(state.active, false, false, {}, {})
                    HomeRuntimeCard(state.running, state.traffic, {})
                }
            }
        } }
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(compose.onNodeWithTag("home-subscription-card").fetchSemanticsNode().boundsInRoot.height / density < 220)
        assertTrue(compose.onNodeWithTag("home-runtime-card").fetchSemanticsNode().boundsInRoot.height / density < 220)
        compose.onNodeWithText("更新订阅").assertHasClickAction()
        compose.onNodeWithText("查看连接").assertHasClickAction()
        save("compact-home")
        compose.runOnIdle { allCards = true }
        save("home-smaller-titles")
        compose.onNodeWithText("DNS").assertDoesNotExist()
        val heights = listOf("home-mode-card", "home-subscription-card", "home-runtime-card")
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().size.height.toFloat() }
        heights.forEach { assertEquals(heights.first(), it, 1f) }
    }

    @Test fun homeCardsGrowForLargeFontsWithoutHidingActions() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LucidTheme(true) {
                    Column(Modifier.width(320.dp).background(LocalLucidColors.current.background).verticalScroll(rememberScrollState())) {
                        HomeGeneralCards(ClientState(active = profile), {}, {}, {})
                    }
                }
            }
        }
        for (title in listOf("代理策略","更新订阅","查看连接")) {
            compose.onNodeWithText(title).performScrollTo()
            save("home-large-$title")
            println("LARGE_HOME $title ${compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot}")
            compose.onNodeWithText(title).assertIsDisplayed().assertHasClickAction()
        }
        save("home-uniform-dark-large")
    }

    @Test fun profileActionsDoNotSelectAndRemainSeparateAtLargeFonts() {
        var scale by mutableFloatStateOf(1f)
        var dark by mutableStateOf(false)
        var updates = 0; var edits = 0; var selections = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                LucidTheme(dark) { Box(Modifier.width(320.dp).background(LocalLucidColors.current.surface)) {
                    ProfileListItem(profile, false, false, { selections++ }, { updates++ }, { edits++ })
                } }
            }
        }
        for (large in listOf(false, true)) {
            compose.runOnIdle { scale = if (large) 2f else 1f; dark = large }
            val update = compose.onNodeWithContentDescription("更新${profile.name}")
            val edit = compose.onNodeWithContentDescription("编辑${profile.name}")
            assertTrue(update.fetchSemanticsNode().boundsInRoot.right <= edit.fetchSemanticsNode().boundsInRoot.left)
            update.performClick(); edit.performClick()
            assertEquals(0, selections)
            save(if (large) "profile-dark-large" else "profile-light")
        }
        assertEquals(2, updates); assertEquals(2, edits)
    }

    @Test fun layoutsPersistAndKeepNavigationWorking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = UiStore(context)
        val previous = store.groupLayout
        val longTitle = "新加坡自动选择策略组长名称"
        val groups = (1..8).map { PolicyGroup(if (it == 2) longTitle else "策略组 $it",
            ProxyGroup(if (it == 2) "url-test" else "select", emptyList(), if (it == 2) "" else "节点 $it")) }
        var open = ""
        var scale by mutableFloatStateOf(1f)
        var dark by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                LucidTheme(dark) { Box(Modifier.fillMaxSize().background(LocalLucidColors.current.background)) {
                    GroupsScreen(ClientState(groups = groups), { open = it }, {}, store)
                } }
            }
        }
        try {
            for (layout in PolicyLayout.values()) {
                compose.onNodeWithText(layout.title()).performClick()
                assertEquals(layout, UiStore(context).groupLayout)
                compose.onNodeWithText("策略组 1").performClick()
                assertEquals("nodes/${android.net.Uri.encode("策略组 1")}", open)
                if (layout == PolicyLayout.Grid) {
                    val first = compose.onNode(hasClickAction() and hasText("策略组 1")).fetchSemanticsNode().boundsInRoot
                    val second = compose.onNode(hasClickAction() and hasText(longTitle)).fetchSemanticsNode().boundsInRoot
                    assertEquals(first.top, second.top, 1f)
                    assertEquals(first.height, second.height, 1f)
                }
                save("groups-${layout.name}")
            }
            compose.runOnIdle { scale = 2f; dark = true }
            compose.onNodeWithText("策略组 1").assertIsDisplayed()
            save("groups-grid-dark-large")
        } finally { store.groupLayout = previous }
    }

    @Test fun smartToolAlignsWithOtherIconRows() {
        var next: Boolean? = null
        compose.setContent { LucidTheme(true) {
            Column(Modifier.width(360.dp).background(LocalLucidColors.current.background)) {
                SmartToolsSection(ClientState(active = profile, smartSummary = SmartSummary(2,2)), toggle = { next = it })
                LucidSection("网络诊断") { LucidRow("IP 与网络检查", symbol = LucidSymbol.Network, onClick = {}) }
            }
        } }
        val smartLeft = compose.onNodeWithText("智能选路", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val otherLeft = compose.onNodeWithText("IP 与网络检查", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        assertEquals(otherLeft,smartLeft,1f)
        compose.onNodeWithText("智能选路").assertIsOn().performClick()
        assertEquals(false,next)
        save("smart-tools-unified")
    }
}
