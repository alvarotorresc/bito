package com.alvarotc.bito.domain.model

import java.time.LocalDate

/** Eje de personalización (pieza 1 §4.1). Viaja por nombre en Room y en la copia de seguridad. */
enum class CustomizationCategory { BODY_COLOR, EYE_COLOR, HEAD, FACE, NECK, FEET, COSTUME, GARDEN_OBJECT }

/** Cuándo se puede comprar un artículo de temporada. Lo comprado se usa todo el año (D5). */
sealed interface SaleWindow {
    fun contains(date: LocalDate): Boolean

    /** Del día [startMonth]/[startDay] al [endMonth]/[endDay], ambos incluidos. Si el fin cae antes, cruza el año. */
    data class Fixed(
        val startMonth: Int,
        val startDay: Int,
        val endMonth: Int,
        val endDay: Int,
    ) : SaleWindow {
        override fun contains(date: LocalDate): Boolean {
            val day = date.monthValue * 100 + date.dayOfMonth
            val start = startMonth * 100 + startDay
            val end = endMonth * 100 + endDay
            return if (start <= end) day in start..end else day >= start || day <= end
        }
    }

    /** Carnaval: los 14 días anteriores al Miércoles de Ceniza, que cae 46 días antes del Domingo de Pascua. */
    data object Carnival : SaleWindow {
        private const val ASH_WEDNESDAY_BEFORE_EASTER = 46L
        private const val CARNIVAL_DAYS = 14L

        override fun contains(date: LocalDate): Boolean {
            val ashWednesday = easterSunday(date.year).minusDays(ASH_WEDNESDAY_BEFORE_EASTER)
            return !date.isBefore(ashWednesday.minusDays(CARNIVAL_DAYS)) && date.isBefore(ashWednesday)
        }
    }
}

/** Domingo de Pascua del calendario gregoriano (algoritmo anónimo de Meeus, Jones y Butcher). */
fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}

/**
 * Un artículo del catálogo. Exactamente uno de [default], [price] o [requiredStreak]: los de serie
 * se tienen siempre (sin fila), los de precio se compran con puntos y los exclusivos los da una
 * racha de cualquier hábito. [season] solo la tienen los disfraces. Los ids viajan en la copia:
 * NUNCA se renombran ni se reutilizan.
 */
data class CatalogItem(
    val id: String,
    val category: CustomizationCategory,
    val price: Int? = null,
    val requiredStreak: Int? = null,
    val default: Boolean = false,
    val season: SaleWindow? = null,
)

/** El catálogo de Habi (pieza 1 §4). Vive en código; Room solo guarda lo adquirido. Precios provisionales hasta la galería. */
object HabiCatalog {
    const val DEFAULT_BODY = "body-salvia"
    const val DEFAULT_EYES = "eyes-tinta"
    private const val COSTUME_PRICE = 150

