package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/** §5.1 n.º 4: desde la hora de la noche, con el día sin sellar y algo que decidir en el cierre. */
object ReglaCerrarDia : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        if (ctx.ahoraMin < ctx.horaNoche || h.diaSellado || h.pendientesCierre <= 0) return emptyList()
        return listOf(
            Candidata(
                situacion = Situacion.CERRAR_DIA,
                ref = null,
                huecos = mapOf(Hueco.N to ValorHueco.Numero(h.pendientesCierre)),
                acciones = listOf(Respuesta.CERRAR_DIA, Respuesta.LUEGO),
                destino = Destino.Cierre,
            ),
        )
    }
}
