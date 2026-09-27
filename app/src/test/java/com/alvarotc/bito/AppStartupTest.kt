package com.alvarotc.bito

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.alvarotc.bito.data.backup.BackupWorker
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.FocusSession
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.ui.notifications.FocusReceiver
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
        // the actual `WidgetRefresher`/`ReminderSync`/`BackupSync`/`FocusSync` `.start()` calls
        // inside the same guarded block (see AppStartup.start).
        assertEquals(1, AppStartup.startInvocations.get())
    }

    @Test
    fun `start launches a fourth collector that keeps the focus tray honest`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        NotificationChannels.ensure(app)
        val notification =
            NotificationCompat.Builder(app, NotificationChannels.REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("stand-in")
                .build()
        NotificationManagerCompat.from(app).notify(Notifier.FOCUS_ID, notification)

        AppStartup.start(app, container, testScope())

        // No session ever gets written for this container, so FocusSync's collector — the fourth
        // one start() launches — reacts to the initial null emission by cancelling FOCUS_ID on
        // its own, without anything else in this test touching the tray.
        eventually { shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID) == null }
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

    // ReminderSync/BackupSync/WidgetRefresher, los otros tres colectores que AppStartup.start
    // lanza a la vez, programan sus propias alarmas ajenas al foco — filtrar por el componente
    // del PendingIntent es lo unico que distingue "hay una alarma de foco" de "hay CUALQUIER
    // alarma", igual que FocusAlarmTest.
    @Suppress("DEPRECATION") // ShadowAlarmManager.ScheduledAlarm#operation has no replacement accessor.
    private fun focusAlarmScheduled(alarmManager: AlarmManager) =
        shadowOf(alarmManager).scheduledAlarms.any {
            shadowOf(it.operation).savedIntent.component?.className == FocusReceiver::class.java.name
        }

    @Test
    fun `start rearms the alarm and the tray for a live session a forced stop already killed both of`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        NotificationChannels.ensure(app)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        runBlocking { container.tasks.create(taskEntity(id = "t1", title = "Leer")) }
        val endsAtMillis = System.currentTimeMillis() + 5 * 60_000L
        val session =
            FocusSession(
                taskId = "t1",
                startedAtMillis = System.currentTimeMillis(),
                endsAtMillis = endsAtMillis,
                endsAtElapsed = 0L,
                bootMillis = 0L,
            )
        runBlocking { container.focus.start(session) }
        // Simula lo que un "forzar detencion" del sistema deja: la sesion sigue en el store, pero
        // ni la alarma ni la bandeja existen — nada las programo en este proceso nuevo.

        AppStartup.start(app, container, testScope())

        eventually { focusAlarmScheduled(alarmManager) }
        eventually { shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID) != null }
        assertEquals(session, runBlocking { container.focus.session.first() })
    }

    @Test
    fun `start resolves a session that expired while the app was dead as the normal end flow does, without reprogramming a past alarm`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        NotificationChannels.ensure(app)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        runBlocking { container.tasks.create(taskEntity(id = "t1", title = "Leer")) }
        val session =
            FocusSession(
                taskId = "t1",
                startedAtMillis = System.currentTimeMillis() - 10 * 60_000L,
                endsAtMillis = System.currentTimeMillis() - 60_000L,
                endsAtElapsed = 0L,
                bootMillis = 0L,
            )
        runBlocking { container.focus.start(session) }

        AppStartup.start(app, container, testScope())

        eventually { shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID) != null }
        assertNotNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
        // Ni la sesion (decision del usuario, igual que cuando la alarma dispara con la app viva)
        // ni una alarma de foco para un instante que ya paso.
        assertEquals(session, runBlocking { container.focus.session.first() })
        assertTrue(!focusAlarmScheduled(alarmManager))
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
