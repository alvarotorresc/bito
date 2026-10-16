package com.alvarotc.bito.sesion

import android.app.Application
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import com.alvarotc.bito.domain.sesion.Tramo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La prueba del mock (`&prueba=1`), pasos 1 a 9 de spec §18.1, con el reloj falso. */
@RunWith(RobolectricTestRunner::class)
// Application a secas: BitoApp arrancaría AppStartup en cada test y sus colectores acapararían los hilos compartidos.
@Config(sdk = [35], application = Application::class)
class RecorridoMockTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoSesion

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        banco.permisoNoMolestar(false)
    }

    @After
    fun tearDown() = banco.cerrar()

    @Test
    fun `the mock walkthrough from Hoy to finished`() =
        runTest {
            val motor = banco.motor()
            banco.tasks.create(taskEntity(id = "t1", title = "Informe", dueKind = DueKind.DATE, dueDay = banco.hoy))

            // 1. Hoy → «Sesión de 25 min» → Preparar con la tarea, 25 y 4 bloques.
            motor.preparar(Destino.Tarea("t1"))
            val plan = motor.borrador.value!!
            assertEquals(Objetivo.Tarea("t1"), plan.objetivo)
            assertEquals(25, plan.minutosFoco)
            assertEquals(4, plan.bloques)

            // 2. Empezar → Respirar para entrar.
            assertNull(motor.empezar())
            assertTrue(motor.instante.value?.actual is Tramo.Respirar)

            // 3. Tras 60 s, Foco del bloque 1.
            banco.reloj.avanzar(60_000L)
            motor.tic()
            assertEquals(Tramo.Foco(1, 25), motor.instante.value?.actual)

            // 4. Al acabar el bloque 1, descanso corto. 6. El descanso lleva al bloque 2.
            banco.reloj.avanzar(25 * MINUTO)
            motor.tic()
            assertEquals(Tramo.Descanso(1, largo = false, minutos = 5), motor.instante.value?.actual)
            banco.reloj.avanzar(5 * MINUTO)
            motor.tic()
            assertEquals(Tramo.Foco(2, 25), motor.instante.value?.actual)

            // 7. Bloques 3 y 4 y, tras el 4, el descanso largo de 15.
            for (bloque in 2..3) {
                banco.reloj.avanzar(25 * MINUTO)
                motor.tic()
                assertEquals(Tramo.Descanso(bloque, largo = false, minutos = 5), motor.instante.value?.actual)
                banco.reloj.avanzar(5 * MINUTO)
                motor.tic()
                assertEquals(Tramo.Foco(bloque + 1, 25), motor.instante.value?.actual)
            }
            banco.reloj.avanzar(25 * MINUTO)
            motor.tic()
            assertEquals(Tramo.Descanso(4, largo = true, minutos = 15), motor.instante.value?.actual)

            // 8. Terminada con 1 h 40 min de foco.
            banco.reloj.avanzar(15 * MINUTO)
            motor.tic()
            val terminada = motor.instante.value!!
            assertNull(terminada.actual)
            assertEquals(100 * MINUTO, terminada.focoMillis)

            // 9. «Sí, terminado» da los bitos que apunta el libro (5: en plazo) y cierra.
            val resultado = motor.siTerminado()
            val libro = banco.domainState.snapshot().pointsLedger.single { it.reason == PointsReason.TASK_DONE }
            assertEquals(5, libro.delta)
            assertEquals(libro.delta, resultado.bitos)
            assertEquals(TaskStatus.DONE, banco.tasks.task("t1")?.status)
            assertNull(motor.registro.value)
            assertNull(banco.store.leer())
            val fila = banco.db.sesionDao().all().single()
            assertEquals(100, fila.minutosFoco)
            assertEquals(4, fila.bloquesHechos)
            assertEquals(ResultadoSesion.HECHA.name, fila.resultado)
        }
}
