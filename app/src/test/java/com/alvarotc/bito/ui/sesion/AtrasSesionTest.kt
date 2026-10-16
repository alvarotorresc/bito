package com.alvarotc.bito.ui.sesion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class AtrasSesionTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `back in each state does what the spec says`() {
        assertNull("en Hoy no hay atrás", atrasDe(null, false))
        assertEquals(AtrasSesion.A_HOY, atrasDe(BloqueSesion.PREPARAR, false))
        assertEquals(AtrasSesion.A_PREPARAR, atrasDe(BloqueSesion.RESPIRAR, false))
        assertEquals(AtrasSesion.SIGUE_EN_MARCHA, atrasDe(BloqueSesion.FOCO, false))
        assertEquals(AtrasSesion.SIGUE_EN_MARCHA, atrasDe(BloqueSesion.DESCANSO, false))
        assertEquals(AtrasSesion.A_HOY, atrasDe(BloqueSesion.TERMINADA, false))
    }

    @Test
    fun `an open sheet is closed first, in every state`() {
        BloqueSesion.entries.forEach { assertEquals(AtrasSesion.CERRAR_HOJA, atrasDe(it, true)) }
    }

    @Test
    fun `the sheet registry closes the sheet it holds once`() {
        val hojas = RegistroHoja()
        var cerrada = 0
        assertFalse(hojas.abierta)
        assertFalse(hojas.cerrar())

        hojas.abrir { cerrada++ }
        assertTrue(hojas.abierta)
        assertTrue(hojas.cerrar())
        assertEquals(1, cerrada)

        hojas.soltar()
        assertFalse(hojas.abierta)
    }

    @Test
    fun `the button says what back does in breathing and elsewhere`() {
        val motor = MotorSesionFalso()
        var accion: AccionAtras? = null
        var estado by mutableStateOf(EstadoSesionUi(BloqueSesion.RESPIRAR))
        rule.setContent {
            ConSistema {
                accion = rememberAtrasSesion(estado, motor, RegistroHoja(), {}, {})
                Box {}
            }
        }
        rule.waitForIdle()
        assertEquals("Saltar y volver a preparar", accion?.etiqueta)

        Snapshot.withMutableSnapshot { estado = EstadoSesionUi(BloqueSesion.FOCO) }
        rule.waitForIdle()
        assertEquals("Volver", accion?.etiqueta)

        Snapshot.withMutableSnapshot { estado = EstadoSesionUi(null) }
        rule.waitForIdle()
        assertNull(accion)
    }

    @Test
    fun `a sheet open in the block takes back before the screen does`() {
        val motor = MotorSesionFalso()
        motor.viva(planSesion(entrada = Entrada.DIRECTO))
        val hojas = RegistroHoja()
        var cerradas = 0
        var aHoy = 0
        var accion: AccionAtras? = null
        rule.setContent {
            ConSistema {
                accion = rememberAtrasSesion(EstadoSesionUi(BloqueSesion.FOCO), motor, hojas, { aHoy++ }, {})
                Box {}
            }
        }
        hojas.abrir { cerradas++ }
        rule.waitForIdle()

        rule.runOnIdle { accion!!.alPulsar() }

        assertEquals(1, cerradas)
        assertEquals("con la hoja abierta no sale a Hoy", 0, aHoy)
        assertNotNull(motor.registro.value)
    }
}
