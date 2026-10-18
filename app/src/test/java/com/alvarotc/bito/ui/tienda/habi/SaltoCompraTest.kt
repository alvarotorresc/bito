package com.alvarotc.bito.ui.tienda.habi

import com.alvarotc.bito.domain.model.EquippedSet
import org.junit.Assert.assertEquals
import org.junit.Test

class SaltoCompraTest {
    private val delta = 0.0001f

    private fun cabeza(id: String?) = EquippedSet(head = id)

    @Test
    fun `nothing on the head is 0,22`() {
        assertEquals(0.22f, SaltoCompra.amplitud(EquippedSet()), delta)
    }

    @Test
    fun `only neck, face, feet or colour is 0,22`() {
        assertEquals(0.22f, SaltoCompra.amplitud(EquippedSet(neck = "neck-bufanda")), delta)
        assertEquals(0.22f, SaltoCompra.amplitud(EquippedSet(face = "face-gafas-sol")), delta)
        assertEquals(0.22f, SaltoCompra.amplitud(EquippedSet(feet = "feet-botas-agua")), delta)
        assertEquals(0.22f, SaltoCompra.amplitud(EquippedSet(bodyColor = "body-rosa")), delta)
    }

    @Test
    fun `wool hat is 0,15`() = assertEquals(0.15f, SaltoCompra.amplitud(cabeza("head-gorro-lana")), delta)

    @Test
    fun `beret is 0,11`() = assertEquals(0.11f, SaltoCompra.amplitud(cabeza("head-boina")), delta)

    @Test
    fun `straw hat is 0,11`() = assertEquals(0.11f, SaltoCompra.amplitud(cabeza("head-paja")), delta)

    @Test
    fun `headphones are 0,07`() = assertEquals(0.07f, SaltoCompra.amplitud(cabeza("head-auriculares")), delta)

    @Test
    fun `top hat is 0,05`() = assertEquals(0.05f, SaltoCompra.amplitud(cabeza("head-copa")), delta)

    @Test
    fun `flower is 0,20`() = assertEquals(0.20f, SaltoCompra.amplitud(cabeza("head-flor")), delta)

    @Test
    fun `crown is 0,06`() = assertEquals(0.06f, SaltoCompra.amplitud(cabeza("head-corona")), delta)

    @Test
    fun `pumpkin costume is 0,13`() = assertEquals(0.13f, SaltoCompra.amplitud(EquippedSet(costume = "costume-calabaza")), delta)

    @Test
    fun `reindeer costume is 0,03`() = assertEquals(0.03f, SaltoCompra.amplitud(EquippedSet(costume = "costume-reno")), delta)

    @Test
    fun `dinosaur costume is 0,14`() = assertEquals(0.14f, SaltoCompra.amplitud(EquippedSet(costume = "costume-dinosaurio")), delta)

    @Test
    fun `the costume wins over the head`() {
        assertEquals(0.03f, SaltoCompra.amplitud(EquippedSet(head = "head-flor", costume = "costume-reno")), delta)
    }

    @Test
    fun `an unknown head is 0,11`() = assertEquals(0.11f, SaltoCompra.amplitud(cabeza("head-inventado")), delta)

    @Test
    fun `the cap is 0,24`() {
        assertEquals(0.24f, SaltoCompra.TOPE, delta)
        assertEquals(0.24f, SaltoCompra.limitar(0.9f), delta)
        assertEquals(0.05f, SaltoCompra.limitar(0.05f), delta)
    }

    @Test
    fun `reduced has no jump`() {
        assertEquals(0f, SaltoCompra.amplitud(cabeza("head-gorro-lana"), reducido = true), delta)
        assertEquals(0f, SaltoCompra.amplitud(EquippedSet(), reducido = true), delta)
    }

    @Test
    fun `the mock amplitude is scaled from body height to canvas height`() {
        // El mock mide el salto sobre el cuerpo (1404) y la app sobre el lienzo (1786).
        assertEquals(0.22f * 1404f / 1786f, SaltoCompra.enLienzo(0.22f), delta)
        assertEquals(0f, SaltoCompra.enLienzo(0f), delta)
    }
}
