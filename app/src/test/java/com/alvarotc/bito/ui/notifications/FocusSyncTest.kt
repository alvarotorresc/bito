package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.data.settings.FocusSession
import com.alvarotc.bito.data.taskEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Molde de FocusAlarmTest: una BitoApp implicita de este mismo fork de JVM puede haber
        // disparado ya AppStartup.start() con SU propio container — quiescing cancela lo que ese
        // arranque hubiera dejado vivo en defaultScope, para que el container de este test no
        // comparta DataStore/Room con uno ajeno que nadie va a cerrar.
        AppStartup.quiesceForTests()
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { scopeJob?.cancelAndJoin() } }
        executor?.shutdownNow()
        AppStartup.quiesceForTests()
    }

    private fun postStandInFocusNotification() {
        val notification =
            NotificationCompat.Builder(app, NotificationChannels.REMINDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("stand-in")
                .build()
        NotificationManagerCompat.from(app).notify(Notifier.FOCUS_ID, notification)
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

        // Gancho deterministico: se dispara cuando el tercer colector termina de evaluar el
        // primer ciclo (aqui, el de limpieza), ya con clear()/cancelFocus()/cancel() resueltos —
        // nada que sondear con Thread.sleep ni con un timeout corto.
        val done = CompletableDeferred<Unit>()
        FocusSync.start(app, container, testScope(), onCycle = { done.complete(Unit) })
        runBlocking { withTimeout(10_000) { done.await() } }

        assertEquals(null, runBlocking { container.focus.session.first() })
        assertNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    /**
     * La limpieza de la bandeja y la alarma no puede depender de quien gane la carrera entre la
     * escritura del propio `focus.clear()` y la nueva emision que esa misma escritura provoca. Con
     * `collectLatest`, la sesion ya nula llegaba al colector y CANCELABA el bloque que la acababa
     * de limpiar, antes de `Notifier.cancelFocus`/`FocusAlarm.cancel`: la alarma quedaba
     * programada para una sesion que ya no existe. [Dispatchers.Unconfined] fuerza justo ese
     * orden (la emision recorre la cadena entera dentro de la escritura, antes de que la escritura
     * reanude a quien la pidio), el mismo que un runner cargado o el pool multihilo de produccion
     * pueden dar por azar.
     */
    @Test
    fun `the cleanup of a vanished task survives its own store write re-emitting the session`() {
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

        val job = Job()
        scopeJob = job
        val done = CompletableDeferred<Unit>()
        FocusSync.start(app, container, CoroutineScope(Dispatchers.Unconfined + job), onCycle = { done.complete(Unit) })

        runBlocking { withTimeout(10_000) { done.await() } }
        assertEquals(null, runBlocking { container.focus.session.first() })
        assertNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
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

        // Mismo gancho que arriba: espera a que el tercer colector evalue su primer ciclo (aqui,
        // sin limpiar nada, porque la tarea sigue existiendo) en vez de dar por hecho un tiempo
        // fijo con Thread.sleep.
        val done = CompletableDeferred<Unit>()
        FocusSync.start(app, container, testScope(), onCycle = { done.complete(Unit) })
        runBlocking { withTimeout(10_000) { done.await() } }

        assertEquals(session, runBlocking { container.focus.session.first() })
        assertNotNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `recoverOnStart posts nothing and schedules nothing for an expired session, however many times the process starts`() {
        val container = AppContainer(app)
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

        // Tres arranques de proceso (recordatorio, widget, medianoche...): ni uno solo avisa.
        repeat(3) { runBlocking { FocusSync.recoverOnStart(app, container) } }

        assertNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertEquals(session, runBlocking { container.focus.session.first() })
    }

    @Test
    fun `recoverOnStart rearms a live session once, and a second process start duplicates nothing`() {
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

        runBlocking { FocusSync.recoverOnStart(app, container) }
        runBlocking { FocusSync.recoverOnStart(app, container) }

        assertNotNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(session, runBlocking { container.focus.session.first() })
    }
}
