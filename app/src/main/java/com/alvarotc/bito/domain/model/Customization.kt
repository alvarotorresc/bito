package com.alvarotc.bito.domain.model

/** Customization axis of an item (tech doc §3); moved here from data.db in M6. */
enum class CustomizationCategory { BODY_COLOR, PATTERN, EYE_COLOR, UPPER, LOWER }

/**
 * One catalog entry. Exactly one of [default] / [price] / [requiredStreak] applies:
 * defaults are always owned (no DB row), priced items are bought with points,
 * exclusives are granted when any habit reaches the streak (in its period unit).
 * Ids are backup-stable: NEVER rename or reuse.
 */
data class CatalogItem(
    val id: String,
    val category: CustomizationCategory,
    val price: Int? = null,
    val requiredStreak: Int? = null,
    val default: Boolean = false,
)

/** The Habi store catalog (docs/05-economia-catalogo.md §3). Lives in code; Room only stores acquisitions. */
object HabiCatalog {
    val all: List<CatalogItem> =
        listOf(
            CatalogItem("body-salvia", CustomizationCategory.BODY_COLOR, default = true),
            CatalogItem("body-vainilla", CustomizationCategory.BODY_COLOR, price = 15),
            CatalogItem("body-melocoton", CustomizationCategory.BODY_COLOR, price = 15),
            CatalogItem("body-cielo", CustomizationCategory.BODY_COLOR, price = 15),
            CatalogItem("body-lavanda", CustomizationCategory.BODY_COLOR, price = 20),
            CatalogItem("body-rosa", CustomizationCategory.BODY_COLOR, price = 20),
            CatalogItem("body-oliva", CustomizationCategory.BODY_COLOR, price = 20),
            CatalogItem("body-terracota", CustomizationCategory.BODY_COLOR, price = 25),
            CatalogItem("body-carbon", CustomizationCategory.BODY_COLOR, price = 25),
            CatalogItem("body-dorado", CustomizationCategory.BODY_COLOR, requiredStreak = 365),
            CatalogItem("eyes-tinta", CustomizationCategory.EYE_COLOR, default = true),
            CatalogItem("eyes-avellana", CustomizationCategory.EYE_COLOR, price = 10),
            CatalogItem("eyes-verde", CustomizationCategory.EYE_COLOR, price = 10),
            CatalogItem("eyes-azul", CustomizationCategory.EYE_COLOR, price = 15),
            CatalogItem("eyes-ambar", CustomizationCategory.EYE_COLOR, price = 15),
            CatalogItem("eyes-violeta", CustomizationCategory.EYE_COLOR, price = 20),
            CatalogItem("eyes-granate", CustomizationCategory.EYE_COLOR, price = 20),
            CatalogItem("pattern-motas", CustomizationCategory.PATTERN, price = 40),
            CatalogItem("pattern-rayitas", CustomizationCategory.PATTERN, price = 40),
            CatalogItem("pattern-corazones", CustomizationCategory.PATTERN, price = 50),
            CatalogItem("pattern-estrellas", CustomizationCategory.PATTERN, price = 50),
            CatalogItem("pattern-flores", CustomizationCategory.PATTERN, price = 60),
            CatalogItem("pattern-chispas", CustomizationCategory.PATTERN, requiredStreak = 7),
            CatalogItem("pattern-llamas", CustomizationCategory.PATTERN, requiredStreak = 30),
            CatalogItem("upper-gorro-lana", CustomizationCategory.UPPER, price = 80),
            CatalogItem("upper-lazo", CustomizationCategory.UPPER, price = 90),
            CatalogItem("upper-copa", CustomizationCategory.UPPER, price = 120),
            CatalogItem("upper-corona", CustomizationCategory.UPPER, requiredStreak = 100),
            CatalogItem("lower-calcetines", CustomizationCategory.LOWER, price = 80),
            CatalogItem("lower-zapatillas", CustomizationCategory.LOWER, price = 120),
        )
    private val byId = all.associateBy { it.id }
    val exclusives: List<CatalogItem> = all.filter { it.requiredStreak != null }

    fun byId(id: String): CatalogItem? = byId[id]
}

/** What Habi wears. Body and eyes always resolve (default when nothing equipped). */
data class EquippedSet(
    val bodyColor: String = "body-salvia",
    val pattern: String? = null,
    val eyeColor: String = "eyes-tinta",
    val upper: String? = null,
    val lower: String? = null,
)

/** Derives the worn set from equipped row ids; unknown ids are ignored (forward compat). */
fun equippedSetOf(equippedIds: Collection<String>): EquippedSet {
    var set = EquippedSet()
    for (id in equippedIds) {
        when (HabiCatalog.byId(id)?.category ?: continue) {
            CustomizationCategory.BODY_COLOR -> set = set.copy(bodyColor = id)
            CustomizationCategory.PATTERN -> set = set.copy(pattern = id)
            CustomizationCategory.EYE_COLOR -> set = set.copy(eyeColor = id)
            CustomizationCategory.UPPER -> set = set.copy(upper = id)
            CustomizationCategory.LOWER -> set = set.copy(lower = id)
        }
    }
    return set
}
