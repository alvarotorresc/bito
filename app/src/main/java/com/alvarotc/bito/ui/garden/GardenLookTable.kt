// Generado por tools/arte/exportar.py. No editar a mano.
package com.alvarotc.bito.ui.garden

import com.alvarotc.bito.ui.habi.ColorTint

/** Aspecto del jardín según su estado, afinado en la galería (tools/arte/tintes.json, sección jardin). */
internal object GardenLookTable {
    /** Lo que pierden las plantas por cada nivel de mustio. */
    const val PLANT_SATURATION_LOSS = 0.2f
    const val PLANT_SEPIA = 0.15f
    const val PLANT_DIM = 0.033333f
    const val PLANT_SHRINK = 0.04f
    const val PLANT_LEAN_DEGREES = 1.7f

    /** Lo que pierde el césped (el fondo) por cada nivel de mustio. */
    const val GRASS_SATURATION_LOSS = 0.133333f
    const val GRASS_SEPIA = 0.083333f
    const val GRASS_DIM = 0.01f

    /** Fracción del alto de la tierra que se hunde bajo el punto de la parcela. */
    const val SOIL_SINK = 0.35f

    /** Tinte de cada estación, por nombre de GardenSeason. La que no está no tiñe. */
    val seasons: Map<String, ColorTint> =
        mapOf(
            "SPRING" to ColorTint(hueDegrees = 0.0f, saturation = 1.08f, brightness = 1.0f, sepia = 0.0f),
            "AUTUMN" to ColorTint(hueDegrees = -14.0f, saturation = 1.05f, brightness = 1.0f, sepia = 0.18f),
            "WINTER" to ColorTint(hueDegrees = 8.0f, saturation = 0.75f, brightness = 1.04f, sepia = 0.0f),
        )

    /** Arriba y abajo del degradado de luz (ARGB), por nombre de LightPhase. */
    val light: Map<String, Pair<Long, Long>> =
        mapOf(
            "MANANA" to (0xFFF3F7FFL to 0xFFFFFAF0L),
            "DIA" to (0xFFFFFFFFL to 0xFFFFFFFFL),
            "TARDE" to (0xFFFFCF9AL to 0xFFFFDDB8L),
            "NOCHE" to (0xFF31406EL to 0xFF4B5A8AL),
        )
}