    val all: List<CatalogItem> =
        listOf(
            CatalogItem(DEFAULT_BODY, CustomizationCategory.BODY_COLOR, default = true),
            CatalogItem("body-vainilla", CustomizationCategory.BODY_COLOR, price = 15),
            CatalogItem("body-melocoton", CustomizationCategory.BODY_COLOR, price = 15),
            CatalogItem("body-cielo", CustomizationCategory.BODY_COLOR, price = 15),
            CatalogItem("body-lavanda", CustomizationCategory.BODY_COLOR, price = 20),
            CatalogItem("body-rosa", CustomizationCategory.BODY_COLOR, price = 20),
            CatalogItem("body-terracota", CustomizationCategory.BODY_COLOR, price = 25),
            CatalogItem("body-carbon", CustomizationCategory.BODY_COLOR, price = 25),
            CatalogItem("body-dorado", CustomizationCategory.BODY_COLOR, requiredStreak = 365),
            CatalogItem(DEFAULT_EYES, CustomizationCategory.EYE_COLOR, default = true),
            CatalogItem("eyes-avellana", CustomizationCategory.EYE_COLOR, price = 10),
            CatalogItem("eyes-verde", CustomizationCategory.EYE_COLOR, price = 10),
            CatalogItem("eyes-azul", CustomizationCategory.EYE_COLOR, price = 15),
            CatalogItem("eyes-ambar", CustomizationCategory.EYE_COLOR, price = 15),
            CatalogItem("eyes-violeta", CustomizationCategory.EYE_COLOR, price = 20),
            CatalogItem("head-gorro-lana", CustomizationCategory.HEAD, price = 60),
            CatalogItem("head-boina", CustomizationCategory.HEAD, price = 70),
            CatalogItem("head-paja", CustomizationCategory.HEAD, price = 80),
            CatalogItem("head-auriculares", CustomizationCategory.HEAD, price = 100),
            CatalogItem("head-copa", CustomizationCategory.HEAD, price = 120),
            CatalogItem("head-flor", CustomizationCategory.HEAD, requiredStreak = 7),
            CatalogItem("head-corona", CustomizationCategory.HEAD, requiredStreak = 100),
            CatalogItem("face-gafas-redondas", CustomizationCategory.FACE, price = 50),
            CatalogItem("face-gafas-sol", CustomizationCategory.FACE, price = 80),
            CatalogItem("neck-bufanda", CustomizationCategory.NECK, price = 50),
            CatalogItem("neck-panuelo", CustomizationCategory.NECK, price = 60),
            CatalogItem("neck-collar-flores", CustomizationCategory.NECK, price = 90),
            CatalogItem("neck-panuelo-brasa", CustomizationCategory.NECK, requiredStreak = 30),
            CatalogItem("feet-calcetines", CustomizationCategory.FEET, price = 60),
            CatalogItem("feet-botas-agua", CustomizationCategory.FEET, price = 90),
            CatalogItem("feet-zapatillas", CustomizationCategory.FEET, price = 120),
            CatalogItem("costume-calabaza", CustomizationCategory.COSTUME, price = COSTUME_PRICE, season = SaleWindow.Fixed(10, 15, 11, 2)),
            CatalogItem("costume-reno", CustomizationCategory.COSTUME, price = COSTUME_PRICE, season = SaleWindow.Fixed(12, 1, 1, 6)),
            CatalogItem("costume-dinosaurio", CustomizationCategory.COSTUME, price = COSTUME_PRICE, season = SaleWindow.Carnival),
        )

    /** Objetos del jardín (pieza 6): se compran en la tienda y no se «llevan puestos». No entran en [all]. */
    val garden: List<CatalogItem> =
        listOf(
            CatalogItem("garden-banco", CustomizationCategory.GARDEN_OBJECT, price = 80),
            CatalogItem("garden-regadera", CustomizationCategory.GARDEN_OBJECT, price = 25),
        )
    private val byId = (all + garden).associateBy { it.id }
    val exclusives: List<CatalogItem> = all.filter { it.requiredStreak != null }

    fun byId(id: String): CatalogItem? = byId[id]

    /** Lo que no tiene temporada está siempre a la venta. */
    fun isOnSale(
        item: CatalogItem,
        date: LocalDate,
    ): Boolean = item.season?.contains(date) ?: true
}

/**
 * Lo que lleva Habi: un artículo por eje (§4.3). Cuerpo y ojos siempre resuelven a algo. El
 * disfraz anula cabeza, cara, cuello, pies y color de cuerpo **al pintar** (ver `habiLayers`), pero
 * lo que hubiera en esos ejes sigue aquí y vuelve en cuanto se quita el disfraz.
 */
data class EquippedSet(
    val bodyColor: String = HabiCatalog.DEFAULT_BODY,
    val eyeColor: String = HabiCatalog.DEFAULT_EYES,
    val head: String? = null,
    val face: String? = null,
    val neck: String? = null,
    val feet: String? = null,
    val costume: String? = null,
) {
    /** Todo lo puesto, también lo que un disfraz tapa. */
    val worn: Set<String> get() = setOfNotNull(bodyColor, eyeColor, head, face, neck, feet, costume)
}

/** El conjunto puesto a partir de los ids marcados como puestos; el último de cada eje gana y lo desconocido se ignora. */
fun equippedSetOf(equippedIds: Collection<String>): EquippedSet {
    var set = EquippedSet()
    for (id in equippedIds) {
        set =
            when (HabiCatalog.byId(id)?.category ?: continue) {
                CustomizationCategory.BODY_COLOR -> set.copy(bodyColor = id)
                CustomizationCategory.EYE_COLOR -> set.copy(eyeColor = id)
                CustomizationCategory.HEAD -> set.copy(head = id)
                CustomizationCategory.FACE -> set.copy(face = id)
                CustomizationCategory.NECK -> set.copy(neck = id)
                CustomizationCategory.FEET -> set.copy(feet = id)
                CustomizationCategory.COSTUME -> set.copy(costume = id)
                // Los objetos de jardín no se llevan puestos: viven en el jardín, no en Habi.
                CustomizationCategory.GARDEN_OBJECT -> set
            }
    }
    return set
}
