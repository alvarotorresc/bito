package com.alvarotc.bito.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomizationTest {
    @Test
    fun `catalog matches the economy session deliverable`() {
        val purchasable = HabiCatalog.all.filter { it.price != null }
        assertEquals(24, purchasable.size)
        assertEquals(975, purchasable.sumOf { it.price!! })
        assertEquals(4, HabiCatalog.exclusives.size)
        assertEquals(
            setOf(7, 30, 100, 365),
            HabiCatalog.exclusives.mapNotNull { it.requiredStreak }.toSet(),
        )
    }

    @Test
    fun `ids are unique and every item is exactly one of default, purchasable or exclusive`() {
        assertEquals(HabiCatalog.all.size, HabiCatalog.all.map { it.id }.toSet().size)
        HabiCatalog.all.forEach {
            val kinds = listOf(it.default, it.price != null, it.requiredStreak != null).count { k -> k }
            assertEquals("item ${it.id}", 1, kinds)
        }
    }

    @Test
    fun `defaults exist only for body and eyes`() {
        assertEquals(
            listOf(CustomizationCategory.BODY_COLOR, CustomizationCategory.EYE_COLOR),
            HabiCatalog.all.filter { it.default }.map { it.category },
        )
    }

    @Test
    fun `equipped set falls back to defaults and resolves categories from the catalog`() {
        assertEquals(EquippedSet(), equippedSetOf(emptyList()))
        val set = equippedSetOf(listOf("body-lavanda", "pattern-motas", "upper-corona", "unknown-id"))
        assertEquals("body-lavanda", set.bodyColor)
        assertEquals("pattern-motas", set.pattern)
        assertEquals("eyes-tinta", set.eyeColor)
        assertEquals("upper-corona", set.upper)
        assertNull(set.lower)
    }
}
