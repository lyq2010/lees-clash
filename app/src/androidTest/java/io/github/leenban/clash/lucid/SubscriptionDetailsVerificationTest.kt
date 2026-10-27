package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leenban.clash.MainActivity
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionDetailsVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 180000)
    fun realLocalHeadersRenderUsageMissingMetadataExpiryAndSaturatedQuota() {
        val model = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val repo = model.repository
        val original = runBlocking { repo.active() }
        try {
            for (kind in listOf("full", "no-info", "over-quota")) {
                val url = "http://127.0.0.1:18080/config-subscription/$kind"
                val name = "Verification subscription $kind"
                val profile = runBlocking {
                    val existing = repo.profiles().firstOrNull { it.name == name }
                    if (existing != null) { require(existing.source == url); repo.reload(existing); repo.profile(existing.uuid.toString())!! }
                    else repo.profile(repo.importUrl(name, url, null).toString())!!
                }
                compose.activityRule.scenario.onActivity { model.openRoute("profile/${profile.uuid}") }
                compose.waitUntil(15000) { !model.state.value.pageLoading && model.state.value.editingProfile?.uuid == profile.uuid }
                if (kind == "no-info") {
                    assertEquals(0L, profile.total)
                    assertEquals(0L, profile.expire)
                    compose.onNodeWithText("订阅服务未提供流量与到期信息。").performScrollTo().assertIsDisplayed()
                } else {
                    if (kind == "full") {
                        assertEquals(1024L, profile.upload); assertEquals(2048L, profile.download)
                        assertEquals(1893456000000L, profile.expire)
                        compose.onNodeWithText("1021.0 KB").performScrollTo().assertIsDisplayed()
                    } else {
                        assertEquals(Long.MAX_VALUE, profile.upload)
                        assertEquals(Long.MAX_VALUE, profile.download)
                        assertEquals(1000L, profile.expire)
                        compose.onNodeWithText("0 B").performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText("3 B").assertDoesNotExist()
                    }
                    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(profile.expire))
                    compose.onNodeWithText(date).performScrollTo().assertIsDisplayed()
                }
                compose.onNodeWithContentDescription("返回").performClick()
            }
        } finally { runBlocking { if (original != null) repo.select(original) } }
    }
}
