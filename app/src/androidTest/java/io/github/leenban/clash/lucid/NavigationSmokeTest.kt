package io.github.leenban.clash.lucid

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.design.model.DarkMode
import io.github.leenban.clash.design.store.UiStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        val expected = runBlocking { ClientRepository(compose.activity).active()?.uuid }
        val model = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        compose.waitUntil(10000) {
            model.state.value.active?.uuid == expected && !model.state.value.pageLoading
        }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(350, 5000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder =
            File(instrumentation.targetContext.getExternalFilesDir(null), "verification").apply {
                mkdirs()
            }
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun navigationAndThemeScreenshots() {
        UiStore(compose.activity).darkMode = DarkMode.ForceLight
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("出站模式").assertIsDisplayed()
        listOf("通用", "网络", "覆写").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        screenshot("home-light")
        compose.onNodeWithText("工具").performClick()
        compose.onNodeWithText("实时连接").assertIsDisplayed()
        screenshot("tools-light")
        compose.onNodeWithText("更多").performClick()
        compose.onNodeWithText("应用设置").performClick()
        compose.onNodeWithText("外观").assertIsDisplayed()
        screenshot("settings-light")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("VPN 与网络").performClick()
        compose.onNodeWithText("使用 VPN 接管系统流量").assertIsDisplayed()
        screenshot("network-light")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("配置覆写").performClick()
        compose.onNodeWithText("HTTP 端口").assertIsDisplayed()
        screenshot("overrides-light")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("首页").performClick()
        UiStore(compose.activity).darkMode = DarkMode.ForceDark
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("出站模式").assertIsDisplayed()
        screenshot("home-dark")
        UiStore(compose.activity).darkMode = DarkMode.ForceLight
        compose.activityRule.scenario.recreate()
    }
}
