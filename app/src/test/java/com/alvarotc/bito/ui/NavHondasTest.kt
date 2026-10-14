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
        assertEquals(TipoTransicion.GLOBAL, transicionDe(Momento.ENTRA, "today", "review", reducido = false))
        assertEquals(TipoTransicion.GLOBAL, transicionDe(Momento.ENTRA, "tasks", "focus?taskId={taskId}", reducido = false))
        assertEquals(TipoTransicion.FUNDIDO, transicionDe(Momento.ENTRA, "today", "habit?id={id}", reducido = true))
    }

    @Test
    fun `descent sounds going in, ascent coming out, nothing elsewhere`() {
        assertEquals(CambioSonoro.DESCENSO, sonidoDeCambio("today", "tasks", haciaDelante = true))
        assertEquals(CambioSonoro.ASCENSO, sonidoDeCambio("tasks", "today", haciaDelante = false))
        assertNull(sonidoDeCambio("today", "review", haciaDelante = true))
        assertNull(sonidoDeCambio("tasks", "tasks", haciaDelante = true))
    }

    @Test
    fun `settings uses the camera both ways and fades when reduced`() {
        assertEquals(TipoTransicion.CAMARA_ENTRA, transicionDe(Momento.ENTRA, "today", "settings", false))
        assertEquals(TipoTransicion.CAMARA_SALE, transicionDe(Momento.SALE, "today", "settings", false))
        assertEquals(TipoTransicion.CAMARA_VUELVE_ENTRA, transicionDe(Momento.VUELVE_ENTRA, "settings", "today", false))
        assertEquals(TipoTransicion.CAMARA_VUELVE_SALE, transicionDe(Momento.VUELVE_SALE, "settings", "today", false))
        Momento.entries.forEach { assertEquals(TipoTransicion.FUNDIDO, transicionAjustes(it, true)) }
    }

    @Test
    fun `the habit sheet fades from Today and goes down a stratum from archived`() {
        assertEquals(TipoTransicion.FICHA_FUNDIDO, transicionDe(Momento.ENTRA, "today", RUTA_FICHA, false))
        assertEquals(TipoTransicion.FICHA_FUNDIDO, transicionDe(Momento.VUELVE_SALE, RUTA_FICHA, "today", false))
        assertEquals(TipoTransicion.BAJA_ENTRA, transicionDe(Momento.ENTRA, "archived", RUTA_FICHA, false))
        assertEquals(TipoTransicion.SUBE_SALE, transicionDe(Momento.VUELVE_SALE, RUTA_FICHA, "archived", false))
    }

    @Test
    fun `sounds for settings, the habit sheet and the deep routes`() {
        assertEquals(CambioSonoro.ALEJAR, sonidoDeCambio("today", "settings", haciaDelante = true))
        assertEquals(CambioSonoro.ACERCAR, sonidoDeCambio("settings", "today", haciaDelante = false))
        assertEquals(CambioSonoro.DESCENSO, sonidoDeCambio("today", RUTA_FICHA, haciaDelante = true))
        assertEquals(CambioSonoro.ASCENSO, sonidoDeCambio(RUTA_FICHA, "today", haciaDelante = false))
        assertNull(sonidoDeCambio("today", "review", haciaDelante = true))
    }
}
