package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Ritmo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.1 n.º 1: sin permiso de notificaciones, o sin alarmas exactas con algún aviso aceptado hoy.
 * Como mucho una vez cada [Ritmo.PERMISO_CADA_DIAS] días: una respuesta en ese plazo la calla.
 * Si faltan los dos, se pide primero el de notificaciones.
 */
object ReglaPermisoAvisos : Regla {
    private val PREFIJO = "${Situacion.PERMISO_AVISOS.name}:"

    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        val faltaAlarmas = !ctx.alarmasExactas && m.avisos.any { it.dia == ctx.hoy }
        if (ctx.permisoAvisos && !faltaAlarmas) return emptyList()
        val respondidaHace =
            m.eventos.any {
                it.tipo == TipoMemoria.RESPUESTA &&
                    it.ref?.startsWith(PREFIJO) == true &&
                    ctx.hoy - it.dia < Ritmo.PERMISO_CADA_DIAS
            }
        if (respondidaHace) return emptyList()
        return listOf(
            Candidata(
                situacion = Situacion.PERMISO_AVISOS,
                ref = null,
                huecos = emptyMap(),
                acciones = listOf(Respuesta.VALE, Respuesta.HOY_NO),
                nucleo = if (ctx.permisoAvisos) Bolsa.NU_PERMISO_ALARMAS else Bolsa.NU_PERMISO_AVISOS,
            ),
        )
    }
}
