package com.alvarotc.bito.ui.sistema

import androidx.compose.animation.core.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class MuellesTest {
    private val catalogo =
        mapOf(
            "marcar" to (0.70f to 700f), "hoja" to (0.85f to 350f), "hundir" to (1.0f to 600f),
            "interruptor" to (0.90f to 600f), "indicador" to (0.85f to 500f), "planta" to (0.22f to 170f),
            "nodulo" to (0.60f to 520f), "camara" to (1.0f to 260f), "capa" to (0.90f to 420f),
            "caida" to (0.78f to 300f), "luz" to (1.0f to 300f), "camino" to (0.92f to 240f),
            "lado" to (1.0f to 380f), "fader" to (1.0f to 1200f), "reloj" to (1.0f to 200f),
        )

    @Test
    fun `the fifteen springs keep the catalog values`() {
        assertEquals(catalogo, Muelles.tabla)
        val hoja: SpringSpec<Float> = Muelles.hoja()
        assertEquals(0.85f, hoja.dampingRatio, 0f)
        assertEquals(350f, hoja.stiffness, 0f)
    }

    @Test
    fun `reduced springs keep their stiffness and lose the bounce`() {
        val planta: SpringSpec<Float> = Muelles.planta(reducido = true)
        assertEquals(1f, planta.dampingRatio, 0f)
        assertEquals(170f, planta.stiffness, 0f)
    }

    @Test
    fun `named durations match the catalog`() {
        assertEquals(150, Duraciones.REDUCIDO)
        assertEquals(240, Duraciones.RODILLO)
        assertEquals(4000, Duraciones.AVISO)
        assertEquals(2400, Duraciones.GLOBITO)
        assertEquals(180, Duraciones.CERRAR_HOJA)
    }
}
