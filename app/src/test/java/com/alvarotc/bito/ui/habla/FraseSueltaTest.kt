package com.alvarotc.bito.ui.habla

import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.TrozoInfo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.catalogoFalso
import com.alvarotc.bito.domain.model.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FraseSueltaTest {
    private val trozos =
        listOf(
            TrozoInfo(0, emptySet(), setOf(Hueco.NOMBRE)),
            TrozoInfo(1, emptySet(), emptySet()),
            TrozoInfo(2, emptySet(), setOf(Hueco.NOMBRE, Hueco.TAREA)),
            TrozoInfo(3, emptySet(), emptySet()),
        )
    private val catalogo = catalogoFalso(Bolsa.SE_ENTRAR to trozos)

    private fun elegir(
        nombre: String?,
        semilla: Long,
        huecos: Map<Hueco, ValorHueco> = emptyMap(),
    ) = FraseSuelta.elegir(Bolsa.SE_ENTRAR, Personality.NEUTRA, nombre, semilla, catalogo, huecos)

    @Test
    fun `without a name only nameless items are chosen`() {
        (0L until 60L).forEach { s ->
            val f = elegir(null, s)
            assertTrue("semilla $s", f.trozos.single().indice in setOf(1, 3))
            assertTrue(Hueco.NOMBRE !in f.huecos)
        }
    }

    @Test
    fun `with a name items with the name are possible and the name goes in the gaps`() {
        val indices = (0L until 60L).map { elegir("Ana", it) }
        assertTrue(indices.any { Hueco.NOMBRE in trozos[it.trozos.single().indice].huecos })
        indices.forEach { assertEquals(ValorHueco.Texto("Ana"), it.huecos[Hueco.NOMBRE]) }
        // Un hueco que el contexto no trae (TAREA) descarta el item 2.
        assertTrue(indices.none { it.trozos.single().indice == 2 })
    }

    @Test
    fun `an item whose gap is in the context can be chosen`() {
        val huecos = mapOf(Hueco.TAREA to ValorHueco.Texto("Leer"))
        assertTrue((0L until 80L).any { elegir("Ana", it, huecos).trozos.single().indice == 2 })
    }

    @Test
    fun `the same seed gives the same item`() {
        (0L until 20L).forEach { s -> assertEquals(elegir("Ana", s), elegir("Ana", s)) }
    }

    @Test
    fun `the key carries the bag, the voice and the index`() {
        val c = elegir("Ana", 7).trozos.single()
        assertEquals(Bolsa.SE_ENTRAR, c.bolsa)
        assertEquals(Personality.NEUTRA, c.voz)
    }

    @Test
    fun `with no item that fits it falls back to the nameless zero or to zero`() {
        val soloNombre = catalogoFalso(Bolsa.SE_ENTRAR to listOf(TrozoInfo(0, emptySet(), setOf(Hueco.NOMBRE))))
        val f = FraseSuelta.elegir(Bolsa.SE_ENTRAR, Personality.NEUTRA, null, 3, soloNombre)
        assertEquals(0, f.trozos.single().indice)
        val conSinNombre =
            catalogoFalso(
                Bolsa.SE_ENTRAR to listOf(TrozoInfo(0, emptySet(), setOf(Hueco.TAREA)), TrozoInfo(1, emptySet(), emptySet())),
            )
        val g = FraseSuelta.elegir(Bolsa.SE_ENTRAR, Personality.NEUTRA, null, 3, conSinNombre)
        assertEquals(1, g.trozos.single().indice)
    }
}
