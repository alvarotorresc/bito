package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Ritmo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.1 n.º 7: lo gordo elegido y nada hecho ni empezado, pasadas [Ritmo.GORDO_ESPERA_MIN] desde
 * que se eligió o más tarde de las 12:00. Una tarea está empezada con un intento o una sesión hoy
 * (`TareaHoy.empezada`); un hábito, con algo registrado hoy. La ref es lo primero de lo gordo.
 * Sin `{tarea}` (HUECOS.md): `{gordo}` es la lista entera y `{primer_paso}`, el de la primera
 * tarea de lo gordo que lo tenga.
 */
object ReglaGordoSinEmpezar : Regla {
    private const val MILLIS_MIN = 60_000L

    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        val primero = h.gordo.firstOrNull() ?: return emptyList()
        if (h.gordo.any { it in h.gordoHecho || empezado(it, h) }) return emptyList()
        val esperado = m.gordoElegidoMillis?.let { ctx.ahoraMillis - it >= Ritmo.GORDO_ESPERA_MIN * MILLIS_MIN } ?: false
        if (!esperado && ctx.ahoraMin <= Ritmo.GORDO_TARDE_MIN) return emptyList()
        val nombres = h.gordo.mapNotNull { nombreDe(it, h) }
        val primerPaso = Respuestas.primerPasoDeGordo(h)
        val huecos =
            buildMap {
                if (nombres.isNotEmpty()) put(Hueco.GORDO, ValorHueco.Lista(nombres))
                if (primerPaso != null) put(Hueco.PRIMER_PASO, ValorHueco.Texto(primerPaso))
            }
        return listOf(
            Candidata(
                situacion = Situacion.GORDO_SIN_EMPEZAR,
                ref = primero.id,
                huecos = huecos,
                acciones = listOf(Respuesta.AHORA, Respuesta.LUEGO),
                destino =
                    when (primero.tipo) {
                        TipoRefGordo.TAREA -> Destino.Tarea(primero.id)
                        TipoRefGordo.HABITO -> Destino.Habito(primero.id)
                    },
            ),
        )
    }

    private fun empezado(
        ref: RefGordo,
        h: Hechos,
    ): Boolean =
        when (ref.tipo) {
            TipoRefGordo.TAREA -> h.tareas.firstOrNull { it.id == ref.id }?.let { it.hecha || it.empezada } ?: false
            TipoRefGordo.HABITO -> h.habitos.firstOrNull { it.id == ref.id }?.let { it.hechoHoy || it.progreso > 0 } ?: false
        }

    private fun nombreDe(
        ref: RefGordo,
        h: Hechos,
    ): String? =
        when (ref.tipo) {
            TipoRefGordo.TAREA -> h.tareas.firstOrNull { it.id == ref.id }?.titulo
            TipoRefGordo.HABITO -> h.habitos.firstOrNull { it.id == ref.id }?.nombre
        }
}
