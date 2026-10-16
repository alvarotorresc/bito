package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

/** La parte del día (spec §4.3): antes de las 6, 6–12, 12–15, 15–20 y desde las 20. */
enum class Franja {
    MADRUGADA,
    MANANA,
    MEDIODIA,
    TARDE,
    NOCHE,
    ;

    companion object {
        /** La franja de un minuto del día de reloj. */
        fun de(minutoDelDia: Int): Franja =
            when {
                minutoDelDia < 6 * 60 -> MADRUGADA
                minutoDelDia < 12 * 60 -> MANANA
                minutoDelDia < 15 * 60 -> MEDIODIA
                minutoDelDia < 20 * 60 -> TARDE
                else -> NOCHE
            }
    }
}

/** Cómo está Habi (spec §4.3, biblia §5): dormido si el día está sellado, esperando si no queda nada o es de noche. */
enum class FaseHabi { DESPIERTA, ESPERANDO, DURMIENDO }

/** Estación por meses meteorológicos, invertida en el hemisferio sur (spec §4.3). */
enum class Estacion {
    PRIMAVERA,
    VERANO,
    OTONO,
    INVIERNO,
    ;

    companion object {
        /** Prefijos de zona horaria del hemisferio sur: lista cerrada. */
        val HEMISFERIO_SUR =
            listOf(
                "Australia/",
                "Pacific/Auckland",
                "Pacific/Chatham",
                "Pacific/Fiji",
                "America/Argentina",
                "America/Santiago",
                "America/Montevideo",
                "America/Sao_Paulo",
                "America/Asuncion",
                "America/La_Paz",
                "America/Lima",
                "Africa/Johannesburg",
                "Africa/Maputo",
                "Africa/Harare",
                "Africa/Windhoek",
                "Indian/Mauritius",
                "Antarctica/",
            )

        fun de(
            fecha: LocalDate,
            zona: ZoneId,
        ): Estacion {
            val norte =
                when (fecha.month) {
                    Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> INVIERNO
                    Month.MARCH, Month.APRIL, Month.MAY -> PRIMAVERA
                    Month.JUNE, Month.JULY, Month.AUGUST -> VERANO
                    else -> OTONO
                }
            val sur = HEMISFERIO_SUR.any { zona.id.startsWith(it) }
            return if (sur) norte.opuesta() else norte
        }

        private fun Estacion.opuesta(): Estacion =
            when (this) {
                PRIMAVERA -> OTONO
                VERANO -> INVIERNO
                OTONO -> PRIMAVERA
                INVIERNO -> VERANO
            }
    }
}

/**
 * El silencio de Bito (spec §6.3), en minutos del día. `desde > hasta` cruza la medianoche;
 * `desde == hasta` no silencia nunca.
 */
data class Silencio(
    val desde: Int,
    val hasta: Int,
) {
    fun contiene(minuto: Int): Boolean =
        when {
            desde == hasta -> false
            desde < hasta -> minuto in desde until hasta
            else -> minuto >= desde || minuto < hasta
        }
}

/**
 * Todo lo que el motor y el redactor saben del momento (spec §4.3). [hoy] es el día lógico;
 * [fecha] y [diaSemana] son los de ese día lógico; [ahoraMin] es el minuto de reloj.
 * Lo construye [Contextos.de].
 */
data class Contexto(
    val hoy: LogicalDay,
    val ahoraMin: Int,
    val ahoraMillis: Long,
    val fecha: LocalDate,
    val diaSemana: DayOfWeek,
    val estacion: Estacion,
    val franja: Franja,
    val fase: FaseHabi,
    val voz: Personality,
    val nombre: String?,
    val horaManana: Int,
    val horaNoche: Int,
    val corteDia: Int,
    val silencio: Silencio?,
    val permisoAvisos: Boolean,
    val alarmasExactas: Boolean,
    val zona: ZoneId,
)
