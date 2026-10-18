package com.alvarotc.bito.ui.inicio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComprasDeTiendaTest {
    @Test
    fun `a stale state on return does not spend the purchase`() {
        val compras = ComprasDeTienda()
        compras.alAbrir()
        compras.alComprar("banco")

        // Hoy vuelve con el estado de antes de comprar (WhileSubscribed ya lo había parado): no entrega ni consume.
        assertNull(compras.alCerrar(emptySet()))
        // Llega el estado real con el banco: ahora sí, una sola vez.
        assertEquals("banco", compras.alCerrar(setOf("banco")))
        assertNull(compras.alCerrar(setOf("banco")))
    }

    @Test
    fun `opening the store starts a visit without purchases`() {
        val compras = ComprasDeTienda()
        compras.alComprar("banco")
        compras.alAbrir()

        assertNull(compras.alCerrar(setOf("banco")))
    }

    @Test
    fun `the last bought object wins`() {
        val compras = ComprasDeTienda()
        compras.alAbrir()
        compras.alComprar("banco")
        compras.alComprar("regadera")

        assertEquals("regadera", compras.alCerrar(setOf("banco", "regadera")))
    }
}
