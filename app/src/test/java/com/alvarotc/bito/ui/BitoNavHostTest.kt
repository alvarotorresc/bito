package com.alvarotc.bito.ui

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bottom bar lives above the NavHost now; these tests guard it surviving a route change.
 * Uses a bare [Application] (not the manifest's BitoApp) so onCreate()'s AppStartup.start() —
 * which builds its own AppContainer — never opens a second DataStore on the same settings file
 * as the one built explicitly below.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp", application = Application::class)
class BitoNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        compose.setContent {
            BitoTheme {
                BitoNavHost(container)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the bottom bar survives navigating to settings`() {
        setContent()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()

        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // The reminders card only renders once SettingsViewModel's DataStore-backed flow has
        // emitted (real dispatcher, not the test's) — waitForIdle alone doesn't pump that.
        val reminderCopy = "Phone nudges so you don't forget to log your habits."
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(reminderCopy, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Settings", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(reminderCopy, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the home icon returns from settings to today`() {
        setContent()
        compose.onNodeWithContentDescription("Settings", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Today", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Today", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the stats tab navigates to the stats screen and keeps the bottom bar`() {
        setContent()

        compose.onNodeWithContentDescription("Stats", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Stats", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the bottom bar hides on the habit form but survives on stats`() {
        setContent()

        compose.onNodeWithContentDescription("New habit", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Stats", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bottom-bar", useUnmergedTree = true).assertExists()
    }
}
