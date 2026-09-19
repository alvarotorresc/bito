package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.HabiDayPhase

/**
 * El día de Habi, en paralelo al del usuario (biblia §5): tres fases, derivadas, sin reloj propio
 * — [minutesOfDay] llega ya resuelto, igual que `today` en el resto de `domain/`.
 *
 * El «atardecer» no es una constante nueva: es [reviewTimeMinutes], la hora a la que el usuario ha
 * dicho que cierra su día (`Settings.reviewTimeMinutes`). Si mueve el repaso a las 23:00, Habi
 * espera el cierre a las 23:00.
 */
object HabiDay {
    fun phaseOf(
        requirableCount: Int,
        doneCount: Int,
        sealed: Boolean,
        minutesOfDay: Int,
        reviewTimeMinutes: Int,
    ): HabiDayPhase =
        when {
            // El sellado manda sobre todo lo demás: el día está cerrado.
            sealed -> HabiDayPhase.ASLEEP
            // Todos los exigibles hechos: pasa a esperar el cierre, sea la hora que sea.
            requirableCount > 0 && doneCount >= requirableCount -> HabiDayPhase.WAITING
            // Ya es la hora de cerrar, queden cosas o no haya ninguna: espera contigo.
            minutesOfDay >= reviewTimeMinutes -> HabiDayPhase.WAITING
            else -> HabiDayPhase.AWAKE
        }
}
