package com.alvarotc.bito.domain.model

/** The three badge families of the functional doc (§5.2). */
enum class BadgeFamily { STREAK, CONSTANCY, MOMENT }

/** One catalog badge (docs/06 §2). [threshold] is the streak length / perfect-day count for the numbered ones, null otherwise. */
data class BadgeDef(val id: String, val family: BadgeFamily, val threshold: Int? = null)

/** The 14 badges of v1 (docs/06 §2). Ids are ASCII kebab-case, immutable for life: they travel in backups. */
object BadgeCatalog {
    /** Stable order = docs/06 §2 table order. */
    val all: List<BadgeDef> =
        listOf(
            BadgeDef("streak-7", BadgeFamily.STREAK, 7),
            BadgeDef("streak-30", BadgeFamily.STREAK, 30),
            BadgeDef("streak-100", BadgeFamily.STREAK, 100),
            BadgeDef("streak-365", BadgeFamily.STREAK, 365),
            BadgeDef("perfect-day-1", BadgeFamily.CONSTANCY, 1),
            BadgeDef("perfect-days-10", BadgeFamily.CONSTANCY, 10),
            BadgeDef("perfect-days-50", BadgeFamily.CONSTANCY, 50),
            BadgeDef("perfect-days-100", BadgeFamily.CONSTANCY, 100),
            BadgeDef("perfect-week", BadgeFamily.CONSTANCY),
            BadgeDef("perfect-month", BadgeFamily.CONSTANCY),
            BadgeDef("first-habit", BadgeFamily.MOMENT),
            BadgeDef("first-week", BadgeFamily.MOMENT),
            BadgeDef("resurrection", BadgeFamily.MOMENT),
            BadgeDef("first-freezer", BadgeFamily.MOMENT),
        )
    private val byId = all.associateBy { it.id }

    fun byId(id: String): BadgeDef? = byId[id]

    val size: Int get() = all.size
}
