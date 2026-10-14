package com.alvarotc.bito.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavHondasTest {
    @Test
    fun `only the four routes of this piece go down a stratum`() {
        assertEquals(setOf("habit?id={id}", "task?id={id}", "tasks", "archived"), RUTAS_HONDAS)
    }

    @Test
    fun `entering a deep route slides up and the screen above goes up a quarter`() {
        assertEquals(TipoTransicion.BAJA_ENTRA, transicionDe(Momento.ENTRA, "today", "tasks", reducido = false))
        assertEquals(TipoTransicion.BAJA_SALE, transicionDe(Momento.SALE, "today", "tasks", reducido = false))
    }

    @Test
    fun `going back from a deep route is the mirror`() {
        assertEquals(TipoTransicion.SUBE_SALE, transicionDe(Momento.VUELVE_SALE, "tasks", "today", reducido = false))
        assertEquals(TipoTransicion.SUBE_ENTRA, transicionDe(Momento.VUELVE_ENTRA, "tasks", "today", reducido = false))
    }

    @Test
    fun `other routes keep the global transition and reduced motion fades`() {
        assertEquals(TipoTransicion.GLOBAL, transicionDe(Momento.ENTRA, "today", "stats", reducido = false))
        assertEquals(TipoTransicion.GLOBAL, transicionDe(Momento.ENTRA, "tasks", "focus?taskId={taskId}", reducido = false))
        assertEquals(TipoTransicion.FUNDIDO, transicionDe(Momento.ENTRA, "today", "habit?id={id}", reducido = true))
    }

    @Test
    fun `descent sounds going in, ascent coming out, nothing elsewhere`() {
        assertEquals(true, sonidoDeCambio("today", "tasks", haciaDelante = true))
        assertEquals(false, sonidoDeCambio("tasks", "today", haciaDelante = false))
        assertNull(sonidoDeCambio("today", "stats", haciaDelante = true))
        assertNull(sonidoDeCambio("tasks", "tasks", haciaDelante = true))
    }
}
