package com.alvarotc.bito.domain.model

/** Las seis especies, en el orden en que se abren las parcelas (pieza 1 §6.1). [key] es la clave del arte. */
enum class GardenSpecies(val key: String) {
    ROUND_LEAVES("hojas-redondas"),
    SUNFLOWERS("girasoles"),
    LAVENDER("lavanda"),
    APPLE_TREE("manzano"),
    FERNS("helechos"),
    CHERRY_TREE("cerezo"),
}

/** Las etapas de una planta y el agua acumulada que pide cada una. Nunca retroceden (D7). */
enum class GrowthStage(val minWater: Double) {
    SEED(0.0),
    SPROUT(3.0),
    PLANT(8.0),
    FLOWERS(15.0),
    TREE(25.0),
}

data class GardenPlot(
    val species: GardenSpecies,
    val stage: GrowthStage,
)

/** Mañana 6–12, día 12–17, tarde 17–21, noche 21–6 (§6.1). */
enum class LightPhase { MANANA, DIA, TARDE, NOCHE }

/**
 * La luz de ahora. [phase] es la del reloj. Fuera de las transiciones, [from] = [to] = [phase] y
 * [transition] = 0; en la media hora alrededor de cada frontera, [transition] va de 0 a 1 de [from] a [to].
 */
data class GardenLight(
    val phase: LightPhase,
    val from: LightPhase,
    val to: LightPhase,
    val transition: Float,
)

enum class GardenSeason { SPRING, SUMMER, AUTUMN, WINTER }

/** Los especiales por logro (§6.2): permanentes y fuera de la tienda. [key] es la clave del arte. */
enum class GardenSpecial(
    val key: String,
    val badgeId: String,
) {
    LANTERN("farolillo", "streak-7"),
    STONE_PATH("caminito", "first-week"),
    POND("estanque", "perfect-month"),
    MUSHROOMS("setas", "resurrection"),
    BIG_CHERRY("cerezo-grande", "perfect-days-100"),
    GOLDEN_TREE("arbol-dorado", "streak-365"),
}

/** Todo lo que la escena del jardín necesita saber. Se deduce del historial (D11); no se guarda. */
data class GardenState(
    val plots: List<GardenPlot>,
    val water: Double,
    val wilt: Int,
    val light: GardenLight,
    val season: GardenSeason,
    val specials: Set<GardenSpecial>,
    // Objetos de jardín comprados (la clave es el id sin «garden-»: «banco», «regadera»).
    val objects: Set<String> = emptySet(),
) {
    /** Las seis parcelas en árbol: a partir de aquí solo cambia la estación. */
    val full: Boolean get() = plots.size == GardenSpecies.entries.size && plots.all { it.stage == GrowthStage.TREE }

    companion object {
        /** Lo que se pinta antes de la primera emisión: un jardín recién plantado a mediodía de verano. */
        val INITIAL =
            GardenState(
                plots = listOf(GardenPlot(GardenSpecies.ROUND_LEAVES, GrowthStage.SEED)),
                water = 0.0,
                wilt = 0,
                light = GardenLight(LightPhase.DIA, LightPhase.DIA, LightPhase.DIA, 0f),
                season = GardenSeason.SUMMER,
                specials = emptySet(),
            )
    }
}
