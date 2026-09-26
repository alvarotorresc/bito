package com.alvarotc.bito.domain.model

/**
 * Los tres modos fijos del ejercicio (D4): Calmarme, Dormir y Centrarme. Se guarda POR NOMBRE en
 * Room, en el backup y en DataStore: un modo futuro se anade AL FINAL, y una version anterior no
 * lo sabra leer.
 */
enum class BreathingMode { CALM, SLEEP, FOCUS }

/** Lo que pinta la palabra de fase: dentro, retener (lleno o vacio) y fuera. */
enum class BreathPhase { INHALE, HOLD, EXHALE }

/**
 * Una sesion de respiracion ya hecha. Hecho crudo, nada derivado.
 *
 * [startedAtMillis] es reloj de pared y solo sirve para saber CUANDO (la semana del contador).
 * [durationSeconds] se mide con elapsedRealtime, nunca restando horas de pared. [completed] es
 * true si el ritmo llego al final y false si se paro antes (boton, atras o segundo plano).
 */
data class BreathingSession(
    val id: String,
    val mode: BreathingMode,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val completed: Boolean,
)
