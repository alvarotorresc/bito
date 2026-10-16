package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/** §5.1 n.º 12: una tarea vencida o que vence hoy, sin hacer, que no es lo gordo. Gana la que lleva más días vencida. */
object ReglaTareaVence : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        val gordo = h.gordo.filter { it.tipo == TipoRefGordo.TAREA }.map { it.id }.toSet()
        return h.tareas
            .filter { !it.hecha && (it.venceHoy || it.vencidaDias != null) && it.id !in gordo }
            .map { tarea ->
                Candidata(
                    situacion = Situacion.TAREA_VENCE,
                    ref = tarea.id,
                    huecos = mapOf(Hueco.TAREA to ValorHueco.Texto(tarea.titulo)),
                    acciones = listOf(Respuesta.AHORA, Respuesta.HOY_NO),
                    destino = Destino.Tarea(tarea.id),
                    peso = tarea.vencidaDias ?: 0,
                )
            }
    }
}
