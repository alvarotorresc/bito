package com.alvarotc.bito.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Temporadas en sus bordes (spec §9.1): el día anterior, el primero, el último y el siguiente. */
class SaleWindowTest {
    private fun onSale(
        id: String,
        y: Int,
        m: Int,
        d: Int,
    ) = HabiCatalog.isOnSale(HabiCatalog.byId(id)!!, LocalDate.of(y, m, d))

    @Test
    fun `easter sunday matches the published dates`() {
        assertEquals(LocalDate.of(2024, 3, 31), easterSunday(2024))
        assertEquals(LocalDate.of(2025, 4, 20), easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), easterSunday(2027))
        assertEquals(LocalDate.of(2028, 4, 16), easterSunday(2028))
    }

    @Test
    fun `the pumpkin is on sale from october 15 to november 2 inclusive`() {
        assertFalse(onSale("costume-calabaza", 2026, 10, 14))
        assertTrue(onSale("costume-calabaza", 2026, 10, 15))
        assertTrue(onSale("costume-calabaza", 2026, 11, 2))
        assertFalse(onSale("costume-calabaza", 2026, 11, 3))
    }

    @Test
    fun `the reindeer season crosses the new year`() {
        assertFalse(onSale("costume-reno", 2026, 11, 30))
        assertTrue(onSale("costume-reno", 2026, 12, 1))
        assertTrue(onSale("costume-reno", 2026, 12, 31))
        assertTrue(onSale("costume-reno", 2027, 1, 6))
        assertFalse(onSale("costume-reno", 2027, 1, 7))
    }

    @Test
    fun `carnival 2027 is the fourteen days before ash wednesday on february 10`() {
        assertFalse(onSale("costume-dinosaurio", 2027, 1, 26))
        assertTrue(onSale("costume-dinosaurio", 2027, 1, 27))
        assertTrue(onSale("costume-dinosaurio", 2027, 2, 9))
        assertFalse(onSale("costume-dinosaurio", 2027, 2, 10))
    }

    @Test
    fun `carnival 2028 follows a later easter into a leap february`() {
        assertFalse(onSale("costume-dinosaurio", 2028, 2, 15))
        assertTrue(onSale("costume-dinosaurio", 2028, 2, 16))
        assertTrue(onSale("costume-dinosaurio", 2028, 2, 29))
        assertFalse(onSale("costume-dinosaurio", 2028, 3, 1))
    }

    @Test
    fun `items without a season are always on sale`() {
        assertTrue(onSale("head-boina", 2026, 8, 14))
        assertTrue(onSale("body-cielo", 2027, 2, 28))
    }
}
