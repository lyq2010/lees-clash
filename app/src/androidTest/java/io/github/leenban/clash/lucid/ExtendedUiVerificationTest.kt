package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leenban.clash.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtendedUiVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun lowFrequencyPagesGeoActionsAndOverrideValidation() {
        val vm = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val original = runBlocking { vm.repository.overrideText() }
        fun open(route: String, title: String) {
            compose.activityRule.scenario.onActivity { vm.openRoute(route) }
            compose.waitUntil(15000) { !vm.state.value.pageLoading }
            compose.onAllNodesWithText(title).onFirst().assertIsDisplayed()
        }
        try {
            for ((route, title) in
                listOf(
                    "help" to "使用帮助",
                    "about" to "关于",
                    "apps" to "分应用代理",
                    "keys" to "加密配置密钥",
                    "providers" to "资源提供器",
                    "files" to "配置与资源文件",
                )) {
                open(route, title)
                compose.onNodeWithContentDescription("返回").performClick()
            }
            open("geodata", "Geo 数据")
            compose.waitUntil(15000) { vm.state.value.geoAssets.size == 5 }
            compose.onNodeWithText("全部更新").assertIsEnabled()
            compose.onAllNodesWithText("在线更新").onFirst().assertIsDisplayed()
            compose.onAllNodesWithText("从文件导入").onFirst().assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            open("override", "配置覆写")
            compose.onNodeWithText("HTTP 端口").performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("70000")
            compose.onNodeWithText("确定").performClick()
            compose.onNodeWithText("端口应在 0 至 65535 之间").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextReplacement("17892")
            compose.onNodeWithText("确定").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.waitUntil(15000) { !vm.state.value.busy }
            compose.onNodeWithText("覆写已保存").assertIsDisplayed()
        } finally {
            runBlocking { vm.repository.saveOverride(original) }
        }
    }
}
