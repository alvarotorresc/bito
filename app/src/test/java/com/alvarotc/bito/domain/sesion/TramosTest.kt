package com.alvarotc.bito.domain.sesion

import com.alvarotc.bito.domain.sesion.Tramo.Descanso
import com.alvarotc.bito.domain.sesion.Tramo.Foco
import com.alvarotc.bito.domain.sesion.Tramo.Respirar
import org.junit.Assert.assertEquals
import org.junit.Test

/** La secuencia de la sesión (spec §7.2). */
class TramosTest {
    @Test
    fun `the defaults give breathing, four blocks and the long break before the end`() {
        assertEquals(
            listOf(
                Respirar(6),
                Foco(1, 25),
                Descanso(1, false, 5),
                Foco(2, 25),
                Descanso(2, false, 5),
                Foco(3, 25),
                Descanso(3, false, 5),
                Foco(4, 25),
                Descanso(4, true, 15),
            ),
            Tramos.de(planSesion()),
        )
    }

    @Test
    fun `one block has no break`() {
        assertEquals(listOf(Foco(1, 25)), Tramos.de(planSesion(bloques = 1, entrada = Entrada.DIRECTO)))
        assertEquals(listOf(Respirar(6), Foco(1, 25)), Tramos.de(planSesion(bloques = 1)))
    }

    @Test
    fun `two and three blocks have short breaks between them only`() {
        assertEquals(
            listOf(Foco(1, 25), Descanso(1, false, 5), Foco(2, 25)),
            Tramos.de(planSesion(bloques = 2, entrada = Entrada.DIRECTO)),
        )
        assertEquals(
            listOf(Foco(1, 25), Descanso(1, false, 5), Foco(2, 25), Descanso(2, false, 5), Foco(3, 25)),
            Tramos.de(planSesion(bloques = 3, entrada = Entrada.DIRECTO)),
        )
    }

    @Test
    fun `without breathing the first block comes first`() {
        val tramos = Tramos.de(planSesion(entrada = Entrada.DIRECTO))
        assertEquals(Foco(1, 25), tramos.first())
        assertEquals(8, tramos.size)
    }

    @Test
    fun `every round closes with a long break, from two to eight`() {
        for (rondas in 2..8) {
            val descansos = Tramos.de(planSesion(bloques = 8, entrada = Entrada.DIRECTO, rondas = rondas)).filterIsInstance<Descanso>()
            for (d in descansos) {
                val largo = d.trasBloque % rondas == 0
                assertEquals("rondas=$rondas tras ${d.trasBloque}", Descanso(d.trasBloque, largo, if (largo) 15 else 5), d)
            }
            val esperados = if (8 % rondas == 0) 8 else 7
            assertEquals("rondas=$rondas", esperados, descansos.size)
        }
    }

    @Test
    fun `the long break goes before the end when the last block closes a round`() {
        assertEquals(Descanso(4, true, 15), Tramos.de(planSesion(rondas = 2)).last())
        assertEquals(Descanso(6, true, 15), Tramos.de(planSesion(bloques = 6, rondas = 3)).last())
        assertEquals(Foco(5, 25), Tramos.de(planSesion(bloques = 5, rondas = 4)).last())
    }
}
