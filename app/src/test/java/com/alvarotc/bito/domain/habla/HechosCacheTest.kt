package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entryOn
import org.junit.Assert.assertEquals
import org.junit.Test

class HechosCacheTest {
    private val leer = RealHabits.readBeforeSleep
    private val agua = RealHabits.makeBed

    private fun estado(vararg extra: com.alvarotc.bito.domain.model.Entry) =
        domainState(
            habits = listOf(leer, agua),
            entries = (1..6).map { entryOn(leer, TODAY - it) } + extra,
        )

    @Test
    fun `the same state is not computed twice`() {
        val cache = HechosCache()
        val primero = cache.hechos(estado(), Memoria(), ctx(), emptyMap())
        assertEquals(2, cache.calculos)
        val segundo = cache.hechos(estado(), Memoria(), ctx(), emptyMap())
        assertEquals(2, cache.calculos)
        assertEquals(primero, segundo)
    }

    @Test
    fun `a new entry of that habit invalidates only that habit`() {
        val cache = HechosCache()
        cache.hechos(estado(), Memoria(), ctx(), emptyMap())
        cache.hechos(estado(entryOn(leer, TODAY)), Memoria(), ctx(), emptyMap())
        assertEquals(3, cache.calculos)
    }

    @Test
    fun `a new logical day invalidates everything`() {
        val cache = HechosCache()
        cache.hechos(estado(), Memoria(), ctx(), emptyMap())
        cache.hechos(estado(), Memoria(), ctx(hoy = TODAY + 1), emptyMap())
        assertEquals(4, cache.calculos)
    }

    @Test
    fun `the cached result equals the direct one`() {
        val cache = HechosCache()
        val estado = estado(entryOn(leer, TODAY))
        cache.hechos(estado, Memoria(), ctx(), emptyMap())
        assertEquals(Hechos.de(estado, Memoria(), ctx(), emptyMap()), cache.hechos(estado, Memoria(), ctx(), emptyMap()))
    }
}
