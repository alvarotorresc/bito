package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [BadgeStrings] is a pure lookup table — badge ids (from [BadgeCatalog]) and [BadgeFamily]
 * values map to string resources, and the domain stays free of Android resource ids. An unknown
 * badge id is a programmer error, not a missing-translation case, so it throws.
 */
class BadgeStringsTest {
    @Test
    fun `every catalog id resolves a name and a how`() {
        BadgeCatalog.all.forEach {
            assertTrue(BadgeStrings.badgeNameRes(it.id) != 0)
            assertTrue(BadgeStrings.badgeHowRes(it.id) != 0)
        }
    }

    @Test
    fun `names are distinct`() {
        val ids = BadgeCatalog.all.map { BadgeStrings.badgeNameRes(it.id) }

        assertEquals(BadgeCatalog.all.size, ids.toSet().size)
    }

    @Test
    fun `hows are distinct`() {
        val ids = BadgeCatalog.all.map { BadgeStrings.badgeHowRes(it.id) }

        assertEquals(BadgeCatalog.all.size, ids.toSet().size)
    }

    @Test
    fun `an unknown id throws`() {
        assertFailsWith<IllegalArgumentException> { BadgeStrings.badgeNameRes("nope") }
        assertFailsWith<IllegalArgumentException> { BadgeStrings.badgeHowRes("nope") }
    }

    @Test
    fun `every catalog badge resolves an icon`() {
        BadgeCatalog.all.forEach { def -> BadgeStrings.badgeIcon(def) }
    }

    @Test
    fun `every family resolves a distinct resource`() {
        val ids = BadgeFamily.entries.map { BadgeStrings.badgeFamilyRes(it) }

        assertEquals(BadgeFamily.entries.size, ids.toSet().size)
    }
}
