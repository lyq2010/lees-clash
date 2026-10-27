package io.github.leenban.clash.lucid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.Until
import io.github.leenban.clash.core.model.LogMessage
import io.github.leenban.clash.design.lucid.LucidTheme
import java.util.Date
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sends ONLY fixed synthetic text through the chooser; never reads or shares runtime logs. */
@RunWith(AndroidJUnit4::class)
class LogShareVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test(timeout = 120000)
    fun syntheticFilteredLogTextSharesAsReadableDocumentThroughSystemChooser() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val vm = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val expected = "SAFE-SHARE synthetic test text"
        val excluded = "EXCLUDED synthetic test text"
        val state = ClientState(logs = listOf(
            LogMessage(LogMessage.Level.Info, expected, Date(0)),
            LogMessage(LogMessage.Level.Debug, excluded, Date(0)),
        ))
        compose.setContent { LucidTheme(false) { LogsScreen(state, vm, {}) } }
        try {
            compose.onNode(hasSetTextAction()).performTextReplacement("SAFE-SHARE")
            compose.onNodeWithText("分享").performClick()
            device.wait(Until.findObject(By.text("Markor")), 15000).click()
            val field = device.findObject(UiSelector().packageName("net.gsantner.markor").classNameMatches(".*EditText"))
            assertTrue(field.waitForExists(15000))
            val name = field.text
            assertTrue(name.matches(Regex("lees-clash-log-[0-9]+")))
            if (device.hasObject(By.text("Share into"))) {
                device.findObject(UiSelector().resourceId("android:id/title").text("Select")).click()
                assertTrue(device.wait(Until.hasObject(By.res("net.gsantner.markor:id/opoc_filesystem_item__title")), 15000))
                if (!device.hasObject(By.text("Notebook"))) {
                    UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Notebook"))
                }
                device.findObject(UiSelector().resourceId("net.gsantner.markor:id/opoc_filesystem_item__title").text("Notebook")).click()
                device.wait(Until.findObject(By.res("net.gsantner.markor:id/ui__filesystem_dialog__button_neutral").enabled(true)), 15000).click()
            }
            var copied = ""
            runBlocking {
                withTimeout(15000) {
                    do {
                        copied = device.executeShellCommand("cat /sdcard/Documents/markor/$name.txt")
                        if (!copied.contains(expected)) delay(100)
                    } while (!copied.contains(expected))
                }
            }
            assertTrue("Recipient must copy the fixed fixture bytes", copied.contains(expected))
            assertFalse("Search-excluded text must not be shared", copied.contains(excluded))
        } finally {
            repeat(4) {
                if (device.currentPackageName != "io.github.leenban.clash") { device.pressBack(); device.waitForIdle() }
            }
        }
    }
}
