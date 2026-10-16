package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco

/** §5.1 n.º 13: siempre hay un saludo, sin acciones. Sin nombre, sin hueco `nombre`; sin hábitos de hoy, sin `hechos` ni `total` (HUECOS.md). */
object ReglaSaludo : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> =
        listOf(
            Candidata(
                situacion = Situacion.SALUDO,
                ref = null,
                huecos =
                    buildMap {
                        ctx.nombre?.takeIf { it.isNotBlank() }?.let { put(Hueco.NOMBRE, ValorHueco.Texto(it)) }
                        if (h.tuDiaTotal > 0) {
                            put(Hueco.HECHOS, ValorHueco.Numero(h.tuDiaHechos))
                            put(Hueco.TOTAL, ValorHueco.Numero(h.tuDiaTotal))
                        }
                    },
                acciones = emptyList(),
            ),
        )
}
