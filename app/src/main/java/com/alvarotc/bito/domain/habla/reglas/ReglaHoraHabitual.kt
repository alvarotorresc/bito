package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Ritmo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/** §5.1 n.º 11: la hora habitual de un hábito pendiente cae en los próximos [Ritmo.HORA_HABITUAL_VENTANA_MIN] minutos. Gana la más cercana. */
object ReglaHoraHabitual : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> =
        h.habitos.mapNotNull { habito ->
            val hora = habito.horaHabitual ?: return@mapNotNull null
            val faltan = hora - ctx.ahoraMin
            if (!habito.pendienteHoy() || faltan !in 0..Ritmo.HORA_HABITUAL_VENTANA_MIN) return@mapNotNull null
            Candidata(
                situacion = Situacion.HORA_HABITUAL,
                ref = habito.id,
                huecos = mapOf(Hueco.HABITO to ValorHueco.Texto(habito.nombre), Hueco.HORA to ValorHueco.Hora(hora)),
                acciones = listOf(Respuesta.AHORA, Respuesta.LUEGO),
                destino = Destino.Habito(habito.id),
                peso = Ritmo.HORA_HABITUAL_VENTANA_MIN - faltan,
            )
        }
}
