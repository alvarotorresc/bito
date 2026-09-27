package com.alvarotc.bito.ui.breathing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.BreathingStats
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BreathingViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val utc = ZoneId.of("UTC")
    private val baseNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z, viernes
    private val baseElapsed = 50_000_000L
    private var currentNow = baseNow
    private var currentElapsed = baseElapsed

    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository
    private val liveViewModels = mutableListOf<BreathingViewModel>()

    private class FakeMusic : BreathingMusic {
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel(music: BreathingMusic = FakeMusic()) =
        BreathingViewModel(
            BreathingRepository(db),
            settingsRepo,
            DomainStateRepository(db),
            RewardsRepository(db),
            music,
            now = { currentNow },
            elapsed = { currentElapsed },
            zone = { utc },
        ).also { liveViewModels += it }

    /** Como [runTest], pero cancela el bucle de ticks de cada VM antes del drenado implicito. */
    private fun runBreathingTest(block: suspend TestScope.() -> Unit) =
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
        settingsRepo = SettingsRepository(store("breathing-vm-settings"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.activate(vm: BreathingViewModel) {
        vm.uiState.onEach {}.launchIn(backgroundScope)
        settle()
    }

    /** Drena lo que ya esta listo sin disparar ningun tick nuevo. */
    private fun TestScope.settle() {
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
    }

    /** Deja correr exactamente un tick del bucle, que recalcula con el reloj actual. */
    private fun TestScope.tick() {
        dispatcher.scheduler.advanceTimeBy(BreathingViewModel.TICK_MS)
        dispatcher.scheduler.runCurrent()
    }

    private suspend fun rows() = db.breathingSessionDao().all()

    @Test
    fun `start anchors at elapsed and shows the first inhale`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)

            vm.start()
            settle()

            val state = vm.uiState.value
            assertEquals(BreathingStage.RUNNING, state.stage)
            assertEquals(baseElapsed, state.anchorElapsed)
            assertEquals(BreathPhase.INHALE, state.phase)
            assertEquals(120, state.remainingSeconds)
            assertEquals(1, vm.phaseChanges.value)
        }

    @Test
    fun `reaching the end saves a complete session with the mode's duration and finishes`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()

            currentElapsed += 120_000
            tick()
            settle()

            assertEquals(BreathingStage.FINISHED, vm.uiState.value.stage)
            val saved = rows().single()
            assertEquals(BreathingMode.CALM, saved.mode)
            assertEquals(baseNow, saved.startedAtMillis)
            assertEquals(120, saved.durationSeconds)
            assertTrue(saved.completed)
        }

    @Test
    fun `stopping at nine seconds saves nothing and goes back to rest`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()

            currentElapsed += 9_999
            vm.stop()
            settle()

            assertEquals(BreathingStage.IDLE, vm.uiState.value.stage)
            assertTrue(rows().isEmpty())
        }

    @Test
    fun `stopping at ten seconds saves an incomplete session of ten`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()

            currentElapsed += 10_000
            vm.stop()
            settle()

            assertEquals(BreathingStage.FINISHED, vm.uiState.value.stage)
            val saved = rows().single()
            assertEquals(10, saved.durationSeconds)
            assertFalse(saved.completed)
        }

    @Test
    fun `going to the background behaves like stop, and does nothing outside a session`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)

            vm.onBackgrounded()
            settle()
            assertEquals(BreathingStage.IDLE, vm.uiState.value.stage)

            vm.start()
            settle()
            currentElapsed += 5_000
            vm.onBackgrounded()
            settle()
            assertEquals(BreathingStage.IDLE, vm.uiState.value.stage)
            assertTrue(rows().isEmpty())

            vm.start()
            settle()
            currentElapsed += 45_000
            vm.onBackgrounded()
            settle()
            assertEquals(BreathingStage.FINISHED, vm.uiState.value.stage)
            assertEquals(45, rows().single().durationSeconds)

            vm.onBackgrounded()
            settle()
            assertEquals(BreathingStage.FINISHED, vm.uiState.value.stage)
            assertEquals(1, rows().size)
        }

    @Test
    fun `phaseChanges goes up once per step, not once per tick`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()
            assertEquals(1, vm.phaseChanges.value)

            currentElapsed += 2_000
            tick()
            assertEquals(1, vm.phaseChanges.value)

            currentElapsed += 2_000 // 4 s: empieza la espiracion de Calmarme
            tick()
            assertEquals(2, vm.phaseChanges.value)
            assertEquals(BreathPhase.EXHALE, vm.uiState.value.phase)

            currentElapsed += 6_000 // 10 s: segundo ciclo
            tick()
            assertEquals(3, vm.phaseChanges.value)
            assertEquals(2, vm.uiState.value.cycle)
        }

    @Test
    fun `choosing a chip writes nothing and starting writes the mode`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)

            vm.selectMode(BreathingMode.SLEEP)
            settle()
            assertEquals(BreathingMode.SLEEP, vm.uiState.value.mode)
            assertEquals(6, vm.uiState.value.totalCycles)
            assertEquals(BreathingMode.CALM, settingsRepo.settings.first().breathingLastMode)

            vm.start()
            settle()
            assertEquals(BreathingMode.SLEEP, settingsRepo.settings.first().breathingLastMode)
        }

    @Test
    fun `the screen opens on the last used mode`() =
        runBreathingTest {
            settingsRepo.update { it.copy(breathingLastMode = BreathingMode.FOCUS) }
            val vm = newViewModel()
            activate(vm)

            assertEquals(BreathingMode.FOCUS, vm.uiState.value.mode)
            assertEquals(128, vm.uiState.value.remainingSeconds)
        }

    @Test
    fun `chips are ignored while running`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()

            vm.selectMode(BreathingMode.FOCUS)
            settle()

            assertEquals(BreathingMode.CALM, vm.uiState.value.mode)
            assertEquals(BreathingStage.RUNNING, vm.uiState.value.stage)
        }

    @Test
    fun `music plays only while running with the switch on and stops on every exit`() =
        runBreathingTest {
            val music = FakeMusic()
            val vm = newViewModel(music)
            activate(vm)

            vm.toggleMusic() // encendida en reposo: no suena todavia
            settle()
            assertEquals(0, music.starts)
            assertTrue(vm.uiState.value.musicEnabled)
            assertTrue(settingsRepo.settings.first().breathingMusicEnabled)

            vm.start()
            settle()
            assertEquals(1, music.starts)

            vm.toggleMusic() // apagada a mitad de sesion: se para
            settle()
            assertEquals(1, music.stops)
            vm.toggleMusic() // y vuelve
            settle()
            assertEquals(2, music.starts)

            currentElapsed += 30_000
            vm.stop()
            settle()
            assertEquals(2, music.stops)

            vm.toggleMusic() // en el final: se apaga el ajuste, no arranca nada
            vm.toggleMusic()
            settle()
            assertEquals(2, music.starts)
        }

    @Test
    fun `again starts a second independent session of the same mode`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.selectMode(BreathingMode.FOCUS)
            vm.start()
            settle()
            currentElapsed += 128_000
            tick()
            settle()

            vm.again()
            settle()
            assertEquals(BreathingStage.RUNNING, vm.uiState.value.stage)
            currentElapsed += 20_000
            vm.stop()
            settle()

            val saved = rows()
            assertEquals(2, saved.size)
            assertEquals(2, saved.map { it.id }.toSet().size)
            assertTrue(saved.all { it.mode == BreathingMode.FOCUS })
            assertEquals(setOf(128, 20), saved.map { it.durationSeconds }.toSet())
        }

    @Test
    fun `clearing the view model stops the music and saves nothing on its own`() =
        runBreathingTest {
            val music = FakeMusic()
            val store = ViewModelStore()
            val vm =
                ViewModelProvider(store, viewModelFactory { initializer { newViewModel(music) } })[BreathingViewModel::class.java]
            activate(vm)
            vm.toggleMusic()
            vm.start()
            settle()

            store.clear()
            settle()

            assertEquals(1, music.stops)
            assertTrue(rows().isEmpty())
        }

    // --- Review Focus -------------------------------------------------------------------

    @Test
    fun `leaving mid-session saves before gone and survives a cancelled scope`() =
        runBreathingTest {
            val vm = newViewModel()
            var rowsAtGone: Int? = null
            vm.uiState.onEach { state -> if (state.gone && rowsAtGone == null) rowsAtGone = rows().size }.launchIn(backgroundScope)
            settle()
            vm.start()
            settle()
            currentElapsed += 30_000

            vm.leave()
            settle()

            assertTrue(vm.uiState.value.gone)
            // La fila ya estaba escrita en el mismo fotograma en que gone paso a true, no despues.
            assertEquals(1, rowsAtGone)
            assertEquals(30, rows().single().durationSeconds)

            // La pantalla se desapila en el mismo fotograma en que para: el scope muere al instante.
            val torn = newViewModel()
            activate(torn)
            torn.start()
            settle()
            currentElapsed += 25_000
            torn.stop()
            torn.viewModelScope.cancel()
            settle()

            assertEquals(2, rows().size)
        }

    @Test
    fun `leaving mid-session never emits the finished stage, even when the session gets saved`() =
        runBreathingTest {
            val vm = newViewModel()
            val seenStages = mutableListOf<BreathingStage>()
            vm.uiState.onEach { seenStages += it.stage }.launchIn(backgroundScope)
            settle()
            vm.start()
            settle()
            currentElapsed += 30_000 // por encima de MIN_SAVED_SECONDS: si hubiera flash, aqui se veria.

            vm.leave()
            settle()

            assertTrue(vm.uiState.value.gone)
            assertEquals(1, rows().size)
            assertFalse(seenStages.contains(BreathingStage.FINISHED))
        }

    @Test
    fun `a double start runs one loop and saves one row`() =
        runBreathingTest {
            val music = FakeMusic()
            val vm = newViewModel(music)
            activate(vm)
            vm.toggleMusic()

            vm.start()
            vm.start()
            settle()
            assertEquals(1, music.starts)

            currentElapsed += 30_000
            vm.stop()
            settle()
            vm.again()
            vm.again()
            settle()
            assertEquals(2, music.starts)
            currentElapsed += 15_000
            vm.stop()
            settle()

            assertEquals(2, rows().size)
        }

    @Test
    fun `stopping twice or stopping at the natural end saves exactly one row`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()
            currentElapsed += 30_000
            vm.stop()
            vm.stop()
            settle()
            assertEquals(1, rows().size)

            vm.again()
            settle()
            currentElapsed += 120_000
            tick() // el tick llega al final y guarda
            vm.stop() // y el toque de «Parar» del mismo fotograma no guarda otra
            settle()

            val saved = rows()
            assertEquals(2, saved.size)
            assertEquals(1, saved.count { it.completed })
        }

    @Test
    fun `a wall clock jump mid-session does not change the saved duration`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()

            currentNow += 3 * 60 * 60 * 1_000L // el usuario adelanta el reloj tres horas
            currentElapsed += 30_000
            vm.stop()
            settle()

            val saved = rows().single()
            assertEquals(30, saved.durationSeconds)
            assertEquals(baseNow, saved.startedAtMillis)
        }

    @Test
    fun `the finished tally already includes the session just saved`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()
            currentElapsed += 30_000

            vm.stop()
            settle()

            val state = vm.uiState.value
            assertEquals(BreathingStage.FINISHED, state.stage)
            assertEquals(BreathingStats.Tally(sessions = 1, seconds = 30), state.week)
            assertEquals(BreathingStats.Tally(sessions = 1, seconds = 30), state.allTime)
            assertEquals(1, state.week.minutes)
        }

    @Test
    fun `the tally never dips to zero on the frame the stage turns finished, and never double counts`() =
        runBreathingTest {
            val vm = newViewModel()
            val states = mutableListOf<BreathingUiState>()
            vm.uiState.onEach { states += it }.launchIn(backgroundScope)
            settle()
            vm.start()
            settle()
            currentElapsed += 30_000

            vm.stop()
            settle()

            assertTrue(states.none { it.stage == BreathingStage.FINISHED && it.week.sessions == 0 })
            assertEquals(1, vm.uiState.value.week.sessions)

            // Deja que Room emita de sobra: la tabla no debe pasar a contar la misma sesion dos veces.
            settle()
            settle()
            assertEquals(1, vm.uiState.value.week.sessions)
            assertEquals(1, rows().size)
        }

    @Test
    fun `stopping right after the rhythm finishes still saves a complete session`() =
        runBreathingTest {
            val vm = newViewModel()
            activate(vm)
            vm.start()
            settle()

            // El ritmo ya llego al final pero el siguiente tick (100 ms) aun no ha corrido.
            currentElapsed += 120_000
            vm.stop()
            settle()

            assertEquals(BreathingStage.FINISHED, vm.uiState.value.stage)
            val saved = rows().single()
            assertEquals(120, saved.durationSeconds)
            assertTrue(saved.completed)
        }
}
