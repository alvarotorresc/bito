package com.alvarotc.bito.ui.sesion

import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadoSesionUiTest {
    private val motor = MotorSesionFalso()
    private val minuto = 60_000L

    @Test
    fun `nothing to show is today`() {
        val e = estadoSesionUi(null, null, preparando = false)

        assertNull(e.bloque)
        assertEquals(Encuadre.HOY, e.encuadre)
    }

    @Test
    fun `a draft is prepare`() {
        val e = estadoSesionUi(null, planSesion(), preparando = true)

        assertEquals(BloqueSesion.PREPARAR, e.bloque)
        assertEquals(Encuadre.PREPARAR, e.encuadre)
    }

    @Test
    fun `each moment of the session picks its block and encuadre`() {
        val plan = planSesion(bloques = 1, entrada = Entrada.RESPIRAR)
        val casos =
            listOf(
                0L to (BloqueSesion.RESPIRAR to Encuadre.RESPIRAR),
                61_000L to (BloqueSesion.FOCO to Encuadre.FOCO),
                300 * minuto to (BloqueSesion.TERMINADA to Encuadre.TERMINADA),
            )
        casos.forEach { (ms, esperado) ->
            val e = estadoSesionUi(motor.viva(plan, ms), null, preparando = false)

            assertEquals(esperado.first, e.bloque)
            assertEquals(esperado.second, e.encuadre)
        }
    }

    @Test
    fun `a break shows the break`() {
        val e = estadoSesionUi(motor.viva(planSesion(entrada = Entrada.DIRECTO), 26 * minuto), null, preparando = false)

        assertEquals(BloqueSesion.DESCANSO, e.bloque)
        assertEquals(Encuadre.DESCANSO, e.encuadre)
    }

    @Test
    fun `prepare wins over a live session while it is being prepared and loses otherwise`() {
        val viva = motor.viva(planSesion(entrada = Entrada.DIRECTO))

        assertEquals(BloqueSesion.PREPARAR, estadoSesionUi(viva, planSesion(), preparando = true).bloque)
        assertEquals(BloqueSesion.FOCO, estadoSesionUi(viva, planSesion(), preparando = false).bloque)
    }
}
