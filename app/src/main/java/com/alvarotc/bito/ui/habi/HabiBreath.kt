package com.alvarotc.bito.ui.habi

import androidx.compose.runtime.Immutable

/** El triple de la amplitud idle: visible a 200 dp sin parecer un globo (spec §6.2). */
const val GUIDED_BREATH_AMPLITUDE = 3f

/**
 * ⚑CALL 1, cerrada el 2026-09-26: rendija. El mismo parpadeo de siempre sostenido casi cerrado.
 * Sostenido en 1.0 no pinta parpados cerrados: BORRA los ojos (drawEyes solo dibuja el ovalo si
 * queda altura), y durante dos minutos Habi se quedaria sin cara.
 */
const val GUIDED_EYE_CLOSURE = 0.9f

/**
 * El gancho externo de [HabiAvatar] (D6): con el, Habi deja de respirar a su ritmo de animo y
 * sigue [fill] (0 = vacio, 1 = lleno, de BreathingRhythm) con [amplitude] veces su amplitud idle y
 * el parpado sostenido en [eyeClosure]. Sin el (null), nada cambia.
 */
@Immutable
data class HabiBreath(
    val fill: Float,
    val amplitude: Float = GUIDED_BREATH_AMPLITUDE,
    val eyeClosure: Float = GUIDED_EYE_CLOSURE,
)

/** La respiracion que pinta el avatar: la fase externa si hay guia, la idle si no. */
internal fun guidedBreathValue(
    breath: HabiBreath?,
    idle: Float,
): Float = breath?.fill ?: idle

/**
 * El parpadeo que pinta el avatar. Con guia manda el cierre sostenido (los parpadeos idle y de
 * toque no tienen sentido con los ojos cerrados). Sin ella, el mayor de los tres canales:
 * idle, toque y [lidRelease] — la apertura lenta al acabar la guia, que vale 0 si nunca la hubo.
 */
internal fun guidedBlinkValue(
    breath: HabiBreath?,
    idleBlink: Float,
    tapBlink: Float,
    lidRelease: Float,
): Float = breath?.eyeClosure ?: maxOf(idleBlink, tapBlink, lidRelease)
