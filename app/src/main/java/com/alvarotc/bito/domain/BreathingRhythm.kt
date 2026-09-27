package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathPhase.EXHALE
import com.alvarotc.bito.domain.model.BreathPhase.HOLD
import com.alvarotc.bito.domain.model.BreathPhase.INHALE
import com.alvarotc.bito.domain.model.BreathingMode
import kotlin.math.PI
import kotlin.math.cos

/**
 * El motor de ritmo (spec §3.2). Puro y determinista: recibe el modo y los milisegundos
 * transcurridos y devuelve donde esta la respiracion. No guarda estado ni lee reloj.
 *
 * Regla unica: nunca se corta un ciclo. La sesion acaba siempre al final de una espiracion, o de
 * la retencion vacia en la caja. Centrarme hace ocho cajas (128 s): siete se quedarian por debajo
 * de los «2 min» anunciados y cortar una a medias romperia la unica promesa del modo.
 *
 * [Point.fill] es lo que sigue Habi: 0 = pulmones vacios, 1 = llenos. Dentro de cada paso la curva
 * es `(1 - cos(pi * p)) / 2`, sin aristas en los extremos, asi que Habi no da tirones entre pasos.
 */
object BreathingRhythm {
    private const val MILLIS_PER_SECOND = 1_000L

    data class Step(val phase: BreathPhase, val seconds: Int)

    data class Point(
        val phase: BreathPhase,
        /** Indice global de paso desde el inicio: cambia si y solo si cambia la fase. */
        val stepIndex: Int,
        /** 1-based. */
        val cycle: Int,
        val totalCycles: Int,
        val fill: Float,
        val remainingMillis: Long,
        val finished: Boolean,
    ) {
        /** Redondeo hacia arriba: el reloj nunca dice 0:00 con la sesion viva. */
        val remainingSeconds: Int get() = ((remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    private val CALM_STEPS = listOf(Step(INHALE, 4), Step(EXHALE, 6))
    private val SLEEP_STEPS = listOf(Step(INHALE, 4), Step(HOLD, 7), Step(EXHALE, 8))
    private val FOCUS_STEPS = listOf(Step(INHALE, 4), Step(HOLD, 4), Step(EXHALE, 4), Step(HOLD, 4))

    fun steps(mode: BreathingMode): List<Step> =
        when (mode) {
            BreathingMode.CALM -> CALM_STEPS
            BreathingMode.SLEEP -> SLEEP_STEPS
            BreathingMode.FOCUS -> FOCUS_STEPS
        }

    fun cycles(mode: BreathingMode): Int =
        when (mode) {
            BreathingMode.CALM -> 12
            BreathingMode.SLEEP -> 6
            BreathingMode.FOCUS -> 8
        }

    fun totalMillis(mode: BreathingMode): Long = cycleMillis(mode) * cycles(mode)

    fun at(
        mode: BreathingMode,
        elapsedMillis: Long,
    ): Point {
        val stepList = steps(mode)
        val cycleCount = cycles(mode)
        val total = totalMillis(mode)
        val t = elapsedMillis.coerceAtLeast(0L)
        if (t >= total) {
            return Point(
                phase = stepList.last().phase,
                stepIndex = stepList.size * cycleCount - 1,
                cycle = cycleCount,
                totalCycles = cycleCount,
                fill = 0f,
                remainingMillis = 0L,
                finished = true,
            )
        }
        val cycleMillis = cycleMillis(mode)
        val cycleIndex = (t / cycleMillis).toInt()
        var inCycle = t - cycleIndex * cycleMillis
        var index = 0
        while (inCycle >= stepList[index].seconds * MILLIS_PER_SECOND) {
            inCycle -= stepList[index].seconds * MILLIS_PER_SECOND
            index++
        }
        val step = stepList[index]
        val progress = inCycle.toFloat() / (step.seconds * MILLIS_PER_SECOND)
        val fill =
            when (step.phase) {
                INHALE -> ease(progress)
                EXHALE -> 1f - ease(progress)
                // El paso anterior decide: tras dentro se retiene lleno, tras fuera (solo la caja) vacio.
                HOLD -> if (stepList[(index - 1 + stepList.size) % stepList.size].phase == INHALE) 1f else 0f
            }
        return Point(
            phase = step.phase,
            stepIndex = cycleIndex * stepList.size + index,
            cycle = cycleIndex + 1,
            totalCycles = cycleCount,
            fill = fill,
            remainingMillis = total - t,
            finished = false,
        )
    }

    private fun cycleMillis(mode: BreathingMode): Long = steps(mode).sumOf { it.seconds } * MILLIS_PER_SECOND

    private fun ease(progress: Float): Float = ((1.0 - cos(PI * progress)) / 2.0).toFloat()
}
