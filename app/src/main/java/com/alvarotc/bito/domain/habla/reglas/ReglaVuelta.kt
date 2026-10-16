package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.1 n.º 2 y §5.6: el primer día tras la ausencia, una sola cosa pequeña. Solo CHECK y
 * CONTADOR, porque «Hecho» marca o suma un paso.
 */
object ReglaVuelta : Regla {
    private val TIPOS = setOf(TipoHabito.CHECK, TipoHabito.CONTADOR)

    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        if (!h.primerDiaTrasAusencia) return emptyList()
        val habito = habitoMejorCumplido(h, TIPOS) ?: return emptyList()
        return listOf(
            Candidata(
                situacion = Situacion.VUELTA,
                ref = habito.id,
                huecos =
                    mapOf(
                        Hueco.HABITO to ValorHueco.Texto(habito.nombre),
                        // Días fuera = hoy - última actividad; `diasSinActividad` solo cuenta los vacíos de en medio.
                        Hueco.DIAS_FUERA to ValorHueco.Numero(h.diasSinActividad + 1),
                    ),
                acciones = listOf(Respuesta.HECHO, Respuesta.HOY_NO),
                destino = Destino.Habito(habito.id),
            ),
        )
    }
}

/**
 * El hábito exigible y pendiente hoy de uno de [tipos] con mejor cumplimiento en sus últimos 30
 * días juzgados (§5.1 n.º 2). Sin días juzgados (`pct30 == null`) va detrás de todos. Empate:
 * objetivo menor, después id.
 */
internal fun habitoMejorCumplido(
    h: Hechos,
    tipos: Set<TipoHabito>,
): HabitoHoy? =
    h.habitos
        .filter { it.exigibleHoy && !it.hechoHoy && it.tipo in tipos }
        .sortedWith(
            compareByDescending<HabitoHoy> { it.pct30 ?: -1 }
                .thenBy { it.objetivo }
                .thenBy { it.id },
        ).firstOrNull()
