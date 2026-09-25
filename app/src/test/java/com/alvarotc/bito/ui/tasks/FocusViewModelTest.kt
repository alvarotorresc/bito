package com.alvarotc.bito.ui.tasks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.FocusStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.ui.notifications.FocusPresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val utc = ZoneId.of("UTC")
    private val baseNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val baseElapsed = 50_000_000L // uptime arbitrario, muy menor que baseNow
    private var currentNow = baseNow
    private var currentElapsed = baseElapsed
    private val today = LogicalDays.logicalDayOf(baseNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var tasksRepo: TasksRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var focusStore: FocusStore

    // Cada VM tiene un tick vivo (delay(1_000) sin fin) en su propio viewModelScope, ajeno al
    // arbol de corrutinas de runTest: sin cancelarlo antes de que el cuerpo del test termine,
    // el drenado implicito de runTest lo persigue para siempre y la suite nunca vuelve.
    private val liveViewModels = mutableListOf<FocusViewModel>()

    private class FakeFocusPresence : FocusPresence {
        var showCalls = 0
        var lastTitle: String? = null
        var lastEndsAtMillis: Long? = null
        var clearCalls = 0

        override fun show(
            taskTitle: String,
            endsAtMillis: Long,
        ) {
            showCalls++
            lastTitle = taskTitle
            lastEndsAtMillis = endsAtMillis
        }

        override fun clear() {
            clearCalls++
        }
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel(
        taskId: String?,
        presence: FakeFocusPresence = FakeFocusPresence(),
        // Mirrors the VM's own default range; tests that care about habiNudge's exact timing
        // override this with a fixed value instead of fighting the real randomness.
        nudgeInterval: () -> Long = { Random.nextLong(20_000L, 40_001L) },
    ) = FocusViewModel(
        tasksRepo,
        focusStore,
        settingsRepo,
        domainStateRepo,
        rewardsRepo,
        reconciler,
        presence,
        taskId,
        now = { currentNow },
        elapsed = { currentElapsed },
        zone = { utc },
        nudgeInterval = nudgeInterval,
    ).also { liveViewModels += it }

    /** Como [runTest], pero cancela el tick de cada VM creado antes de que el drenado implicito lo vea. */
    private fun runFocusTest(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                liveViewModels.forEach { it.viewModelScope.cancel() }
                liveViewModels.clear()
            }
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        domainStateRepo = DomainStateRepository(db)
        tasksRepo = TasksRepository(db)
        settingsRepo = SettingsRepository(store("focus-vm-settings"))
        rewardsRepo = RewardsRepository(db)
        reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        focusStore = FocusStore(store("focus-vm-focus"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** Suscribe [vm] para que su StateFlow arranque y deja correr lo que ya esta listo. */
    private fun TestScope.activate(vm: FocusViewModel) {
        vm.uiState.onEach {}.launchIn(backgroundScope)
        settle()
    }

    /** Un avance breve que drena las corrutinas listas sin tocar el bucle de tick — nunca cuelga. */
    private fun TestScope.settle() {
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
    }

    /** Deja que el `delay(1_000)` del tick complete una vuelta, para recalcular con el reloj actual. */
    private fun TestScope.advanceOneTick() {
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `starting writes the session with both clocks and shows the presence`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", title = "Llamar al banco", createdOnDay = today))
            val presence = FakeFocusPresence()
            val vm = newViewModel("t1", presence)
            activate(vm)

            vm.start()
            settle()

            val session = focusStore.session.first()!!
            assertEquals("t1", session.taskId)
            assertEquals(currentNow, session.startedAtMillis)
            assertEquals(currentNow + FocusViewModel.DEFAULT_MINUTES * 60_000L, session.endsAtMillis)
            assertEquals(currentElapsed + FocusViewModel.DEFAULT_MINUTES * 60_000L, session.endsAtElapsed)
            assertEquals(currentNow - currentElapsed, session.bootMillis)
            assertEquals(1, presence.showCalls)
            assertEquals("Llamar al banco", presence.lastTitle)
            assertEquals(session.endsAtMillis, presence.lastEndsAtMillis)
            assertTrue(vm.uiState.value.running)
            assertEquals(FocusViewModel.DEFAULT_MINUTES * 60_000L, vm.uiState.value.remainingMillis)
        }

    @Test
    fun `selecting a custom number of minutes starts a session that long`() =
        // "Otro" (D6/D14 revoked): select() already took any int before this task, this only
        // proves the pill-less path the UI now offers actually reaches it.
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm = newViewModel("t1")
            activate(vm)

            vm.select(40)
            vm.start()
            settle()

            val session = focusStore.session.first()!!
            assertEquals(currentNow + 40 * 60_000L, session.endsAtMillis)
            assertEquals(40 * 60_000L, vm.uiState.value.remainingMillis)
        }

    @Test
    fun `plus five, fifteen and thirty move the end and write nothing in the domain`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val presence = FakeFocusPresence()
            val vm = newViewModel("t1", presence)
            activate(vm)
            vm.start()
            settle()
            val startEndsAtMillis = focusStore.session.first()!!.endsAtMillis

            vm.extend(5)
            settle()
            assertEquals(startEndsAtMillis + 5 * 60_000L, focusStore.session.first()!!.endsAtMillis)

            vm.extend(15)
            settle()
            assertEquals(startEndsAtMillis + 20 * 60_000L, focusStore.session.first()!!.endsAtMillis)

            vm.extend(30)
            settle()
            assertEquals(startEndsAtMillis + 50 * 60_000L, focusStore.session.first()!!.endsAtMillis)

            assertEquals(4, presence.showCalls) // el arranque + los tres "+"
            assertTrue(db.taskEventDao().all().isEmpty())
            assertEquals(TaskStatus.OPEN, db.taskDao().byId("t1")!!.status)
        }

    @Test
    fun `two extends fired back to back without waiting between them still add up`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm = newViewModel("t1")
            activate(vm)
            vm.start()
            settle()
            val startEndsAtMillis = focusStore.session.first()!!.endsAtMillis

            // Sin settle() entre medias: las dos corrutinas de extend() quedan encoladas antes de
            // que ninguna llegue a leer la sesion — el mutex es lo unico que evita que ambas lean
            // la MISMA sesion vieja y una de las dos "+" se pierda.
            vm.extend(5)
            vm.extend(5)
            settle()

            assertEquals(startEndsAtMillis + 10 * 60_000L, focusStore.session.first()!!.endsAtMillis)
        }

    @Test
    fun `finishing marks the task done right away, clears session and presence before the hold, then goes gone after it`() =
        // Recalibrado dos veces. Habi acompana al terminar: finish() sostiene FINISH_HOLD_MS con
        // el bocadillo de HabiVoice.taskDoneRes antes de marcar gone. M10 review ola 4 (Critical):
        // la limpieza de sesion y bandeja ya NO espera al hold -- corre justo despues de marcar la
        // tarea, para que un ViewModel que muere durante la pausa (atras, recientes, proceso) no
        // deje nada huerfano. El bocadillo se sostiene con lo que finish() guardo (heldDone), no
        // con la sesion viva.
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val presence = FakeFocusPresence()
            val vm = newViewModel("t1", presence)
            activate(vm)
            vm.start()
            settle()

            vm.finish()
            settle()

            val task = db.taskDao().byId("t1")!!
            assertEquals(TaskStatus.DONE, task.status)
            assertEquals(today, task.doneOnDay)
            assertTrue(db.pointsLedgerDao().all().any { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" })
            // Todavia en la pausa: la pantalla sigue "running" con el aviso, pero sesion y bandeja
            // ya estan limpias -- si el ViewModel muriera aqui mismo no quedaria nada por limpiar.
            assertTrue(vm.uiState.value.running)
            assertTrue(vm.uiState.value.justFinished)
            assertFalse(vm.uiState.value.gone)
            assertEquals(1, presence.clearCalls)
            assertNull(focusStore.session.first())

            dispatcher.scheduler.advanceTimeBy(1_500)
            dispatcher.scheduler.runCurrent()

            assertEquals(1, presence.clearCalls)
            assertTrue(vm.uiState.value.gone)
        }

    @Test
    fun `finishing twice back to back without waiting between them clears only once`() =
        // Sin el guard de reentrada en finish(), dos toques en el mismo fotograma lanzarian dos
        // corrutinas -- cada una limpiaria sesion y bandeja por su cuenta, doblando presence.clearCalls.
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val presence = FakeFocusPresence()
            val vm = newViewModel("t1", presence)
            activate(vm)
            vm.start()
            settle()

            vm.finish()
            vm.finish()
            settle()

            assertEquals(1, presence.clearCalls)

            dispatcher.scheduler.advanceTimeBy(1_500)
            dispatcher.scheduler.runCurrent()

            assertEquals(1, presence.clearCalls)
            assertTrue(vm.uiState.value.gone)
        }

    @Test
    fun `finishing through the bare focus route still shows the done phrase`() =
        // La notificacion permanente y onKeepOther entran por "focus" a secas: requestedTaskId es
        // null, asi que el bocadillo no puede depender de el ni de la sesion (limpia justo despues
        // de marcar la tarea) -- solo de lo que finish() guardo en heldDone.
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", title = "Llamar al banco", createdOnDay = today))
            val vm1 = newViewModel("t1")
            activate(vm1)
            vm1.start()
            settle()

            val vm2 = newViewModel(null)
            activate(vm2)
            assertTrue(vm2.uiState.value.running)

            vm2.finish()
            settle()

            assertTrue(vm2.uiState.value.running)
            assertTrue(vm2.uiState.value.justFinished)
            assertEquals("Llamar al banco", vm2.uiState.value.title)
            assertFalse(vm2.uiState.value.gone)

            dispatcher.scheduler.advanceTimeBy(1_500)
            dispatcher.scheduler.runCurrent()

            assertTrue(vm2.uiState.value.gone)
        }

    @Test
    fun `giving up writes an ATTEMPT, clears the session and the presence`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val presence = FakeFocusPresence()
            val vm = newViewModel("t1", presence)
            activate(vm)
            vm.start()
            settle()

            vm.giveUp()
            settle()

            assertEquals(TaskStatus.OPEN, db.taskDao().byId("t1")!!.status)
            assertTrue(
                db.taskEventDao().all().any { it.taskId == "t1" && it.kind == TaskEventKind.ATTEMPT && it.logicalDay == today },
            )
            assertNull(focusStore.session.first())
            assertEquals(1, presence.clearCalls)
            assertTrue(vm.uiState.value.gone)
        }

    @Test
    fun `the session survives rebuilding the view model`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm1 = newViewModel("t1")
            activate(vm1)
            vm1.start()
            settle()
            val endsAtMillis = focusStore.session.first()!!.endsAtMillis

            // Reconstruida pidiendo la misma tarea (p.ej. se volvio a abrir su pantalla).
            val vm2 = newViewModel("t1")
            activate(vm2)
            assertTrue(vm2.uiState.value.running)
            assertEquals("t1", vm2.uiState.value.taskId)
            assertEquals(endsAtMillis - currentNow, vm2.uiState.value.remainingMillis)

            // Reconstruida sin tarea pedida (la notificacion permanente abre "focus" a secas).
            val vm3 = newViewModel(null)
            activate(vm3)
            assertTrue(vm3.uiState.value.running)
            assertEquals("t1", vm3.uiState.value.taskId)
        }

    @Test
    fun `a different boot signature makes the wall clock decide what is left`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm = newViewModel("t1")
            activate(vm)
            vm.start()
            settle()
            val endsAtMillis = focusStore.session.first()!!.endsAtMillis

            // Pasan 4 minutos de reloj y el dispositivo se reinicia: el uptime vuelve casi a cero.
            currentNow += 4 * 60_000L
            currentElapsed = 5_000L
            advanceOneTick()

            assertEquals(endsAtMillis - currentNow, vm.uiState.value.remainingMillis)
            assertTrue(vm.uiState.value.running)
        }

    @Test
    fun `starting another task while one runs writes an ATTEMPT on the previous one`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", title = "Tarea uno", createdOnDay = today))
            tasksRepo.create(taskEntity(id = "t2", title = "Tarea dos", createdOnDay = today))
            val vm1 = newViewModel("t1")
            activate(vm1)
            vm1.start()
            settle()

            val presence2 = FakeFocusPresence()
            val vm2 = newViewModel("t2", presence2)
            activate(vm2)
            // La sesion de t1 sigue viva: se repostea aunque esta pantalla pida t2 y muestre el conflicto.
            assertEquals(1, presence2.showCalls)
            assertEquals("Tarea uno", vm2.uiState.value.busyWith)
            assertEquals("t2", vm2.uiState.value.taskId)
            assertFalse(vm2.uiState.value.running)

            vm2.switchToRequested()
            settle()

            assertTrue(
                db.taskEventDao().all().any { it.taskId == "t1" && it.kind == TaskEventKind.ATTEMPT && it.logicalDay == today },
            )
            val session = focusStore.session.first()!!
            assertEquals("t2", session.taskId)
            assertEquals(2, presence2.showCalls) // el repost de t1 al construirse + el arranque de t2
        }

    @Test
    fun `requesting another task while the previous one already expired shows no conflict and steals it silently`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", title = "Tarea uno", createdOnDay = today))
            tasksRepo.create(taskEntity(id = "t2", title = "Tarea dos", createdOnDay = today))
            val vm1 = newViewModel("t1")
            activate(vm1)
            vm1.select(5)
            vm1.start()
            settle()

            currentNow += 6 * 60_000L // la sesion de t1 ya vencio

            val vm2 = newViewModel("t2")
            activate(vm2)
            assertNull(vm2.uiState.value.busyWith)
            assertFalse(vm2.uiState.value.running)

            vm2.start()
            settle()

            val session = focusStore.session.first()!!
            assertEquals("t2", session.taskId)
            assertTrue(db.taskEventDao().all().none { it.taskId == "t1" })
        }

    @Test
    fun `keeping the other one touches nothing`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            tasksRepo.create(taskEntity(id = "t2", createdOnDay = today))
            val vm1 = newViewModel("t1")
            activate(vm1)
            vm1.start()
            settle()

            val presence2 = FakeFocusPresence()
            val vm2 = newViewModel("t2", presence2)
            activate(vm2)
            assertEquals(1, presence2.showCalls) // el repost de la sesion viva de t1, ajeno a keepOther

            vm2.keepOther()
            settle()

            assertEquals(1, presence2.showCalls)
            assertTrue(db.taskEventDao().all().isEmpty())
            assertEquals("t1", focusStore.session.first()!!.taskId)
            assertEquals(0, presence2.clearCalls)
            assertEquals(TaskStatus.OPEN, db.taskDao().byId("t1")!!.status)
        }

    @Test
    fun `a session whose task was deleted leaves without noise`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val presence = FakeFocusPresence()
            val vm = newViewModel("t1", presence)
            activate(vm)
            vm.start()
            settle()
            assertTrue(vm.uiState.value.running)

            tasksRepo.delete("t1")
            settle()

            // La limpieza del store, la bandeja y la alarma ya no las hace este ViewModel — es
            // FocusSync quien las posee (ver FocusSyncTest), asi que aqui solo queda comprobar que
            // la pantalla se va sin ruido.
            assertTrue(vm.uiState.value.gone)
            assertTrue(db.taskEventDao().all().isEmpty()) // ni DONE ni ATTEMPT: la tarea ya no esta
        }

    @Test
    fun `an end already past reports zero and keeps the five buttons alive`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm = newViewModel("t1")
            activate(vm)
            vm.select(5)
            vm.start()
            settle()

            currentNow += 6 * 60_000L // ya paso el fin de una sesion de 5 minutos
            advanceOneTick()

            assertEquals(0L, vm.uiState.value.remainingMillis)
            assertTrue(vm.uiState.value.running)
            assertFalse(vm.uiState.value.gone)
        }

    @Test
    fun `extending an expired session counts the extra minutes from now, not from the stale end`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val presence = FakeFocusPresence()
            val vm = newViewModel("t1", presence)
            activate(vm)
            vm.select(5)
            vm.start()
            settle()

            currentNow += 6 * 60_000L // ya paso el fin de una sesion de 5 minutos, como en el caso de arriba
            advanceOneTick()
            assertEquals(0L, vm.uiState.value.remainingMillis)

            vm.extend(5)
            settle()

            assertEquals(5 * 60_000L, vm.uiState.value.remainingMillis)
        }

    @Test
    fun `advancing both clocks by the same amount counts down what elapsed time took`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm = newViewModel("t1")
            activate(vm)
            vm.start()
            settle()

            // El tiempo pasa sin reiniciar el dispositivo: los dos relojes avanzan igual, la firma
            // de arranque sigue coincidiendo y el reloj de uptime decide cuanto queda.
            currentNow += 4 * 60_000L
            currentElapsed += 4 * 60_000L
            advanceOneTick()

            assertEquals((FocusViewModel.DEFAULT_MINUTES - 4) * 60_000L, vm.uiState.value.remainingMillis)
            assertTrue(vm.uiState.value.running)
        }

    @Test
    fun `reopening on a live session rearms the alarm and reposts the notification`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", title = "Llamar al banco", createdOnDay = today))
            val vm1 = newViewModel("t1")
            activate(vm1)
            vm1.start()
            settle()
            val session = focusStore.session.first()!!

            // Simula que "forzar detencion" mato la alarma y la notificacion; la sesion sigue en el store.
            val presence = FakeFocusPresence()
            val vm2 = newViewModel("t1", presence)
            activate(vm2)

            assertEquals(1, presence.showCalls)
            assertEquals("Llamar al banco", presence.lastTitle)
            assertEquals(session.endsAtMillis, presence.lastEndsAtMillis)
        }

    @Test
    fun `reopening on an expired session rearms nothing`() =
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm1 = newViewModel("t1")
            activate(vm1)
            vm1.select(5)
            vm1.start()
            settle()

            currentNow += 6 * 60_000L // ya vencio antes de reabrir la pantalla

            val presence = FakeFocusPresence()
            val vm2 = newViewModel("t1", presence)
            activate(vm2)

            assertEquals(0, presence.showCalls)
            assertTrue(vm2.uiState.value.running)
            assertEquals(0L, vm2.uiState.value.remainingMillis)
        }

    @Test
    fun `habi nudges itself while the countdown really runs, never at rest or once expired`() =
        // Habi acompana de verdad (spec Sec8.5 revocado en parte). nudgeInterval fijo en 5s: la
        // aleatoriedad real (20-40s) no estorba la aritmetica del test.
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm = newViewModel("t1", nudgeInterval = { 5_000L })
            activate(vm)

            assertEquals(0, vm.habiNudge.value) // en reposo, antes de "Empezar": nada

            vm.select(5)
            vm.start()
            settle()

            assertEquals(0, vm.habiNudge.value) // arranca, pero el primer intervalo aun no paso

            dispatcher.scheduler.advanceTimeBy(5_000)
            dispatcher.scheduler.runCurrent()
            assertEquals(1, vm.habiNudge.value)

            dispatcher.scheduler.advanceTimeBy(5_000)
            dispatcher.scheduler.runCurrent()
            assertEquals(2, vm.habiNudge.value)

            currentNow += 6 * 60_000L // vence la sesion de 5 minutos
            dispatcher.scheduler.advanceTimeBy(5_000)
            dispatcher.scheduler.runCurrent()
            val nudgesAtExpiry = vm.habiNudge.value

            // Vencida (00:00): el bucle ya no reprograma nada, por mucho que el reloj siga andando.
            dispatcher.scheduler.advanceTimeBy(30_000)
            dispatcher.scheduler.runCurrent()
            assertEquals(nudgesAtExpiry, vm.habiNudge.value)
        }

    @Test
    fun `no nudge fires if the session expires while waiting for the next one`() =
        // M10 review ola 4 (Minor #4): el incremento tras el delay era incondicional, asi que una
        // sesion que vence DURANTE la espera disparaba exactamente un pulso de mas justo despues
        // del 00:00 -- lo que "vencida: nada" excluye. currentNow salta 6 minutos antes de que el
        // primer intervalo de 5s cumpla, asi que la sesion de 5 minutos ya esta vencida cuando el
        // delay se resuelve.
        runFocusTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            val vm = newViewModel("t1", nudgeInterval = { 5_000L })
            activate(vm)
            vm.select(5)
            vm.start()
            settle()

            currentNow += 6 * 60_000L
            dispatcher.scheduler.advanceTimeBy(5_000)
            dispatcher.scheduler.runCurrent()

            assertEquals(0, vm.habiNudge.value)
        }
}
