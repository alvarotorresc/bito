package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.BreathingSession
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import java.time.ZoneId

/**
 * El contador honesto del final del ejercicio (spec §3.3). Informativo: ninguna regla de la app
 * depende de el, y respirar no entra en puntos, rachas, dia perfecto, insignias ni animo (D1).
 *
 * La semana es la ISO de lunes a domingo sobre dias logicos, la misma que usan las tareas y la
 * semana perfecta. La tabla no guarda dia logico (D9 fija sus cinco columnas), asi que el dia de
 * cada sesion se deriva de su startedAtMillis con el corte y la zona que recibe esta funcion: un
 * cambio de hora de corte puede mover de semana una sesion hecha cerca de la medianoche del
 * domingo. Desviacion acotada de la regla E7, aceptada a sabiendas.
 */
object BreathingStats {
    private const val SECONDS_PER_MINUTE = 60

    data class Tally(val sessions: Int, val seconds: Int) {
        /** Redondeo al minuto mas cercano, con minimo 1 si hubo algo: 40 s dicen «1 min», no «0 min». */
        val minutes: Int get() = if (seconds == 0) 0 else maxOf(1, (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE)
    }

    fun thisWeek(
        sessions: List<BreathingSession>,
        today: LogicalDay,
        cutoffMinutes: Int,
        zone: ZoneId,
    ): Tally {
        val week = LogicalDays.periodKeyOf(today, Period.WEEK)
        return tallyOf(
            sessions.filter {
                LogicalDays.periodKeyOf(LogicalDays.logicalDayOf(it.startedAtMillis, cutoffMinutes, zone), Period.WEEK) == week
            },
        )
    }

    fun allTime(sessions: List<BreathingSession>): Tally = tallyOf(sessions)

    private fun tallyOf(sessions: List<BreathingSession>): Tally = Tally(sessions.size, sessions.sumOf { it.durationSeconds })
}
