package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HoraPropuesta
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino

/** §5.1 n.º 9: la racha está a 1 o 2 periodos de un hito y el hábito sigue pendiente hoy. Gana la racha mayor. */
object ReglaRachaCerca : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> =
        h.habitos
            .filter { it.hitoCerca != null && it.pendienteHoy() }
            .map { habito ->
                val hora = HoraPropuesta.de(habito, ctx)
                Candidata(
                    situacion = Situacion.RACHA_CERCA,
                    ref = habito.id,
                    huecos =
                        buildMap {
                            put(Hueco.HABITO, ValorHueco.Texto(habito.nombre))
                            put(Hueco.RACHA, ValorHueco.Numero(habito.racha))
                            habito.faltanHito?.let { put(Hueco.FALTAN, ValorHueco.Numero(it)) }
                            habito.bitosHito?.let { put(Hueco.BITOS, ValorHueco.Numero(it)) }
                        },
                    acciones = accionesConHora(hora),
                    destino = Destino.Habito(habito.id),
                    hora = hora,
                    peso = habito.racha,
                )
            }
}
