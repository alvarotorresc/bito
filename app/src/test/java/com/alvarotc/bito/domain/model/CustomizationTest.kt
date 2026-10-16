package com.alvarotc.bito.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizationTest {
    private fun ids(category: CustomizationCategory) = HabiCatalog.all.filter { it.category == category }.map { it.id }

    @Test
    fun `the catalog has exactly the axes and items of the spec`() {
        assertEquals(
            listOf(
                "body-salvia", "body-vainilla", "body-melocoton", "body-cielo", "body-lavanda",
                "body-rosa", "body-terracota", "body-carbon", "body-dorado",
            ),
            ids(CustomizationCategory.BODY_COLOR),
        )
        assertEquals(
            listOf("eyes-tinta", "eyes-avellana", "eyes-verde", "eyes-azul", "eyes-ambar", "eyes-violeta"),
            ids(CustomizationCategory.EYE_COLOR),
        )
        assertEquals(
            listOf("head-gorro-lana", "head-boina", "head-paja", "head-auriculares", "head-copa", "head-flor", "head-corona"),
            ids(CustomizationCategory.HEAD),
        )
        assertEquals(listOf("face-gafas-redondas", "face-gafas-sol"), ids(CustomizationCategory.FACE))
        assertEquals(
            listOf("neck-bufanda", "neck-panuelo", "neck-collar-flores", "neck-panuelo-brasa"),
            ids(CustomizationCategory.NECK),
        )
        assertEquals(listOf("feet-calcetines", "feet-botas-agua", "feet-zapatillas"), ids(CustomizationCategory.FEET))
        assertEquals(listOf("costume-calabaza", "costume-reno", "costume-dinosaurio"), ids(CustomizationCategory.COSTUME))
    }

    @Test
    fun `every price stays inside its axis range`() {
        val ranges =
            mapOf(
                CustomizationCategory.BODY_COLOR to 15..25,
                CustomizationCategory.EYE_COLOR to 10..20,
                CustomizationCategory.HEAD to 60..120,
                CustomizationCategory.FACE to 50..80,
                CustomizationCategory.NECK to 50..90,
                CustomizationCategory.FEET to 60..120,
                CustomizationCategory.COSTUME to 150..150,
            )
        HabiCatalog.all.filter { it.price != null }.forEach {
            assertTrue("${it.id} costs ${it.price}", it.price!! in ranges.getValue(it.category))
        }
    }

    @Test
    fun `the four exclusives are the streak rewards of the spec`() {
        assertEquals(
            mapOf(7 to "head-flor", 30 to "neck-panuelo-brasa", 100 to "head-corona", 365 to "body-dorado"),
            HabiCatalog.exclusives.associate { it.requiredStreak!! to it.id },
        )
    }

    @Test
    fun `only costumes have a season and every costume has one`() {
        HabiCatalog.all.forEach {
            assertEquals(it.id, it.category == CustomizationCategory.COSTUME, it.season != null)
        }
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
            listOf(HabiCatalog.DEFAULT_BODY, HabiCatalog.DEFAULT_EYES),
            HabiCatalog.all.filter { it.default }.map { it.id },
        )
    }

    @Test
    fun `the equipped set takes one item per axis, the last one wins and unknown ids are ignored`() {
        assertEquals(EquippedSet(), equippedSetOf(emptyList()))
        val set =
            equippedSetOf(
                listOf(
                    "body-lavanda",
                    "head-boina",
                    "head-copa",
                    "face-gafas-sol",
                    "neck-bufanda",
                    "feet-calcetines",
                    "costume-reno",
                    "unknown-id",
                ),
            )
        assertEquals("body-lavanda", set.bodyColor)
        assertEquals("eyes-tinta", set.eyeColor)
        assertEquals("head-copa", set.head)
        assertEquals("face-gafas-sol", set.face)
        assertEquals("neck-bufanda", set.neck)
        assertEquals("feet-calcetines", set.feet)
        assertEquals("costume-reno", set.costume)
        assertNull(equippedSetOf(listOf("body-cielo")).costume)
    }

    @Test
    fun `what a costume hides is still worn, so it comes back when the costume goes`() {
        val set = EquippedSet(head = "head-boina", neck = "neck-bufanda", costume = "costume-reno")
        assertTrue(set.worn.containsAll(listOf("head-boina", "neck-bufanda", "costume-reno", "body-salvia", "eyes-tinta")))
        assertEquals("head-boina", set.copy(costume = null).head)
    }

    @Test
    fun `all stays the wardrobe and garden has the two objects`() {
        // El armario son 34 artículos (la spec y el plan dicen 31 por error de recuento); no cambia.
        assertEquals(34, HabiCatalog.all.size)
        assertTrue(HabiCatalog.all.none { it.category == CustomizationCategory.GARDEN_OBJECT })
        assertEquals(listOf("garden-banco" to 80, "garden-regadera" to 25), HabiCatalog.garden.map { it.id to it.price })
        HabiCatalog.garden.forEach {
            assertEquals(CustomizationCategory.GARDEN_OBJECT, it.category)
            assertNull(it.season)
            assertNull(it.requiredStreak)
            assertTrue(!it.default)
        }
    }

    @Test
    fun `byId finds the wardrobe and the garden`() {
        assertEquals("garden-banco", HabiCatalog.byId("garden-banco")?.id)
        assertEquals("head-boina", HabiCatalog.byId("head-boina")?.id)
    }

    @Test
    fun `a garden object is never part of the equipped set`() {
        assertEquals(EquippedSet(), equippedSetOf(listOf("garden-banco", "garden-regadera")))
    }
}
