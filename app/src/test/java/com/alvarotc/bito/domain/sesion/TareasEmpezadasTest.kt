package com.alvarotc.bito.domain.sesion

import com.alvarotc.bito.data.sesion.SesionCerrada
import org.junit.Assert.assertEquals
import org.junit.Test

class TareasEmpezadasTest {
    private fun cerrada(objetivo: Objetivo) = SesionCerrada("s", objetivo, 20_000, 1L, 2L, 25, 1, 4, ResultadoSesion.HECHA)

    @Test
    fun `a closed session and the live one both count, for tasks only`() {
        val viva = registroSesion(planSesion(objetivo = Objetivo.Tarea("viva")))
        val cerradas = listOf(cerrada(Objetivo.Tarea("hecha")), cerrada(Objetivo.Habito("h1")), cerrada(Objetivo.Libre))
        assertEquals(setOf("hecha", "viva"), TareasEmpezadas.de(cerradas, viva))
    }

    @Test
    fun `without sessions nothing is started`() {
        assertEquals(emptySet<String>(), TareasEmpezadas.de(emptyList(), null))
        assertEquals(emptySet<String>(), TareasEmpezadas.de(emptyList(), registroSesion(planSesion(objetivo = Objetivo.Libre))))
    }
}
