package com.alvarotc.bito.ui.inicio

import com.alvarotc.bito.domain.propuestas.Destino
import org.junit.Assert.assertEquals
import org.junit.Test

class EntradaSesionTest {
    @Test
    fun `each destination has its route`() {
        assertEquals("focus?taskId=t1", EntradaSesion.ruta(Destino.Tarea("t1")))
        assertEquals("focus", EntradaSesion.ruta(Destino.Habito("h1")))
        assertEquals("breathing", EntradaSesion.ruta(Destino.Respirar))
        assertEquals("focus", EntradaSesion.ruta(null))
    }
}
