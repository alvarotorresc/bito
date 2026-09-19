package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay

/**
 * El ritual del ojo (biblia §4): Habi nace con los dos ojos en blanco, se pinta el primero al
 * crear el primer hábito y el segundo al alcanzar el hito de racha 7. **Nunca se despintan.**
 *
 * La clave de diseño: el nivel es DERIVABLE del historial, así que se auto-sana. Lo que se guarda
 * en DataStore (`Settings.habiEyesPainted`) es una caché, no la verdad — por eso restaurar un
 * backup con vida ya vivida devuelve los ojos pintados sin ceremonia y sin nada codificado en el
 * códec (spec §7.4).
 *
 * [FIRST_STREAK] se lee como `streak-7` lo define `docs/06-badges.md` §3, y se detecta con la
 * MISMA función con la que [PointsEngine] concede `STREAK_MILESTONE`: el ojo, el badge «Primera
 * llama» y el exclusivo `pattern-chispas` coinciden por construcción, no por casualidad.
 */
object EyeRitual {
    const val FIRST_STREAK = 7

    /** El nivel que la historia justifica, sin mirar nada persistido. */
    fun derivedLevel(
        state: DomainState,
        today: LogicalDay,
    ): Int =
        when {
            state.habits.any { Streaks.reachedMilestones(state, it, today, setOf(FIRST_STREAK)).isNotEmpty() } -> 2
            state.habits.isNotEmpty() -> 1
            else -> 0
        }

    /** Monotonía por construcción: nunca baja. */
    fun heal(
        stored: Int,
        derived: Int,
    ): Int = maxOf(stored, derived)
}

/** De qué nivel a qué nivel subió el ritual en un pase de reconcile. No nulo ⇒ hay rito que pintar. */
data class EyeTransition(val from: Int, val to: Int)
