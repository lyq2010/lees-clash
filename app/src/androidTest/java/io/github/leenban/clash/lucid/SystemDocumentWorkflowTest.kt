package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import io.github.leenban.clash.MainActivity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemDocumentWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val model get() = ViewModelProvider(compose.activity)[ClientViewModel::class.java]

    private fun rotatePicker() {
        if (device.displayRotation == 0) device.setOrientationLeft() else device.setOrientationNatural()
        device.waitForIdle()
    }

    private fun open(route: String, title: String) {
        compose.activityRule.scenario.onActivity { model.openRoute(route) }
        compose.waitUntil(15000) {
            !model.state.value.pageLoading && compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openRoots(title: String) {
        val root = By.res("android:id/title").text(title)
        repeat(3) {
            if (device.hasObject(root)) return
            // Rotation can replace the window between locating the menu and tapping it.
            device.findObject(UiSelector().description("Show roots")).click()
            if (device.wait(Until.hasObject(root), 5000)) return
        }
        fail("System document root must be reachable: $title")
    }

    private fun saveDocument(name: String, recreate: Boolean) {
        assertTrue(device.wait(Until.hasObject(By.res("android:id/title").clazz("android.widget.EditText")), 15000))
        if (recreate) rotatePicker()
        // The picker remembers the preceding search directory. Choose a known
        // destination before checking the exported fixture bytes through adb.
        openRoots("Downloads")
        val downloads = By.res("android:id/title").text("Downloads")
        device.waitForIdle()
        device.findObject(UiSelector().resourceId("android:id/title").text("Downloads")).click()
        assertTrue(device.wait(Until.gone(downloads), 15000))
        device.wait(Until.findObject(By.res("android:id/title").clazz("android.widget.EditText")), 15000).text = name
        device.wait(Until.findObject(By.res("android:id/button1")), 15000).click()
        assertTrue(device.wait(Until.gone(By.pkg("com.google.android.documentsui")), 15000))
    }

    private fun chooseDocument(name: String, recreate: Boolean) {
        android.util.Log.i("SafVerification", "Opening filesystem root")
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 15000))
        if (recreate) rotatePicker()
        // Use the filesystem document provider: the Downloads media index can lag behind
        // a completed SAF write on a fresh Android17 image.
        openRoots(android.os.Build.MODEL)
        device.waitForIdle()
        device.findObject(UiSelector().resourceId("android:id/title").text(android.os.Build.MODEL)).click()
        assertTrue(device.wait(Until.gone(By.res("android:id/title").text(android.os.Build.MODEL)), 15000))
        android.util.Log.i("SafVerification", "Opening filename search")
        device.waitForIdle()
        device.findObject(UiSelector().description("Search")).click()
        val search = device.wait(Until.findObject(By.res("com.google.android.documentsui:id/search_src_text")), 15000)
        search.text = name
        assertEquals(name, search.text)
        device.pressEnter()
        android.util.Log.i("SafVerification", "Waiting for fixture search result")
        val file = device.findObject(UiSelector().resourceId("android:id/title").text(name))
        assertTrue(file.waitForExists(15000))
        file.click()
        assertTrue(device.wait(Until.gone(By.pkg("com.google.android.documentsui")), 15000))
        android.util.Log.i("SafVerification", "Document selected")
    }

    @Test(timeout = 240000)
    fun safBackupSavePreviewAndGeoImportAfterRecreation() {
        val repo = model.repository
        val original = runBlocking { repo.active() }
        try {
            runBlocking { repo.restoreSelection(compose.activity, original) }
            open("backup", "本地备份与恢复")
            val backupName = "lees-saf-${UUID.randomUUID()}.zip"
            compose.onNodeWithText("保存备份").performClick()
            saveDocument(backupName, true)
            compose.waitUntil(120000) { !model.state.value.busy && model.state.value.notice?.text == "备份已保存" }
            compose.onNodeWithText("选择备份文件").performClick()
            chooseDocument(backupName, false)
            compose.waitUntil(30000) { model.state.value.backupPreview != null }
            compose.onNodeWithText("恢复备份").assertIsDisplayed()
            assertEquals(runBlocking { repo.geoAssets().count { it.size > 0 } }, model.state.value.backupPreview!!.geoFiles)
            compose.onNodeWithText("恢复").performClick()
            compose.waitUntil(120000) { !model.state.value.busy && model.state.value.notice?.text?.startsWith("恢复完成：") == true }

            val fixture = runBlocking { repo.profiles().first { it.name == "Local integration test" } }
            val expected = runBlocking { repo.readDocument("${fixture.uuid}/config.yaml") }
            val name = "lees-saf-${UUID.randomUUID()}.yaml"
            open("files/${fixture.uuid}", "配置与资源文件")
            compose.waitUntil(15000) { model.state.value.files.any { it.id.endsWith("/config.yaml") } }
            val displayName = model.state.value.files.first { it.id.endsWith("/config.yaml") }.name
            compose.onNodeWithText("导出 $displayName").performClick()
            saveDocument(name, true)
            compose.waitUntil(15000) { !model.state.value.busy && model.state.value.notice?.text == "文件已导出" }
            assertEquals(expected.trim(), device.executeShellCommand("cat /sdcard/Download/$name").trim())
            compose.onNodeWithContentDescription("返回").performClick()

            open("import", "导入配置")
            compose.onNodeWithText("从文件导入").performScrollTo().performClick()
            chooseDocument(name, true)
            compose.waitUntil(30000) { model.state.value.profiles.any { it.name == name.removeSuffix(".yaml") } }
            val imported = model.state.value.profiles.first { it.name == name.removeSuffix(".yaml") }
            assertEquals(expected, runBlocking { repo.readDocument("${imported.uuid}/config.yaml") })
            compose.onNodeWithContentDescription("返回").performClick()

            val before = runBlocking { repo.profiles().map { it.uuid }.toSet() }
            val geo = runBlocking { repo.geoAssets() }
            open("geodata", "Geo 数据")
            compose.onAllNodesWithText("从文件导入").onFirst().performClick()
            chooseDocument(name, true)
            compose.waitUntil(15000) { !model.state.value.busy && model.state.value.error != null }
            assertEquals(before, runBlocking { repo.profiles().map { it.uuid }.toSet() })
            assertEquals(geo, runBlocking { repo.geoAssets() })
        } finally {
            repeat(3) {
                if (device.currentPackageName == "com.google.android.documentsui") {
                    device.pressBack()
                    device.waitForIdle()
                }
            }
            device.setOrientationNatural()
            device.unfreezeRotation()
            runBlocking { if (original != null) repo.select(original) }
        }
    }
}
