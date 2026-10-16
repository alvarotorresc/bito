package com.alvarotc.bito.sesion

import android.app.Application
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

/** Lo que viene relleno en Preparar según de dónde se llega (spec §4.1, §21.3, §21.4, §21.12). */
@RunWith(RobolectricTestRunner::class)
// Application a secas: BitoApp arrancaría AppStartup en cada test y sus colectores acapararían los hilos compartidos.
@Config(sdk = [35], application = Application::class)
class PrepararPlanTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoSesion
    private lateinit var motor: MotorSesionAndroid

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        motor = banco.motor()
        banco.permisoNoMolestar(false)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun borrador(): PlanSesion = requireNotNull(motor.borrador.value) { "sin borrador" }

    private fun esperado(
        objetivo: Objetivo,
        minutos: Int = 25,
        bloques: Int = 4,
    ) = PlanSesion(
        objetivo = objetivo,
        minutosFoco = minutos,
        entrada = Entrada.RESPIRAR,
        bloques = bloques,
        descansoCorto = 5,
        descansoLargo = 15,
        rondas = 4,
        descansoSolo = true,
        fondo = SessionBackground.RAIN,
        noMolestar = false,
    )

    @Test
    fun `a task prepares that task with pomo focus and rounds`() =
        runTest {
            banco.tasks.create(taskEntity(id = "t1"))
            motor.preparar(Destino.Tarea("t1"))
            assertEquals(esperado(Objetivo.Tarea("t1")), borrador())
        }

    @Test
    fun `the big thing prepares its first open task`() =
        runTest {
            banco.tasks.create(taskEntity(id = "hecha", status = TaskStatus.DONE, doneOnDay = banco.hoy, doneAtMillis = 1L))
            banco.tasks.create(taskEntity(id = "t2"))
            banco.loGordo.guardar(banco.hoy, listOf(RefGordo(TipoRefGordo.TAREA, "hecha"), RefGordo(TipoRefGordo.TAREA, "t2")))
            motor.preparar(Destino.LoGordo)
            assertEquals(Objetivo.Tarea("t2"), borrador().objetivo)
        }

    @Test
    fun `the big thing with only habits prepares the first habit`() =
        runTest {
            banco.habits.create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, logMode = LogMode.BINARY))
            banco.loGordo.guardar(banco.hoy, listOf(RefGordo(TipoRefGordo.HABITO, "h1")))
            motor.preparar(Destino.LoGordo)
            assertEquals(esperado(Objetivo.Habito("h1")), borrador())
        }

    @Test
    fun `a check habit prepares the habit with pomo focus and rounds`() =
        runTest {
            banco.habits.create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, logMode = LogMode.BINARY))
            motor.preparar(Destino.Habito("h1"))
            assertEquals(esperado(Objetivo.Habito("h1")), borrador())
        }

    @Test
    fun `a duration habit with 17 minutes left prepares 20 minutes in one block`() =
        runTest {
            banco.habits.create(habitEntity(id = "h1", metric = Metric.DURATION, target = 30, unit = null))
            banco.acciones.log("h1", 13)
            motor.preparar(Destino.Habito("h1"))
            assertEquals(esperado(Objetivo.Habito("h1"), minutos = 20, bloques = 1), borrador())
        }

    @Test
    fun `a duration habit already met still prepares five minutes`() =
        runTest {
            banco.habits.create(habitEntity(id = "h1", metric = Metric.DURATION, target = 30, unit = null))
            banco.acciones.log("h1", 30)
            motor.preparar(Destino.Habito("h1"))
            assertEquals(5, borrador().minutosFoco)
        }

    @Test
    fun `a count habit adds one step and prepares nothing`() =
        runTest {
            banco.habits.create(habitEntity(id = "h1", metric = Metric.COUNT, target = 8, step = 2))
            motor.preparar(Destino.Habito("h1"))
            assertNull(motor.borrador.value)
            val entradas: List<EntryEntity> = banco.db.entryDao().all()
            assertEquals(listOf(2), entradas.filter { it.habitId == "h1" }.map { it.value })
            assertEquals(banco.hoy, entradas.single().logicalDay)
        }

    @Test
    fun `with no destination the big thing goes first`() =
        runTest {
            banco.tasks.create(taskEntity(id = "vencida", dueKind = DueKind.DATE, dueDay = banco.hoy - 2))
            banco.tasks.create(taskEntity(id = "gorda"))
            banco.loGordo.guardar(banco.hoy, listOf(RefGordo(TipoRefGordo.TAREA, "gorda")))
            motor.preparar(null)
            assertEquals(Objetivo.Tarea("gorda"), borrador().objetivo)
        }

    @Test
    fun `with no destination and no big thing the oldest overdue task goes first`() =
        runTest {
            banco.tasks.create(taskEntity(id = "de-hoy", dueKind = DueKind.DATE, dueDay = banco.hoy))
            banco.tasks.create(taskEntity(id = "vencida", dueKind = DueKind.DATE, dueDay = banco.hoy - 1))
            banco.tasks.create(taskEntity(id = "mas-vencida", dueKind = DueKind.DATE, dueDay = banco.hoy - 4))
            motor.preparar(null)
            assertEquals(Objetivo.Tarea("mas-vencida"), borrador().objetivo)
        }

    @Test
    fun `with no destination and no overdue task the one due today goes first`() =
        runTest {
            banco.tasks.create(taskEntity(id = "suelta"))
            banco.tasks.create(taskEntity(id = "de-hoy", dueKind = DueKind.DATE, dueDay = banco.hoy))
            motor.preparar(null)
            assertEquals(Objetivo.Tarea("de-hoy"), borrador().objetivo)
        }

    @Test
    fun `with no tasks the session is free`() =
        runTest {
            motor.preparar(null)
            assertEquals(esperado(Objetivo.Libre), borrador())
        }

    @Test
    fun `dnd needs the setting and the permission`() =
        runTest {
            banco.settings.update { it.copy(sessionDnd = true) }
            motor.preparar(null)
            assertFalse(borrador().noMolestar)
            banco.permisoNoMolestar(true)
            motor.preparar(null)
            assertTrue(borrador().noMolestar)
        }

    @Test
    fun `the draft takes entry background and breaks from settings`() =
        runTest {
            banco.settings.update {
                it.copy(
                    sessionEntry = Entrada.DIRECTO,
                    sessionBackground = SessionBackground.CAFE,
                    pomoFocus = 40,
                    pomoRounds = 3,
                    pomoShort = 7,
                    pomoLong = 20,
                    pomoAutoBreak = false,
                )
            }
            motor.preparar(null)
            val plan = borrador()
            assertEquals(Entrada.DIRECTO, plan.entrada)
            assertEquals(SessionBackground.CAFE, plan.fondo)
            assertEquals(40, plan.minutosFoco)
            assertEquals(3, plan.bloques)
            assertEquals(3, plan.rondas)
            assertEquals(7, plan.descansoCorto)
            assertEquals(20, plan.descansoLargo)
            assertFalse(plan.descansoSolo)
        }

    @Test
    fun `the draft remembers entry and dnd but not time or blocks`() =
        runTest {
            motor.preparar(null)
            motor.cambiarBorrador(borrador().copy(entrada = Entrada.DIRECTO, noMolestar = true, minutosFoco = 45, bloques = 2))
            motor.borradorGuardado()
            val guardado = banco.settings.settings.first()
            assertEquals(Entrada.DIRECTO, guardado.sessionEntry)
            assertTrue(guardado.sessionDnd)
            assertEquals(25, guardado.pomoFocus)
            assertEquals(4, guardado.pomoRounds)
            assertEquals(45, borrador().minutosFoco)
        }

    @Test
    fun `two quick changes are saved in order`() =
        runTest {
            motor.preparar(null)
            motor.cambiarBorrador(borrador().copy(fondo = SessionBackground.BROWN))
            motor.cambiarBorrador(borrador().copy(fondo = SessionBackground.CAFE))
            motor.borradorGuardado()
            assertEquals(SessionBackground.CAFE, banco.settings.settings.first().sessionBackground)
        }

    @Test
    fun `changing the background in prepare saves it to settings`() =
        runTest {
            motor.preparar(null)
            motor.cambiarBorrador(borrador().copy(fondo = SessionBackground.BROWN))
            motor.borradorGuardado()
            val guardado = banco.settings.settings.first()
            assertEquals(SessionBackground.BROWN, guardado.sessionBackground)
            assertEquals(Entrada.RESPIRAR, guardado.sessionEntry)
            assertFalse(guardado.sessionDnd)
        }
}
