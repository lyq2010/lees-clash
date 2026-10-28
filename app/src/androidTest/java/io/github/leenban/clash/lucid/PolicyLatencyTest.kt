package io.github.leenban.clash.lucid

import android.os.Parcel
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.core.model.ProxyGroup
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.design.model.PolicyLayout
import io.github.leenban.clash.design.store.UiStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PolicyLatencyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedLatencyUsesColorsInEveryGroupLayout() {
        val store = UiStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = store.groupLayout
        var delay by mutableIntStateOf(299)
        var running by mutableStateOf(true)
        var dark by mutableStateOf(false)
        compose.setContent { LucidTheme(dark) {
            GroupsScreen(ClientState(running = running, groups = listOf(
                PolicyGroup("策略组", ProxyGroup("select", emptyList(), "当前节点", delay = delay))
            )), {}, {}, store)
        } }
        try {
            for (theme in listOf(false, true)) {
                compose.runOnIdle { dark = theme }
                for (layout in PolicyLayout.values()) {
                    compose.onNodeWithText(layout.title()).performClick()
                    compose.onNodeWithText("当前节点").assertIsDisplayed()
                    for ((value, color) in listOf(299 to Color(0xFF34C759), 300 to Color(0xFFD99A00),
                        599 to Color(0xFFD99A00), 600 to Color(0xFFFF453A), 65535 to Color(0xFFFF453A))) {
                        compose.runOnIdle { delay = value }
                        val node = compose.onNodeWithText(policyLatencyText(value)!!, useUnmergedTree = true)
                        node.assertIsDisplayed()
                        val results = mutableListOf<TextLayoutResult>()
                        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                        assertEquals(color, results.single().layoutInput.style.color)
                    }
                    compose.runOnIdle { delay = 0 }
                    compose.onNodeWithText("超时").assertDoesNotExist()
                    compose.onNodeWithText("未测试").assertDoesNotExist()
                    compose.onNodeWithText("0 ms").assertDoesNotExist()
                    compose.runOnIdle { delay = 120; running = false }
                    compose.onNodeWithText("120 ms").assertDoesNotExist()
                    compose.runOnIdle { running = true }
                    compose.onNodeWithText("120 ms").assertIsDisplayed()
                }
            }
        } finally { store.groupLayout = previous }
    }

    @Test fun groupDelaySurvivesBinderParcel() {
        val group = ProxyGroup("select", emptyList(), "Node", delay = 180)
        val parcel = Parcel.obtain()
        try {
            group.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            assertEquals(group, ProxyGroup.CREATOR.createFromParcel(parcel))
        } finally { parcel.recycle() }
    }
}
