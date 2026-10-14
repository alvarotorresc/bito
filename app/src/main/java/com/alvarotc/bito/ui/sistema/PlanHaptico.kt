package com.alvarotc.bito.ui.sistema

import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.view.HapticFeedbackConstants

/** Una primitiva de composición con su escala (0–1, ya multiplicada por la fuerza) y su retraso previo. */
data class Paso(
    val primitiva: Int,
    val escala: Float,
    val retrasoMs: Int = 0,
)

/** Lo que hay que hacer sonar en el motor del móvil para una [Haptica]. */
sealed interface PlanTacto {
    data class Primitivas(
        val pasos: List<Paso>,
    ) : PlanTacto

    data class Constante(
        val constante: Int,
    ) : PlanTacto

    data class Predefinido(
        val efecto: Int,
    ) : PlanTacto

    data class Pulso(
        val ms: Long,
        val amplitud: Int,
    ) : PlanTacto

    data object Nada : PlanTacto
}

/** Decide, sin tocar Android, cómo se materializa cada háptica según el nivel de API y lo que soporte el motor. */
object PlanHaptico {
    private data class Receta(
        val pasos: List<Paso>,
        val sdkMinimo: Int,
    )

    fun de(
        h: Haptica,
        sdk: Int,
        primitivaOk: (Int) -> Boolean,
        fuerza: Int,
        vibracion: Boolean,
        vistaUsable: Boolean,
    ): PlanTacto {
        if (!vibracion || fuerza <= 0) return PlanTacto.Nada
        val constante = constanteDeVista(h, sdk, vistaUsable)
        if (constante != null) return PlanTacto.Constante(constante)
        val receta = receta(h, sdk)
        if (sdk >= 30 && sdk >= receta.sdkMinimo && receta.pasos.all { primitivaOk(it.primitiva) }) {
            return PlanTacto.Primitivas(receta.pasos.map { it.copy(escala = it.escala * fuerza / 10f) })
        }
        return if (sdk >= 29) {
            PlanTacto.Predefinido(predefinido(h))
        } else {
            PlanTacto.Pulso(duracionPulso(h), 255 * fuerza / 10)
        }
    }

    private fun constanteDeVista(
        h: Haptica,
        sdk: Int,
        vistaUsable: Boolean,
    ): Int? =
        when {
            !vistaUsable -> null
            h == Haptica.SEG && sdk >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            h == Haptica.CONFIRM && sdk >= 30 -> HapticFeedbackConstants.CONFIRM
            h == Haptica.REJECT && sdk >= 30 -> HapticFeedbackConstants.REJECT
            else -> null
        }

    private fun receta(
        h: Haptica,
        sdk: Int,
    ): Receta =
        when (h) {
            Haptica.TICK -> Receta(listOf(Paso(Composition.PRIMITIVE_TICK, 0.3f)), 30)
            Haptica.TICKS ->
                Receta(
                    listOf(
                        Paso(Composition.PRIMITIVE_TICK, 0.25f, 0),
                        Paso(Composition.PRIMITIVE_TICK, 0.25f, 110),
                        Paso(Composition.PRIMITIVE_TICK, 0.25f, 110),
                    ),
                    30,
                )
            Haptica.CLICK -> Receta(listOf(Paso(Composition.PRIMITIVE_CLICK, 0.6f)), 30)
            Haptica.LOW -> Receta(listOf(Paso(Composition.PRIMITIVE_LOW_TICK, 0.4f)), 31)
            Haptica.FALL -> Receta(listOf(Paso(Composition.PRIMITIVE_QUICK_FALL, 0.4f)), 30)
            Haptica.RISE -> Receta(listOf(Paso(Composition.PRIMITIVE_SLOW_RISE, 0.5f)), 30)
            Haptica.THUD -> Receta(listOf(Paso(Composition.PRIMITIVE_THUD, 0.5f)), 31)
            Haptica.SEG -> Receta(listOf(Paso(Composition.PRIMITIVE_TICK, 0.2f)), 30)
            Haptica.CONFIRM ->
                Receta(
                    listOf(
                        Paso(Composition.PRIMITIVE_CLICK, 0.5f),
                        Paso(Composition.PRIMITIVE_TICK, 0.4f, 80),
                    ),
                    30,
                )
            Haptica.REJECT ->
                if (sdk >= 31) {
                    Receta(listOf(Paso(Composition.PRIMITIVE_THUD, 0.5f)), 31)
                } else {
                    Receta(
                        listOf(
                            Paso(Composition.PRIMITIVE_CLICK, 0.7f),
                            Paso(Composition.PRIMITIVE_CLICK, 0.7f, 100),
                        ),
                        30,
                    )
                }
        }

    private fun predefinido(h: Haptica): Int =
        when (h) {
            Haptica.TICK, Haptica.TICKS, Haptica.SEG, Haptica.LOW -> VibrationEffect.EFFECT_TICK
            Haptica.CLICK, Haptica.FALL, Haptica.RISE, Haptica.CONFIRM -> VibrationEffect.EFFECT_CLICK
            Haptica.THUD, Haptica.REJECT -> VibrationEffect.EFFECT_HEAVY_CLICK
        }

    private fun duracionPulso(h: Haptica): Long =
        when (h) {
            Haptica.TICK, Haptica.TICKS, Haptica.SEG, Haptica.LOW -> 12L
            Haptica.CLICK, Haptica.FALL, Haptica.RISE, Haptica.CONFIRM -> 20L
            Haptica.THUD, Haptica.REJECT -> 35L
        }
}
