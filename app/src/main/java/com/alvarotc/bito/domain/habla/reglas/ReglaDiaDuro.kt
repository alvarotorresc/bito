package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
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
 * §5.1 n.º 3 y §5.5: ayer fue «Duro», así que hoy una sola cosa, con el criterio de la vuelta.
 * «Vale» va sin hora: solo deja la confirmación.
 */
object ReglaDiaDuro : Regla {
    private val TIPOS = setOf(TipoHabito.CHECK, TipoHabito.CONTADOR, TipoHabito.DURACION)

    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        if (!h.diaDuro) return emptyList()
        val habito = habitoMejorCumplido(h, TIPOS) ?: return emptyList()
        return listOf(
            Candidata(
                situacion = Situacion.DIA_DURO,
                ref = habito.id,
                huecos = mapOf(Hueco.HABITO to ValorHueco.Texto(habito.nombre)),
                acciones = listOf(Respuesta.VALE, Respuesta.HOY_NO),
                destino = Destino.Habito(habito.id),
            ),
        )
    }
}
