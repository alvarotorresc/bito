package com.alvarotc.bito.ui.habi

import androidx.compose.runtime.Immutable

/** El triple de la amplitud idle: visible a 200 dp sin parecer un globo (spec §6.2). */
const val GUIDED_BREATH_AMPLITUDE = 3f

/**
 * Párpados del modo guiado: 1 = la pieza de ojos cerrados pintada (spec §3.5), ya no una rendija.
 * El pintor vectorial viejo borra los ojos con 1.0; muere con la Tarea 11.
 */
const val GUIDED_EYE_CLOSURE = 1f

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
): Float = breath?.fill?.coerceIn(0f, 1f) ?: idle

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
