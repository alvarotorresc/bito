package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.settings.FocusSession
import com.alvarotc.bito.data.taskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * El segundo colector de [FocusSync]: unico dueño de limpiar una sesion viva cuya tarea ya no
 * existe (spec §8.5 "Recuperacion") — antes vivia en `FocusViewModel.init`, pero un ViewModel
 * muere con la pantalla y la sesion sigue en el store aunque nadie la mire. Bare [Application]
 * (sin arrancar `BitoApp`) y [AppContainer] propio, mismo molde que
 * `com.alvarotc.bito.AppStartupTest`, para que este test no dependa de si otro test del mismo fork
 * de JVM ya disparo `AppStartup`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FocusSyncTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private var scopeJob: Job? = null
    private var executor: ExecutorService? = null

    // Hilo propio, no Dispatchers.Default: en la suite completa ese pool esta compartido con
    // WidgetRefresher — uno de los cuatro colectores de AppStartup que queda vivo sin cancelar en
    // cuanto cualquier BitoApp implicita arranca en este fork de JVM (ver AppStartup/AppStartupTest)
    // — y competir por un hueco ahi metia una espera de decenas de segundos, a veces mas, en las
    // corrutinas de este colector.
    private fun testScope(): CoroutineScope {
        val exec = Executors.newSingleThreadExecutor()
        executor = exec
        val job = Job()
        scopeJob = job
        return CoroutineScope(exec.asCoroutineDispatcher() + job)
    }

    @Before
    fun setUp() {
        NotificationChannels.ensure(app)
        shadowOf(notificationManager).setNotificationsEnabled(true)
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { scopeJob?.cancelAndJoin() } }
        executor?.shutdownNow()
    }

    private fun postStandInFocusNotification() {
        val notification =
            NotificationCompat.Builder(app, NotificationChannels.REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("stand-in")
                .build()
        NotificationManagerCompat.from(app).notify(Notifier.FOCUS_ID, notification)
    }

    // Un poco mas que los 2s de siempre: las dos siguientes lineas de FocusSync tras el store
    // (Notifier.cancelFocus, FocusAlarm.cancel) son sincronas, pero corren en el hilo propio de
    // [testScope], no en el de este test.
    private fun eventually(
        timeoutMs: Long = 5_000,
        check: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!check()) {
            if (System.currentTimeMillis() > deadline) error("condition not met within ${timeoutMs}ms")
            Thread.sleep(5)
        }
    }

    @Test
    fun `a live session of a task that no longer exists clears the store, the tray and the alarm`() {
        val container = AppContainer(app)
        val session =
            FocusSession(
                taskId = "ghost",
                startedAtMillis = System.currentTimeMillis(),
                endsAtMillis = System.currentTimeMillis() + 5 * 60_000L,
                endsAtElapsed = 0L,
                bootMillis = 0L,
            )
        runBlocking { container.focus.start(session) }
        FocusAlarm.schedule(app, session.endsAtMillis)
        postStandInFocusNotification()

        FocusSync.start(app, container, testScope())

        // Espera SUSPENDIDA sobre el propio Flow, no un sondeo por Thread.sleep: este colector
        // combina NUEVE Flow de Room (DomainStateRepository.observe()) sobre un [AppContainer] con
        // base de datos real, y en la suite completa comparte el executor global de Room con
        // WidgetRefresher — uno de los cuatro colectores de AppStartup, que observa exactamente el
        // mismo domainState.observe() sin parar en cualquier BitoApp implicita que ya arrancara en
        // este fork de JVM (ver AppStartup/AppStartupTest).
        runBlocking { withTimeout(10_000) { container.focus.session.first { it == null } } }
        eventually { shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID) == null }
        eventually { shadowOf(alarmManager).scheduledAlarms.isEmpty() }
    }

    @Test
    fun `a live session of a task that still exists is left intact`() {
        val container = AppContainer(app)
        runBlocking { container.tasks.create(taskEntity(id = "t1", title = "Leer")) }
        val session =
            FocusSession(
                taskId = "t1",
                startedAtMillis = System.currentTimeMillis(),
                endsAtMillis = System.currentTimeMillis() + 5 * 60_000L,
                endsAtElapsed = 0L,
                bootMillis = 0L,
            )
        runBlocking { container.focus.start(session) }
        FocusAlarm.schedule(app, session.endsAtMillis)
        Notifier.showFocus(app, "Leer", session.endsAtMillis)

        FocusSync.start(app, container, testScope())
        // Da tiempo a que el colector, si fuera a limpiar algo, ya lo hubiera hecho — no hay una
        // condicion positiva que esperar aqui, la ausencia de cambio es la propia aserción.
        Thread.sleep(1_000)

        assertEquals(session, runBlocking { container.focus.session.first() })
        assertNotNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
