package com.alvarotc.bito.sesion.fuera

import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Tramo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La regla del servicio, la única que usan lo de fuera (con la app delante o fuera) y el receptor (re-review R1, R2). */
class ReglaServicioTest {
    private val respirar = Tramo.Respirar()
    private val foco = Tramo.Foco(bloque = 1, minutos = 25)
    private val descanso = Tramo.Descanso(trasBloque = 1, largo = false, minutos = 5)
    private val rain = SessionBackground.RAIN
    private val none = SessionBackground.NONE

    @Test
    fun `it goes on while something sounds`() {
        assertTrue(ReglaServicio.sigue(respirar, none, suena = true, fondoPendiente = false))
        assertTrue(ReglaServicio.sigue(foco, rain, suena = true, fondoPendiente = false))
    }

    @Test
    fun `a background requested and not yet confirmed holds it`() {
        assertTrue(ReglaServicio.sigue(foco, rain, suena = false, fondoPendiente = true))
    }

    @Test
    fun `a break holds it only when the session has a background`() {
        assertTrue(ReglaServicio.sigue(descanso, rain, suena = false, fondoPendiente = false))
        assertFalse(ReglaServicio.sigue(descanso, none, suena = false, fondoPendiente = false))
    }

    @Test
    fun `a focus with nothing sounding and nothing pending lets it go`() {
        // Pérdida del foco de audio para siempre, Bito callado o fondo Ninguno tras respirar.
        assertFalse(ReglaServicio.sigue(foco, rain, suena = false, fondoPendiente = false))
        assertFalse(ReglaServicio.sigue(foco, none, suena = false, fondoPendiente = false))
    }

    @Test
    fun `nothing holds it once the session is over`() {
        assertFalse(ReglaServicio.sigue(null, rain, suena = true, fondoPendiente = true))
    }

    @Test
    fun `it starts only when something sounds, never on a pending background or a break`() {
        assertTrue(ReglaServicio.arranca(foco, suena = true))
        assertFalse(ReglaServicio.arranca(foco, suena = false))
        assertFalse(ReglaServicio.arranca(descanso, suena = false))
        assertFalse(ReglaServicio.arranca(null, suena = true))
    }
}
