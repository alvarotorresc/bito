package com.alvarotc.bito

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.alvarotc.bito.data.backup.BackupWorker
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.ui.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

private const val FOLDER = "content://tree/backups"

/**
 * Cross-lane smoke test: start() must not throw and must leave all three channels behind.
 * Uses a bare [Application] so the manifest's BitoApp doesn't boot its own AppContainer in
 * onCreate() — that second DataStore on the same settings file crashes a background coroutine
 * ("multiple DataStores active") that surfaces as UncaughtExceptionsBeforeTest in whichever
 * test runs next on the worker.
 *
 * Every test here injects its own [CoroutineScope] (backed by a [Job] this class owns) instead of
 * letting [AppStartup.start] fall back to its production `defaultScope` — that scope is never
 * cancelled by design, and [AppStartup] is a JVM-wide singleton under Robolectric, so a leaked
 * scope here would otherwise bleed collectors (and their WorkManager/notification side effects)
 * into whichever test runs next, same JVM, same suite.
 *
 * [setUp] calls [AppStartup.resetForTests] so THIS class's own [AppStartup.start] calls run for
 * real even if some unrelated, earlier Robolectric test in the same JVM fork already tripped the
 * `started` latch through its own implicit `BitoApp.onCreate()` (see [AppStartup]'s class KDoc —
 * that's most of this suite's ~44 other Robolectric classes, since they don't override the
 * manifest's `BitoApp`). [tearDown] cancels this test's own scope and calls
 * [AppStartup.quiesceForTests] — NOT [AppStartup.resetForTests] — so the latch stays tripped
 * afterwards: resetting it here would re-arm it for whatever unrelated test runs next in this
 * fork, letting a fresh, uncancelled set of production collectors launch on `defaultScope` all
 * over again. This is exactly the mechanism M9.5's task 1 fixes — ledgered against
 * `TrayRefresherTest`'s full-suite-only flake since M8; after this fix that flake is no longer an
 * acceptable excuse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppStartupTest {
    private var scopeJob: Job? = null

    private fun testScope(): CoroutineScope {
        val job = Job()
        scopeJob = job
        return CoroutineScope(Dispatchers.Default + job)
    }

    @Before
    fun setUp() {
        AppStartup.resetForTests()
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { scopeJob?.cancelAndJoin() } }
        AppStartup.quiesceForTests()
    }

    @Test
    fun `start does not throw and creates all three notification channels`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)

        AppStartup.start(app, container, testScope())

        val channelIds =
            NotificationManagerCompat.from(app)
                .notificationChannelsCompat
                .map { it.id }
        assertTrue(channelIds.contains(NotificationChannels.REMINDERS))
        assertTrue(channelIds.contains(NotificationChannels.REVIEW))
        // Proves the celebrations channel exists synchronously before any component (the
        // widget's LogHabitAction included) can run — start() is called from BitoApp.onCreate()
        // directly, not from a launched coroutine, so PerfectDayNotifier.maybeNotify (T13) never
        // races it.
        assertTrue(channelIds.contains(NotificationChannels.CELEBRATIONS))
    }

    @Test
    fun `start is idempotent`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val scope = testScope()

        AppStartup.start(app, container, scope)
        AppStartup.start(app, container, scope)

        // Both BackupSync's periodic-work write and ReminderScheduler's alarm set are idempotent
        // at the OS-API level (a unique work / a re-set alarm looks identical whether one or two
        // identical collectors produced it), so the only honest way to prove the SECOND start()
        // didn't launch a second set of collectors is this counter, incremented in lockstep with
        // the actual `WidgetRefresher`/`ReminderSync`/`BackupSync` `.start()` calls inside the
        // same guarded block (see AppStartup.start).
        assertEquals(1, AppStartup.startInvocations.get())
    }

    @Test
    fun `an injected scope cancels the collectors`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val container = AppContainer(app)
        val scope = testScope()
        val workManager = WorkManager.getInstance(app)

        fun workInfos() = workManager.getWorkInfosForUniqueWork(BackupWorker.WORK_NAME).get()

        AppStartup.start(app, container, scope)

        // A folder reaches BackupSync's collector and, through it, WorkManager — proves the
        // injected scope is really the one driving the collector, not a leftover default one.
        runBlocking { container.settings.update { it.copy(backupFolderUri = FOLDER) } }
        eventually { workInfos().size == 1 }
        assertEquals(TimeUnit.DAYS.toMillis(1), workInfos().single().periodicityInfo?.repeatIntervalMillis)

        // cancelAndJoin suspends until the collector coroutine (a child of this job) has actually
        // finished, not just been asked to stop — so there is no race window left: the write below
        // happens after the collector is structurally incapable of ever observing it.
        runBlocking { withTimeout(5_000) { scope.coroutineContext[Job]!!.cancelAndJoin() } }

        runBlocking { container.settings.update { it.copy(backupFrequency = BackupFrequency.WEEKLY) } }

        // Still DAILY: the cancelled collector never saw the WEEKLY write, so it never reached
        // WorkManager with it.
        assertEquals(TimeUnit.DAYS.toMillis(1), workInfos().single().periodicityInfo?.repeatIntervalMillis)
    }

    /**
     * Proves the WIRING, not just [com.alvarotc.bito.ui.onboarding.OnboardingReconciler.reconcile]
     * in isolation ([com.alvarotc.bito.ui.onboarding.OnboardingReconcilerTest] already covers that
     * deterministically): a habit seeded before [AppStartup.start] runs must actually reach
     * `onboardingDone = true` through the real [AppStartup.start] call, on the real (test-injected)
     * scope — not a fake standing in for it. `eventually` polls (same idiom `an injected scope
     * cancels the collectors` already uses for BackupSync's own async WorkManager write) because
     * [com.alvarotc.bito.ui.onboarding.OnboardingReconciler.start] fires into [scope] as a
     * fire-and-forget coroutine with no completion signal exposed to the caller.
     */
    @Test
    fun `start seeds onboardingDone when the database already has habits`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        runBlocking { container.database.habitDao().upsert(habitEntity(id = "restored-habit")) }

        AppStartup.start(app, container, testScope())

        eventually { runBlocking { container.settings.settings.first() }.onboardingDone }
    }

    private fun eventually(
        timeoutMs: Long = 2_000,
        check: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!check()) {
            if (System.currentTimeMillis() > deadline) error("condition not met within ${timeoutMs}ms")
            Thread.sleep(5)
        }
    }
}
