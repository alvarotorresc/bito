package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaDiaDuroTest {
    private val habitos =
        listOf(
            habito("a", nombre = "Agua", pct30 = 50),
            habito("b", nombre = "Meditar", pct30 = 90, tipo = TipoHabito.DURACION, objetivo = 10),
        )

    @Test
    fun `a hard yesterday gives one plain yes without hour`() {
        val c = ReglaDiaDuro.candidatas(hechos(animoAyer = 2, habitos = habitos), Memoria(), ctx()).single()
        assertEquals(Situacion.DIA_DURO, c.situacion)
        assertEquals(listOf(Respuesta.VALE, Respuesta.HOY_NO), c.acciones)
        assertNull(c.hora)
        assertEquals("b", c.ref) // duración sí cuenta en el día duro
        assertEquals(ValorHueco.Texto("Meditar"), c.huecos[Hueco.HABITO])
        assertEquals(Destino.Habito("b"), c.destino)
    }

    @Test
    fun `a so-so yesterday is not a hard day`() =
        assertTrue(
            ReglaDiaDuro.candidatas(hechos(animoAyer = 1, habitos = habitos), Memoria(), ctx()).isEmpty(),
        )

    @Test
    fun `limit and quitting habits are never proposed`() =
        assertTrue(
            ReglaDiaDuro
                .candidatas(
                    hechos(animoAyer = 2, habitos = listOf(habito(tipo = TipoHabito.LIMITE), habito("d", tipo = TipoHabito.DEJAR))),
                    Memoria(),
                    ctx(),
                ).isEmpty(),
        )
}
