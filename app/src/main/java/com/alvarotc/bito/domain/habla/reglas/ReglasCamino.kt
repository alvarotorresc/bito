package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.model.Mood

/**
 * §5.8: el comentario de Camino según el ánimo de la semana; la ausencia manda. Sin acciones.
 * `{pct}` falta si la semana no tiene variación y `{n}` (días perfectos del mes) si vale 0 (HUECOS.md): el
 * redactor filtra los trozos que los piden.
 */
object ReglasCamino : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        val situacion =
            when (h.animoSemana) {
                Mood.DRAMATIC -> Situacion.CA_AUSENCIA
                Mood.RADIANT -> Situacion.CA_RADIANTE
                Mood.WILTED -> Situacion.CA_MUSTIO
                Mood.NORMAL -> Situacion.CA_NORMAL
            }
        return listOf(
            Candidata(
                situacion = situacion,
                ref = null,
                huecos =
                    buildMap {
                        if (h.diasPerfectosMes > 0) put(Hueco.N, ValorHueco.Numero(h.diasPerfectosMes))
                        h.deltaSemanaPct?.let { put(Hueco.PCT, ValorHueco.Numero(it)) }
                    },
                acciones = emptyList(),
            ),
        )
    }
}
