package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.leenban.clash.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun moreOpensNotificationsAndPreferenceSurvivesRecreation() {
        val model = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val repo = model.repository
        val original = runBlocking { repo.active() }
        val dynamic = repo.service.dynamicNotification
        try {
            runBlocking { repo.restoreSelection(compose.activity, original) }
            compose.activityRule.scenario.onActivity { model.changed(); model.openRoute("more") }
            compose.waitUntil(10000) { compose.onAllNodesWithText("通知").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("通知").performScrollTo().performClick()
            compose.onNodeWithText("系统通知").assertHasClickAction()
            compose.onNodeWithText("通知显示实时流量").performClick()
            assertEquals(!dynamic, repo.service.dynamicNotification)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("通知显示实时流量").assertIsDisplayed()
            assertEquals(!dynamic, repo.service.dynamicNotification)
        } finally { repo.service.dynamicNotification = dynamic }
    }
}
