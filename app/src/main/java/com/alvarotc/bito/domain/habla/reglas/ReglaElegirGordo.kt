package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Ritmo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.1 n.º 6 y §5.4: antes de las 13:00, sin lo gordo elegido, fuera del día duro y con 2 o más
 * candidatos (`Hechos.candidatosGordo`, que cuenta [com.alvarotc.bito.domain.habla.LoGordo.candidatos],
 * lo mismo que la hoja). `{tarea}` es la sugerencia de Habi, si la hay.
 */
object ReglaElegirGordo : Regla {
    private const val MIN_CANDIDATOS = 2

    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        if (ctx.ahoraMin >= Ritmo.GORDO_HASTA_MIN || h.gordo.isNotEmpty() || h.diaDuro) return emptyList()
        if (h.candidatosGordo < MIN_CANDIDATOS) return emptyList()
        val sugerida =
            h.sugerenciaGordo
                ?.takeIf { it.tipo == TipoRefGordo.TAREA }
                ?.let { ref -> h.tareas.firstOrNull { it.id == ref.id } }
        return listOf(
            Candidata(
                situacion = Situacion.ELEGIR_GORDO,
                ref = null,
                huecos = sugerida?.let { mapOf(Hueco.TAREA to ValorHueco.Texto(it.titulo)) } ?: emptyMap(),
                acciones = listOf(Respuesta.ELEGIR_GORDO, Respuesta.HOY_NO),
                destino = Destino.LoGordo,
            ),
        )
    }
}
