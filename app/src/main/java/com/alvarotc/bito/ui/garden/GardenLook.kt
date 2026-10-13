package com.alvarotc.bito.ui.garden

import com.alvarotc.bito.domain.model.GardenLight
import com.alvarotc.bito.domain.model.GardenSeason
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.ui.habi.ColorTint
import kotlin.math.tan

/**
 * Cómo se ve el jardín según su estado (§6.3), por código sobre el mismo arte (D10). Todos los
 * valores salen de [GardenLookTable], que genera `exportar.py` desde la sección `jardin` de
 * `tools/arte/tintes.json`: lo que se afina en la galería es lo que pinta la app.
 */
internal object GardenLook {
    fun plantTints(
        wilt: Int,
        season: GardenSeason,
    ): List<ColorTint> =
        listOfNotNull(
            if (wilt == 0) {
                null
            } else {
                ColorTint(
                    saturation = 1f - GardenLookTable.PLANT_SATURATION_LOSS * wilt,
                    brightness = 1f - GardenLookTable.PLANT_DIM * wilt,
                    sepia = GardenLookTable.PLANT_SEPIA * wilt,
                )
            },
            seasonTint(season),
        )

    fun grassTints(
        wilt: Int,
        season: GardenSeason,
    ): List<ColorTint> =
        listOfNotNull(
            if (wilt == 0) {
                null
            } else {
                ColorTint(
                    saturation = 1f - GardenLookTable.GRASS_SATURATION_LOSS * wilt,
                    brightness = 1f - GardenLookTable.GRASS_DIM * wilt,
                    sepia = GardenLookTable.GRASS_SEPIA * wilt,
                )
            },
            seasonTint(season),
        )

    /** El `scaleY` del prototipo: la planta mustia encoge desde el pie. */
    fun plantScaleY(wilt: Int): Float = 1f - GardenLookTable.PLANT_SHRINK * wilt

    /** El `skewX` del prototipo, en grados (negativo: la copa se va a la derecha). */
    fun plantLeanDegrees(wilt: Int): Float = -GardenLookTable.PLANT_LEAN_DEGREES * wilt

    /**
     * El mismo `skewX` como factor de cizalla, anclado en el pie: x' = x + shear · (y − pie). Por
     * encima del pie (y menor) la copa se desplaza a la derecha.
     */
    fun plantShear(wilt: Int): Float = tan(Math.toRadians(plantLeanDegrees(wilt).toDouble())).toFloat()

    /** El tinte de la estación; la que no está en la tabla (verano) no tiñe. */
    private fun seasonTint(season: GardenSeason): ColorTint? = GardenLookTable.seasons[season.name]

    /** Cuánto es de noche, de 0 a 1, teniendo en cuenta las transiciones: decide el brillo del farolillo. */
    fun nightness(light: GardenLight): Float {
        val fromNight = if (light.from == LightPhase.NOCHE) 1f - light.transition else 0f
        val toNight = if (light.to == LightPhase.NOCHE) light.transition else 0f
        return fromNight + toNight
    }

    /** Arriba y abajo del degradado de luz (ARGB), multiplicado sobre la escena. El día no tiñe. */
    fun lightGradient(light: GardenLight): Pair<Long, Long> {
        val (fromTop, fromBottom) = colorsOf(light.from)
        val (toTop, toBottom) = colorsOf(light.to)
        return lerpArgb(fromTop, toTop, light.transition) to lerpArgb(fromBottom, toBottom, light.transition)
    }

    private fun colorsOf(phase: LightPhase): Pair<Long, Long> = GardenLookTable.light[phase.name] ?: (WHITE to WHITE)

    private const val WHITE = 0xFFFFFFFFL

    private fun lerpArgb(
        a: Long,
        b: Long,
        t: Float,
    ): Long {
        var out = 0xFF000000L
        for (shift in listOf(16, 8, 0)) {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            out = out or ((ca + ((cb - ca) * t)).toLong().coerceIn(0, 255) shl shift)
        }
        return out
    }
}
