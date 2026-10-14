package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAroTest {
    @Test
    fun `the ring level goes from 0 to 8 and never overflows`() {
        assertEquals(0, aroDeProgreso(0, 8))
        assertEquals(4, aroDeProgreso(4, 8))
        assertEquals(8, aroDeProgreso(9, 8))
        assertEquals(3, aroDeProgreso(25, 60)) // 3,33 -> 3
        assertEquals(0, aroDeProgreso(1, 0))
    }

    @Test
    fun `a negative progress stays at the empty ring`() {
        assertEquals(0, aroDeProgreso(-3, 8))
    }

    @Test
    fun `every ring level has its drawable`() {
        val ids = (0..8).map { aroDrawable(it) }
        assertEquals(9, ids.toSet().size)
    }

    @Test
    fun `the list widget shows three rows and counts the rest`() {
        assertEquals(0, pendientesOcultos(0))
        assertEquals(0, pendientesOcultos(3))
        assertEquals(2, pendientesOcultos(5))
    }

    @Test
    fun `the cached Habi bitmap key ignores accessories`() {
        val a = claveHabi(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(head = "gorro")), 96)
        val b = claveHabi(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()), 96)
        assertEquals(a, b)
    }
}
