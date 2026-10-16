package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.1 n.º 5: no queda nada exigible hoy y el día no está sellado (Habi esperando). Un límite o
 * un dejar sin fallar cuentan como hechos (`HabitoHoy.hechoHoy`). Sin huecos (HUECOS.md).
 */
object ReglaTodoHecho : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        if (h.diaSellado || h.habitos.any { it.exigibleHoy && !it.hechoHoy }) return emptyList()
        return listOf(
            Candidata(
                situacion = Situacion.TODO_HECHO,
                ref = null,
                huecos = emptyMap(),
                acciones = listOf(Respuesta.CERRAR_DIA, Respuesta.LUEGO),
                destino = Destino.Cierre,
            ),
        )
    }
}
