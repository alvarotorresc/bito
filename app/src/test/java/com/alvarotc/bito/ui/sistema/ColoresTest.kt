package com.alvarotc.bito.ui.sistema

import androidx.compose.ui.graphics.Color
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColoresTest {
    @Test
    fun `the palette keeps the catalog values`() {
        assertEquals(Color(0xFF7DB6C9), BitoClaro.agua)
        assertEquals(Color(0xFFE4CFB4), BitoClaro.tAlta)
        assertEquals(Color(0xFFF3E7D3), BitoClaro.canto)
        assertEquals(Color(0xFF4A2C22), BitoClaro.contorno)
        assertEquals(Color(0xFF8E2F22), BitoClaro.peligro)
        assertEquals(0.38f, BitoClaro.velo.alpha, 0.01f)
        assertEquals(Color(0xFFCFC5B8), BitoClaro.vetaFria)
    }

    @Test
    fun `the old names are aliases of the new palette`() {
        assertEquals(BitoClaro.tAlta, Papel)
        assertEquals(BitoClaro.canto, Tarjeta)
        assertEquals(BitoClaro.agua, Hoja)
        assertEquals(BitoClaro.tinta, Tinta)
        assertEquals(BitoClaro.peligro, Peligro)
        assertEquals(Escena.rachas.anillo, Brasa)
    }

    @Test
    fun `the five settings strata go from light to dark`() {
        val e = BitoClaro.estratos
        assertEquals(listOf(false, false, false, true, true), e.todos.map { it.oscuro })
        assertEquals(Color(0xFF6E4E39), e.copias.fondo)
        assertEquals(Color(0xFFF4EAD8), e.acerca.tinta)
    }

    @Test
    fun `the sky clock stops cover the whole day`() {
        assertEquals(0, Escena.relojCielo.first().first)
        assertEquals(24 * 60, Escena.relojCielo.last().first)
        assertTrue(Escena.relojCielo.zipWithNext().all { (a, b) -> a.first < b.first })
    }
}
