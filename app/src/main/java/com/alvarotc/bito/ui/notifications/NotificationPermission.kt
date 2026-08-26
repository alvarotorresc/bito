package com.alvarotc.bito.ui.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException

/**
 * The two routes the first-run prompt must never interrupt: the onboarding flow itself and its
 * replay from Ajustes. A system dialog landing over the story beats reads as the app fishing for
 * access before it has shown what the access is for — the prompt waits for the user to actually
 * arrive in the app.
 */
private val PROMPT_FREE_ROUTES = setOf("onboarding", "onboarding_replay")

/**
 * Whether [NotificationPermissionPrompt] should raise the system dialog on this frame — the whole
 * decision except the once-per-install claim, which is a suspending store write.
 *
 * A null [route] means the NavHost hasn't set its first back-stack entry yet (same "wait for a
 * REAL route" reasoning [com.alvarotc.bito.ui.BitoNavHost]'s pending-request effect documents):
 * prompting there would fire before the user sees anything at all.
 *
 * Below API 33 POST_NOTIFICATIONS doesn't exist and notifications arrive granted, so there is
 * nothing to ask for — a user who switches them off on 26-32 is covered by the Ajustes notice,
 * which is deliberately NOT gated on 33.
 */
internal fun shouldPromptForNotifications(
    sdkInt: Int,
    notificationsEnabled: Boolean,
    route: String?,
): Boolean =
    sdkInt >= 33 &&
        !notificationsEnabled &&
        route != null &&
        route !in PROMPT_FREE_ROUTES

/**
 * Asks for POST_NOTIFICATIONS once per install, the first time the user lands on a real route.
 *
 * Reminders are seeded at first launch ([SettingsRepository.seedDefaultReminders]) and alarms are
 * scheduled from them, but on API 33+ every one of them dies silently at
 * [Notifier]'s `areNotificationsEnabled()` guard until this permission is granted — so the app's
 * whole reminder feature hangs off this prompt actually being raised.
 *
 * Hosting it on arrival, rather than inside the onboarding flow, is what makes ONE code path
 * cover every way a user gets here: finishing onboarding (which navigates straight to `today`),
 * restoring a backup (which skips onboarding entirely — see
 * [com.alvarotc.bito.ui.onboarding.OnboardingReconciler]), and updating an install that was
 * created before this prompt existed (already past onboarding, so nothing in that flow could ever
 * reach them). It renders nothing.
 *
 * The claim is taken only after the SDK and permission checks pass, never before: claiming on a
 * path that never shows a dialog would burn the install's single prompt on nothing.
 */
@Composable
fun NotificationPermissionPrompt(
    settings: SettingsRepository,
    currentRoute: String?,
) {
    val context = LocalContext.current
    // The result needs no handling: a grant makes the already-scheduled reminders visible on their
    // own, and a refusal is picked up as the notice in Ajustes the next time that screen resumes.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(currentRoute) {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!shouldPromptForNotifications(Build.VERSION.SDK_INT, enabled, currentRoute)) return@LaunchedEffect
        // A DataStore IO failure must not crash the app on its first frame; not claiming means
        // the prompt is simply retried on the next route change. Cancellation keeps propagating —
        // same guard shape as ReminderSync's own seeding call.
        val claimed =
            runCatching { settings.claimNotificationPrompt() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrDefault(false)
        // The SDK guard is repeated so the constant reference itself is provably safe, not just
        // reachability-safe (same convention as SettingsScreen's exact-alarm intent).
        //
        // runCatching for blast radius, not likelihood: this effect runs on the first frame of
        // every launch for every user, so a stripped OEM/Go build with no permission-controller
        // activity to resolve would turn a missing dialog into a crash on startup. The claim is
        // already spent by then either way, and the notice in Ajustes remains the way back.
        if (claimed && Build.VERSION.SDK_INT >= 33) {
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}
