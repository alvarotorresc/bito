package com.alvarotc.bito.domain.habla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NocheEnSilencioTest {
    private val s2300a0730 = Silencio(23 * 60, 7 * 60 + 30)
    private val s2100a0730 = Silencio(21 * 60, 7 * 60 + 30)

    @Test
    fun `the decided policy is to bring the night notice forward`() =
        assertEquals(PoliticaNocheEnSilencio.ADELANTAR, NocheEnSilencio.POLITICA)

    @Test
    fun `outside the silence nothing moves`() = assertEquals(21 * 60 + 30, NocheEnSilencio.minuto(21 * 60 + 30, s2300a0730, corteDia = 0))

    @Test
    fun `without silence nothing moves`() = assertEquals(21 * 60 + 30, NocheEnSilencio.minuto(21 * 60 + 30, null, corteDia = 0))

    @Test
    fun `night inside the silence moves to its start`() =
        assertEquals(21 * 60, NocheEnSilencio.minuto(21 * 60 + 30, s2100a0730, corteDia = 0))

    @Test
    fun `night inside a silence that wraps midnight moves to its start`() =
        assertEquals(23 * 60, NocheEnSilencio.minuto(23 * 60 + 15, s2300a0730, corteDia = 3 * 60))

    @Test
    fun `bringing forward never moves the night to the day before`() = assertNull(NocheEnSilencio.minuto(60, s2300a0730, corteDia = 3 * 60))

    @Test
    fun `waiting for the end loses the night when the end is another logical day`() =
        assertNull(
            NocheEnSilencio.minuto(21 * 60 + 30, s2100a0730, corteDia = 0, politica = PoliticaNocheEnSilencio.ESPERAR_AL_FINAL),
        )

    @Test
    fun `waiting for the end keeps the night when the silence ends before the cutoff`() =
        assertEquals(
            2 * 60,
            NocheEnSilencio.minuto(
                23 * 60,
                Silencio(22 * 60 + 30, 2 * 60),
                corteDia = 3 * 60,
                politica = PoliticaNocheEnSilencio.ESPERAR_AL_FINAL,
            ),
        )

    @Test
    fun `waiting for the end keeps the night when the silence ends later the same day`() =
        assertEquals(
            22 * 60,
            NocheEnSilencio.minuto(
                21 * 60 + 30,
                Silencio(21 * 60, 22 * 60),
                corteDia = 0,
                politica = PoliticaNocheEnSilencio.ESPERAR_AL_FINAL,
            ),
        )
}
