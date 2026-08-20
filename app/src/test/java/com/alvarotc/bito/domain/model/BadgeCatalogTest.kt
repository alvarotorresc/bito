package com.alvarotc.bito.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BadgeCatalogTest {
    @Test
    fun `the catalog has the 14 ids of the badges session in its order`() {
        assertEquals(
            listOf(
                "streak-7", "streak-30", "streak-100", "streak-365",
                "perfect-day-1", "perfect-days-10", "perfect-days-50", "perfect-days-100", "perfect-week", "perfect-month",
                "first-habit", "first-week", "resurrection", "first-freezer",
            ),
            BadgeCatalog.all.map { it.id },
        )
        assertEquals(14, BadgeCatalog.size)
    }

    @Test
    fun `ids are unique ascii kebab-case and resolve by id`() {
        assertEquals(BadgeCatalog.all.size, BadgeCatalog.all.map { it.id }.toSet().size)
        assertTrue(BadgeCatalog.all.all { it.id.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")) })
        assertEquals(BadgeFamily.MOMENT, BadgeCatalog.byId("resurrection")!!.family)
        assertEquals(30, BadgeCatalog.byId("streak-30")!!.threshold)
        assertNull(BadgeCatalog.byId("streak-9000"))
    }

    @Test
    fun `families split four-six-four`() {
        assertEquals(4, BadgeCatalog.all.count { it.family == BadgeFamily.STREAK })
        assertEquals(6, BadgeCatalog.all.count { it.family == BadgeFamily.CONSTANCY })
        assertEquals(4, BadgeCatalog.all.count { it.family == BadgeFamily.MOMENT })
    }
}
