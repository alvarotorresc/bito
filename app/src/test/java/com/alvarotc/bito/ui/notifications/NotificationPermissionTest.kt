package com.alvarotc.bito.ui.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision behind the once-per-install POST_NOTIFICATIONS prompt, pinned as a plain function
 * so every SDK level is testable without Robolectric having to pretend to be one.
 *
 * The bug this guards against was the opposite shape: the only prompt in the app was gated on
 * "this is the first reminder hour", a condition the default-hours seeding
 * ([com.alvarotc.bito.data.settings.SettingsRepository.seedDefaultReminders]) makes false forever
 * before any user can see it — so on API 33+ the permission was never requested at all and every
 * reminder died silently inside Notifier. The gate is the PERMISSION being missing, never a
 * once-in-a-lifetime state that install-time seeding already consumed.
 */
class NotificationPermissionTest {
    @Test
    fun `prompts on API 33+ when notifications are off and a real route is showing`() {
        assertTrue(shouldPromptForNotifications(sdkInt = 33, notificationsEnabled = false, route = "today"))
        assertTrue(shouldPromptForNotifications(sdkInt = 35, notificationsEnabled = false, route = "today"))
    }

    @Test
    fun `never prompts below API 33, where the permission does not exist`() {
        // API 26-32 grant notifications at install; there is nothing to request, and requesting
        // anything there would be a dialog the platform has no answer for.
        assertFalse(shouldPromptForNotifications(sdkInt = 26, notificationsEnabled = false, route = "today"))
        assertFalse(shouldPromptForNotifications(sdkInt = 32, notificationsEnabled = false, route = "today"))
    }

    @Test
    fun `never prompts when notifications are already enabled`() {
        // Covers the ordinary second launch and every launch after: the claim marker is never
        // even reached, so nothing is spent on a user who already said yes.
        assertFalse(shouldPromptForNotifications(sdkInt = 35, notificationsEnabled = true, route = "today"))
    }

    @Test
    fun `never prompts over the onboarding flow or its replay`() {
        // The prompt waits for the user to land in the app: mid-story it would ask for access
        // before the app has shown what the access is for. Finishing onboarding navigates to
        // "today", which is where the request they've now got context for actually fires.
        assertFalse(shouldPromptForNotifications(sdkInt = 35, notificationsEnabled = false, route = "onboarding"))
        assertFalse(shouldPromptForNotifications(sdkInt = 35, notificationsEnabled = false, route = "onboarding_replay"))
    }

    @Test
    fun `never prompts before the NavHost has a route`() {
        // Same "wait for a REAL route" rule BitoNavHost's pending-request effect keeps: a null
        // route means nothing is on screen yet.
        assertFalse(shouldPromptForNotifications(sdkInt = 35, notificationsEnabled = false, route = null))
    }

    @Test
    fun `every route that is not onboarding is a prompting route`() {
        // Deliberately allow-list-free: the restore path lands on "today", but a user who deep
        // links straight into a habit (a widget or notification tap) is just as entitled to the
        // one prompt as anyone else.
        listOf("today", "stats", "habi", "settings", "detail/abc", "review", "habit").forEach { route ->
            assertTrue(route, shouldPromptForNotifications(sdkInt = 35, notificationsEnabled = false, route = route))
        }
    }
}
