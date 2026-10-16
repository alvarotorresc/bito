package com.alvarotc.bito.ui.today

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.habla.aDominio
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.TipoMemoria
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * La vuelta (spec §5.11) queda en la memoria con el primer registro tras 3 o más días fuera, venga de donde venga: aquí,
 * desde la lista de Hoy, con el diario del contenedor de la app. Una sola vez, y nada si solo se abre la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VueltaDesdeLaListaTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val zona = ZoneId.systemDefault()
    private val hoy = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, zona)
    private val ahora = mediodia(hoy)
    private lateinit var c: AppContainer
    private lateinit var vm: TodayViewModel
    private val escucha = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun mediodia(dia: Int): Long = LocalDate.ofEpochDay(dia.toLong()).atTime(12, 0).atZone(zona).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        c = AppContainer(app)
        runBlocking {
            c.settings.update { it.copy(onboardingDone = true, dayCutoffMinutes = 0) }
            c.habits.create(habitEntity(id = "agua", name = "Agua", createdOnDay = hoy - 20))
            // Lo último que hizo: hace 4 días.
            c.journal.log(EntryEntity("antes", "agua", hoy - 4, 1, mediodia(hoy - 4)))
        }
        vm =
            TodayViewModel(
                c.domainState,
                c.habits,
                c.journal,
                c.settings,
                c.reconciler,
                c.rewards,
                tasks = c.tasks,
                now = { ahora },
                zone = { zona },
            )
        escucha.launch { vm.uiState.collect() }
    }

    @After
    fun tearDown() {
        escucha.cancel()
    }

    private fun vueltas(): List<EventoMemoria> =
        runBlocking { c.database.hablaDao().todosEventos().mapNotNull { it.aDominio() }.filter { it.tipo == TipoMemoria.VUELTA } }

    private fun entradasDeHoy(): Int = runBlocking { c.database.entryDao().all().count { it.logicalDay == hoy } }

    private fun hasta(
        timeoutMs: Long = 5_000,
        check: () -> Boolean,
    ) {
        val limite = System.currentTimeMillis() + timeoutMs
        while (!check()) {
            if (System.currentTimeMillis() > limite) error("no llegó en $timeoutMs ms")
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun tarjeta(): HabitCardUi {
        hasta { vm.uiState.value.cards.any { it.id == "agua" } }
        return vm.uiState.value.cards.single { it.id == "agua" }
    }

    @Test
    fun `the first log from the list after four days away leaves the return in the memory, once`() {
        vm.tapPrimary(tarjeta())
        hasta { entradasDeHoy() == 1 }
        hasta { vueltas().isNotEmpty() }

        val vuelta = vueltas().single()
        assertEquals(hoy, vuelta.dia)
        assertEquals(4, vuelta.valor)
        assertEquals("agua", vuelta.ref)

        vm.tapPrimary(tarjeta())
        hasta { entradasDeHoy() == 2 }

        assertEquals(listOf(vuelta), vueltas())
    }

    @Test
    fun `opening the app without logging leaves no return`() {
        tarjeta()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(vueltas().isEmpty())
    }
}
