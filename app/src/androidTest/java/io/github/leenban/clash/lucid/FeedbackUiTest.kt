package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.leenban.clash.design.lucid.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeedbackUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun overlaysKeepHeaderStableAndErrorsCanExpandAndDismiss() {
        var state by mutableStateOf(ClientState())
        var toggled = false
        val longError = "请求失败：" + "请检查当前网络与地址。".repeat(20)
        compose.setContent {
            LucidTheme(false) {
                Box(Modifier.size(360.dp, 640.dp)) {
                    Box(Modifier.testTag("page-header")) {
                        HomeHeader("Weget", false, false, false, {}, { toggled = true })
                    }
                    ClientFeedback(state, true) { state = state.copy(error = null) }
                }
            }
        }
        val before = compose.onNodeWithTag("page-header").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = state.copy(notice = ClientNotice(1, "配置已切换")) }
        compose.onNodeWithTag("feedback-toast").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("page-header").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state = state.copy(error = longError) }
        compose.onNodeWithTag("feedback-alert").assertIsDisplayed()
        compose.onNodeWithText("启动").performClick()
        assertTrue("Alert must leave header actions usable", toggled)
        compose.onNodeWithTag("feedback-toast").assertDoesNotExist()
        compose.onNodeWithText("查看详情").performClick()
        compose.onNodeWithText("操作未完成").assertIsDisplayed()
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("feedback-alert").assertDoesNotExist()
        compose.onNodeWithTag("feedback-toast").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("page-header").fetchSemanticsNode().boundsInRoot)
    }
}
