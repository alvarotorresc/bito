package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Personality
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * El único sitio que construye un [Contexto] (spec §4.3). Puro: quien llama lee los ajustes y
 * los permisos, y calcula [diaSellado] y [quedanExigibles] desde el estado (con `Sealing` y
 * `Compliance`) antes de llamar, nunca desde `Hechos`.
 */
object Contextos {
    fun de(
        ahoraMillis: Long,
        zona: ZoneId,
        corteDia: Int,
        horaManana: Int,
        horaNoche: Int,
        dndActivo: Boolean,
        dndDesde: Int,
        dndHasta: Int,
        voz: Personality,
        nombre: String?,
        permisoAvisos: Boolean,
        alarmasExactas: Boolean,
        diaSellado: Boolean,
        quedanExigibles: Boolean,
    ): Contexto {
        val reloj = Instant.ofEpochMilli(ahoraMillis).atZone(zona).toLocalDateTime()
        val hoy = LogicalDays.logicalDayOf(ahoraMillis, corteDia, zona)
        val fecha = LocalDate.ofEpochDay(hoy.toLong())
        val ahoraMin = reloj.hour * 60 + reloj.minute
        // Pasada la medianoche y antes del corte, el día lógico sigue siendo el de ayer: ya es de noche.
        val deNoche = ahoraMin >= horaNoche || reloj.toLocalDate() != fecha
        val fase =
            when {
                diaSellado -> FaseHabi.DURMIENDO
                !quedanExigibles || deNoche -> FaseHabi.ESPERANDO
                else -> FaseHabi.DESPIERTA
            }
        return Contexto(
            hoy = hoy,
            ahoraMin = ahoraMin,
            ahoraMillis = ahoraMillis,
            fecha = fecha,
            diaSemana = fecha.dayOfWeek,
            estacion = Estacion.de(fecha, zona),
            franja = Franja.de(ahoraMin),
            fase = fase,
            voz = voz,
            nombre = nombre?.trim()?.takeIf { it.isNotEmpty() },
            horaManana = horaManana,
            horaNoche = horaNoche,
            corteDia = corteDia,
            silencio = if (dndActivo) Silencio(dndDesde, dndHasta) else null,
            permisoAvisos = permisoAvisos,
            alarmasExactas = alarmasExactas,
            zona = zona,
        )
    }
}

/**
 * §5.1: desde [Ritmo.CADUCA_ANTES_NOCHE_MIN] antes de la hora de la noche, y pasada la medianoche antes del corte
 * (sigue siendo la noche del mismo día lógico). Las reglas que caducan ahí («ayer se escapó», «racha cerca», «día
 * flojo») ya no proponen desde ese momento, y la fuente las da por caducadas con esta misma cuenta.
 */
fun Contexto.cercaDeLaNoche(): Boolean =
    ahoraMin >= horaNoche - Ritmo.CADUCA_ANTES_NOCHE_MIN ||
        Instant.ofEpochMilli(ahoraMillis).atZone(zona).toLocalDate() != fecha
