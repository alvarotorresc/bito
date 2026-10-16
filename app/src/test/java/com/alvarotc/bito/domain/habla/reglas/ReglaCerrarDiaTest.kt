package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaCerrarDiaTest {
    private val noche = 21 * 60 + 30

    @Test
    fun `before the night hour it does not come out`() =
        assertTrue(
            ReglaCerrarDia.candidatas(hechos(pendientesCierre = 3), Memoria(), ctx(ahoraMin = noche - 1, horaNoche = noche)).isEmpty(),
        )

    @Test
    fun `from the night hour it proposes closing the day`() {
        val c = ReglaCerrarDia.candidatas(hechos(pendientesCierre = 3), Memoria(), ctx(ahoraMin = noche, horaNoche = noche)).single()
        assertEquals(Situacion.CERRAR_DIA, c.situacion)
        assertEquals(listOf(Respuesta.CERRAR_DIA, Respuesta.LUEGO), c.acciones)
        assertEquals(ValorHueco.Numero(3), c.huecos[Hueco.N])
        assertEquals(Destino.Cierre, c.destino)
    }

    @Test
    fun `a sealed day is not closed again`() =
        assertTrue(
            ReglaCerrarDia.candidatas(
                hechos(pendientesCierre = 3, diaSellado = true),
                Memoria(),
                ctx(ahoraMin = noche, horaNoche = noche),
            ).isEmpty(),
        )

    @Test
    fun `nothing to decide, nothing to close`() =
        assertTrue(ReglaCerrarDia.candidatas(hechos(pendientesCierre = 0), Memoria(), ctx(ahoraMin = noche, horaNoche = noche)).isEmpty())
}
